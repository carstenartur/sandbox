# ADR 0008: Int-to-Enum compatibility policy

- Status: Accepted
- Date: 2026-07-26
- Issues: #1216, #1223, #1225, #1226

## Context

An integer domain may be only a private implementation detail, or it may be part of a public method signature, persisted database value, serialized document, preference, command-line option or network protocol. Replacing those cases with the same enum rewrite would confuse source cleanup with compatibility migration.

Enum declaration order is not an external identity. `Enum.ordinal()` must never replace an integer value used outside a closed source implementation.

## Decision

Sandbox defines three distinct modes.

### `CLOSED_SOURCE`

This is the only implemented automatic mode.

It may run only when:

- the declaration and migrated method are not public or protected;
- every source caller and supported constant reference is inside the proven selected scope;
- every call passes one modeled constant rather than an arbitrary integer expression;
- values are distinct and form a finite domain;
- no unsupported method reference, parameter use or constant use exists;
- the generated declaration and every generated reference resolve without a name collision;
- the complete change is previewed, applied and undone atomically.

The generated enum does not need a numeric field because no external numeric identity is claimed. The impact level is `PROJECT_CLOSED`, and ordinary save-action execution is prohibited.

### `NUMERIC_ADAPTER`

This future opt-in mode is `COMPATIBILITY_MANAGED` and remains fail-closed until all required behavior is implemented.

It requires:

- an explicit immutable numeric value for every constant;
- deterministic `fromValue(int)` behavior;
- an explicit policy for unknown, sparse and aliased values;
- temporary source compatibility overloads where selected;
- deprecation rather than silent removal of public integer constants;
- preservation and tests for persistence, serialization, preferences, CLI and wire formats;
- explicit review of binary compatibility;
- no use of `ordinal()`.

Bit masks are not ordinary enum domains. They require a separate `EnumSet`/flag migration policy.

### `MANUAL_EXTERNAL`

Domains involving non-Java resources, ambiguous values, build configuration, schema changes or interactive compatibility choices are `MANUAL_REFACTORING`. The automatic cleanup reports why it cannot proceed and delegates to a dedicated refactoring workflow.

## Generated names and references

Domain type names are deterministic. A collision causes rejection rather than an unexplained numeric suffix. The plan identity records the prospective name, and stale-plan resolution must prove that the same owner, method, constants, calls and generated type name still exist.

Generated references use the shortest unambiguous AST name permitted by Java accessibility. Cross-package callers are rejected while the generated nested enum remains package-private. Visibility expansion belongs only to `NUMERIC_ADAPTER`.

## Consequences

- Current package-scoped migrations remain conservative and source-closed.
- Public or externally represented integer APIs cannot be enabled by merely broadening detection.
- Preview and CLI diagnostics can state a stable impact and compatibility claim.
- Future compatibility work has a separate opt-in mode and test surface rather than weakening the automatic cleanup.
