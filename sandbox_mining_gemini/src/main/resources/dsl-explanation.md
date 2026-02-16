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

Each rule consists of a **pattern** (what to match) and a **rewrite** (what to replace it with):

```
pattern_expression
=>
replacement_expression
```

### Guarded Rules

Rules can have guards that restrict when they apply:

```
pattern_expression :: guard_condition
=>
replacement_expression
```

### Variables

- `$identifier` matches and captures any expression
- `$type` matches a type reference
- Fully qualified names (e.g., `java.util.List`) are automatically imported

### Examples

```
<!id: use-standard-charsets>
<!description: Replace Charset.forName("UTF-8") with StandardCharsets.UTF_8>
<!severity: WARNING>

java.nio.charset.Charset.forName("UTF-8")
=>
java.nio.charset.StandardCharsets.UTF_8
```

```
<!id: use-try-with-resources>
<!description: Convert manual close() to try-with-resources>

$stream.close()
=>
// Use try-with-resources instead
```
