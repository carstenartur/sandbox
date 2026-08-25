# Method Reuse architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](README.md) | [TODO](TODO.md)

## Purpose

Method Reuse has two explicit transformation paths:

- **Repeated-sequence extraction** creates a private shared method from repeated contiguous statements.
- **Existing-method reuse** replaces an inline sequence with a call to a compatible method that already exists.

The general option is not a whole-method delegation detector and does not maintain a second Extract Method implementation.

## Repeated-sequence pipeline

```text
Cleanup profile and minimum length
              ↓
cheap contiguous-statement candidate discovery
              ↓
coarse shape grouping and deterministic ranking
              ↓
JDT SnippetFinder duplicate pre-check
              ↓
JDT ExtractMethodRefactoring
  - initial semantic conditions
  - parameters / output / return value
  - control flow and exceptions
  - destination and naming checks
  - valid duplicate occurrence set
              ↓
one CompilationUnitChange returned as ICleanUpFix
```

### `MethodReuseCleanUpCore`

The ordinary Cleanup core reads the two boolean modes and the string-valued minimum-length option. For repeated-sequence mode it asks `RepeatedCodeSequenceExtractor` for one prepared fix. When no extractable candidate exists and existing-method mode is also enabled, it falls back to the existing local sequence-to-method-call implementation.

This mode intentionally uses an ordinary single-compilation-unit Cleanup. JDT's duplicate replacement is scoped to the enclosing type in that compilation unit, so project-wide scope expansion and a custom multi-file transaction are neither necessary nor truthful for the current feature.

### `RepeatedCodeSequenceExtractor`

This class owns only candidate enumeration and deterministic choice. It enumerates contiguous direct statement windows in method blocks, groups candidates by enclosing type, statement count, and statement node kinds, and validates promising candidates with JDT.

The coarse key is not a semantic equivalence proof. It exists only to avoid invoking the relatively expensive refactoring for clearly unrelated windows.

Candidate value is ranked by:

```text
statement count × JDT-valid duplicate count
```

Ties prefer the longer sequence, then more duplicates, then the earlier source offset.

### JDT authority

`ExtractMethodRefactoring` is the sole authority for the generated method and replacements. The cleanup calls:

- `checkInitialConditions`;
- `setMethodName`;
- `setVisibility(PRIVATE)`;
- `setReplaceDuplicates(true)`;
- `checkFinalConditions`;
- `createChange`.

This preserves JDT's handling of bindings, arguments, return values, static context, checked exceptions, branch behavior, duplicate local-variable mappings, inherited conflicts, imports, formatting, and text edits.

The plugin must not copy `ExtractMethodAnalyzer`, `SnippetFinder`, parameter inference, or method-generation logic into Sandbox.

## One extraction per pass

Independent Extract Method changes are computed against a particular source snapshot. Composing several independently prepared changes can make later offsets and semantic assumptions stale. The cleanup therefore selects one best group per compilation unit and pass and lets JDT replace all duplicates of that group. Another Cleanup run may process a remaining independent group.

## Existing-method path

`MethodReuseCleanUpFixCore.INLINE_SEQUENCES` and its helper classes remain the separate local path for matching an inline sequence against a method that already exists. It does not participate in repeated-sequence extraction and does not create a method.

## Configuration

- `MYCleanUpConstants.METHOD_REUSE_CLEANUP`: enable repeated-sequence extraction.
- `MethodReuseCleanUpOptions.MINIMUM_STATEMENTS`: minimum contiguous statement count; UI values 3, 4, and 5; default 3.
- `MYCleanUpConstants.METHOD_REUSE_INLINE_SEQUENCES`: enable reuse of an existing method.

Both transformation modes are disabled in save-action defaults. Structural extraction requires an explicit Cleanup run and preview.

## Safety and performance boundaries

- binding-resolved JDT validation is mandatory;
- same enclosing type and compilation unit for duplicate replacement;
- anonymous classes are not selected by the current discovery layer;
- candidate sequence length is bounded;
- JDT validation attempts are bounded per compilation unit;
- JDT errors reject the candidate without fallback rewriting;
- a non-`CompilationUnitChange` result is treated as an error;
- no AST nodes or prepared plans are retained across Cleanup runs.

## Tests

The module tests prove:

- a repeated three-statement sequence is extracted and all occurrences are replaced;
- variable names are mapped through JDT's duplicate machinery;
- a configured threshold of four rejects a three-statement group;
- unrelated sequences remain unchanged;
- existing-method reuse remains active independently;
- the UI preview describes extraction rather than obsolete whole-method delegation.
