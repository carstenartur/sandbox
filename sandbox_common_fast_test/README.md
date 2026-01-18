# sandbox_common_fast_test

## Overview

This module provides fast, lightweight unit tests for `sandbox_common` that run without requiring the full Eclipse/OSGi runtime. Unlike `sandbox_common_test` which uses `eclipse-test-plugin` packaging and Tycho's OSGi test runner, this module uses standard Maven `jar` packaging and Maven Surefire for test execution.

## Purpose

The primary goal is to provide:

1. **Fast feedback loop**: Tests run in seconds instead of minutes
2. **CI-friendly execution**: Can run without complex Tycho/target platform setup
3. **GitHub Copilot compatible**: Automated agents can run these tests easily
4. **Development velocity**: Quick unit tests during development

## Architecture

### Maven Configuration

- **Packaging**: `jar` (standard Maven, not `eclipse-test-plugin`)
- **Test Runner**: Maven Surefire Plugin
- **Dependencies**: Direct dependencies from Maven Central, not P2 repositories

### Dependencies

The module depends on:

- `org.eclipse.jdt:org.eclipse.jdt.core` - JDT Core from Maven Central
- `org.eclipse.platform:org.eclipse.core.runtime` - Eclipse Core Runtime
- `org.eclipse.platform:org.eclipse.core.resources` - Eclipse Resources
- `org.eclipse.platform:org.eclipse.text` - Eclipse Text
- JUnit Jupiter 5.x - Testing framework

### What Can Be Tested Here

Tests that only use:
- JDT Core AST parsing (`ASTParser`, `CompilationUnit`, etc.)
- AST visitors and matchers
- Code pattern matching
- Simple utility classes

### What Cannot Be Tested Here

Tests that require:
- Eclipse workspace (`IWorkspace`, `IProject`)
- Eclipse UI components
- OSGi services
- Plugin lifecycle management
- Full IDE integration

For those cases, use the existing `sandbox_common_test` module.

## Running Tests

### Command Line

```bash
# Set Java 21 (required)
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Run tests
cd sandbox_common_fast_test
mvn test

# Or from the root
mvn test -pl sandbox_common_fast_test
```

### IDE

Tests can be run directly from any IDE (Eclipse, IntelliJ IDEA, VS Code) as standard JUnit 5 tests without any special configuration.

## Test Examples

### Simple AST Parsing

```java
@Test
public void testParseSimpleClass() {
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    String code = "public class HelloWorld { }";
    parser.setSource(code.toCharArray());
    
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
    parser.setCompilerOptions(options);
    
    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
    assertNotNull(cu);
}
```

### AST Visitor Pattern

```java
@Test
public void testFindEnhancedForLoops() {
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    String code = """
        public class Example {
            void process(List<String> items) {
                for (String item : items) {
                    System.out.println(item);
                }
            }
        }""";
    
    parser.setSource(code.toCharArray());
    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
    
    boolean[] found = {false};
    cu.accept(new ASTVisitor() {
        @Override
        public boolean visit(EnhancedForStatement node) {
            found[0] = true;
            return super.visit(node);
        }
    });
    
    assertTrue(found[0]);
}
```

## Benefits

### Speed Comparison

- **sandbox_common_test** (Tycho): ~2-3 minutes for full test execution
- **sandbox_common_fast_test** (Surefire): ~5-10 seconds for test execution

### Development Workflow

1. Write fast unit tests in `sandbox_common_fast_test` for quick iteration
2. Add integration tests in `sandbox_common_test` when workspace/UI features are needed
3. Run fast tests frequently during development
4. Run full integration tests before committing

## Extending This Approach

This proof of concept can be applied to other test modules:

1. **sandbox_cleanup_application_test**: Extract tests that don't need OSGi runtime
2. **Other _test modules**: Identify tests using only JDT Core APIs

### Guidelines for Porting Tests

A test is a good candidate for fast testing if it:
- Only parses Java code with `ASTParser`
- Uses AST visitors and matchers
- Tests utility methods without Eclipse services
- Doesn't use `ICompilationUnit`, `IWorkspace`, or UI components

A test should stay in the OSGi test module if it:
- Uses Eclipse workspace APIs
- Requires Eclipse UI components
- Tests plugin lifecycle or services
- Needs OSGi/P2 resolution

## Limitations

1. **No access to Eclipse workspace**: Cannot test code that requires `IWorkspace`, `IProject`, etc.
2. **Limited Eclipse platform features**: Only core runtime and resources available
3. **Not a replacement**: OSGi integration tests are still essential for full testing

## Future Improvements

1. Add more tests from `sandbox_common_test` that only use AST parsing
2. Create similar fast test modules for other components
3. Document which tests should go where (fast vs. integration)
4. Optimize dependencies to reduce startup time further

## References

- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
- [Eclipse JDT Core on Maven Central](https://central.sonatype.com/artifact/org.eclipse.jdt/org.eclipse.jdt.core)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
