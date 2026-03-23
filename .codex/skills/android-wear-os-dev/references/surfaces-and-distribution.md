# Surfaces And Distribution

Read this file only when the task touches tiles, complications, watch faces, or Wear packaging behavior.

## Tiles

- Treat tiles as glanceable, low-friction entry points, not mini apps.
- Keep copy short, value immediate, and tap targets obvious.
- Prefer content that remains useful with partial or slightly stale data.
- Re-check tile-specific design guidance before inventing layouts.

Official pages:

- Build tiles: https://developer.android.com/training/wearables/tiles/get_started
- Tiles design hub: https://developer.android.com/design/ui/wear/guides/surfaces/tiles
- Best practices for tiles: https://developer.android.com/design/ui/wear/guides/surfaces/tiles/bestpractices

## Complications

- Use a complication data source only when the information is useful from the watch face itself.
- Return clean raw values and metadata; the watch face formats the display.
- Provide preview data and accessibility metadata.
- Keep update behavior and battery cost in mind.

Official pages:

- Expose data to complications: https://developer.android.com/training/wearables/complications/exposing-data
- Watch face and complications APIs: https://developer.android.com/training/wearables/watch-faces

## Watch Faces

- Treat watch faces as a separate product surface with stricter visual, battery, and platform constraints than standard apps.
- Re-check current platform requirements before recommending an implementation path.
- If the task is code-first, prefer the current official Watch Face Format guidance over older bespoke engine advice unless the docs explicitly require otherwise.

Official pages:

- Build watch faces: https://developer.android.com/training/wearables/watch-faces/
- Watch face design guide: https://developer.android.com/design/ui/wear/guides/m2-5/surfaces/watch-faces
- Wear OS app quality: https://developer.android.com/docs/quality-guidelines/wear-app-quality

Volatile fact to re-check live:

- As of 2026-03-22, the watch face docs state that Watch Face Format is required for installing watch faces on all Wear OS devices as of January 2026.

## Standalone Versus Companion-Dependent Apps

- Prefer standalone behavior whenever the core use case can work on watch alone.
- If the watch app depends on the phone, model that explicitly and handle the missing-companion state in the UX.
- Do not assume the phone app is already installed.
- Be careful with Play metadata and manifest declarations for standalone status.

Official page:

- Standalone versus non-standalone Wear OS apps: https://developer.android.com/training/wearables/apps/standalone-apps
