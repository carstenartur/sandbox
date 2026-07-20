# Planned multi-file cleanups

## Purpose

Some source migrations cannot be decided correctly from one Java file:

- converting an integer state domain to an enum changes declarations, signatures, callers, and comparisons;
- migrating JUnit 4 rules or lifecycle contracts may change base classes, subclasses, suites, shared resources, and consuming tests;
- removing or changing an API requires coordinated edits in every selected compilation unit.

The Sandbox implementation uses the existing Eclipse cleanup lifecycle instead of introducing a second refactoring engine.

## What the existing cleanup API already provides

For each Java project in an explicit cleanup run, Eclipse:

1. creates one cleanup instance;
2. calls `checkPreConditions(IJavaProject, ICompilationUnit[], ...)` with all target compilation units;
3. invokes that same cleanup instance once per target through `createFix(CleanUpContext)`;
4. collects the returned `CompilationUnitChange` objects;
5. presents one preview and applies and undoes the complete LTK change tree atomically.

Therefore a cleanup can perform project-wide analysis in `checkPreConditions`, retain an immutable semantic plan, and return only the current file's part of that plan from `createFix`.

## Shared classes

### `sandbox_common_core`

`org.sandbox.jdt.cleanup.multifile.IMultiFileCleanUpScopeProvider`
: Optional capability for discovering related compilation units that were not in the initial selection.

`org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits`
: Deterministically collects all source compilation units in a Java project.

`org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan`
: Minimal immutable plan containing project and compilation-unit handles.

The package has no dependency on JDT UI. This keeps it usable by headless analysis code and avoids a circular dependency with a patched `org.eclipse.jdt.ui` bundle.

### `sandbox_common`

`org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp<P>`
: JDT-UI-dependent base class that stores one immutable plan per Java project between precondition analysis and per-file fix generation.

`org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult<P>`
: Carries the plan and its `RefactoringStatus` diagnostics.

## Lifecycle

```text
Initial Java selection
        │
        ├─ optional scope expansion in patched CleanUpRefactoring
        │      └─ repeat until no provider adds a compilation unit
        │
        ├─ checkPreConditions(project, completeTargetArray)
        │      └─ create immutable semantic migration plan
        │
        ├─ existing JDT batch parser and fixpoint iterator
        │      └─ createFix(context) for each target
        │             └─ resolve this unit's planned edits against the current AST
        │
        ├─ existing overlap and fresh-AST handling
        ├─ existing resource validation and preview
        └─ one atomic apply and undo
```

## Implementing a planned cleanup

```java
public final class ExampleCleanUp
        extends AbstractPlannedMultiFileCleanUp<ExamplePlan> {

    @Override
    protected MultiFileCleanUpPlanResult<ExamplePlan> createPlan(
            IJavaProject project,
            ICompilationUnit[] units,
            IProgressMonitor monitor) throws CoreException {
        ExamplePlan plan = ExamplePlanner.analyse(project, units, monitor);
        return plan.isEmpty()
                ? MultiFileCleanUpPlanResult.noPlan()
                : MultiFileCleanUpPlanResult.success(plan);
    }

    @Override
    protected ICleanUpFix createFixForPlan(
            ExamplePlan plan,
            CleanUpContext context) throws CoreException {
        return plan.createFixFor(context);
    }

    @Override
    protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(
            IJavaProject project,
            Collection<ICompilationUnit> currentScope,
            IProgressMonitor monitor) throws CoreException {
        return ExampleScopeDiscovery.findRelated(project, currentScope, monitor);
    }
}
```

The extension-point wrapper must forward `expandCleanUpScope(...)` because Eclipse registers the wrapper rather than the core implementation.

## Plan rules

A production plan should contain:

- Java element handles and binding keys;
- generated names and compatibility choices;
- explicit expected declarations and references;
- a deterministic list of edits per compilation unit;
- rejection diagnostics for incomplete or unsafe candidates.

A plan must not retain AST nodes. The AST used during planning is not necessarily the AST later supplied to `createFix`; earlier cleanups may already have changed a working copy.

For each file, `createFixForPlan` must resolve planned declarations and references again. If a required target is missing or no longer matches, throw a `CoreException` or otherwise reject the complete coordinated candidate. Do not silently apply only the edits that remain convenient.

## Scope expansion patch

The Sandbox JDT UI fork adds a deliberately small enhancement to `CleanUpRefactoring`:

1. detect cleanups exposing `expandCleanUpScope(...)`;
2. ask them for additional compilation units after cleanup options are installed;
3. merge and de-duplicate results until the target set reaches a fixed point;
4. reject missing or cross-project units;
5. feed the expanded scope into the unchanged cleanup pipeline.

The implementation currently uses reflective capability discovery so the patched JDT UI bundle does not depend on Sandbox bundles and no second patch of `org.eclipse.jdt.core.manipulation` is needed. The typed Sandbox interface documents and tests the contract.

See `carstenartur/eclipse.jdt.ui#94`.

## OSGi product integration

`org.eclipse.jdt.ui` is a singleton bundle. A fragment cannot reliably override an existing host class because host classes are resolved before attached fragment classes.

Use a patched, higher-version `org.eclipse.jdt.ui` bundle in the Sandbox target/product or distribute it through a p2 feature patch. The bundle keeps its symbolic name and compatible package exports and dependencies.

No JDT UI patch is required when users explicitly run cleanup on a complete project, source folder, package, working set, or multi-file selection. The patch is only needed to add related files outside that original target set automatically.

## Save actions

The save participant supplies only the saved compilation unit and does not run the scope-expansion phase. This is intentional.

Local, semantics-preserving transformations may remain available as save actions. Project-wide API migrations should require an explicit cleanup run with preview; saving one editor must not silently rewrite unrelated files.

## Implemented consumers

### Int-to-enum

Two paths are implemented:

1. A local detector migrates a private closed integer state flow inside one compilation unit.
2. `IntEnumMultiFilePlanner` detects a conservative package-scoped state API when the complete Java source project is in scope. It migrates package-private `static final int` constants, the package-private method parameter and its equality tests, and callers in other compilation units.

The cross-file plan records constant, type, method, and parameter identities through binding keys and Java-element handles. Before rewriting each file it verifies expected reference and invocation counts against the current AST. The current project-wide path deliberately rejects public/protected APIs, type hierarchies, arbitrary integer arguments, aliases, arithmetic, bit flags, unresolved uses, persistence/protocol semantics, and incomplete project selections.

### JUnit migration

`JUnitMultiFilePlanner` implements the first coordinated JUnit migration:

- a named class directly extending JUnit 4 `ExternalResource` may be declared in one file;
- one or more selected test files may use it through `@Rule` or `@ClassRule` fields;
- rule fields become Jupiter `@RegisterExtension` fields in their own compilation units;
- the resource class becomes the corresponding Before/After Each or Before/After All callback implementation in its own compilation unit;
- mixed instance and class-rule use of one resource type is rejected because one callback lifecycle cannot represent both safely.

The old local helper is prevented from editing the planned declarations, so no AST node from one compilation unit is ever passed to another file's `ASTRewrite`.

Further planned consumers include test hierarchies, suites, shared helper APIs, method sources, and dependency validation.

## Headless execution

A headless caller must add all participating compilation units to one `CleanUpRefactoring`. Running a separate refactoring per file prevents coordinated planning.

The current `sandbox_cleanup_application` still executes one refactoring per file. Its batching conversion is tracked separately from the IDE cleanup implementation and must be completed before the new project-wide plans are advertised for command-line use.

## Testing requirements

Implemented tests cover:

- planning before local fixes and cleanup of retained state;
- fatal planning diagnostics;
- target-scope expansion into one unified preview in the JDT UI fork;
- a named `ExternalResource` plus `@Rule` consumer in another file;
- a named `ExternalResource` plus `@ClassRule` consumer in another file;
- a package-scoped integer state method plus caller in another file.

Additional required coverage remains:

- duplicate provider results and transitive fixed-point discovery;
- cross-project and missing targets;
- stale-plan rejection after an earlier cleanup changes a target;
- one atomic apply and undo in PDE integration tests;
- save-action isolation;
- ordinary cleanups unchanged when no provider is present.
