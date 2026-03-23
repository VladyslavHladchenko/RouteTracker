# Samsung Galaxy Watch

Read this file only when the task explicitly targets Samsung Galaxy Watch hardware, One UI Watch behavior, or Samsung-specific debugging/testing.

Keep the general Wear OS guidance as the default. Use this file to add device-specific checks, not to replace the Android Developers docs.

## Current Local Device Assumption

- Default Samsung target: Galaxy Watch7 44mm.
- Current display target: `480 x 480` on a `1.5"` round display.
- OS family: Wear OS Powered by Samsung.
- Input assumption: touch-first, with Samsung's digital scrolling bezel behavior available on device.

If the user later switches to a different Samsung watch size or series, re-check the display specs before using this file for screenshot or clipping decisions.

## UI And Input Implications

- Validate round-screen layouts against a `480 x 480` circle before assuming a generic Wear layout is safe.
- Do not assume a physical rotating bezel. Galaxy Watch7 uses Samsung's digital scrolling bezel interaction rather than a mechanical bezel.
- Do not make bezel interaction mandatory. Samsung documents that the bezel feature can be disabled, so core navigation and scrolling must remain usable with touch alone.
- Keep vertical scrolling, list selection, and picker interactions comfortable under both touch-only use and rotary-style input handling.
- Re-check gesture conflicts between horizontal swipe-to-dismiss and vertical list scrolling on Samsung hardware.

## Debugging And Deployment

- Prefer testing on the physical watch when Samsung-specific behavior matters.
- For wireless debugging, use Samsung's current Galaxy Watch pairing flow with `adb pair` and `adb connect`.
- Samsung's current developer guide notes that One UI settings paths can differ by version, so do not hard-code setup paths in user guidance without re-checking the current page.
- When writing step-by-step debugging instructions, prefer the official Samsung page over memory.

Official page:

- Connect Galaxy Watch with Android Studio: https://developer.samsung.com/health/sensor/guide/connect-watch.html

## Testing Strategy

- Prefer the real Galaxy Watch7 44mm for touch-target, clipping, gesture, and performance checks.
- Use Samsung Remote Test Lab only when hardware is unavailable or when you need a second Samsung device for comparison.
- Treat Galaxy Emulator Skin as cosmetic only. It helps visual framing, but it does not reproduce One UI Watch behavior or real-device performance.

Official pages:

- Remote Test Lab: https://developer.samsung.com/remote-test-lab
- Galaxy Emulator Skin guide: https://developer.samsung.com/galaxy-emulator-skin/guide.html

## Out Of Scope By Default

- Samsung Health Sensor SDK
- Samsung Health Data SDK
- One UI Watch system features that are not exposed as third-party app APIs

Do not load Samsung health references unless the task explicitly needs Samsung health or sensor integration.

## High-Signal Device Pages

- Galaxy Watch7 44mm specs: https://www.samsung.com/us/business/mobile/wearables/smartwatches/galaxy-watch7-44mm-green-wifi-bluetooth-sm-l310nzgaxaa/
  Why: confirms `480 x 480`, `1.5"` display, and other device-level constraints for current local testing.
- Galaxy Watch navigation and bezel behavior: https://www.samsung.com/us/support/answer/ANS10003406/
  Why: confirms digital bezel behavior on Watch7-family devices and that bezel interaction exists alongside touch navigation.
