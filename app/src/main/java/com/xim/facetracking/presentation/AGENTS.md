# Presentation layer

## Purpose

Render the tracking experience and translate interaction into UDF actions. Inherit the repository root guide.

## Owned responsibilities

- `CaptureRoute` acquires the lifecycle-owned ViewModel, collects state with lifecycle awareness and forwards permission/lifecycle results.
- `CaptureScreen` and reusable composables render immutable parameters and emit actions.
- `CaptureViewModel` serializes actions and platform events, invokes domain transitions and executes commands through injected ports.
- Expose one immutable `StateFlow<CaptureUiState>` and accept `onAction(CaptureIntent)`.
- Localized strings and issue-code-to-prompt mapping belong here.
- Render overlay geometry separately from measurement geometry; visual smoothing must not alter guidance inputs.
- A preview host may use Android views through a presentation-owned attachment interface wired by the composition root.

## Allowed dependencies

Domain models, transitions and ports; Kotlin/coroutines; Compose; Android UI, resources and lifecycle APIs.

## Prohibited patterns

- Importing infrastructure or `di`, or constructing CameraX/ML Kit implementations.
- Deriving quality metrics or making guidance decisions in ViewModels or composables.
- Mutating state from unsequenced SDK callbacks or duplicating session truth.
- Starting camera work because a composable recomposed.
- Passing Android views or lifecycle owners into domain.

## Verification

Use fake ports and controlled clocks to test event ordering, obsolete callback rejection, cancellation and command execution once per operation. UI tests verify state rendering, permission actions, readiness, retry, accessible text and large fonts. Camera behavior requires device checks.
