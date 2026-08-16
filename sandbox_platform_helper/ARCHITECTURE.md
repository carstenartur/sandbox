# Platform Helper Plugin — Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](README.md) | [TODO](TODO.md)

## Overview

The Platform Helper plugin provides conservative Eclipse cleanup operations for
`org.eclipse.core.runtime.Status` and `MultiStatus` constructor calls.

The primary invariant is **semantic preservation**. Status severity, explicit
plug-in identity, message, throwable and application-specific status codes are
observable data. A cleanup must not discard or replace them merely to obtain a
shorter factory call.

## Supported transformations

### `Status`: remove only a proven redundant OK code

The five-argument `Status` constructor carries an explicit code:

```java
new Status(IStatus.WARNING, pluginId, IStatus.OK, message, exception)
```

When both severity and code resolve to supported compile-time integer values,
the cleanup removes only the redundant `IStatus.OK` argument and retains every
other value:

```java
new Status(IStatus.WARNING, pluginId, message, exception)
```

This works for both supported identity overloads:

```java
new Status(severity, String pluginId, code, message, throwable)
new Status(severity, Class<?> caller, code, message, throwable)
```

The corresponding four-argument constructor preserves the original String or
Class identity and assumes code `IStatus.OK`.

### `MultiStatus`: name only a compile-time zero code

A `MultiStatus` code is not generally redundant. Nonzero application-specific
codes must remain unchanged.

The cleanup may replace only a code whose compile-time value is provably zero:

```java
new MultiStatus(pluginId, 0, message, exception)
```

with the named equivalent:

```java
new MultiStatus(pluginId, IStatus.OK, message, exception)
```

`IStatus.OK`, a static import of that field, nonzero constants, method calls and
unresolved expressions are not rewritten.

## Why factory methods are not the default target

`Status.info(...)`, `Status.warning(...)` and `Status.error(...)` determine the
plug-in identifier from the calling class through `StackWalker` and the OSGi
bundle containing that class. A constructor may instead contain:

- a literal identifier;
- a constant from another bundle;
- a delegated logging identity;
- a Class object different from the calling class;
- a dynamically computed String.

Replacing such a constructor with a factory method changes `IStatus#getPlugin()`
unless equivalence is proven. The current conservative implementation therefore
keeps the explicit identity and uses the shorter constructor overload.

A future factory rewrite is acceptable only with a dedicated proof that the
original identity equals the bundle identity that the factory will derive. UI
review is not a substitute for that proof.

## Detection contract

`AbstractSimplifyPlatformStatus` performs the shared `Status` analysis.

A candidate is accepted only when:

1. the class-instance creation resolves exactly to
   `org.eclipse.core.runtime.Status`;
2. the constructor has five arguments;
3. severity has a compile-time integer value equal to `IStatus.INFO`,
   `IStatus.WARNING` or `IStatus.ERROR` for the active helper;
4. code has a compile-time integer value equal to `IStatus.OK`;
5. the target rewrite can retain severity, explicit identity, message and
   throwable.

Using `Expression.resolveConstantExpressionValue()` avoids source-spelling
heuristics. The following are semantically equivalent candidates:

```java
IStatus.OK
0
private static final int OK_CODE = 0;
```

The following are rejected:

```java
17
APPLICATION_SPECIFIC_CODE
computeCode()
```

unless a named constant is itself a resolved compile-time zero value.

`MultiStatusSimplifyPlatformStatus` applies the same compile-time-value rule but
never rewrites nonzero or unresolved codes.

## Rewrite contract

The `Status` rewrite creates a new `ClassInstanceCreation` and moves:

1. severity;
2. explicit String or Class identity;
3. message;
4. throwable, including an explicit `null`.

The proven `IStatus.OK` argument is the only omitted node. The rewrite uses the
standard JDT `CompilationUnitRewrite`, `ASTRewrite`, import rewrite and comment-
preserving replacement infrastructure, so it remains integrated with the
Cleanup preview and global refactoring undo manager.

The `MultiStatus` rewrite moves plug-in id, message and throwable unchanged and
replaces only a proven zero expression with `IStatus.OK`.

## Cleanup integration

`SimplifyPlatformStatusCleanUpCore`:

- is registered through `org.eclipse.jdt.ui.cleanUps`;
- requests a resolved AST;
- creates one `CompilationUnitRewriteOperationsFixCore` per compilation unit;
- shares a processed-node set between INFO, WARNING, ERROR and MultiStatus
  helpers;
- currently retains the existing Java-compliance gate used by the feature;
- participates in standard Eclipse file-level LTK preview, Apply and Undo.

The standard preview shows one selectable file and the combined diff from all
enabled applicable cleanup operations for that file. It does not promise a
separate checkbox for every internal text-edit group.

## Test strategy

`sandbox_platform_helper_test` covers:

- INFO, WARNING and ERROR;
- String and Class identity overloads;
- null and non-null throwable values;
- `IStatus.OK`, literal zero and zero constants;
- nonzero literals and application-specific constants;
- nonconstant code expressions;
- unsupported severity;
- already simplified constructors;
- `MultiStatus` zero normalization;
- rejection of nonzero and unresolved `MultiStatus` codes.

Expected results must retain the original identity expression. Tests that expect
an arbitrary plug-in id or nonzero status code to disappear are defects, not
valid modernization examples.

## Real-corpus QA

The pinned upstream scenario work in issue #1497 uses exact repository/ref/commit
identity and must classify candidates before screenshots are generated.

The initial JDT UI audit uses:

```text
repository: https://github.com/eclipse-jdt/eclipse.jdt.ui.git
ref: R4_40
commit: c922f757b27b7e2b6215db383cec5f8aafd13227
```

Representative classifications include:

- `ProposalSorterHandle`: five-argument `Status` calls with `IStatus.OK`; safe
  constructor simplification retains `JavaPlugin.getPluginId()`;
- `JarBuilder`: application-specific `IJavaStatusConstants.INTERNAL_ERROR`;
  rejected and unchanged;
- `JavaSearchResult`: numeric zero; semantically eligible even though it is not
  spelled `IStatus.OK`.

The same corrected applicability rules must drive headless inventory, real LTK
preview, Apply/Undo assertions and screenshot provenance.

## Module structure

- `org.sandbox.jdt.internal.corext.fix` — cleanup operation enumeration and
  rewrite operations;
- `org.sandbox.jdt.internal.corext.fix.helper` — Status/MultiStatus detection and
  rewrite helpers;
- `org.sandbox.jdt.internal.ui.fix` — Eclipse Cleanup integration;
- `sandbox_platform_helper_test` — JUnit/Tycho regression tests;
- `sandbox_platform_helper_help` — installed Eclipse Help.

## Known limitations

- No factory-method conversion is offered without an identity-equivalence proof.
- The feature does not rewrite custom `IStatus` implementations or subclasses.
- It does not infer runtime values from methods, fields that are not compile-time
  constants, configuration files or service calls.
- The Java-compliance gate is retained for compatibility and may be reconsidered
  separately from semantic correctness.
- Rejection reasons are not yet exposed as a reusable machine-readable inventory;
  that belongs to the scenario/provenance work in #1497.

## References

- [Status API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/Status.html)
- [MultiStatus API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/MultiStatus.html)
- [Eclipse runtime status handling](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/runtime_status.htm)
- [Reusable screenshot and pinned-workspace contract](https://github.com/carstenartur/sandbox/issues/1497)
- [Semantic preservation issue](https://github.com/carstenartur/sandbox/issues/1498)

## Documentation requirements

The corresponding feature module must keep its localized feature properties in
sync with the actual supported transformations. Help and screenshots may describe
only changes that the implementation proves safe; they must not shift semantic
verification to the user after an automatic rewrite.
