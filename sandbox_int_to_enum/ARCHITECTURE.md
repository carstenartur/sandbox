# Architecture: Int-to-Enum Refactoring

## Purpose

The plugin identifies legacy Java designs in which integers encode a finite state domain and replaces provably safe cases with an enum.

This is a semantic refactoring rather than a textual pattern replacement. Integer constants may represent states, bit masks, protocol values, persisted identifiers, error codes, array indexes, or arithmetic values. A valid migration must therefore prove how declarations and values flow through the program before changing their type.

## Transformation layers

### 1. Implemented local detector

The first implementation recognises candidates whose complete relevant data flow is contained in one compilation unit and is private.

```java
private static final int STATUS_PENDING = 0;
private static final int STATUS_APPROVED = 1;

private void process(int status) {
    if (status == STATUS_PENDING) {
        handlePending();
    } else if (status == STATUS_APPROVED) {
        handleApproved();
    }
}
```

The detector validates bindings, constant values, all parameter references, all constant references, and every call to the private method. It then generates a private nested enum and rewrites the proven call sites and comparisons.

The if/else structure is preserved. Conversion to switch is not required for type safety and can introduce control-flow hazards involving unlabeled `break`, `continue`, abrupt completion, or unreachable statements.

### 2. Selected-scope multi-file cleanup

The standard cleanup API is per-file only at the final edit boundary, but its lifecycle is project-aware:

1. `ICleanUp.checkPreConditions(IJavaProject, ICompilationUnit[], ...)` receives every compilation unit selected for the project.
2. The same cleanup instance is invoked later through `createFix(CleanUpContext)` for each target unit.
3. Each fix returns a local `CompilationUnitChange`.
4. `CleanUpRefactoring` collects all local changes into one LTK change tree, validates all modified resources, previews them together, and applies and undoes them atomically.

Consequently, a project-wide semantic plan can be calculated without changing JDT UI when all affected compilation units are already selected. The cleanup stores an immutable plan keyed by Java project, then emits only the current file's part of that plan from `createFix`.

The plan must store stable Java element handles, binding keys, and semantic edit descriptors rather than retaining AST nodes between parser passes. The current AST may already include edits from earlier cleanups in the fixpoint pipeline, so every planned edit must be re-resolved and validated before it is emitted.

### 3. Automatic target-scope expansion

The existing lifecycle cannot add compilation units that were not part of the original cleanup selection. This matters when cleanup starts on one class or package but analysis discovers callers, interfaces, implementations, suites, or shared helpers elsewhere in the project.

A minimal patched `org.eclipse.jdt.ui` bundle can extend `CleanUpRefactoring` before precondition checking:

1. detect cleanups implementing a Sandbox-owned scope-provider SPI;
2. request additional related compilation units;
3. compute a transitive, deduplicated target closure;
4. add those units to the existing target set;
5. continue through the unchanged precondition, batch parser, working-copy fixpoint, overlap handling, preview, validation, apply, and undo pipeline.

Example experimental SPI in `sandbox_common`:

```java
public interface IMultiFileCleanUpScopeProvider {
    Collection<ICompilationUnit> expandScope(
        IJavaProject project,
        Collection<ICompilationUnit> initialScope,
        IProgressMonitor monitor) throws CoreException;
}
```

This approach is deliberately narrower than making a cleanup return an arbitrary `CompositeChange`. The current cleanup refactoring already knows how to combine per-file changes correctly; the missing capability is mainly target discovery.

### 4. Dedicated LTK refactoring

A dedicated refactoring remains useful when the operation needs interactive choices such as enum naming, compatibility bridge methods, preservation of integer adapters, persistence mappings, or partial/excluded source roots. It is not required merely to coordinate edits across Java files already included in a cleanup run.

## Package structure

### `org.sandbox.jdt.internal.corext.fix`

- `IntToEnumFixCore` selects transformation helpers and creates rewrite operations.

### `org.sandbox.jdt.internal.corext.fix.helper`

- `AbstractTool<T>` defines detection, rewrite, and preview hooks.
- `IntToEnumHelper` implements conservative binding-based if/else detection and migration.
- `SwitchIntToEnumHelper` implements the existing switch prototype.

### `org.sandbox.jdt.internal.ui.fix`

- `IntToEnumCleanUp` is the UI wrapper.
- `IntToEnumCleanUpCore` integrates with the cleanup lifecycle.

### `sandbox_common`

The planned multi-file infrastructure should live here so it can be shared by `sandbox_int_to_enum`, `sandbox_junit_cleanup`, and later migrations.

Proposed reusable concepts:

- `AbstractPlannedMultiFileCleanUp<P>` or an equivalent composition-based coordinator;
- `PlanResult<P>` containing an immutable plan and `RefactoringStatus`;
- per-compilation-unit edit descriptors;
- explicit rejection diagnostics;
- optional `IMultiFileCleanUpScopeProvider` for the patched JDT UI bundle.

## If/else candidate analysis

### Constant group discovery

The current detector collects fields that are:

- `private`;
- `static`;
- `final`;
- primitive `int`;
- compile-time constants with an `Integer` value.

Candidate constants must share an underscore-delimited prefix, such as `STATUS_`. The prefix produces the enum type name and the suffix produces each enum constant name.

The detector rejects:

- fewer than two constants;
- duplicate numeric values;
- invalid generated identifiers;
- a generated enum name that conflicts with an existing nested type.

### State-carrier discovery

The recognised state carrier is currently a plain `int` parameter of a private method. Every branch condition in the root if/else-if chain must be an equality comparison between the same parameter binding and one of the constant bindings. Operand order may be reversed.

```java
status == STATUS_PENDING
STATUS_APPROVED == status
```

More complex boolean expressions are rejected rather than partially rewritten.

### Whole-reference validation

Before producing a rewrite operation, the detector traverses the complete compilation unit and proves that:

- the state parameter is referenced only by the recognised comparisons;
- group constants are referenced only by their declarations, recognised comparisons, and proven call arguments;
- every call to the private method passes a constant from the group;
- the method is not used through a method reference or another unsupported construct.

A single unsupported reference rejects the entire candidate. The cleanup never performs a partial migration.

### Rewrite

For an accepted candidate, one `CompilationUnitRewriteOperationWithSourceRange`:

1. inserts a private nested enum before the first migrated field;
2. removes complete constant field declarations or only the migrated fragments;
3. changes the private method parameter from `int` to the enum type;
4. replaces constant references in comparisons with qualified enum constants;
5. replaces every proven call argument with the corresponding enum constant.

## Project-wide candidate model

The next step is to extract discovery from `IntToEnumHelper` into an immutable semantic model containing:

- constant group and numeric-value semantics;
- declarations and generated names;
- state carriers;
- comparison and switch sites;
- producer and consumer edges;
- required edits per compilation unit;
- compatibility boundaries;
- explicit rejection reasons and confidence level.

Project-wide analysis must consider:

- JDT search results across source roots;
- method hierarchies, interfaces, overrides, and implementations;
- callers, assignments, fields, locals, return values, and method references;
- persisted and wire-format integer values;
- reflection, JNI, generated code, and external binaries;
- compatibility adapters or deprecated bridge constants.

Where numeric identity must remain stable, the generated enum must use an explicit value field and conversion method rather than `ordinal()`.

## Ordering and fixpoint behaviour

A multi-file migration plan can become stale if unrelated cleanups structurally change the same sources before it runs. The implementation must therefore:

- use cleanup ordering (`runAfter`) so semantic migrations run late;
- request fresh ASTs where necessary;
- resolve planned locations by binding key or Java model handle against the current working copy;
- fail the whole candidate if any required edit no longer matches;
- clear all per-run plan state after success, failure, or cancellation.

## Save-action boundary

The JDT save participant calls `checkPreConditions` with only the saved compilation unit. Multi-file migration must therefore not run as a save action. Local transformations may remain available on save, while project-wide planning is enabled only for explicit cleanup runs or the headless batch application.

## Headless application

`sandbox_cleanup_application` currently creates one `CleanUpRefactoring` per file. Multi-file support requires it to collect and group all selected compilation units by Java project, add the entire group to one refactoring, and apply/check/diff the resulting composite change as one transaction.

## OSGi delivery

`org.eclipse.jdt.ui` is a singleton bundle. A normal fragment cannot override an existing host class because the host bundle class path is searched first. The Sandbox product should therefore install a patched higher-version `org.eclipse.jdt.ui` bundle through a p2 feature patch or its custom target/product.

Keep the experimental scope-provider SPI in a Sandbox package. The public `org.eclipse.jdt.ui.cleanup` package is supplied by the singleton `org.eclipse.jdt.core.manipulation` bundle; adding a new interface there would unnecessarily require patching that bundle too.

## Relationship to JDT UI PR 68

PR 68 demonstrates that the cleanup orchestration can be changed, but its API and execution path are broader than necessary. It should be reduced to scope expansion and reuse the existing fixpoint pipeline. In particular, the implementation should not:

- hold every AST context for the whole project longer than required;
- run multi-file changes outside the normal working-copy lifecycle;
- swallow `CoreException` and continue a supposedly atomic migration;
- introduce independent-selection/recomputation APIs before the preview UI supports them.

## Reuse by JUnit migration

The shared planned-cleanup infrastructure is also required by `sandbox_junit_cleanup`, especially for migrations involving test superclass/subclass relationships, suites and referenced test classes, named rules or `ExternalResource` implementations declared in other files, lifecycle contracts, method sources, and helper APIs used by several tests.

Both plugins should produce an immutable semantic plan first and emit one validated local fix per affected compilation unit.

## Dependencies

- Eclipse JDT Core DOM, Java model, search, and bindings
- Eclipse JDT Core Manipulation rewrite infrastructure
- Eclipse JDT UI cleanup and refactoring infrastructure
- Eclipse LTK refactoring framework
- `sandbox_common` shared infrastructure
