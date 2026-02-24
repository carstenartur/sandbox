# TriggerPattern DSL (.sandbox-hint Format)

The TriggerPattern DSL is a domain-specific language for describing Java code transformations.
Each `.sandbox-hint` file contains one or more transformation rules.

## Structure

A hint file has optional metadata directives followed by transformation rules.

### Metadata Directives

```
<!id: unique-identifier>
<!description: Human-readable description of the transformation>
<!severity: INFO|WARNING|ERROR|HINT>
<!minJavaVersion: 11>
<!tags: tag1, tag2, tag3>
<!include: other.hint.id>
<!caseInsensitive>
```

**IMPORTANT**: Every metadata directive MUST be enclosed with angle brackets and have a closing `>`:
- ✅ Correct: `<!description: Replace deprecated API calls>`
- ❌ Wrong: `<!description: Replace deprecated API calls` (missing closing `>`)

**Multi-line metadata**: Long descriptions may span multiple lines. The parser accumulates lines
starting from `<!` until a closing `>` is found:
```
<!description: Add guard for unhandled or disabled Eclipse commands after declaration.
Prevents showing key bindings for commands that are not handled or enabled.>
```

**NetBeans compatibility**: Metadata also supports the NetBeans `<!key="value">` format
(e.g., `<!description="NetBeans style metadata">`). Surrounding quotes are stripped automatically.

### Comments

The parser supports two comment styles:

```
// This is a line comment (everything after // is ignored)

/* This is a block comment.
   It can span multiple lines. */
```

Comments are stripped before any rule parsing occurs.

### Custom Code Blocks (NetBeans Compatibility)

NetBeans `.hint` files may contain `<? ?>` custom Java code blocks. These are **gracefully
skipped** by the parser (with a log message). They cannot be executed in the Eclipse JDT
environment:

```
<? import java.util.*; ?>
```

### Foreach Expansion

The `<!foreach>` directive allows a single rule template to be expanded into multiple rules,
one for each key-value pair. This is especially useful for encoding-related transformations.

**Syntax:**
```
<!foreach VARNAME: "key1" -> val1, "key2" -> val2, ...>
```

**Usage in rules:** `${VARNAME}` expands to the key, `${VARNAME_CONSTANT}` expands to the value.

**Example (encoding rules):**
```
<!foreach CHARSET: "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1>

$s.getBytes("${CHARSET}") :: sourceVersionGE(7)
=>
$s.getBytes(java.nio.charset.StandardCharsets.${CHARSET_CONSTANT})
;;
```

This expands into two rules: one for `"UTF-8"` → `UTF_8` and one for `"ISO-8859-1"` → `ISO_8859_1`.

### Transformation Rules

Each rule consists of a **pattern** (what to match) and a **rewrite** (what to replace it with).

#### 1. Simple Rule

```
pattern_expression
=>
replacement_expression
;;
```

**CRITICAL SYNTAX RULES**:
1. The `=>` arrow is MANDATORY between the pattern and replacement. Never omit it.
2. Every rule MUST end with `;;` on its own line.
3. Each rule has exactly this structure — pattern, then `=>`, then replacement, then `;;`.
4. Do NOT place the pattern and replacement on the same line without `=>`.
5. Complex expressions (method chains, constructors) are valid as patterns — just ensure `=>` separates pattern from replacement.

Example of a valid complex rule:

```
new java.io.File($path).toString()
=>
java.nio.file.Path.of($path).toString()
;;
```

#### 2. Guarded Rule

Rules can have guards (conditions) that must be true for the rule to apply.
Multiple guards are combined with AND using `&&`.

```
pattern_expression :: guard_condition
=>
replacement_expression
;;
```

**Multi-line guard continuation**: Guards can span multiple lines. A line starting with
`::` continues the guard from the previous line, combining them with AND:

```
pattern_expression :: guard1
                   :: guard2
                   :: guard3
=>
replacement_expression
;;
```

This is equivalent to `pattern_expression :: guard1 && guard2 && guard3`.

#### 3. Multi-Rewrite Rule (Ordered Alternatives)

Try replacements in order. The first one with a passing guard (or no guard) is used.

```
pattern_expression
=> replacement_1 :: guard_1
=> replacement_2 :: guard_2
=> replacement_default :: otherwise
;;
```

#### 4. Hint-Only Rule (No Quick Fix)

Just shows a warning message without offering an automated replacement.

```
"Warning message to display":
pattern_expression :: guard_condition
;;
```

### Variables and Naming

- `$identifier`: Matches any single expression (e.g., variable, method call).
- `$type`: Matches a type reference.
- `$args$`: Matches zero or more arguments (varargs/list placeholder). Used for method calls with variable argument counts (e.g., `java.util.Arrays.asList($args$)`).
- **Patterns MUST use fully qualified names (FQNs)** for types and methods (e.g., `java.nio.charset.Charset.forName()`).
  - Simple name patterns (e.g., `Charset.forName()`) will **NOT** match and should not be used.
  - FQN patterns can match both FQN usage and imported simple names in source code.
  - When the replacement is applied, imports are automatically managed (added/removed as needed).

### Available Guards

You can use these functions in `:: guard` expressions:

| Guard | Description |
|-------|-------------|
| `instanceof($var, "Type")` | True if `$var` is of the given type (e.g., `java.lang.String`). Supports array types (e.g., `"Type[]"`). |
| `isStatic($var)` | True if the matched method/field is static |
| `isFinal($var)` | True if the matched variable is final |
| `contains("text")` | True if the enclosing method contains the text pattern |
| `notContains("text")` | True if the enclosing method does **NOT** contain the text |
| `sourceVersionGE(11)` | True if source level is Java 11 or higher |
| `sourceVersionLE(11)` | True if source level is Java 11 or lower |
| `sourceVersionBetween(11, 17)` | True if source level is between Java 11 and 17 |
| `hasAnnotation($var, "Type")`| True if the element has the specified annotation |
| `isDeprecated($var)` | True if the element is marked as deprecated |
| `hasNoSideEffect($var)` | True if the expression has no side effects |
| `referencedIn($var, $expr)` | True if `$var`'s identifier appears in `$expr`'s AST subtree. Both arguments are placeholder names. |
| `isLiteral($var)` | True if the expression is a literal value |
| `isNullLiteral($var)` | True if the expression is a `null` literal |
| `isNullable($var)` | True if the expression may be null (1-arg form). With `isNullable($var, minScore)`, returns true if the nullability score (0-10) meets the threshold. |
| `isNonNull($var)` | True if the expression is guaranteed non-null |
| `matchesAny($var, "pattern1", "pattern2")` | True if `$var` matches any of the patterns |
| `matchesNone($var, "pattern1", "pattern2")` | True if `$var` matches none of the patterns |
| `isCharsetString($var)` | True if `$var` is a charset name string |
| `isSingleCharacter($var)` | True if `$var` is a single-character string |
| `isRegexp($var)` | True if `$var` is a regular expression pattern |
| `elementKindMatches($var, "kind")` | True if the element kind matches (e.g., "METHOD", "FIELD") |
| `isInTryWithResourceBlock($var)` | True if the code is inside a try-with-resources block |
| `isPassedToMethod($var)` | True if the node is passed as an argument to a method call or constructor. Takes 0 or 1 args (placeholder name). |
| `inSerializableClass()` | True if the code is in a serializable class |
| `containsAnnotation("Type")` | True if the enclosing element contains the annotation |
| `parentMatches("pattern")` | True if the parent AST node matches the pattern |
| `inClass("ClassName")` | True if the code is inside the specified class |
| `inPackage("package.name")` | True if the code is inside the specified package |
| `hasModifier($var, "modifier")` | True if the element has the specified modifier (e.g., "PUBLIC", "STATIC", "FINAL") |
| `isStatic($var)` | True if the element has the `static` modifier. Shorthand for `hasModifier($var, "STATIC")`. |
| `isFinal($var)` | True if the element has the `final` modifier. Shorthand for `hasModifier($var, "FINAL")`. |
| `mode("modeName")` | True if the `sandbox.cleanup.mode` compiler option matches the given mode (case-insensitive). Supported modes: `KEEP_BEHAVIOR`, `ENFORCE_UTF8`, `ENFORCE_UTF8_AGGREGATE`. |
| `methodNameMatches($var, "regex")` | True if the method name bound to `$var` matches the given regex pattern. Used with METHOD_DECLARATION patterns (e.g., `void $name($params$)`). |
| `enclosingClassExtends("fqn")` | True if the enclosing class extends the given type (directly or transitively). Essential for migration rules targeting specific base classes (e.g., `"junit.framework.TestCase"`). Falls back to textual `extends` clause comparison when bindings are unavailable. |
| `otherwise` | Always true (used as default fallback in multi-rewrite rules) |

### Common Mistakes

**❌ Using simple names in patterns:**
```
// WRONG - will not match!
Charset.forName("UTF-8")
=> StandardCharsets.UTF_8
;;
```

**✅ Always use fully qualified names:**
```
// CORRECT - matches both FQN and imported usage
java.nio.charset.Charset.forName("UTF-8")
=> java.nio.charset.StandardCharsets.UTF_8
;;
```

This FQN pattern will match both:
- `java.nio.charset.Charset.forName("UTF-8")` (FQN in source)
- `Charset.forName("UTF-8")` (with `import java.nio.charset.Charset;`)

### Examples

**Simple replacement:**
```
<!id: use-standard-charsets>
<!description: Replace Charset.forName("UTF-8") with StandardCharsets.UTF_8>
<!severity: WARNING>

java.nio.charset.Charset.forName("UTF-8")
=>
java.nio.charset.StandardCharsets.UTF_8
;;
```

**Guarded rule with version check:**
```
new java.io.FileReader($path) :: sourceVersionGE(11)
=>
new java.io.FileReader($path, java.nio.charset.StandardCharsets.UTF_8)
;;
```

**Guard with negation (`notContains`):**
```
// Only warn if init() is NOT called — hint-only rule (no replacement)
"Call init() after construction":
$obj = new MyClass() :: notContains("$obj.init()")
;;
```

**Multi-rewrite (Conditional replacement):**
```
java.util.Arrays.asList($args)
=> java.util.List.of($args) :: sourceVersionGE(9)
=> java.util.Collections.unmodifiableList(java.util.Arrays.asList($args)) :: otherwise
;;
```

**Hint-only rule (warning without quick fix):**
```
"Consider using InputStream.transferTo() for stream copying (Java 9+)":
$in.read($buf) :: sourceVersionGE(9) && notContains("transferTo")
;;
```

### Annotation Rules

Annotation patterns start with `@` and can include fully qualified names and attributes.

**Simple annotation replacement:**
```
@org.junit.Before
=> @org.junit.jupiter.api.BeforeEach
;;
```

**Annotation with attributes:**
```
@java.lang.Deprecated :: sourceVersionGE(9)
=> @java.lang.Deprecated(forRemoval = true)
;;
```

**Note:** Annotation replacement is a single expression — the `@annotation(attributes)` on one line.
Multi-statement transformations (adding `if` blocks, `return` statements) are NOT supported.

### Adding Annotations to Methods

When the source and replacement are both method declarations, the engine diffs
annotations between them and adds the missing ones to the matched method.
This follows the **NetBeans-compatible** "source → target" pattern syntax.

**Syntax:**
```
void $name($params$) :: methodNameMatches($name, "regex")
=> @fully.qualified.AnnotationName void $name($params$)
;;
```

**Key features:**
- **Natural syntax**: The replacement IS the target method declaration with the annotation.
- **Idempotent**: If the annotation is already present on the method, no change is made.
- **Import management**: The FQN is added as an import; the simple name is used for the annotation.
- **Static/non-static guards**: Use `isStatic($name)` / `!isStatic($name)` to distinguish method types.
- **Pattern-based**: Use `methodNameMatches($name, "regex")` to filter methods by name.

**Example — JUnit 3 to JUnit 5 migration (instance methods):**
```
// Add @Test to non-static methods named test*
void $name($params$) :: methodNameMatches($name, "test.*") && !isStatic($name)
=> @org.junit.jupiter.api.Test void $name($params$)
;;

// Add @BeforeEach to non-static setUp() methods
void $name($params$) :: methodNameMatches($name, "setUp") && !isStatic($name)
=> @org.junit.jupiter.api.BeforeEach void $name($params$)
;;
```

**Example — JUnit 3 to JUnit 5 migration (static lifecycle methods):**
```
// Add @BeforeAll to static setUpBeforeClass() methods
void $name($params$) :: methodNameMatches($name, "setUpBeforeClass") && isStatic($name)
=> @org.junit.jupiter.api.BeforeAll void $name($params$)
;;
```

### Multiline Replacements

Continuation lines after `=>` that do not start with `=>` are accumulated into
a single multiline replacement text, joined with newlines.

```
$x.open()
=>
$x.open()
$x.init()
;;
```

The replacement text becomes `$x.open()\n$x.init()`.

**Note:** Multi-rewrite rules (multiple `=>` alternatives) are NOT affected — each
`=>` starts a new alternative as before.

### Unsupported Features / Limitations

The following are not yet supported by the current DSL. If a transformation requires any of these,
mark it as `RED` / not yet implementable — these limitations may be addressed in future DSL versions:

1. **Complex multi-line statement blocks in replacements**: While multiline replacements are now
   supported for simple continuation lines, complex control flow (`if`/`else`, `return`, `{}` blocks)
   in replacements is NOT supported at the expression rewrite level.
   ```
   // ❌ NOT SUPPORTED — control flow in replacement
   $pattern
   =>
   if (!($cmd.isHandled())) {
       return;
   }
   ;;
   ```

2. **Bitwise/shift operators in patterns or replacements**: Bitwise operators like `|`, `&`, `^`, `~`, `>>`, `<<`
   in patterns or replacements are NOT supported. (Arithmetic operators like `+` and `-` are supported.)
   ```
   // ❌ NOT SUPPORTED — bitwise OR in replacement
   $mgr.handle($status, StatusManager.SHOW)
   => $mgr.handle($status, StatusManager.SHOW | StatusManager.LOG)
   ;;
   ```

3. **Inserting new statements or control flow**: The DSL performs **pattern matching and replacement**,
   not arbitrary code insertion. Adding guard clauses, new method calls after a statement, or
   wrapping code in try/catch blocks is not supported.
   **Note:** Detection of structural context via guards (e.g., `isInTryWithResourceBlock()`) *is* supported — only structural rewrites that insert new statements are not yet possible.

4. **Complex expression composition in replacements**: Wrapping a matched variable in a new
   expression (e.g., `$x` → `List.of($x)`) has limited support. Simple wrapping works, but
   combining it with arity changes may not.

## Bidirectional Transformations

Some code transformations are valid in **both directions**. For example:
- `obj.toString()` → `String.valueOf(obj)` is safer when `obj` may be null
- `String.valueOf(obj)` → `obj.toString()` is more direct when `obj` is guaranteed non-null

Use nullability guards (`isNullable`, `isNonNull`) to decide the safe direction:

```
// Safe: String.valueOf handles null
$obj.toString() :: isNullable($obj)
=> java.lang.String.valueOf($obj)
;;

// Direct: when non-null is guaranteed
java.lang.String.valueOf($obj) :: isNonNull($obj)
=> $obj.toString()
;;
```

When proposing a transformation, consider whether the reverse direction is also useful.
If both directions are valid, propose both as separate rules with appropriate guards.

## Source Version Guidance

**Minimum version is Java 8.** We do not support anything older than Java 8. Do not use `sourceVersionGE()` with values below 8.

**The guard version must match the Java version that INTRODUCED the replacement API**, not the project's current Java version. If the replacement API has existed since Java 8 or earlier, **omit the `sourceVersionGE()` guard entirely** — the rule applies unconditionally.

**Examples:**
- `Collection.isEmpty()` exists since Java 2 → **no guard needed**
- `String.valueOf()` exists since Java 1.0 → **no guard needed**
- `Map.entry()` introduced in Java 9 → `sourceVersionGE(9)`
- `List.of()` introduced in Java 9 → `sourceVersionGE(9)`
- `String.isBlank()` introduced in Java 11 → `sourceVersionGE(11)`

| API / Feature | Introduced | Guard |
|---|---|---|
| `Collection.isEmpty()`, `String.valueOf()`, `StringBuilder` | Java ≤ 7 | **(none — omit guard)** |
| `java.util.Objects.requireNonNull()` | 7 → ≤8 baseline | **(none — omit guard)** |
| `java.nio.charset.StandardCharsets` | 7 → ≤8 baseline | **(none — omit guard)** |
| `java.util.Optional` | 8 | **(none — omit guard, 8 is baseline)** |
| `java.util.stream.Stream` | 8 | **(none — omit guard, 8 is baseline)** |
| `java.util.List.of()`, `Set.of()`, `Map.of()` | 9 | `sourceVersionGE(9)` |
| `Map.entry()` | 9 | `sourceVersionGE(9)` |
| `InputStream.transferTo()` | 9 | `sourceVersionGE(9)` |
| `java.lang.String.isBlank()`, `.strip()` | 11 | `sourceVersionGE(11)` |
| `java.nio.file.Path.of()` | 11 | `sourceVersionGE(11)` |
| `java.lang.String.formatted()` | 15 | `sourceVersionGE(15)` |
| Pattern matching `instanceof` | 16 | `sourceVersionGE(16)` |
| Record classes | 16 | `sourceVersionGE(16)` |
| Sealed classes | 17 | `sourceVersionGE(17)` |
| `java.util.SequencedCollection` | 21 | `sourceVersionGE(21)` |

**Rule:** If the replacement API was introduced in Java 8 or earlier, do NOT add any `sourceVersionGE()` guard. Only add the guard when the replacement uses APIs introduced in Java 9 or later.

**Do NOT default to `sourceVersionGE(11)` for everything.** Check the actual API introduction version.

## Invalid Constructs — Do NOT Generate

The following constructs are **NOT valid** in the TriggerPattern DSL. If you see these
in generated output, they are hallucinations and must be rejected:

| Invalid Construct | Why It's Wrong | What To Use Instead |
|-------------------|----------------|---------------------|
| `<trigger>...</trigger>` | XML syntax — the DSL uses plain text, not XML | Write the pattern directly as plain text |
| `<import>...</import>` | Imports are automatically inferred from FQNs — no explicit import directive exists | Use fully qualified names; imports are managed automatically |
| `<pattern>...</pattern>` | XML wrapper — rules are written as plain text lines | Write the pattern directly as plain text |
| `isType($var)` | Not a real guard — does not exist | `instanceof($var, "Type")` |
| `isNull($var)` | Not a real guard — does not exist | `isNullLiteral($var)` to check for `null` literals |
| `hasType($var, "Type")` | Not a real guard — does not exist | `instanceof($var, "Type")` |
| `isInstanceOf($var, "Type")` | Not a real guard — wrong name | `instanceof($var, "Type")` |
| `@Override` in replacements | For expression replacements, annotations are not supported as part of the replacement text | For METHOD_DECLARATION patterns, use natural syntax: `=> @FQN void $name($params$)` |
| Multi-statement replacements | Complex control flow (`if`/`else`, `return`, `{}` blocks) is not supported | Simple multiline continuations ARE supported (lines after `=>` are joined). For complex logic, use programmatic cleanups. |
| `addAnnotation @FQN` | Removed — this directive syntax is no longer supported | Use natural method-rewrite syntax: `=> @FQN void $name($params$)` |
| `sourceVersionGE(7)` or lower (incl. `sourceVersionGE(6)`) | Java 7 and below are not supported; 8 is the baseline | Omit the guard entirely (applies unconditionally) |
| `sourceVersionGE(8)` | Java 8 is the baseline — guard is always true and therefore useless | Omit the guard entirely (applies unconditionally) |
| Simple names in patterns | `Charset.forName()` will not match | Use FQN: `java.nio.charset.Charset.forName()` |
| Bitwise operators (`\|`, `&`, `^`, `>>`, `<<`) | Not supported in patterns or replacements | Mark as RED / not implementable |

**CRITICAL**: Do NOT invent guard function names. The **complete** list of valid guards is in
the "Available Guards" table above. If a guard is not in that table, it does not exist.
Using a non-existent guard will cause a parse error.

## JSON Output Rules

When including DSL rules in JSON fields like `dslRule` or `dslRuleAfterChange`, ensure all newlines are properly escaped as `\n` within the JSON string. Do NOT use literal line breaks inside JSON string values.
