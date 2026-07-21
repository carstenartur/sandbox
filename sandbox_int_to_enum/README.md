# Int-to-Enum Refactoring Plugin

## Overview

This Eclipse plugin detects legacy Java code in which integer constants represent a closed set of states and migrates provably safe cases to an enum.

The implementation has three deliberately separated capabilities:

- **Local if/else state detection** — binding-based migration when the complete state flow is contained in one compilation unit.
- **Complete-project coordinated migration** — a narrow package-scoped method/caller migration that runs only when every project source compilation unit is in scope.
- **Integer switch migration** — the existing experimental prototype for switch statements.

Local cleanup does not automatically request project sources. Complete-project analysis is an explicit, disabled-by-default option because it can inspect and modify additional files and is materially more expensive.

## Why this needs semantic analysis

A group of similarly named integer constants is not sufficient evidence for an enum. Integers may be used as bit masks, protocol values, persisted identifiers, arithmetic operands, public API values, or values crossing file boundaries. The cleanup therefore analyses bindings and all relevant references before changing a type.

## Implemented safe local if/else migration

The local implementation transforms a candidate only when all of the following are true:

1. At least two `private static final int` constants share an underscore-delimited prefix, such as `STATUS_*`.
2. Their compile-time integer values are distinct.
3. A `private` method has an `int` parameter compared with those constants in an `if`/`else if` chain.
4. All comparisons refer to the same parameter binding.
5. Every use of that parameter is one of the recognised comparisons.
6. Every call site in the compilation unit passes one of the recognised constants.
7. The constants have no unsupported remaining references.
8. The generated enum name and constants are valid and do not conflict with an existing nested type.

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
- parameters passed arbitrary integer expressions rather than recognised constants;
- constants used in arithmetic, persistence, return values, unrelated method arguments, or unrelated comparisons;
- duplicate integer values used as aliases;
- bit flags, which generally require a different model such as `EnumSet`;
- inheritance, interfaces, overrides, method references, or state flows spanning several methods;
- partial-project multi-file analysis;
- existing nested types that conflict with the generated enum name.

A rejected candidate is left unchanged.

## Complete-project coordinated migration

The coordinated planner currently recognises a deliberately narrow package-scoped state method and proven callers in other files. Before producing any edits it requires every source compilation unit in the Java project to be present in the cleanup target. This closed-world requirement prevents unselected callers or constant references from being missed.

There are two ways to provide that scope:

1. **Select the entire Java project explicitly.** Stock cleanup orchestration passes all selected source units to the shared planner.
2. **Enable automatic project-wide scope expansion.** In a product containing the patched JDT UI scope-provider integration, enable the child option **Analyze all project source files for coordinated migrations**. The cleanup then requests every project source compilation unit before precondition checking.

The project-wide option is disabled by default. Enabling only **Convert int constants to enum/switch** retains the user's initial selection and runs the local detector without scanning unrelated project files.

The existing cleanup refactoring still owns parsing, fixpoint processing, overlap handling, preview, validation, apply, and undo. The Sandbox cleanup contributes scope discovery and an immutable semantic plan; it does not bypass the standard LTK transaction.

A dedicated LTK refactoring remains useful for interactive naming and compatibility choices, but it is not required merely to coordinate already closed and fully selected Java source scope.

## Save actions

Local transformation may remain available as a save action. Project-wide scope expansion is initialized to `false` for save actions and also requires the main cleanup option, so saving one compilation unit cannot silently edit other files.

## Usage

1. Open **Preferences → Java → Code Style → Clean Up**.
2. Create or edit a cleanup profile.
3. Enable **Convert int constants to enum/switch** for local transformations in the selected cleanup scope.
4. For the coordinated complete-project migration, additionally enable **Analyze all project source files for coordinated migrations**.
5. Review the cleanup preview before applying changes.

## Requirements

- Eclipse 2025-12 or later
- Java 21 or later
- The patched JDT UI scope-provider integration for automatic target expansion; otherwise select the complete Java project manually

## Technical documentation

- [Architecture](ARCHITECTURE.md)
- [Implementation roadmap and limitations](TODO.md)

## License

Eclipse Public License 2.0
