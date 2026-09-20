# Coding agent guide

## Purpose

Build the Android guided face-tracking experience using unidirectional data flow (UDF) and the Single Responsibility Principle (SRP). Keep the repository focused on live tracking, positioning and user guidance.

This guide applies throughout the project. Layer guides add constraints for their subtrees without weakening these rules. Keep one Gradle application module and use constructor injection; do not introduce a DI framework or new modules merely to enforce boundaries.

## Owned responsibilities

Each component owns one reason to change. Separate camera lifecycle, detection, measurement, guidance decisions and UI rendering. Avoid catch-all managers, repositories and utility classes; name components after their actual responsibility.

All target packages are under `app/src/main/java/com/xim/facetracking/`.

| Package | Responsibility | Local guide |
| --- | --- | --- |
| `presentation/` | Routes, screens, UI state, ViewModel, localized prompts, preview host | [Presentation](app/src/main/java/com/xim/facetracking/presentation/AGENTS.md) |
| `domain/` | Transitions, positioning and guidance rules, geometry, models and ports | [Domain](app/src/main/java/com/xim/facetracking/domain/AGENTS.md) |
| `infrastructure/` | Camera, detector and frame adapters | [Infrastructure](app/src/main/java/com/xim/facetracking/infrastructure/AGENTS.md) |
| `di/` | Composition root and factories | [Composition root](app/src/main/java/com/xim/facetracking/di/AGENTS.md) |

`MainActivity` remains the entry point and connects `di/CaptureGraph` to `presentation/CaptureRoute`. Pure geometry and image-metric algorithms belong in domain; SDK buffers and coordinate-transform adapters belong in infrastructure.

## UDF contract

```text
UI action or platform event
             |
CaptureViewModel: serialized event processing
             |
Domain transition: state + event -> new state + commands
             |                              |
Immutable CaptureUiState             Execute through injected ports
             |                              |
Lifecycle-aware Compose UI           Results return as events
```

- Expose one read-only `StateFlow<CaptureUiState>` and `onAction(CaptureIntent)`. Keep mutable state private.
- Distinguish user actions from `CaptureEvent` results for camera, analysis and lifecycle. Presentation translates actions into domain events.
- A deterministic transition returns new domain state and explicit commands; it performs no I/O.
- Serialize transitions. High-frequency observations may use a separately conflated input; camera failures must not be dropped. Detect observation gaps from timestamps.
- Correlate asynchronous work with session IDs and ignore obsolete results.
- Acquire ViewModels through Android lifecycle ownership, never `remember { CaptureViewModel() }`.
- Keep session stage, readiness and progress authoritative; derive UI labels rather than maintaining parallel mutable copies.

## Agent workflow

1. Identify the owning responsibility and read the root and applicable nested guides.
2. Inspect existing call sites and tests.
3. Make the smallest cohesive change; do not launch a broad unrelated refactor.
4. Add or update meaningful tests and run the relevant checks.
5. Report what changed, checks actually run and unverified device behavior.

## Tracking invariants

- Preserve Android-only, portrait, front-camera behavior unless the user requests a product change.
- The active flow uses a centered white oval, two seconds of stable alignment and then live face following.
- Missing mandatory measurements are unknown, never implicit passes.
- Tracking, visual overlay smoothing and guidance remain separate.
- Keep raw camera frames out of logs and persistent app storage.
- Each monitoring attempt has a monotonically increasing session ID; stale callbacks and non-increasing timestamps are ignored.

## Allowed dependencies

- `presentation -> domain`, plus Android UI/lifecycle libraries.
- `infrastructure -> domain`, plus required platform/SDK libraries.
- `domain -> Kotlin and coroutines`; no dependencies on other application layers.
- `di -> presentation + domain + infrastructure` for construction only.
- `MainActivity -> di + presentation` as the entry-point exception.
- Presentation and infrastructure must not import each other. A narrow Android preview-attachment interface may live in presentation and be wired by `di`.

## Prohibited patterns

- Android, Compose, CameraX, ML Kit, `File`, UI result classes or SDK objects in domain contracts.
- Concrete infrastructure dependencies, camera ownership or quality algorithms in ViewModels.
- Business decisions in composables or the composition root.
- Hidden global/session state, uncorrelated callbacks or a second camera owner/state machine.
- Treating skipped analysis as camera-frame loss or describing positioning success as validated measurement quality.

## Verification

- `./gradlew testDebugUnitTest`: deterministic domain, transition and orchestration tests.
- `./gradlew assembleDebug`: compilation and packaging.
- `./gradlew connectedDebugAndroidTest`: instrumented tests when a suitable device/emulator is available.
- Real-device acceptance: preview/analysis concurrency, tracking loss/recovery and mirror/crop/rotation alignment.
- Documentation-only changes: verify paths, guide inheritance and dependency consistency.
