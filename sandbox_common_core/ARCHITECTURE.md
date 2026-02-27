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
- LLM layer (`org.sandbox.jdt.triggerpattern.llm`): LlmClient, LlmProvider, LlmClientFactory,
  OpenAiCompatibleClient, GeminiClient, OpenAiClient, DeepSeekClient, QwenClient, LlamaClient,
  MistralClient, CommitEvaluation, PromptBuilder, DslContextCollector
- Git layer (`org.sandbox.jdt.triggerpattern.git`): CommitWalker, DiffExtractor, RepoCloner
- DSL validation: DslValidator (in `org.sandbox.jdt.triggerpattern.internal`)

### What stays in sandbox_common

- Editor support (SWT/JFace): `triggerpattern.editor.*`
- Quick assist processors (Eclipse UI): `triggerpattern.ui.*`
- Cleanup framework bridge: `triggerpattern.cleanup.*`
- `GuardRegistry` — singleton that delegates to `BuiltInGuards` and adds extension-point loading
- `HintFileRegistry` — singleton that delegates to `HintFileStore` and adds workspace/extension-point loading
- `HintRegistry` — annotation-based hint registration with extension-point support
- Eclipse platform integration: HintMarkerManager, HintContext
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

**Recent additions (Phase 1.1/1.3)**:
- `subtypeOf($var, "fqn")` — walks type hierarchy to check subtype relationships
- `hasSuppressWarnings("key")` — checks if enclosing declaration has `@SuppressWarnings`

**Tier 2 additions (Phase 1.2/2.1/2.3)**:
- `hasField("name")` — checks if enclosing class has a field with given name
- `isInLoop()` — checks if matched node is inside a loop (for/while/do/enhanced-for)
- `paramCount(n)` — checks if enclosing method has exactly n parameters
- `hasReturnType("type")` — checks if enclosing method return type matches
- `isStringLiteral($var)` — checks if placeholder is a StringLiteral node

**Tier 3 additions (Phase 3.4/3.5)**:
- `isPublic($var)` — checks if binding has public access modifier
- `isPrivate($var)` — checks if binding has private access modifier
- `isProtected($var)` — checks if binding has protected access modifier
- `throwsException("type")` — checks if enclosing method declares a matching throws clause

### Per-Rule Metadata (Phase 3.1-3.3)

Rules within a `.sandbox-hint` file can have per-rule annotations:
- `@id: encoding.fileReader` — unique ID for usage tracking via `RuleUsageTracker`
- `@severity: error` — overrides the hint-file-level severity

These annotations appear on lines before the source pattern within a rule block.
`TransformationRule.getRuleId()` and `TransformationRule.getSeverity()` expose them.

### DSL `<!treeKind:>` Directive (Phase 1.2)

The `HintFileParser` now supports `<!treeKind: METHOD_DECLARATION, IF_STATEMENT>`
directives. AST node type names are resolved via lazy-initialized reflection on
`ASTNode` class constants. The resolved node types are stored in
`HintFile.getTreeKindNodeTypes()` for downstream consumers (e.g., `HintFileFixCore`)
to use with `TriggerPatternEngine.findMatchesByNodeType()`.

### SuppressWarningsChecker

Utility class in `internal` package that walks up the AST from a given node,
checking each enclosing `BodyDeclaration` for a `@SuppressWarnings` annotation
containing a given key. Supports `SingleMemberAnnotation` and `NormalAnnotation`
forms, including array initializer values.

### Type Constraint Resolution (Phase 1.1)

`TriggerPatternEngine.findMatches(ICompilationUnit, Pattern)` now enables
`setResolveBindings(true)` when the pattern has `ConstraintVariableType[]`
constraints. After structural matching, `checkTypeConstraints()` filters
matches to retain only those where bound nodes satisfy the type constraints
via `ITypeBinding`. Graceful degradation: constraints are skipped when
binding resolution is not available.

### HintFileStore (Grenzfall 3)

Eclipse-independent hint file storage is in `HintFileStore`. It provides:
- In-memory ConcurrentHashMap storage with secondary index by declared ID
- `loadFromString()`, `loadFromReader()`, `loadFromClasspath()`
- Include resolution with circular-reference detection
- Bundled library loading (generic, non-domain-specific libraries only)
- Inferred rule management

**Bundled libraries** (loaded via `HintFileStore.loadBundledLibraries()`):
- `collections.sandbox-hint`, `modernize-java9.sandbox-hint`,
  `modernize-java11.sandbox-hint`, `performance.sandbox-hint`
- These are generic pattern libraries usable by CLI tools, standalone tests,
  and any JVM application without Eclipse/OSGi.

**Domain-specific hint files** live in their respective plugins and are loaded
via the `org.sandbox.jdt.triggerpattern.hints` extension point:
- `sandbox_encoding_quickfix`: `encoding.sandbox-hint`
- `sandbox_junit_cleanup`: `junit5.sandbox-hint`, `annotations5.sandbox-hint`,
  `assume5.sandbox-hint`, `junit3-migration.sandbox-hint`

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

### LLM Layer (Issue #727, Phase 0)

The `org.sandbox.jdt.triggerpattern.llm` package contains Eclipse-independent
LLM client abstractions and implementations, extracted from `sandbox_mining_core`
so that both the CLI tool and the Eclipse plugin can reuse the same infrastructure.

- `LlmClient` — interface for all LLM providers
- `LlmProvider` — enum of supported providers (GEMINI, OPENAI, DEEPSEEK, QWEN, LLAMA, MISTRAL)
- `LlmClientFactory` — factory with auto-detection from environment variables
- `OpenAiCompatibleClient` — abstract base class for OpenAI-compatible providers
- Concrete clients: `GeminiClient`, `OpenAiClient`, `DeepSeekClient`, `QwenClient`, `LlamaClient`, `MistralClient`
- `CommitEvaluation` — record holding evaluation results
- `PromptBuilder` — constructs LLM prompts with DSL context
- `DslContextCollector` — walks directory tree to collect `.sandbox-hint` files

### Git Layer (Issue #727, Phase 0)

The `org.sandbox.jdt.triggerpattern.git` package provides Eclipse-independent
Git operations using JGit:

- `CommitWalker` — iterates commits with date filtering and batch pagination
- `DiffExtractor` — extracts diffs with truncation and path filtering
- `RepoCloner` — clones repositories (full or shallow)
