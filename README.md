# RouteTracker

Wear OS app for monitoring the next direct tram departures on Prague line `7` between two hardcoded platform pairs.

The project currently focuses on the `wear` module. The `mobile` module exists in the repo, but the active feature work is in the watch app.

## What It Does

- Fetches the next `3` direct departures for line `7`
- Supports two hardcoded directions
- Shows departures in:
  - a Wear OS `tile`
  - a watch-face `complication`
  - the full-screen app `activity`
- Lets the user switch direction on-watch
- Supports pausing and resuming automatic data updates
- Supports a `Show seconds` display setting
- Supports a `Details auto-refresh` setting for the open departure dialog

## Terminology

- `Complication`: the small widget shown on the watch face
- `Tile`: the swipeable Wear OS panel opened from the watch face
- `Activity`: the full-screen app opened after tapping the tile or complication

## Hardcoded Route Configuration

Current route pairs:

- `Palmovka -> Nadrazi Vrsovice`
  - source: `U529Z1P`
  - destination: `U463Z1P`
- `Nadrazi Vrsovice -> Palmovka`
  - source: `U463Z2P`
  - destination: `U529Z2P`

Configured line:

- line short name: `7`

Configured result count:

- always show the next `3` departures

These values are defined in the wear app and are intentionally hardcoded for now.

## Data Source

The wear app uses Golemio endpoints described by the local OpenAPI specs:

- `golemio-pid-openapi.json`
- `golemio-openapi.json`

The current implementation is interested in only a small subset of each response.

### `/v2/pid/departureboards`

Response format:

- JSON object: `PIDDepartureBoard`
- top-level keys:
  - `stops[]`
  - `departures[]`
  - `infotexts[]`

The app uses `departures[]`. Each departure item is a JSON object with these relevant nested objects:

- `route`
  - `short_name: string`
  - `type: number`
- `stop`
  - `id: string`
  - `platform_code: string|null`
- `trip`
  - `id: string`
  - `headsign: string`
  - `is_canceled: boolean`
- `departure_timestamp`
  - `predicted: string` in ISO datetime format
  - `scheduled: string` in ISO datetime format
  - `minutes: string`
- `arrival_timestamp`
  - `predicted: string` in ISO datetime format
  - `scheduled: string` in ISO datetime format
  - `minutes: string`
- `delay`
  - `seconds: number|null`
  - `minutes: number|null`
  - `is_available: boolean`

How the app uses it:

- reads `route.short_name` and keeps only line `7`
- reads `stop.id` and keeps only the currently selected source stop
- reads `trip.id` as the stable ID for follow-up GTFS and vehicle queries
- reads both `departure_timestamp.scheduled` and `departure_timestamp.predicted`
- reads `arrival_timestamp.scheduled` and `arrival_timestamp.predicted` as the vehicle's board arrival to the origin stop when available
- treats `predicted` as the board-provided stop-specific expected departure time when vehicle-position delay is not available
- reads `delay.seconds` as the board-level delay fallback
- reads `trip.is_canceled` as the initial cancellation flag

Data returned but not used by the app:

- `stops[]`
- `infotexts[]`
- most route metadata such as `is_night`
- most trip metadata such as `headsign`, `is_at_stop`, wheelchair info

### `/v2/gtfs/trips/{id}`

Request options used by the app:

- `date=YYYY-MM-DD`
- `includeStops=true`
- `includeStopTimes=true`
- `includeRoute=true`

Response format:

- JSON object based on `GTFSTrip`
- enriched with optional objects/arrays such as:
  - `stop_times[]`
  - `route`
  - `service`
  - `shapes[]`

The app uses `stop_times[]`. Each stop time item is a JSON object with these relevant fields:

- `stop_id: string`
- `arrival_time: string` in GTFS service-time format `HH:MM:SS`
- `departure_time: string` in GTFS service-time format `HH:MM:SS`
- `stop_sequence: number`
- `trip_id: string`

How the app uses it:

- finds the boarded occurrence of the source stop using `stop_id` plus the closest matching `departure_time`
- searches only downstream `stop_times[]` entries after boarding
- checks whether the selected destination `stop_id` appears later in the same trip
- reads the destination `arrival_time` to know when the direct ride should arrive
- combines the destination `arrival_time` with the resolved realtime delay to show an activity-level arrival timestamp

Data returned but not used by the app:

- `shapes[]`
- `service`
- most base trip metadata such as `route_id`, `shape_id`, `trip_headsign`
- most route metadata even though `includeRoute=true` is requested

### `/v2/vehiclepositions/{gtfsTripId}`

Response format:

- GeoJSON-like feature object
- top-level keys include:
  - `type`
  - `geometry`
  - `properties`

The app uses `properties.last_position`. Relevant nested fields are:

- `delay.actual: number|null`
- `is_canceled: boolean|null`
- `origin_timestamp: string` in ISO datetime format

How the app uses it:

- if present, `properties.last_position.delay.actual` becomes the preferred delay source
- when that preferred delay exists, the app rebuilds the displayed departure time as `scheduled + delay.actual`
- if present, `properties.last_position.is_canceled` replaces the older departure-board cancellation flag
- if the endpoint returns `404`, the app treats that as "no live vehicle position available" and keeps the departure-board values

Data returned but not used by the app:

- `geometry`
- `trip`
- `all_positions`
- `bearing`, `speed`, `tracking`, `next_stop`, `last_stop`

### Internal App Model

The timing merge is handled by `DepartureTimingResolver` in `wear/src/main/java/com/example/routetracker/data/DepartureTiming.kt`.

It resolves one final departure timestamp using this priority:

1. `scheduled + vehiclepositions.properties.last_position.delay.actual`
2. `departure_timestamp.predicted`
3. `departure_timestamp.scheduled`

Separately, it resolves the displayed delay using this priority:

1. `vehiclepositions.properties.last_position.delay.actual`
2. `departureboards.delay.seconds`
3. derived value from `predicted - scheduled`
4. `0`

After that merge, the repository converts the result into an internal `RouteDeparture` object with:

- `tripId: string`
- `departureTime: ZonedDateTime`
- `countdownMinutes: int`
- `delayMinutes: int`
- `destinationArrivalTime: ZonedDateTime?`
- `departureBoardDetails`
  - `departureTime`
    - `scheduledTime: ZonedDateTime`
    - `predictedTime: ZonedDateTime|null`
  - `originArrivalTime`
    - `scheduledTime: ZonedDateTime`
    - `predictedTime: ZonedDateTime|null`
  - `delaySeconds: Int?`
- `vehiclePositionDetails`
  - `delaySeconds: Int?`
  - `originTimestamp: ZonedDateTime?`

### High-Level Route-Finding Flow

1. Fetch the departure board for the selected source stop.
2. From `departures[]`, keep only items where `route.short_name == "7"` and `stop.id` matches the selected platform.
3. Parse `departure_timestamp.scheduled` and `departure_timestamp.predicted` into Prague `ZonedDateTime` values.
4. Fetch `/v2/gtfs/trips/{tripId}` for that departure.
5. In `stop_times[]`, find the boarded source stop occurrence.
6. Search later `stop_times[]` entries for the selected destination stop ID.
7. Fetch `/v2/vehiclepositions/{gtfsTripId}` when available.
8. Prefer `last_position.is_canceled` over the older board value.
9. Resolve one final departure time and one final delay value with `DepartureTimingResolver`.
10. Build up to the next `3` direct departures for the watch surfaces.

## Surface Behavior

### Complication

The app now exposes two watch-face complications:

- `Transport Tracker (Minutes)`
  - minute-oriented countdown using Wear OS short single-unit rendering
- `Transport Tracker (Stopwatch)`
  - stopwatch-style countdown intended for "don't miss the tram" use cases

Both complication providers show the next departure as a self-updating countdown:

- main text:
  - countdown to the resolved next departure time
  - provided as a time-based complication text, so the watch face can keep counting down locally between API refreshes
- small secondary marker:
  - hidden when fresh
  - tiny `•` dot when stale

The stale marker means:

- the complication is showing data from a snapshot older than `30 seconds`
- or the repository already marked the snapshot stale because it had to fall back to cached/paused data

The complication timeline also preloads the next known departures, so when the current tram passes the watch face can switch to the next one without waiting for a new network fetch.

Important notes:

- the watch face controls final rendering, so exact placement is watch-face-dependent
- text color is also watch-face-controlled, so the status marker is a monochrome dot rather than a guaranteed green/red dot
- the stale threshold is exactly `30 seconds` in app logic, but the visible flip still depends on when the watch face redraws

Examples:

- resolved next departure in `11` minutes -> complication counts down to that departure locally
- once the snapshot becomes older than `30 seconds`, a small stale dot appears
- when the first departure time is reached, the timeline switches to the second departure if one is available

### How Complication Updates Work

Each time Wear OS requests complication data, the app builds a fresh `ComplicationDataTimeline` from the current snapshot.

That timeline contains:

- the current next departure
- future handoff points for departure `2` and `3`
- the future point when the stale dot should appear if no newer snapshot replaces it

After the timeline is returned, the watch face can keep using that already-delivered timeline locally.

That means:

- the countdown can keep changing even when the app is not called again
- the complication can switch from one departure to the next using the cached timeline
- Wear OS may still be showing an older timeline until it asks the app for a newer one

Important consequence:

- complication timeline updates are not guaranteed exactly every `30 seconds`
- `UPDATE_PERIOD_SECONDS=30` is only the app's requested cadence
- the stale dot exists to show that the currently displayed timeline is based on data older than `30 seconds`

### Tile

The tile shows:

- selected direction label
- next `3` departures

Each departure row is currently formatted as:

- `HH:mm  N min  +D`

Where:

- `HH:mm` = departure clock time
- `N min` = countdown in minutes to the resolved departure time
- `+D` = delay, only shown when delay is positive

Example:

- `21:03  14 min`
- `21:13  24 min  +2`

### Activity

The activity opens on the main departures view.

It shows:

- an always-visible system clock overlay in the activity gutter
- a header card with:
  - line number
  - selected destination
  - live/cached/paused status
- a direction selector
- the next departures as cards
- a manual refresh button

Tapping a departure card opens a details dialog.

That dialog shows the captured API detail fields for the selected item:

- `departureboards`
  - `departure_timestamp.scheduled`
  - `departure_timestamp.predicted`
  - `delay`
  - `arrival_timestamp.scheduled`
  - `arrival_timestamp.predicted`
- `trip detail`
  - `destination arrival`
- `vehiclepositions`
  - `delay`
  - `origin_timestamp`

The `Auto updates` control is placed above the main view in the scrollable list, so it should be reachable by swiping down from the initial activity position.

At the very top there is also a small `Settings` icon button.

Tapping it opens a settings dialog with:

- `Show seconds: On/Off`
- `Details auto-refresh: On/Off`
- `Live snapshot` cache duration
- `Trip detail` cache duration
- `Vehicle live` cache duration

When enabled:

- departure clock times use `HH:mm:ss`
- the activity header `last updated` time uses `HH:mm:ss`
- the always-visible system clock uses `HH:mm:ss` and ticks live each second
- activity departure rows switch from floored `In X min` text to live `In X min YY s` / `In YY s` text based on the current system clock
- tile departure clock times use `HH:mm:ss`

When `Details auto-refresh` is enabled:

- an open departure details dialog refreshes every `10 seconds`
- the dialog reuses the latest matching `tripId` from the refreshed snapshot when available
- this only runs while the global `Auto updates` switch is on

Current default cache settings are:

- live snapshot cache: `2 s`
- GTFS trip detail cache: `1 min`
- vehicle position cache: `2 s`

Each departure card shows:

- first line: departure clock time
- second line: countdown text to the resolved departure time
- optional delay text

Example:

- `23:26`
- `In 7 min`

Or:

- `23:26`
- `In 7 min  Delay +1 min`

## What The Numbers Mean

### Complication number

- `time remaining until the resolved departure time`
- rendered by the watch face from the stored target departure timestamp
- never adds the same delay twice

Important nuance for the minutes complication:

- the activity and tile currently floor countdowns to whole minutes
- the minutes complication uses Wear OS `TimeDifferenceComplicationText` with minute granularity
- that means partial minutes can round up on the watch face

Example:

- if the real remaining time is `13 min 05 s`
  - activity/tile may show `13`
  - the minutes complication may show `14M`

The stopwatch complication avoids that specific rounding issue by showing a stopwatch-style live countdown instead.

### Tile number

- `minutes until the resolved departure time`
- delay shown separately as `+D`

### Activity list number

- `minutes until the resolved departure time`
- delay shown separately as text

### Header time in the activity

The time in the header status line is not a departure time.

It is:

- `last successful snapshot update time`

Examples:

- `Live | 23:18`
- `Cached | 23:18`
- `Paused | 23:18`

## Refresh, Caching, And Updates

### Cache

The repository keeps three separate in-memory caches:

- live snapshot cache: `2 seconds`
- GTFS trip detail cache: `1 minute`
- vehicle position cache: `2 seconds`

Purpose:

- live snapshot cache:
  - prevents duplicate full refreshes when tile, complication, and activity request data almost at the same time
- GTFS trip detail cache:
  - reduces repeated `/v2/gtfs/trips/{id}` requests for the same trip during frequent refreshes
  - lowers the chance of API rate limiting on the static trip-detail endpoint
- vehicle position cache:
  - prevents duplicate short-interval `/v2/vehiclepositions/{gtfsTripId}` requests
  - also caches `404 not found` results briefly so missing live positions are not retried immediately

These cache durations are configurable from the settings dialog under `Cache`.

### Automatic refresh

Current refresh configuration:

- activity auto-refresh: aligned to wall-clock `:00` and `:30` seconds while the app is open
- details dialog auto-refresh: every `10 seconds` while the dialog is open and the setting is enabled
- tile freshness interval: `30 seconds`
- complication requested update period: `30 seconds`

Note:

- when the app is open, each aligned activity refresh also triggers best-effort tile and complication refresh requests
- complication refresh timing is still system-controlled by Wear OS
- `30 seconds` is a requested cadence, not a hard guarantee
- between complication refreshes, the complication countdown can still keep moving because it is based on a target departure timestamp rather than a frozen number

### Auto updates toggle

The activity includes an `Auto updates` toggle.

When auto updates are `On`:

- background polling continues
- tile and complication can fetch fresh data

When auto updates are `Off`:

- automatic polling stops
- tile and complication stop fetching fresh data
- cached data is shown when available
- status is shown as paused

Manual refresh still works as an explicit one-shot refresh.

## Logging

Useful log tags:

- `RouteRepository`
- `RouteTrackerTile`
- `RouteTrackerComp`
- `RouteTrackerUi`

Example Logcat filter:

```text
adb logcat RouteRepository:D RouteTrackerTile:D RouteTrackerComp:D RouteTrackerUi:D *:S
```

## Project Structure

Important files:

- `wear/src/main/java/com/example/routetracker/data/RouteRepository.kt`
  - route finding
  - API calls
  - caching
  - shared display labels
  - selected direction and auto-update preferences
- `wear/src/main/java/com/example/routetracker/data/DepartureTiming.kt`
  - pure timing resolver
  - final departure timestamp selection
  - conversion into `RouteDeparture`
- `wear/src/main/java/com/example/routetracker/complication/MainComplicationService.kt`
  - complication provider
- `wear/src/main/java/com/example/routetracker/tile/MainTileService.kt`
  - tile UI
- `wear/src/main/java/com/example/routetracker/presentation/MainActivity.kt`
  - full-screen watch UI
- `wear/src/test/java/com/example/routetracker/data/DepartureTimingResolverTest.kt`
  - unit tests for resolver priority and countdown behavior
- `wear/src/main/java/com/example/routetracker/presentation/theme/Theme.kt`
  - watch theme/colors
- `wear/src/main/AndroidManifest.xml`
  - complication update period
  - tile service registration

## Build

Typical wear build command used during development:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME='C:\Users\Hladc\AndroidStudioProjects\RouteTracker\.gradle-home'
.\gradlew.bat :wear:assembleDebug --console=plain
```

## Known Environment Issues

### Kotlin daemon warning

On this machine, Gradle often shows a Kotlin daemon startup failure like:

- `AccessDeniedException` under `C:\Users\Hladc\AppData\Local\kotlin\daemon`

When that happens:

- Gradle falls back to non-daemon Kotlin compilation
- builds still succeed
- compilation is slower and can look like it is hanging

### PowerShell profile warning

The shell may print:

- `Atuin requires the PSReadLine module to be installed.`

That is unrelated to the app itself. It is a PowerShell profile warning and only adds terminal noise.

## Current Limitations

- route configuration is hardcoded
- API token is hardcoded in the wear data layer
- only one line is supported
- only direct trips are supported
- no settings screen for choosing stops or lines
- complication layout is limited by the watch face
- API rate limiting can still happen because trip detail is fetched per candidate trip

## Good Next Improvements

- cache GTFS trip details by `tripId + service date`
- move token/configuration out of source code
- add tests for delay formatting and route matching
- improve empty/error state copy
- optionally support user-configurable lines and stop pairs
