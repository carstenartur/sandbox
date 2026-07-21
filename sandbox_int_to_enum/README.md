# Int-to-Enum Refactoring Plugin

## Overview

This Eclipse plugin detects legacy Java code in which integer constants represent a closed set of states and migrates provably safe cases to an enum.

The implementation has two transformation paths:

- **If/else state detection** — implemented conservatively with binding and usage analysis.
- **Integer switch migration** — existing experimental prototype for switch statements.

## Why this needs semantic analysis

A group of similarly named integer constants is not sufficient evidence for an enum. Integers may be used as bit masks, protocol values, persisted identifiers, arithmetic operands, public API values, or values crossing file boundaries. The cleanup therefore analyses bindings and all relevant references before changing a type.

## Implemented safe if/else migration

The current implementation transforms a candidate only when all of the following are true:

1. At least two `private static final int` constants share an underscore-delimited prefix, such as `STATUS_*`.
2. Their compile-time integer values are distinct.
3. A `private` method has an `int` parameter compared with those constants in an `if`/`else if` chain.
4. All comparisons refer to the same parameter binding.
5. Every use of that parameter is one of the recognised comparisons.
6. Every call site in the compilation unit passes one of the recognised constants.
7. The constants have no unsupported remaining references.
8. The generated enum name and constants are valid and do not conflict with an existing nested type.

These restrictions describe the first implemented detector, not a fundamental restriction of the Eclipse cleanup framework. The existing `ICleanUp` lifecycle calls `checkPreConditions(IJavaProject, ICompilationUnit[], ...)` with all selected compilation units and then invokes the same cleanup instance once per target unit. A cleanup can therefore prepare an immutable project-wide migration plan and return one local `CompilationUnitChange` for each file. `CleanUpRefactoring` already combines those changes into one preview, apply operation, and undo.

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

The implemented detector does not yet migrate:

- public, protected, or package-visible constants or method signatures;
- parameters passed arbitrary integer expressions rather than recognised constants;
- constants used in arithmetic, persistence, return values, method arguments, or unrelated comparisons;
- duplicate integer values used as aliases;
- bit flags, which generally require a different model such as `EnumSet`;
- state flows spanning multiple methods or compilation units;
- existing nested types that conflict with the generated enum name.

A rejected candidate is left unchanged.

## Project-wide migration

A complete migration of legacy APIs requires project-wide reference analysis and coordinated edits to declarations, callers, implementations, overrides, fields, locals, tests, serialization boundaries, and possibly external compatibility adapters.

There are two stages:

1. **Selected-scope multi-file cleanup without a JDT UI patch.** During `checkPreConditions`, analyse all compilation units already selected for cleanup and build a shared immutable plan. During `createFix`, emit only the planned edits for the current `CleanUpContext`. The existing cleanup refactoring collects all per-file changes atomically.
2. **Automatic scope expansion with a small patched JDT UI bundle.** Before precondition checking, a patched `CleanUpRefactoring` asks participating cleanups for additional related compilation units and adds them to the normal target set. The existing parsing, fixpoint, overlap, preview, validation, apply, and undo pipeline remains in use.

A dedicated LTK refactoring remains useful for interactive naming and compatibility choices, but it is not required merely to coordinate changes across already selected Java files.

The reusable planning infrastructure is tracked in issue #1206 and is intended for both this plugin and `sandbox_junit_cleanup`.

## Save actions

The local transformation may remain available as a save action. Project-wide migration must be restricted to explicit cleanup runs because the save participant supplies only the saved compilation unit and should not silently edit other files.

## Usage

1. Open **Preferences → Java → Code Style → Clean Up**.
2. Create or edit a cleanup profile.
3. Enable **Convert int constants to enum/switch**.
4. Run the cleanup on selected Java sources, a package, source folder, or project.

## Requirements

- Eclipse 2025-12 or later
- Java 21 or later

## Technical documentation

- [Architecture](ARCHITECTURE.md)
- [Implementation roadmap and limitations](TODO.md)

## License

Eclipse Public License 2.0
