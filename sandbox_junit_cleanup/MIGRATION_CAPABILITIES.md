# JUnit migration capabilities

This matrix describes the executable migration boundary on `main`. It deliberately distinguishes local syntax rewrites from coordinated semantic migrations. A green checkbox means that the listed shape has production code and active tests; it does not mean that every use of the corresponding JUnit feature is migratable.

## Safety model

JUnit migration uses three layers:

1. **Semantic planner** — discovers a closed source scope, resolves bindings, classifies execution semantics and emits stable rejection reasons.
2. **Plan-aware hint program** — performs only planner-authorized local AST rewrites. Every plan-aware program must declare both:

   ```text
   <!binding-policy: required>
   <!requires-plan: contract-id>
   ```

3. **Runtime oracle** — compares JDT JUnit discovery/execution before and after migration where the migration changes test identity or multiplicity.

A missing binding, binary-only participant, stale plan, incomplete project scope or unsupported execution hook prevents the complete migration unit from being produced.

## Capability matrix

| Source shape | Status | Execution path | Important boundary |
|---|---:|---|---|
| JUnit 4 lifecycle annotations | Supported | local cleanup / declarative rewrite | mixed or custom lifecycle semantics may require coordinated planning |
| JUnit 4 assertions and assumptions | Supported for classified overloads | local cleanup | message/delta overloads require resolved method/type semantics |
| `@Test(expected=...)` | Supported | imperative body rewrite | wraps the proven method body in `assertThrows` |
| `@Test(timeout=...)` and supported timeout rules | Supported | local structured rewrite | custom timeout wrappers remain unsupported |
| `TemporaryFolder`, `TestName`, supported `ExternalResource` rules | Supported | local or coordinated rule migration | external/binary resource classes and mixed rule scopes fail closed |
| transitive `ExternalResource` fixture chains | Supported | multi-file semantic plan | every editable fixture and consumer project must be in scope |
| ordinary closed JUnit 3 hierarchy | Supported | `junit3-hierarchy` semantic plan plus plan-aware DSL | constructors, name/result hooks, decorators and custom harness references are rejected |
| simple JUnit 3 `suite()` aggregator | Supported | fail-closed suite model | only plain top-level aggregator types and modeled composition forms |
| JUnit 4 `@RunWith(Suite.class)` / class selection | Supported for modeled forms | suite cleanup | custom runners and dynamic composition require a dedicated contract |
| local JUnit 4 Parameterized provider | Narrow support | local plugin | only the provider/body shapes explicitly accepted by the planner |
| external/inherited Parameterized provider | Not yet supported | planned in #1367 | requires caller/provider/constructor/field relations and runtime multiplicity checks |
| JUnit 3 custom harness | Not generally supported | dedicated framework migration required | must not be flattened into annotations |
| shared assertion helper API across projects | Not yet supported | planned in #1367 | requires complete caller/callee closure |
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

## Documentation rule

Do not describe the plugin as providing unrestricted “full JUnit 3/4 migration”. The accurate claim is:

> Automated JUnit migration for explicitly modeled local and coordinated source shapes, with fail-closed diagnostics for unsupported execution semantics.

## Roadmap

The detailed implementation roadmap is tracked in #1367. Existing coordinated migration work remains connected to #1217 and #1334.
