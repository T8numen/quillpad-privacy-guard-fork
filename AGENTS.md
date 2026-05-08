# Quillpad Project Instructions

## Project Overview

- This is an Android app using Kotlin, XML/ViewBinding, Navigation, Room, Koin, DataStore, FlowSharedPreferences, and some Jetpack Compose dependencies.
- Keep changes small and consistent with the existing Activity/Fragment architecture.
- Default assistant replies should be in Simplified Chinese unless the user asks otherwise.

## Build And Validation

- Use the Gradle wrapper on Windows: `.\gradlew.bat :app:assembleDebug`.
- Prefer focused checks when possible, then run `.\gradlew.bat :app:assembleDebug` before handing off Android code changes.
- If a connected device is available and the user wants runtime verification, install with `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

## Version And Archive Workflow

- For project modifications, update `app/build.gradle.kts` `versionCode` and `versionName`.
- Record user-visible or maintenance-relevant changes in `history.md`.
- Archive debug APKs as `apk-history/debug/app-debug-v<version>-<code>.apk`.
- Archive source snapshots as `source-history/quillpad-v<version>-<code>-source.zip`.

## Privacy Feature Notes

- Hidden/privacy features should usually be recorded in `history.md`, not in the public in-app `app/src/main/assets/whatsnew.yaml`, unless the user explicitly asks.
- `fragment_privacy` still exists as a compatibility route, but the active privacy home experience is driven by `ActivityViewModel.isPrivacyPageActive` while showing `fragment_main`.
- Be careful with lifecycle callbacks in `MainActivity` and `EditorFragment`; privacy return cleanup must not expose private editor or private home screens while returning to the default home.

## Safety

- The worktree often contains large uncommitted changes. Do not run destructive git commands such as `git reset --hard` or `git checkout --` unless the user explicitly requests them.
- Do not remove unrelated dead code or reformat unrelated files.
