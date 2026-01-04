# TriggerPattern Hint Engine

A pattern-based code matching and transformation engine for Eclipse JDT, inspired by NetBeans' TriggerPattern functionality.

## Overview

The TriggerPattern hint engine allows developers to define code patterns with placeholders and automatically suggest refactorings when those patterns are matched. It integrates seamlessly with Eclipse's Quick Assist UI.

## Quick Start

### 1. Define a Hint

Create a hint provider class with annotated methods:

```java
package com.example.hints;

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.sandbox.jdt.triggerpattern.api.*;

public class MyHintProvider {
    
    @TriggerPattern(value = "$x + 1", kind = PatternKind.EXPRESSION)
    @Hint(displayName = "Replace with increment operator")
    public static IJavaCompletionProposal simplifyIncrement(HintContext ctx) {
        // Get matched nodes
        ASTNode matchedNode = ctx.getMatch().getMatchedNode();
        ASTNode xNode = ctx.getMatch().getBindings().get("$x");
        
        // Create replacement using ASTRewrite
        AST ast = ctx.getASTRewrite().getAST();
        PrefixExpression prefixExpr = ast.newPrefixExpression();
        prefixExpr.setOperator(PrefixExpression.Operator.INCREMENT);
        prefixExpr.setOperand((Expression) ASTNode.copySubtree(ast, xNode));
        
        // Apply the rewrite
        ctx.getASTRewrite().replace(matchedNode, prefixExpr, null);
        
        // Return the proposal
        return new ASTRewriteCorrectionProposal(
            "Replace with ++",
            ctx.getICompilationUnit(),
            ctx.getASTRewrite(),
            10,
            null
        );
    }
}
```

### 2. Register the Hint Provider

Add to your plugin's `plugin.xml`:

```xml
<extension point="org.sandbox.jdt.triggerpattern.hints">
   <hintProvider class="com.example.hints.MyHintProvider"/>
</extension>
```

### 3. Use in Eclipse

1. Open a Java file
2. Place cursor on matching code (e.g., `a + 1`)
3. Press `Ctrl+1` (Quick Assist)
4. See your hint proposal!

## Pattern Syntax

### Expression Patterns

Match Java expressions with placeholders:

```java
@TriggerPattern(value = "$x + 1", kind = PatternKind.EXPRESSION)
// Matches: a + 1, variable + 1, obj.field + 1, etc.

@TriggerPattern(value = "$obj.toString()", kind = PatternKind.EXPRESSION)
// Matches: any object's toString() call

@TriggerPattern(value = "$a + $b", kind = PatternKind.EXPRESSION)
// Matches: any addition with two different operands
```

### Statement Patterns

Match Java statements:

```java
@TriggerPattern(value = "if ($cond) $then;", kind = PatternKind.STATEMENT)
// Matches: if statements with single-statement body

@TriggerPattern(value = "return $x;", kind = PatternKind.STATEMENT)
// Matches: return statements
```

### Placeholder Rules

- **Prefix with `$`**: `$x`, `$var`, `$condition`
- **First binding**: First occurrence of `$x` binds to an AST node
- **Subsequent matches**: Later occurrences of `$x` must match the same node

Example:
```java
@TriggerPattern(value = "$x + $x", kind = PatternKind.EXPRESSION)
// Matches: a + a, b + b
// Does NOT match: a + b (different variables)
```

## API Reference

### Core Classes

#### `Pattern`
Represents a pattern with placeholders.

```java
Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
```

#### `Match`
Result of a successful pattern match.

```java
Match match = ...;
ASTNode matchedNode = match.getMatchedNode();
Map<String, ASTNode> bindings = match.getBindings();
int offset = match.getOffset();
int length = match.getLength();
```

#### `HintContext`
Context provided to hint methods.

```java
public static IJavaCompletionProposal myHint(HintContext ctx) {
    CompilationUnit cu = ctx.getCompilationUnit();
    ICompilationUnit icu = ctx.getICompilationUnit();
    Match match = ctx.getMatch();
    ASTRewrite rewrite = ctx.getASTRewrite();
    ImportRewrite importRewrite = ctx.getImportRewrite();
    // ...
}
```

#### `TriggerPatternEngine`
Main engine for finding pattern matches.

```java
TriggerPatternEngine engine = new TriggerPatternEngine();
List<Match> matches = engine.findMatches(compilationUnit, pattern);
```

### Annotations

#### `@TriggerPattern`
Marks a method as a pattern hint.

```java
@TriggerPattern(
    value = "$x + 1",           // Pattern string (required)
    kind = PatternKind.EXPRESSION, // EXPRESSION or STATEMENT (default: EXPRESSION)
    id = "simplify.increment"   // Optional unique ID
)
```

#### `@Hint`
Provides metadata about a hint.

```java
@Hint(
    displayName = "Simplify increment",  // UI display name
    description = "Replace x+1 with ++x", // Description
    enabledByDefault = true,             // Enable by default (default: true)
    severity = "info"                    // Severity level (default: "info")
)
```

## Extension Point

### Using Extension Point Directly

You can also register hints declaratively in `plugin.xml`:

```xml
<extension point="org.sandbox.jdt.triggerpattern.hints">
   <!-- Register a provider class with @TriggerPattern methods -->
   <hintProvider class="com.example.MyHintProvider"/>
   
   <!-- Or register a pattern directly -->
   <pattern
      id="simplify.increment"
      value="$x + 1"
      kind="EXPRESSION"
      displayName="Simplify increment"
      class="com.example.IncrementHint"
      method="simplify"/>
</extension>
```

## Advanced Usage

### Working with Bindings

Access bound AST nodes from placeholders:

```java
@TriggerPattern(value = "$x.equals($y)", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal replaceEquals(HintContext ctx) {
    Map<String, ASTNode> bindings = ctx.getMatch().getBindings();
    
    Expression left = (Expression) bindings.get("$x");
    Expression right = (Expression) bindings.get("$y");
    
    // Create Objects.equals(left, right)
    // ...
}
```

### Managing Imports

Use `ImportRewrite` to add necessary imports:

```java
@TriggerPattern(value = "$x + $y", kind = PatternKind.EXPRESSION)
public static IJavaCompletionProposal useOptional(HintContext ctx) {
    ImportRewrite imports = ctx.getImportRewrite();
    
    // Add import for Optional
    String optionalType = imports.addImport("java.util.Optional");
    
    // Use in your transformation
    // ...
}
```

### Multiple Proposals

Return multiple proposals for the same pattern:

```java
@TriggerPattern(value = "$x + 1", kind = PatternKind.EXPRESSION)
public static List<IJavaCompletionProposal> incrementOptions(HintContext ctx) {
    List<IJavaCompletionProposal> proposals = new ArrayList<>();
    
    // Option 1: ++x (prefix)
    proposals.add(createPrefixIncrementProposal(ctx));
    
    // Option 2: x++ (postfix)
    proposals.add(createPostfixIncrementProposal(ctx));
    
    return proposals;
}
```

## Examples

See `org.sandbox.jdt.triggerpattern.examples.ExampleHintProvider` for working examples:

- **simplifyIncrement**: Replaces `$x + 1` with `++$x`
- **simplifyDecrement**: Replaces `$x - 1` with `--$x`

## Architecture

The TriggerPattern engine consists of:

1. **PatternParser**: Parses pattern strings into AST nodes
2. **PlaceholderAstMatcher**: Matches patterns with placeholder binding
3. **TriggerPatternEngine**: Traverses AST to find matches
4. **HintRegistry**: Discovers and manages registered hints
5. **TriggerPatternQuickAssistProcessor**: Integrates with Eclipse Quick Assist

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architecture documentation.

## Testing

The `sandbox_common_test` module contains comprehensive tests:

- **PatternParserTest**: Tests pattern parsing
- **PlaceholderMatcherTest**: Tests placeholder binding logic
- **TriggerPatternEngineTest**: Tests pattern matching and traversal

## Future Enhancements

Planned features (see [TODO.md](TODO.md) for details):

- **Multi-placeholders**: `$x$` to match lists (argument lists, statement sequences)
- **Constraints**: Type-based placeholder constraints (`$x:SimpleName`)
- **Performance**: Pattern indexing for faster matching
- **Cleanup Integration**: Use patterns in Save Actions and batch cleanups
- **Pattern Libraries**: Reusable pattern catalogs

## Contributing

To add new hints to the TriggerPattern engine:

1. Create a hint provider class in your plugin
2. Annotate methods with `@TriggerPattern` and `@Hint`
3. Register the provider in your `plugin.xml`
4. Test with various code examples
5. Document the pattern and its behavior

## License

Eclipse Public License 2.0

## Credits

Inspired by NetBeans' TriggerPattern API, reimplemented for Eclipse JDT.
