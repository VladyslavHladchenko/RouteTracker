# RouteTracker

Wear OS app for tracking the next direct public-transport departures on Prague PID routes.

The active work is in the `wear` module. The app is now route-configurable on the watch itself:

- choose origin stop/station
- optionally narrow origin to a platform
- choose destination stop/station
- optionally narrow destination to a platform
- optionally lock to one line
- save favorite route setups

The current live-search logic still supports only `direct` rides. No transfers are computed yet.

## Terminology

- `Complication`: the small widget on the watch face
- `Tile`: the swipeable Wear OS panel opened from the watch face
- `Activity`: the full-screen app screen

## Current UX

### Activity

The activity opens on the live departures board.

Visible immediately:

- current system clock on the curved left edge
- selected route header
- next direct departures
- refresh button

Above the main board, available by swiping down:

- settings button
- auto-updates toggle
- route setup launcher

### Route Setup

The route setup overlay is the main configuration flow for the watch.

Home step:

- current origin
- current destination
- current line or `Any line`
- favorite toggle
- `Apply route`
- favorite routes list for one-tap reuse

Focused sub-pages:

- stop search
- platform picker
- line search

The stop and line search is local and suggestion-based. Users type into a search field and the app filters a pre-fetched local catalog.

When multiple station groups share the same public name, search cards are disambiguated by platform summary, for example:

- `Platforms 1, 2`
- `Platforms A, B, C +7`

Nearby same-name station groups are merged into one search result before display, so interchanges like `Želivského` show one combined card containing all nearby metro and surface platforms.

### Favorites

Favorites are stored locally and shown on the route-setup home screen.

Current behavior:

- `Save favorite` adds the current route setup
- tapping a favorite applies it immediately
- favorites are de-duplicated by origin, destination, platform filters, and line
- favorites are capped at `8`

## Route Model

The persisted route configuration is `RouteSelection`:

- `origin: StopSelection`
- `destination: StopSelection`
- `line: LineSelection?`

`StopSelection` stores:

- station key
- station name
- optional platform key
- optional platform label
- resolved stop IDs used for API queries

This lets the app keep working even if the static stop catalog is temporarily unavailable, because live lookups can still use the stored GTFS stop IDs.

## API Usage

The app uses the local Golemio OpenAPI specs:

- `golemio-pid-openapi.json`
- `golemio-openapi.json`

### Static Catalog: `/v2/gtfs/stops`

Purpose:

- stop suggestions while typing
- station grouping
- optional platform picker

Request pattern:

- `GET /v2/gtfs/stops?limit=10000&offset=0`
- repeat with increasing `offset`
- stop when returned page size is smaller than `10000`

Reason:

- Prague currently has more than `10,000` GTFS stops, so one request is not enough

Response shape:

- top-level JSON object
- `features[]`
- each item uses `properties`

Fields used from `properties`:

- `stop_id`
- `stop_name`
- `platform_code`
- `parent_station`
- `asw_id.node`

How the app uses them:

- sanitizes API placeholder values like literal `"null"` before indexing
- collapses the full `parent_station` chain to one root station
- exposes platform filters as a second-step choice
- stores all resolved stop IDs behind `Any platform`

Station grouping rule:

1. top-most `parent_station` chain anchor
2. dominant hierarchy anchor within the same `asw_id.node`
3. normalized stop name fallback

After that first grouping step, nearby groups with the same normalized public name are merged using stop coordinates. This avoids duplicate watch search results for one interchange while keeping far-away same-name stops separate.

Station naming rule:

1. prefer the shallowest non-empty stop name in the grouped hierarchy
2. fall back to the best public name seen on the same `asw_id.node`
3. finally fall back to `stop_id` if no better name exists

This avoids showing intermediary technical labels like `Kolej 1 - Start` or literal `null` when the root GTFS station record is unnamed.

Only non-empty, non-`null` `platform_code` values become explicit platform choices. If a station has no usable platform labels, the UI offers only `Any platform`.

### Static Catalog: `/v2/gtfs/routes`

Purpose:

- line suggestions while typing
- optional line filter

Request pattern:

- `GET /v2/gtfs/routes?limit=10000&offset=0`
- repeat if necessary

Fields used:

- `route_short_name`
- `route_long_name`
- `route_type`

The app groups by `route_short_name`, because direct live filtering only needs the short public line label.

### Local Search

Search is intentionally local, not API-driven.

Why:

- avoids rate-limit pressure while typing
- makes suggestions instant after the catalog is cached
- works better on a watch than round-tripping every character

Normalization rules:

- ignore case
- ignore accents
- collapse repeated whitespace
- treat punctuation as separators

Examples:

- `Nadrazi Vrsovice` matches `Nádraží Vršovice`
- `pal mov ka` still matches `Palmovka`

### Live Departures: `/v2/pid/departureboards`

Purpose:

- discover what can be boarded now from the selected origin stop set

Request pattern:

- `ids[]` repeated for every selected origin stop ID
- `timeFrom`
- `minutesAfter=120`
- `limit`
- `order=real`

Important detail:

- when `Any platform` is selected, `ids[]` contains all stop IDs for the station
- when a platform is selected, `ids[]` contains only that platform's stop IDs

Fields used from each `departures[]` item:

- `route.short_name`
- `stop.id`
- `trip.id`
- `trip.is_canceled`
- `departure_timestamp.scheduled`
- `departure_timestamp.predicted`
- `arrival_timestamp.scheduled`
- `arrival_timestamp.predicted`
- `delay.seconds`

How the app uses them:

- line filter is applied only when a line is configured
- otherwise all direct candidate departures are allowed through
- timestamp fields are sanitized before parsing so API `"null"` values do not crash the live board

### Trip Verification: `/v2/gtfs/trips/{id}`

Purpose:

- confirm the departure reaches the chosen destination directly

Request options:

- `date=YYYY-MM-DD`
- `includeStops=true`
- `includeStopTimes=true`
- `includeRoute=true`

Fields used:

- `stop_times[].stop_id`
- `stop_times[].arrival_time`
- `stop_times[].departure_time`

How it works:

1. find the boarded source stop occurrence in `stop_times[]`
2. search only later stops
3. accept the trip if any downstream `stop_id` matches the selected destination stop set

Destination matching behavior:

- selected destination platform -> exact stop IDs for that platform
- `Any platform` -> any stop ID grouped under that destination station

### Vehicle Live Data: `/v2/vehiclepositions/{gtfsTripId}`

Purpose:

- fresher delay
- realtime cancellation flag
- raw vehicle `origin_timestamp` for the details dialog

Fields used:

- `properties.last_position.delay.actual`
- `properties.last_position.is_canceled`
- `properties.last_position.origin_timestamp`

If the endpoint returns `404`, the app treats it as "no live vehicle position available" and falls back to departure-board data.

## Timing Resolution

`DepartureTimingResolver` resolves one final departure timestamp with this priority:

1. `scheduled + vehiclepositions.delay.actual`
2. `departure_timestamp.predicted`
3. `departure_timestamp.scheduled`

Displayed delay is resolved with this priority:

1. `vehiclepositions.delay.actual`
2. `departureboards.delay.seconds`
3. derived `predicted - scheduled`
4. `0`

This avoids double-counting delay.

## Caching

### Live caches

Configurable in settings:

- live snapshot cache: default `2 s`
- GTFS trip detail cache: default `1 min`
- vehicle position cache: default `2 s`

Purpose:

- live snapshot cache prevents duplicate full refreshes when tile/activity/complication ask almost at once
- trip detail cache reduces repeated `/v2/gtfs/trips/{id}` calls and lowers rate-limit risk
- vehicle cache avoids duplicate short-interval live position lookups and briefly caches `404` misses

### Static catalog cache

The stop and line catalog is cached separately:

- stored on disk in app-private storage
- kept in memory after load
- refreshed at most once per day
- prefetched in the background when the activity starts

If refresh fails but a cached catalog exists, the app falls back to the cached catalog.

## Refresh Behavior

- activity auto-refresh: aligned to wall-clock `:00` and `:30`
- details dialog auto-refresh: every `10 s` while open and enabled
- tile freshness interval: requested `30 s`
- complication update period: requested `30 s`

Wear OS still controls when tiles and complications are actually redrawn.

## Complications

Two complication providers are exposed:

- `Transport Tracker (Minutes)`
- `Transport Tracker (Stopwatch)`

Both are timeline-based:

- countdown keeps moving locally between provider calls
- future handoff points for departures `2` and `3` are preloaded
- stale dot appears after `30 s` without fresh data

Stale means:

- the snapshot is older than `30 s`
- or the repository already marked it stale because data is paused/fallback

The stopwatch complication is safer when the exact remaining seconds matter.

## Tile

The tile shows:

- destination on top
- configured line or `Any direct line`
- next `3` departures

When the route is `Any line`, each row includes the live line short name.

## Activity Details Dialog

Tapping a departure shows:

- line
- destination arrival
- departure-board:
  - departure scheduled
  - departure predicted
  - board delay
  - origin arrival scheduled
  - origin arrival predicted
- vehicle position:
  - delay
  - `origin_timestamp`

## Key Files

- `wear/src/main/java/com/example/routetracker/data/RouteRepository.kt`
- `wear/src/main/java/com/example/routetracker/data/TransitCatalogRepository.kt`
- `wear/src/main/java/com/example/routetracker/data/TransitCatalogBuilder.kt`
- `wear/src/main/java/com/example/routetracker/data/SearchNormalizer.kt`
- `wear/src/main/java/com/example/routetracker/presentation/MainActivity.kt`
- `wear/src/main/java/com/example/routetracker/presentation/RouteSetupOverlay.kt`
- `wear/src/main/java/com/example/routetracker/tile/MainTileService.kt`
- `wear/src/main/java/com/example/routetracker/complication/BaseCountdownComplicationService.kt`
- `wear/src/main/java/com/example/routetracker/data/ComplicationTimelinePlanner.kt`

## Build And Test

Build:

```powershell
./gradlew :wear:assembleDebug
```

Unit tests:

```powershell
./gradlew :wear:testDebugUnitTest
```

## Known Environment Quirk

On this machine, Kotlin daemon startup fails with `AccessDeniedException` under:

- `C:\Users\Hladc\AppData\Local\kotlin\daemon`

Gradle falls back to non-daemon Kotlin compilation and the build still succeeds, but compiles are slower and look like they hang for a while.
