# CI and GitHub Workflows

This project uses GitHub Actions on GitHub-hosted runners. The setup is split into one default CI workflow for fast feedback and two heavier Wear OS workflows that run automatically on pull requests, rerun on `main` after merge to refresh shared Gradle caches, and can also be launched manually.

Draft pull requests are intentionally lighter than ready-for-review pull requests. Draft PRs are the default state for screenshot-first Wear UI prototyping. Ready-for-review PRs are the point where the full validation set should run before merge.

Before merging a PR, all three workflows should have passed for the current head commit: `Android CI`, `Wear Screenshot Record`, and `Wear UI Tests`.

## Overview

| Workflow | File | Trigger | Purpose |
| --- | --- | --- | --- |
| Android CI | `.github/workflows/android-ci.yml` | `pull_request`, `push` to `main`, `workflow_dispatch` | Draft PRs get reduced smoke validation. Ready PRs, `main`, and manual runs get the full build, lint, and unit test flow. |
| Wear Screenshot Record | `.github/workflows/wear-screenshot-record.yml` | `pull_request`, `push` to `main`, `workflow_dispatch` | Upload screenshot artifacts for screenshot-first review on draft PRs and full screenshot recording on ready PRs. |
| Wear UI Tests | `.github/workflows/wear-ui-tests.yml` | `pull_request`, `push` to `main`, `workflow_dispatch` | Skip draft PRs, then boot a Wear emulator and run instrumented UI tests once the PR is ready for review. |

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

On draft pull requests with Android-relevant changes, it runs reduced smoke validation only. On ready-for-review pull requests, `push` to `main`, and manual dispatch, it runs the full validation flow.

The workflow always reports a `Build And Test` check so it can be used with branch protection. For docs-only, README-only, and other non-Android changes, it exits early with a successful no-op result instead of running the Android toolchain.

Within that one job, build, lint, and JVM test work are split into separate GitHub Actions steps so failures are easier to identify without paying the setup cost of separate jobs.

`Android CI`, `Wear Screenshot Record`, and `Wear UI Tests` all enable Gradle configuration cache and provide an encrypted cache key to `gradle/actions/setup-gradle`, so runs on `main` can populate reusable configuration-cache entries and later non-default-branch runs can restore them in read-only mode. The two Wear workflows therefore rerun on `main` after merge to keep those heavier caches warm as well.

Full mode runs:

```bash
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :mobile:assembleDebug
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :wear:lintDebug
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :wear:assembleDebug
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :wear:assembleDebugAndroidTest
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :wear:testDebugUnitTest -Proborazzi.test.verify=true
```

Draft smoke mode runs one or both of these module assemble commands, depending on which app areas changed:

```bash
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :mobile:assembleDebug
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :wear:assembleDebug
```

Artifacts uploaded:

- `android-apks`: debug APKs and test APKs from the build
- `android-reports`: lint, unit test, and other generated reports

If the optional stable debug-keystore secrets are configured, the debug APKs from `android-apks` keep the same signing identity across CI runs and can update an existing CI-installed app on a device or watch.

Use this workflow for:

- draft compile/smoke validation
- regular ready-for-review validation
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

Changes outside that set, such as `docs/**`, `README.md`, and other repository metadata, still get a green `Build And Test` check without doing an Android build.

On draft pull requests, the smoke build only assembles the affected app module targets. For example, `mobile`-only draft changes do not assemble `wear`, and `wear`-only draft changes do not assemble `mobile`.

### Wear Screenshot Record

File: `.github/workflows/wear-screenshot-record.yml`

This workflow runs on both draft and ready-for-review pull requests so its `Record Screenshots` job can be used as the primary screenshot review artifact during UI prototyping. It also runs on `push` to `main` so post-merge screenshot recording can refresh shared Gradle caches. `workflow_dispatch` remains available when you want to rerun screenshot recording on demand.

It runs:

```bash
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :wear:testDebugUnitTest --tests com.example.routetracker.presentation.WearScreenshotTest -Proborazzi.test.record=true
```

Artifacts uploaded:

- `wear-screenshots`: generated screenshot files from the recording run

Use this workflow when:

- a UI change intentionally updates expected Wear screenshots
- you want downloadable screenshots from CI
- Codex or a contributor needs visual artifacts while working on an issue

This workflow skips itself cleanly when no wear or build-relevant files changed, so required checks stay green without paying screenshot-recording cost on unrelated PRs.

### Wear UI Tests

File: `.github/workflows/wear-ui-tests.yml`

This workflow runs on ready-for-review pull requests, on `push` to `main`, and through `workflow_dispatch`. Draft pull requests skip this job intentionally so prototype-first UI work does not pay emulator startup cost too early. When it does run, it installs the Wear OS system image, creates an AVD, boots the emulator, waits for full boot, disables animations, and then runs instrumented tests.

It runs:

```bash
./gradlew --stacktrace --configuration-cache --configuration-cache-problems=warn :wear:connectedDebugAndroidTest
```

Artifacts uploaded:

- `wear-ui-test-reports`: connected test reports and device-side outputs
- `wear-ui-emulator-log`: emulator boot and runtime log for debugging

Use this workflow when:

- debugging real Wear UI flows
- investigating failures that only happen on device/emulator
- collecting instrumented test artifacts
- validating fixes for issues that Codex or a developer is working on

This workflow also skips itself cleanly when no wear or build-relevant files changed.

## Why the workflows are split

The split follows Android CI best practices:

- Keep draft PRs lightweight for screenshot-first prototyping.
- Keep the main PR workflow fast and deterministic.
- Run expensive emulator-based checks only when they are needed.
- Always upload reports and artifacts so failures are inspectable after the run.
- Use Gradle caching and a minimal SDK package set to reduce setup time.
- Prefer daemon-backed Gradle invocations in CI so split steps in the same job can reuse a warm JVM instead of paying repeated startup cost.
- Use job-level conditions inside workflows instead of path-filtered workflow skips so required checks can still report a successful conclusion.

## Local command mapping

The current workflows are based on the same commands that were already useful locally:

- `:wear:assembleDebug`
- `:wear:assembleDebugAndroidTest`
- `:wear:testDebugUnitTest -Proborazzi.test.verify=true`
- `:wear:testDebugUnitTest -Proborazzi.test.record=true`

Draft PRs usually map closest to `:wear:assembleDebug` plus `:wear:testDebugUnitTest -Proborazzi.test.record=true`.

Ready-for-review PRs add the heavier checks around them, especially `:mobile:assembleDebug`, `:wear:lintDebug`, artifact upload, and the emulator-backed run for `:wear:connectedDebugAndroidTest`.

## Manual runs

You can trigger the Wear workflows from the GitHub Actions UI because both include `workflow_dispatch`, even though they also run automatically for pull requests and on `main` after merge.

For merge readiness, check that the pull-request-triggered runs for all three workflows have completed successfully on the current head commit. Use manual dispatch when you need an extra rerun on the same branch head.

You can also trigger them with the GitHub CLI:

```bash
gh workflow run "Wear Screenshot Record" --ref main
gh workflow run "Wear UI Tests" --ref main
```

To use `gh workflow run`, the token used by `gh` needs repository `Actions` permission with `Write` access.

## Current caveats

As of March 27, 2026:

- `Android CI` is passing.
- `Wear Screenshot Record` is passing and uploads screenshot artifacts.
- `Wear UI Tests` is passing and uploads emulator logs and test reports.

There is also still a GitHub warning around `android-actions/setup-android@v3` being a Node 20 action. The workflows currently set `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true`, but the warning is still worth tracking.

## Secrets and permissions

Repository secret expected by the workflows:

- `GOLEMIO_API_KEY`
- `GRADLE_ENCRYPTION_KEY` for encrypted Gradle configuration-cache reuse in `Android CI`, `Wear Screenshot Record`, and `Wear UI Tests`

Optional repository secrets for stable CI debug signing:

- `ANDROID_DEBUG_KEYSTORE_BASE64`
- `ANDROID_DEBUG_KEYSTORE_PASSWORD`
- `ANDROID_DEBUG_KEY_ALIAS`
- `ANDROID_DEBUG_KEY_PASSWORD`

When these are set, `Android CI` decodes the keystore into the runner, exports signing environment variables, and both `mobile` and `wear` debug builds use that stable key instead of a runner-generated debug keystore.

Workflow permissions are intentionally minimal:

- `contents: read`

This is enough for checkout and CI execution. Manual dispatch from the GitHub web UI depends on repository access, while `gh workflow run` depends on the local token permissions used by GitHub CLI.
