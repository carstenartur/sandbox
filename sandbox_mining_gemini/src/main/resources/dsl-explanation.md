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
<!min-java-version: 11>
<!tags: tag1, tag2, tag3>
```

### Transformation Rules

Each rule consists of a **pattern** (what to match) and a **rewrite** (what to replace it with).

#### 1. Simple Rule

```
pattern_expression
=>
replacement_expression
;;
```

#### 2. Guarded Rule

Rules can have guards (conditions) that must be true for the rule to apply.
Multiple guards are combined with AND.

```
pattern_expression :: guard_condition
=>
replacement_expression
;;
```

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

### Variables

- `$identifier`: Matches any expression (e.g., variable, method call).
- `$type`: Matches a type reference.
- Fully qualified names (e.g., `java.util.List`) are **automatically imported** and shortened.

### Available Guards

You can use these functions in `:: guard` expressions:

| Guard | Description |
|-------|-------------|
| `instanceof($var, "Type")` | True if `$var` is of the given type (e.g., `java.lang.String`) |
| `isStatic($var)` | True if the matched method/field is static |
| `isFinal($var)` | True if the matched variable is final |
| `isNull($var)` | True if the matched expression is `null` |
| `contains("text")` | True if the enclosing method contains the text pattern |
| `notContains("text")` | True if the enclosing method does **NOT** contain the text |
| `sourceVersionGE(11)` | True if source level is Java 11 or higher |
| `sourceVersionLE(11)` | True if source level is Java 11 or lower |
| `sourceVersionBetween(11, 17)` | True if source level is between Java 11 and 17 |
| `hasAnnotation($var, "Type")`| True if the element has the specified annotation |
| `isDeprecated($var)` | True if the element is marked as deprecated |
| `hasNoSideEffect($var)` | True if the expression has no side effects |
| `isLiteral($var)` | True if the expression is a literal value |
| `isNullLiteral($var)` | True if the expression is `null` |
| `isNullable($var)` | True if the expression may be null |
| `isNonNull($var)` | True if the expression is guaranteed non-null |
| `matchesAny($var, "pattern1", "pattern2")` | True if `$var` matches any of the patterns |
| `matchesNone($var, "pattern1", "pattern2")` | True if `$var` matches none of the patterns |
| `isCharsetString($var)` | True if `$var` is a charset name string |
| `isSingleCharacter($var)` | True if `$var` is a single-character string |
| `isRegexp($var)` | True if `$var` is a regular expression pattern |
| `inClass("ClassName")` | True if the code is inside the specified class |
| `inPackage("package.name")` | True if the code is inside the specified package |
| `hasModifier($var, "modifier")` | True if the element has the specified modifier |
| `otherwise` | Always true (used as default fallback in multi-rewrite rules) |

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
new FileReader($path) :: sourceVersionGE(11)
=>
new FileReader($path, StandardCharsets.UTF_8)
;;
```

**Guard with negation (`notContains`):**
```
// Only warn if init() is NOT called
$obj = new MyClass() :: notContains("$obj.init()")
=>
$obj = new MyClass();
$obj.init()
;;
```

**Multi-rewrite (Conditional replacement):**
```
Arrays.asList($args)
=> List.of($args) :: sourceVersionGE(9)
=> Collections.unmodifiableList(Arrays.asList($args)) :: otherwise
;;
```

**Hint-only rule (warning without quick fix):**
```
"Consider using InputStream.transferTo() for stream copying (Java 9+)":
$in.read($buf) :: sourceVersionGE(9) && notContains("transferTo")
;;
```
