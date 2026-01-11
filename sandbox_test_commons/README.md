# Test Commons Module

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Test Commons** module provides shared test infrastructure, utilities, and base classes used across all sandbox test modules. It centralizes common testing patterns to reduce duplication and ensure consistency in test implementations.

## Key Features

- 🧪 **Shared Test Base Classes** - Common base classes for test cases
- 🔧 **Test Utilities** - Helper methods for test setup and assertions
- 📦 **Mock Objects** - Reusable mock implementations
- 🎯 **AST Test Helpers** - Utilities for testing AST transformations
- 🔌 **JUnit 5 Integration** - Modern JUnit 5 test infrastructure

## Components

### Base Test Classes

Common base classes for different test scenarios:

#### `AbstractCleanUpTest`
Base class for cleanup transformation tests:
- Sets up Eclipse test workspace
- Provides before/after comparison utilities
- Handles AST parsing and rewriting
- Manages compilation units

#### `AbstractQuickFixTest`
Base class for quick fix tests:
- Sets up problem markers
- Invokes quick fixes
- Verifies fix results
- Handles multiple fix scenarios

### Test Utilities

#### AST Testing
- `ASTTestHelper` - Create and compare AST nodes
- `ASTMatcher` - Match AST patterns in tests
- `ASTNodeFactory` - Create test AST structures

#### Workspace Setup
- `WorkspaceHelper` - Create test projects
- `ProjectHelper` - Configure test projects
- `CompilationUnitHelper` - Create test Java files

#### Assertions
- `CleanUpAssertions` - Verify cleanup transformations
- `ASTAssertions` - Assert AST structure
- `SourceAssertions` - Compare source code

### Mock Objects

Reusable mock implementations:
- `MockCompilationUnit` - Test compilation unit
- `MockIFile` - Test file resource
- `MockIProject` - Test project
- `MockProgressMonitor` - Test progress monitoring

## Usage

### Extending Base Classes

```java
public class MyCleanUpTest extends AbstractCleanUpTest {
    
    @Test
    void testMyTransformation() {
        // Given
        String input = """
            public class Test {
                void method() {
                    // old code
                }
            }
            """;
        
        String expected = """
            public class Test {
                void method() {
                    // new code
                }
            }
            """;
        
        // When
        String actual = applyCleanUp(input, new MyCleanUp());
        
        // Then
        assertEqualCode(expected, actual);
    }
}
```

### Using Test Utilities

```java
public class MyTest {
    
    @Test
    void testASTTransformation() {
        // Create test AST
        AST ast = ASTTestHelper.createAST();
        MethodDeclaration method = ASTNodeFactory.createMethod(ast, "test");
        
        // Apply transformation
        MyTransformer transformer = new MyTransformer();
        transformer.transform(method);
        
        // Verify result
        ASTAssertions.assertHasAnnotation(method, "Test");
    }
}
```

### Using Mock Objects

```java
public class MyWorkspaceTest {
    
    @Test
    void testProjectOperation() {
        // Setup
        IProject project = new MockIProject("TestProject");
        IFile file = new MockIFile(project, "Test.java");
        
        // Execute
        MyOperation operation = new MyOperation();
        operation.execute(file);
        
        // Verify
        assertTrue(file.exists());
    }
}
```

## Test Patterns

### Cleanup Test Pattern

Standard pattern for testing cleanups:

1. **Given** - Define input code
2. **When** - Apply cleanup
3. **Then** - Assert expected output

```java
@Test
void testCleanup() {
    String input = "...";
    String expected = "...";
    String actual = applyCleanUp(input);
    assertEqualCode(expected, actual);
}
```

### Parameterized Test Pattern

Test multiple scenarios:

```java
@ParameterizedTest
@EnumSource(TestCase.class)
void testMultipleScenarios(TestCase testCase) {
    String actual = applyCleanUp(testCase.input());
    assertEqualCode(testCase.expected(), actual);
}
```

### AST Transformation Pattern

Test AST manipulations:

```java
@Test
void testASTTransform() {
    CompilationUnit cu = parseCode("...");
    ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
    
    // Apply transformation
    myTransform(cu, rewrite);
    
    // Verify
    String result = rewrite.rewriteAST().toString();
    assertEqualCode(expected, result);
}
```

## Dependencies

Test modules that depend on test_commons:
- `sandbox_encoding_quickfix_test`
- `sandbox_junit_cleanup_test`
- `sandbox_functional_converter_test`
- `sandbox_platform_helper_test`
- `sandbox_jface_cleanup_test`
- All other `*_test` modules

## JUnit 5 Support

The module uses JUnit 5 (Jupiter):

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
```

### Migration from JUnit 4

For tests still using JUnit 4:
- Use JUnit 5 vintage engine
- Or migrate to JUnit 5 API
- See [JUnit Cleanup](../sandbox_junit_cleanup/) for migration tool

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Test infrastructure design
- **[TODO](TODO.md)** - Planned test utilities
- **[HelperVisitor API Tests](../sandbox_common_test/TESTING.md)** - Example test documentation

## Testing the Tests

The test commons module itself has tests to verify test infrastructure:

```bash
xvfb-run --auto-servernum mvn test -pl sandbox_test_commons
```

## Best Practices

### 1. Reuse Base Classes
Extend provided base classes instead of reimplementing common setup.

### 2. Use Test Utilities
Leverage utilities instead of writing boilerplate test code.

### 3. Follow Patterns
Use established test patterns for consistency.

### 4. Document Test Intent
Clear test names and comments explain what's being tested.

### 5. Test One Thing
Each test should verify one specific behavior.

## Contributing

To add new test utilities:

1. **Identify Common Pattern**
   - Find repeated code across test modules

2. **Create Utility**
   - Implement in test_commons
   - Document usage

3. **Add Tests**
   - Test the utility itself

4. **Update Tests**
   - Refactor existing tests to use utility

See [TODO.md](TODO.md) for planned test infrastructure improvements.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related**: All `sandbox_*_test` modules depend on this infrastructure
