# JUnit Cleanup Plugin - TODO (Missing File Created)

This file was missing from the sandbox_junit_cleanup plugin. It has been created to satisfy the repository documentation requirements.

## Status Summary

**Current State**: Mature implementation with comprehensive architecture documented in ARCHITECTURE.md

### Completed
- ✅ Full JUnit 3/4 to JUnit 5 migration support
- ✅ Helper class extraction for maintainability
- ✅ Comprehensive test coverage
- ✅ Multiple plugin implementations for different migration scenarios
- ✅ @Test(expected) parameter migration to assertThrows() (TestExpectedJUnitPlugin)
- ✅ @Test(timeout) parameter migration to @Timeout (TestTimeoutJUnitPlugin)
- ✅ Parameter order correction for assertions (AssertOptimizationJUnitPlugin - expected/actual swapping)
- ✅ Quick Select presets for easier preference configuration (SandboxCodeTabPage)
- ✅ Parameterized test migration (ParameterizedTestJUnitPlugin)
- ✅ @Category to @Tag migration (CategoryJUnitPlugin)
- ✅ @Rule TemporaryFolder migration to @TempDir (RuleTemporaryFolderJUnitPlugin)
- ✅ @Rule ExpectedException migration to assertThrows() (RuleExpectedExceptionJUnitPlugin)
- ✅ **@Rule Timeout migration to class-level @Timeout** (RuleTimeoutJUnitPlugin)
- ✅ **@RewriteRule annotation framework** - Declarative transformation for simple annotation migrations

### In Progress
- None currently

### Recent Code Quality Improvements

#### HelperVisitor Fluent API Migration (✅ COMPLETED - January 2026)
**Priority**: Medium  
**Effort**: 2-3 hours  
**Status**: ✅ **COMPLETED**

Migrated all remaining plugins to use the modern HelperVisitor Fluent API, eliminating verbose static method patterns:

**Plugins Migrated**:
- ✅ `AssumeOptimizationJUnitPlugin` - Migrated from `callMethodInvocationVisitor()` to `forMethodCalls()`
- ✅ `AssertOptimizationJUnitPlugin` - Consolidated multiple `forMethodCall()` to `forMethodCalls()`
- ✅ `RuleTestnameJUnitPlugin` - Migrated from `callFieldDeclarationVisitor()` to `forField()`
- ✅ `RuleExternalResourceJUnitPlugin` - Migrated from `callFieldDeclarationVisitor()` to `forField()`
- ✅ `RunWithJUnitPlugin` - Migrated from `callSingleMemberAnnotationVisitor()` to `forAnnotation()`

**Already Using Fluent API** (no changes needed):
- ✅ `AssertJUnitPlugin` - Uses `forMethodCalls().andStaticImports().andImportsOf()`
- ✅ `AssumeJUnitPlugin` - Uses `forMethodCalls().andStaticImports().andImportsOf()`
- ✅ `RuleTemporayFolderJUnitPlugin` - Uses `forField().withAnnotation().ofType()`
- ✅ `CategoryJUnitPlugin` - Uses `forAnnotation()`
- ✅ `TestExpectedJUnitPlugin` - Uses `forAnnotation()`

**Benefits**:
- Reduced code duplication by consolidating multiple visitor calls
- Improved code readability with fluent method chaining
- Consistent API usage across all plugins
- Easier to understand and maintain
- Reduced from 4-8 separate visitor calls to 1-2 consolidated Fluent API chains per plugin

**Migration Pattern**:
```java
// Old verbose pattern
HelperVisitor.callMethodInvocationVisitor(CLASS, "method1", cu, holder, processed, callback);
HelperVisitor.callMethodInvocationVisitor(CLASS, "method2", cu, holder, processed, callback);

// New Fluent API
HelperVisitor.forMethodCalls(CLASS, Set.of("method1", "method2"))
    .in(cu)
    .excluding(processed)
    .processEach(holder, callback);
```

### Pending
- [ ] @Rule ErrorCollector migration to multiple assertions
- [ ] Additional JUnit 5 features support
- [ ] Performance optimization for large test suites
- [ ] Enhanced error reporting
- [ ] **@RewriteRule enhancements**:
  - [ ] Support for multi-placeholder patterns (e.g., method invocations with multiple parameters)
  - [ ] Support for NormalAnnotation with named parameters
  - [ ] Support for qualified annotation names in replacement patterns

## Recent Enhancements

### @RewriteRule Annotation Framework (✅ COMPLETED)
**Priority**: High  
**Effort**: 6-8 hours  
**Status**: ✅ **COMPLETED**

Added declarative `@RewriteRule` annotation to eliminate boilerplate in simple annotation migration plugins:

**Problem Solved**:
- All TriggerPattern plugins had nearly identical `process2Rewrite()` implementations (~15-20 lines of boilerplate)
- Only differences: annotation names and import statements
- High risk of copy-paste errors

**Implementation**:
- Created `@RewriteRule` annotation in `sandbox_common/src/org/sandbox/jdt/triggerpattern/api/RewriteRule.java`
- Extended `TriggerPatternCleanupPlugin` with default `process2Rewrite()` implementation
- Parses `replaceWith` pattern at runtime to create appropriate AST nodes
- Automatically handles import/static import management
- Supports placeholder preservation (e.g., `@Ignore($value)` → `@Disabled($value)`)

**Usage Example**:
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
    // process2Rewrite() now automatic - only getPreview() needed!
}
```

**Benefits**:
- Reduced plugin code from ~100 lines to ~80 lines (20% reduction per plugin)
- Declarative, self-documenting transformations
- Eliminated copy-paste errors
- Foundation for future code generation tooling

**Current Limitations** (documented in code):
- Only simple (unqualified) annotation names supported
- Only single placeholder patterns supported
- NormalAnnotation with named parameters requires custom implementation

**Plugins Using @RewriteRule**:
- `BeforeJUnitPlugin` - @Before → @BeforeEach
- `AfterJUnitPlugin` - @After → @AfterEach

**Future Enhancements** (see Pending section):
- Multi-placeholder support for complex transformations
- NormalAnnotation support
- Qualified annotation name support

### Quick Select Presets (✅ COMPLETED)
**Priority**: High  
**Effort**: 2-3 hours  
**Status**: ✅ **COMPLETED**

Added Quick Select combo box to JUnit cleanup preferences to allow users to select predefined groups of cleanup options:
- **Full Migration**: Enables all JUnit 4 to 5 migration options (except optimizations)
- **Annotations Only**: Safe migration of annotations (@Test, @Before, @After, @BeforeClass, @AfterClass, @Ignore)
- **Lifecycle Only**: Just lifecycle annotations (@Before, @After, @BeforeClass, @AfterClass)
- **Assertions Only**: Migrate Assert → Assertions
- **Rules Only**: Migrate JUnit 4 Rules (TemporaryFolder, TestName, ExternalResource, Timeout)

**Benefits**:
- Significantly reduces time to configure JUnit migration preferences
- Reduces user errors when selecting related options
- Provides guided migration paths for different use cases
- Users can still customize individual options after selecting a preset

**Implementation**:
- Added combo box widget in SandboxCodeTabPage.java
- Implemented applyQuickSelection() method to programmatically set checkbox states
- Added internationalized message strings for all preset options
- Main JUNIT_CLEANUP checkbox is automatically enabled when any preset is selected

## Priority Tasks

### 1. Parameterized Test Migration
**Priority**: Medium  
**Effort**: 10-12 hours  
**Status**: ✅ **COMPLETED**

Support for migrating JUnit 4 parameterized tests to JUnit 5 `@ParameterizedTest` has been implemented:
- ✅ Detects JUnit 4 `@RunWith(Parameterized.class)` tests
- ✅ Transforms to `@ParameterizedTest` with appropriate source annotations
- ✅ Handles parameter providers and method sources
- ✅ Updates test method signatures

**Implementation**: `ParameterizedTestJUnitPlugin` is fully implemented and registered in `JUnitCleanUpFixCore`.

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
- ~~`@Rule Timeout` → `@Timeout` annotation~~ ✅ **COMPLETED**
- `ErrorCollector` → multiple assertions - **PENDING**
- Custom rules → extension implementations - **PENDING**

**Status**: 
- ✅ TemporaryFolder migration fully implemented
- ✅ ExpectedException migration fully implemented (handles basic expect() and expectMessage() patterns; cause/matchers not yet supported)
- ✅ **Timeout Rule migration fully implemented** (RuleTimeoutJUnitPlugin)
  - Handles @Rule and @ClassRule variants
  - Extracts timeout values from Timeout.seconds(), Timeout.millis(), and constructor patterns
  - Converts to class-level @Timeout annotation with proper TimeUnit
  - Comprehensive test coverage in MigrationRulesToExtensionsTest
- ❌ ErrorCollector migration not yet implemented
- ❌ Custom rules migration not yet implemented

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
**Status**: ✅ **PARTIALLY COMPLETED** - assumeThat migration

Handle Hamcrest matcher migration:
- ✅ `Assume.assumeThat()` with Hamcrest matchers now correctly uses `org.hamcrest.junit.MatcherAssume`
- ✅ Detects Hamcrest matchers by checking if parameters implement `org.hamcrest.Matcher` interface
- ✅ Avoids adding unused `org.junit.jupiter.api.Assumptions` import when only Hamcrest assumeThat is used
- [ ] `Assert.assertThat()` with Hamcrest matchers (already uses `org.hamcrest.MatcherAssert` - see AssertJUnitPlugin)
- [ ] Update other Hamcrest imports as needed
- [ ] Additional Hamcrest integration improvements

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

### Assertion Parameter Order Correction
**Priority**: High  
**Effort**: 8-10 hours  
**Status**: ✅ **COMPLETED**

Correct parameter order in assertion methods to follow JUnit best practices (expected, actual):
- `assertEquals(actual, EXPECTED)` → `assertEquals(EXPECTED, actual)`
- `assertArrayEquals(getArray(), new int[]{1,2,3})` → `assertArrayEquals(new int[]{1,2,3}, getArray())`
- `assertSame(getInstance(), SINGLETON)` → `assertSame(SINGLETON, getInstance())`
- `assertNotSame(getObject(), EXPECTED)` → `assertNotSame(EXPECTED, getObject())`
- `assertIterableEquals(getList(), List.of(1,2,3))` → `assertIterableEquals(List.of(1,2,3), getList())`
- `assertLinesMatch(getLines(), List.of("a","b"))` → `assertLinesMatch(List.of("a","b"), getLines())`

**Implementation Notes**:
- Implemented in AssertOptimizationJUnitPlugin
- Detects when second parameter is constant but first is not
- Supports literals, final fields, enum constants, array literals, and collection factory methods
- Handles both JUnit 4 (message first) and JUnit 5 (message last) parameter orders
- Methods supported: assertEquals, assertNotEquals, assertArrayEquals, assertSame, assertNotSame, assertIterableEquals, assertLinesMatch

**Constant Detection**:
- Number, String, Boolean, Character, Null literals
- Type literals (e.g., MyClass.class)
- Final fields and static fields
- Enum constants
- Array creation expressions with constant initializers
- Collection factory methods: List.of(), Set.of(), Arrays.asList(), Map.of()
- Method calls on literals: "test".getBytes()

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
