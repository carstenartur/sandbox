# Platform Helper Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Platform Helper** plugin modernizes Eclipse Platform `Status` construction
without changing observable status data. It removes a redundant compile-time
`IStatus.OK` code and chooses the shortest target whose equivalence can be
proved:

- a `Status.info`, `Status.warning`, or `Status.error` factory when the explicit
  identity is provably the calling bundle identity and the factory return type
  fits the source context;
- otherwise the four-argument `Status` constructor, retaining the original
  String or Class identity;
- for `MultiStatus`, only a compile-time numeric zero may be named as
  `IStatus.OK`.

Fewer source arguments are not information loss when severity, the OK code, and
bundle identity are supplied by the factory contract and equality has been
proved. The cleanup never asks the user to repair an unproven semantic change in
the preview.

## Quick start

1. Open **Java > Code Style > Clean Up** in Eclipse Preferences.
2. Edit a dedicated cleanup profile.
3. Enable **Platform Status (Sandbox)**.
4. Run **Source > Clean Up...** on a version-controlled selection.
5. Inspect each changed file in the standard LTK preview, then Apply or Undo as
   usual.

## Factory transformation

For a manifest-backed bundle whose runtime symbolic name is
`com.example.plugin`:

```java
private static final String PLUGIN_ID = "com.example.plugin";

// Before
IStatus status = new Status(
        IStatus.ERROR,
        PLUGIN_ID,
        IStatus.OK,
        "Failed to load preferences",
        exception);

// After
IStatus status = Status.error("Failed to load preferences", exception);
```

The omitted values are still present in the resulting status:

- `Status.error` supplies severity `ERROR`;
- the factory contract supplies code `IStatus.OK`;
- `StackWalker` and the calling bundle supply `com.example.plugin`;
- message and throwable are moved unchanged.

For fragment projects, the runtime identity is taken from `Fragment-Host`, not
from the fragment's own symbolic name. A matching host-id constant may use the
factory; a fragment-id constant stays in the constructor fallback.

The same proof is available for the exact enclosing class literal in a bundle
project:

```java
new Status(IStatus.WARNING, MyView.class, IStatus.OK, message, exception)
```

inside `MyView` may become:

```java
Status.warning(message, exception)
```

## Identity-preserving fallback

When equality cannot be proved, only the redundant OK code is removed:

```java
// Before
new Status(IStatus.ERROR, responsiblePluginId, IStatus.OK, message, exception)

// After
new Status(IStatus.ERROR, responsiblePluginId, message, exception)
```

This fallback is used for delegated or mismatching identifiers, unresolved
expressions, arbitrary method calls, a Class identity different from the
calling class, missing bundle metadata, unavailable factory overloads, and
factory return types that do not fit the surrounding source target.

A call such as `JavaPlugin.getPluginId()` is deliberately retained. Even when
its returned value is expected to equal the bundle id, removing a method call or
its declaring-class initialization can be observable. Value equality alone is
not evaluation equivalence.

## Factory overload and type safety

The cleanup resolves the actual `Status` methods from the project's build path.
It uses a factory only when:

- the required overload exists;
- the message and throwable can be represented;
- the factory return type is assignment-compatible with the target context.

For example, an `INFO` status with a non-null throwable keeps the constructor
because the Platform API has no equivalent two-argument `Status.info` overload.
A factory returning `IStatus` is not substituted into a context that requires
concrete `Status`.

## Compile-time value recognition

Severity and code are compared by resolved value, not source spelling. These
are equivalent OK-code candidates:

```java
IStatus.OK
0
private static final int OK_CODE = 0;
```

Nonzero, unresolved, and runtime-computed codes remain unchanged.

## `MultiStatus`

```java
// Before
new MultiStatus(pluginId, 0, message, exception)

// After
new MultiStatus(pluginId, IStatus.OK, message, exception)
```

A nonzero application-specific code is never replaced:

```java
new MultiStatus(pluginId, APPLICATION_ERROR_CODE, message, exception)
```

## Native cleanup and TriggerPattern DSL

The native JDT cleanup can use project metadata, bindings, target types, and
compile-time values, so it performs the proven factory optimization described
above. The bundled TriggerPattern rule has no equivalent project-level proof;
it therefore remains conservative and rewrites only five-argument constructors
to identity-preserving four-argument constructors.

## Testing

`sandbox_platform_helper_test` covers supported severities, String and Class
identities, matching and delegated bundle ids, factory overload selection,
return-type compatibility, null and non-null throwables, named/literal/constant
zero, rejected nonzero codes, import conflicts, `MultiStatus`, and idempotence.

Run the repository's normal Maven/Tycho verification. Canonical Help screenshots
must come from the real SWTBot workbench scenario rather than manual image edits.

## Documentation

- [Architecture](ARCHITECTURE.md)
- [Installed Eclipse Help](../sandbox_platform_helper_help/html/index.html)
- [Main README](../README.md#platform-status-helper-sandbox_platform_helper)

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related plugins**: [Encoding Quickfix](../sandbox_encoding_quickfix/) · [JFace Cleanup](../sandbox_jface_cleanup/)
