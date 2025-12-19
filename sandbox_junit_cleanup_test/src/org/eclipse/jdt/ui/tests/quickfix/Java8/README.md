# JUnit Migration Test Suite Organization

This directory contains tests for the JUnit 4→5 migration cleanup feature. The test suite has been reorganized into focused, aspect-based test classes for better clarity and maintainability.

## Test Class Organization

### By Migration Aspect

Each test class focuses on a specific aspect of JUnit migration:

#### `MigrationAssertionsTest.java`
Tests for assertion migrations from JUnit 4 to JUnit 5.
- **Coverage**: `Assert.*` → `Assertions.*` transformations
- **Key scenarios**:
  - `assertEquals`, `assertNotEquals` with parameter order changes (message moves to last)
  - `assertTrue`, `assertFalse` with and without messages
  - `assertNull`, `assertNotNull`
  - `assertSame`, `assertNotSame`
  - `assertArrayEquals` for array comparisons
  - `fail()` with and without messages
  - Floating point assertions with delta
  - Static imports (wildcard `.*` and explicit imports)

#### `MigrationAssumptionsTest.java`
Tests for assumption migrations.
- **Coverage**: `Assume.*` → `Assumptions.*` transformations
- **Key scenarios**:
  - `assumeTrue`, `assumeFalse` with parameter order changes
  - `assumeNotNull`
  - `assumeThat` with Hamcrest matchers → static import from `MatcherAssume`
  - Combined assumptions in single test

#### `MigrationLifecycleTest.java`
Tests for test lifecycle annotation migrations.
- **Coverage**: 
  - `@Before` → `@BeforeEach`
  - `@After` → `@AfterEach`
  - `@BeforeClass` → `@BeforeAll`
  - `@AfterClass` → `@AfterAll`
- **Key scenarios**:
  - Individual lifecycle method migrations
  - All lifecycle methods together in one class

#### `MigrationIgnoreTest.java`
Tests for ignored test migrations.
- **Coverage**: `@Ignore` → `@Disabled`
- **Key scenarios**:
  - `@Ignore` without message
  - `@Ignore("reason")` with message
  - Multiple ignored tests in same class

#### `MigrationRulesToExtensionsTest.java`
Tests for JUnit 4 Rules → JUnit 5 Extensions migrations.
- **Coverage**:
  - `TemporaryFolder` → `@TempDir` with `Path`
  - `TestName` → `TestInfo` with `@BeforeEach` initialization
  - *(Disabled)* Anonymous `ExternalResource` → Custom extensions (hash-based class names make exact matching brittle)
  - *(Disabled)* `@ClassRule` → static `@RegisterExtension` (hash-based class names make exact matching brittle)
  - `@Rule` → `@RegisterExtension`
- **Key scenarios**:
  - Nested/inner class resources
  - Multiple rules in same test class
  - Static vs instance rules
- **Note**: Anonymous ExternalResource migrations are covered by `JUnitCleanupCases.RuleAnonymousExternalResource` and `RuleNestedExternalResource` parameterized tests

#### `MigrationRunnersTest.java`
Tests for `@RunWith` migrations.
- **Coverage**:
  - `@RunWith(Suite.class)` → `@Suite` with `@SelectClasses`
  - `@RunWith(MockitoJUnitRunner.class)` → `@ExtendWith(MockitoExtension.class)` ✅ **Implemented**
  - `@RunWith(SpringRunner.class)` → `@ExtendWith(SpringExtension.class)` ✅ **Implemented**
  - *(Disabled)* `@RunWith(Parameterized.class)` → `@ParameterizedTest` with `@MethodSource`
- **Key scenarios**:
  - Framework extension migrations (Mockito, Spring)
  - Supports both old and new package names
- **Note**: Parameterized migration is complex and pending implementation

#### `MigrationExceptionsTest.java`
Tests for exception handling migrations.
- **Coverage** (all currently disabled):
  - `@Test(expected=Exception.class)` → `assertThrows()`
  - `ExpectedException` rule → `assertThrows()` with message validation
  - `ExpectedException.expectCause()` → `assertInstanceOf()` on cause
- **Note**: All tests disabled pending production code implementation

#### `MigrationTestAnnotationTest.java`
Tests for `@Test` annotation migrations.
- **Coverage**:
  - Basic `@Test` import change
  - *(Disabled)* `@Test(timeout=...)` → `@Timeout` annotation

#### `MigrationCombinationsTest.java`
Tests for complex scenarios combining multiple migration features.
- **Coverage**:
  - Full test class with all lifecycle methods, assertions, and `@Ignore`
  - `TemporaryFolder` + `TestName` rules combined
  - Suite with assertions and lifecycle methods
- **Purpose**: Validate that combinations of migrations work correctly together

## Legacy Test Files

### `JUnitMigrationCleanUpTest.java`
The original test orchestrator that uses parameterized tests with enum-based test cases.
- Contains integration tests with multiple cleanups enabled
- Tests multi-file scenarios (ExternalResource transformations across files)
- Tests edge cases like nested ExternalResource classes

### `JUnitCleanupCases.java`
Enum containing JUnit 4→5 migration test cases with given/expected pairs.
- **Cases**:
  - `PositiveCase`: Comprehensive migration with all features
  - `AlreadyJunit5Case`: No changes needed
  - `StaticImportCase`: Static import wildcard handling
  - `StaticExplicitImportCase`: Explicit static imports
  - `RuleAnonymousExternalResource`: Anonymous ExternalResource migration
  - `RuleNestedExternalResource`: Complex nested rules scenario
  - `TestnameRule`: Combined TemporaryFolder + TestName

### `JUnit3CleanupCases.java`
Enum containing JUnit 3→5 migration test cases (currently disabled).

## Test Naming Conventions

### Test Method Naming
All test methods follow the pattern: `migrates_<old>_to_<new>_<scenario>()`

Examples:
- `migrates_before_to_beforeEach()`
- `migrates_assertEquals_with_message_parameter_order()`
- `migrates_temporaryFolder_rule()`
- `migrates_test_expected_to_assertThrows()` (disabled)

### Disabled Tests
Tests for features not yet implemented in production code are marked with:
```java
@Disabled("Not yet implemented - <brief reason>")
@Test
public void migrates_<scenario>() {
    // Test implementation
}
```

## Test Organization Principles

### Single Responsibility
Each test method validates one specific migration scenario.

### Arrange-Act-Assert
Tests follow the AAA pattern:
1. **Arrange**: Create test compilation unit with JUnit 4 code
2. **Act**: Enable cleanup constants and run refactoring
3. **Assert**: Verify expected JUnit 5 output

### Parameterized Tests
Where multiple similar scenarios exist (e.g., different assertion types), parameterized tests with `@EnumSource` are used:
```java
@ParameterizedTest
@EnumSource(AssertionCases.class)
public void migrates_junit4_assertions_to_junit5(AssertionCases testCase) {
    // Test implementation
}
```

### Data-Driven Test Cases
Test data is defined as enums with `given` and `expected` fields:
```java
enum AssertionCases {
    BasicAssertions(
        """
        // JUnit 4 code
        """,
        """
        // Expected JUnit 5 code
        """
    );
    
    final String given;
    final String expected;
    
    AssertionCases(String given, String expected) {
        this.given = given;
        this.expected = expected;
    }
}
```

## Running Tests

### Prerequisites
- Java 21 (required for Tycho 5.0.1)
- Maven 3.6+
- Eclipse JDT test infrastructure

### Build and Test
```bash
# Build from repository root with Java 21
export JAVA_HOME=/path/to/java-21
mvn clean verify -Pjacoco

# Run specific test class
mvn test -Dtest=MigrationAssertionsTest

# Run with Xvfb (required on Linux for UI tests)
xvfb-run --auto-servernum mvn verify
```

### Test Execution
Tests are Eclipse plugin tests that:
1. Create a temporary Eclipse workspace
2. Set up JUnit 4 or JUnit 5 classpath
3. Create test compilation units
4. Run JUnit cleanup refactoring
5. Verify expected transformations

## Coverage Gaps (Future Work)

The following migration scenarios are identified but not yet fully implemented:

### High Priority
1. **Exception Handling**
   - `@Test(expected=...)` → `assertThrows()`
   - `ExpectedException` rule → `assertThrows()` with validation

2. **Timeout Handling**
   - `@Test(timeout=...)` → `@Timeout` annotation
   - `@Rule Timeout` → `@Timeout` on test methods

3. **Parameterized Tests**
   - `@RunWith(Parameterized.class)` → `@ParameterizedTest`
   - `@Parameters` method → `@MethodSource`/`@ValueSource`/etc.

### Medium Priority (Updated 2025-12-16)
4. **TemporaryFolder Migration**
   - Complete implementation of `@Rule TemporaryFolder` → `@TempDir Path`
   - Method call updates (newFile/newFolder → resolve().toFile())

### Low Priority
5. **Suite Migration Enhancements**
   - Improve @RunWith(Suite.class) migration
   - Handle edge cases in suite configurations

### Completed Recently ✅
- ~~MockitoJUnitRunner → MockitoExtension~~ **Implemented (2025-12-16)**
- ~~SpringRunner → SpringExtension~~ **Implemented (2025-12-16)**

### Test Organization Improvements
5. Further split large test cases in `JUnitCleanupCases` enum into focused scenarios
6. Add edge case testing for each migration type
7. Add negative test cases (scenarios that should NOT be migrated)

## Maintenance Guidelines

### Adding New Tests
1. Identify the migration aspect (assertions, lifecycle, rules, etc.)
2. Add test to appropriate focused test class
3. Use descriptive method name following `migrates_<old>_to_<new>_<scenario>()` pattern
4. Keep tests small and focused on one scenario
5. If production code doesn't support it yet, mark with `@Disabled` and explanation

### Updating Existing Tests
1. If modifying expected behavior, update all affected test cases
2. Ensure parameterized test enums are updated if adding new scenarios
3. Run full test suite to ensure no regressions

### Migrating from Legacy Tests
When moving test cases from `JUnitMigrationCleanUpTest` to focused classes:
1. Identify the primary migration aspect being tested
2. Extract to appropriate focused test class
3. Simplify to test single concern if it was testing multiple aspects
4. Update method name to follow new convention
5. Keep original test if it tests complex multi-file scenarios

## Implementation Tracking

See [TODO.md](TODO.md) for:
- Missing features that need to be implemented in production code
- Bugs discovered during testing
- Implementation progress tracking
- Prioritized list of work items

## Related Documentation
- [TODO: Implementation Tracking](TODO.md) - Track missing features and bugs
- [JUnit Migration Cleanup Feature](../../../../../../../sandbox_junit_cleanup/README.md)
- [Eclipse JDT CleanUp API](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/ui/cleanup/package-summary.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
