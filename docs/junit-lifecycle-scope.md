# Coordinated JUnit lifecycle migration

JUnit 4 lifecycle annotations can participate in inherited contracts. Migrating only the currently selected compilation unit can therefore create a mixed JUnit 4/Jupiter hierarchy even when the local annotation replacement itself is syntactically valid.

## Coordinated options

The following migrations are classified as `PROJECT_CLOSED`:

- `@Before` to `@BeforeEach`;
- `@After` to `@AfterEach`;
- `@BeforeClass` to `@BeforeAll`;
- `@AfterClass` to `@AfterAll`.

They are not offered as ordinary save actions. A cleanup run may apply them only after the source hierarchy has been closed and previewed.

## Scope closure

`JUnitLifecycleScopeCandidateDetector` starts from the selected source types and follows:

- source superclasses;
- implemented source interfaces;
- lifecycle annotations declared directly on the selected type;
- lifecycle annotations inherited from a source superclass or interface default method.

Binding-derived hierarchy types are passed to the workspace reference search. This adds source subclasses and implementors within the allowed test/support roots. Scope expansion repeats until it reaches a fixed point.

## Fail-closed behavior

Lifecycle plugins are removed from the local rewrite set unless the cleanup instance has proved a complete hierarchy scope. This prevents a stock/unpatched cleanup orchestrator, a manually restricted selection or an unresolved hierarchy from producing a partial migration.

Syntactically recognizable lifecycle annotations with missing or recovered bindings request the conservative allowed-source fallback. References outside the project or permitted source-root policy reject automatic migration.

## Current boundary

This stage coordinates lifecycle annotations only. It does not yet remove JUnit 4 build dependencies, migrate arbitrary custom runners, infer parameterized providers or rewrite non-Java build resources. Those remain separate stages of issue #1217.
