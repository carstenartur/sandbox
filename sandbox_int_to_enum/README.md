# Constant-to-Enum Refactoring Plugin

## Overview

This Eclipse plugin detects legacy Java code in which integral or String constants represent a closed set of states and migrates provably safe cases to an enum. Its bundle and existing cleanup profile IDs retain the historical `int_to_enum` name.

The implementation has three deliberately separated capabilities:

- **Local if/else state detection** — binding-based migration when the complete state flow is contained in one compilation unit.
- **Project-wide coordinated migration** — a narrow package-scoped method/caller migration that requires a proven closed source scope.
- **Integer switch migration** — the existing experimental prototype for switch statements.

Local cleanup does not automatically request project sources. Complete-project analysis is an explicit, disabled-by-default option because it can inspect and modify additional files and is materially more expensive.

## Why this needs semantic analysis

A group of similarly named constants is not sufficient evidence for an enum. Their values may be part of protocol, persistence or public API contracts; numeric constants may also be bit masks or arithmetic operands. The cleanup therefore analyses bindings and source references before changing a type.

## Implemented safe local if/else migration

The local and coordinated if/else analyzers support the same domains:

| Declared state and constant type | Supported comparisons | Value contract |
|---|---|---|
| `byte`, `short`, `char`, `int`, `long` | `==`, `!=`, either operand order | Distinct compile-time values; long values retain all 64 bits |
| `String`, `java.lang.String` | `==`, `!=`, `String.equals`, `Objects.equals` | Distinct, non-null compile-time strings; every caller passes a domain constant |

All constants in one domain must have the same resolved type as the state parameter. The original comparisons and control flow remain intact. String identity comparisons are safe here because the closed call graph supplies only compile-time string constants, which are interned. Runtime strings, null arguments, duplicate values, case-insensitive comparisons, boxed primitives, floating-point types, booleans and arbitrary objects are left unchanged.

The integer-switch prototype remains separate; this extension does not add String or long switch migration.

The local implementation transforms a candidate only when all of the following are true:

1. At least two `private static final` constants of a supported type share an underscore-delimited prefix, such as `STATUS_*`.
2. Their compile-time values are distinct.
3. A `private` method has a parameter of the same type compared with those constants in an `if`/`else if` chain.
4. All comparisons refer to the same parameter binding.
5. Every use of that parameter is one of the recognised comparisons.
6. Every call site in the compilation unit passes one of the recognised constants.
7. The constants have no unsupported remaining references.
8. The generated enum name and constants are valid; the enum name does not hide an existing declaration or type reference in its owner.
9. Replacing a reference cannot discard a receiver evaluation, such as `lookup().STATUS_PENDING`.

These restrictions describe the local detector, not a fundamental restriction of the Eclipse cleanup framework. The existing `ICleanUp` lifecycle calls `checkPreConditions(IJavaProject, ICompilationUnit[], ...)` with all target compilation units and then invokes the same cleanup instance once per target unit. A cleanup can therefore prepare an immutable project-wide migration plan and return one local `CompilationUnitChange` for each file. `CleanUpRefactoring` combines those changes into one preview, apply operation, and undo.

## Example

### Before

```java
public class OrderProcessor {
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    public void run() {
        process(STATUS_PENDING);
    }

    private void process(int status) {
        if (status == STATUS_PENDING) {
            handlePending();
        } else if (status == STATUS_APPROVED) {
            handleApproved();
        } else if (status == STATUS_REJECTED) {
            handleRejected();
        }
    }
}
```

### After

```java
public class OrderProcessor {
    private enum Status {
        PENDING, APPROVED, REJECTED
    }

    public void run() {
        process(Status.PENDING);
    }

    private void process(Status status) {
        if (status == Status.PENDING) {
            handlePending();
        } else if (status == Status.APPROVED) {
            handleApproved();
        } else if (status == Status.REJECTED) {
            handleRejected();
        }
    }
}
```

The cleanup deliberately preserves the existing control flow. Replacing an if/else chain with a switch is a separate transformation and can change the meaning of unlabeled `break` statements or create unreachable statements in branches that complete abruptly.

## Cases currently rejected

The implemented detectors do not yet migrate:

- public or protected constants and method signatures;
- parameters passed arbitrary expressions, null, or runtime strings rather than recognised constants;
- constants used in arithmetic, persistence, return values, unrelated method arguments, or unrelated comparisons;
- duplicate values used as aliases, including folded string expressions;
- bit flags, which generally require a different model such as `EnumSet`;
- inheritance, interfaces, overrides, method references, or state flows spanning several methods;
- partial-project multi-file analysis;
- existing nested types that conflict with the generated enum name.

A rejected candidate is left unchanged.

## Project-wide coordinated migration

The coordinated planner recognises a deliberately narrow package-scoped state method and proven callers in other files. Before producing edits, it requires a closed scope containing the declarations and every relevant source use. This prevents unselected callers or constant references from being missed.

Enable **Analyze all project source files for coordinated migrations**, then provide that scope in one of two ways:

1. **Select the entire Java project explicitly.** Stock cleanup orchestration passes the complete selected source scope to the shared planner.
2. **Use automatic scope expansion.** In a product containing the patched JDT UI scope-provider integration, binding-based project searches discover the required callers and constant uses before precondition checking. A verified closure can include only the required related units and leave unrelated sources out of the target set.

The project-wide option is disabled by default. Enabling only **Convert state constants to enum** retains the user's initial selection and runs the local detector without scanning unrelated project files.

The immutable plan retains each constant's declared type and value. Before generating a file's edits, it rechecks values, parameter uses, package visibility and enum-name availability. An incompatible intervening edit invalidates the coordinated plan, including its pending caller changes.

The existing cleanup refactoring still owns parsing, fixpoint processing, overlap handling, preview, validation, apply, and undo. The Sandbox cleanup contributes scope discovery and an immutable semantic plan; it does not bypass the standard LTK transaction.

A dedicated LTK refactoring remains useful for interactive naming and compatibility choices, but it is not required merely to coordinate already closed and fully selected Java source scope.

## Save actions

Local transformation may remain available as a save action. Project-wide scope expansion is initialized to `false` for save actions and also requires the main cleanup option, so saving one compilation unit cannot silently edit other files.

## Usage

1. Open **Preferences → Java → Code Style → Clean Up**.
2. Create or edit a cleanup profile.
3. On the **Int to Enum (Sandbox)** tab, enable **Convert state constants to enum** for local transformations in the selected cleanup scope.
4. For coordinated migration, additionally enable **Analyze all project source files for coordinated migrations**; on an unpatched Eclipse host, select the complete Java project explicitly.
5. Review the cleanup preview before applying changes.

## Requirements

- Build baseline: Eclipse 2026-09 / Platform 4.41
- Java 21 or later
- The patched JDT UI scope-provider integration for automatic target expansion; otherwise select the complete Java project manually

## Technical documentation

- [Architecture](ARCHITECTURE.md)
- [Implementation roadmap and limitations](TODO.md)

## License

Eclipse Public License 2.0
