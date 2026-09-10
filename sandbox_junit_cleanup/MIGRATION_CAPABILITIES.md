# JUnit migration capabilities

This matrix describes the executable migration boundary on `main`. It deliberately distinguishes local syntax rewrites from coordinated semantic migrations. A green checkbox means that the listed shape has production code and active tests; it does not mean that every use of the corresponding JUnit feature is migratable.

## Safety model

JUnit migration uses three layers:

1. **Semantic planner** — discovers a closed source scope, resolves bindings, classifies execution semantics and emits stable rejection reasons.
2. **Plan-aware hint program** — performs only planner-authorized local AST rewrites. One declaration selects the plan and implies fail-closed semantic target resolution:

   ```text
   <!requires-plan: contract-id>
   ```

3. **Runtime oracle** — compares JDT JUnit discovery/execution before and after migration where the migration changes test identity or multiplicity.

A missing binding, binary-only participant, stale plan, incomplete project scope or unsupported execution hook prevents the complete migration unit from being produced. Ordinary non-plan hints retain compatibility behavior by default; `binding-policy: required` uses the implemented `MATCH` / `NO_MATCH` / `UNKNOWN` guard diagnostics. Plan-aware hints continue to use only `requires-plan`.

## Capability matrix

| Source shape | Status | Execution path | Important boundary |
|---|---:|---|---|
| JUnit 4 lifecycle annotations | Supported | local cleanup / declarative rewrite | mixed or custom lifecycle semantics may require coordinated planning |
| JUnit 4 assertions and assumptions | Supported for classified overloads | local cleanup | message/delta overloads require resolved method/type semantics |
| closed direct assertion/assumption helper | Supported for private and package-private static wrappers | helper declaration plus workspace-wide source caller closure, then ordinary assertion/assumption rewrite | exactly one direct JUnit 4 `Assert`/`Assume` invocation; public/protected, overridable package-instance and compound/recursive wrappers remain outside this slice |
| `@Test(expected=...)` | Supported | imperative body rewrite | wraps the proven method body in `assertThrows` |
| `@Test(timeout=...)` and supported timeout rules | Supported | local structured rewrite | custom timeout wrappers remain unsupported |
| `TemporaryFolder`, `TestName`, supported `ExternalResource` rules | Supported | local or coordinated rule migration | external/binary resource classes and mixed rule scopes fail closed |
| transitive `ExternalResource` fixture chains | Supported | multi-file semantic plan | every editable fixture and consumer project must be in scope |
| ordinary closed JUnit 3 hierarchy | Supported | `junit3-hierarchy` semantic plan plus plan-aware DSL | constructors, name/result hooks, decorators and custom harness references are rejected |
| simple JUnit 3 `suite()` aggregator | Supported | fail-closed suite model | only plain top-level aggregator types and modeled composition forms |
| JUnit 4 `@RunWith(Suite.class)` / class selection | Supported for modeled forms | suite cleanup | custom runners and dynamic composition require a dedicated contract |
| local JUnit 4 Parameterized provider | Supported for modeled forms | local plugin or coordinated `junit4-parameterized` plan | unsupported provider/runtime shapes remain explicit rejection cases |
| inherited/delegated Parameterized provider | Supported for proven editable source closures | `JUnit4ParameterizedPlanner` plus coordinated `ParameterizedClass` execution | exact source fingerprints, static delegate closure and supported provider rows are required |
| Parameterized constructor/field injection | Supported for proven closed-source plans | ordered semantic relations plus coordinated `ParameterizedClass` execution | stable binding keys, contiguous field indices and proven argument types; unsupported hooks fail closed |
| JUnit 3 custom harness | Not generally supported | dedicated framework migration required | must not be flattened into annotations |
| public/shared helper API or recursive helper chain | Not yet supported | later #1367 Phase-4 slices | requires stronger dispatch/API/signature proof and complete caller/callee closure |
| automatic JUnit 4/Vintage dependency removal | Not yet supported | planned resource-change phase | allowed only after all source and generated consumers are classified |

## Stable rejection categories

The migration reports actionable reason codes rather than applying a partial rewrite. Representative categories include:

- incomplete source hierarchy or references;
- external or read-only project participant;
- excluded JUnit 3 base type;
- custom suite composition, decorator or harness;
- unsupported Parameterized provider or runner shape;
- mixed JUnit generations;
- stale semantic plan or missing planned target;
- missing required semantic binding.

For closed helper discovery, a helper is deliberately not promoted to a coordinated migration seed when it is public/protected, dynamically dispatchable through a package-private instance method, or contains behavior beyond one direct JUnit 4 assertion/assumption call. Once a helper is accepted, the existing workspace-wide related-source search is authoritative for its caller closure: inaccurate, binary, generated, excluded-root or otherwise non-editable references make the closure incomplete rather than permitting a partial migration.

## Documentation rule

Do not describe the plugin as providing unrestricted “full JUnit 3/4 migration”. The accurate claim is:

> Automated JUnit migration for explicitly modeled local and coordinated source shapes, with fail-closed diagnostics for unsupported execution semantics.

## Executable Parameterized boundary

`JUnit4ParameterizedPlannerTest`, `ParameterizedMigrationDiagnosticsTest` and the coordinated runtime tests exercise the production `junit4-parameterized` planning and execution path. `SemanticRewritePlanFactsTest` checks stable parameter identities in the shared DSL model.

The implementation re-establishes the closed editable scope, compares source fingerprints, re-resolves authorized declarations and uses JDT's JUnit finder/loader adapters to compare ordered test identity, multiplicity, display names and results. The coordinated executor is enabled only when the selected project provides Jupiter's `ParameterizedClass`; unsupported hooks, malformed/ambiguous identities and stale source remain rejection cases. Plans retain stable keys, handles, relations and fingerprints rather than AST nodes.

## Executable helper boundary

`JUnitSharedHelperScopeDetectorTest` covers the first Phase-4 vertical slice:

- caller-selected package-private static assertion helpers are discovered through resolved method bindings;
- a selected private direct wrapper becomes a reverse-reference search seed;
- assertion and assumption options remain independent;
- public, protected and overridable package-private instance helpers are not promoted;
- wrappers with additional behavior are not flattened into the direct-wrapper contract.

The detector does not invent a second call-graph implementation. Accepted helper methods are passed to the shared `RelatedCompilationUnitSearch`, which performs the workspace-wide source reference closure used by other coordinated cleanups. The ordinary `AssertJUnitPlugin` / `AssumeJUnitPlugin` then owns the actual JUnit invocation and import rewrite once the helper compilation unit is part of the closed cleanup scope.

## Roadmap

The detailed implementation roadmap is tracked in #1367. Remaining Phase-4 work includes recursive/shared helper chains and API/signature-sensitive wrappers; Phase 5 owns atomic dependency/resource changes. Existing coordinated migration work remains connected to #1217 and #1334.
