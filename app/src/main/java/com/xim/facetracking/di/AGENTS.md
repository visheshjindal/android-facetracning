# Composition root

## Purpose

Wire the application explicitly while preserving layer boundaries. Inherit the repository root guide and use constructor injection in the existing application module.

## Owned responsibilities

- Construct clocks, domain policies, port implementations and lifecycle-aware ViewModel factories.
- Supply a narrow bridge implementing the presentation-owned preview attachment interface and delegating to infrastructure.
- Keep application dependencies free of activity references and session-scoped mutable state.
- Allow tests to inject fakes and controlled clocks without changing business logic.
- Provide dependencies to `MainActivity` and the route; other layers must not use a global service locator.

## Allowed dependencies

All three application layers and Android lifecycle/factory APIs required for construction.

## Prohibited patterns

- Business rules, hidden threshold overrides, session transitions or mutable tracking history.
- Global service locators, activity leaks and shared mutable temporal evaluators.
- Adding a DI framework or Gradle modules merely for architecture compliance.

## Verification

Check that factories construct the intended adapters, tests can override ports/clocks and dependencies point in the allowed direction. Run compilation checks when wiring code changes.
