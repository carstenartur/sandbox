# Source-root policy for coordinated cleanups

## Purpose

A Java project can contain production sources, tests, test fixtures, generated sources, linked source folders, and compiler output. Coordinated cleanups must not treat every source package-fragment root as equally editable.

`JavaProjectCompilationUnits` therefore classifies each package-fragment root before scope expansion and applies a cleanup-specific `SourceRootPolicy`.

## Root classification

| Kind | Detection | Default behavior |
|---|---|---|
| `PRODUCTION` | Existing source root that is not classified otherwise | Editable when the cleanup policy includes it |
| `TEST` | JDT test classpath attribute or conventional test/test-fixture path segment | Editable when the cleanup policy includes it |
| `GENERATED` | Conventional generated-source path segment | Never edited by default |
| `DERIVED` | Workspace resource marked derived | Never edited by default |
| `OUTPUT` | Root inside the project or source-entry output location | Never edited by default |
| `EXCLUDED` | Missing, binary, unsupported, or lacking classpath metadata | Never edited |

JDT classpath metadata is authoritative for whether a source root is usable and whether it is a test root. Conventional path names classify test layouts only after a resolved or raw classpath entry has been obtained; they never make a root with missing classpath metadata editable. This covers Maven/Gradle-style `src/test/java`, test fixtures, and integration-test roots without broadening a broken Java model.

Generated-source detection is intentionally conservative. A cleanup must not rewrite generated code merely because the generator output happens to be represented as a source root. Users must change the generator or explicitly copy generated code into an ordinary editable source root.

## Policies

### `EXPLICIT_SELECTED_ROOTS`

Only editable roots already represented in the explicit cleanup selection are included. No cross-root expansion occurs.

### `TEST_ROOTS_AND_SELECTED_SUPPORT`

All editable test and test-fixture roots are included. An editable non-test root is included only when it was explicitly selected.

This is the policy for coordinated JUnit migration. It allows a selected shared test-support source root to participate without treating unrelated production roots as test code.

### `PRODUCTION_WITH_DEPENDENT_TESTS`

When the explicit selection contains a production root, all editable production and test roots may participate so callers and tests can be verified together. When the selection is test-only, production roots are not added.

This is the policy for project-wide Int-to-Enum migration. Tests may validate and migrate callers of a production candidate, but a test-only candidate cannot silently trigger a production API migration.

### `COMPLETE_PROJECT`

All editable production and test roots are included. This remains available to infrastructure callers that explicitly require the complete editable Java project, but coordinated cleanup consumers should prefer a narrower policy.

## Determinism and safety

The resulting compilation-unit list is sorted by Java-element handle. Missing classpath metadata, missing bindings, and unsupported roots fail closed rather than broadening scope.

Generated, derived, output, metadata-less, and non-source roots are excluded before any cleanup-specific policy is evaluated. A policy can therefore select only roots that are editable by default.

## Preview visibility

The policy itself is part of the cleanup contract and is covered by common-layer tests. Structured reporting of the exact roots added to a particular preview belongs to the shared scope/rejection diagnostics tracked in #1214. Until that UI is available, cleanup descriptions and this document define the permitted expansion boundary; no hidden exception may broaden it.

## Verification

`JavaProjectCompilationUnitsTest` uses isolated Java-model proxies to cover:

- production and test roots;
- Maven/Gradle-style test roots and test fixtures;
- generated and derived roots;
- project and source-entry output roots;
- missing classpath metadata and binary roots;
- test-only versus production-origin Int-to-Enum expansion;
- JUnit test-root expansion with an explicitly selected support root;
- explicit-root and complete-project policies;
- deterministic omission of non-editable roots from every policy.

Related issues: #1224, #1212, #1214, #1221.
