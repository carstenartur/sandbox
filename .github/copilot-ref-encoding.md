# Encoding Cleanup Plugin Reference

> **Read this when**: Working on `sandbox_encoding_quickfix` or `sandbox_encoding_quickfix_test`.

## Purpose

Replaces platform-dependent encoding with explicit charset usage:
- `FileReader`/`FileWriter` → explicit charset constructors
- `Files.readAllLines()`, `Files.readString()`, etc. → charset parameter
- `InputStreamReader`/`OutputStreamWriter` → `StandardCharsets.UTF_8`
- `Scanner`, `Formatter`, `URLDecoder`, `URLEncoder` → charset overloads
- Supports Java 11+ (Java 7 no longer supported)

## Three Cleanup Modes

Controlled by `MYCleanUpConstants`:

| Mode | Constant | Behavior |
|------|----------|----------|
| KEEP_BEHAVIOR | `EXPLICITENCODING_KEEP_BEHAVIOR` | Replace with `Charset.defaultCharset()` — preserves runtime behavior |
| ENFORCE_UTF8 | `EXPLICITENCODING_INSERT_UTF8` | Replace with `StandardCharsets.UTF_8` — forces UTF-8 |
| AGGREGATE_TO_UTF8 | `EXPLICITENCODING_AGGREGATE_UTF8` | Like ENFORCE but extracts `private static final Charset UTF_8` field |

Test patterns are separated by mode:
- `ExplicitEncodingPatternsKeepBehavior` → KEEP_BEHAVIOR
- `ExplicitEncodingPatternsPreferUTF8` → ENFORCE_UTF8
- `ExplicitEncodingPatternsAggregateUTF8` → AGGREGATE_TO_UTF8

## Helper Classes

Located in `sandbox_encoding_quickfix/src/.../helper/`. Each helper handles one API:

Every helper needs:
1. `find()` — discover AST nodes to transform
2. `rewrite()` — apply transformation using `ASTNodes.replaceAndRemoveNLS()`
3. `getPreview()` — generate preview text
4. Proper imports: `CoreException`, `JavaManipulationPlugin`, `ASTNodes`
5. Call `removeUnsupportedEncodingException()` if original throws it

## Key Rules

### NLS Comment Removal
When replacing `StringLiteral` with non-string expression, remove `//$NON-NLS-n$`:
```java
// CORRECT:
ASTNodes.replaceAndRemoveNLS(rewrite, nodedata.visited(), charsetNode, group, cuRewrite);
// WRONG:
listRewrite.replace(nodedata.visited(), charsetNode, group); // leaves stale NLS
```

### NLS: Remove LAST Comment, Not First
When a line has multiple NLS comments, remove the **last** one (string literals numbered left-to-right).

### replaceAndRemoveNLS + ListRewrite Conflict
Cannot mix `replaceAndRemoveNLS()` with `ListRewrite` on same parent. Use `listRewrite.replace()` + `listRewrite.insertLast()` together instead. Affects: `FormatterExplicitEncoding` (2-arg case).

### Formatter Constructors Need Locale
`java.util.Formatter` has NO 2-arg `(X, Charset)` constructor. All are 3-arg: `Formatter(X, Charset, Locale)`.
```java
// WRONG: no such constructor
new Formatter(file, StandardCharsets.UTF_8);
// CORRECT:
new Formatter(file, StandardCharsets.UTF_8, Locale.getDefault());
```

## Test Patterns

Test enums MUST have:
```java
String given;
String expected;
boolean skipCompileCheck;
// Two constructors: 2-arg (default skip=true) and 3-arg
```

### Import Order in Expected Output
**NEVER guess import ordering.** Eclipse's ImportRewrite behaves differently depending on whether imports are only added vs added+removed. ALWAYS check CI logs.

### Text Block Indentation
Newly-added imports in test text blocks need **fewer tabs** than existing content (to match 0 leading whitespace in cleanup output).