# Coding agent guide

## Purpose

Build the Android guided facial-video capture feature using unidirectional data flow (UDF) and the Single Responsibility Principle (SRP). This document defines the target architecture, not a claim that the existing prototype conforms to it.

This guide applies throughout the project. Layer guides below add constraints for their subtrees without weakening these rules. Read the relevant layer guide when migrating legacy code into that layer, even before its files move. Keep one Gradle application module and use constructor injection; do not introduce a DI framework or new modules merely to enforce these boundaries.

## Owned responsibilities

Each component owns one reason to change. Separate camera lifecycle, recording, detection, measurement, quality decisions, UI rendering, storage, and submission. Avoid catch-all managers, repositories, and utility classes; name components after their actual responsibility.

### Package map

All target packages are under `app/src/main/java/com/xim/facetracking/`.

| Package | Responsibility | Local guide |
| --- | --- | --- |
| `presentation/` | Routes, screens, UI state, ViewModel, localized prompts, preview host | [Presentation](app/src/main/java/com/xim/facetracking/presentation/AGENTS.md) |
| `domain/` | Transitions, quality rules, temporal policies, acceptance, models and ports | [Domain](app/src/main/java/com/xim/facetracking/domain/AGENTS.md) |
| `infrastructure/` | Camera, detector, frame adapters, recording, storage and submission implementations | [Infrastructure](app/src/main/java/com/xim/facetracking/infrastructure/AGENTS.md) |
| `di/` | Composition root and factories | [Composition root](app/src/main/java/com/xim/facetracking/di/AGENTS.md) |

`MainActivity` remains the entry point and connects the composition root to the capture route. Separate infrastructure responsibilities into `camera`, `analysis`, `storage`, and `submission` packages when implementing them. Pure geometry and image-metric algorithms belong in domain; SDK buffers and coordinate-transform adapters belong in infrastructure.

### UDF contract

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
- Distinguish user actions from `CaptureEvent` results for camera, analysis, recording, lifecycle, and submission. Presentation translates actions into domain events.
- A deterministic transition returns new domain state and explicit commands; it performs no I/O. Execute commands once through injected ports, not as a consequence of recomposition or state collection restarting.
- Serialize transitions. High-frequency observations may use a separately conflated input; recording-finalization, cancellation, and submission events must not be dropped. Detect observation gaps from timestamps.
- Correlate asynchronous work with session and operation IDs. Ignore obsolete results while still releasing their resources through the owning adapter.
- Consequential outcomes belong in state. Transient UI effects may launch permission requests or app settings, with results returned as actions/events.
- Acquire ViewModels through Android lifecycle ownership, never `remember { CaptureViewModel() }`.
- Keep session stage, readiness, and progress authoritative; derive UI labels rather than maintaining parallel mutable copies.

### Agent workflow

1. Identify the owning responsibility and read the root and applicable nested guides.
2. Inspect existing call sites and tests. Treat prototype code as migration input, not architectural precedent.
3. Make the smallest cohesive change. Migrate touched responsibilities in focused changes; do not launch a broad refactor merely to comply with these guides.
4. Add or update meaningful tests for changed behavior and run the relevant checks below.
5. Report what changed, checks actually run, and unverified device behavior. Never infer functional completeness from a successful build.

### Capture invariants

- Preserve the agreed Android-only, portrait, front-camera, manual-start, silent, continuous 40-second capture behavior unless the user requests a product change.
- Preserve existing versioned prototype defaults; do not silently tune thresholds during architectural work. Research thresholds remain provisional until validated.
- Readiness and acceptance require known results for mandatory checks. Missing measurements are unknown, never implicit passes.
- Tracking, visual overlay smoothing, guidance, and recorded output remain separate. The saved video stays full-frame without overlays, face-following crops, or face-following zoom.
- Interrupted/shortened recordings cannot become completed submissions. Retry uses the existing valid artifact and the same submission identity.
- Protect facial video and raw frames from logs and backups. Storage owns lifecycle policy; fake submission must never be represented as actual backend delivery.

### Current implementation and deferred work

The active feature is the positioning milestone: camera permission, a centered white oval mask, two seconds of stable alignment, then face following. Recording remains disabled in the active UI at the user's request. Do not restore recording controls during structural refactors.

| Responsibility | Current source |
| --- | --- |
| Entry point | `MainActivity.kt` connects `di/CaptureGraph` to `presentation/CaptureRoute` |
| MVI contract | `presentation/CaptureViewModel.kt`: `CaptureIntent`, immutable `CaptureUiState`, `onAction` |
| Deterministic transitions | `domain/CaptureReducer.kt` and `domain/CaptureContract.kt` |
| Positioning decisions | `domain/PositioningPolicy.kt`; caller-owned temporal state |
| UI and preview host | `presentation/CaptureScreen.kt`, `CaptureRoute.kt`, `PreviewHost.kt`, `PositioningMask.kt` |
| Camera ownership | `infrastructure/camera/CameraXTrackingSession.kt` |
| SDK detection and conversion | `infrastructure/analysis/MlKitFaceAnalyzer.kt` |
| Quality policies | Separate domain evaluators, guidance, timeline, and acceptance policies |
| Deferred recording | `infrastructure/camera/CameraXRecorder.kt` behind `RecordingPort` |
| Deferred storage/submission | `infrastructure/storage/`, `infrastructure/submission/` behind domain ports |
| Dependency wiring | `di/CaptureGraph.kt` and the presentation-owned preview bridge |

The legacy `capture/`, `ui/`, and `model/` Kotlin classes have been migrated or replaced. Do not recreate a second capture state machine or camera owner in those packages. Recording, regional image quality and submission are retained as isolated foundations; their end-to-end integration and device acceptance are deferred. Positioning success must not be described as research-validated recording quality.

MVI uses `CaptureIntent` for user/lifecycle/layout input, `CaptureEvent` for reducer input and port results, and `CaptureCommand` for work executed through ports. `onAction(CaptureIntent)` is the presentation entry point. The ViewModel's main dispatcher serializes state transitions. Each monitoring attempt has a monotonically increasing session ID; stale callback IDs and non-increasing timestamps are ignored.

## Allowed dependencies

- `presentation -> domain`, plus Android UI/lifecycle libraries.
- `infrastructure -> domain`, plus the platform/SDK libraries required by each adapter.
- `domain -> Kotlin and coroutines`; no dependencies on other application layers.
- `di -> presentation + domain + infrastructure` for construction only.
- `MainActivity -> di + presentation` as the entry-point exception.
- Presentation and infrastructure must not import each other. A narrow Android preview-attachment interface may live in presentation and be wired by an adapter in `di`; Android views, lifecycle owners, and surfaces must never enter domain contracts.
- Test code may construct fakes/adapters as needed, but production dependency rules must remain intact.

## Prohibited patterns

- Android, Compose, CameraX, ML Kit, `File`, UI result classes, or SDK objects in domain contracts.
- Concrete infrastructure dependencies, direct filesystem work, camera ownership, or quality algorithms in ViewModels.
- Business decisions in composables or the composition root.
- Hidden global/session state, uncorrelated callbacks, and UI timers that own recording completion.
- Calling stateful temporal components pure, estimating coverage by multiplying frame count by an assumed cadence, or treating skipped analysis as recording-frame loss.
- Unrequested threshold changes or broad migrations bundled into unrelated work.

## Verification

- `./gradlew testDebugUnitTest`: deterministic domain, state-transition, and orchestration tests. Add coverage for the behavior being changed.
- `./gradlew assembleDebug`: compilation and packaging; it does not establish camera or quality correctness.
- `./gradlew connectedDebugAndroidTest`: instrumented tests when a suitable device/emulator is available. Report unavailable device checks explicitly.
- Real-device acceptance: concurrent preview/analysis/recording, tracking loss/recovery, mirror/crop/rotation alignment, recording interruption, output duration, and playback/frame inspection to confirm overlays and face-following transforms are absent.
- Regression tests must cover stale callbacks, session resets, unknown required metrics, timestamp-based coverage, interrupted recordings, and same-artifact/idempotency-key retry.
- Documentation-only changes: verify paths, guide inheritance, dependency consistency, and migration references. Do not run application tests solely for Markdown changes or claim these guides make existing code compliant.
