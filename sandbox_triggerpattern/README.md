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

### Simplify Boolean Return

```java
@Hint("Simplify boolean return")
@TriggerPattern("if ($cond) { return true; } else { return false; }")
public void simplifyBooleanReturn(HintContext ctx) {
    Expression condition = ctx.getPlaceholder("$cond");
    ctx.replace(ctx.createReturn(condition));
}
```

### Use String.isEmpty()

```java
@Hint("Use String.isEmpty()")
@TriggerPattern("$str.length() == 0")
public void useIsEmpty(HintContext ctx) {
    Expression str = ctx.getPlaceholder("$str");
    ctx.replace(ctx.createMethodCall(str, "isEmpty"));
}
```

### Replace null check with Objects.requireNonNull

```java
@Hint("Use Objects.requireNonNull")
@TriggerPattern("if ($x == null) { throw new NullPointerException(); }")
public void useRequireNonNull(HintContext ctx) {
    Expression var = ctx.getPlaceholder("$x");
    ctx.replace(ctx.createMethodCall("Objects", "requireNonNull", var));
}
```

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
