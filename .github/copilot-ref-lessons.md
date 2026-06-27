# Copilot Agent - Learned Lessons & Session Knowledge

> **Read this when**: You hit a recurring bug, need to understand a past fix, or are working on encoding/JUnit/DSL areas.
> When you fix a bug or discover a pattern, **UPDATE THIS FILE** so future sessions don't repeat mistakes.

---

## 1. NLS Comment Removal When Replacing String Literals

**Issue**: When replacing a `StringLiteral` (e.g., `"UTF-8"`) with a non-string expression (e.g., `StandardCharsets.UTF_8`), the `//$NON-NLS-n$` line comment must be removed.

**Wrong**:
```java
listRewrite.replace(nodedata.visited(), callToCharsetDefaultCharset, group);
```

**Correct**:
```java
try {
    ASTNodes.replaceAndRemoveNLS(rewrite, nodedata.visited(), callToCharsetDefaultCharset, group, cuRewrite);
} catch (CoreException e) {
    JavaManipulationPlugin.log(e);
}
```

**Required imports**: `CoreException`, `JavaManipulationPlugin`, `ASTNodes`

**Rule**: ANY helper that replaces a StringLiteral MUST use `ASTNodes.replaceAndRemoveNLS()`, never `listRewrite.replace()`.

---

## 2. Eclipse Import Order

1. `java.*` and `javax.*` imports first
2. `org.eclipse.*` imports next
3. `org.sandbox.*` imports last
4. Static imports at the top
5. **Never leave unused imports** — Tycho treats them as errors

---

## 3. Test Pattern Enum Structure

All test pattern enums in `sandbox_encoding_quickfix_test` MUST have `given`, `expected`, `skipCompileCheck` fields and two constructors (2-arg defaulting skip=true, 3-arg explicit).

---

## 4. Test Expected Output Must Match Actual Cleanup Output

**Never guess.** Check CI log's "but was:" section. Must exactly match imports, whitespace, NLS comments, exception declarations.

---

## 5. Do NOT Modify Tests Back and Forth

Fix root cause ONCE. Read CI log → determine if implementation or test is wrong → fix once → update this file.

---

## 6. Common Encoding Cleanup Modes

- **KEEP_BEHAVIOR**: `Charset.defaultCharset()` — preserves runtime behavior
- **ENFORCE_UTF8**: `StandardCharsets.UTF_8` — forces UTF-8
- **AGGREGATE_TO_UTF8**: Like ENFORCE but extracts static field

---

## 7. When Creating New Helper Classes

Every `*ExplicitEncoding` helper needs: `find()`, `rewrite()` (with `replaceAndRemoveNLS`), `getPreview()`, proper imports, `removeUnsupportedEncodingException()` if applicable.

---

## 8. Import Order in Test Expected Output — Complex Reordering!

**#1 most recurring bug.** Eclipse ImportRewrite behaves differently when imports are only added vs added+removed.

- **Case 1 (only added)**: Existing imports keep original order, new imports appended
- **Case 2 (added+removed)**: Eclipse may reposition existing imports

**Rule**: NEVER guess. ALWAYS check CI log and copy exact import order.

---

## 9. Formatter Constructors Require Locale.getDefault()

`java.util.Formatter` has NO 2-arg `(X, Charset)` constructor. All are 3-arg: `Formatter(X, Charset, Locale)`. 

---

## 10. replaceAndRemoveNLS Conflicts with ListRewrite

Cannot mix on same parent node. `listRewrite.insertLast()` silently fails. Use `listRewrite.replace()` + `listRewrite.insertLast()` together. Only affects `FormatterExplicitEncoding`.

---

## 11. Text Block Indentation for Newly-Added Imports

Newly-added imports in test text blocks need **fewer tabs** than existing content to match 0 leading whitespace in cleanup output.

---

## 12. Given Input Must Also Compile When Using FullCompileCheck

`assertRefactoringResultAsExpectedWithFullCompileCheck` checks given input compiles BEFORE running cleanup. All types in given code must be properly imported.

---

## 13. ASTRewrite Queues Changes — Original AST Is Unchanged Until Apply

`ASTRewrite.remove()` does NOT modify AST immediately. Track removal counts explicitly.

---

## 14. NLS Comment Removal Must Target the LAST Comment

Use `LAST_NLS_COMMENT` pattern with negative lookahead. In `replaceTryBodyAndUnwrap()`, only apply to the statement containing the visited node.

---

## 15. TriggerPattern DSL: METHOD_DECLARATION Annotation Rewrite

Natural method-rewrite syntax for adding annotations. `methodNameMatches` guard, `isStatic`/`!isStatic` guards, multiline replacements with `\n` joining.

Key files: `BuiltInGuards.java`, `HintFileFixCore.java`, `HintFileParser.java`, `HintFileStore.java`

---

## 16. Recovered Bindings Break Annotation Matching

Always check `isRecovered()` before trusting `ITypeBinding.getQualifiedName()`. Fall back to `annotation.getTypeName().getFullyQualifiedName()`.

Affects: `LambdaASTVisitor.java` — all annotation visit/endVisit methods and field type matching.

---

## 17. JUnit 3 Migration Hints Must Check Superclass

Use `enclosingClassExtends("junit.framework.TestCase")` guard to avoid false positives on non-TestCase classes.

---

## 18. CRLF Line Ending Issues in Test Comparisons

Normalize with `s.replace("\r\n", "\n").replace("\r", "\n");`. Fix BOTH source files AND test framework.

---

## 19. Hint File (.sandbox-hint) Placement Rules

- Generic libraries → `sandbox_common_core` bundled resources
- Domain-specific → respective plugin via extension point in `plugin.xml`
- NEVER duplicate between modules
- `sandbox_common/src/` should contain NO hint files

---

## 20. JUnit Cleanup Plugin Architecture

- `JUNIT_CLEANUP_4_SUITE` is NOT mapped — enabling it has no effect
- `JUNIT_CLEANUP_4_RUNWITH` controls `RunWithJUnitPlugin`
- `RUNWITH_ENCLOSED`, `RUNWITH_THEORIES`, `RUNWITH_CATEGORIES` always active when JUNIT_CLEANUP enabled

---

## 21. ThrowingRunnableJUnitPlugin Already Handles ParameterizedType

Check if features are already implemented before re-implementing. `@Disabled` messages may be outdated.

---

## 22. Generic Type Parameter Method Call Migration (.run() → .execute())

4 strategies for resolving ThrowingRunnable through generics. Enhanced `isThrowingRunnableType()` checks erasure, type variable bounds, capture bindings, interfaces, superclass.

---

## 23. DslExplanationGuardSyncTest — New Guards Require Doc Update

After adding guards to `BuiltInGuards.registerAll()`, update `dsl-explanation.md` in BOTH `sandbox_common_core` and `sandbox_mining_core`.

---

## 24. TriggerPatternEngine Type Hierarchy — Use Visited Set

Any method that recursively walks `getSuperclass()`/`getInterfaces()` MUST use `Set<String> visited` to prevent infinite recursion.

---

## 25. Adding New BuiltInGuards — Complete Checklist

1. Register in `BuiltInGuards.registerAll()`
2. Update `testAllBuiltInGuardsRegistered` test
3. Add to `dsl-explanation.md` in BOTH modules

---

## 26. HintFileParser Lazy Initialization Pattern

Use `volatile` + double-checked locking for cached reflection data.

---

## 27. Per-Rule Metadata in DSL — @id: and @severity:

`@id:` and `@severity:` lines before source pattern in rule block. NOT comments. Consumed by `buildRule()`.

---

## 28. AstProcessorBuilder: Chaining is SCOPED

Chained `onXxx()` calls create scoped visitors. Use separate builder instances for independent visitors.

---

## 29. Mining Core: Brace Balance in MiningCli.java

Count braces before committing. Flat indentation makes scope hard to track.

---

## 30. Mining Core: New Modules Are Plain Maven JARs

`sandbox_mining_core` and `sandbox_common_core` use `maven-compiler-plugin`, no Xvfb needed. SpotBugs runs during compile.

---

## 31. Whitespace Normalization in Refactoring Tests

Normalize: line endings, tabs→spaces, trailing whitespace, blank lines. ALWAYS check CI logs for actual output.