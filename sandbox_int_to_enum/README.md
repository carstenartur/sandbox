# Int-to-Enum Refactoring Plugin

## Overview

This Eclipse plugin detects legacy Java code in which integer constants represent a closed set of states and migrates provably safe cases to an enum.

The implementation has two transformation paths:

- **If/else state detection** — implemented conservatively with binding and usage analysis.
- **Integer switch migration** — existing experimental prototype for switch statements.

## Why this needs semantic analysis

A group of similarly named integer constants is not sufficient evidence for an enum. Integers may be used as bit masks, protocol values, persisted identifiers, arithmetic operands, public API values, or values crossing file boundaries. The cleanup therefore analyses bindings and all relevant references before changing a type.

## Implemented safe if/else migration

The ordinary single-file cleanup currently transforms a candidate only when all of the following are true:

1. At least two `private static final int` constants share an underscore-delimited prefix, such as `STATUS_*`.
2. Their compile-time integer values are distinct.
3. A `private` method has an `int` parameter compared with those constants in an `if`/`else if` chain.
4. All comparisons refer to the same parameter binding.
5. Every use of that parameter is one of the recognised comparisons.
6. Every call site in the compilation unit passes one of the recognised constants.
7. The constants have no unsupported remaining references.
8. The generated enum name and constants are valid and do not conflict with an existing nested type.

The visibility restrictions are intentional. A normal `ICleanUp` receives one compilation unit at a time and cannot prove that public or package-visible constants and method signatures have no references in other files.

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

## Cases intentionally rejected

The single-file cleanup does not currently migrate:

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

This cannot be implemented safely as an ordinary third-party cleanup because `ICleanUpFix#createChange` returns a single `CompilationUnitChange`. Two viable designs are:

1. add explicit multi-file cleanup support to JDT UI; or
2. implement a dedicated LTK refactoring in this plugin that produces a `CompositeChange`.

The semantic candidate detector should remain independent of the UI path so it can be reused by both the conservative cleanup and the future project-wide refactoring.

## Usage

1. Open **Preferences → Java → Code Style → Clean Up**.
2. Create or edit a cleanup profile.
3. Enable **Convert int constants to enum/switch**.
4. Run the cleanup on selected Java sources.

The transformation can also be enabled as a save action, although project-wide migrations should not run as save actions.

## Requirements

- Eclipse 2025-12 or later
- Java 21 or later

## Technical documentation

- [Architecture](ARCHITECTURE.md)
- [Implementation roadmap and limitations](TODO.md)

## License

Eclipse Public License 2.0
