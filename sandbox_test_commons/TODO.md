# Test Commons - TODO

## Status Summary

**Current State**: Stable infrastructure providing test support for all cleanup test modules

### Completed
- ✅ JUnit 5 extension framework for cleanup testing
- ✅ Multi-version Java support (Java 8, 9, 10, 17, 18, 22)
- ✅ Project lifecycle management (setup/teardown)
- ✅ Compilation unit creation and manipulation helpers
- ✅ Refactoring execution and assertion helpers
- ✅ Workspace management utilities
- ✅ Integration with all test modules

### In Progress
- 🔄 Documentation (ARCHITECTURE.md complete, this TODO in progress)

### Pending
- [ ] Performance optimization for test execution
- [ ] Enhanced debugging support
- [ ] Test result reporting
- [ ] Additional assertion helpers
- [ ] Test data management utilities

## Priority Tasks

### 1. Performance Optimization
**Priority**: Medium  
**Effort**: 6-8 hours

Improve test execution speed by optimizing project creation and cleanup:

**Current Performance**:
- Project creation: ~100-200ms per test
- Cleanup overhead: ~50-100ms per test
- Total overhead per test: ~150-300ms

**Optimization Strategies**:
1. **Project Pooling**: Reuse projects across tests
   ```java
   // Maintain pool of pre-created projects
   static ProjectPool pool = new ProjectPool(5);
   
   @BeforeEach
   void setup() {
       project = pool.acquire();
       // Configure for test
   }
   
   @AfterEach
   void cleanup() {
       pool.release(project);  // Reset and return to pool
   }
   ```

2. **Lazy Initialization**: Only create resources when needed
3. **Parallel Test Execution**: Support concurrent test runs
4. **In-Memory Workspace**: Use in-memory file system for tests

**Expected Improvement**: 50-70% reduction in test overhead

### 2. Enhanced Debugging Support
**Priority**: High  
**Effort**: 4-6 hours

Add debugging utilities to help diagnose test failures:

**Features**:
1. **Diff Visualization**: Show side-by-side diff of expected vs actual
   ```java
   context.assertWithDiff(expected, actual);
   // Displays: 
   // Expected | Actual
   // ---------|-------
   // line1    | line1
   // line2    | LINE2  <-- Difference highlighted
   ```

2. **AST Inspection**: Dump AST structure for debugging
   ```java
   context.dumpAST(cu);  // Print AST tree
   context.compareAST(cu1, cu2);  // Compare AST structures
   ```

3. **Compilation Problem Reporter**:
   ```java
   context.reportProblems(cu);
   // Output:
   // ERROR at line 5: Type mismatch
   // WARNING at line 12: Unused import
   ```

4. **Step-by-Step Execution**: Trace cleanup execution
   ```java
   context.setDebugMode(true);
   context.applyCleanup(cu, options);
   // Logs each cleanup step
   ```

**Benefits**:
- Faster debugging of test failures
- Better understanding of cleanup behavior
- Easier contribution for new developers

### 3. Test Data Management
**Priority**: Low  
**Effort**: 6-8 hours

Add utilities for managing test data and resources:

**Features**:
1. **Test Resource Loading**:
   ```java
   // Load test code from resource files
   String source = context.loadResource("testcases/encoding/FileReader.java");
   String expected = context.loadResource("testcases/encoding/FileReader.expected");
   ```

2. **Test Data Generation**:
   ```java
   // Generate test compilation units programmatically
   CUBuilder builder = context.builder()
       .addClass("Test")
       .addMethod("void test()", "System.out.println(\"test\");")
       .build();
   ```

3. **Test Case Repository**:
   - Organize test cases in structured format
   - Support parameterized tests with data files
   - Enable test case reuse across modules

**Benefits**:
- Cleaner test code
- Easier test maintenance
- Reusable test cases

### 4. Additional Assertion Helpers
**Priority**: Medium  
**Effort**: 4-5 hours

Add more assertion methods for common test scenarios:

**New Assertions**:
```java
// Assert cleanup produced any change
context.assertChanged(cu);

// Assert specific AST node types present/absent
context.assertContainsNode(cu, MethodInvocation.class);
context.assertNotContainsNode(cu, ClassInstanceCreation.class);

// Assert import statements
context.assertImportsContain(cu, "java.nio.charset.StandardCharsets");
context.assertImportsNotContain(cu, "java.io.UnsupportedEncodingException");

// Assert compilation errors
context.assertNoErrors(cu);
context.assertErrorCount(cu, 0);
context.assertWarningCount(cu, 2);

// Assert code patterns
context.assertMatches(cu, "FileReader.*StandardCharsets");
context.assertNotMatches(cu, "new FileReader\\(.*\\)");

// Assert method signatures
context.assertMethodExists(cu, "void test()");
context.assertMethodCount(cu, "test", 1);
```

**Benefits**:
- More expressive tests
- Better failure messages
- Reduced boilerplate in tests

### 5. Test Result Reporting
**Priority**: Low  
**Effort**: 6-8 hours

Generate comprehensive test reports:

**Features**:
1. **Coverage Report**: Show which cleanups are tested
2. **Performance Report**: Track test execution times
3. **Failure Analysis**: Aggregate common failure patterns
4. **Version Compatibility Report**: Show Java version test coverage

**Report Formats**:
- HTML: Interactive report with drill-down
- JSON: Machine-readable for CI integration
- XML: JUnit-compatible format

**Benefits**:
- Better visibility into test health
- Identify undertested areas
- Track performance regressions

### 6. Support for Additional Java Versions
**Priority**: Low  
**Effort**: 2-3 hours per version

Add support for additional Java versions as they are released:

**Future Versions**:
- `EclipseJava23` - Java 23 (when released)
- `EclipseJava24` - Java 24 (when released)
- `EclipseJava25` - Java 25 (when released)

**Implementation**:
```java
public class EclipseJava23 extends AbstractEclipseJava {
    public EclipseJava23() {
        super("23", JavaCore.VERSION_23);
    }
}
```

**Maintenance**: Update as new Java versions are supported by Eclipse

## Known Issues

### 1. Slow Test Execution
**Severity**: Medium

Project creation overhead makes tests slow (150-300ms per test).

**Workaround**: Reuse projects where safe, use `@BeforeAll` for shared setup

**Potential Fix**: Implement project pooling (see Priority Task #1)

### 2. Workspace Pollution
**Severity**: Low

Failed tests may leave projects in workspace if cleanup doesn't run.

**Workaround**: Manually clean workspace between test runs

**Potential Fix**: Add cleanup hook that runs even on test failure:
```java
@AfterEach(onFailure = CLEANUP)
void cleanup() {
    // Always cleanup, even on test failure
}
```

### 3. Limited Platform Support
**Severity**: Low

Tests only run on platforms supported by Eclipse (Windows, Linux, macOS).

**Workaround**: Use CI environment (GitHub Actions provides all platforms)

**Note**: This is an Eclipse platform limitation, not specific to test commons

### 4. No Parallel Execution
**Severity**: Low

Tests must run sequentially due to shared workspace.

**Workaround**: None currently

**Potential Fix**: 
- Use separate workspace per thread
- Implement thread-safe project management
- Enable JUnit 5 parallel execution

## Future Enhancements

### Test Fixtures Library
**Priority**: Medium  
**Effort**: 8-10 hours

Create library of reusable test fixtures:
- Common compilation unit templates
- Standard project configurations
- Typical cleanup scenarios
- Pre-configured test projects

### Test Recorder
**Priority**: Low  
**Effort**: 10-12 hours

Record cleanup transformations and generate test cases:
```java
// Start recording
context.startRecording();

// Perform manual cleanup in IDE
// ... user applies cleanup via Eclipse UI ...

// Stop recording and generate test
TestCase testCase = context.stopRecording();
testCase.save("testMyCleanup.java");
```

### Visual Test Debugger
**Priority**: Low  
**Effort**: 15-20 hours

Create Eclipse view for debugging tests:
- Show AST tree
- Highlight changes made by cleanup
- Step through cleanup operations
- Visualize before/after code

### Test Migration Assistant
**Priority**: Low  
**Effort**: 6-8 hours

Help migrate tests from JUnit 4 to JUnit 5 extension model:
- Detect old test pattern
- Suggest migration to extension
- Auto-generate extension code

## Testing Strategy

### Current Testing
- Used by all test modules (12+ modules)
- Hundreds of tests depend on test commons
- Indirect testing through usage

### Needed Direct Tests
- [ ] Unit tests for AbstractEclipseJava methods
- [ ] Integration tests for project lifecycle
- [ ] Performance tests for overhead measurement
- [ ] Compatibility tests for each Java version

### Test Coverage Goals
- 80% code coverage for test commons itself
- All public methods tested
- Error conditions handled
- Edge cases validated

## Performance Benchmarks

### Current Performance

| Operation | Time (ms) |
|-----------|-----------|
| Create project | 100-200 |
| Setup classpath | 50-100 |
| Create CU | 10-20 |
| Apply cleanup | 50-150 |
| Cleanup project | 50-100 |
| **Total per test** | **260-570** |

### Target Performance

| Operation | Target (ms) | Improvement |
|-----------|-------------|-------------|
| Create project | 10-20 | 90% |
| Setup classpath | 5-10 | 90% |
| Create CU | 5-10 | 50% |
| Apply cleanup | 30-100 | 33% |
| Cleanup project | 5-10 | 90% |
| **Total per test** | **55-150** | **73%** |

**Achievement Strategy**: Project pooling + lazy initialization + in-memory workspace

## Documentation Improvements

### Completed Documentation
- ✅ ARCHITECTURE.md: Design and implementation details
- ✅ TODO.md: This file

### Additional Documentation Needed
- [ ] API documentation (Javadoc for all public methods)
- [ ] Tutorial: Writing your first cleanup test
- [ ] Best practices guide
- [ ] Troubleshooting common test issues
- [ ] Migration guide from JUnit 4 to JUnit 5

## Eclipse JDT Contribution

### Contribution Value

Test commons provides valuable testing infrastructure that could benefit Eclipse JDT:
- Standardized test setup across cleanup implementations
- Multi-version testing support
- Reusable test utilities
- Comprehensive assertion helpers

### Prerequisites for Contribution
- [ ] Adapt to Eclipse test plugin structure
- [ ] Align with Eclipse naming conventions
- [ ] Remove sandbox-specific code
- [ ] Add comprehensive Javadoc
- [ ] Get community feedback

### Porting Checklist
- [ ] Replace `org.sandbox` with `org.eclipse` in package names
- [ ] Move to `org.eclipse.jdt.ui.tests` project
- [ ] Integrate with Eclipse CI infrastructure
- [ ] Update to use Eclipse test utilities where available
- [ ] Submit to Eclipse Gerrit for review

## Technical Debt

### Current Technical Debt
1. **Limited Javadoc**: Many methods lack comprehensive documentation
2. **No Direct Tests**: Test commons itself is not directly tested
3. **Hard-coded Paths**: Some paths are hard-coded instead of configurable
4. **No Error Recovery**: Limited recovery from setup failures

### Refactoring Needs
1. **Extract Project Manager**: Separate project lifecycle management
2. **Extract CU Builder**: Create fluent API for building compilation units
3. **Extract Assertion Library**: Separate assertion helpers
4. **Add Configuration Object**: Replace method parameters with config object

### Code Quality Improvements
- Add comprehensive Javadoc
- Improve error messages
- Add parameter validation
- Extract constants
- Reduce method complexity

## Community Feedback

### Questions to Community
1. Is project creation performance acceptable?
2. What additional assertion methods would be useful?
3. Should we support parallel test execution?
4. What debugging features are most needed?

### Feature Requests
(To be populated based on user feedback)

## References

- [JUnit 5 Extensions](https://junit.org/junit5/docs/current/user-guide/#extensions)
- [Eclipse JDT Core Tests](https://github.com/eclipse-jdt/eclipse.jdt.core/tree/master/org.eclipse.jdt.core.tests.compiler)
- [Eclipse JDT UI Tests](https://github.com/eclipse-jdt/eclipse.jdt.ui/tree/master/org.eclipse.jdt.ui.tests)

## Contact

For questions, suggestions, or contributions related to test commons:
- Open an issue in the repository
- Submit a pull request
- Contact: See project contributors

## Dependencies

Test commons is a foundational module depended upon by:
- All `*_test` modules in the sandbox project
- Enables consistent testing across cleanup implementations
- Critical infrastructure for project quality assurance
