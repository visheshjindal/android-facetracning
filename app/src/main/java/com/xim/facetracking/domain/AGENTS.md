# Domain layer

## Purpose

Own capture decisions independently of Android, UI, detector SDKs, storage and transport. Inherit the repository root guide; these rules also guide migration of legacy capture logic.

## Owned responsibilities

| Component | Single responsibility |
| --- | --- |
| `CaptureReducer` | Legal session transitions and commands: `state + event -> new state + commands` |
| Quality evaluators | All applicable per-check results, including unknown/unavailable measurements |
| `MovementEvaluator` | Position/orientation changes within a timestamped movement window |
| `GuidancePolicy` | Priority, onset/recovery persistence, hysteresis, prompt stability and readiness |
| `QualityTimelineAccumulator` | Failure intervals, observation gaps and monitoring coverage |
| `RecordingAcceptancePolicy` | Decision over finalized recording properties and complete quality history |
| Geometry and metric algorithms | Deterministic region geometry and image statistics in explicit coordinate spaces |
| Versioned capture specification | Provisional policy values, units, timings, required checks and tolerances |

- Keep domain capture state distinct from presentation `CaptureUiState`. Events and commands express intent/results without UI/SDK classes.
- Define ports for capture control/events, artifact storage, submission and clocks. Use opaque artifact IDs plus immutable metadata; infrastructure resolves IDs to files.
- Submission requests carry a stable idempotency identity across retries. Results distinguish acknowledgement, retryable failure and rejection.
- Inject monotonic time for session durations/windows and wall time only for expiry/calendar purposes. Do not read system time inside pure decisions.
- Each temporal component defines session ownership, initialization and reset. Pure functions receive history explicitly; stateful accumulators are explicitly stateful and session-scoped.
- Use frame timestamps and known sample-validity intervals for coverage. Account for gaps and union overlapping failure intervals; repeated snapshots must not duplicate history.
- Return all applicable checks before prompt selection so lower-priority failures remain represented in recording history.
- Distinguish acceptable, failed and unknown required checks. Readiness/acceptance must not interpret null metrics as acceptable.
- Use explicit coordinate-space, rotation/mirroring and units contracts. Geometry and metrics paired in an observation describe the same frame; preview-smoothed geometry is never a quality input.

## Allowed dependencies

Kotlin and coroutines only for production domain code. Use plain value types, collections and explicit interfaces. Tests may use the project's JVM test libraries and deterministic fakes.

## Prohibited patterns

- Android, Compose, CameraX, ML Kit, UI resource IDs, SDK buffers/exceptions, `File`, concrete adapters, and dependencies on presentation/infrastructure/di.
- Display strings, file paths as artifact identity, direct storage/network access, or thread/dispatcher ownership inside pure rules.
- Hidden mutable history in an API described as pure, state shared across recording attempts, and frame-count-based duration or coverage.
- Conflating tracking success with readiness, discarding lower-priority checks, or silently inventing production thresholds.

## Verification

Use JVM tests with explicit timestamps for transition legality, prompt timing/hysteresis, readiness withdrawal, movement windows, unknown checks, interval unions, tracking gaps, stable snapshot aggregation, duration tolerance and acceptance reasons. Test session reset and obsolete event IDs. Use synthetic pixel/geometry fixtures for metrics and coordinate math; include invalid/clipped regions. Domain tests must not require an Android runtime or camera.
