# Multi-file cleanup cheatsheet

## Choose the right mechanism

| Requirement | Mechanism |
|---|---|
| Change one Java file | Ordinary `ICleanUp#createFix` |
| Analyse and change several already selected Java files | `AbstractPlannedMultiFileCleanUp` |
| Automatically add related Java files outside the selection | Planned cleanup + patched `org.eclipse.jdt.ui` scope expansion |
| Create/delete Java files | Dedicated LTK refactoring or a future arbitrary-`Change` cleanup extension |
| Change Maven/PDE/module/non-Java resources | Dedicated LTK refactoring |
| Run while saving one editor | Single-file cleanup only |

## Minimal implementation

```java
final class MyCleanUp extends AbstractPlannedMultiFileCleanUp<MyPlan> {
    @Override
    protected MultiFileCleanUpPlanResult<MyPlan> createPlan(
            IJavaProject project,
            ICompilationUnit[] units,
            IProgressMonitor monitor) throws CoreException {
        return MultiFileCleanUpPlanResult.success(
                MyPlanner.create(project, units, monitor));
    }

    @Override
    protected ICleanUpFix createFixForPlan(
            MyPlan plan,
            CleanUpContext context) throws CoreException {
        return plan.fixFor(context.getCompilationUnit(), context.getAST());
    }

    @Override
    protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(
            IJavaProject project,
            Collection<ICompilationUnit> currentScope,
            IProgressMonitor monitor) throws CoreException {
        return MyScopeFinder.findRelated(project, currentScope, monitor);
    }
}
```

## Wrapper forwarding

The extension registry creates the UI wrapper, not the core implementation:

```java
public final class MyCleanUpWrapper
        extends AbstractCleanUpCoreWrapper<MyCleanUp>
        implements IMultiFileCleanUpScopeProvider {

    @Override
    public Collection<ICompilationUnit> expandCleanUpScope(
            IJavaProject project,
            Collection<ICompilationUnit> currentScope,
            IProgressMonitor monitor) throws CoreException {
        return cleanUpCore.expandCleanUpScope(project, currentScope, monitor);
    }
}
```

Without forwarding, the patched JDT UI orchestrator cannot see the provider.

## Plan contents

Store:

- `IJavaElement#getHandleIdentifier()` values;
- binding keys from declarations;
- qualified type and method signatures;
- generated enum/extension names;
- immutable edit descriptors grouped by compilation-unit handle;
- explicit reasons a candidate was rejected.

Do not store:

- `ASTNode` instances;
- `ASTRewrite` instances;
- mutable `CompilationUnit` trees;
- editor documents or working copies;
- process-global static plan state.

## Planning checklist

- [ ] Options were set before scope discovery and planning.
- [ ] The monitor is checked for cancellation.
- [ ] Every affected source file is in the target scope.
- [ ] Binary/external/unresolved references have an explicit policy.
- [ ] Public APIs have a compatibility strategy.
- [ ] Duplicate or aliased integer values are handled explicitly.
- [ ] Bit flags are rejected or migrated to `EnumSet` semantics.
- [ ] Persistence and wire values never depend on `enum.ordinal()`.
- [ ] The plan is immutable and deterministic.
- [ ] Planning produces useful `RefactoringStatus` diagnostics.

## Fix-generation checklist

- [ ] Resolve each planned target against the current AST.
- [ ] Verify the binding key/signature before editing.
- [ ] Use one `CompilationUnitChange` per file.
- [ ] Use `ASTRewrite` and `ImportRewrite` belonging to that file.
- [ ] Never pass AST nodes from another compilation unit to a rewrite.
- [ ] Reject a coordinated candidate if one required local edit is stale.
- [ ] Avoid overlapping edits with earlier cleanups.
- [ ] Request a fresh AST or run late when previous cleanups can invalidate the plan.

## Scope provider contract

```java
Collection<ICompilationUnit> expandCleanUpScope(
    IJavaProject project,
    Collection<ICompilationUnit> currentScope,
    IProgressMonitor monitor) throws CoreException;
```

Rules:

- return an empty collection when no expansion is needed;
- returning current units again is allowed;
- results are merged and de-duplicated;
- the method may be called repeatedly until no unit is added;
- return only existing units from the supplied project;
- do not modify source or create working copies;
- throw on analysis failure instead of returning a partial unsafe scope.

## Common mistakes

### Building a `CompositeChange` inside `createFix`

Do not. `ICleanUpFix` remains local to one compilation unit. Return one local change and let `CleanUpRefactoring` combine all files.

### Holding AST nodes from `checkPreConditions`

Do not. The later fixpoint may use a fresh AST containing edits from earlier cleanups.

### Rewriting a type declaration from another AST

Do not pass a node found in `Other.java` to the `ASTRewrite` for `Test.java`. Add `Other.java` to the target scope and create its edit when its own `CleanUpContext` arrives.

### Using static global maps

Do not. They leak across projects, save actions, tests, cancellation, and parallel workbench operations. Keep run state in the cleanup instance and clear it in postconditions.

### Enabling project-wide migration as a save action

Do not. Save actions intentionally receive only the saved file and have no unified preview.

## Int-to-enum notes

- Use explicit numeric fields and `fromValue(int)` when numeric identity matters.
- Never replace persisted values with `ordinal()`.
- Analyse method hierarchies and callers before changing a signature.
- Treat bitwise operators as evidence for flags, not an ordinary enum.

## JUnit migration notes

Coordinate:

- base test classes and subclasses;
- inherited lifecycle methods;
- suites and referenced test classes;
- shared `ExternalResource` implementations and every rule field;
- helper assertion/assumption APIs and their callers;
- method-source providers;
- JUnit 4 dependency removal only after the selected migration component is closed.

## Useful tests

```text
initial target A
scope provider adds B
B reveals C
preconditions receive A, B, C
one file-local change is produced for each affected unit
preview contains all changes
one failure prevents partial application
undo restores all units
```
