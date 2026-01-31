# TriggerPattern Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **TriggerPattern** plugin provides a powerful pattern matching engine for code transformations in Eclipse. It allows defining code patterns using a simple syntax and automatically suggesting fixes when patterns are matched. This is the foundation for creating custom hints and quick fixes with minimal boilerplate.

## Key Features

- 🔍 **Pattern Matching Engine** - Match AST patterns using simple syntax
- 🎯 **Placeholder Support** - Use `$x` syntax to match any expression
- 🔧 **Annotation-Based Hints** - Define hints using `@TriggerPattern` and `@Hint`
- ⚡ **Quick Assist Integration** - Automatic integration with Eclipse Quick Assist
- 📦 **Extension Point** - Register custom hint providers
- 🔌 **Eclipse Integration** - Works seamlessly with JDT

## Quick Start

### Creating a Simple Hint

```java
@Hint("Simplify increment")
@TriggerPattern("$x = $x + 1")
public void simplifyIncrement(HintContext ctx) {
    // Get matched placeholder
    Expression var = ctx.getPlaceholder("$x");
    
    // Create suggestion: $x++
    PostfixExpression increment = ctx.getAST().newPostfixExpression();
    increment.setOperand((Expression) ASTNode.copySubtree(ctx.getAST(), var));
    increment.setOperator(PostfixExpression.Operator.INCREMENT);
    
    // Apply transformation
    ctx.replace(increment);
}
```

When Eclipse encounters code like `count = count + 1`, this hint will suggest changing it to `count++`.

### Pattern Syntax

The pattern language supports:

#### Basic Patterns
```java
"$x + 1"           // Match any expression plus 1
"$x.method()"      // Match method call on any receiver
"if ($cond) { }"   // Match if statement with any condition
```

#### Placeholders
- `$x`, `$y`, `$z` - Match any expression
- Can be reused within same pattern (must match same expression)

#### Statement Patterns
```java
"$x = $y;"         // Assignment statement
"return $x;"       // Return statement
"if ($c) { $s }"   // If with condition and body
```

## Core Concepts

### TriggerPattern

The `@TriggerPattern` annotation defines what code to match:

```java
@TriggerPattern("$x.equals($y)")
public void handleEquals(HintContext ctx) {
    // This runs when pattern matches
}
```

### Hint

The `@Hint` annotation provides user-facing description:

```java
@Hint("Use Objects.equals for null safety")
@TriggerPattern("$x.equals($y)")
public void suggestObjectsEquals(HintContext ctx) {
    // Transform to: Objects.equals($x, $y)
}
```

### HintContext

Provides access to matched code and transformation utilities:

```java
public interface HintContext {
    Expression getPlaceholder(String name);
    AST getAST();
    void replace(ASTNode newNode);
    void remove();
    // ... more methods
}
```

## Example Hints

The TriggerPattern plugin includes both **simple** and **complex** cleanup examples to showcase its versatility.

### Simple Patterns

#### Empty String Concatenation

```java
@Hint(displayName = "Replace with String.valueOf()")
@TriggerPattern(value = "\"\" + $x", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal replaceEmptyStringConcatenation(HintContext ctx) {
    // Transforms: "" + value  →  String.valueOf(value)
}
```

#### Use String.isEmpty()

```java
@Hint(displayName = "Use isEmpty()")
@TriggerPattern(value = "$str.length() == 0", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal replaceStringLengthCheck(HintContext ctx) {
    // Transforms: str.length() == 0  →  str.isEmpty()
}
```

#### Simplify Boolean Comparison

```java
@Hint(displayName = "Simplify boolean comparison")
@TriggerPattern(value = "$x == true", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal simplifyBooleanComparisonTrue(HintContext ctx) {
    // Transforms: flag == true  →  flag
}
```

#### Simplify Ternary Boolean

```java
@Hint(displayName = "Simplify ternary boolean")
@TriggerPattern(value = "$cond ? true : false", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal simplifyTernaryBooleanTrueFalse(HintContext ctx) {
    // Transforms: isValid() ? true : false  →  isValid()
}
```

### Complex Patterns

These examples demonstrate TriggerPattern's power for sophisticated transformations:

#### Collection Size Check

```java
@Hint(displayName = "Use isEmpty() for collections")
@TriggerPattern(value = "$list.size() == 0", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal replaceCollectionSizeCheck(HintContext ctx) {
    // Transforms: list.size() == 0  →  list.isEmpty()
    // Works for any Collection type (List, Set, Map, etc.)
}
```

**Why this matters**: Traditional Eclipse cleanups require type checking to determine if the expression is a Collection. TriggerPattern's placeholder matching handles this automatically.

#### StringBuilder Single Append (Anti-pattern Detection)

```java
@Hint(displayName = "Simplify StringBuilder single append")
@TriggerPattern(value = "new StringBuilder().append($x).toString()", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal simplifyStringBuilderSingleAppend(HintContext ctx) {
    // Transforms: new StringBuilder().append(value).toString()  →  String.valueOf(value)
    // Detects unnecessary StringBuilder usage for single value conversion
}
```

**Complexity**: Matches a **chained method call pattern** across three method invocations (constructor, append, toString). Traditional approach would need complex AST visitor traversal.

#### String.format Simplification

```java
@Hint(displayName = "Simplify String.format")
@TriggerPattern(value = "String.format(\"%s\", $x)", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal simplifyStringFormat(HintContext ctx) {
    // Transforms: String.format("%s", obj)  →  String.valueOf(obj)
    // Identifies performance improvement opportunity
}
```

**Complexity**: Matches **method with specific literal argument** plus placeholder. Shows how TriggerPattern can mix literals and placeholders.

#### Objects.equals for Null Safety

```java
@Hint(displayName = "Use Objects.equals for null safety")
@TriggerPattern(value = "$x.toString().equals($y)", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal useObjectsEquals(HintContext ctx) {
    // Transforms: obj.toString().equals(str)  →  Objects.equals(obj.toString(), str)
    // Prevents NullPointerException by using null-safe utility method
    // Automatically adds import for java.util.Objects
}
```

**Complexity**: 
- Matches **nested method calls** (toString() within equals())
- **Two placeholders** bound to different expressions
- **Import management** - adds `java.util.Objects` import automatically
- **Null-safety** - transforms potentially unsafe code to safe API

#### Objects.requireNonNullElse (Java 9+ API)

```java
@Hint(displayName = "Use Objects.requireNonNullElse")
@TriggerPattern(value = "$x != null ? $x : $default", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal useRequireNonNullElse(HintContext ctx) {
    // Transforms: value != null ? value : "default"  →  Objects.requireNonNullElse(value, "default")
    // Modernizes code to use Java 9+ API
    // Placeholder $x appears TWICE in pattern and must match same expression
}
```

**Complexity**:
- **Conditional expression** matching with placeholder reuse
- **Same placeholder used multiple times** - TriggerPattern ensures both $x references match
- **Modern API suggestion** - demonstrates how patterns can guide towards newer, better APIs
- **Import management** - adds required import

### Pattern Comparison: Traditional vs TriggerPattern

| Aspect | Traditional Cleanup | TriggerPattern |
|--------|-------------------|----------------|
| **Lines of Code** | 100-300 per pattern | 30-50 per pattern |
| **AST Traversal** | Manual visitor implementation | Automatic |
| **Type Checking** | Manual ITypeBinding checks | Automatic via placeholders |
| **Pattern Definition** | Scattered across visitor methods | Single declarative string |
| **Placeholder Binding** | Manual AST node extraction | Automatic via $x syntax |
| **Chained Calls** | Complex recursive traversal | Natural pattern syntax |
| **Import Management** | Manual ImportRewrite | Built into HintContext |

### Why Complex Examples Matter

These complex patterns demonstrate that TriggerPattern excels at scenarios that are **tedious with traditional approaches**:

1. **Chained Method Calls**: `new StringBuilder().append($x).toString()` would require traversing multiple AST nodes and verifying the call chain structure
2. **Multiple Placeholders**: `$x.toString().equals($y)` requires binding two different sub-expressions
3. **Placeholder Reuse**: `$x != null ? $x : $default` ensures the same expression appears in both condition and then-branch
4. **Import Management**: Automatically handles adding imports like `java.util.Objects`

## Architecture

The pattern engine consists of:

### PatternParser
- Parses pattern strings into AST templates
- Handles placeholder extraction
- Validates syntax

### PatternMatcher
- Traverses AST to find matches
- Binds placeholders to actual code
- Handles nested patterns

### HintRegistry
- Loads hints from extension points
- Indexes patterns for efficient matching
- Lazy loading for performance

### QuickAssistProcessor
- Integrates with Eclipse Quick Assist (Ctrl+1)
- Presents matching hints to user
- Applies transformations

## Registration

### Via Extension Point

In `plugin.xml`:
```xml
<extension point="org.sandbox.jdt.triggerpattern.hintProviders">
  <hintProvider class="com.example.MyHintProvider"/>
</extension>
```

### Via Annotation Scanning

The registry automatically discovers classes with `@Hint` and `@TriggerPattern` annotations.

## Documentation

- **[TRIGGERPATTERN.md](../sandbox_common/TRIGGERPATTERN.md)** - Comprehensive pattern syntax guide
- **[Architecture](ARCHITECTURE.md)** - Implementation details
- **[TODO](TODO.md)** - Future enhancements

## Testing

Tests are in `sandbox_triggerpattern_test`:
- Pattern parser tests
- Pattern matcher tests
- Hint engine integration tests

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_triggerpattern_test
```

## Performance

The pattern engine is optimized for:
- Lazy loading of hint providers
- Pattern indexing by AST node type
- Early exit for non-matching patterns
- Caching of compiled patterns

## Future Enhancements

Planned features (see [TODO.md](TODO.md)):
- Multi-placeholder support (`$args$` for lists)
- Placeholder constraints (`$x:SimpleName`)
- Pattern composition and libraries
- Negative patterns
- Optional parts and repetition

## Use Cases

The TriggerPattern engine is perfect for:
- Custom code style enforcement
- Project-specific refactorings
- API modernization hints
- Best practice suggestions
- Security vulnerability detection

## Contributing

To add new hints:
1. Create a class with `@Hint` and `@TriggerPattern` annotations
2. Implement transformation logic
3. Register via extension point
4. Add tests

See [Architecture](ARCHITECTURE.md) for detailed implementation guide.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Modules**: [Common Utilities](../sandbox_common/) contains the TriggerPattern implementation
