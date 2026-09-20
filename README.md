# Guided facial capture for Android

Native Android application using Kotlin, Compose, CameraX and on-device ML Kit detection.

The active milestone positions one face inside a centered white oval mask. After two seconds of stable alignment, the mask fades and an outline follows the detected face. Tracking loss hides the outline. Recording and submission are not enabled in this milestone.

## MVI and clean architecture

```text
CaptureIntent -> CaptureViewModel -> CaptureReducer -> immutable CaptureUiState -> CaptureScreen
                         |                  |
                         |            CaptureCommand
                         |                  |
                         +-- CaptureEvent <- injected domain port <- infrastructure adapter
```

- `presentation`: lifecycle-aware route, stateless screen, preview-host contract, ViewModel and UI state.
- `domain`: Kotlin-only state transitions, positioning and quality policies, models, clocks and ports.
- `infrastructure`: CameraX lifecycle, ML Kit analysis, recording, temporary files and fake submission.
- `di`: constructor wiring, retained graph, ViewModel factory and preview bridge.

The ViewModel serializes inputs on the main dispatcher. The camera adapter conflates analysis observations but preserves failure events. Session IDs invalidate callbacks after stop, retry and layout changes. Detector work uses a background executor; SDK geometry is mapped into normalized preview coordinates before it reaches the domain/UI.

See [AGENTS.md](AGENTS.md) and each layer's guide for dependency rules. JVM architecture tests enforce the import boundaries.

## Verification

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew connectedDebugAndroidTest
```

Instrumented screen tests use a fake preview and do not require facial capture. Device camera validation still requires checking mask alignment, tracking, background/resume, and camera error recovery with the actual front camera.

## Deferred foundations

Quality evaluators, timeline aggregation, acceptance, `RecordingPort`, private artifact storage and simulated submission remain isolated for future capture work. The camera session currently binds preview and analysis only. A future video-enabled session must wire recording, artifact cleanup, complete quality measurements and submission commands before enabling recording UI. Fake acknowledgement is explicitly marked simulated. Thresholds in `CaptureSpec.DefaultV0_1` remain provisional, not research-validated.
