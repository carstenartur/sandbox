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

## 17. Java 8 OutputStreamWriter Cleanup Does NOT Apply

**Issue**: The `OutputStreamWriter` encoding cleanup does not transform code when targeting Java 8.
The test expected output incorrectly showed the transformation being applied (adding `Charset.defaultCharset()`
and `StandardCharsets.UTF_8`), but the actual cleanup produces no change.

**Fix**: Changed OUTPUTSTREAMWRITER expected output in `Java8/ExplicitEncodingPatterns.java` to match
the given input (no transformation).

**Rule**: For Java 8, `OutputStreamWriter` constructors are not rewritten by the encoding cleanup.
The expected output should be identical to the given input.

---

## 18. Java 8 INPUTSTREAMREADER Import Order After Cleanup

**Issue**: When the INPUTSTREAMREADER cleanup adds `java.nio.charset.Charset` and
`java.nio.charset.StandardCharsets` imports, they are inserted BEFORE `java.io.FileNotFoundException`
(which was the last existing import), not after it.

**Wrong expected order**:
```java
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
```

**Correct expected order** (what cleanup actually produces):
```java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.FileNotFoundException;
```

**Rule**: New `java.nio.charset.*` imports are inserted before `java.io.FileNotFoundException`
when `FileNotFoundException` is the last import in the original import list.

**Fixed**: 2026-02-22

---
