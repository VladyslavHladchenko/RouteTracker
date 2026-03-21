# AGENTS.md

## Project snapshot

- RouteTracker is an Android project for Prague PID departures, with most active product work in the `wear` module.
- `mobile` exists and should keep building, but the main UX, UI tests, and screenshot tests live under `wear`.
- CI and workflow behavior are documented in `docs/ci.md`.
- Local API reference files such as `golemio-openapi.json` and `golemio-pid-openapi.json` are checked in for reference only.

## Important directories

- `wear/src/main/` - Wear OS app code, screens, repository logic, and app behavior
- `wear/src/test/` - JVM tests and Roborazzi screenshot tests
- `wear/src/androidTest/` - instrumented Wear UI tests
- `wear/src/test/screenshots/` - committed Roborazzi baselines
- `mobile/` - companion app module; keep it building unless the task explicitly scopes it out
- `gradlew` and `gradlew.bat` - the checked-in Gradle Wrapper launchers; prefer these over a random system Gradle when possible
- `.github/workflows/` - GitHub Actions workflows used as the main validation path
- `docs/ci.md` - current CI setup and artifact behavior

## Working agreements

- Keep changes focused. Do not mix unrelated refactors into feature or bug-fix work.
- Preserve existing UI and architecture patterns unless the task explicitly calls for redesign.
- Prefer repo-tracked instructions over local-only setup. Do not depend on ignored local files such as `.codex/`, `.android-local/`, `.gradle-local/`, or `gradlew-local.ps1`.
- Do not commit or push directly to protected `main`. Work on a task branch and deliver changes through a pull request.
- Never commit machine-specific files, caches, local properties, APK outputs, or generated local artifacts unless the task is specifically about committed screenshot baselines.
- Never add or expose real API keys, tokens, signing files, or other secrets.
- When a task involves installing or updating a debug build on a physical watch/device, prefer the user-level Android debug keystore under `%USERPROFILE%\\.android\\debug.keystore` so local builds remain compatible with Android Studio and CI debug installs. Do not switch to `.android-local` for those install/update flows unless the user explicitly wants an isolated signer and accepts a one-time uninstall.

## Build, test, and lint commands

Prefer the smallest command that proves the change, but use CI as the final source of truth for Android validation.

Notes:

- `gradlew` is the Gradle Wrapper shell script. It is committed to the repo so the project can run with the exact Gradle version pinned by the wrapper metadata instead of whatever Gradle happens to be installed globally.
- On Linux or in Codex cloud containers, run `chmod +x ./gradlew` first if `./gradlew` fails with `Permission denied`.
- In Codex cloud, if the wrapper download is blocked but an installed `gradle` binary is available at exactly version `9.3.1`, it is acceptable to use `gradle` instead of `./gradlew` for validation.
- For install-compatible local debug builds, keep `GRADLE_USER_HOME` local if needed, but leave `ANDROID_USER_HOME` unset or point it at `%USERPROFILE%\\.android`. Setting `ANDROID_USER_HOME=.android-local` changes the debug keystore and will break in-place updates over Android Studio or CI-installed builds.

Primary CI-equivalent command set:

```bash
./gradlew --no-daemon --stacktrace --continue \
  :mobile:assembleDebug \
  :wear:lintDebug \
  :wear:assembleDebug \
  :wear:assembleDebugAndroidTest \
  :wear:testDebugUnitTest \
  -Proborazzi.test.verify=true
```

Targeted commands:

```bash
./gradlew --no-daemon --stacktrace :wear:assembleDebug
./gradlew --no-daemon --stacktrace :wear:assembleDebugAndroidTest
./gradlew --no-daemon --stacktrace :wear:testDebugUnitTest --tests com.example.routetracker.presentation.WearScreenshotTest -Proborazzi.test.verify=true
./gradlew --no-daemon --stacktrace :wear:testDebugUnitTest --tests com.example.routetracker.presentation.WearScreenshotTest -Proborazzi.test.record=true
./gradlew --no-daemon --stacktrace :wear:connectedDebugAndroidTest
```

## Validation strategy

- For Codex web and GitHub-driven tasks, rely on GitHub Actions as the main validation path.
- The default workflow is `Build And Test` in `.github/workflows/android-ci.yml`.
- Use `Wear Screenshot Record` when a UI change intentionally updates screenshot baselines.
- Use `Wear UI Tests` only when emulator-backed validation is needed.
- Distinguish local vs Codex cloud environments before choosing where to validate:
  - local desktop / local CLI sessions: prefer GitHub Actions for broad build-and-test validation so the user's machine stays responsive; only run the smallest local command needed for fast feedback or device-specific work
  - Codex web / Codex cloud sessions: use the cloud environment for targeted builds and tests when they are relevant, because they do not load the user's machine; still use GitHub Actions as the final source of truth for branch validation
  - repo-specific signal: this project's Codex web environment sets `CODEX_CI`; if it is present, treat the session as Codex cloud for validation decisions
- If the Codex cloud environment cannot fully reproduce the Android toolchain or emulator setup, do not invent weaker substitutes. State clearly what was validated locally and what still depends on CI.
- If heavier Android or Roborazzi tasks start correctly but do not finish within the session budget, report that they were attempted, do not claim success, and rely on CI for the final result.
- When UI changes affect snapshots, update screenshot baselines and make the visual change easy to inspect in the PR.

## Secrets and live data

- `GOLEMIO_API_KEY` is compiled into `BuildConfig` when provided. Treat any real key as sensitive.
- Current tests and screenshot coverage should use preview or fake data and should not require a live API key.
- Do not introduce workflows or changes that embed a real API key into downloadable CI artifacts unless the user explicitly asks for that tradeoff.
- If a task truly requires live API access, stop and ask before proceeding.
- For debug builds on a physical watch, prefer the debug-only ADB broadcast receiver over manual on-watch typing when setting a temporary API key override:
  - `adb shell am broadcast -a com.example.routetracker.debug.SET_API_KEY -n com.example.routetracker/.debug.DebugApiKeyOverrideReceiver --es value "<key>"`
  - `adb shell am broadcast -a com.example.routetracker.debug.CLEAR_API_KEY -n com.example.routetracker/.debug.DebugApiKeyOverrideReceiver`

## Wear-specific expectations

- Default to the `wear` module unless the task clearly concerns `mobile` or shared Gradle/configuration files.
- Respect round-screen constraints. Watch for clipped labels, off-screen actions, and overly tall cards.
- For changes to departures, trip details, route setup, or other visible Wear UI, update tests and screenshots together when behavior or visuals change.

## PR expectations

- Branches should be short-lived and scoped to one task or issue.
- Keep PRs scoped to one concern.
- Summaries should state the user-visible change, validation performed, and any remaining limitation.
- If CI fails, investigate the reported failure rather than bypassing or weakening checks.
- If work started from an issue or PR comment, keep follow-up work tied to that context and address review comments directly.

## Review guidelines

- Prioritize behavioral regressions, missing or stale tests, round-screen visibility issues, data/caching mistakes, and secret leakage.
- Call out missing screenshot updates when UI changed.
- Treat docs-only wording issues as low priority unless the task explicitly asks for documentation review.

## Done means

- The requested behavior is implemented.
- Relevant tests or screenshot baselines are updated when needed.
- Appropriate GitHub Actions checks pass, or any remaining required CI is clearly identified.
- No local-only files or secrets are committed.
- Any limitation, follow-up, or unrun validation is stated explicitly.
