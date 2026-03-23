---
name: android-wear-os-dev
description: Build, review, fix, or design Android apps with a Wear OS focus using current official Android guidance. Use when working on Wear OS app architecture, Compose for Wear OS screens, watch UI/UX, round-screen layouts, swipe-dismiss navigation, rotary input, accessibility, performance, tiles, complications, watch faces, or Play quality requirements.
---

# Android Wear OS Dev

Build with current official Android guidance and watch-first constraints.

Prefer official Android Developers documentation for anything versioned, policy-sensitive, or design-sensitive. Re-check release pages and quality docs when the task depends on current library versions or platform requirements.

## Quick Start

1. Classify the task:
   - app screen, Compose UI, or navigation
   - performance, accessibility, or review
   - tile, complication, or watch face
   - packaging or standalone/non-standalone behavior
2. Read [references/core-guidance.md](references/core-guidance.md) first.
3. Read [references/samsung-galaxy-watch.md](references/samsung-galaxy-watch.md) only if the task explicitly targets Samsung Galaxy Watch hardware, One UI Watch behavior, or Samsung-specific debugging/testing.
4. Read [references/surfaces-and-distribution.md](references/surfaces-and-distribution.md) only if the task touches tiles, complications, watch faces, or install/distribution behavior.
5. Browse the linked official pages again before making claims about versions, release status, or policy requirements.

## Default Technical Choices

- Prefer Compose for Wear OS Material 3 for new UI work.
- Do not mix Wear Material libraries with the mobile Material libraries on the same screen unless the official docs explicitly support it.
- Use `AppScaffold` at the activity level and `ScreenScaffold` per screen for Material 3 Compose work.
- Use `SwipeDismissableNavHost` and Wear navigation patterns for hierarchical flows.
- Use `TransformingLazyColumn` or the recommended Wear list primitives instead of phone-first list patterns.
- Design for round screens first. Protect edge content, avoid clipped actions, and use responsive padding.
- Support rotary input, font scaling, semantics, and content descriptions when the UI is interactive.
- Treat performance and battery as first-class constraints. Validate key flows on real Wear hardware when performance matters.

## Watch-First Heuristics

- Optimize for short, glanceable, high-frequency tasks.
- Keep critical actions obvious and reachable with one hand.
- Avoid dense forms and long text entry on watch.
- Minimize dependency on the phone unless the use case truly requires it.
- Favor resilient offline/cache-friendly behavior for essential watch flows.
- When reviewing UI, explicitly check round-screen clipping, scroll affordances, gesture conflicts, touch target size, and contrast.

## Validation

- For UI changes, verify behavior on round devices and multiple sizes if the project supports that.
- For accessibility-sensitive changes, verify semantics, focus order, rotary interaction, and screen reader labels.
- For performance-sensitive changes, measure release-like builds rather than drawing conclusions from debug builds.
- For dependency updates, confirm current stable versions on the official Jetpack release pages immediately before editing.

## Important Limits

- Do not rely on stale blog posts or third-party summaries when official docs exist.
- Do not hard-code library versions from this skill into code without checking the linked release pages live.
- Do not port phone UI patterns directly to Wear OS without checking the Wear design and quality guidance.
