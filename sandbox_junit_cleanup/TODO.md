# JUnit Cleanup Plugin - TODO (Missing File Created)

This file was missing from the sandbox_junit_cleanup plugin. It has been created to satisfy the repository documentation requirements.

## Status Summary

**Current State**: Mature implementation with comprehensive architecture documented in ARCHITECTURE.md

### Completed
- ✅ Full JUnit 3/4 to JUnit 5 migration support
- ✅ Helper class extraction for maintainability
- ✅ Comprehensive test coverage
- ✅ Multiple plugin implementations for different migration scenarios
- ✅ @Test(expected) parameter migration to assertThrows()

### In Progress
- None currently

### Pending
- [ ] Additional JUnit 5 features support
- [ ] Performance optimization for large test suites
- [ ] Enhanced error reporting

## Priority Tasks

### 1. Parameterized Test Migration
**Priority**: Medium  
**Effort**: 10-12 hours

Add support for migrating JUnit 4 parameterized tests to JUnit 5 `@ParameterizedTest`:
- Detect JUnit 4 `@RunWith(Parameterized.class)` tests
- Transform to `@ParameterizedTest` with appropriate source annotations
- Handle parameter providers and method sources
- Update test method signatures

**Benefits**:
- More comprehensive JUnit 5 migration
- Cleaner test code with JUnit 5 parameterized features

### 2. Timeout Annotation Migration
**Priority**: Low  
**Effort**: 4-6 hours  
**Status**: ✅ **COMPLETED**

Migrate timeout specifications:
- JUnit 4 `@Test(timeout=...)` → JUnit 5 `@Timeout(...)`
- Update timeout values and units
- Handle TimeUnit conversions
- Optimize output: use SECONDS when timeout is divisible by 1000ms

### 2a. Test Expected Exception Migration
**Priority**: High  
**Effort**: 8-10 hours  
**Status**: ✅ **COMPLETED**

Migrate @Test(expected) parameter to assertThrows():
- JUnit 4 `@Test(expected = ExceptionClass.class)` → JUnit 5 `assertThrows(ExceptionClass.class, () -> {...})`
- Wraps entire method body in assertThrows lambda
- Removes expected parameter from @Test annotation
- Adds static import for assertThrows
- Works alongside timeout migration for combined parameters

**Implementation**: TestExpectedJUnitPlugin handles this transformation by:
- Detecting @Test annotations with expected parameter via NormalAnnotation visitor
- Extracting the exception TypeLiteral
- Creating lambda expression wrapping the method body
- Generating assertThrows method invocation
- Removing expected parameter (converts to marker annotation if only parameter)

### 3. Rule Migration Framework
**Priority**: High  
**Effort**: 12-15 hours

Generalize ExternalResourceRefactorer to handle other JUnit 4 rules:
- ~~`TemporaryFolder` → `@TempDir`~~ ✅ **COMPLETED**
- ~~`ExpectedException` → `assertThrows()`~~ ✅ **COMPLETED**
- `ErrorCollector` → multiple assertions
- Custom rules → extension implementations

**Status**: TemporaryFolder and ExpectedException migrations fully implemented. ExpectedException handles basic expect() and expectMessage() patterns. ExpectedException with cause/matchers not yet supported.

**Benefits**:
- Complete JUnit 4 to 5 migration capability
- Reduces manual migration effort significantly

### 4. Enhanced Reporting
**Priority**: Low  
**Effort**: 6-8 hours

Provide detailed migration reports:
- List all transformations applied
- Highlight manual interventions needed
- Show coverage of migration (% tests migrated)
- Export report in HTML/Markdown format

## Known Issues

None currently identified. The implementation is stable and well-tested.

## Future Enhancements

### AssertJ Integration
**Priority**: Low  
**Effort**: 8-10 hours

Optionally migrate JUnit assertions to AssertJ:
- `assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`
- Fluent assertion chains
- Better error messages

**Note**: This would be a separate optional cleanup, not part of core JUnit migration.

### Hamcrest Matcher Support
**Priority**: Low  
**Effort**: 6-8 hours

Handle Hamcrest matcher migration:
- Update imports from `org.hamcrest` as needed
- Ensure compatibility with JUnit 5
- Preserve matcher-based assertions

### Test Categorization
**Priority**: Low  
**Effort**: 4-6 hours  
**Status**: ✅ **COMPLETED**

Migrate JUnit 4 categories to JUnit 5 tags:
- `@Category(FastTests.class)` → `@Tag("FastTests")`
- Handles single categories on methods and classes
- Handles multiple categories: `@Category({Fast.class, Unit.class})` → `@Tag("Fast") @Tag("Unit")`
- Update test suite configurations
- Preserve category hierarchies

**Implementation Notes**:
- Implemented in CategoryJUnitPlugin
- Extracts simple class name from TypeLiteral
- Supports ArrayInitializer for multiple categories
- Creates multiple @Tag annotations for multiple categories
- Updates imports appropriately

## Testing Strategy

### Current Coverage
Comprehensive test suite in `sandbox_junit_cleanup_test`:
- Migration scenarios for all supported patterns
- Edge cases and error handling
- Integration with Eclipse test framework

### Future Testing
- Add tests for new rule migrations
- Performance tests for large test suites
- Regression tests for complex scenarios

## Eclipse JDT Contribution

### Contribution Potential
This plugin provides significant value for JUnit migration. Consider contributing to Eclipse or as standalone Eclipse plugin.

### Prerequisites
- [ ] Ensure all tests pass on multiple Eclipse versions
- [ ] Complete rule migration framework
- [ ] Add user documentation and migration guide
- [ ] Get community feedback on migration approach

### Porting Checklist
- [ ] Replace `org.sandbox` with `org.eclipse` in package names
- [ ] Move helper classes to appropriate Eclipse modules
- [ ] Register cleanups in Eclipse's extension points
- [ ] Submit to Eclipse Gerrit for review or publish as Eclipse Marketplace plugin

## Technical Debt

### Helper Class Organization
Current helper classes are well-organized. No significant debt identified.

### Test Organization
Tests are comprehensive but could benefit from:
- Parameterized tests to reduce duplication
- Shared test utilities for common patterns
- Better test categorization (unit vs integration)

## Performance Considerations

### Current Performance
Efficient for typical test suites. Single-pass AST traversal minimizes overhead.

### Optimization Opportunities
- Batch processing for multiple test files
- Parallel migration of independent test classes
- Caching of type bindings and resolved references

## References

- [JUnit 5 Migration Guide](https://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- See ARCHITECTURE.md for detailed design documentation

## Contact

For questions about JUnit cleanup or suggestions for improvements, please open an issue in the repository.
