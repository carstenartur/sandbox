# Test Commons - Architecture

## Overview

The **Test Commons** module provides shared test infrastructure and utilities for all sandbox cleanup test modules. It contains JUnit 5 extensions, test fixtures, and helper classes that enable comprehensive testing of Eclipse JDT cleanup implementations across multiple Java versions.

## Purpose

- Provide reusable test infrastructure for all cleanup test modules
- Manage temporary Eclipse projects and workspaces for testing
- Support multi-version Java compliance testing (Java 8, 9, 10, 17, 18, 22)
- Simplify cleanup testing with helper methods and assertions
- Ensure consistent test setup across all test modules

## Core Components

### 1. AbstractEclipseJava

**Location**: `org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava`

**Purpose**: Base JUnit 5 extension that provides the test infrastructure for Eclipse JDT cleanup and refactoring tests.

**Responsibilities**:
- Create and configure temporary Eclipse Java projects for each test
- Set up Java compiler compliance level based on version
- Manage cleanup profiles and options for testing
- Provide helper methods for executing refactorings
- Clean up resources after test execution
- Handle workspace operations safely

**Key Methods**:
```java
// Project setup
IJavaProject createJavaProject(String projectName) throws CoreException
void setupProject(IJavaProject project, String complianceLevel) throws CoreException

// Cleanup execution
void assertRefactoringResultAsExpected(ICompilationUnit cu, String expected)
void assertRefactoringHasChange(ICompilationUnit cu)
void applyCleanup(ICompilationUnit cu, Map<String, String> options)

// Test helpers
ICompilationUnit createCU(IJavaProject project, String packageName, String source)
String getTestSource(String testName)
```

**Extension Pattern**:
```java
@RegisterExtension
static AbstractEclipseJava context = new EclipseJava17();

@Test
void testMyCleanup() {
    ICompilationUnit cu = context.createCU(...);
    context.assertRefactoringResultAsExpected(cu, expected);
}
```

### 2. Version-Specific Extensions

**Purpose**: Provide Java version-specific test environments

**Available Versions**:
- `EclipseJava8` - Java 8 (1.8) compliance
- `EclipseJava9` - Java 9 compliance
- `EclipseJava10` - Java 10 compliance
- `EclipseJava17` - Java 17 compliance
- `EclipseJava18` - Java 18 compliance
- `EclipseJava22` - Java 22 compliance

**Implementation Pattern**:
```java
public class EclipseJava17 extends AbstractEclipseJava {
    public EclipseJava17() {
        super("17", JavaCore.VERSION_17);
    }
}
```

**Usage in Tests**:
```java
@ParameterizedTest
@EnumSource(Java.class)
void testAcrossVersions(Java java) {
    AbstractEclipseJava context = java.getContext();
    // Test code that runs for each Java version
}
```

### 3. TestOptions

**Location**: `org.eclipse.jdt.testplugin.TestOptions`

**Purpose**: Provide default JDT options for testing

**Functionality**:
- Configure default compiler options
- Set up workspace preferences
- Provide consistent test environment

## Test Infrastructure Pattern

### JUnit 5 Extension Model

The test infrastructure uses JUnit 5's extension API:

```java
public abstract class AbstractEclipseJava 
    implements BeforeEachCallback, AfterEachCallback {
    
    @Override
    public void beforeEach(ExtensionContext context) {
        // Create temporary project
        // Set up classpath
        // Configure Java version
    }
    
    @Override
    public void afterEach(ExtensionContext context) {
        // Delete project
        // Clean up resources
        // Reset workspace state
    }
}
```

### Project Lifecycle

```
Test Start
   ↓
beforeEach()
   ├─ Create temporary IJavaProject
   ├─ Configure Java compliance level
   ├─ Set up classpath with runtime stubs
   └─ Create source folder structure
   ↓
Test Execution
   ├─ Create compilation units
   ├─ Apply cleanups
   └─ Assert results
   ↓
afterEach()
   ├─ Delete project from workspace
   ├─ Clean up file system
   └─ Reset Eclipse state
   ↓
Test End
```

## Cleanup Testing Pattern

### Standard Test Flow

```java
@Test
void testEncodingCleanup() {
    // 1. Create compilation unit with test code
    ICompilationUnit cu = context.createCU(project, "test",
        "public class Test {" +
        "  void method() throws Exception {" +
        "    FileReader r = new FileReader(\"file.txt\");" +
        "  }" +
        "}");
    
    // 2. Configure cleanup options
    Map<String, String> options = new HashMap<>();
    options.put("encoding.cleanup", "true");
    
    // 3. Apply cleanup
    context.applyCleanup(cu, options);
    
    // 4. Assert expected result
    String expected =
        "public class Test {" +
        "  void method() throws Exception {" +
        "    FileReader r = new FileReader(\"file.txt\", StandardCharsets.UTF_8);" +
        "  }" +
        "}";
    context.assertRefactoringResultAsExpected(cu, expected);
}
```

### Multi-Version Testing

```java
@ParameterizedTest
@EnumSource(value = Java.class, names = {"JAVA8", "JAVA17", "JAVA22"})
void testAcrossVersions(Java java) {
    AbstractEclipseJava context = java.getContext();
    
    // Test code runs for each specified Java version
    ICompilationUnit cu = context.createCU(...);
    context.applyCleanup(cu, options);
    
    // Expected result may vary by Java version
    String expected = getExpectedForVersion(java);
    context.assertRefactoringResultAsExpected(cu, expected);
}
```

## Helper Methods

### Compilation Unit Creation

```java
// Create CU from string
ICompilationUnit cu = context.createCU(
    project,
    "org.example",
    "MyClass.java",
    sourceCode
);

// Load CU from test resource file
String source = context.getTestSource("TestClass");
ICompilationUnit cu = context.createCU(project, "test", "TestClass.java", source);
```

### Refactoring Execution

```java
// Apply cleanup and assert change occurred
context.assertRefactoringHasChange(cu);

// Apply cleanup and assert specific result
context.assertRefactoringResultAsExpected(cu, expectedSource);

// Apply cleanup with custom options
Map<String, String> options = new HashMap<>();
options.put(CleanUpConstants.ORGANIZE_IMPORTS, "true");
context.applyCleanup(cu, options);
```

### Assertion Helpers

```java
// Assert compilation unit matches expected source
context.assertEquals(expectedSource, cu.getSource());

// Assert no compilation errors
context.assertNoCompilationErrors(cu);

// Assert specific cleanup was applied
context.assertCleanupApplied(cu, "encoding.cleanup");
```

## Workspace Management

### Project Setup

```java
// Create Java project with specific version
IJavaProject project = context.createJavaProject("TestProject");
context.setupProject(project, JavaCore.VERSION_17);

// Add source folder
IPackageFragmentRoot sourceFolder = project.getPackageFragmentRoot(
    project.getProject().getFolder("src")
);

// Add libraries to classpath
context.addLibrary(project, "junit-5.jar");
```

### Resource Cleanup

```java
// Delete project after test
@AfterEach
void cleanup() {
    if (project != null && project.exists()) {
        project.getProject().delete(true, true, null);
    }
}

// Clean workspace between tests
context.cleanWorkspace();
```

## Java Version Support

### Version-Specific Features

Each Java version may have different API availability:

| Java Version | Key Features Available |
|--------------|------------------------|
| Java 8 | Lambdas, streams, default methods |
| Java 9 | Module system, private interface methods |
| Java 10 | Local variable type inference (var) |
| Java 17 | Sealed classes, pattern matching, records |
| Java 18 | Code snippets in Javadoc |
| Java 22 | Unnamed patterns, string templates preview |

### Compliance Configuration

```java
// Set Java version for project
Map<String, String> options = new HashMap<>();
options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_17);
options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_17);
project.setOptions(options);
```

### Runtime Stubs

Test projects use runtime stubs (lightweight JRE replacements) for:
- Faster test execution
- Consistent test environment
- Version-specific API availability

## Design Patterns

### Extension Point Pattern

The infrastructure uses JUnit 5 extension points:
- `BeforeEachCallback` - Project setup before each test
- `AfterEachCallback` - Cleanup after each test
- `ParameterResolver` - Inject test context into test methods

### Template Method Pattern

`AbstractEclipseJava` provides template methods that subclasses can override:

```java
protected void customizeProject(IJavaProject project) {
    // Subclasses can customize project setup
}

protected Map<String, String> getDefaultOptions() {
    // Subclasses can provide version-specific defaults
}
```

### Factory Pattern

Version-specific extensions act as factories:

```java
Java.JAVA17.getContext()  // Returns EclipseJava17 instance
```

## Integration with Test Modules

### Test Module Structure

Each test module (e.g., `sandbox_encoding_quickfix_test`) uses test commons:

```
sandbox_encoding_quickfix_test
├── pom.xml (depends on sandbox_test_commons)
├── src/org/sandbox/jdt/ui/tests/quickfix/
│   └── Java10/
│       └── ExplicitEncodingCleanUpTest.java
│           └── Uses EclipseJava10 extension
```

### Dependency Declaration

In test module's `pom.xml` or `MANIFEST.MF`:

```xml
<dependency>
    <groupId>org.sandbox</groupId>
    <artifactId>sandbox_test_commons</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

### Example Test Class

```java
public class EncodingCleanUpTest {
    @RegisterExtension
    static AbstractEclipseJava context = new EclipseJava17();
    
    @Test
    void testFileReaderTransformation() {
        ICompilationUnit cu = context.createCU(...);
        context.assertRefactoringResultAsExpected(cu, expected);
    }
}
```

## Package Structure

```
org.sandbox.jdt.ui.tests.quickfix.rules
├── AbstractEclipseJava           # Base extension
├── EclipseJava8                 # Java 8 extension
├── EclipseJava9                 # Java 9 extension
├── EclipseJava10                # Java 10 extension
├── EclipseJava17                # Java 17 extension
├── EclipseJava18                # Java 18 extension
└── EclipseJava22                # Java 22 extension

org.eclipse.jdt.testplugin
└── TestOptions                   # Default options
```

## Best Practices

### Test Isolation

Each test should be independent:
```java
@Test
void testA() {
    // Create fresh compilation unit
    // Don't reuse from previous test
}

@Test
void testB() {
    // Create fresh compilation unit
    // State from testA is cleaned up
}
```

### Resource Management

Always clean up resources:
```java
@AfterEach
void cleanup() throws Exception {
    if (project != null) {
        project.getProject().delete(true, true, null);
    }
}
```

### Version-Specific Testing

Test behavior across supported Java versions:
```java
@ParameterizedTest
@EnumSource(Java.class)
void testFeature(Java java) {
    // Test runs for each Java version
}
```

## Performance Considerations

### Test Execution Speed

- Project creation is expensive (~100-200ms per test)
- Use `@BeforeAll` for shared setup when possible
- Minimize number of compilation units created
- Clean up promptly to free memory

### Optimization Tips

```java
// Reuse project for multiple tests if safe
@RegisterExtension
static AbstractEclipseJava context = new EclipseJava17();

@BeforeAll
static void setupProject() {
    // One-time setup
}

@Test
void test1() { /* reuses project */ }

@Test  
void test2() { /* reuses project */ }
```

## Debugging Support

### Verbose Logging

Enable verbose logging for debugging:
```java
context.setVerbose(true);  // Log all operations
```

### Project Inspection

Keep project after test for inspection:
```java
@AfterEach
void cleanup() {
    // Comment out for debugging
    // project.getProject().delete(true, true, null);
}
```

### Compilation Error Handling

```java
try {
    context.assertRefactoringResultAsExpected(cu, expected);
} catch (AssertionError e) {
    System.out.println("Actual source:");
    System.out.println(cu.getSource());
    System.out.println("Compilation problems:");
    context.printCompilationProblems(cu);
    throw e;
}
```

## Eclipse JDT Correspondence

This test infrastructure is inspired by Eclipse JDT's own test framework:

**Sandbox**: `org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava`  
**Eclipse JDT**: `org.eclipse.jdt.ui.tests.quickfix.CleanUpTestCase`

The architecture can be ported to Eclipse JDT by:
1. Adapting to Eclipse's test plugin structure
2. Using Eclipse's existing test utilities
3. Integrating with Eclipse's CI infrastructure

## References

- [JUnit 5 Extension Model](https://junit.org/junit5/docs/current/user-guide/#extensions)
- [Eclipse JDT Test Plugin](https://github.com/eclipse-jdt/eclipse.jdt.ui/tree/master/org.eclipse.jdt.ui.tests)
- [Eclipse Test Framework](https://wiki.eclipse.org/Eclipse/Testing)

## Usage in Other Modules

Test commons is used by all test modules:
- `sandbox_encoding_quickfix_test`
- `sandbox_functional_converter_test`
- `sandbox_jface_cleanup_test`
- `sandbox_junit_cleanup_test`
- `sandbox_platform_helper_test`
- `sandbox_tools_test`
- `sandbox_xml_cleanup_test`
- `sandbox_method_reuse_test`
- `sandbox_common_test`
- `sandbox_usage_view_test`
- `sandbox_extra_search_test`
- `sandbox_triggerpattern_test`

Each test module declares a dependency on test_commons and uses the JUnit 5 extensions to create comprehensive test suites for their cleanup implementations.
