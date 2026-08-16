# Platform Helper Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Platform Helper** plugin simplifies Eclipse Platform `Status` and
`MultiStatus` constructor calls without changing their observable status data.
It removes a redundant, compile-time `IStatus.OK` argument while retaining the
original severity, plug-in identity, message and throwable.

## Key features

- **Preserves plug-in identity** — String and Class identity expressions stay in
  the rewritten constructor.
- **Preserves status codes** — nonzero, unresolved and runtime-computed codes are
  left unchanged.
- **Uses resolved constants** — `IStatus.OK`, literal `0` and compile-time zero
  constants are recognized by value rather than source spelling.
- **Conservative `MultiStatus` support** — only a proven zero code may be named
  as `IStatus.OK`.
- **Eclipse integration** — changes use the standard Cleanup preview, Apply and
  global Undo infrastructure.

## Quick start

1. Open **Java > Code Style > Clean Up** in Eclipse Preferences.
2. Edit a dedicated cleanup profile.
3. Enable **Platform Status (Sandbox)**.
4. Run **Source > Clean Up...** on a small version-controlled selection.
5. Select each file in the real LTK preview and inspect its combined diff.

## Transformations

### `Status`

```java
// Before
IStatus status = new Status(
        IStatus.ERROR,
        MyPlugin.PLUGIN_ID,
        IStatus.OK,
        "Failed to load preferences",
        exception);

// After
IStatus status = new Status(
        IStatus.ERROR,
        MyPlugin.PLUGIN_ID,
        "Failed to load preferences",
        exception);
```

The explicit plug-in identifier is retained. The cleanup does not replace the
constructor with `Status.error(...)` unless a future implementation can prove
that the factory-derived caller bundle is exactly equivalent.

### Numeric zero

```java
// Before
IStatus status = new Status(IStatus.WARNING, pluginId, 0, message, null);

// After
IStatus status = new Status(IStatus.WARNING, pluginId, message, null);
```

### `MultiStatus`

```java
// Before
MultiStatus status = new MultiStatus(pluginId, 0, message, exception);

// After
MultiStatus status = new MultiStatus(pluginId, IStatus.OK, message, exception);
```

A nonzero application-specific code is not changed:

```java
new MultiStatus(pluginId, APPLICATION_ERROR_CODE, message, exception)
```

## Applicability boundary

A `Status` constructor is changed only when:

- its type binding resolves exactly to `org.eclipse.core.runtime.Status`;
- it has the supported five-argument shape;
- severity resolves to INFO, WARNING or ERROR;
- code resolves at compile time to `IStatus.OK`;
- the shorter constructor can retain every remaining argument.

No change is made for CANCEL, nonzero codes, runtime-computed codes, unresolved
bindings, already simplified constructors or unsupported types.

## Why factories are not used automatically

`Status.info`, `Status.warning` and `Status.error` infer the plug-in id from the
calling class and its OSGi bundle. An explicit constructor may intentionally use
a different id. Dropping that value changes `IStatus#getPlugin()` and can affect
logging, filtering and diagnostics. Readability does not justify that semantic
change.

## Testing

The Tycho test module `sandbox_platform_helper_test` covers:

- all supported severities;
- String and Class identity overloads;
- null and non-null throwable values;
- named, literal and constant zero;
- nonzero and nonconstant rejection cases;
- idempotence of already simplified constructors;
- safe and rejected `MultiStatus` forms.

Run the repository's normal Maven/Tycho verification. The Help screenshot gate
uses a real Eclipse workbench and real target-platform bindings; manually edited
screenshots are not accepted as evidence.

## Real-corpus QA

Issue [#1497](https://github.com/carstenartur/sandbox/issues/1497) tracks reuse
of the pinned JDT Core/JDT UI Oomph workspaces for canonical screenshots and
provenance. Issue [#1498](https://github.com/carstenartur/sandbox/issues/1498)
tracks the semantic-preservation correction described here.

## Documentation

- [Architecture](ARCHITECTURE.md)
- [Installed Eclipse Help](../sandbox_platform_helper_help/html/usage.html)
- [Main README](../README.md#platform-status-helper-sandbox_platform_helper)

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related plugins**: [Encoding Quickfix](../sandbox_encoding_quickfix/) · [JFace Cleanup](../sandbox_jface_cleanup/)
