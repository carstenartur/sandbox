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

## Current consumers

### Int-to-enum

The cleanup now participates in the planned lifecycle and can request the full project source scope. Its current local detector still performs the implemented private, closed-data-flow transformation. The next layer is an `IntEnumMigrationPlan` for public/package declarations, interfaces, overrides, and callers.

### JUnit migration

The JUnit cleanup uses the same lifecycle and project scope. Existing transformations continue to produce their local changes. The planned layer is the foundation for shared `ExternalResource` types, test hierarchies, suites, method sources, helper APIs, and dependency validation.

## Headless execution

A headless caller must add all participating compilation units to one `CleanUpRefactoring`. Running a separate refactoring per file prevents coordinated planning.

The command-line cleanup application is being moved to project-batched execution so `APPLY`, `CHECK`, and `DIFF` operate on the same atomic multi-file change tree.

## Testing requirements

At minimum, test:

- all selected units are visible during planning;
- the same cleanup instance is used for every local fix;
- expanded units enter preconditions, preview, validation, apply, and undo;
- duplicate provider results do not duplicate targets;
- transitive discovery reaches a fixed point;
- cross-project and missing targets abort the run;
- plan state is cleared after success and failure;
- a stale local target prevents partial migration;
- save actions remain single-file;
- ordinary cleanups are unchanged when no provider is present.
