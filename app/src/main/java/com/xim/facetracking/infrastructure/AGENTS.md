# Infrastructure layer

## Purpose

Implement domain ports using camera, detector, filesystem and submission facilities. Inherit the repository root guide and preserve domain ownership of capture decisions.

## Owned responsibilities

- `camera/`: separate session binding/lifecycle from recording control. Own CameraX resources, surface attachment implementation, recording events and finalized media inspection.
- `analysis/`: separate ML Kit detection, frame conversion, transform mapping and observation assembly. Call pure domain algorithms with correctly paired data.
- `storage/`: resolve opaque artifact IDs, allocate private temporary files, check availability, delete/expire files, and implement backup/retention policy.
- `submission/`: implement fake and eventual real submission through the same domain port. Preserve artifact and idempotency identity on retry; emit progress and typed results.
- Use appropriate background executors/dispatchers and bounded keep-latest analysis backpressure. Reliable recording/submission events use a separate path that does not drop terminal events.
- Release every frame and owned resource on success, failure and cancellation. Clear analyzers, close detectors, stop active recordings and unbind owned camera use cases at their lifecycle boundary.
- Pair region geometry and pixel metrics from the same timestamped frame. Transform analysis coordinates explicitly for rotation, crop, scaling and mirroring; never sample raw sensor pixels using preview coordinates.
- Emit unavailable required metrics as unknown. Do not substitute stale geometry or old metrics after tracking loss.
- Recording events and monotonic time establish capture timing; finalized media supplies persisted duration, dimensions and encoding properties. UI recomposition/collectors must not own recording lifetime.
- Save continuous full-frame video without burned-in overlays or face-following transforms. Distinguish skipped analysis from recording-frame loss.
- Correlate callback results with session/operation IDs, map SDK failures to typed domain results, and release abandoned artifacts even when their results are obsolete.
- Fake acknowledgement is explicitly simulated. Only a real backend acknowledgement may represent actual delivery; retryable transport failure does not discard a valid artifact.
- Storage implements the agreed cancellation, retake, successful-submission and expiry cleanup policy. Keep facial frames/video and sensitive file details out of routine logs and backups.

## Allowed dependencies

Domain contracts/algorithms and platform/SDK libraries needed by each adapter: Android, CameraX, ML Kit, filesystem/media APIs and the selected transport. Dependencies between local components must stay explicit through constructor injection.

## Prohibited patterns

- Importing presentation or `di`; the composition root adapts any presentation-owned preview attachment interface to this layer.
- Owning UI state, localized prompts, business thresholds, readiness transitions or recording-acceptance policy.
- Doing detector/pixel/file/network work on the UI thread, unbounded frame queues, or retaining image buffers past their valid lifetime.
- Treating a preview box as proof of skin-region visibility, using smoothed overlay geometry for metrics, or mixing coordinate spaces.
- Exposing raw SDK error codes/exceptions as UI messages, generating a new idempotency key for each retry, or declaring success before acknowledgement.

## Verification

Test adapters with fake domain ports/sinks where appropriate and instrument Android-specific behavior. Verify frame release, stale callback cleanup, cancellation, camera/detector failure, low storage, interrupted recording, artifact expiry and same-artifact retry. On real devices verify concurrent preview/analysis/recording, backpressure responsiveness, tracking reacquisition and coordinate alignment. Inspect finalized media for duration/properties, absence of overlays and absence of face-following crop/zoom. A passing JVM suite or APK build does not establish these properties.
