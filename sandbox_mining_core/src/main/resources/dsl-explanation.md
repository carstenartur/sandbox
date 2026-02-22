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
| `hasModifier($var, "modifier")` | True if the element has the specified modifier (e.g., "public", "private") |
| `mode("modeName")` | True if the `sandbox.cleanup.mode` compiler option matches the given mode (case-insensitive). Supported modes: `KEEP_BEHAVIOR`, `ENFORCE_UTF8`, `ENFORCE_UTF8_AGGREGATE`. |
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

### Unsupported Features / Limitations

The following are **NOT supported** by the DSL. If a transformation requires any of these,
mark it as `RED` / not implementable:

1. **Multi-line statement blocks in replacements**: Replacements must be single expressions or single statements.
   Do NOT use `if`/`else` blocks, `return` statements, or `{}` blocks as replacements.
   ```
   // ❌ NOT SUPPORTED — multi-line replacement block
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

4. **Complex expression composition in replacements**: Wrapping a matched variable in a new
   expression (e.g., `$x` → `List.of($x)`) has limited support. Simple wrapping works, but
   combining it with arity changes may not.

## Invalid Constructs — Do NOT Generate

The following constructs are **NOT valid** in the TriggerPattern DSL. If you see these
in generated output, they are hallucinations and must be rejected:

| Invalid Construct | Why It's Wrong |
|-------------------|----------------|
| `<trigger>...</trigger>` | XML syntax — the DSL uses plain text, not XML |
| `<import>...</import>` | Imports are automatically inferred from FQNs — no explicit import directive exists |
| `isType($var)` | Not a real guard — use `instanceof($var, "Type")` instead |
| `isNull($var)` | Not a real guard — use `isNullLiteral($var)` to check for `null` literals |
| `<pattern>...</pattern>` | XML wrapper — rules are written as plain text lines |
| `@Override` in replacements | Annotations on method declarations are not supported as replacements |
| Multi-statement replacements | Replacements must be single expressions. No `if`/`else`, `return`, or `{}` blocks. |

## JSON Output Rules

When including DSL rules in JSON fields like `dslRule` or `dslRuleAfterChange`, ensure all newlines are properly escaped as `\n` within the JSON string. Do NOT use literal line breaks inside JSON string values.
