# Reflective Pattern Handling Framework

This framework enables writing Eclipse JDT cleanup plugins using a declarative style similar to NetBeans' `@TriggerPattern` approach. Instead of manually implementing pattern matching and AST traversal, you can simply annotate methods with `@PatternHandler` and let the framework handle the infrastructure.

## Overview

The framework consists of three main components:

1. **`@PatternHandler`** - Annotation for marking handler methods
2. **`PatternContext`** - Context object providing access to match information and AST tools
3. **`ReflectivePatternCleanupPlugin`** - Base class that automatically discovers and invokes handlers

## Quick Start

### 1. Create a Plugin Class

Extend `ReflectivePatternCleanupPlugin` and add methods annotated with `@PatternHandler`:

```java
public class MyCleanupPlugin extends ReflectivePatternCleanupPlugin {
    
    @PatternHandler(value = "if ($expr instanceof $type)", kind = PatternKind.STATEMENT)
    public void handleInstanceOf(PatternContext context) {
        // Your transformation logic here
        ASTNode expr = context.getBoundNode("$expr");
        ASTNode type = context.getBoundNode("$type");
        
        // Access AST rewriting tools
        ASTRewrite rewrite = context.getRewrite();
        AST ast = context.getAST();
        ImportRewrite importRewrite = context.getImportRewrite();
        
        // Perform transformation
        // ...
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        return afterRefactoring ? "// after code" : "// before code";
    }
}
```

### 2. Handler Method Requirements

Methods annotated with `@PatternHandler` must:
- Have `public` visibility
- Return `void`
- Take exactly one parameter of type `PatternContext`

### 3. Pattern Syntax

The framework uses the same pattern syntax as `TriggerPatternEngine`:
- `$var` - Matches a single AST node and binds it to the name `var`
- `$args$` - Matches a list of nodes (multi-placeholder)
- Standard Java syntax for the rest

Examples:
```java
@PatternHandler("$x + 1")                    // Expression pattern
@PatternHandler(value = "return $expr;", kind = PatternKind.STATEMENT)
@PatternHandler(value = "@Test", kind = PatternKind.ANNOTATION, 
                qualifiedType = "org.junit.Test")
```

## PatternContext API

The `PatternContext` object provides convenient access to everything needed for transformations:

```java
// Access match information
Match match = context.getMatch();
ASTNode matchedNode = context.getMatchedNode();

// Get bound placeholders
ASTNode singleNode = context.getBoundNode("$var");
List<ASTNode> multiNodes = context.getBoundList("$args$");

// Access AST tools
AST ast = context.getAST();
ASTRewrite rewrite = context.getRewrite();
ImportRewrite importRewrite = context.getImportRewrite();
TextEditGroup editGroup = context.getEditGroup();
```

## Multiple Handlers

A single plugin can have multiple `@PatternHandler` annotated methods:

```java
public class MathSimplificationPlugin extends ReflectivePatternCleanupPlugin {
    
    @PatternHandler("$x + 0")
    public void handleAddZero(PatternContext context) {
        // Remove + 0
    }
    
    @PatternHandler("$x * 1")
    public void handleMultiplyOne(PatternContext context) {
        // Remove * 1
    }
    
    @PatternHandler("$x * 0")
    public void handleMultiplyZero(PatternContext context) {
        // Replace with 0
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        return "...";
    }
}
```

## Complete Example

See `ConvertToSwitchPatternPlugin` in the `org.sandbox.jdt.triggerpattern.examples` package for a complete working example that:
- Matches `if (expr instanceof Type)` patterns
- Collects chains of instanceof checks
- Converts them to modern switch expressions
- Demonstrates accessing bound placeholders and performing AST rewrites

## Architecture

### Handler Discovery

When a `ReflectivePatternCleanupPlugin` is instantiated:
1. It scans all methods for `@PatternHandler` annotations
2. Validates method signatures
3. Extracts pattern definitions from annotations
4. Makes handlers accessible for invocation

### Match Processing

When the cleanup runs:
1. `findAllMatches()` finds all pattern matches in the compilation unit
2. Each match is associated with its handler method (using `IdentityHashMap`)
3. `processRewrite()` creates a `PatternContext` for each match
4. Handler methods are invoked reflectively with the context

### Integration with Eclipse

The framework integrates seamlessly with Eclipse's cleanup infrastructure through `AbstractPatternCleanupPlugin`:
- Pattern matching via `TriggerPatternEngine`
- AST rewriting via `ASTRewrite` and `ImportRewrite`
- Change tracking via `TextEditGroup`
- Support for qualified type validation

## Benefits

### Compared to Manual Implementation

**Before (Traditional):**
```java
public class OldStyleCleanup extends AbstractCleanUp {
    @Override
    public CleanUpRequirements getRequirements() { ... }
    
    @Override
    public ICleanUpFix createFix(CleanUpContext context) {
        // Manual pattern matching
        // Manual AST traversal
        // Complex match tracking
        // Lots of boilerplate
    }
}
```

**After (Reflective):**
```java
public class NewStyleCleanup extends ReflectivePatternCleanupPlugin {
    @PatternHandler("$x + 1")
    public void handlePattern(PatternContext context) {
        // Just the transformation logic
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) { ... }
}
```

### Key Advantages

- **Less Boilerplate**: No manual pattern matching or AST traversal code
- **Declarative**: Pattern is declared right next to the handler
- **Type-Safe**: Compile-time checking of handler signatures
- **Maintainable**: Transformation logic is isolated in focused methods
- **Extensible**: Easy to add new patterns by adding new methods
- **Testable**: Handlers can be tested independently

## Testing

The framework includes comprehensive tests in `ReflectivePatternCleanupPluginTest`:
- Handler discovery
- Pattern matching
- Handler invocation
- Multiple handlers per plugin

## Implementation Notes

### Design Decisions

1. **IdentityHashMap for Match Tracking**: Since `Match` objects don't store their pattern origin, we use identity-based mapping to associate matches with handlers.

2. **Reflection for Handler Invocation**: Allows flexible, annotation-driven discovery while maintaining type safety through validation.

3. **PatternContext as Holder**: Uses the holder pattern from `AbstractPatternCleanupPlugin` to pass rewriting context to handlers.

### Limitations

- Handler methods must have the exact signature `void methodName(PatternContext)`
- Cannot override pattern discovery (all `@PatternHandler` methods are discovered)
- Single handler per pattern (if multiple handlers have the same pattern, behavior is undefined)

## Future Enhancements

Potential improvements for future versions:
- Support for handler method ordering/priorities
- Conditional handler execution based on additional criteria
- Integration with Eclipse cleanup preferences UI
- Support for chaining handlers
- Performance optimizations for large codebases

## See Also

- `TriggerPatternEngine` - The underlying pattern matching engine
- `AbstractPatternCleanupPlugin` - Base class for pattern-based cleanups
- `PatternHandler` - Annotation API documentation
- `PatternContext` - Context API documentation
