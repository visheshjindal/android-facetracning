# Guided face tracking for Android

Native Android application using Kotlin, Jetpack Compose, CameraX, and on-device ML Kit Face Detection.

The app provides a guided face-positioning experience using a centered white oval mask. After one second of continuous, stable alignment, the mask fades out and a live tracking outline follows the user's face across the preview while corrective guidance remains active.

---

## App Usage & User Flow

### Prerequisites
- **Device**: Android 7.0 (API level 24) or higher with a front-facing camera.
- **Orientation**: Portrait mode (locked).
- **Environment**: Adequate ambient lighting for reliable face detection and pose estimation.

---

### Step-by-Step Experience

#### 1. Grant Camera Permission
- When launched for the first time, the app requests access to the front-facing camera (`android.permission.CAMERA`).
- Tap **Grant camera access** to accept the runtime permission prompt.
- If previously denied or set to "Don't ask again", tap **Open app settings** to enable the permission manually, or **Exit** to close the application.

#### 2. Initial Face Alignment (Positioning Mask)
Once camera access is granted, the live camera feed opens with a centered white cutout oval mask. The top guidance banner displays real-time instructions to help you align your face inside the target oval:

| Guidance Prompt | Condition & User Action |
| :--- | :--- |
| **Place your face inside the oval** | No face is detected in the alignment area. Bring your face into view. |
| **Center your face inside the oval** | Your face is detected but offset from the center. Shift horizontally or vertically toward the oval center. |
| **Move a little closer** | Your face is too small relative to the target oval. Bring the device closer to your face. |
| **Move a little farther away** | Your face is too large relative to the target oval. Move the device slightly farther away. |
| **Look straight at the camera** | Your head angle exceeds pose limits (yaw > 10°, pitch > 15°, or roll > 8°). Look directly into the front camera lens. |
| **Hold still for a moment** | Your face is centered, properly scaled, and looking straight ahead. Hold your position steadily. |

#### 3. One-Second Stable Hold
- When **"Hold still for a moment"** appears, the app evaluates stability over a continuous **1-second** interval (`holdMs = 1000L`).
- If you move significantly, drift, tilt your head, or if tracking is interrupted during this second, the stability timer resets.
- Once one second of continuous alignment elapses, the white oval mask smoothly animates and fades out (450ms transition).

#### 4. Live Face Following
- The guidance banner updates to: **"Face positioned — following your face"**.
- A vibrant green outline (`#00FF87`) tracks and follows the primary detected face in real time as you move within the camera viewport.
- The white positioning mask remains hidden, but the banner provides explicit **Move left**, **Move right**, **Move up**, or **Move down** guidance when the face leaves the centered target area. It also retains distance and pose guidance. Corrective guidance does not interrupt tracking or require another stable hold.
- **Multiple Faces**: The detector tracks the primary detection (the first face returned by ML Kit). Additional faces in the background receive no outline and do not obstruct tracking.
- **Identity Agnostic**: There is no biometric identity lock; if the primary face changes, the outline follows the current primary detection.

#### 5. Tracking Loss & Recovery
- If you move out of frame, turn away, or the detector loses sight of your face:
  - The green outline disappears.
  - The banner displays: **"Tracking lost — look back at the camera, or restart tracking"**.
  - **Automatic Recovery**: Simply return your face into the camera view. Tracking automatically resumes without forcing you through the initial oval alignment again.
  - **Manual Restart**: Tap the **Restart tracking** button to reset the session back to the centered white oval mask and the 1-second alignment phase.

#### 6. Error Handling & Camera Recovery
- If the front camera becomes unavailable or face detection fails:
  - An error message will appear (e.g., *"The front camera is unavailable. Please try again."* or *"Face detection is unavailable. Please try again."*).
  - Tap **Try again** to re-initialize the camera session, or tap **Exit** to leave.

---

## MVI and Clean Architecture

The app follows Unidirectional Data Flow (UDF) and the Single Responsibility Principle (SRP):

```text
CaptureIntent -> CaptureViewModel -> CaptureReducer -> immutable CaptureUiState -> CaptureScreen
                         |                  |
                         |            CaptureCommand
                         |                  |
                         +-- CaptureEvent <- FaceTrackingPort <- CameraXTrackingSession
```

- **`presentation/`**: Lifecycle-aware `CaptureRoute`, stateless `CaptureScreen`, `PositioningMask`, `CaptureViewModel`, and `CaptureUiState`.
- **`domain/`**: Kotlin-only deterministic state transitions (`CaptureReducer`), positioning policies (`PositioningPolicy`), quality models, and `FaceTrackingPort`.
- **`infrastructure/`**: CameraX lifecycle binding (`CameraXTrackingSession`), ML Kit face analyzer (`MlKitFaceAnalyzer`), frame coordinate normalizer (`PreviewCoordinates`), and system monotonic clock.
- **`di/`**: Pure constructor wiring (`CaptureGraph`), retained graph, and ViewModel factory.

### Key Architectural Invariants
- **Serialized Transitions**: The ViewModel executes state transitions serially on the main dispatcher.
- **Session Correlation**: Every tracking session uses a strictly increasing `sessionId` to invalidate obsolete asynchronous detector callbacks and camera events.
- **Normalized Geometry**: Face coordinates are mapped to normalized, mirrored preview coordinates `[0.0, 1.0]` before reaching domain logic and UI renderers.
- **Separation of Concerns**: Face tracking, visual overlay smoothing, and guidance decisions remain isolated.

See [AGENTS.md](AGENTS.md) and the local layer guides in each package for architectural and dependency rules.

---

## Verification & Build

### Running Unit Tests
Deterministic domain, transition, and architecture boundary tests:
```sh
./gradlew testDebugUnitTest
```

### Compiling and Linting
```sh
./gradlew assembleDebug lintDebug
```

### Instrumented Tests
Instrumented Compose UI tests using simulated preview bridges:
```sh
./gradlew connectedDebugAndroidTest
```

> **Note on Real-Device Testing**: While automated tests validate domain policies and UI composables using synthetic previews, real-device validation with the front camera is required to verify physical lens aspect ratios, mirroring, ambient lighting adaptability, and tracking performance.
