# Contributing to JUnit Migration Cleanup

This guide helps contributors add new migration features to the JUnit cleanup plugin.

## Quick Start

### Prerequisites
- Java 21
- Maven 3.6+
- Eclipse JDT knowledge
- Understanding of AST (Abstract Syntax Tree) manipulation

### Repository Structure
```
sandbox_junit_cleanup/              # Main plugin implementation
├── src/org/sandbox/jdt/internal/corext/fix/
│   ├── JUnitCleanUpFixCore.java   # Registry of all cleanup plugins
│   └── helper/
│       ├── AbstractTool.java       # Base class for plugins
│       ├── BeforeJUnitPlugin.java  # Example: Simple annotation replacement
│       ├── AssertJUnitPlugin.java  # Example: Complex parameter reordering
│       ├── TestJUnitPlugin.java    # Example: Marker annotation replacement
│       └── ...                     # Other plugins
sandbox_junit_cleanup_test/         # Test suite
└── src/org/eclipse/jdt/ui/tests/quickfix/Java8/
    ├── Migration*Test.java         # Focused test classes
    ├── JUnitCleanupCases.java      # Parameterized test data
    └── TODO.md                     # Missing features tracker
```

## Adding a New Migration Feature

### Step 1: Create the Plugin Class

Choose the appropriate base class based on complexity:

#### Simple Annotation Replacement
Extend `AbstractMarkerAnnotationJUnitPlugin` for simple annotation name changes:

```java
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.JUnitConstants.*;

public class YourPluginJUnitPlugin extends AbstractMarkerAnnotationJUnitPlugin {

    @Override
    protected String getSourceAnnotation() {
        return "org.junit.YourAnnotation";  // JUnit 4 annotation FQCN
    }

    @Override
    protected String getTargetAnnotationName() {
        return "YourAnnotation";  // JUnit 5 annotation simple name
    }

    @Override
    protected String getTargetAnnotationImport() {
        return "org.junit.jupiter.api.YourAnnotation";  // JUnit 5 FQCN
    }
    
    @Override
    public String getPreview(boolean afterRefactoring) {
        if (afterRefactoring) {
            return """
                    import org.junit.jupiter.api.YourAnnotation;
                    """;
        }
        return """
                import org.junit.YourAnnotation;
                """;
    }

    @Override
    public String toString() {
        return "YourAnnotation";
    }
}
```

#### Complex Transformations
Extend `AbstractTool` directly for complex migrations:

```java
public class ComplexMigrationPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

    @Override
    public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
            Set<CompilationUnitRewriteOperationWithSourceRange> operations, 
            Set<ASTNode> nodesprocessed) {
        ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
        
        // Use HelperVisitor to find nodes
        HelperVisitor.callMethodInvocationVisitor(
            "org.junit.OldClass", 
            "methodName", 
            compilationUnit, 
            dataHolder,
            nodesprocessed, 
            (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder)
        );
    }

    @Override
    void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, 
            ImportRewrite importRewriter, JunitHolder junitHolder) {
        // Implement AST transformations here
        // Use helper classes:
        // - ImportHelper for import management
        // - AssertionRefactorer for parameter reordering
        // - ExternalResourceRefactorer for lifecycle management
        // - TestNameRefactorer for method signature changes
    }

    // Implement getPreview() and toString()
}
```

### Step 2: Register the Plugin

Add to `JUnitCleanUpFixCore.java`:

```java
public enum JUnitCleanUpFixCore {
    BEFORE(new BeforeJUnitPlugin()),
    AFTER(new AfterJUnitPlugin()),
    // ... existing plugins ...
    YOURFEATURE(new YourPluginJUnitPlugin()),  // ADD THIS
    // ...
}
```

### Step 3: Add Cleanup Constants

Add constants to `sandbox_common/src/org/sandbox/jdt/internal/corext/fix2/MYCleanUpConstants.java`:

```java
public class MYCleanUpConstants {
    // ... existing constants ...
    
    /**
     * Migrate JUnit 4 YourFeature to JUnit 5.
     * <p>
     * Possible values: {TRUE, FALSE}
     * </p>
     * @since 1.2.2
     */
    public static final String JUNIT_CLEANUP_4_YOURFEATURE= "cleanup.junit4_yourfeature";
}
```

### Step 4: Wire Up in CleanUp Classes

1. **Add to options initializer** (`sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/preferences/cleanup/DefaultCleanUpOptionsInitializer.java`):
```java
settings.put(MYCleanUpConstants.JUNIT_CLEANUP_4_YOURFEATURE, CleanUpOptions.FALSE);
```

2. **Add to cleanup core** (`sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/fix/JUnitCleanUpCore.java`):
```java
if (isEnabled(MYCleanUpConstants.JUNIT_CLEANUP_4_YOURFEATURE)) {
    JUnitCleanUpFixCore.YOURFEATURE.find(this, compilationUnit, operations, nodesprocessed);
}
```

### Step 5: Write Tests

Create tests in appropriate test class (or create new one for new aspect):

```java
@Test
public void migrates_yourFeature_to_jupiter() throws CoreException {
    IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
    ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
            """
            package test;
            import org.junit.YourAnnotation;
            
            public class MyTest {
                @YourAnnotation
                public void test() {
                }
            }
            """, false, null);

    context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
    context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_YOURFEATURE);

    context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
            """
            package test;
            import org.junit.jupiter.api.YourAnnotation;
            
            public class MyTest {
                @YourAnnotation
                public void test() {
                }
            }
            """
    }, null);
}
```

### Step 6: Build and Test

```bash
# Build the project
mvn clean verify -Pjacoco

# Run specific test
mvn test -pl sandbox_junit_cleanup_test -Dtest=YourTestClass#migrates_yourFeature_to_jupiter

# Run with Xvfb (Linux only)
xvfb-run --auto-servernum mvn test -pl sandbox_junit_cleanup_test
```

## Common Migration Patterns

### Pattern 1: Simple Annotation Name Change
**Example**: `@Before` → `@BeforeEach`

Uses `AbstractMarkerAnnotationJUnitPlugin` - just override annotation names.

### Pattern 2: Method Parameter Reordering
**Example**: `Assert.assertEquals("msg", expected, actual)` → `Assertions.assertEquals(expected, actual, "msg")`

Use `AssertionRefactorer.reorderParameters()`:
```java
Set<String> oneParam = Set.of("assertTrue", "assertFalse");
Set<String> twoParam = Set.of("assertEquals", "assertNotEquals");
AssertionRefactorer.reorderParameters(methodInvocation, rewriter, group, oneParam, twoParam);
```

### Pattern 3: Annotation Parameter to Lambda
**Example**: `@Test(expected = Exception.class)` → `assertThrows(Exception.class, () -> { ... })`

Requires:
1. Extract annotation parameter value
2. Wrap method body in lambda expression
3. Create assertThrows call
4. Replace annotation

### Pattern 4: Rule to Extension
**Example**: `@Rule TemporaryFolder` → `@TempDir Path`

Requires:
1. Change field type
2. Update field initializer
3. Change annotation
4. Update method calls on the field

### Pattern 5: Import Transformation
**Example**: Static import migration

Use `ImportHelper`:
```java
importRewriter.addStaticImport("org.junit.jupiter.api.Assertions", "assertEquals", false);
importRewriter.removeStaticImport("org.junit.Assert.assertEquals");
```

## Testing Guidelines

### Test Organization
- Group tests by migration aspect (assertions, lifecycle, rules, etc.)
- One test method per scenario
- Use descriptive names: `migrates_<old>_to_<new>_<scenario>()`

### Test Structure (Arrange-Act-Assert)
```java
@Test
public void migrates_feature_x_to_feature_y() throws CoreException {
    // Arrange - Create JUnit 4 code
    IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
    ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
            """
            // JUnit 4 code here
            """, false, null);

    // Act - Enable cleanup and run
    context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
    context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_YOURFEATURE);

    // Assert - Verify JUnit 5 output
    context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
            """
            // Expected JUnit 5 code here
            """
    }, null);
}
```

### Disabled Tests for Future Features
If production code isn't ready:
```java
@Disabled("Not yet implemented - brief reason")
@Test
public void migrates_future_feature() throws CoreException {
    // Test implementation
}
```

## AST Manipulation Tips

### Finding Nodes
Use `HelperVisitor` methods:
- `callMethodInvocationVisitor()` - Find method calls
- `callMarkerAnnotationVisitor()` - Find annotations
- `callImportDeclarationVisitor()` - Find imports
- `callFieldDeclarationVisitor()` - Find fields

### Creating New Nodes
```java
AST ast = rewriter.getAST();

// Create annotation
MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
newAnnotation.setTypeName(ast.newSimpleName("Test"));

// Create method invocation
MethodInvocation newCall = ast.newMethodInvocation();
newCall.setName(ast.newSimpleName("assertEquals"));
newCall.arguments().add(rewriter.createCopyTarget(arg1));
```

### Replacing Nodes
```java
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

// Replace but keep comments
ASTNodes.replaceButKeepComment(rewriter, oldNode, newNode, group);

// Simple replace
rewriter.replace(oldNode, newNode, group);
```

### Import Management
```java
// Add import
importRewriter.addImport("org.junit.jupiter.api.Test");

// Remove import
importRewriter.removeImport("org.junit.Test");

// Static import
importRewriter.addStaticImport("org.junit.jupiter.api.Assertions", "assertEquals", false);
```

## Common Pitfalls

### 1. Not Handling All Import Variants
JUnit 4 code might use:
- Qualified calls: `org.junit.Assert.assertEquals()`
- Static imports: `import static org.junit.Assert.*;`
- Explicit static imports: `import static org.junit.Assert.assertEquals;`
- Instance imports: `import org.junit.Assert;`

Your plugin must handle all variants!

### 2. Forgetting to Update Subclasses
Some transformations affect inheritance hierarchies. Use visitors that traverse type hierarchies.

### 3. Not Preserving Comments
Always use `ASTNodes.replaceButKeepComment()` instead of plain `rewriter.replace()` to preserve Javadoc and inline comments.

### 4. Missing Edge Cases
Test edge cases:
- Empty method bodies
- Multiple annotations on same element
- Nested classes
- Anonymous classes
- Lambda expressions

### 5. Import Conflicts
Let `ImportRewrite` handle conflicts automatically - don't manually build import statements.

## Debugging Tips

### Enable Debug Logging
Set environment variable:
```bash
export ECLIPSE_DEBUG=true
mvn test -pl sandbox_junit_cleanup_test
```

### Inspect Generated AST
```java
// In your plugin:
System.out.println("AST: " + ast.toString());
System.out.println("Node: " + node.toString());
```

### Test in Eclipse IDE
1. Import project into Eclipse
2. Run tests as JUnit Plugin Tests
3. Set breakpoints in plugin code
4. Step through AST transformations

## Resources

- [Eclipse JDT AST Documentation](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/package-summary.html)
- [JUnit 5 Migration Guide](https://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4)
- [ARCHITECTURE.md](ARCHITECTURE.md) - Detailed plugin architecture
- [TODO.md](../sandbox_junit_cleanup_test/src/org/eclipse/jdt/ui/tests/quickfix/Java8/TODO.md) - Feature implementation tracking

## Getting Help

1. Check existing plugin implementations for similar patterns
2. Review test cases for expected behavior
3. Read ARCHITECTURE.md for design patterns
4. Check TODO.md for known issues and feature status

## Feature Module Maintenance

When modifying the feature module (`sandbox_junit_cleanup_feature`):

### Updating feature.xml and feature.properties

**Important**: `feature.xml` and `feature.properties` must be kept synchronized:

1. **If you add a new placeholder in feature.xml** (e.g., `%newProperty`):
   ```xml
   <feature>
      <description>%newProperty</description>
   </feature>
   ```
   Add the property to `sandbox_junit_cleanup_feature/feature.properties`:
   ```properties
   newProperty=My new description text
   ```

2. **If you remove a placeholder from feature.xml**:
   - Remove the corresponding property from feature.properties

3. **If you rename a placeholder**:
   - Update both the feature.xml reference AND the property name in feature.properties

### Common Feature Properties

The feature.properties file should always contain:
- `description` - Feature description
- `copyright` - Copyright notice
- `license` - Full license text
- `licenseURL` - License URL

**Why This Matters:**
- Missing properties cause build failures
- Properties are used in Eclipse update sites and installation dialogs
- Enables internationalization support

**Verification:**
After modifying feature files, always run:
```bash
mvn clean verify -pl sandbox_junit_cleanup_feature
```

## License

All contributions must include the Eclipse Public License 2.0 header:

```java
/*******************************************************************************
 * Copyright (c) 2025 Your Name.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Your Name - initial implementation
 *******************************************************************************/
```
