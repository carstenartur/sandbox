# Sandbox Common Module - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#common) | [Architecture](ARCHITECTURE.md)

## Status Summary

**Current State**: Stable foundation module with improved documentation

### Completed
- ✅ `MYCleanUpConstants` - Central repository for cleanup constants
- ✅ Package structure mirrors Eclipse JDT for easy porting
- ✅ Integration with all sandbox plugins
- ✅ All constants in `LibStandardNames` are now public with comprehensive Javadoc
- ✅ Comprehensive Javadoc for `ReferenceHolder`, `ASTProcessor`, and `VisitorEnum`
- ✅ Mining Analysis Phase 2+3: `RuleInferenceEngine`, `AstDiffAnalyzer`, `PlaceholderGeneralizer`, `ConfidenceCalculator`, `ImportDiffAnalyzer`, `RuleGrouper`, `InferredRuleValidator` (Issue #727)
- ✅ Mining Phase 1.3+3.2: `DiffHunkRefiner`, `DiffHunk`, `FileDiff`, `CommitInfo`, `CommitAnalysisResult`, `CommitAnalysisListener` (Issue #727)
- ✅ Mining Tests: `RuleGrouperTest`, `ImportDiffAnalyzerTest`, `InferredRuleValidatorTest`, `DiffHunkRefinerTest`, `CommitAnalysisResultTest` (Issue #727)
- ✅ Mining Phase 1.1+1.2: `GitHistoryProvider` interface, `CommandLineGitProvider`, `AsyncCommitAnalyzer`, `GitProviderException` (Issue #727)
- ✅ Mining Phase 3: `RuleInferenceEngine.inferFromCommit()` and `inferFromHistory()` methods (Issue #727)
- ✅ Mining Tests: `CommandLineGitProviderTest`, `AsyncCommitAnalyzerTest` (Issue #727)
- ✅ Mining Phase 4: Eclipse UI — `RefactoringMiningView`, `CommitTableEntry`, `CommitTableContentProvider`, `CommitTableLabelProvider`, `CommitAnalysisJob`, `CommitAnalysisScheduler`, `InferredRuleDetailPanel` (Issue #727)
- ✅ Mining Phase 5.1: `HintFileRegistry.registerInferredRules()`, `getInferredHintFiles()`, `promoteToManual()` (Issue #727)
- ✅ Mining Phase 5.2: Export functionality — `.sandbox-hint` file export from RefactoringMiningView (Issue #727)
- ✅ Mining Tests: `HintFileRegistryInferredTest` (Issue #727)

### In Progress
- None currently

### Pending
- [ ] Create dedicated test module for unit testing utilities
- [ ] Extract additional shared utilities from individual plugins
- [ ] Enhance documentation of available utilities
- [ ] Monitor Eclipse JDT UI for new AST node types and update helper APIs accordingly
- [ ] Mining Phase 1.2: `JGitHistoryProvider` and `EGitHistoryProvider` implementations (Issue #727)
- [ ] Mining Phase 5.2: `RuleUsageTracker` — feedback loop tracking rule usage counts (Issue #727)

## Priority Tasks

### 1. Monitor Eclipse JDT AST Changes
**Priority**: Medium (Ongoing Maintenance)  
**Effort**: 1-2 hours per Eclipse release

Regularly check Eclipse JDT UI releases for new AST node types:
- Review Eclipse JDT UI release notes for AST changes
- Check for new node types in `org.eclipse.jdt.core.dom` package
- Update helper visitor APIs (`HelperVisitor`, `ASTProcessor`, etc.) to support new nodes
- Ensure utilities like `ASTNavigationUtils` handle all current node types

**Process**:
1. When new Eclipse version is released, check JDT UI changelog
2. Identify new AST node types or modifications
3. Update `VisitorEnum` if new visitor methods are needed
4. Extend `HelperVisitor` and related APIs to support new nodes
5. Test with sample code using new AST features
6. Update documentation

**Benefits**:
- Keeps utilities current with Eclipse JDT
- Prevents missing node type support
- Ensures complete AST coverage

### 2. Audit for Additional Shared Code
**Priority**: Medium  
**Effort**: 4-6 hours

Scan all sandbox plugins for duplicated utility code that could be centralized in `sandbox_common`:
- AST manipulation patterns
- Import management helpers
- Type resolution utilities
- Common transformation patterns

**Benefits**:
- Reduces code duplication
- Improves maintainability
- Easier to port to Eclipse JDT

### 3. Create Dedicated Test Module
**Priority**: Low  
**Effort**: 2-3 hours

Currently, shared utilities lack dedicated unit tests. Consider creating `sandbox_common_test` if the utility code grows.

**Requirements**:
- Test `MYCleanUpConstants` integrity
- Test any shared utility methods
- Follow existing test patterns from other modules

### 4. Document Available Utilities
**Priority**: Medium  
**Effort**: 2-3 hours

Create comprehensive documentation for shared utilities:
- Method signatures and purposes
- Usage examples from existing plugins
- Best practices for using common utilities
- Update this architecture.md with detailed utility descriptions

## Known Issues

None currently identified.

## Future Enhancements

### Utility Library Expansion
Consider adding more sophisticated shared utilities:
- **AST Builders**: Fluent builders for common AST node types
- **Matcher Library**: Reusable AST pattern matchers
- **Transformation Templates**: Common code transformation patterns
- **Validation Utilities**: Shared precondition checking logic

### Configuration Framework
Explore creating a configuration framework for cleanup preferences:
- Standard patterns for defining cleanup options
- UI generation helpers for preference pages
- Validation and default value management

### Documentation Generation
Consider auto-generating documentation from constant definitions:
- Extract cleanup descriptions from constant names
- Generate cleanup catalog from `MYCleanUpConstants`
- Create developer guide for adding new cleanups

## Technical Debt

### Constant Organization
**Priority**: Low

`MYCleanUpConstants` currently contains constants from all plugins. Consider whether to:
- Keep centralized (current approach - simpler)
- Split into plugin-specific constant classes (more modular)
- Use a hybrid approach (core constants centralized, plugin-specific constants distributed)

**Decision Criteria**:
- How does Eclipse JDT organize their constants?
- What makes porting to Eclipse JDT easier?
- What improves maintainability for sandbox development?

## Testing Strategy

Currently, testing is indirect through dependent plugins. Future improvements:
- Direct unit tests for utility methods
- Integration tests for constant integrity
- Validation that all cleanup IDs are unique
- Verification that constants follow naming conventions

## Contribution to Eclipse JDT

### Prerequisites
Before contributing shared utilities to Eclipse JDT:
1. Ensure utilities are well-tested and documented
2. Verify they follow Eclipse coding conventions
3. Confirm they don't duplicate existing Eclipse utilities
4. Get approval for the utility's inclusion in Eclipse codebase

### Porting Checklist
- [ ] Replace `org.sandbox` with `org.eclipse` in package names
- [ ] Merge `MYCleanUpConstants` into Eclipse's `CleanUpConstants`
- [ ] Move utilities to appropriate Eclipse modules
- [ ] Update all dependent plugins to use Eclipse constants
- [ ] Submit to Eclipse Gerrit for review
- [ ] Update documentation to reflect new Eclipse structure

## References

- [Eclipse CleanUpConstants](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java)
- [Eclipse JDT Core Manipulation](https://github.com/eclipse-jdt/eclipse.jdt.ui/tree/master/org.eclipse.jdt.core.manipulation)

## Contact

For questions about shared utilities or adding new common code, please open an issue or discussion in the repository.

## TriggerPattern Hint Engine

### Completed (v1.2.2)
- [x] Core pattern parser for expressions and statements
- [x] Placeholder matching with `$x` syntax
- [x] Pattern matching engine with AST traversal
- [x] HintContext for providing context to hints
- [x] Annotation-based hint registration (`@TriggerPattern`, `@Hint`)
- [x] Extension point for hint providers
- [x] HintRegistry with lazy loading
- [x] Quick Assist processor integration
- [x] Example hint provider (increment/decrement simplification)
- [x] Test module with parser, matcher, and engine tests

### Completed (v1.2.3)
- [x] Extended pattern parser for annotations, method calls, imports, and fields
- [x] New PatternKind values: ANNOTATION, METHOD_CALL, IMPORT, FIELD
- [x] Annotation pattern matching with placeholder support
- [x] Method call pattern matching with placeholder support
- [x] Import declaration pattern matching
- [x] Field declaration pattern matching
- [x] Optional qualifiedType field in Pattern class for type-aware matching
- [x] Order-independent annotation member-value pair matching
- [x] Comprehensive test suite for new pattern kinds (NewPatternKindsTest)
- [x] Updated documentation with examples for JUnit migration use cases

### Completed (v1.3.2 - Hint File Parser Engine, Phases 1-5)
- [x] Phase 1: Variadic Placeholders (`$args$` syntax)
  - [x] Match argument lists: `method($args$)` → 0..n arguments
  - [x] Match statement sequences: `{ $stmts$ }` → 0..n statements
  - [x] Mixed patterns: `method($a, $args$)` and `method($args$, $last)`
  - [x] Binding as `List<ASTNode>` via `Match.getListBinding()`
- [x] Phase 2: Multi-Statement Pattern Matching
  - [x] `STATEMENT_SEQUENCE` PatternKind with sliding-window matching
  - [x] Statement sequence matching within larger blocks
- [x] Phase 3: Guard Functions Framework
  - [x] Guard expression AST: `FunctionCall`, `And`, `Or`, `Not`
  - [x] Recursive descent parser for guard expressions
  - [x] Built-in guards: `instanceof` (with array type support `Type[]`), `matchesAny` (with literal matching via `extractNodeText()`), `matchesNone` (with literal matching), `hasNoSideEffect`, `sourceVersionGE/LE/Between`, `isStatic`, `isFinal`, `hasAnnotation`, `isDeprecated`, `referencedIn`, `elementKindMatches`, `contains`, `notContains`
  - [x] Custom guard registration via `GuardRegistry.register()`
  - [x] String literal support in guard expression arguments
  - [x] Locale-safe `toUpperCase(Locale.ROOT)` in element kind matching
  - [x] Unterminated string literal detection with clear error messages
- [x] Phase 4: Conditional Multi-Rewrite
  - [x] `RewriteAlternative` with replacement + optional guard
  - [x] `TransformationRule` with ordered alternatives
  - [x] `otherwise` as catch-all (last alternative)
- [x] Phase 5: DSL File Format Parser (`.sandbox-hint`)
  - [x] Metadata directives, comment stripping, pattern kind inference
  - [x] Rule syntax: Pattern `::` guard `=>` rewrite
  - [x] Multi-rewrite support, hint-only rules, error handling

### Completed (v1.3.3 - Improvements over NetBeans, Phase 6)
- [x] Phase 6.1: Import Management
  - [x] `ImportDirective` class with `addImport`, `removeImport`, `addStaticImport`, `removeStaticImport`
  - [x] Auto-detection from fully qualified names in replacement patterns
  - [x] Integration into `TransformationRule` and `HintFileParser`
  - [x] DSL syntax: `addImport`/`removeImport`/`addStaticImport`/`removeStaticImport` directives in rules
- [x] Phase 6.2: Pattern Libraries
  - [x] `HintFileRegistry` singleton for loading/managing `.sandbox-hint` files
  - [x] Support for classpath resources, strings, and readers
  - [x] Bundled library names defined (encoding, collections, modernize-java9, modernize-java11, performance, junit5)
  - [x] Bundled `.sandbox-hint` pattern library files created:
    - [x] `encoding.sandbox-hint` — 7 StandardCharsets migration rules with guards and import directives
    - [x] `collections.sandbox-hint` — 7 Collection API modernization rules (Java 9+)
    - [x] `modernize-java9.sandbox-hint` — 7 Java 9+ API modernization rules (Collections.unmodifiable, Optional, Stream.toList)
    - [x] `modernize-java11.sandbox-hint` — 7 Java 11+ API modernization rules
    - [x] `performance.sandbox-hint` — 9 performance optimization rules
    - [x] `junit5.sandbox-hint` — 8 JUnit 4 → 5 migration rules with import directives
  - [x] `loadBundledLibraries()` loads all bundled files from classpath
- [x] Phase 6.3: Negated Patterns
  - [x] `contains` guard: checks if text pattern occurs in enclosing method body
  - [x] `notContains` guard: checks if text pattern does NOT occur in method body
  - [x] `GuardContext.getMatchedNode()` for context-based search
- [x] Phase 6.4: Preview Generation
  - [x] `PreviewGenerator` with automatic placeholder substitution
  - [x] `Preview` record with `before`, `after`, `description`, `format()`
  - [x] Default example values for common placeholder names
  - [x] Support for variadic placeholder substitution
- [x] Phase 6.5: Dry-Run / Reporting
  - [x] `DryRunReporter` — finds all matches without modifying code
  - [x] `ReportEntry` record with line, offset, matched code, suggested replacement
  - [x] JSON report generation via `toJson()`
  - [x] CSV report generation via `toCsv()`
  - [x] Placeholder substitution in suggested replacements
  - [x] Support for hint-only rules (no replacement)

### Completed (v1.3.4 - Pattern Composition)
- [x] Pattern composition via `<!include:>` directive
  - [x] `HintFile.getIncludes()` / `HintFile.addInclude()` for referencing other hint files by ID
  - [x] `HintFileRegistry.resolveIncludes()` for recursive include resolution
  - [x] Circular include detection (silently breaks cycles)
  - [x] Missing include references silently skipped
  - [x] `BatchTransformationProcessor` constructor with pre-resolved rules
  - [x] Comprehensive test suite (PatternCompositionTest)

### Completed (v1.3.5 - DSL-to-CleanUp/QuickAssist Bridge, Phase 7.3)
- [x] Phase 7.3: Quick Fix / Clean Up Integration from `.sandbox-hint` files
  - [x] `HintFileFixCore` — bridges `.sandbox-hint` DSL to `CompilationUnitRewriteOperation`
  - [x] `HintFileCleanUpCore` / `HintFileCleanUp` — Eclipse CleanUp wrapper for hint file rules
  - [x] `HintFileQuickAssistProcessor` — Quick Assist from `.sandbox-hint` file rules
  - [x] CleanUp preference checkbox (`HINTFILE_CLEANUP` constant)
  - [x] Save Actions integration via `DefaultCleanUpOptionsInitializer` / `SaveActionCleanUpOptionsInitializer`
  - [x] UI preferences in `SandboxCodeTabPage`
  - [x] Registered in `plugin.xml` (both CleanUp and QuickAssist)
  - [x] Import directive support in rewrite operations

### Completed (v1.3.6 - Workspace Hint Files, Phase 5.4)
- [x] Phase 5.4: Workspace configuration for per-project `.sandbox-hint` files
  - [x] `HintFileRegistry.loadProjectHintFiles(IProject)` — discovers and loads `.sandbox-hint` files from project directories
  - [x] `HintFileRegistry.invalidateProject(IProject)` — forces re-scan on next access
  - [x] Automatic project scanning in `HintFileFixCore` (CleanUp) and `HintFileQuickAssistProcessor` (Quick Assist)
  - [x] Project-scoped IDs: `project:<name>:<relative-path>` for namespace isolation
  - [x] Skips hidden directories, `bin/`, and `target/` folders
  - [x] At-most-once scanning per project (tracked via `loadedProjects` set)
  - [x] Comprehensive test suite (`WorkspaceHintFileTest`)

### Planned Enhancements

#### High Priority
- [x] Performance optimization: `PatternIndex` indexes patterns by kind for single-traversal batch matching
- [x] Thread-safety: `HintFileRegistry` uses `ConcurrentHashMap` and `AtomicBoolean`
- [x] `PlaceholderAstMatcher.mergeBindings()` for combining bindings across multiple matchers
- [x] Cleanup integration
  - [x] Batch processing: `BatchTransformationProcessor` applies all rules from a `HintFile` in a single pass
  - [x] CleanUp implementation using TriggerPattern engine (Eclipse CleanUp wrapper) — `HintFileCleanUp`
  - [x] Save Actions integration — `HintFileCleanUpCore` registered in `SaveActionCleanUpOptionsInitializer`
- [x] Pattern composition
  - [x] Allow patterns to reference other patterns via `<!include:>` directive
- [x] Quick Assist from `.sandbox-hint` files — `HintFileQuickAssistProcessor`
- [x] Workspace hint files — users can place `.sandbox-hint` files in projects
- [ ] Add integration tests for HintRegistry and extension point loading
- [ ] Add UI tests for Quick Assist processor (requires PDE test setup)
- [ ] Documentation: User guide for creating custom hints

#### Low Priority
- [ ] Pattern debugging tools
  - [ ] Visualize pattern matches in AST
  - [ ] Test harness for pattern development
- [ ] Performance monitoring
  - [ ] Track pattern matching performance
  - [ ] Identify slow patterns
- [ ] Advanced pattern features
  - [ ] Optional parts: `$x?.method()`
  - [ ] Repetition: `$x+` (one or more)

### Known Issues
- None at this time

### Technical Debt
- Consider separating TriggerPattern into its own plugin for better modularity
- Pattern parser could be made more robust with better error handling
