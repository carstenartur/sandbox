# Candidate-driven scope for coordinated cleanups

## Purpose

A coordinated cleanup must update every source location whose Java semantics change, but it must not parse or edit an entire project merely because one selected file contains a possible candidate.

The JUnit ExternalResource and project-wide Int-to-Enum cleanups therefore derive a binding-indexed source closure from the selected candidate before their semantic planners run.

This layer builds on the source-root policy documented in `multi-file-source-root-policy.md`. A reference can be part of the closure only when its compilation unit is editable under that policy.

## Processing stages

1. **Selected-scope candidate scan**
   - Parse only the explicitly selected compilation units.
   - Detect structural candidates.
   - Resolve the exact JDT Java elements that can change meaning.

2. **Workspace-wide reference search**
   - Search exact references through JDT Search across the workspace.
   - Include the declaring compilation units and every accurate source reference.
   - Retain deterministic Java-element-handle ordering.

3. **Policy filter and closure decision**
   - Accept only source units in the same Java project and in permitted production/test roots.
   - Reject binary, external-project, generated, derived, output, inaccurate, and otherwise non-source matches.
   - Record a closed-scope decision only when all observed references can be represented by editable source units.

4. **Semantic planning**
   - Parse only the proven closed source subset.
   - Preserve the planners' original complete-project entry points for explicit callers and compatibility tests.
   - Produce no coordinated migration when the scope decision is rejected.

## Staged migration invariant

A cleanup may be run repeatedly with different option selections. Candidate discovery must therefore describe semantic roles rather than only the syntax that happened to exist before the first rewrite.

For safely migratable source, applying option set **A** and then option set **B** must reach the same semantic state as applying **A ∪ B** together. When the first pass cannot preserve the edges required by a later pass, it must remain unchanged and refuse the incomplete migration instead of making it irreversible.

The JUnit cleanup implements this invariant by treating pre- and post-migration forms as equivalent scope anchors:

- `@Before` and `@BeforeEach`, `@After` and `@AfterEach`, `@BeforeClass` and `@BeforeAll`, and `@AfterClass` and `@AfterAll` identify the same lifecycle hierarchy role;
- JUnit 4 `@SuiteClasses` and JUnit Platform `@SelectClasses` identify the same direct suite-membership edges;
- named `ExternalResource` declarations and their `@Rule`/`@ClassRule` fields form one atomic migration feature, regardless of which side's preference was selected;
- a JUnit 4 `Parameterized` runner, its provider/constructor state, and its test/lifecycle/Rule annotations remain one execution component. A later repair pass accepts both JUnit 4 and already migrated Jupiter `@Test` annotations.

The target-side annotations are deliberately retained as discovery anchors. Removing a legacy marker is therefore not allowed to hide remaining source from a later cleanup run.

## Cleanup-specific search seeds

### Int-to-Enum

For each selected structural candidate, the search seeds are:

- every candidate package-private `static final int` constant;
- every candidate package-private method whose `int` parameter would become the generated enum type.

The closure therefore includes the owner and every accurate source caller/reference user of the changed constants and method signatures.

### JUnit ExternalResource

The declaration-side and field-side options are normalized to one coordinated feature. Enabling either option computes the same closure and prevents a selected declaration from losing its `ExternalResource` contract before all Rule users are known.

For each selected candidate, the search seed is the concrete resource type:

- a selected class directly extending JUnit 4 `ExternalResource`; or
- the resolved declared type of a selected `@Rule` or `@ClassRule` field.

The closure therefore includes the resource declaration and every accurate source use that must move to Jupiter extension semantics.

### JUnit lifecycle hierarchies

When an enabled lifecycle migration is visible in the selected type or one of its source supertypes, the cleanup identifies the highest lifecycle-declaring source root and adds every source subtype. The detector accepts both JUnit 4 and Jupiter annotations, so a base class migrated in an earlier pass still pulls in unmigrated overriding subclasses.

Only the hierarchy is added. Ordinary fields, parameters, imports, and unrelated references to a lifecycle base do not broaden the scope.

### JUnit suites

A selected `@SuiteClasses` or `@SelectClasses` annotation adds every directly referenced source test class. Suite membership remains a scope edge even when the current pass enables a different JUnit migration component from the pass that converted the suite annotation.

### JUnit Parameterized execution components

A JUnit 4 `Parameterized` class is not split by granular annotation choices. Structural rewrites are quarantined until the Parameterized migration is selected and its complete local provider/constructor contract is eligible. Assertion and assumption rewrites may still proceed because they do not change test discovery or execution lifecycle.

For recovery from source produced by an older cleanup version, the Parameterized rewrite recognizes both `org.junit.Test` and `org.junit.jupiter.api.Test` before replacing either with `@ParameterizedTest`.

## Fallback and refusal rules

### No candidate

No additional compilation unit is requested. Ordinary selected-file cleanup behavior remains local.

### Candidate bindings unavailable

When the selected syntax clearly indicates a candidate but JDT cannot recover all seed elements, the cleanup preserves the previous conservative fallback and requests the complete source-root-policy scope. The semantic planner still performs its normal binding validation before generating edits.

### Search result not representable as editable project source

The coordinated migration is refused when any exact closure search reports:

- a binary reference;
- a reference in another Java project;
- a generated, derived, output, or excluded source root;
- an inaccurate JDT match;
- a match without a Java-model compilation unit;
- a candidate declaration outside the permitted policy scope.

The cleanup does not silently broaden to unrelated workspace content and does not produce a partial migration.

## Host integration and lifecycle ownership

`IMultiFileCleanUpScopeProvider` is an optional integration point. A host that supports candidate-driven expansion must call `expandCleanUpScope(...)`, add the returned compilation units, and repeat until the provider reaches its fixed point.

The ordinary unpatched Eclipse `CleanUpRefactoring` does not call this provider API. Callers using that standard lifecycle must therefore supply the complete coordinated compilation-unit scope explicitly. The original complete-scope planner entry points remain supported for this path.

Scope decisions are stored only for the serialized cleanup lifecycle established by `AbstractPlannedMultiFileCleanUp`:

- a pending handle set describes the source closure requested from a supporting host;
- a verified handle set records the fixed point once those units are present;
- a rejected decision forces the coordinated planner to return an empty migration plan;
- the decision is consumed and cleared when planning begins.

## Verification

The common-layer tests cover deterministic admission and fail-closed handling for:

- accurate permitted references;
- inaccurate matches;
- binary/non-source matches;
- other-project matches;
- policy-excluded source units;
- declarations outside the allow-list.

The cleanup scope tests start from narrowly selected sources and verify that:

- the exact Int-to-Enum caller closure is returned;
- the exact JUnit Rule-user closure is returned from either ExternalResource-side preference;
- legacy and already migrated lifecycle bases return the same source hierarchy closure;
- both `@SuiteClasses` and `@SelectClasses` return the same direct member closure;
- unrelated editable source is not admitted;
- each closure is emitted once and reaches a stable fixed point.

The lifecycle tests exercise the ordinary Eclipse path with an explicit complete scope and verify that:

- all semantically coupled sources are migrated atomically;
- a later pass can still annotate overrides of an already migrated lifecycle base;
- a Test-only pass cannot detach methods from a remaining JUnit 4 Parameterized runner, while a later Parameterized pass can repair an already-Jupiter `@Test`;
- selecting only one ExternalResource-side option still migrates the declaration and Rule field together;
- unrelated selected source remains unchanged;
- apply and undo preserve the complete verified source/error baseline.

Related issues: #1212, #1214, #1221, #1224, #1485.
