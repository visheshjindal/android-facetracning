# Guided face tracking for Android

Native Android application using Kotlin, Compose, CameraX and on-device ML Kit detection.

The app positions one face inside a centered white oval mask. After two seconds of stable alignment, the mask fades and an outline follows the detected face. Tracking loss hides the outline and guidance helps the user recover.

## MVI and clean architecture

```text
CaptureIntent -> CaptureViewModel -> CaptureReducer -> immutable CaptureUiState -> CaptureScreen
                         |                  |
                         |            CaptureCommand
                         |                  |
                         +-- CaptureEvent <- FaceTrackingPort <- CameraXTrackingSession
```

- `presentation`: lifecycle-aware route, stateless screen, preview-host contract, ViewModel and UI state.
- `domain`: Kotlin-only state transitions, positioning and guidance policies, models and clock/face-tracking ports.
- `infrastructure`: CameraX lifecycle and ML Kit analysis.
- `di`: constructor wiring, retained graph, ViewModel factory and preview bridge.

The ViewModel serializes inputs on the main dispatcher. The camera adapter conflates analysis observations but preserves failure events. Session IDs invalidate callbacks after stop, retry and layout changes. Detector work uses a background executor; SDK geometry is mapped into normalized preview coordinates before it reaches domain and UI code.

See [AGENTS.md](AGENTS.md) and each layer's guide for dependency rules. JVM architecture tests enforce the import boundaries.

## Verification

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew connectedDebugAndroidTest
```

Instrumented screen tests use a fake preview and do not require a camera. Device validation still requires checking mask alignment, tracking, background/resume behavior and camera error recovery with the actual front camera.
