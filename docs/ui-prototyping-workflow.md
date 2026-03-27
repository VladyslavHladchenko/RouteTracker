# UI Prototyping Workflow

This repository uses a prototype-first workflow for all visible Wear UI work.

Scope:

- `wear` activity screens
- Wear tile
- Wear complication

The `mobile` module keeps building, but it is outside this workflow for now.

## Tooling

Required for UI contributors:

- Android Studio Panda 2 `2025.3.2` or newer

Recommended:

- Physical Galaxy Watch7 for touch-first validation

Optional:

- Figma for human-edited visual references or alternate design exploration
- Wear OS emulator when hardware is unavailable

Not used in this repo for screenshot baselines:

- `com.android.compose.screenshot`

Roborazzi remains the only committed screenshot-baseline system.

## Required Flow

1. Build or update a previewable Wear surface from fake state first.
2. Review the surface locally in Android Studio using:
   - Compose Preview
   - UI Check
   - Run Preview
   - Live Edit when motion or touch behavior matters
3. Use Figma only when it adds value for human review or alternate design exploration.
4. Update screenshot baselines and tests when the visual output changes.
5. Open or update the PR with:
   - touched Wear surfaces
   - embedded screenshots for visual changes
   - validation performed
   - exact Figma frame link when Figma was part of the review

## Surface Inventory

Use the same shared surface inventory across previews, screenshots, and optional Figma frames:

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

Preview functions and screenshot files should use the same `surface_state` naming. Optional Figma frames should match when used, for example:

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

For visual changes:

- update Roborazzi coverage when a new visual state is introduced
- wait for `Wear Screenshot Record` to pass for the PR head
- download and review the screenshot artifact
- embed the relevant screenshots in the PR description
