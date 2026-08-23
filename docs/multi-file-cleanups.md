# Coordinated multi-file cleanups

## Purpose

Some source migrations cannot be decided or applied correctly from one Java file:

- converting an integer state domain to an enum changes declarations, signatures, callers, comparisons, and switches;
- migrating a JUnit 4 rule may change a shared resource class and every `@Rule` or `@ClassRule` consumer;
- future API or signature migrations may require coordinated declaration and caller edits.

Sandbox uses the Eclipse cleanup lifecycle rather than introducing a second refactoring engine. A cleanup performs project-wide analysis during precondition checking, retains an immutable semantic plan, and emits the current compilation unit's part of that plan when Eclipse requests its fix.

## Product and host boundary

The Sandbox cleanup bundles remain compatible with stock Eclipse. Their optional multi-file bridges use JDT model objects and JDK collection types and are discovered reflectively, so an unpatched `org.eclipse.jdt.ui` host can still load the bundles.

Candidate-level atomic preview selection is an **optional host capability**, not a stock-Eclipse promise. The dedicated patched-product QA path currently pins:

```text
repository: https://github.com/carstenartur/eclipse.jdt.ui.git
commit: 11268d554d484fb7cc8c73054694d33153aa239c
expected parent: 9965d9c97d21ad61f28e03b9d7e28b7040f7a8d9
bundle: org.eclipse.jdt.ui 3.38.0.*
```

The ordinary Sandbox target and ordinary Help screenshot workflow deliberately remain on stock Eclipse. A separate read-only workflow builds the exact replacement bundle, verifies its compatibility with the stock target, publishes a local feature patch, installs it into the test target, and runs the real Workbench scenarios. Documentation and screenshots must identify this boundary instead of implying that every Eclipse installation enforces atomic candidates.

## Cleanup lifecycle

```text
Initial Java selection
        │
        ├─ optional fixed-point scope expansion in the patched host
        │      └─ related source units are added and validated
        │
        ├─ checkPreConditions(project, completeTargetArray)
        │      └─ create immutable semantic candidates and rejection diagnostics
        │
        ├─ existing JDT batch parser and fixpoint iterator
        │      └─ createFix(context) for each target compilation unit
        │             └─ resolve planned identities again against the current AST
        │
        ├─ existing overlap and fresh-AST handling
        ├─ candidate-aware execution preview in the optional patched host
        └─ one LTK operation with atomic apply and byte-exact Undo
```

The plan must not retain AST nodes. Earlier cleanups may change a working copy between planning and fix creation. Every required declaration, reference, and invocation is resolved again from stable Java-element handles, binding keys, expected identities, and deterministic counts. If a required target is missing or no longer matches, the complete candidate is rejected; implementations must not silently apply only the remaining convenient edits.

## Candidate metadata contract

A coordinated cleanup wrapper may expose `getCoordinatedCleanUpPreview(IJavaProject)` to the optional patched host. Each immutable candidate provides only dependency-free metadata:

- a stable candidate identity;
- a user-facing candidate name;
- the ordered participating compilation units;
- explanatory details, including the atomic-selection statement.

Candidate identity and ordering are deterministic. Disjoint candidates are ordered by stable candidate identity even when per-file LTK changes arrive in another order. The affected-file order supplied by the cleanup is preserved inside the candidate.

The metadata describes a plan that has already passed semantic validation. It is not a second planner, and it does not authorize the host to infer atomicity merely because a cleanup also expands scope.

## Preview and selection contract

With the pinned optional patched JDT UI host, one coordinated migration candidate is one atomic LTK selection unit:

- the preview displays the candidate as one checkbox leaf;
- selecting it shows every required file and the atomicity explanation;
- required file changes and nested edit groups are not independently selectable;
- users may enable or disable disjoint candidates independently;
- disabling one candidate leaves every other candidate unchanged;
- a partially disabled nested change is rejected before execution and by direct perform validation;
- applying a selected candidate changes all participating files in one operation;
- Undo restores all participating files byte-for-byte;
- ordinary local cleanups retain Eclipse's normal file-level and edit-group selection;
- save actions remain single-file and never enter this coordinated preview path.

The canonical SWTBot-generated images are installed with the corresponding Help bundles:

- `sandbox_int_to_enum_help/images/int-to-enum-coordinated-preview.png`;
- `sandbox_junit_cleanup_help/images/junit-coordinated-preview.png`.

Both are reproduced only in the dedicated patched-product workflow. The stock Help workflow preserves the reviewed baselines rather than pretending to reproduce fork-only behavior on an unpatched host.

## Shared implementation boundary

### `sandbox_common_core`

`org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider`
: Optional capability for discovering related compilation units that were not in the initial selection.

Only the small UI-independent SPI is exported from the core bundle. The patched JDT UI host uses reflective capability discovery and has no runtime dependency on Sandbox bundles.

### `sandbox_common`

`org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp<P>`
: JDT-UI-dependent base class that stores one immutable plan per Java project between precondition analysis and per-file fix generation.

`org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult<P>`
: Carries the plan and its `RefactoringStatus` diagnostics.

`org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits`
: Deterministically collects source compilation units in a Java project.

`org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan`
: Minimal immutable plan containing project and compilation-unit handles.

Keeping implementation and plan classes together in `sandbox_common` avoids an OSGi split package. A package imported by another bundle is wired to one exporter and must not be assembled from classes exported by both common bundles.

The extension-point wrapper, rather than only its core implementation, must forward every optional host capability because Eclipse registers the wrapper instance.

## Scope expansion

The maintained JDT UI fork adds a deliberately narrow enhancement to `CleanUpRefactoring`:

1. detect cleanups exposing scope expansion;
2. ask them for additional compilation units after cleanup options are installed;
3. merge and de-duplicate results until the target set reaches a fixed point;
4. reject missing, non-source, or cross-project units;
5. feed the expanded scope into the unchanged cleanup pipeline.

Scope expansion and atomic preview selection are separate contracts. A scope provider does not automatically make every generated change atomic; the cleanup must explicitly expose stable coordinated candidate metadata.

## Implemented consumers

### Int-to-Enum

Two paths are implemented:

1. A local detector migrates a private closed integer state flow inside one compilation unit.
2. `IntEnumMultiFilePlanner` detects a conservative package-scoped state API when the complete source project is available. It migrates package-private `static final int` constants, the package-private method parameter and its equality tests, and callers in other compilation units.

The cross-file plan records constant, type, method, and parameter identities through binding keys and Java-element handles. Before rewriting each file it verifies expected reference and invocation counts against the current AST. The project-wide path rejects public or protected APIs, type hierarchies, arbitrary integer arguments, aliases, arithmetic, bit flags, unresolved uses, persistence or protocol semantics, incomplete source scope, and generated enum names that conflict with an existing nested type.

The real Workbench scenario proves that the coordinated declaration and caller changes appear as one atomic candidate, deselecting it changes no file, finishing changes both files, and Undo restores both files exactly.

### JUnit migration

`JUnitMultiFilePlanner` implements the first coordinated JUnit migration:

- a named class directly extending JUnit 4 `ExternalResource` may be declared in one file;
- one or more fields in the resource file or other selected test files may use it through `@Rule` or `@ClassRule`;
- consumer fields become Jupiter `@RegisterExtension` fields in their own compilation units;
- the resource class becomes the corresponding before/after callback implementation in its own compilation unit;
- mixed instance and class-rule use of one resource type is rejected because one callback lifecycle cannot represent both safely.

The old local helper is prevented from editing planned declarations, so an AST node from one compilation unit is never passed to another file's `ASTRewrite`.

The real Workbench fixture creates two disjoint resource-and-consumer candidates. It proves that both appear as atomic leaves, can be selected independently, expose their ordered affected files and explanation, apply only the selected candidate, leave the deselected candidate byte-identical, and Undo the selected multi-file migration exactly.

## Strict and best-effort planning

Strict planning is fail closed: an incomplete or unsupported candidate is not emitted.

JUnit best-effort mode may retain one independently proven candidate while rejecting another candidate and producing explicit manual-completion diagnostics. It does **not** make the files inside one retained coordinated candidate partially selectable. Candidate independence and within-candidate atomicity are separate properties.

## Save actions and headless execution

The save participant supplies only the saved compilation unit and does not run scope expansion. This is intentional. Local semantics-preserving transformations may remain available as save actions; project-wide API migrations require an explicit cleanup run with preview.

A headless caller must add every participating compilation unit to one `CleanUpRefactoring`. Running a separate refactoring per file prevents coordinated planning and atomic execution. The current `sandbox_cleanup_application` still executes one refactoring per file; its project-wide transaction conversion remains tracked in #1210. Until that work is complete, coordinated project-wide plans must not be advertised as a safe command-line capability.

## Verification

The merged contract is protected at several levels:

- headless wrapper tests verify the reflective metadata signatures and candidate contents;
- planner and lifecycle tests cover stale plans, rejection diagnostics, ordering, and retained-state cleanup;
- JDT-side tests cover fail-closed partial activation, direct perform, disjoint and overlapping candidates, local selection behavior, apply, and Undo;
- Sandbox SWTBot tests drive the real **Source > Clean Up...** wizard for Int-to-Enum and JUnit;
- the workflow verifies that both SWTBot methods executed;
- reviewed screenshots are compared with narrow GTK-only rendering tolerances while any content-area change fails;
- patch source commit, parent, bundle hash, target compatibility, local p2 repository, and screenshot evidence are retained as artifacts.

## Remaining work

The current capability is deliberately closed-scope, not a claim that arbitrary project-wide API migration is solved. Important follow-ups include:

1. complete project-wide transactions in the headless cleanup application (#1210);
2. widen Int-to-Enum only after visibility, compatibility, qualification, and generated-name policies are proven (#1216, #1223, #1225);
3. extend coordinated JUnit migration incrementally (#1217);
4. keep scope discovery candidate-driven and bounded (#1212, #1221, #1224);
5. preserve truthful release and Help boundaries for the optional patched product (#1451, #1455, #1456).

The broader dependency-ordered roadmap remains tracked by #1229.
