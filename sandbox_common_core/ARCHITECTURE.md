# Architecture: sandbox_common_core

## Design Decision

This module was extracted from `sandbox_common` (issue #730) to separate
Eclipse-independent code from OSGi/UI-dependent code.

### What belongs here (sandbox_common_core)

- Classes that only use `org.eclipse.jdt.core.dom.*` or pure Java
- HelperVisitor API and all builder classes
- TriggerPattern engine, parsers, matchers
- Guard expression model (but NOT the GuardRegistry singleton)
- `BuiltInGuards` — all built-in guard function implementations (extracted from `GuardRegistry`)
- `HintFileStore` — Eclipse-independent hint file storage and loading (extracted from `HintFileRegistry`)
- Data classes: Match, Pattern, HintFile, TransformationRule, etc.
- Annotations: @TriggerPattern, @Hint, @TriggerTreeKind, etc.

### What stays in sandbox_common

- Editor support (SWT/JFace): `triggerpattern.editor.*`
- Quick assist processors (Eclipse UI): `triggerpattern.ui.*`
- Cleanup framework bridge: `triggerpattern.cleanup.*`
- `GuardRegistry` — singleton that delegates to `BuiltInGuards` and adds extension-point loading
- `HintFileRegistry` — singleton that delegates to `HintFileStore` and adds workspace/extension-point loading
- `HintRegistry` — annotation-based hint registration with extension-point support
- Eclipse platform integration: HintMarkerManager, HintContext
- Mining analysis/git (may move to core in future)
- CleanUp constants: MYCleanUpConstants
- Utility classes using ASTNodes/ScopeAnalyzer from JDT UI

### Guard Function Resolution

`GuardExpression.FunctionCall` uses `GuardFunctionResolverHolder` to
decouple from `GuardRegistry`. The registry sets itself as the resolver
in its constructor. This allows core code to evaluate guard expressions
without importing the registry directly.

### BuiltInGuards (Grenzfall 2)

All built-in guard functions (instanceof, matchesAny, isStatic, etc.) are
implemented in `BuiltInGuards` as static methods. `GuardRegistry` in
`sandbox_common` delegates to `BuiltInGuards.registerAll(guards)` during
initialization, then adds extension-point loading on top. This allows
standalone tests and CLI tools to use built-in guards without OSGi.

### HintFileStore (Grenzfall 3)

Eclipse-independent hint file storage is in `HintFileStore`. It provides:
- In-memory ConcurrentHashMap storage with secondary index by declared ID
- `loadFromString()`, `loadFromReader()`, `loadFromClasspath()`
- Include resolution with circular-reference detection
- Bundled library loading
- Inferred rule management

`HintFileRegistry` in `sandbox_common` wraps a `HintFileStore` instance and
adds workspace scanning (`loadProjectHintFiles(IProject)`) and extension-point
loading (`loadFromExtensions()`) — both of which require Eclipse/OSGi APIs.

### FixUtilities API Change

`FixUtilities.rewriteFix` takes `(Match, ASTRewrite, String)` instead of
`(HintContext, String)` to avoid depending on `HintContext` (which requires
`ICompilationUnit` from Eclipse platform). Callers that have a `HintContext`
simply pass `ctx.getMatch()` and `ctx.getASTRewrite()`.

### OSGi Compatibility

The module produces an OSGi-compatible JAR via `bnd-maven-plugin`, allowing
it to be used as a `Require-Bundle` dependency by `sandbox_common`.
