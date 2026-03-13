# Copilot Agent - Learned Lessons & Session Knowledge

> **Purpose**: This file captures hard-won knowledge from past agent sessions.
> Read this file ONLY when working on a task where these lessons are relevant.
> When you learn something new or fix a recurring issue, UPDATE THIS FILE.

## ⚠️ CRITICAL: Update This File

When you fix a bug or discover a pattern, **add it here immediately** so future sessions
(including after crashes) don't repeat the same mistakes. This is the agent's persistent memory.

---

## 1. NLS Comment Removal When Replacing String Literals

**Issue**: When replacing a `StringLiteral` (e.g., `"UTF-8"`) with a non-string expression
(e.g., `StandardCharsets.UTF_8`), the `//$NON-NLS-n$` line comment must be removed.

**Wrong** (leaves stale NLS comment):
```java
listRewrite.replace(nodedata.visited(), callToCharsetDefaultCharset, group);
```

**Correct** (removes NLS comment):
```java
try {
    ASTNodes.replaceAndRemoveNLS(rewrite, nodedata.visited(), callToCharsetDefaultCharset, group, cuRewrite);
} catch (CoreException e) {
    JavaManipulationPlugin.log(e);
}
```

**Required imports for replaceAndRemoveNLS**:
```java
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
```

**Rule**: ANY helper that replaces a StringLiteral in its `nodedata.replace()` branch
MUST use `ASTNodes.replaceAndRemoveNLS()`, never `listRewrite.replace()`.

---

## 2. Eclipse Import Order

Eclipse/Tycho enforces a specific import order. Follow these rules:

1. `java.*` and `javax.*` imports first
2. `org.eclipse.*` imports next
3. `org.sandbox.*` imports last
4. Static imports at the top (before regular imports)
5. **Never leave unused imports** — Tycho treats them as errors, not warnings

---

## 3. Test Pattern Enum Structure (ExplicitEncodingPatterns*)

All test pattern enums in `sandbox_encoding_quickfix_test` MUST have:

```java
String given;
String expected;
boolean skipCompileCheck;

EnumName(String given, String expected) {
    this(given, expected, true);  // default: skip compile check
}

EnumName(String given, String expected, boolean skipCompileCheck) {
    this.given = given;
    this.expected = expected;
    this.skipCompileCheck = skipCompileCheck;
}
```

**Rule**: If a test references `test.skipCompileCheck`, the enum MUST have the field and both constructors.

---

## 4. Test Expected Output Must Match Actual Cleanup Output

**Never guess** what the cleanup produces. The expected output in test patterns must exactly match
what the cleanup implementation generates, including:
- Import ordering (Eclipse's import organizer order)
- NLS comments (present or absent)
- Whitespace and indentation
- Exception declarations in method signatures (removed when no longer needed)

When a test fails, check the CI log's "but was:" section to see the actual output.

---

## 5. Do NOT Modify Tests Back and Forth

**Anti-pattern**: Changing test expectations to match broken implementation, then changing
implementation, then changing tests again. This wastes time and money.

**Correct approach**:
1. Read the CI log to see actual vs expected
2. Determine whether the **implementation** or the **test** is wrong
3. Fix the root cause ONCE
4. **Update this file** with the lesson learned

---

## 6. Common Encoding Cleanup Modes

The encoding cleanup has three modes controlled by `MYCleanUpConstants`:
- **KEEP_BEHAVIOR**: Replace implicit encoding with explicit `Charset.defaultCharset()` — preserves runtime behavior
- **ENFORCE_UTF8** (INSERT_UTF8): Replace with `StandardCharsets.UTF_8` — changes behavior to always use UTF-8
- **AGGREGATE_TO_UTF8**: Like ENFORCE_UTF8 but extracts a `private static final Charset UTF_8 = StandardCharsets.UTF_8` field

---

## 7. When Creating New Helper Classes

Every new `*ExplicitEncoding` helper class needs:
1. `find()` method — discovers AST nodes to transform
2. `rewrite()` method — applies the transformation using `ASTNodes.replaceAndRemoveNLS()` for replacements
3. `getPreview()` method — generates preview text
4. Proper imports including `CoreException`, `JavaManipulationPlugin`, `ASTNodes`
5. Call to `removeUnsupportedEncodingException()` if the original code could throw `UnsupportedEncodingException`

---

## 8. Import Order in Test Expected Output

**NEVER guess import ordering.** ALWAYS check the CI log's "but was:" section and copy
the exact import order from there.

---

## 9. Formatter Constructors Require Locale.getDefault()

`java.util.Formatter` has NO 2-argument constructor `(X, Charset)`.
All Charset-accepting constructors are 3-argument: `Formatter(X, Charset, Locale)`.

---

## 10. replaceAndRemoveNLS Conflicts with ListRewrite

Cannot mix `ASTNodes.replaceAndRemoveNLS()` with `ListRewrite` operations on the same parent node.
Use `listRewrite.replace()` + `listRewrite.insertLast()` together instead.

---

## 11. ASTRewrite Queues Changes — Original AST Is Unchanged Until Apply

`ASTRewrite.remove()` and `ASTRewrite.replace()` do NOT modify the original AST immediately.
Track removal counts explicitly when inspecting the tree after queueing removals.

---

## 12. NLS Comment Removal Must Target the LAST Comment

Use `LAST_NLS_COMMENT` pattern with negative lookahead to remove the last `//$NON-NLS-n$` on a line.

---

## 13. CRLF Line Ending Issues in Test Comparisons

Normalize line endings in test framework. Use `s.replace("\r\n", "\n").replace("\r", "\n");`

---

## 14. TriggerPattern DSL Lessons

- METHOD_DECLARATION annotation rewrite uses natural method-rewrite syntax
- `methodNameMatches` guard for regex method name filtering
- `isStatic`/`!isStatic` guards for static vs instance methods
- Multiline replacements: continuation lines after `=>` accumulated with `\n`

---

## 15. Recovered Bindings Break Annotation Matching

Always check `isRecovered()` before trusting `ITypeBinding.getQualifiedName()`.
Fall back to source-level names for recovered bindings.

---

## 16. JUnit 3 Migration Hints Must Check Superclass

Use `enclosingClassExtends("junit.framework.TestCase")` guard to avoid false positives.

---

## 17. Hint File Placement Rules

- Generic libraries → `sandbox_common_core` bundled resources
- Domain-specific libraries → respective plugin via extension point
- NEVER duplicate hint files between modules

---

## 18. Adding New BuiltInGuards — Checklist

1. Register in `BuiltInGuards.registerAll()`
2. Update `testAllBuiltInGuardsRegistered` test
3. Add to `dsl-explanation.md` in BOTH `sandbox_common_core` and `sandbox_mining_core`

---

## 19. AstProcessorBuilder: Chaining is SCOPED

Chained `onXxx()` calls create scoped visitors (each runs inside previous match).
Use separate `AstProcessorBuilder` instances for independent visitors.

---

## 20. Mining Core Modules Are Plain Maven JARs

`sandbox_mining_core` and `sandbox_common_core` use `maven-compiler-plugin`, no Xvfb needed.

---

## 21. Whitespace Normalization in Tests

Normalize: line endings, tabs→spaces, trailing whitespace, blank lines.
ALWAYS check CI logs for actual output before writing expected test strings.
