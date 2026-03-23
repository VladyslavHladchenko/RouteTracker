# Core Guidance

Read this file first for most Android or Wear OS app work.

## Best-Practice Defaults

- Start from the official Wear design and app-quality docs before changing visible UI.
- Prefer Compose for Wear OS for new UI work.
- Prefer Wear Material 3 over legacy Wear Material where feasible.
- Check the Jetpack release pages before changing dependencies. Versions move independently across `compose-foundation`, `compose-material3`, and related libraries.
- Treat round-screen safety, swipe-dismiss behavior, rotary input, and glanceability as baseline requirements, not polish items.
- If the task is Samsung-specific, read `references/samsung-galaxy-watch.md` after this file instead of mixing vendor assumptions into general Wear guidance.

## UI/UX Rules That Matter Most

- Design for quick, glanceable interactions. Watch flows should complete in seconds, not feel like shrunken phone screens.
- Keep primary actions legible and reachable near the thumb arc; avoid stuffing secondary actions into already-tight layouts.
- Use responsive padding instead of hard-coded phone-style gutters so content survives both small and large round screens.
- Avoid text-heavy screens. Prioritize one clear purpose per screen.
- Preserve system idioms: `TimeText`, scroll indicators, swipe-to-dismiss, and rotary scrolling where appropriate.
- Review both clipped edges and oversized vertical stacks. Watch UIs fail both ways.

## Compose Implementation Defaults

- Prefer `AppScaffold` plus `ScreenScaffold` for Material 3 Compose layouts.
- Prefer `SwipeDismissableNavHost` for navigation.
- Prefer `TransformingLazyColumn` for primary scrolling lists.
- Use Wear-specific list padding helpers such as `rememberResponsiveColumnPadding()` where available in the chosen library set.
- Prefer Wear components over mobile `androidx.compose.material` equivalents on watch screens.

## Accessibility And Input

- Support rotary input for scrollable and picker-style surfaces when the flow benefits from it.
- Provide semantics and `contentDescription` values for non-text affordances.
- Preserve readable typography under scaling. Re-check clipping with larger fonts.
- Keep touch targets watch-appropriate and avoid forcing tiny icon-only actions into the bezel.

## Quality Gotchas From The Current Wear Checklist

- Use a black background for apps and tiles unless a stronger official surface-specific rule applies.
- Keep essential text at 12sp or larger, and non-essential text at 10sp or larger.
- Ensure no text or controls are cut off by screen edges and that layouts fit at least a 192dp circle.
- Do not require username or password entry directly on the watch.
- If the app includes tiles, include tile previews for the tile manager and Play-facing assets where required.

## Performance And Battery

- Performance regressions are easier to hide on debug builds. Measure release-like builds when perf matters.
- Baseline profiles, startup profiles, Macrobenchmark, and JankStats are relevant for non-trivial Wear apps.
- Re-test key user journeys after migrating to Material 3 Expressive or introducing richer motion.
- Validate on physical devices when making performance claims.

## High-Signal Official Pages

- Wear design hub: https://developer.android.com/design/ui/wear
  Why: central UX, layouts, components, style, and surface guidance.
- Design for wearables: https://developer.android.com/design/ui/wear/guides/get-started/design-for-wearables
  Why: current entry point for watch-first design constraints, including round-screen tradeoffs.
- UX design principles for wearables: https://developer.android.com/design/ui/wear/guides/get-started/design-for-wearables/principles
  Why: establishes glanceable, task-focused watch-first interaction goals.
- Principles of Wear OS development: https://developer.android.com/training/wearables/principles
  Why: development-side guidance for how watch apps differ from phone apps.
- Compose for Wear OS overview: https://developer.android.com/training/wearables/compose
  Why: current Compose entry point and navigation to lists, navigation, performance, rotary input, and migration docs.
- Compose screen sizes on Wear: https://developer.android.com/training/wearables/compose/screen-size
  Why: current guidance for round/square sizing and safe layout decisions.
- Compose navigation on Wear: https://developer.android.com/training/wearables/compose/navigation
  Why: canonical swipe-dismiss navigation behavior and APIs.
- Rotary input with Compose: https://developer.android.com/training/wearables/compose/rotary-input
  Why: essential for crown/bezel interaction and scroll ergonomics.
- Migrate to Material 3 in Compose for Wear OS: https://developer.android.com/training/wearables/compose/migrate-to-material3
  Why: documents `AppScaffold`, `ScreenScaffold`, `TransformingLazyColumn`, and current M3 migration patterns.
- Accessibility on Wear OS: https://developer.android.com/training/wearables/accessibility
  Why: watch-specific accessibility expectations beyond generic Android advice.
- Wear OS app quality: https://developer.android.com/docs/quality-guidelines/wear-app-quality
  Why: policy-adjacent quality checklist for UI, accessibility, battery, and Play readiness.
- Compose performance on Wear OS: https://developer.android.com/training/wearables/compose/performance
  Why: current performance guidance for baseline profiles, benchmarking, release-mode measurement, and Material 3 Expressive costs.
- Wear Compose Material 3 release page: https://developer.android.com/jetpack/androidx/releases/wear-compose-m3
  Why: authoritative current version and migration status for Wear Material 3.
- Wear Compose release page: https://developer.android.com/jetpack/androidx/releases/wear-compose
  Why: authoritative status for legacy Wear Compose Material artifacts and deprecation context.

## Volatile Facts To Re-Check Live

- As of 2026-03-22, the Wear Compose release page shows `androidx.wear.compose:compose-material` stable `1.5.6` dated March 11, 2026, and marks it as superseded by `compose-material3`.
- As of 2026-03-22, the Wear Compose Material 3 release page shows stable `1.5.0` dated August 27, 2025.

Do not assume those versions remain current. Verify live before editing dependencies or writing upgrade advice.
