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

The wear app uses Golemio endpoints:

- `/v2/pid/departureboards`
- `/v2/gtfs/trips/{tripId}`
- `/v2/vehiclepositions/{tripId}`

High-level route-finding flow:

1. Read live departures for the selected origin stop.
2. Filter to line `7`.
3. Load GTFS trip detail for each candidate.
4. Verify that the trip reaches the selected destination stop directly.
5. Enrich the result with delay and cancellation data when available.
6. Return the next `3` matching trips.

## Surface Behavior

### Complication

The complication shows a single number only:

- `live countdown in minutes`

That value is:

- `minutes until departure + positive/negative delay`, clamped to `0`

Example:

- scheduled in `10` minutes with delay `+1` -> complication shows `11`

The complication does not currently show delay as a separate badge.

Important note:

- Watch-face layout is controlled by the watch face, not fully by the app.
- The app supplies data, but exact placement is watch-face-dependent.

### Tile

The tile shows:

- selected direction label
- next `3` departures

Each departure row is currently formatted as:

- `HH:mm  N min  +D`

Where:

- `HH:mm` = departure clock time
- `N min` = live countdown in minutes
- `+D` = delay, only shown when delay is positive

Example:

- `21:03  14 min`
- `21:13  24 min  +2`

### Activity

The activity opens on the main departures view.

It shows:

- a header card with:
  - line number
  - selected destination
  - live/cached/paused status
- a direction selector
- the next departures as cards
- a manual refresh button

The `Auto updates` control is placed above the main view in the scrollable list, so it should be reachable by swiping down from the initial activity position.

At the very top there is also a small `Settings` icon button.

Tapping it opens a settings dialog with:

- `Show seconds: On/Off`

When enabled:

- departure clock times use `HH:mm:ss`
- the activity header `last updated` time uses `HH:mm:ss`
- tile departure clock times use `HH:mm:ss`

Each departure card shows:

- first line: departure clock time
- second line: live countdown text
- optional delay text

Example:

- `23:26`
- `In 7 min`

Or:

- `23:26`
- `In 7 min  Delay +1 min`

## What The Numbers Mean

### Complication number

- `live minutes until departure`
- uses delay-adjusted countdown

### Tile number

- `live minutes until departure`
- delay shown separately as `+D`

### Activity list number

- `live minutes until departure`
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

The repository keeps a short in-memory cache:

- cache window: `2 seconds`

Purpose:

- prevents duplicate API calls when tile, complication, and activity request data almost at the same time

### Automatic refresh

Current refresh configuration:

- activity auto-refresh: aligned to wall-clock `:00` and `:30` seconds while the app is open
- tile freshness interval: `30 seconds`
- complication requested update period: `30 seconds`

Note:

- when the app is open, each aligned activity refresh also triggers best-effort tile and complication refresh requests
- complication refresh timing is still system-controlled by Wear OS
- `30 seconds` is a requested cadence, not a hard guarantee

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
- `wear/src/main/java/com/example/routetracker/complication/MainComplicationService.kt`
  - complication provider
- `wear/src/main/java/com/example/routetracker/tile/MainTileService.kt`
  - tile UI
- `wear/src/main/java/com/example/routetracker/presentation/MainActivity.kt`
  - full-screen watch UI
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
