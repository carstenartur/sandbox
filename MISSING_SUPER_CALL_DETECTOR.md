# Missing Super Call Detector - TriggerPattern Extension

This document describes the extension to the TriggerPattern framework that enables declarative detection of missing super calls in overridden methods.

## Overview

The "Missing Super Call" detector extends the TriggerPattern framework with three key capabilities:

1. **METHOD_DECLARATION Pattern Kind**: Match method declarations by signature
2. **Override Detection**: Constrain matches to methods that override specific types
3. **Body Constraints**: Validate the presence or absence of patterns in method bodies

## Motivation

Many frameworks require overridden methods to call their super implementation. Examples include:

- SWT Widget's `dispose()` method
- Android Activity lifecycle methods (`onCreate()`, `onDestroy()`)
- Eclipse Part's `dispose()` method
- JavaFX's lifecycle methods

Writing individual AST visitors for each case is time-consuming and error-prone. This extension enables declarative rules that are:

- **Concise**: 5-10 lines of annotated code vs. hundreds of lines of visitor logic
- **Maintainable**: Clear intent, easy to understand and modify
- **Extensible**: New rules can be added without modifying engine code

## API Design

### Basic Method Declaration Matching

```java
@TriggerPattern(
    value = "void dispose()",
    kind = PatternKind.METHOD_DECLARATION
)
@Hint(displayName = "Method matches dispose signature")
public static IJavaCompletionProposal matchDispose(HintContext ctx) {
    // Matches ANY void dispose() method
}
```

### With Method Name Placeholder

```java
@TriggerPattern(
    value = "void $name()",
    kind = PatternKind.METHOD_DECLARATION
)
@Hint(displayName = "Match any void no-arg method")
public static IJavaCompletionProposal matchVoidMethods(HintContext ctx) {
    // $name binding contains the method name
}
```

### With Parameter Placeholders

```java
@TriggerPattern(
    value = "void processEvent($params$)",
    kind = PatternKind.METHOD_DECLARATION
)
@Hint(displayName = "Match processEvent with any parameters")
public static IJavaCompletionProposal matchProcessEvent(HintContext ctx) {
    // $params$ contains list of parameters
}
```

### With Override Constraint

```java
@TriggerPattern(
    value = "void dispose()",
    kind = PatternKind.METHOD_DECLARATION,
    overrides = "org.eclipse.swt.widgets.Widget"
)
@Hint(displayName = "Match dispose() that overrides Widget")
public static IJavaCompletionProposal matchWidgetDispose(HintContext ctx) {
    // Only matches if method overrides Widget.dispose()
}
```

### With Body Constraint (Positive)

```java
@TriggerPattern(
    value = "void onCreate($bundle)",
    kind = PatternKind.METHOD_DECLARATION,
    overrides = "android.app.Activity"
)
@BodyConstraint(mustContain = "super.onCreate($args$)", negate = false)
@Hint(displayName = "Verify super.onCreate() is called")
public static IJavaCompletionProposal verifyOnCreate(HintContext ctx) {
    // Matches only if super.onCreate() IS called
}
```

### With Body Constraint (Negative - Missing Call Detection)

```java
@TriggerPattern(
    value = "void dispose()",
    kind = PatternKind.METHOD_DECLARATION,
    overrides = "org.eclipse.swt.widgets.Widget"
)
@BodyConstraint(mustContain = "super.dispose()", negate = true)
@Hint(displayName = "Missing super.dispose() call",
      description = "Methods overriding dispose() should call super.dispose()")
public static IJavaCompletionProposal detectMissingDispose(HintContext ctx) {
    // Matches methods that DON'T call super.dispose()
    // ctx contains all information needed to create a fix
    
    // Create fix: add super.dispose() call
    AST ast = ctx.getASTRewrite().getAST();
    SuperMethodInvocation superCall = ast.newSuperMethodInvocation();
    superCall.setName(ast.newSimpleName("dispose"));
    // ... add to method body ...
    
    return new ASTRewriteCorrectionProposal(...);
}
```

## Implementation Status

### ✅ Completed (Phase 1 & 2)

1. **METHOD_DECLARATION Pattern Kind**
   - Added `PatternKind.METHOD_DECLARATION` enum value
   - Extended `PatternParser` to parse method declaration patterns
   - Extended `TriggerPatternEngine` to match method declarations
   - Extended `PlaceholderAstMatcher` to handle method matching
   - Comprehensive tests in `NewPatternKindsTest`

2. **API Design**
   - Added `overrides` attribute to `@TriggerPattern` annotation
   - Added `overridesType` field to `Pattern` class
   - Created `@BodyConstraint` annotation
   - Example code in `MissingSuperCallHintProvider`

### 🚧 In Progress (Phase 3)

3. **Override Detection Implementation**
   - [ ] Enable conditional binding resolution in `TriggerPatternEngine`
   - [ ] Implement override checking using ITypeHierarchy
   - [ ] Filter matches based on override constraint
   - [ ] Add tests for override detection

4. **Body Constraint Implementation**
   - [ ] Parse body constraint patterns
   - [ ] Implement body content matching
   - [ ] Handle negation (`negate = true`)
   - [ ] Add tests for body constraints

## Usage Examples

### Example 1: SWT Widget dispose()

```java
@TriggerPattern(
    value = "void dispose()",
    kind = PatternKind.METHOD_DECLARATION,
    overrides = "org.eclipse.swt.widgets.Widget"
)
@BodyConstraint(mustContain = "super.dispose()", negate = true)
@Hint(displayName = "Missing super.dispose()")
public static IJavaCompletionProposal checkWidgetDispose(HintContext ctx) {
    // Fix: Add super.dispose() at end of method
}
```

**Detects:**
```java
class MyWidget extends Widget {
    @Override
    public void dispose() {
        // Missing: super.dispose();
        cleanup();
    }
}
```

### Example 2: Eclipse Part dispose()

```java
@TriggerPattern(
    value = "void dispose()",
    kind = PatternKind.METHOD_DECLARATION,
    overrides = "org.eclipse.ui.part.WorkbenchPart"
)
@BodyConstraint(mustContain = "super.dispose()", negate = true)
@Hint(displayName = "Missing super.dispose() in WorkbenchPart")
public static IJavaCompletionProposal checkPartDispose(HintContext ctx) {
    // Fix: Add super.dispose()
}
```

### Example 3: Android Activity onCreate()

```java
@TriggerPattern(
    value = "void onCreate($savedInstanceState)",
    kind = PatternKind.METHOD_DECLARATION,
    overrides = "android.app.Activity"
)
@BodyConstraint(mustContain = "super.onCreate($args$)", negate = true)
@Hint(displayName = "Missing super.onCreate()")
public static IJavaCompletionProposal checkActivityOnCreate(HintContext ctx) {
    // Fix: Add super.onCreate(savedInstanceState) at start of method
}
```

## Technical Design

### Pattern Matching Flow

```
Source Code
    ↓
TriggerPatternEngine.findMatches()
    ↓
1. AST Visitor finds MethodDeclaration nodes
    ↓
2. PlaceholderAstMatcher checks signature match
    ↓
3. Check override constraint (if specified)
   - Requires binding resolution
   - Uses ITypeHierarchy to check inheritance
    ↓
4. Check body constraint (if specified)
   - Parse body constraint pattern
   - Search method body for pattern
   - Apply negation if specified
    ↓
5. Create Match if all constraints satisfied
    ↓
Hint method receives HintContext with Match
    ↓
Create and return IJavaCompletionProposal
```

### Override Detection (Planned)

Override detection requires enabling binding resolution:

```java
// In TriggerPatternEngine
ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
astParser.setSource(icu);
astParser.setResolveBindings(true);  // Enable for override detection
astParser.setProject(icu.getJavaProject());
```

Then check hierarchy:

```java
IMethodBinding methodBinding = methodDecl.resolveBinding();
if (methodBinding != null) {
    IMethodBinding[] overriddenMethods = 
        Bindings.findOverriddenMethods(methodBinding, true);
    
    for (IMethodBinding overridden : overriddenMethods) {
        ITypeBinding declaringType = overridden.getDeclaringClass();
        if (pattern.getOverridesType().equals(
            declaringType.getQualifiedName())) {
            // Match found!
        }
    }
}
```

### Body Constraint Checking (Planned)

Body constraints are checked after signature and override matching:

```java
if (pattern.hasBodyConstraint()) {
    BodyConstraint constraint = getBodyConstraint(hintMethod);
    Pattern bodyPattern = new Pattern(
        constraint.mustContain(),
        constraint.kind()
    );
    
    MethodDeclaration method = (MethodDeclaration) candidateNode;
    Block body = method.getBody();
    
    if (body != null) {
        List<Match> bodyMatches = findMatches(body, bodyPattern);
        boolean found = !bodyMatches.isEmpty();
        boolean shouldMatch = !constraint.negate();
        
        if (found != shouldMatch) {
            // Constraint not satisfied, skip this match
            continue;
        }
    }
}
```

## Testing

### Unit Tests

Tests in `NewPatternKindsTest` cover:

- ✅ Simple method declaration matching
- ✅ Method name placeholders
- ✅ Parameter matching with placeholders
- ✅ Multi-placeholder parameters
- ✅ Return type matching

### Integration Tests (Planned)

Tests for complete flow:

- [ ] Override detection with binding resolution
- [ ] Body constraint positive matching
- [ ] Body constraint negative matching (missing call detection)
- [ ] Complete missing super call detection flow
- [ ] Fix generation and application

## Performance Considerations

### Binding Resolution

Enabling binding resolution has performance implications:

- **Cost**: Requires full project compilation state
- **Benefit**: Essential for override detection
- **Optimization**: Enable conditionally only for patterns with `overrides` constraint

```java
boolean needsBindings = pattern.getOverridesType() != null;
astParser.setResolveBindings(needsBindings);
```

### Body Constraint Checking

Body constraint checking can be expensive:

- **Cost**: AST traversal for each method body
- **Benefit**: Declarative rule specification
- **Optimization**: 
  - Skip body check if signature/override already fails
  - Cache parsed body constraint patterns
  - Use efficient visitor pattern for body traversal

## Future Enhancements

### XML Configuration

Allow defining rules in `plugin.xml`:

```xml
<extension point="org.sandbox.jdt.triggerpattern.rules">
    <rule id="swt.missing.dispose" severity="warning">
        <match kind="method" 
               pattern="void dispose()" 
               overrides="org.eclipse.swt.widgets.Widget" />
        <constraint type="bodyDoesNotContain" 
                   pattern="super.dispose()" />
        <fix type="insertStatement" 
             pattern="super.dispose();" 
             position="last" />
    </rule>
</extension>
```

### Pattern Language

More expressive patterns:

```java
// Match any protected/public void method with no parameters
@TriggerPattern(
    value = "(protected|public) void $name()",
    kind = PatternKind.METHOD_DECLARATION
)

// Match lifecycle methods matching pattern
@TriggerPattern(
    value = "void on${lifecycle}($params$)",
    kind = PatternKind.METHOD_DECLARATION,
    overrides = "android.app.Activity"
)
```

### Position-Aware Body Constraints

Specify where in the method body the call should appear:

```java
@BodyConstraint(
    mustContain = "super.onCreate($args$)",
    position = Position.FIRST,  // Must be first statement
    negate = true
)
```

## Related Work

### NetBeans Hint System

NetBeans provides a similar declarative hint system. This implementation is inspired by:

- [NetBeans DoubleCheck hint](https://github.com/apache/netbeans/blob/master/java/java.hints/src/org/netbeans/modules/java/hints/DoubleCheck.java)
- NetBeans declarative hints language

### Eclipse Quick Assist

This extends Eclipse's Quick Assist infrastructure with pattern-based matching, making it easier to create hints without writing full AST visitors.

## Contributing

To add a new missing super call detector:

1. Define the pattern in a hint provider class
2. Annotate with `@TriggerPattern`, `@BodyConstraint`, `@Hint`
3. Implement the hint method to create the fix
4. Add tests in the test module
5. Document in this README

Example contribution:

```java
@TriggerPattern(
    value = "void myLifecycleMethod()",
    kind = PatternKind.METHOD_DECLARATION,
    overrides = "com.example.BaseClass"
)
@BodyConstraint(mustContain = "super.myLifecycleMethod()", negate = true)
@Hint(displayName = "Missing super call")
public static IJavaCompletionProposal checkMyLifecycle(HintContext ctx) {
    // Implementation
}
```

## License

Eclipse Public License 2.0

## References

- [TriggerPattern Architecture](sandbox_common/ARCHITECTURE.md#triggerpattern-hint-engine)
- [Eclipse JDT AST Documentation](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/package-summary.html)
- [GitHub Issues: Missing Super Call Detector](https://github.com/carstenartur/sandbox/issues?q=is%3Aissue+%22Missing+Super+Call+Detector%22)
