# CI and GitHub Workflows

This project uses GitHub Actions on GitHub-hosted runners. The setup is split into one default CI workflow for fast feedback and two manual workflows for heavier Wear OS tasks.

## Overview

| Workflow | File | Trigger | Purpose |
| --- | --- | --- | --- |
| Android CI | `.github/workflows/android-ci.yml` | `pull_request`, `push` to `main`, `workflow_dispatch` | Main build, lint, unit test, screenshot verification, artifact upload |
| Wear Screenshot Record | `.github/workflows/wear-screenshot-record.yml` | `workflow_dispatch` | Re-record Roborazzi screenshot baselines and upload generated images |
| Wear UI Tests | `.github/workflows/wear-ui-tests.yml` | `workflow_dispatch` | Boot a Wear emulator and run instrumented UI tests |

## Shared CI setup

All workflows use the same core conventions:

- GitHub-hosted Ubuntu runners (`ubuntu-latest`)
- Java 21 through `actions/setup-java`
- Gradle dependency and build caching through `gradle/actions/setup-gradle`
- Android SDK setup through `android-actions/setup-android`
- A project-local `ANDROID_USER_HOME` to avoid writing into the runner's default home directory
- `GOLEMIO_API_KEY` from repository secrets

These choices keep the common path fast while still supporting Android and Wear OS specific tasks.

## Workflow details

### Android CI

File: `.github/workflows/android-ci.yml`

This is the default workflow for pull requests and pushes to `main`. It is designed to give a strong signal without paying emulator startup cost on every change.

The workflow always reports a `Build And Test` check so it can be used with branch protection. For docs-only, README-only, and other non-Android changes, it exits early with a successful no-op result instead of running the Android toolchain.

It runs:

```bash
./gradlew --no-daemon --stacktrace :mobile:assembleDebug
./gradlew --no-daemon --stacktrace :wear:lintDebug
./gradlew --no-daemon --stacktrace :wear:assembleDebug
./gradlew --no-daemon --stacktrace :wear:assembleDebugAndroidTest
./gradlew --no-daemon --stacktrace :wear:testDebugUnitTest -Proborazzi.test.verify=true
```

Artifacts uploaded:

- `android-apks`: debug APKs and test APKs from the build
- `android-reports`: lint, unit test, and other generated reports

If the optional stable debug-keystore secrets are configured, the debug APKs from `android-apks` keep the same signing identity across CI runs and can update an existing CI-installed app on a device or watch.

Use this workflow for:

- regular PR validation
- confirming the project still builds
- catching lint and unit test regressions
- verifying Roborazzi screenshots against committed baselines
- collecting build outputs and reports for issue work

The heavy Android steps run only when at least one Android-relevant path changed:

- `.github/workflows/android-ci.yml`
- `.github/workflows/wear-screenshot-record.yml`
- `.github/workflows/wear-ui-tests.yml`
- `mobile/**`
- `wear/**`
- `gradle/**`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`

Changes outside that set, such as `docs/**`, `README.md`, and other repository metadata, still get a green `Build And Test` check without doing a full Android build.

### Wear Screenshot Record

File: `.github/workflows/wear-screenshot-record.yml`

This workflow is intentionally manual because it updates screenshot outputs and is not something we want on every pull request.

It runs:

```bash
./gradlew --no-daemon --stacktrace :wear:testDebugUnitTest --tests com.example.routetracker.presentation.WearScreenshotTest -Proborazzi.test.record=true
```

Artifacts uploaded:

- `wear-screenshots`: generated screenshot files from the recording run

Use this workflow when:

- a UI change intentionally updates expected Wear screenshots
- you want downloadable screenshots from CI
- Codex or a contributor needs visual artifacts while working on an issue

### Wear UI Tests

File: `.github/workflows/wear-ui-tests.yml`

This workflow is also manual because emulator startup is slower and more failure-prone than normal JVM-based CI. It installs the Wear OS system image, creates an AVD, boots the emulator, waits for full boot, disables animations, and then runs instrumented tests.

It runs:

```bash
./gradlew --no-daemon --stacktrace :wear:connectedDebugAndroidTest
```

Artifacts uploaded:

- `wear-ui-test-reports`: connected test reports and device-side outputs
- `wear-ui-emulator-log`: emulator boot and runtime log for debugging

Use this workflow when:

- debugging real Wear UI flows
- investigating failures that only happen on device/emulator
- collecting instrumented test artifacts
- validating fixes for issues that Codex or a developer is working on

## Why the workflows are split

The split follows Android CI best practices:

- Keep the main PR workflow fast and deterministic.
- Run expensive emulator-based checks only when they are needed.
- Always upload reports and artifacts so failures are inspectable after the run.
- Use Gradle caching and a minimal SDK package set to reduce setup time.
- Use `--no-daemon` in CI to avoid confusing "idle daemon still running" situations after work is already finished.

## Local command mapping

The current workflows are based on the same commands that were already useful locally:

- `:wear:assembleDebug`
- `:wear:assembleDebugAndroidTest`
- `:wear:testDebugUnitTest -Proborazzi.test.verify=true`
- `:wear:testDebugUnitTest -Proborazzi.test.record=true`

CI adds a few more checks around them, especially `:mobile:assembleDebug`, `:wear:lintDebug`, artifact upload, and the manual emulator run for `:wear:connectedDebugAndroidTest`.

## Manual runs

You can trigger the manual workflows from the GitHub Actions UI because both include `workflow_dispatch`.

You can also trigger them with the GitHub CLI:

```bash
gh workflow run "Wear Screenshot Record" --ref main
gh workflow run "Wear UI Tests" --ref main
```

To use `gh workflow run`, the token used by `gh` needs repository `Actions` permission with `Write` access.

## Current caveats

As of March 20, 2026:

- `Android CI` is passing.
- `Wear Screenshot Record` is working and uploads screenshot artifacts.
- `Wear UI Tests` now boots the emulator and reaches `:wear:connectedDebugAndroidTest`, so the remaining failures are real test/app failures rather than workflow setup failures.

At the moment, the failing instrumented tests are in:

- `wear/src/androidTest/java/com/example/routetracker/presentation/WearUiFlowTest.kt`

There is also still a GitHub warning around `android-actions/setup-android@v3` being a Node 20 action. The workflows currently set `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true`, but the warning is still worth tracking.

## Secrets and permissions

Repository secret expected by the workflows:

- `GOLEMIO_API_KEY`

Optional repository secrets for stable CI debug signing:

- `ANDROID_DEBUG_KEYSTORE_BASE64`
- `ANDROID_DEBUG_KEYSTORE_PASSWORD`
- `ANDROID_DEBUG_KEY_ALIAS`
- `ANDROID_DEBUG_KEY_PASSWORD`

When these are set, `Android CI` decodes the keystore into the runner, exports signing environment variables, and both `mobile` and `wear` debug builds use that stable key instead of a runner-generated debug keystore.

Workflow permissions are intentionally minimal:

- `contents: read`

This is enough for checkout and CI execution. Manual dispatch from the GitHub web UI depends on repository access, while `gh workflow run` depends on the local token permissions used by GitHub CLI.
