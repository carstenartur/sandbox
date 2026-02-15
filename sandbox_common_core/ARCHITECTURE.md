# Architecture: sandbox_common_core

## Design Decision

This module was extracted from `sandbox_common` (issue #730) to separate
Eclipse-independent code from OSGi/UI-dependent code.

### What belongs here (sandbox_common_core)

- Classes that only use `org.eclipse.jdt.core.dom.*` or pure Java
- HelperVisitor API and all builder classes
- TriggerPattern engine, parsers, matchers
- Guard expression model (but NOT the GuardRegistry singleton)
- Data classes: Match, Pattern, HintFile, TransformationRule, etc.
- Annotations: @TriggerPattern, @Hint, @TriggerTreeKind, etc.

### What stays in sandbox_common

- Editor support (SWT/JFace): `triggerpattern.editor.*`
- Quick assist processors (Eclipse UI): `triggerpattern.ui.*`
- Cleanup framework bridge: `triggerpattern.cleanup.*`
- Extension point registries: GuardRegistry, HintFileRegistry, HintRegistry
- Eclipse platform integration: HintMarkerManager, HintContext
- Mining analysis/git (may move to core in future)
- CleanUp constants: MYCleanUpConstants
- Utility classes using ASTNodes/ScopeAnalyzer from JDT UI

### Guard Function Resolution

`GuardExpression.FunctionCall` uses `GuardFunctionResolverHolder` to
decouple from `GuardRegistry`. The registry sets itself as the resolver
in its constructor. This allows core code to evaluate guard expressions
without importing the registry directly.

### FixUtilities API Change

`FixUtilities.rewriteFix` takes `(Match, ASTRewrite, String)` instead of
`(HintContext, String)` to avoid depending on `HintContext` (which requires
`ICompilationUnit` from Eclipse platform). Callers that have a `HintContext`
simply pass `ctx.getMatch()` and `ctx.getASTRewrite()`.

### OSGi Compatibility

The module produces an OSGi-compatible JAR via `bnd-maven-plugin`, allowing
it to be used as a `Require-Bundle` dependency by `sandbox_common`.
