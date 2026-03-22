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
- Keep `README.md` human-facing. Put agent-only workflow, validation, and execution policy in `AGENTS.md` or `docs/ci.md` instead of expanding `README.md` with Codex-specific instructions.
- Prefer repo-tracked instructions over local-only setup. Do not depend on ignored local files such as `.codex/`, `.android-local/`, `.gradle-local/`, or `gradlew-local.ps1`.
- Do not commit or push directly to protected `main`. Work on a short-lived task branch and deliver changes through a pull request.
- Never commit machine-specific files, caches, local properties, APK outputs, or generated local artifacts unless the task is specifically about committed screenshot baselines.
- Never add or expose real API keys, tokens, signing files, or other secrets.
- When a task involves installing or updating a debug build on a physical watch/device, prefer the user-level Android debug keystore under `%USERPROFILE%\\.android\\debug.keystore` so local builds remain compatible with Android Studio and CI debug installs. Do not switch to `.android-local` for those install/update flows unless the user explicitly wants an isolated signer and accepts a one-time uninstall.

## Build, lint, and test commands

Prefer the smallest command that proves the change. For local validation, run build, lint, and test as separate Gradle invocations instead of a single all-in-one command unless the task explicitly asks for CI-parity reproduction.

Notes:

- `gradlew` is the Gradle Wrapper shell script. It is committed to the repo so the project can run with the exact Gradle version pinned by the wrapper metadata instead of whatever Gradle happens to be installed globally.
- On Linux or in Codex cloud containers, run `chmod +x ./gradlew` first if `./gradlew` fails with `Permission denied`.
- In Codex cloud, if the wrapper download is blocked but an installed `gradle` binary is available at exactly version `9.3.1`, it is acceptable to use `gradle` instead of `./gradlew` for validation.
- Reuse Gradle daemon and configuration cache for local builds. Do not add `--no-daemon`; `gradle.properties` already enables configuration cache with problems downgraded to warnings.
- On local Windows sessions, if `java`, `javap`, `jar`, or similar JDK tools are not configured globally, use Android Studio's bundled JBR from `%ProgramFiles%\\Android\\Android Studio\\jbr\\bin\\` instead of assuming a separate system JDK install.
- For install-compatible local debug builds, keep `GRADLE_USER_HOME` local if needed, but leave `ANDROID_USER_HOME` unset or point it at `%USERPROFILE%\\.android`. Setting `ANDROID_USER_HOME=.android-local` changes the debug keystore and will break in-place updates over Android Studio or CI-installed builds.

Targeted commands:

```bash
./gradlew --stacktrace :mobile:assembleDebug
./gradlew --stacktrace :wear:assembleDebug
./gradlew --stacktrace :wear:lintDebug
./gradlew --stacktrace :wear:testDebugUnitTest
./gradlew --stacktrace :wear:assembleDebugAndroidTest
./gradlew --stacktrace :wear:testDebugUnitTest --tests com.example.routetracker.presentation.WearScreenshotTest -Proborazzi.test.verify=true
./gradlew --stacktrace :wear:testDebugUnitTest --tests com.example.routetracker.presentation.WearScreenshotTest -Proborazzi.test.record=true
./gradlew --stacktrace :wear:connectedDebugAndroidTest
```

## Validation strategy

- The default workflow is `Android CI` in `.github/workflows/android-ci.yml`; it reports the required `Build And Test` check.
- `Wear Screenshot Record` and `Wear UI Tests` both run automatically on pull requests and can also be launched manually with `workflow_dispatch`.
- Distinguish local vs Codex cloud environments before choosing where to validate:
  - local desktop / local CLI sessions: prefer GitHub Actions for normal feature validation so the user's machine stays responsive; use local Gradle only for complex debugging, narrow fast feedback, device-specific work, or when GitHub cannot answer the question
  - Codex web / Codex cloud sessions: this repo currently assumes no practical GitHub access from that environment, so prefer local cloud builds and tests there; keep them minimal and separate instead of using one large Gradle command
  - repo-specific signal: this project's Codex web environment sets `CODEX_CI`; if it is present, treat the session as Codex cloud for validation decisions
- For normal feature work in local agent sessions, push the branch, open or update the pull request, and let `Android CI` validate Android-relevant changes. Do not default to a broad local Gradle run first.
- Before merge, make sure all three GitHub workflows have passed for the PR head commit: `Android CI`, `Wear Screenshot Record`, and `Wear UI Tests`.
- Use `gh run list`, `gh run watch`, and `gh run view --log-failed` to inspect `Android CI`, `Wear Screenshot Record`, and `Wear UI Tests` results when GitHub CLI is available.
- If you need to rerun screenshot or emulator validation on the same branch head, manually dispatch `Wear Screenshot Record` or `Wear UI Tests` with `gh workflow run ...` and then monitor the runs with `gh run ...`.
- For docs-only, README-only, and other non-Android changes, `Android CI` is expected to succeed as a no-op.
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
