# Domain layer

## Purpose

Own tracking and guidance decisions independently of Android, UI and detector SDKs. Inherit the repository root guide.

## Owned responsibilities

- `CaptureReducer`: legal session transitions and commands.
- `PositioningPolicy`: alignment, stable hold and face-following decisions.
- Quality evaluators: all applicable per-check results, including unknown measurements.
- `MovementEvaluator`: position/orientation changes within a timestamped window.
- `GuidancePolicy`: prompt priority, persistence, hysteresis and readiness.
- Geometry and metric algorithms: deterministic calculations in explicit coordinate spaces.
- `CaptureSpec`: provisional, version-controlled guidance thresholds and timings.

Keep domain state distinct from presentation `CaptureUiState`. Events and commands express intent/results without UI or SDK classes. Use monotonic time for session windows. Pure functions receive history explicitly; stateful temporal data must have clear session ownership and reset behavior.

## Allowed dependencies

Kotlin and coroutines only for production domain code. Tests may use JVM test libraries and deterministic fakes.

## Prohibited patterns

- Android, Compose, CameraX, ML Kit, UI resources, SDK buffers/exceptions, files or other application layers.
- Display strings, direct device access or dispatcher ownership inside pure rules.
- Hidden mutable history in APIs described as pure.
- Conflating tracking visibility with readiness or silently inventing production thresholds.

## Verification

Use JVM tests with explicit timestamps for transition legality, prompt timing, readiness withdrawal, movement windows, unknown checks, session reset and obsolete event IDs. Use synthetic pixel/geometry fixtures for metric and coordinate math.
