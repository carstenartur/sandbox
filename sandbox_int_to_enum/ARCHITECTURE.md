# Architecture: Int-to-Enum Refactoring

## Purpose

The plugin identifies legacy Java designs in which integers encode a finite state domain and replaces provably safe cases with an enum.

This is a semantic refactoring rather than a textual pattern replacement. Integer constants may represent states, bit masks, protocol values, persisted identifiers, error codes, array indexes, or arithmetic values. A valid migration must therefore prove how declarations and values flow through the program before changing their type.

## Transformation layers

### 1. Conservative single-file cleanup

The existing Eclipse cleanup extension receives one `CleanUpContext` for one compilation unit and returns an `ICleanUpFix`. The if/else implementation therefore accepts only candidates whose complete relevant data flow is local and private.

Current safe candidate:

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

### 2. Existing switch prototype

`SwitchIntToEnumHelper` detects integer switch cases and creates a nested enum. It predates the conservative if/else detector and still requires hardening so that it applies the same whole-reference and API-visibility safety rules.

### 3. Future project-wide refactoring

Public and package-visible constants, interface methods, overridden methods, fields, return values, and callers in other files require coordinated multi-file changes.

There are two viable integration paths:

- a new multi-file cleanup lifecycle in JDT UI; or
- a dedicated LTK `Refactoring` contributed by this plugin.

A dedicated refactoring can be implemented outside JDT UI and return an LTK `CompositeChange`. Appearing as a standard item in the Java Clean Up profile requires JDT UI infrastructure because the current `ICleanUpFix` contract is compilation-unit based.

## Package structure

### `org.sandbox.jdt.internal.corext.fix`

- `IntToEnumFixCore` selects the transformation helper and creates rewrite operations.

### `org.sandbox.jdt.internal.corext.fix.helper`

- `AbstractTool<T>` defines detection, rewrite, and preview hooks.
- `IntToEnumHelper` implements conservative binding-based if/else detection and migration.
- `SwitchIntToEnumHelper` implements the existing switch prototype.

### `org.sandbox.jdt.internal.ui.fix`

- `IntToEnumCleanUp` is the UI wrapper.
- `IntToEnumCleanUpCore` integrates with the single-file cleanup lifecycle.

### `org.sandbox.jdt.internal.ui.preferences.cleanup`

Contains the cleanup profile and save-action configuration UI.

## If/else candidate analysis

### Constant group discovery

The current single-file detector collects fields that are:

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

Examples accepted by the condition matcher:

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

The edit remains one compilation-unit change and participates in normal cleanup preview and undo.

## Why public API migration is different

A source file cannot prove that a public constant or method is unused elsewhere. Binary clients may also depend on compile-time inlined constants or an integer method signature. A project-wide migration must consider:

- JDT search results across source roots;
- method hierarchies, interfaces, overrides, and implementations;
- callers, assignments, fields, locals, return values, and method references;
- persisted and wire-format integer values;
- reflection, JNI, generated code, and external binaries;
- compatibility adapters or deprecated bridge constants.

This analysis must finish before any edit is applied, and all edits should be presented and committed as one atomic `CompositeChange`.

## Reusable future model

The next architectural step is to extract candidate discovery from `IntToEnumHelper` into a reusable semantic model containing:

- constant group and numeric-value semantics;
- state carriers;
- comparison and switch sites;
- producer and consumer edges;
- required edits per compilation unit;
- explicit rejection reasons and confidence level.

That model can support:

- the conservative cleanup;
- a report-only inspection or marker;
- a quick assist for one candidate;
- a project-wide LTK refactoring;
- future JDT UI multi-file cleanup integration.

## Portability to Eclipse JDT

The helper and candidate-analysis code can be moved from `org.sandbox.jdt.internal.*` to the corresponding JDT internal packages. Standard cleanup-profile integration for project-wide candidates additionally needs a JDT UI API and lifecycle change. The dedicated-refactoring approach does not.

## Dependencies

- Eclipse JDT Core DOM and bindings
- Eclipse JDT Core Manipulation rewrite infrastructure
- Eclipse JDT UI cleanup APIs
- Eclipse LTK refactoring framework
- `sandbox_common` helper infrastructure
