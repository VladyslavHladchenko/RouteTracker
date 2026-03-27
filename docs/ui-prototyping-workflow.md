# UI Prototyping Workflow

This repository uses a prototype-first workflow for all visible Wear UI work.

Scope:

- `wear` activity screens
- Wear tile
- Wear complication

The `mobile` module keeps building, but it is outside this workflow for now.

## Tooling

Default workflow:

- GitHub pull requests plus CI screenshot artifacts

Recommended:

- Physical Galaxy Watch7 for touch-first validation

Optional:

- Android Studio Panda 2 `2025.3.2` or newer for local Compose Preview, UI Check, Run Preview, and Live Edit
- Wear OS emulator when hardware is unavailable

Not used in this repo for screenshot baselines:

- `com.android.compose.screenshot`

Roborazzi remains the only committed screenshot-baseline system.

## Required Flow

1. Build or update a previewable Wear surface from fake state first.
2. Open or update a draft PR and use CI screenshot artifacts as the primary review surface.
3. Iterate on the fake-state surface and screenshot outputs until the visuals are right.
4. Use Android Studio locally only when Compose Preview, UI Check, Run Preview, or Live Edit will save time.
5. Mark the PR ready for review when full validation should run.
6. Update screenshot baselines and tests when the visual output changes.
7. Open or update the PR with:
   - touched Wear surfaces
   - embedded screenshots for visual changes
   - validation performed

## PR States

- Draft PRs are the default state for visible Wear UI prototyping.
- Draft PRs keep `Wear Screenshot Record` active, reduce `Android CI` to smoke validation, and skip `Wear UI Tests`.
- Ready-for-review PRs run the full required validation set before merge.

## Surface Inventory

Use the same shared surface inventory across previews and screenshots:

- `board`
- `settings`
- `api-key`
- `quick-switch`
- `route-setup`
- `station-search`
- `platform-picker`
- `line-search`
- `trip-details`
- `tile`
- `complication`

Preview functions and screenshot files should use the same `surface_state` naming, for example:

- `board_loading`
- `quick_switch_empty`
- `trip_details_vehicle_unavailable`

## Code Structure Expectations

- Keep stateful orchestration in the container screens.
- Keep previewable surfaces free of repository, network, and context-bound formatting work.
- Use shared fake data from `wear/src/main/java/com/example/routetracker/presentation/preview/`.
- Keep a discoverable preview entry point for every visible Compose surface.
- Use sanctioned preview paths for non-Compose surfaces:
  - tile preview functions in the tile service
  - complication `getPreviewData()` plus screenshots
- Reuse the same fake data in previews and screenshot tests.

## Validation

CI stays the same:

- `Android CI`
- `Wear Screenshot Record`
- `Wear UI Tests`

PR state changes how those checks behave:

- draft PRs: screenshot-first review with reduced CI cost
- ready-for-review PRs: full merge validation

For visual changes:

- update Roborazzi coverage when a new visual state is introduced
- wait for `Wear Screenshot Record` to pass for the PR head
- download and review the screenshot artifact
- embed the relevant screenshots in the PR description
