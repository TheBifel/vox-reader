---
memory_type: workflow
authority: canonical
last_verified: 2026-08-10
update_when: build tools, verification, device policy, or asset handling changes
---

# Development workflow

## Prerequisites

- Android Studio compatible with the checked-in Gradle wrapper.
- Android SDK 36 and JDK 21.
- Emulator or Android 8.0/API 26+ device.

Open the repository root in Android Studio and use the `app` run configuration.

## Standard verification

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain --no-daemon
git diff --check
```

Use the emulator for implementation and regression checks. Keep screenshots to the minimum needed for visual verification. Install or run on the physical Samsung only when the user explicitly requests it. The Galaxy S21 test device has successfully run the buffered Kitten path; device success is not a substitute for unit/lint/build checks.

## Manual smoke test

1. Open one local EPUB and one local FB2 through both supported entry paths where practical.
2. Confirm latest-position restore, horizontal paging, vertical scrolling, TOC, links, and history.
3. Import the matching local sidecar and check colors plus long-press speaker identity.
4. Exercise narrator-only mode with a book that has no map.
5. Start each available voice engine, change speakers, lock the screen, and use system media controls.
6. When touching speech, test an empty buffer/loading transition and post-processing both off and on.

## Local assets and privacy

`test-data/` may hold the user's two local book/map pairs for manual testing, but it is ignored and must stay untracked. Downloaded models, generated audio, screenshots, work packets, `local.properties`, IDE state, build output, and secrets also stay out of git. Never commit copyrighted book contents or API credentials.

The tracked sherpa-onnx AAR is required for neural inference. Voice model weights are not bundled; the user downloads them from the in-app catalog into private app storage.

## Speaker maps

Use [../reference/SPEAKER_ANALYSIS_JSON.md](../reference/SPEAKER_ANALYSIS_JSON.md) for the app contract and [../../skills/voxreader-label-book/SKILL.md](../../skills/voxreader-label-book/SKILL.md) for preparation/labeling. The app's validator is authoritative. Keep any preparation work directory outside version control.

## Git practice

Preserve unrelated local changes. Prefer small commits that separate stable product work from documentation or cleanup. Before pushing, verify the branch, remote, status, tests, lint, build, markdown links, and ignored local artifacts.
