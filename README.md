# Guided face tracking for Android

A live front-camera experience that helps the user position their face, follows it as they move, and provides corrective positioning and lighting guidance. Runs on Android 7.0+ in portrait orientation.

## See it in action

Actual app screenshots from a Samsung Galaxy S24 (SM-S921B), captured on 20 September 2026.

| 1. Align your face | 2. Live tracking and guidance | 3. Adaptive lighting guidance | 4. Recover from tracking loss |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/alignment.png" width="200" alt="White oval positioning mask, centering prompt and lighting indicator"> | <img src="docs/screenshots/live-tracking.png" width="200" alt="Green outline follows the detected face while the app tracks position"> | <img src="docs/screenshots/lighting-guidance.png" width="200" alt="Top banner and amber lighting indicator prompt the user to face a soft, even light source"> | <img src="docs/screenshots/tracking-lost.png" width="200" alt="Tracking lost message, face return outline and Restart tracking button"> |
| The white mask guides alignment; the lighting indicator shows current conditions. | After a stable hold, the mask clears and a green outline follows the face. Position, distance and head-angle prompts remain active. | When uneven or extreme lighting is detected, the banner and amber indicator prompt corrective action, such as softening harsh light or adding light to a shadowed side. | A return outline stays visible. Returning the face resumes tracking; **Restart tracking** starts alignment again. |

Adaptive lighting guidance samples multi-axis luminance across the face to identify low light, excessive brightness, horizontal imbalance (with left/right prompts), vertical imbalance, and mixed high-contrast exposure. When vertical or mixed imbalances occur, the app directs the user to face a soft, even light source. Warnings require sustained persistence before triggering, and the indicator collapses to a compact badge once lighting is acceptable. See [lighting validation](docs/lighting-validation.md) for profile thresholds and calibration protocols. Positioning success is not a validated image-quality or identity check.

```mermaid
flowchart LR
    A[Camera permission] --> B[Align in oval]
    B --> C[Stable hold]
    C --> D[Live face following]
    D --> E[Face lost]
    E -->|Face returns| D
    E -->|Restart tracking| B
```

## Critical implementation details

- **Kotlin + Jetpack Compose** render immutable UI state; **CameraX + on-device ML Kit** provide the preview and face observations.
- **Unidirectional data flow:** UI actions and camera events enter the ViewModel, a pure domain reducer decides state and commands, and the UI renders the result. Camera lifecycle, detection, guidance and overlay rendering have separate responsibilities, wired through constructor injection.
- **Multi-axis lighting analysis:** an affine-mapped sparse luma sampler computes regional trimmed means and exposure tails without passing SDK image buffers into domain logic. A hysteresis policy applies 400 ms persistence to warnings and 700 ms to acceptable states, collapsing to a compact icon after 1.5 seconds of stable lighting.
- **Reliable session handling:** serialized transitions, increasing session IDs and timestamp checks reject obsolete callbacks. Camera frames are not logged or persisted by the app.
- **Current timing discrepancy:** the implementation uses a **1-second** stable hold; [the project guide](AGENTS.md) specifies **2 seconds**. This documentation update preserves existing behavior.

## Verification

Built and launched on the Galaxy S24 running Android 16. Screenshots show real camera alignment, tracking with corrective guidance, adaptive lighting alerts, and tracking loss.

| Check | Result |
| --- | --- |
| `./gradlew assembleDebug` | Passed |
| `./gradlew testDebugUnitTest` | 38 tests passed |
| `./gradlew connectedDebugAndroidTest` | 12 tests passed |

Automatic recovery, lighting transitions and mirror/crop/rotation accuracy still need dedicated real-device acceptance checks; screenshots alone do not verify them.

To run locally, open the project in Android Studio, select a device with a front camera, run `app`, and grant camera access.
