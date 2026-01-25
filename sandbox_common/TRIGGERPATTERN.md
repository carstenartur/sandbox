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

### Annotation Patterns

Match annotations on classes, methods, or fields:

```java
@TriggerPattern(value = "@Before", kind = PatternKind.ANNOTATION)
// Matches: simple marker annotations like @Before, @After

@TriggerPattern(value = "@Test(expected=$ex)", kind = PatternKind.ANNOTATION)
// Matches: @Test annotation with expected parameter, captures exception type

@TriggerPattern(value = "@SuppressWarnings($value)", kind = PatternKind.ANNOTATION)
// Matches: @SuppressWarnings with any value, captures the value
```

### Method Call Patterns

Match method invocations with placeholders:

```java
@TriggerPattern(value = "Assert.assertEquals($a, $b)", kind = PatternKind.METHOD_CALL)
// Matches: assertEquals calls with 2 arguments

@TriggerPattern(value = "assertEquals($msg, $expected, $actual)", kind = PatternKind.METHOD_CALL)
// Matches: assertEquals calls with 3 arguments (with message)

@TriggerPattern(value = "$obj.toString()", kind = PatternKind.METHOD_CALL)
// Matches: toString() calls on any object, captures the receiver
```

### Import Patterns

Match import declarations:

```java
@TriggerPattern(value = "import org.junit.Assert", kind = PatternKind.IMPORT)
// Matches: specific import statement

@TriggerPattern(value = "import static org.junit.Assert.assertEquals", kind = PatternKind.IMPORT)
// Matches: static import for assertEquals method
```

### Field Patterns

Match field declarations with placeholders:

```java
@TriggerPattern(value = "private String $name", kind = PatternKind.FIELD)
// Matches: private String fields, captures field name

@TriggerPattern(value = "@Rule public TemporaryFolder $name", kind = PatternKind.FIELD)
// Matches: @Rule annotated TemporaryFolder fields

@TriggerPattern(value = "public $type $name", kind = PatternKind.FIELD)
// Matches: any public field, captures both type and name
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

### Multi-Placeholders

Multi-placeholders match zero or more AST nodes using the syntax `$name$` (starting and ending with `$`):

```java
@TriggerPattern(value = "Assert.assertEquals($args$)", kind = PatternKind.METHOD_CALL)
// Matches: assertEquals() with any number of arguments
// - Assert.assertEquals(a, b)           → $args$ = [a, b]
// - Assert.assertEquals("msg", a, b)    → $args$ = ["msg", a, b]
// - Assert.assertEquals("msg", a, b, 0.01) → $args$ = ["msg", a, b, 0.01]
```

**Use cases:**
- Match method calls with variable argument counts
- Simplify patterns that previously required multiple separate patterns
- Enable more flexible code transformations

**Accessing multi-placeholder bindings:**
```java
public static IJavaCompletionProposal myHint(HintContext ctx) {
    // For single placeholders
    ASTNode node = ctx.getMatch().getBinding("$x");
    
    // For multi-placeholders
    List<ASTNode> args = ctx.getMatch().getListBinding("$args$");
    for (ASTNode arg : args) {
        // Process each argument
    }
}
```

### Type Constraints

Type constraints restrict placeholders to match only specific AST node types using the syntax `$name:TypeName`:

```java
@TriggerPattern(value = "Assert.assertEquals($msg:StringLiteral, $a, $b)", 
                kind = PatternKind.METHOD_CALL)
// Only matches when first argument is a String literal
// Matches: Assert.assertEquals("test failed", expected, actual)
// Does NOT match: Assert.assertEquals(message, expected, actual)  // message is not a literal
```

**Supported type constraints:**
- `StringLiteral` - String literals (`"text"`)
- `NumberLiteral` - Numeric literals (`42`, `3.14`)
- `TypeLiteral` - Type literals (`Exception.class`)
- `SimpleName` - Simple identifiers (`variable`)
- `MethodInvocation` - Method calls (`method()`)
- `Expression` - Any expression (supertype)
- `Statement` - Any statement (supertype)
- Custom types - Any JDT AST node class name

**Examples:**
```java
// Match only when exception is a class literal
@TriggerPattern(value = "@Test(expected=$ex:TypeLiteral)", kind = PatternKind.ANNOTATION)

// Match only when first argument is a method invocation
@TriggerPattern(value = "$obj.equals($arg:MethodInvocation)", kind = PatternKind.METHOD_CALL)

// Combine multi-placeholder with type constraint
@TriggerPattern(value = "method($args$:Expression)", kind = PatternKind.METHOD_CALL)
// All arguments must be expressions (which is always true, but demonstrates syntax)
```

**When to use type constraints:**
- Avoid matching incorrect code patterns
- Ensure transformations only apply when semantically valid
- Reduce false positives in pattern matching
- Make patterns more precise and self-documenting

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

// For single placeholders
ASTNode node = match.getBinding("$x");
Map<String, Object> bindings = match.getBindings();

// For multi-placeholders
List<ASTNode> nodes = match.getListBinding("$args$");

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

### Example: JUnit Migration Using New Pattern Kinds

```java
// Migrate @Before annotations to @BeforeEach
@TriggerPattern(value = "@Before", kind = PatternKind.ANNOTATION)
@Hint(displayName = "Migrate to JUnit 5 @BeforeEach")
public static IJavaCompletionProposal migrateBeforeAnnotation(HintContext ctx) {
    ImportRewrite imports = ctx.getImportRewrite();
    imports.addImport("org.junit.jupiter.api.BeforeEach");
    imports.removeImport("org.junit.Before");
    
    // Replace @Before with @BeforeEach
    AST ast = ctx.getASTRewrite().getAST();
    MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
    newAnnotation.setTypeName(ast.newName("BeforeEach"));
    
    ctx.getASTRewrite().replace(ctx.getMatch().getMatchedNode(), newAnnotation, null);
    return createProposal(ctx);
}

// Migrate Assert.assertEquals to Assertions.assertEquals
@TriggerPattern(value = "Assert.assertEquals($a, $b)", kind = PatternKind.METHOD_CALL)
@Hint(displayName = "Migrate to JUnit 5 Assertions")
public static IJavaCompletionProposal migrateAssertEquals(HintContext ctx) {
    Map<String, ASTNode> bindings = ctx.getMatch().getBindings();
    
    ImportRewrite imports = ctx.getImportRewrite();
    imports.addImport("org.junit.jupiter.api.Assertions");
    
    // Create Assertions.assertEquals(a, b)
    AST ast = ctx.getASTRewrite().getAST();
    MethodInvocation newCall = ast.newMethodInvocation();
    newCall.setExpression(ast.newName("Assertions"));
    newCall.setName(ast.newSimpleName("assertEquals"));
    newCall.arguments().add(ASTNode.copySubtree(ast, bindings.get("$a")));
    newCall.arguments().add(ASTNode.copySubtree(ast, bindings.get("$b")));
    
    ctx.getASTRewrite().replace(ctx.getMatch().getMatchedNode(), newCall, null);
    return createProposal(ctx);
}

// Remove unused imports
@TriggerPattern(value = "import org.junit.Assert", kind = PatternKind.IMPORT)
@Hint(displayName = "Remove unused JUnit 4 import")
public static IJavaCompletionProposal removeUnusedImport(HintContext ctx) {
    ctx.getASTRewrite().remove(ctx.getMatch().getMatchedNode(), null);
    return createProposal(ctx);
}

// Migrate @Rule fields to @TempDir
@TriggerPattern(value = "@Rule public TemporaryFolder $name", kind = PatternKind.FIELD)
@Hint(displayName = "Migrate to JUnit 5 @TempDir")
public static IJavaCompletionProposal migrateTempFolder(HintContext ctx) {
    Map<String, ASTNode> bindings = ctx.getMatch().getBindings();
    
    ImportRewrite imports = ctx.getImportRewrite();
    imports.addImport("org.junit.jupiter.api.io.TempDir");
    
    // Create @TempDir Path name
    AST ast = ctx.getASTRewrite().getAST();
    // ... (field transformation logic)
    
    return createProposal(ctx);
}
```

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

## Recent Enhancements

Version 1.2.3 added support for:

- **ANNOTATION patterns**: Match annotations like `@Before`, `@Test(expected=$ex)`
- **METHOD_CALL patterns**: Match method invocations like `Assert.assertEquals($a, $b)`
- **IMPORT patterns**: Match import declarations like `import org.junit.Assert`
- **FIELD patterns**: Match field declarations like `@Rule public TemporaryFolder $name`
- **Qualified type support**: Optional `qualifiedType` parameter for type-aware matching

## Future Enhancements

Planned features (see [TODO.md](TODO.md) for details):

- **Multi-placeholders**: `$x$` to match lists (argument lists, statement sequences)
- **Type constraints**: Type-based placeholder constraints (`$x:SimpleName`)
- **Performance**: Pattern indexing for faster matching
- **Pattern Libraries**: Reusable pattern catalogs

## Cleanup Plugin Integration

### Using @CleanupPattern for Eclipse Cleanup Plugins

The `@CleanupPattern` annotation connects TriggerPattern with Eclipse's cleanup framework, enabling declarative pattern-based cleanup plugins.

#### Benefits

| Aspect | Traditional Plugin | TriggerPattern Plugin |
|--------|-------------------|----------------------|
| Lines of Code | 80-150 | 20-40 |
| Pattern Definition | Scattered in find() | Declarative annotation |
| AST Traversal | Manual visitor | Automatic |
| Type Validation | Manual binding checks | qualifiedType parameter |
| Boilerplate | High | Minimal |

#### Example: BeforeJUnitPluginV2

```java
@CleanupPattern(
    value = "@Before",
    kind = PatternKind.ANNOTATION,
    qualifiedType = "org.junit.Before",
    cleanupId = "cleanup.junit.before",
    description = "Migrate @Before to @BeforeEach",
    displayName = "JUnit 4 @Before → JUnit 5 @BeforeEach"
)
public class BeforeJUnitPluginV2 extends TriggerPatternCleanupPlugin {
    
    @Override
    protected JunitHolder createHolder(Match match) {
        JunitHolder holder = new JunitHolder();
        holder.minv = match.getMatchedNode();
        return holder;
    }
    
    @Override
    protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
            ImportRewrite importRewriter, JunitHolder junitHolder) {
        Annotation annotation = junitHolder.getAnnotation();
        MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
        newAnnotation.setTypeName(ast.newSimpleName("BeforeEach"));
        ASTNodes.replaceButKeepComment(rewriter, annotation, newAnnotation, group);
        importRewriter.removeImport("org.junit.Before");
        importRewriter.addImport("org.junit.jupiter.api.BeforeEach");
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return """
                @BeforeEach
                public void setUp() {
                }
                """;
        }
        return """
            @Before
            public void setUp() {
            }
            """;
    }
}
```

#### How to Create a Cleanup Plugin

1. **Extend TriggerPatternCleanupPlugin**: Your plugin class extends the base class
2. **Add @CleanupPattern annotation**: Declare the pattern, type, and metadata
3. **Implement createHolder()**: Extract data from the match
4. **Implement process2Rewrite()**: Transform the AST
5. **Implement getPreview()**: Provide before/after code examples

#### Annotation Parameters

- **value**: The pattern string with placeholders (e.g., `"@Before"`, `"Assert.assertEquals($a, $b)"`)
- **kind**: PatternKind enum (ANNOTATION, METHOD_CALL, IMPORT, FIELD, EXPRESSION, STATEMENT)
- **qualifiedType**: Optional fully qualified type for validation (e.g., `"org.junit.Before"`)
- **cleanupId**: ID used in plugin.xml and preferences (e.g., `"cleanup.junit.before"`)
- **description**: Human-readable description for UI
- **displayName**: Optional display name for UI

#### Advanced Usage

##### Multiple Patterns

If you need multiple patterns, override `getPatterns()` instead of using the annotation:

```java
public class MultiPatternPlugin extends TriggerPatternCleanupPlugin {
    
    @Override
    protected List<Pattern> getPatterns() {
        return List.of(
            new Pattern("@Before", PatternKind.ANNOTATION, "org.junit.Before"),
            new Pattern("@After", PatternKind.ANNOTATION, "org.junit.After")
        );
    }
    
    // ... rest of implementation
}
```

##### Additional Validation

Override `shouldProcess()` to add custom validation logic:

```java
@Override
protected boolean shouldProcess(Match match, Pattern pattern) {
    // Add custom checks beyond type validation
    ASTNode node = match.getMatchedNode();
    // ... custom validation logic
    return true; // or false to skip this match
}
```

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
