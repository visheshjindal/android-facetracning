# Composition root

## Purpose

Wire the application explicitly while preserving layer boundaries. Inherit the repository root guide. Use constructor injection within the existing application module.

## Owned responsibilities

- Construct clocks, domain policies, port implementations, session-scoped collaborators and lifecycle-aware ViewModel factories.
- Select fake versus real submission explicitly through configuration; make the simulated nature of fake delivery available to presentation state.
- Supply a narrow bridge implementing the presentation-owned preview attachment interface and delegating to infrastructure. This bridge is wiring/platform attachment only; it contains no capture decisions.
- Make ownership and cleanup scope explicit: application dependencies must not retain activities or capture-session mutable state; each capture attempt gets correctly initialized temporal state.
- Allow tests to inject fakes, controlled clocks and alternative adapter results without changing business logic.
- Provide dependencies to `MainActivity`/the route; other layers must not look them up through a global service locator.

## Allowed dependencies

All three application layers and the Android lifecycle/factory APIs required for construction. This is the only layer that connects concrete presentation and infrastructure implementations.

## Prohibited patterns

- Business rules, threshold overrides hidden in factories, session transitions, mutable capture history, storage operations or submission execution.
- Global service locators, activity leaks and shared mutable temporal evaluators across sessions.
- Adding a DI framework or Gradle modules merely for architecture compliance.

## Verification

Check that factories construct the intended adapters and lifecycle-scoped owners, fake/real selection is explicit, and tests can override ports/clocks. Verify dependency direction and session ownership. Run relevant compilation checks when wiring code changes; Markdown-only changes need path and instruction-consistency checks only.
