# Presentation layer

## Purpose

Render the capture experience and translate interaction into UDF actions. Inherit the repository root guide; these rules apply to this subtree and to legacy UI responsibilities being migrated here.

## Owned responsibilities

- `CaptureRoute` acquires the lifecycle-owned ViewModel, collects state with lifecycle awareness, and forwards permission/lifecycle results. It hosts the narrow preview-attachment bridge.
- `CaptureScreen` and reusable composables render immutable parameters and emit actions. Local state is limited to ephemeral visual concerns, not capture-session decisions.
- `CaptureViewModel` serializes actions and platform events, invokes domain transitions/policies, maps domain state to UI state, and executes returned commands through injected ports.
- Expose one immutable `CaptureUiState` through read-only `StateFlow` and accept `onAction(CaptureIntent)`. Derive progress labels, readiness text, and button availability from authoritative state.
- Localized strings and issue-code-to-prompt mapping belong here, using Android resources. Domain returns structured issue codes and values, not display text.
- Render overlay geometry separately from measurement geometry. Visual smoothing must not change quality inputs.
- A preview host may use Android views and a presentation-owned attachment interface. The composition root supplies the bridge implementation without exposing concrete camera adapters to presentation.
- UI effects are transient requests such as opening settings. Recording, acceptance, and submission outcomes remain in state and survive collector recreation within the supported session lifecycle.

## Allowed dependencies

Domain models, transitions and ports; Kotlin/coroutines; Compose; Android UI, resources and lifecycle APIs. Presentation-owned Android preview attachment types are permitted at the view boundary only.

## Prohibited patterns

- Importing infrastructure, constructing CameraX/ML Kit/submission/storage implementations, or importing `di` to fetch dependencies.
- Opening/deleting files, deriving quality metrics, running recording timers, or making acceptance decisions in the ViewModel or composables.
- Mutating state from unsequenced SDK callbacks or duplicating session truth in separate mutable UI fields.
- Launching capture/upload because a composable recomposed, or reexecuting commands when a `StateFlow` collector restarts.
- Passing Android views/lifecycle owners/surfaces into domain, retaining activity references in a ViewModel, or manually remembering a ViewModel instance.

## Verification

Use fake ports and controlled clocks to test event ordering, obsolete callback rejection, cancellation, interruption, and command execution once per operation. UI tests verify state rendering, permission actions, readiness, retake, retry, accessible text/labels, and large fonts. Confirm recreation does not restart recording or submission. Camera performance/output claims require device checks, as defined in the root guide.
