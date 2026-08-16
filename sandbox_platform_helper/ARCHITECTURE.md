# Platform Helper Plugin — Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](README.md) | [TODO](TODO.md)

## Design goal

The Platform Helper cleanup modernizes Eclipse Platform `Status` construction
while preserving every observable `IStatus` value and every observable source
expression. It therefore chooses the shortest target whose equivalence can be
proved; a lower parameter count is not treated as information loss by itself.

The relevant observable data are severity, plug-in identity, application code,
message, and throwable. Expression evaluation is observable too: removing a
method invocation can change side effects or class initialization even when its
returned String is expected to equal the current bundle id.

## Decision tree

For a binding-resolved five-argument `Status` constructor with supported
severity and a compile-time code value of `IStatus.OK`, the native cleanup makes
this decision:

1. Prove that the explicit identity is side-effect-free and equals the identity
   the factory derives for the calling class.
2. Resolve the actual `Status.info`, `Status.warning`, or `Status.error` overload
   from the project's build path.
3. Prove that message and throwable are representable and that the factory
   return type is assignment-compatible with the source target.
4. If all proofs succeed, generate the factory call.
5. Otherwise, generate the four-argument constructor and retain the explicit
   identity.

Nonzero, unresolved, and runtime-computed codes are not changed.

## Factory path

### Bundle identity proof

The cleanup reads `META-INF/MANIFEST.MF` from the containing Java project. For
a regular bundle it normalizes `Bundle-SymbolicName`; for a fragment it uses the
`Fragment-Host` symbolic name because the host is the bundle that defines the
fragment class at runtime. Directives and version attributes are removed before
comparison.

A factory is considered only for these side-effect-free identities:

- a compile-time String constant whose value equals `Bundle-SymbolicName`;
- the exact enclosing class literal, for example `MyView.class` inside
  `MyView`.

Compile-time String constants are safe to omit because Java inlines their values
and referencing them does not trigger class initialization. The exact current
class literal is safe because the constructor and the factory identify the same
calling class and therefore the same manifest-backed bundle.

These expressions do not establish the required proof:

- a different literal or constant;
- a Class literal different from the enclosing class;
- a local variable or parameter;
- an unresolved expression;
- a method call such as `JavaPlugin.getPluginId()`.

A getter is deliberately retained even when its expected return value equals the
bundle id. Evaluating the method and initializing its declaring class can be
observable; value equality alone is not evaluation equivalence.

### Factory overload proof

The cleanup does not assume a fixed Eclipse Platform API shape. It resolves the
actual static factory methods from the `Status` type binding on the project's
build path.

For a literal null throwable, the one-argument factory is preferred when its
return type fits the target. If it does not, the two-argument overload is tried
with the explicit null. For a non-null throwable, the two-argument overload is
required.

This matters because the factory return types are not uniform. For example, a
factory returning `IStatus` must not replace a constructor in a source context
that requires concrete `Status`. Likewise, an INFO status with a non-null
throwable keeps the constructor when no equivalent `Status.info(String,
Throwable)` overload exists.

### Factory rewrite

A proven candidate such as:

```java
new Status(IStatus.ERROR, PLUGIN_ID, IStatus.OK, message, exception)
```

may become:

```java
Status.error(message, exception)
```

The factory supplies the severity and OK code. `StackWalker` and the calling
bundle supply the already-proven equivalent plug-in identity. Message and
throwable are moved without re-evaluation.

## Identity-preserving constructor path

When factory equivalence is not fully proven, the cleanup removes only the
redundant OK code:

```java
new Status(severity, explicitIdentity, IStatus.OK, message, throwable)
```

becomes:

```java
new Status(severity, explicitIdentity, message, throwable)
```

The explicit String or Class expression, severity, message, and throwable remain
in the same evaluation order. This is the fallback for delegated identities,
method calls, missing bundle metadata, unavailable overloads, and incompatible
factory return types.

## Compile-time value analysis

`Expression.resolveConstantExpressionValue()` is used rather than source-text
matching. These code expressions are equivalent candidates:

```java
IStatus.OK
0
private static final int OK_CODE = 0;
```

These remain unchanged unless a named constant itself resolves to zero:

```java
17
APPLICATION_SPECIFIC_CODE
computeCode()
```

The same resolved-value principle is used for supported severities.

## `MultiStatus`

A `MultiStatus` application code remains observable through `IStatus#getCode()`.
The cleanup therefore never replaces a nonzero or unresolved code with
`IStatus.OK`.

The only supported rewrite is a naming normalization:

```java
new MultiStatus(pluginId, 0, message, exception)
```

becomes:

```java
new MultiStatus(pluginId, IStatus.OK, message, exception)
```

An existing canonical `IStatus.OK` reference is left unchanged. Import conflicts
are handled through the standard JDT import rewrite.

## Native cleanup and TriggerPattern DSL

The native JDT cleanup is authoritative for factory selection because it has
access to the compilation unit, project manifest, resolved bindings, target
type, and compile-time values.

The bundled TriggerPattern DSL rules do not have an equivalent project-level
identity and target-type proof. They therefore remain conservative: supported
five-argument constructors are rewritten only to identity-preserving
four-argument constructors. The general Platform Logging library contains no
Status constructor-to-factory rules.

## Eclipse cleanup integration

`SimplifyPlatformStatusCleanUpCore`:

- requests a resolved AST;
- applies the current Java-compliance gate;
- shares one processed-node set between INFO, WARNING, ERROR, and MultiStatus;
- creates comment-preserving `CompilationUnitRewrite` operations;
- participates in the standard LTK preview, Apply, and global Undo workflow.

The real execution preview is a review aid, not a semantic safety boundary. The
candidate detector must establish equivalence before a proposal is shown or a
save action is allowed to apply it.

## Test strategy

Focused tests cover:

- INFO, WARNING, and ERROR;
- compile-time String identity equal to the bundle symbolic name;
- exact enclosing class identity;
- delegated and mismatching identities;
- method-call identities retained for evaluation safety;
- one- and two-argument overload selection;
- concrete `Status` versus `IStatus` target compatibility;
- INFO with a non-null throwable;
- literal, named, and constant zero;
- nonzero and runtime-computed codes;
- `MultiStatus` normalization and rejection cases;
- import conflicts and idempotence.

Pinned real-corpus tests and SWTBot screenshots must use the same applicability
logic. In the current JDT UI corpus, `JavaPlugin.getPluginId()` remains in the
constructor fallback because it is a method invocation, while literal or
compile-time constants can use a factory when the manifest proves equality.

## Known limitations

- Only a PDE-style project manifest at `META-INF/MANIFEST.MF` is currently used
  as bundle metadata.
- Getter methods are not analyzed for purity or initialization equivalence.
- A Class identity from another source type in the same bundle is not yet used
  as a proof; only the exact enclosing class literal is accepted.
- Rejection reasons are not yet exposed as a machine-readable inventory.
- Custom `IStatus` implementations, subclasses, and indirect factory wrappers
  are outside the matching scope.

## References

- [Status API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/Status.html)
- [MultiStatus API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/MultiStatus.html)
- [Semantic preservation issue](https://github.com/carstenartur/sandbox/issues/1498)
- [Pinned workspace and screenshot scenarios](https://github.com/carstenartur/sandbox/issues/1497)
