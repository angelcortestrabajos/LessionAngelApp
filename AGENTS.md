# AGENTS.md

## Project overview

LessionAngel is a single-module Android app built with Jetpack Compose and the Gradle Android plugin. The root project is intentionally small: `settings.gradle.kts` includes only `:app`, and the application namespace is `com.example.lessionangel`.

## Key files and architecture

- `app/src/main/java/com/example/lessionangel/MainActivity.kt`: the main application entry point and the primary place for UI/state logic. It contains:
  - the `AppScreen` enum (`CONNECTION`, `DATA`, `RESULTS`, `HISTORY`)
  - the `TrainingSession` and `Measurement` data models
  - the `LessionApp()` composable with the full app flow
  - the simulated real-time measurement loop and persisted session history
- `app/src/main/java/com/example/lessionangel/ui/theme/*`: Compose theme helpers and color definitions used by the app.
- `app/src/main/AndroidManifest.xml`: declares the single launcher activity (`.MainActivity`).
- `app/src/main/res/*`: app resources, launcher assets, colors, and XML config.
- `app/build.gradle.kts`: the Android app module config. It enables Compose, sets Java 11, and uses `minSdk = 24` and SDK 37.

The app is not organized into MVVM or layered features; most of the state and screen logic lives in `MainActivity.kt` in one Compose flow.

## Persistence and data model

Session history is saved in `SharedPreferences` using the keys defined at the top of `MainActivity.kt`:

- `SESSION_PREFERENCES = "training_sessions"`
- `SESSION_DATA_KEY = "sessions"`

`loadSessions()` and `saveSessions()` persist a JSON array of `TrainingSession` objects. Preserve the structure exactly when editing the model:

- `TrainingSession`: `id`, `startedAt`, `finishedAt`, `readings`
- `Measurement`: `heartRate`, `effort`, `fatigue`, `steps`

This matters because old saved data is parsed directly from JSON with `JSONArray`/`JSONObject` and there is no migration layer.

## Workflow and build commands

Run commands from the project root.

- Windows PowerShell: `./gradlew test`
- Windows PowerShell: `./gradlew assembleDebug`
- Android emulator/device tests: `./gradlew connectedDebugAndroidTest`

The repo currently includes only the default template test files (`app/src/test/...` and `app/src/androidTest/...`). The app is intentionally demo-driven; there is no backend service or CI pipeline yet.

## Coding conventions to follow

- Keep UI and state changes in `MainActivity.kt` unless there is a clear, explicit need to split logic into new files.
- Prefer Compose patterns already used in the codebase: `remember`, `mutableStateOf`, `mutableStateListOf`, `LaunchedEffect`, and Material 3 components.
- Reuse the existing color palette and styling conventions already defined in `MainActivity.kt` (`Purple`, `Accent`, `Red`, `Card`, `Green`) instead of introducing a second theme system.
- The app is in "demo mode" by design: `LaunchedEffect(recording)` generates random heart-rate, effort, fatigue, and step values instead of real sensor readings. If you add sensor integration, update both the connection flow and the live-reading logic together.
- When changing screen navigation, keep `historyReturnScreen` consistent with the prior screen so the `HISTORY` screen can return to the correct place.
- The generated Android output directories (`build/`, `.gradle/`, `.idea/`) are artifacts and should not be edited by hand.

## Key examples for AI agents

- The main behavior for saving and displaying sessions is in the `RESULTS` and `HISTORY` screens in `MainActivity.kt`.
- The real-time graph is implemented by `HeartRateChart()`; it expects a `List<Float>` and draws a simple line chart over a fixed range.
- The connection screen intentionally includes a Bluetooth/Wi‑Fi selector and a device dropdown, but the app does not yet connect to a real device; the selected device only drives the demo UI state.

## Constraints and notes

- There is no separate backend, repository layer, or dependency injection setup in this project yet.
- The repository currently contains no `.github` instructions, no `AGENT.md`, and no other custom AI rule files besides this document; this file is therefore the canonical guidance for coding agents in this repo.

