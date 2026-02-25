# Copilot Agent - Learned Lessons & Session Knowledge

> **Purpose**: This file captures hard-won knowledge from past agent sessions.
> Every new agent session MUST read this file before making changes.
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

**Files that follow this pattern** (in `sandbox_encoding_quickfix/.../helper/`):
- InputStreamReaderExplicitEncoding ✅ (was already correct)
- OutputStreamWriterExplicitEncoding ✅ (was already correct)
- ChannelsNewReaderExplicitEncoding ✅ (was already correct)
- StringExplicitEncoding ✅ (was already correct)
- StringGetBytesExplicitEncoding ✅ (was already correct)
- ByteArrayOutputStreamExplicitEncoding ✅ (fixed 2026-02-21)
- FilesNewBufferedReaderExplicitEncoding ✅ (fixed 2026-02-21)
- FilesNewBufferedWriterExplicitEncoding ✅ (fixed 2026-02-21)
- FilesReadAllLinesExplicitEncoding ✅ (fixed 2026-02-21)
- FilesReadStringExplicitEncoding ✅ (fixed 2026-02-21)
- FilesWriteStringExplicitEncoding ✅ (fixed 2026-02-21)
- FormatterExplicitEncoding ✅ (fixed 2026-02-21)
- PropertiesStoreToXMLExplicitEncoding ✅ (fixed 2026-02-21)
- ScannerExplicitEncoding ✅ (fixed 2026-02-21)
- URLDecoderDecodeExplicitEncoding ✅ (fixed 2026-02-21)
- URLEncoderEncodeExplicitEncoding ✅ (fixed 2026-02-21)

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

**Files that use this pattern**:
- `Java8/ExplicitEncodingPatterns.java` ✅
- `Java10/ExplicitEncodingPatternsKeepBehavior.java` ✅ (fixed 2026-02-21)
- `Java10/ExplicitEncodingPatternsPreferUTF8.java` ✅
- `Java10/ExplicitEncodingPatternsAggregateUTF8.java` ✅
- `Java22/ExplicitEncodingPatterns.java` — uses `assertRefactoringResultAsExpectedWithFullCompileCheck` directly (no skipCompileCheck field needed currently)

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

Each mode produces different output. Test patterns are separated by mode:
- `ExplicitEncodingPatternsKeepBehavior` → KEEP_BEHAVIOR
- `ExplicitEncodingPatternsPreferUTF8` → ENFORCE_UTF8
- `ExplicitEncodingPatternsAggregateUTF8` → AGGREGATE_TO_UTF8

---

## 7. When Creating New Helper Classes

Every new `*ExplicitEncoding` helper class needs:
1. `find()` method — discovers AST nodes to transform
2. `rewrite()` method — applies the transformation using `ASTNodes.replaceAndRemoveNLS()` for replacements
3. `getPreview()` method — generates preview text
4. Proper imports including `CoreException`, `JavaManipulationPlugin`, `ASTNodes`
5. Call to `removeUnsupportedEncodingException()` if the original code could throw `UnsupportedEncodingException`

---

## 8. ⚠️ Import Order in Test Expected Output — Complex Reordering Behavior!

**This is the #1 most recurring bug.** The encoding cleanup's import behavior is nuanced
and varies depending on whether imports are only added, or both added and removed.

### Case 1: Imports only ADDED (no imports removed)

The cleanup preserves the ORIGINAL import order from the given input. New imports
(e.g., `java.nio.charset.Charset`, `java.nio.charset.StandardCharsets`) are appended at the end,
but existing imports stay in their original order.

**Also**: The cleanup retains `import java.io.UnsupportedEncodingException;` even after removing
it from catch clauses (the `ImportRemover` doesn't always remove it in the union-type path).

**Rules for Case 1**:
1. Keep existing `java.io.*` imports in the **same order** as the given input
2. Append newly-added imports (`java.nio.charset.*`, etc.) after the existing imports
3. Keep `import java.io.UnsupportedEncodingException;` if it was in the given input
4. **NEVER sort imports alphabetically** — that does not match what the cleanup produces

### Case 2: Imports ADDED and REMOVED (e.g., PRINTWRITER replaces entire constructor)

When the cleanup **replaces a type entirely** (e.g., `new PrintWriter(filename)` →
`new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), Charset.defaultCharset()))`),
Eclipse's ImportRewrite may **reposition existing imports** that share a package with newly-added ones.

**Example** — PRINTWRITER given imports: `PrintWriter, Writer, FileNotFoundException`

**Actual output** (from CI):
```java
import java.io.PrintWriter;        // preserved (not removed by cleanup)
import java.io.Writer;             // preserved
import java.nio.charset.Charset;   // newly added
import java.io.BufferedWriter;     // newly added
import java.io.FileNotFoundException; // repositioned (was 3rd, now 5th)
import java.io.FileOutputStream;   // newly added
import java.io.OutputStreamWriter; // newly added
```

**Key observation**: `FileNotFoundException` moved from position 3 to position 5 because
Eclipse's ImportRewrite reorganized the `java.io.*` group when adding new imports to it.
The `java.nio.charset.Charset` import appears before the new `java.io.*` imports.

### General Rule

**NEVER guess import ordering.** ALWAYS check the CI log's "but was:" section and copy
the exact import order from there. The behavior depends on which specific imports are
added/removed and how Eclipse's ImportRewrite handles the interaction.

**Affected files** (all test pattern enums with expected output):
- `Java22/ExplicitEncodingPatterns.java` ✅ (fixed 2026-02-21: FILEWRITER, FILEREADER, INPUTSTREAMREADER, OUTPUTSTREAMWRITER, PRINTWRITER, STRINGGETBYTES, THREE, ENCODINGASSTRINGPARAMETER, CHANNELSNEWREADER, CHANNELSNEWWRITER)
- `Java10/ExplicitEncodingPatternsKeepBehavior.java` — check if same issue exists
- `Java10/ExplicitEncodingPatternsPreferUTF8.java` — check if same issue exists
- `Java10/ExplicitEncodingPatternsAggregateUTF8.java` — check if same issue exists
- `Java8/ExplicitEncodingPatterns.java` — check if same issue exists

**Rule**: EVERY time you write or modify a test expected output, verify that imports are in
correct alphabetical order. This is NOT optional.

---

## 9. ⚠️ Formatter Constructors Require Locale.getDefault() — No 2-arg Formatter(X, Charset) Exists

**Issue**: `java.util.Formatter` has NO 2-argument constructor that takes `(File/String/OutputStream, Charset)`.
All Charset-accepting constructors are **3-argument**: `Formatter(X, Charset, Locale)`.

See: https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/util/Formatter.html

**Correct transformations**:
- `Formatter(X, "UTF-8")` → `Formatter(X, StandardCharsets.UTF_8, Locale.getDefault())`
- `Formatter(X, "UTF-8", locale)` → `Formatter(X, StandardCharsets.UTF_8, locale)` (Locale already present)
- `Formatter(X)` → `Formatter(X, Charset.defaultCharset(), Locale.getDefault())`

**Wrong** (produces invalid code — no such constructor):
```java
Formatter s = new Formatter(new File("f"), StandardCharsets.UTF_8);
```

**Correct** (3-arg constructor exists since Java 10):
```java
Formatter s = new Formatter(new File("f"), StandardCharsets.UTF_8, Locale.getDefault());
```

**Implementation**: `FormatterExplicitEncoding.rewrite()` must:
1. Replace `String` encoding with `Charset` via `ASTNodes.replaceAndRemoveNLS()`
2. For 2-arg case: add `Locale.getDefault()` as 3rd argument via `listRewrite.insertLast()`
3. For 1-arg case: add both `Charset` and `Locale.getDefault()`
4. For 3-arg case: only replace the String → Charset (Locale already exists)

**Test expectations** must include `import java.util.Locale;` and `Locale.getDefault()` in Formatter calls.

**Rule**: Always check the actual Java API documentation before assuming a constructor exists.
Do NOT trust comments in the code that claim `Formatter(X, Charset)` is valid.

---

## 10. ⚠️ replaceAndRemoveNLS Conflicts with ListRewrite — Cannot Mix Them

**Issue**: `ASTNodes.replaceAndRemoveNLS()` conflicts with `ListRewrite` operations on the
same parent node. When you need to BOTH replace an existing argument AND insert a new argument,
`replaceAndRemoveNLS` makes `listRewrite.insertLast()` silently fail — regardless of ordering.

See: https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/121

**Symptoms**:
- The `ImportRewrite` still works (imports are added correctly)
- But `ListRewrite.insertLast()` / `ListRewrite.insertAfter()` silently do nothing
- The replacement from `replaceAndRemoveNLS` itself succeeds

**Wrong** (mixing replaceAndRemoveNLS with listRewrite — insertLast silently fails):
```java
// Neither order works:
listRewrite.insertLast(localeNode, group);  // ← SILENTLY FAILS
ASTNodes.replaceAndRemoveNLS(rewrite, nodedata.visited(), charsetNode, group, cuRewrite);
// OR:
ASTNodes.replaceAndRemoveNLS(rewrite, nodedata.visited(), charsetNode, group, cuRewrite);
listRewrite.insertLast(localeNode, group);  // ← ALSO SILENTLY FAILS
```

**Correct** (use listRewrite for BOTH replace and insert):
```java
listRewrite.replace(nodedata.visited(), charsetNode, group);
listRewrite.insertLast(localeNode, group);  // ← Works!
```

**Trade-off**: `listRewrite.replace()` does NOT remove NLS comments (unlike `replaceAndRemoveNLS`).
If the original StringLiteral had a `//$NON-NLS-n$` comment, it may be left behind.
This is acceptable when the alternative is silently losing the Locale argument.

**When replaceAndRemoveNLS IS safe**: When you ONLY need to replace an argument and do NOT
need to insert any additional arguments on the same node. This is the case for all other
helpers (InputStreamReader, OutputStreamWriter, Scanner, etc.) — they only replace.

**Currently affects**: Only `FormatterExplicitEncoding` (2-arg case needs both replace + insert).

---

## 11. ⚠️ Text Block Indentation for Newly-Added Imports in Test Expected Output

**Issue**: The cleanup adds new imports (e.g., `java.nio.charset.Charset`) without the
indentation that existing imports have. In test text blocks, newly-added imports must use
**fewer tabs** than existing content.

**How text block stripping works**:
- Java strips the minimum leading whitespace across all non-blank lines AND the closing `"""`
- If the minimum is 5 tabs (e.g., from a closing `}` or the `"""`), then:
  - Lines at 6 tabs → 1 tab in resulting string
  - Lines at 5 tabs → 0 tabs in resulting string

**Example**: If existing imports are at 6 tabs (→ 1 tab after stripping), newly-added imports
must be at 5 tabs (→ 0 tabs after stripping) to match the cleanup's actual output.

**Wrong** (all imports at same tab level — newly-added imports get unwanted tab):
```
						import java.io.FileNotFoundException;          ← 6 tabs → \t
						import java.nio.charset.Charset;               ← 6 tabs → \t (WRONG)
```

**Correct** (newly-added imports at minimum tab level — no leading tab):
```
						import java.io.FileNotFoundException;          ← 6 tabs → \t
					import java.nio.charset.Charset;                   ← 5 tabs → (none)
```

**Rule**: When writing expected output in text blocks, check the CI "but was:" output to see
exact indentation of newly-added imports. They typically have 0 leading whitespace.

---

## 12. ⚠️ Given Input Must Also Compile When Using FullCompileCheck

**Issue**: `assertRefactoringResultAsExpectedWithFullCompileCheck` checks that the **given**
input compiles BEFORE running the cleanup (line 664 in AbstractEclipseJava). If the given
code references a type (e.g., `UnsupportedEncodingException` in a catch clause) but doesn't
import it, the test fails with a compilation error — not a cleanup error.

**Symptom**: `E1.java has compilation problems: ERROR line N: X cannot be resolved to a type`
before the cleanup even runs.

**Common mistake**: Given code has `catch (FileNotFoundException | UnsupportedEncodingException e)`
but forgets `import java.io.UnsupportedEncodingException;` in the imports section.

**Rule**: Every type referenced in given input code must be properly imported. After editing
given code, verify all types in catch clauses, throws declarations, and method bodies have
corresponding imports.

---

## 13. ⚠️ ASTRewrite Queues Changes — Original AST Is Unchanged Until Apply

**Issue**: `ASTRewrite.remove()` and `ASTRewrite.replace()` do **not** modify the original AST
immediately. They queue edits that are applied later. Code that inspects the original AST after
queueing removals will still see the original (un-removed) nodes.

**Symptom**: `simplifyEmptyTryStatement` checked `tryStatement.catchClauses().isEmpty()` after
`removeExceptionFromTryCatch` had already called `rewrite.remove(catchClause, group)`. The
original AST still had the catch clause, so `isEmpty()` returned `false`, and the try block
was left orphaned (no catch, no finally) — producing invalid Java code.

**Fix**: Track the number of nodes queued for removal and subtract from the original count:
```java
// In removeExceptionFromTryCatch: return count of removed catch clauses
int removedCount = removeExceptionFromTryCatch(...);

// In simplifyEmptyTryStatement: use removedCatchCount instead of checking live AST
int remainingCatchClauses = tryStatement.catchClauses().size() - removedCatchCount;
if (remainingCatchClauses > 0 || tryStatement.getFinally() != null) {
    return; // still has catch clauses or finally — don't simplify
}
```

**Rule**: When writing AST rewrite logic that inspects the tree after queueing removals,
**always** account for the fact that queued removals are not yet visible in the original AST.
Either pass removal counts explicitly or use a different approach to determine remaining nodes.

---

## 14. NLS Comment Removal Must Target the LAST Comment, Not the First

**Issue**: When a line has multiple NLS comments (e.g., `//$NON-NLS-1$ //$NON-NLS-2$`), and
the cleanup replaces a string literal (like the encoding argument `"UTF-8"`) with a non-string
expression (like `StandardCharsets.UTF_8`), the NLS comment for the **replaced** string must be
removed — which is always the **last** NLS comment on the line (since string literals are numbered
left-to-right).

**Example**: Given:
```java
InputStreamReader is2 = new InputStreamReader(new FileInputStream("file2.txt"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
```
After cleanup, `"UTF-8"` is replaced by `E1.UTF_8`. The remaining string `"file2.txt"` keeps
`//$NON-NLS-1$`, and `//$NON-NLS-2$` (for the removed `"UTF-8"`) must be removed.

**Wrong** (removes FIRST NLS comment):
```java
original = NLS_COMMENT.matcher(original).replaceFirst(""); // Removes //$NON-NLS-1$ ❌
```

**Correct** (removes LAST NLS comment using negative lookahead):
```java
private static final Pattern LAST_NLS_COMMENT = Pattern.compile("[ ]*\\/\\/\\$NON-NLS-[0-9]+\\$(?!.*\\/\\/\\$NON-NLS-)");
original = LAST_NLS_COMMENT.matcher(original).replaceFirst(""); // Removes //$NON-NLS-2$ ✅
```

**File**: `AbstractExplicitEncoding.java` — both `replaceArgumentAndRemoveNLS()` and
`replaceTryBodyAndUnwrap()` use `LAST_NLS_COMMENT`.

**Also**: In `replaceTryBodyAndUnwrap()`, the NLS comment removal must only be applied to the
statement that contains the visited node (`if (stmt == statement)`), not to all statements in
the try body.

**Fixed**: 2026-02-22

---

## 14. TriggerPattern DSL: METHOD_DECLARATION Annotation Rewrite and Multiline Replacements

**Issue**: The `.sandbox-hint` DSL could not add annotations to method declarations — it only
supported replacing existing nodes. JUnit 3→5 migration requires adding `@Test`, `@BeforeEach`,
`@AfterEach` to conventionally-named methods that have no annotations.

**Solution (2026-02-24)**:
1. **Natural method-rewrite syntax** (NetBeans-compatible): The replacement is a method
   declaration with the annotation prepended. The engine diffs annotations between source
   and replacement patterns, adding missing ones idempotently via `handleMethodDeclarationRewrite()`.
2. **`methodNameMatches` guard**: Regex-based method name filtering for `METHOD_DECLARATION` patterns.
3. **Static/non-static support**: `isStatic($name)` / `!isStatic($name)` guards distinguish
   static lifecycle methods (e.g., `setUpBeforeClass`) from instance methods (e.g., `setUp`).
4. **`resolveModifiers` fallback**: When binding resolution is unavailable, navigates from
   `SimpleName` to parent `BodyDeclaration` to read modifiers directly from AST.
5. **METHOD_DECLARATION inference fix**: `HintFileParser.inferPatternKind()` correctly
   recognizes `void $name($params$)` as `METHOD_DECLARATION`.
6. **Newline after annotation**: Uses `rewrite.createStringPlaceholder("@Name\n", ...)`
   to ensure the annotation appears on its own line above the method declaration.
7. **Multiline replacements**: Continuation lines after `=>` that don't start with `=>`
   are accumulated into a single replacement text joined with `\n`.

**Key files changed**:
- `BuiltInGuards.java` — added `evaluateMethodNameMatches()`, improved `resolveModifiers()` fallback
- `HintFileFixCore.java` — added `handleMethodDeclarationRewrite()` (natural syntax)
- `HintFileParser.java` — added `looksLikeMethodDeclaration()`, multiline replacement accumulation
- `HintFileStore.java` — registered `junit3-migration.sandbox-hint` as bundled library

**DSL syntax**:
```
// Instance method: !isStatic guard
void $name($params$) :: methodNameMatches($name, "test.*") && !isStatic($name)
=> @org.junit.jupiter.api.Test void $name($params$)
;;

// Static method: isStatic guard
void $name($params$) :: methodNameMatches($name, "setUpBeforeClass") && isStatic($name)
=> @org.junit.jupiter.api.BeforeAll void $name($params$)
;;
```

**⚠️ `addAnnotation` directive was removed** — use natural method-rewrite syntax only.

**Multiline replacement example**:
```
$x.open()
=>
$x.open()
$x.init()
;;
```

---
## 20. ⚠️ Recovered Bindings Break Annotation and Field Type Matching in LambdaASTVisitor

**Issue**: When using the standalone `ASTParser` with `setResolveBindings(true)` and
`setBindingsRecovery(true)`, types not on the classpath (e.g., JUnit's `@Rule`, `ErrorCollector`)
produce **recovered** bindings — non-null `ITypeBinding` objects with `isRecovered() == true`.

The old code checked `binding != null` before using `binding.getQualifiedName()`, assuming
null meant "unresolved". But recovered bindings are non-null, so the fallback to source-level
names (`annotation.getTypeName().getFullyQualifiedName()`) was never reached. The recovered
binding's `getQualifiedName()` may return empty string `""` or an unreliable name.

**Affected code**: `LambdaASTVisitor.java` — all annotation visit/endVisit methods:
- `visit(MarkerAnnotation)`, `endVisit(MarkerAnnotation)`
- `visit(NormalAnnotation)`, `endVisit(NormalAnnotation)`
- `visit(SingleMemberAnnotation)`
- `visit(FieldDeclaration)` — both annotation matching AND field type matching

**Wrong** (trusts any non-null binding):
```java
ITypeBinding binding = annotation.resolveTypeBinding();
String fullyQualifiedName;
if (binding != null) {
    fullyQualifiedName = binding.getQualifiedName(); // may be "" for recovered binding
} else {
    fullyQualifiedName = annotation.getTypeName().getFullyQualifiedName(); // never reached
}
```

**Correct** (checks `isRecovered()` before trusting binding):
```java
String fullyQualifiedName = resolveAnnotationName(annotation);
// where resolveAnnotationName is:
private static String resolveAnnotationName(Annotation annotation) {
    ITypeBinding binding = annotation.resolveTypeBinding();
    if (binding != null && !binding.isRecovered()) {
        String qname = binding.getQualifiedName();
        if (qname != null && !qname.isEmpty()) {
            return qname;
        }
    }
    return annotation.getTypeName().getFullyQualifiedName();
}
```

**For field type matching** in `visit(FieldDeclaration)`, the same principle applies:
```java
// Wrong: isExternalResource may get a recovered binding that walks up to Object
if (isExternalResource(fieldBinding, superclassname)) { ... }
// Correct: skip isExternalResource for recovered bindings, fall through to source-name match
if (fieldBinding != null && !fieldBinding.isRecovered() && isExternalResource(fieldBinding, superclassname)) { ... }
```

**Rule**: When checking `ITypeBinding` from `resolveTypeBinding()` or `resolveBinding().getType()`,
ALWAYS check `isRecovered()` before trusting `getQualifiedName()` or walking the superclass chain.
Recovered bindings are non-null but unreliable — fall back to source-level names instead.

**Fixed**: 2026-02-23

---

## 17. JUnit 3 Migration Hint Rules Must Check Superclass

**Issue**: The `junit3-migration.sandbox-hint` rules originally matched ANY class with methods
named `test*`, `setUp`, `tearDown`, etc. — regardless of whether the class extends
`junit.framework.TestCase`. This produces false positives on regular classes.

**Wrong** (matches any class with test-like method names):
```
void $name($params$) :: methodNameMatches($name, "test.*") && !isStatic($name)
=> @org.junit.jupiter.api.Test void $name($params$)
```

**Correct** (only matches classes extending TestCase):
```
void $name($params$) :: methodNameMatches($name, "test.*") && !isStatic($name) && enclosingClassExtends("junit.framework.TestCase")
=> @org.junit.jupiter.api.Test void $name($params$)
```

**Guard**: `enclosingClassExtends("fqn")` — walks the superclass chain (with bindings) or
falls back to textual `extends` clause comparison (without bindings).

**Location**: `sandbox_common_core/.../BuiltInGuards.java` — `evaluateEnclosingClassExtends()`

**Rule**: Any migration hint that targets a framework-specific class pattern (JUnit 3, etc.)
MUST include a type hierarchy guard to avoid false positives on unrelated classes.

**Fixed**: 2026-02-24

---

## 12. CRLF Line Ending Issues in Test Comparisons

**Issue**: When Copilot-generated code introduces `\r\n` line endings (e.g., in `UseExplicitEncodingFixCore.java`),
test comparisons fail because `ArrayList.remove(Object)` in `assertEqualStringsIgnoreOrder` uses exact `String.equals()`.

**The comparison chain**:
```
assertRefactoringResultAsExpected()
  → cu.getBuffer().getContents()    // actual source (may have \r\n)
  → assertEqualStringsIgnoreOrder() // exact String.equals() comparison
    → expectedList.remove(actual)   // fails if \r\n vs \n mismatch
```

**Fix (two-pronged)**:
1. **Option A**: Always convert source files to LF-only line endings (remove CRLF from committed files)
2. **Option B**: Normalize line endings in the test framework (`AbstractEclipseJava.java`) at:
   - `assertRefactoringResultAsExpected()`: normalize `cu.getBuffer().getContents()`
   - `assertEqualStringsIgnoreOrder()`: normalize both `actuals` and `expecteds` arrays

**Location**: `sandbox_test_commons/.../rules/AbstractEclipseJava.java`
- Uses `normalizeLineEndings()` private helper: `s.replace("\r\n", "\n").replace("\r", "\n")`

**Rule**: When fixing CRLF issues, apply BOTH options — fix the source file AND protect the test framework.

**Fixed**: 2026-02-22

---

## 21. Hint File (.sandbox-hint) Placement Rules

**Issue**: `.sandbox-hint` files were duplicated across `sandbox_common`, `sandbox_common_core`, and domain plugins, causing stale copies and confusion about which was the source of truth.

**Two loading mechanisms exist:**

| Mechanism | Where files live | How loaded | Purpose |
|---|---|---|---|
| **Bundled libraries** | `sandbox_common_core/src/main/resources/.../triggerpattern/internal/` | `HintFileStore.BUNDLED_LIBRARIES` array | Generic patterns available everywhere (CLI, tests, non-Eclipse) |
| **Extension-point** | Domain plugin `src/.../hints/` (e.g., `sandbox_junit_cleanup`) | `plugin.xml` via `org.sandbox.jdt.triggerpattern.hints` | Domain-specific patterns, loaded in Eclipse runtime |

**Rules:**
1. **Generic libraries** (collections, modernize-java9, modernize-java11, performance) belong in `sandbox_common_core` bundled resources
2. **Domain-specific libraries** (junit5, annotations5, assume5, junit3-migration, encoding) belong in their respective plugin, registered via extension point in `plugin.xml`
3. **NEVER duplicate** hint files between `sandbox_common` and `sandbox_common_core` — `sandbox_common_core` is the source of truth for bundled libraries
4. `sandbox_common/src/` should contain **NO** `.sandbox-hint` files — those all moved to `sandbox_common_core` during the extraction (issue #730)
5. When adding a new JUnit-related hint file, add it to `sandbox_junit_cleanup`, NOT to `sandbox_common_core`

**Fixed**: 2026-02-24 — Deleted 4 stale duplicates from `sandbox_common/src/`, moved `junit3-migration.sandbox-hint` from `sandbox_common_core` bundled libraries to `sandbox_junit_cleanup` extension point.

---

## 22. JUnit Cleanup Plugin Architecture — JUNIT_CLEANUP_4_SUITE vs JUNIT_CLEANUP_4_RUNWITH

**Issue**: `JUNIT_CLEANUP_4_SUITE` constant exists in `MYCleanUpConstants` but is **NOT mapped** in
`JUnitCleanUpCore.computeFixSet()`. The Suite migration functionality is handled by
`RunWithJUnitPlugin` which is controlled by `JUNIT_CLEANUP_4_RUNWITH`.

**Key Facts**:
- `JUNIT_CLEANUP_4_SUITE` → not mapped to any enum value → enabling/disabling it has NO effect
- `JUNIT_CLEANUP_4_RUNWITH` → maps to `JUnitCleanUpFixCore.RUNWITH` → controls `RunWithJUnitPlugin`
- `RunWithJUnitPlugin` handles both `@RunWith(Suite.class)` and `@Suite.SuiteClasses` migrations
- There are also `RUNWITH_ENCLOSED`, `RUNWITH_THEORIES`, `RUNWITH_CATEGORIES` enum values that have
  no individual toggle — they're always active when `JUNIT_CLEANUP` is enabled

**Learned**: 2026-02-24

---

## 23. ThrowingRunnableJUnitPlugin Already Handles ParameterizedType

**Issue**: Tests `migrates_generic_type_parameter` and `migrates_complete_eclipse_platform_example`
were `@Disabled` with message "Currently fails due to missing support for type parameter references".
However, the plugin code ALREADY implements full ParameterizedType support:
- `visit(ParameterizedType node)` in the ASTVisitor
- `processParameterizedType()` for rewriting
- `createExecutableType()` for recursive type argument transformation
- `containsThrowingRunnable()` for detecting ThrowingRunnable in type arguments

**Rule**: Before implementing a feature, check if it's already been implemented but the tests weren't
re-enabled. The `@Disabled` annotation message may be outdated.

**Learned**: 2026-02-24

---

## 24. Generic Type Parameter Method Call Migration (.run() → .execute())

**Issue**: When `ThrowingRunnable` is used as a generic type argument (e.g., `AtomicReference<ThrowingRunnable>`),
calling `.run()` on the result of a generic method (e.g., `ref.get().run()`) was NOT being migrated to `.execute()`.
The type replacement worked (→ `AtomicReference<Executable>`), but the method call stayed as `.run()`.

**Root Cause**: Eclipse JDT's binding resolution for method calls through generic return types
may not resolve `methodBinding.getDeclaringClass()` or `expression.resolveTypeBinding()` to
`ThrowingRunnable` in a straightforward way. The simple `getQualifiedName()` check was insufficient.

**Fix**: Created a comprehensive `isThrowingRunnableRunCall(MethodInvocation)` method with 4 strategies:
1. Check `methodBinding.getDeclaringClass()` via `isThrowingRunnableType()` (handles direct, erasure, type vars, captures)
2. Check `methodBinding.getMethodDeclaration().getDeclaringClass()` (unparameterized declaration)
3. Check `receiverMethodBinding.getReturnType()` when receiver is a method call (e.g., `ref.get()`)
4. Check receiver's receiver type arguments (e.g., check if `ref` in `ref.get().run()` has `ThrowingRunnable` type arg)

Also enhanced `isThrowingRunnableType()` to check:
- Direct qualified name match
- Erasure
- Type variable bounds
- Capture bindings (wildcard bounds)
- Implemented interfaces
- Superclass hierarchy

**File**: `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/ThrowingRunnableJUnitPlugin.java`

**Learned**: 2026-02-24

---