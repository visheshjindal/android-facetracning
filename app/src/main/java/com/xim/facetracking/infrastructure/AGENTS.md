# Infrastructure layer

## Purpose

Implement domain tracking ports using camera and detector facilities. Inherit the repository root guide and preserve domain ownership of decisions.

## Owned responsibilities

- `camera/`: CameraX binding, lifecycle, preview attachment, observation delivery and cleanup.
- `analysis/`: ML Kit detection, frame conversion, coordinate mapping and observation assembly.
- Use background executors and bounded keep-latest analysis backpressure.
- Release every frame and owned resource on success, failure and cancellation.
- Map analysis coordinates explicitly for rotation, crop, scaling and mirroring.
- Emit unavailable required metrics as unknown and never substitute stale geometry after tracking loss.
- Correlate callback results with session IDs and map SDK failures to typed domain results.

## Allowed dependencies

Domain contracts/algorithms and platform/SDK libraries needed by each adapter. Local dependencies remain explicit through constructor injection.

## Prohibited patterns

- Importing presentation or `di`.
- Owning UI state, localized prompts, thresholds, readiness transitions or guidance decisions.
- Detector or pixel work on the UI thread, unbounded frame queues or retaining image buffers past their valid lifetime.
- Mixing coordinate spaces or exposing raw SDK exceptions as UI messages.

## Verification

Test frame release, stale callback rejection, cancellation and camera/detector failures. On real devices verify backpressure responsiveness, tracking reacquisition and coordinate alignment.
