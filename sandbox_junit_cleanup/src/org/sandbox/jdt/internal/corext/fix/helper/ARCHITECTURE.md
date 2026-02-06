# JUnit Cleanup Helper Directory Architecture

> **Navigation**: [Main Plugin Architecture](../../../../../../../ARCHITECTURE.md) | [Plugin README](../../../../../../../README.md) | [TODO](../../../../../../../TODO.md)

## Overview

This directory contains the implementation of JUnit migration plugins that transform JUnit 3/4 tests to JUnit 5 (Jupiter). The helper directory serves as the core implementation layer for all JUnit cleanup operations.

**Purpose**: 
- Houses all individual migration plugins (e.g., `BeforeJUnitPlugin`, `TestJUnitPlugin`)
- Provides shared infrastructure through the `lib/` subdirectory
- Implements the actual AST transformations that migrate test code

## Two Plugin Architectures

The JUnit cleanup framework supports two distinct architectural approaches for implementing migration plugins. Understanding when to use each is crucial for effective plugin development.

### 1. AbstractTool Architecture

**Location**: `lib/AbstractTool.java`

**Use Case**: Complex transformations requiring full control over AST traversal and rewrite logic.

**Characteristics**:
- Manual visitor implementation in `find()` method
- Custom AST node discovery and filtering logic
- Full control over when and how nodes are processed
- Direct manipulation of AST rewriter in `process2Rewrite()`
- ~80-100 lines of code per plugin

**When to Use AbstractTool**:
- Complex multi-step transformations (e.g., `ExternalResourceJUnitPlugin`)
- Need to traverse and modify multiple related AST nodes
- Custom validation logic beyond simple type matching
- Transformations that involve:
  - Class hierarchy modifications
  - Method renaming and signature changes
  - Field refactoring with usage analysis
  - Anonymous class transformations

**Example - ExternalResourceJUnitPlugin**:
```java
public class ExternalResourceJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {
    @Override
    public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
            Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
        // Custom visitor implementation
        ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
        HelperVisitor.callTypeDeclarationVisitor(ORG_JUNIT_RULES_EXTERNAL_RESOURCE, compilationUnit, 
            dataHolder, nodesprocessed, (visited, aholder) -> processFoundNode(...));
    }
    
    @Override
    protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, 
            ImportRewrite importRewriter, JunitHolder junitHolder) {
        // Custom AST transformation logic
        TypeDeclaration node = junitHolder.getTypeDeclaration();
        modifyExternalResourceClass(node, null, false, rewriter, ast, group, importRewriter);
    }
}
```

### 2. TriggerPatternCleanupPlugin Architecture

**Location**: `lib/TriggerPatternCleanupPlugin.java`

**Use Case**: Declarative pattern-based transformations with minimal boilerplate.

**Characteristics**:
- Pattern matching handled by `TriggerPatternEngine`
- Declarative annotations define what to match and how to transform
- Automatic import management
- Minimal code - typically 20-30 lines per plugin
- Two sub-approaches available

**When to Use TriggerPatternCleanupPlugin**:

#### A. With @RewriteRule Annotation (Simplest)

**Best for**: Simple annotation replacements without complex logic.

**Example - BeforeJUnitPlugin**:
```java
@CleanupPattern(
    value = "@Before",
    kind = PatternKind.ANNOTATION,
    qualifiedType = "org.junit.Before"
)
@RewriteRule(
    replaceWith = "@BeforeEach",
    removeImports = {"org.junit.Before"},
    addImports = {"org.junit.jupiter.api.BeforeEach"}
)
public class BeforeJUnitPlugin extends TriggerPatternCleanupPlugin {
    // Only getPreview() needed - process2Rewrite() is automatic!
    @Override
    public String getPreview(boolean afterRefactoring) { ... }
}
```

**Supported Transformations**:
- Marker annotations: `@Before` → `@BeforeEach`
- Single-value annotations: `@Ignore($value)` → `@Disabled($value)`

**Limitations**:
- Cannot handle NormalAnnotation with named parameters (e.g., `@Ignore(value="reason")`)
- Single placeholder only (no multiple named parameters)

#### B. With Custom process2Rewrite() (More Flexible)

**Best for**: Pattern-based matching with custom transformation logic.

**Example - IgnoreJUnitPlugin**:
```java
@CleanupPattern(
    value = "@Ignore",
    kind = PatternKind.ANNOTATION,
    qualifiedType = "org.junit.Ignore"
)
public class IgnoreJUnitPlugin extends TriggerPatternCleanupPlugin {
    @Override
    protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
            ImportRewrite importRewriter, JunitHolder junitHolder) {
        // Custom logic to handle three annotation types:
        // - MarkerAnnotation: @Ignore → @Disabled
        // - SingleMemberAnnotation: @Ignore("reason") → @Disabled("reason")
        // - NormalAnnotation: @Ignore(value="reason") → @Disabled("reason")
    }
}
```

**When to override process2Rewrite()**:
- Need to handle multiple annotation formats
- Require conditional transformation logic
- Must access binding information from pattern matches
- Need special handling for edge cases

### Decision Matrix

| Requirement | AbstractTool | TriggerPattern + @RewriteRule | TriggerPattern + Custom |
|-------------|--------------|-------------------------------|-------------------------|
| Simple annotation migration | ❌ Overkill | ✅ Perfect | ⚠️ Possible but verbose |
| Pattern matching with placeholders | ⚠️ Manual | ✅ Automatic | ✅ Automatic |
| Multi-node transformations | ✅ Full control | ❌ Not supported | ⚠️ Limited |
| Class hierarchy changes | ✅ Yes | ❌ No | ❌ No |
| NormalAnnotation parameters | ✅ Yes | ❌ No | ✅ Yes |
| Code size | 80-100 lines | 20-30 lines | 30-50 lines |
| Learning curve | Steep | Minimal | Moderate |

## Directory Structure

```
helper/
├── lib/                          # Shared infrastructure
│   ├── AbstractTool.java         # Base class for custom plugins
│   ├── TriggerPatternCleanupPlugin.java  # Base class for pattern-based plugins
│   ├── JunitHolder.java          # Data holder for migration operations
│   ├── AbstractRuleFieldPlugin.java      # Helper for @Rule field migrations
│   ├── AbstractTestAnnotationParameterPlugin.java  # Helper for test annotations with parameters
│   ├── AssertionRefactorer.java  # Assertion reordering utilities
│   ├── ExternalResourceRefactorer.java   # ExternalResource transformation utilities
│   ├── ImportHelper.java         # Import management utilities
│   ├── JUnitConstants.java       # Shared constant definitions
│   ├── LifecycleMethodAdapter.java       # Lifecycle method transformation utilities
│   ├── TestNameRefactorer.java   # TestName field refactoring utilities
│   └── DocumentHelper.java       # Documentation utilities
├── BeforeJUnitPlugin.java        # @Before → @BeforeEach (TriggerPattern + @RewriteRule)
├── AfterJUnitPlugin.java         # @After → @AfterEach (TriggerPattern + @RewriteRule)
├── TestJUnitPlugin.java          # @Test migration (TriggerPattern + @RewriteRule)
├── IgnoreJUnitPlugin.java        # @Ignore → @Disabled (TriggerPattern + Custom)
├── ExternalResourceJUnitPlugin.java  # ExternalResource → callbacks (AbstractTool)
├── ParameterizedTestJUnitPlugin.java # @RunWith(Parameterized) → @ParameterizedTest (AbstractTool)
└── ...                           # Other migration plugins
```

### lib/ Directory Purpose

The `lib/` subdirectory contains:
- **Base classes**: `AbstractTool`, `TriggerPatternCleanupPlugin`
- **Helper classes**: Refactorers, utilities, adapters
- **Data holders**: `JunitHolder` for passing migration data
- **Constants**: Shared FQN strings and configuration

**Why separate lib/?**
- Clear separation of infrastructure from plugins
- Reusable components across multiple plugins
- Easier to locate base classes and utilities
- Follows Java package convention for internal libraries

## JunitHolder Design

**Location**: `lib/JunitHolder.java`

**Purpose**: A type-safe data holder that passes information from the `find()` phase to the `process2Rewrite()` phase.

**Refactored Design** (as of January 2026):
- Private fields with fluent setters for encapsulation
- Builder pattern for convenient construction
- Backward-compatible setter methods
- Specialized getter methods for AST type casting

**Usage Pattern**:
```java
// Option 1: Direct construction with setters (backward compatible)
JunitHolder holder = new JunitHolder();
holder.setMinv(annotationNode)
      .setMinvname(annotationNode.getTypeName().getFullyQualifiedName())
      .setValue("some value");

// Option 2: Builder pattern (new, recommended)
JunitHolder holder = JunitHolder.builder()
    .minv(annotationNode)
    .minvname(annotationNode.getTypeName().getFullyQualifiedName())
    .value("some value")
    .build();

// Access in process2Rewrite()
Annotation annotation = holder.getAnnotation();  // Type-safe cast
String name = holder.getMinvname();
```

**Fields**:
- `minv`: The main AST node to transform (Annotation, MethodInvocation, FieldDeclaration, etc.)
- `minvname`: Name/identifier associated with the node
- `value`: Additional string value
- `additionalInfo`: Context object for complex transformations
- `bindings`: Placeholder bindings from TriggerPattern (e.g., `$value` → Expression)
- `nodesprocessed`: Set of processed nodes (rarely used)
- `method`: Method invocation reference (rarely used)
- `count`: Counter for tracking (rarely used)

## How to Add a New Migration Rule

### Step 1: Choose Your Architecture

**Simple annotation migration?** → Use TriggerPatternCleanupPlugin with @RewriteRule

**Pattern-based with custom logic?** → Use TriggerPatternCleanupPlugin with custom process2Rewrite()

**Complex multi-node transformation?** → Use AbstractTool

### Step 2: Create the Plugin Class

**Example: Migrating @BeforeClass → @BeforeAll**

```java
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.*;

@CleanupPattern(
    value = "@BeforeClass",
    kind = PatternKind.ANNOTATION,
    qualifiedType = "org.junit.BeforeClass",
    cleanupId = "cleanup.junit.beforeclass",
    description = "Migrate @BeforeClass to @BeforeAll"
)
@RewriteRule(
    replaceWith = "@BeforeAll",
    removeImports = {"org.junit.BeforeClass"},
    addImports = {"org.junit.jupiter.api.BeforeAll"}
)
public class BeforeClassJUnitPlugin extends TriggerPatternCleanupPlugin {
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return "@BeforeAll\nstatic void setup() { }";
        }
        return "@BeforeClass\npublic static void setup() { }";
    }
}
```

### Step 3: Register in plugin.xml

Edit `/sandbox_junit_cleanup/plugin.xml`:

```xml
<extension point="org.eclipse.jdt.ui.cleanUps">
    <cleanUp
        class="org.sandbox.jdt.internal.ui.fix.JUnitCleanUp"
        id="org.sandbox.jdt.ui.cleanup.junit.beforeclass"
        runAfter="org.eclipse.jdt.ui.cleanup.junit.before">
        <cleanUpConfigUI
            class="org.sandbox.jdt.internal.ui.fix.JUnitCleanUpConfigTabPage"/>
    </cleanUp>
</extension>
```

### Step 4: Add to MYCleanUpConstants

Edit `sandbox_common/src/org/sandbox/jdt/internal/corext/fix2/MYCleanUpConstants.java`:

```java
public static final String JUNIT_BEFORECLASS= "cleanup.junit.beforeclass"; //$NON-NLS-1$
```

### Step 5: Wire up in CleanUpOptions

Edit `JUnitCleanUp.java` to include your new plugin in the list.

### Step 6: Add Tests

Create test class in `sandbox_junit_cleanup_test/src/org/eclipse/jdt/ui/tests/quickfix/`:

```java
@ParameterizedTest
@EnumSource(value = EclipseVersion.class)
void testBeforeClassMigration(EclipseVersion version) throws CoreException {
    // Test implementation
}
```

### Step 7: Add Documentation

Update `sandbox_junit_cleanup/README.md` with:
- Feature description
- Before/after code examples
- Supported Java versions

## Best Practices

### 1. Prefer Declarative Over Imperative
- Use `@RewriteRule` when possible
- Only write custom logic when necessary

### 2. Leverage Helper Classes
- `ImportHelper` for import management
- `AssertionRefactorer` for assertion parameter reordering
- `ExternalResourceRefactorer` for ExternalResource transformations

### 3. Handle Edge Cases
- Check for null bindings
- Validate type bindings before casting
- Handle both simple and qualified type names

### 4. Write Comprehensive Tests
- Cover multiple Java versions
- Test edge cases (nested classes, complex expressions)
- Include disabled tests for future enhancements

### 5. Document Everything
- Add JavaDoc to all public methods
- Include before/after examples in class JavaDoc
- Update plugin README with new features

### 6. Use JunitHolder Properly
- Use builder pattern for new code
- Access fields through getters in process2Rewrite()
- Use specialized getters (`getAnnotation()`, `getFieldDeclaration()`) for type-safe access

## Pattern Matching Syntax

The TriggerPattern engine supports rich pattern syntax:

### Annotation Patterns
```java
"@Before"              // Marker annotation
"@Ignore($value)"      // Single value with placeholder
"@Test(timeout=$t)"    // Named parameter (limited support)
```

### Placeholder Types
- `$x` - Single AST node (Expression, Annotation, etc.)
- `$args$` - Multi-placeholder (list of nodes)

### Binding Access
```java
Expression value = junitHolder.getBindingAsExpression("$value");
ASTNode node = junitHolder.getBinding("$x");
boolean hasArg = junitHolder.hasBinding("$args$");
```

## Common Pitfalls

### 1. Forgetting Import Management
❌ **Wrong**:
```java
// Change annotation but forget imports
rewriter.replace(oldAnnotation, newAnnotation, group);
```

✅ **Correct**:
```java
rewriter.replace(oldAnnotation, newAnnotation, group);
importRewriter.removeImport("org.junit.Before");
importRewriter.addImport("org.junit.jupiter.api.BeforeEach");
```

### 2. Direct Field Access to JunitHolder
❌ **Wrong** (breaks encapsulation):
```java
holder.minv = node;  // Compile error - field is private
```

✅ **Correct**:
```java
holder.setMinv(node);  // Use setter
// OR
JunitHolder holder = JunitHolder.builder()
    .minv(node)
    .build();
```

### 3. Not Checking for Already Processed Nodes
❌ **Wrong**:
```java
public void find(..., Set<ASTNode> nodesprocessed) {
    // Process node without checking
    processNode(node);
}
```

✅ **Correct**:
```java
public void find(..., Set<ASTNode> nodesprocessed) {
    if (nodesprocessed.contains(node)) {
        return;
    }
    nodesprocessed.add(node);
    processNode(node);
}
```

### 4. Assuming @RewriteRule Handles All Cases
❌ **Wrong** (NormalAnnotation not supported):
```java
@RewriteRule(replaceWith = "@Disabled($value)")
// Will fail for @Ignore(value="reason")
```

✅ **Correct**:
```java
// Override process2Rewrite() for NormalAnnotation support
@Override
protected void process2Rewrite(...) {
    if (annotation instanceof NormalAnnotation) {
        // Custom handling
    }
}
```

## Related Documentation

- [Main Architecture](../../../../../../../ARCHITECTURE.md) - Overall plugin architecture
- [TriggerPattern API](../../../../../../../sandbox_triggerpattern/ARCHITECTURE.md) - Pattern matching engine
- [JUnit 5 Migration Guide](https://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4) - Official JUnit documentation

## Version History

- **January 2026**: Refactored JunitHolder to use builder pattern with private fields
- **December 2025**: Introduced @RewriteRule annotation for declarative transformations
- **2024**: Initial TriggerPattern framework
- **2021**: Original AbstractTool architecture
