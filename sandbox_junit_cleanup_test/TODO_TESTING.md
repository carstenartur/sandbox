# JUnit Migration Test Suite - Implementation Tracking

> **Navigation**: [Main README](../README.md) | [Testing Guide](TESTING.md) | [sandbox_junit_cleanup Architecture](../sandbox_junit_cleanup/ARCHITECTURE.md) | [sandbox_junit_cleanup TODO](../sandbox_junit_cleanup/TODO.md)

This document tracks missing features and bugs in the JUnit migration cleanup implementation that were discovered during test suite refactoring.

**Original Location**: This documentation was moved from `src/org/eclipse/jdt/ui/tests/quickfix/Java8/TODO.md` to improve discoverability.

## 📋 Quick Summary (as of 2025-12-16)

### What's Working ✅
The plugin successfully handles most common JUnit 4 to JUnit 5 migrations:
- **Lifecycle annotations**: @Before/@After/@BeforeClass/@AfterClass → @BeforeEach/@AfterEach/@BeforeAll/@AfterAll
- **Test annotations**: @Ignore → @Disabled, basic @Test migration, @Test(timeout) → @Timeout
- **Exception testing**: @Test(expected) → assertThrows()
- **Assertions**: All standard assertions with correct parameter reordering
- **Assumptions**: Basic assumptions and Hamcrest assumeThat
- **Rules**: TestName → TestInfo, ExternalResource extension pattern
- **Runners**: MockitoJUnitRunner → MockitoExtension, SpringRunner → SpringExtension, Suite → @Suite with @SelectClasses, Enclosed → @Nested, Theories → @ParameterizedTest, Categories → @Suite with @IncludeTags/@ExcludeTags

### What's Not Working ❌
Complex transformations that require code body changes:
- **Parameterized tests**: @RunWith(Parameterized.class)
- **TemporaryFolder**: Rule field migration incomplete

### Migration Coverage
- **~80% of common patterns** are fully supported
- **~15% are complex** and require AST body transformations
- **~5% are simple** but not yet implemented


## 📖 About This Document

This TODO tracks **missing features** in the **JUnit migration cleanup** itself, NOT migration of this repository's own test code.

**Important Context:**
- This repository's test code **already uses JUnit 5** (see `Migration*Test.java` files)
- The JUnit 4 code in `JUnitCleanupCases.java` is **intentional test data** - it's the "before" state that the cleanup should transform
- The **sandbox_junit_cleanup** module provides an Eclipse cleanup that migrates OTHER projects from JUnit 4 to JUnit 5
- This document tracks which migration features work and which still need to be implemented

**For Contributors:**
- To add a new migration feature, create a plugin class in `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/`
- Add tests in the `Migration*Test.java` files 
- Enable the tests when the feature is complete
- Update this TODO to track progress

## 🔴 Critical Missing Features

### 1. TemporaryFolder → @TempDir Migration
**Status:** Not Implemented  
**Priority:** High  
**Affected Tests:**
- `MigrationRulesToExtensionsTest.migrates_temporaryFolder_rule`
- `MigrationRulesToExtensionsTest.migrates_junit4_rules_to_junit5_extensions` (TemporaryFolderBasic case)
- `MigrationCombinationsTest.migrates_test_with_temporaryFolder_and_testName`

**Description:**
The cleanup should migrate JUnit 4's `@Rule TemporaryFolder` to JUnit 5's `@TempDir Path`:

```java
// JUnit 4
@Rule
public TemporaryFolder tempFolder = new TemporaryFolder();

@Test
public void test() throws IOException {
    File file = tempFolder.newFile("test.txt");
}

// Should become JUnit 5
@TempDir
Path tempFolder;

@Test
public void test() throws IOException {
    File file = tempFolder.resolve("test.txt").toFile();
}
```

**Implementation Notes:**
- Replace `@Rule TemporaryFolder` field with `@TempDir Path` field
- Update method calls: `tempFolder.newFile(name)` → `tempFolder.resolve(name).toFile()`
- Update method calls: `tempFolder.newFolder(name)` → `tempFolder.resolve(name).toFile()`
- Add `import java.nio.file.Path`
- Add `import org.junit.jupiter.api.io.TempDir`
- Remove `import org.junit.Rule`
- Remove `import org.junit.rules.TemporaryFolder`

---

### 2. @RunWith(Suite.class) → @Suite Migration
**Status:** ✅ **IMPLEMENTED**  
**Priority:** High  
**Affected Tests:**
- `MigrationRunnersTest.migrates_runWith_suite` (enabled)
- `MigrationCombinationsTest.migrates_suite_with_assertions_and_lifecycle` (enabled)

**Description:**
The cleanup migrates JUnit 4's `@RunWith(Suite.class)` to JUnit 5's `@Suite`:

```java
// JUnit 4
@RunWith(Suite.class)
@Suite.SuiteClasses({TestClass1.class, TestClass2.class})
public class MyTestSuite {
}

// Migrated to JUnit 5
@Suite
@SelectClasses({TestClass1.class, TestClass2.class})
public class MyTestSuite {
}
```

**Implementation:**
- Implemented in `RunWithJUnitPlugin.java`
- Removes `@RunWith(Suite.class)` annotation and adds `@Suite` (from `org.junit.platform.suite.api.Suite`)
- Replaces `@Suite.SuiteClasses` with `@SelectClasses` (from `org.junit.platform.suite.api.SelectClasses`)
- Correctly updates all imports

---

## 🟡 Medium Priority Issues

### 3. @RunWith(Enclosed.class) → @Nested Migration
**Status:** ✅ **IMPLEMENTED**  
**Priority:** Medium  
**Affected Tests:**
- `MigrationRunnersAdvancedTest.migrates_runWith_enclosed_to_nested` (enabled)

**Description:**
The cleanup migrates JUnit 4's `@RunWith(Enclosed.class)` to JUnit 5's `@Nested`:

```java
// JUnit 4
@RunWith(Enclosed.class)
public class EnclosedTest {
    public static class WhenConditionA {
        @Test
        public void shouldDoSomething() {
        }
    }
}

// JUnit 5
public class EnclosedTest {
    @Nested
    class WhenConditionA {
        @Test
        void shouldDoSomething() {
        }
    }
}
```

**Implementation:**
- Removes `@RunWith(Enclosed.class)` from outer class
- Removes `static` and optionally `public` modifiers from inner classes
- Adds `@Nested` annotation to inner classes
- Updates imports accordingly
- **Note:** Only transforms static inner classes that contain test methods (methods annotated with @Test)

---

### 4. @RunWith(Theories.class) → @ParameterizedTest Migration
**Status:** ✅ **IMPLEMENTED**  
**Priority:** Medium  
**Affected Tests:**
- `MigrationRunnersAdvancedTest.migrates_runWith_theories_to_parameterizedTest` (enabled)

**Description:**
The cleanup migrates JUnit 4's `@RunWith(Theories.class)` with `@DataPoints` to JUnit 5's `@ParameterizedTest`:

```java
// JUnit 4
@RunWith(Theories.class)
public class TheoriesTest {
    @DataPoints
    public static int[] values = {1, 2, 3, 4, 5};
    
    @Theory
    public void testPositiveNumbers(int value) {
        assertTrue(value > 0);
    }
}

// JUnit 5
public class TheoriesTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void testPositiveNumbers(int value) {
        assertTrue(value > 0);
    }
}
```

**Implementation:**
- Removes `@RunWith(Theories.class)` from class
- Removes `@DataPoints` field
- Replaces `@Theory` method with `@ParameterizedTest` and `@ValueSource`
- Updates imports accordingly
- **Limitations:** 
  - Only handles a single `@Theory` method (multiple theory methods are not fully supported)
  - Only handles a single `@DataPoints` field
  - Supports int[], String[], double[], long[], short[], byte[], float[], char[], boolean[], and Class[] arrays

---

### 5. @RunWith(Categories.class) → @Suite with @IncludeTags/@ExcludeTags Migration
**Status:** ✅ **IMPLEMENTED**  
**Priority:** Medium  
**Affected Tests:**
- `MigrationRunnersAdvancedTest.migrates_runWith_categories_to_suite_with_tags` (enabled)

**Description:**
The cleanup migrates JUnit 4's `@RunWith(Categories.class)` to JUnit 5's `@Suite` with tag-based filtering:

```java
// JUnit 4
@RunWith(Categories.class)
@IncludeCategory(FastTests.class)
@ExcludeCategory(SlowTests.class)
@SuiteClasses({TestA.class, TestB.class})
public class FastTestSuite {
}

// JUnit 5
@Suite
@IncludeTags("FastTests")
@ExcludeTags("SlowTests")
@SelectClasses({TestA.class, TestB.class})
public class FastTestSuite {
}
```

**Implementation:**
- Replaces `@RunWith(Categories.class)` with `@Suite`
- Transforms `@IncludeCategory(X.class)` to `@IncludeTags("X")`
- Transforms `@ExcludeCategory(X.class)` to `@ExcludeTags("X")`
- Transforms `@SuiteClasses` to `@SelectClasses`
- Updates imports accordingly
- **Note:** Category class hierarchies are converted to plain string tags, losing type safety and hierarchy support

---

### 6. assumeThat with Hamcrest - Unused Import
**Status:** Bug in Cleanup  
**Priority:** Medium  
**Affected Tests:**
- `MigrationAssumptionsTest.migrates_assumeThat_with_hamcrest`

**Description:**
When migrating `Assume.assumeThat()` with Hamcrest matchers, the cleanup correctly uses static import for `assumeThat` from `org.hamcrest.junit.MatcherAssume`, but it also adds an unused `import org.junit.jupiter.api.Assumptions` that should be removed.

**Current Behavior:**
```java
import static org.hamcrest.junit.MatcherAssume.assumeThat;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assumptions;  // ← UNUSED, should be removed
import org.junit.jupiter.api.Test;
```

**Expected Behavior:**
The `Assumptions` import should not be added when Hamcrest's `assumeThat` is used.

---

### Not Yet Implemented (Future Work)

These features are documented as not yet implemented and have disabled tests:

#### 4. @Test(expected=Exception.class) → assertThrows()
**Status:** ✅ **IMPLEMENTED**  
**Priority:** High  
**Complexity:** High (requires wrapping method body in lambda)  
**Tracked in:** `MigrationExceptionsTest` (tests enabled)

**Description:**
```java
// JUnit 4
@Test(expected = IllegalArgumentException.class)
public void testException() {
    throw new IllegalArgumentException("Expected");
}

// Should become JUnit 5
@Test
public void testException() {
    assertThrows(IllegalArgumentException.class, () -> {
        throw new IllegalArgumentException("Expected");
    });
}
```

**Implementation Notes:**
- Implemented in TestExpectedJUnitPlugin
- Detects @Test(expected=...) annotations via NormalAnnotation visitor
- Extracts the exception TypeLiteral from the expected parameter
- Creates lambda expression wrapping the entire method body
- Generates assertThrows method invocation
- Removes expected parameter from @Test (converts to marker annotation if only parameter)
- Adds static import for assertThrows
- Works alongside TestTimeoutJUnitPlugin for combined parameters

#### 5. ExpectedException Rule → assertThrows()
**Status:** Not Implemented  
**Priority:** Medium  
**Complexity:** High (requires analyzing rule method calls and transforming test body)  
**Tracked in:** `MigrationExceptionsTest` (disabled)

#### 6. @Test(timeout=...) → @Timeout
**Status:** ✅ **IMPLEMENTED**  
**Priority:** Medium  
**Complexity:** Medium  
**Tracked in:** `MigrationTestAnnotationTest.migrates_test_timeout_to_timeout_annotation` (enabled)

**Description:**
```java
// JUnit 4
@Test(timeout = 1000)
public void testWithTimeout() { }

// Should become JUnit 5
@Test
@Timeout(value = 1, unit = TimeUnit.SECONDS)
public void testWithTimeout() { }
```

**Implementation Notes:**
- Implemented in TestTimeoutJUnitPlugin
- Detects @Test(timeout=...) annotations
- Extracts timeout value in milliseconds
- Converts to optimal TimeUnit (SECONDS if divisible by 1000, otherwise MILLISECONDS)
- Removes timeout parameter from @Test
- Adds new @Timeout annotation
- Updates imports appropriately

#### 7. @Rule Timeout → @Timeout
**Status:** Not Implemented  
**Priority:** Low  
**Complexity:** Medium  
**Tracked in:** `MigrationRulesToExtensionsTest.migrates_timeout_rule` (disabled)

#### 8. @RunWith(Parameterized.class) → @ParameterizedTest
**Status:** ✅ **IMPLEMENTED**  
**Priority:** High (commonly used pattern)  
**Complexity:** Very High (requires major refactoring of test structure)  
**Tracked in:** `MigrationRunnersTest.migrates_runWith_parameterized` (enabled)

**Description:**
This is a complex transformation requiring:
- Converting constructor parameters to method parameters
- Transforming @Parameters method to @MethodSource
- Converting Object[][] arrays to Stream<Arguments>
- Changing field initialization to parameter injection

#### 9. @RunWith(MockitoJUnitRunner.class) → @ExtendWith(MockitoExtension.class)
**Status:** ✅ **IMPLEMENTED** (as of 2025-12-16)  
**Priority:** High (commonly used pattern)  
**Complexity:** Low (simple annotation replacement)  
**Tracked in:** `MigrationRunnersTest.migrates_runWith_mockito` (enabled)

**Implementation Notes:**
- Implemented in RunWithJUnitPlugin
- Removes @RunWith(MockitoJUnitRunner.class)
- Adds @ExtendWith(MockitoExtension.class)
- Handles both org.mockito.junit.MockitoJUnitRunner and org.mockito.runners.MockitoJUnitRunner
- Updates imports appropriately

#### 10. @RunWith(SpringRunner.class) → @ExtendWith(SpringExtension.class)
**Status:** ✅ **IMPLEMENTED** (as of 2025-12-16)  
**Priority:** High (commonly used pattern)  
**Complexity:** Low (simple annotation replacement)  
**Tracked in:** `MigrationRunnersTest.migrates_runWith_spring` (enabled)

**Implementation Notes:**
- Implemented in RunWithJUnitPlugin
- Removes @RunWith(SpringRunner.class)
- Adds @ExtendWith(SpringExtension.class)
- Handles both SpringRunner and SpringJUnit4ClassRunner
- Updates imports appropriately

---

## ✅ Implementation Progress

### Completed Features (Fully Working)
- ✅ **Lifecycle annotations migration**:
  - @Before → @BeforeEach (BeforeJUnitPlugin)
  - @After → @AfterEach (AfterJUnitPlugin)
  - @BeforeClass → @BeforeAll (BeforeClassJUnitPlugin)
  - @AfterClass → @AfterAll (AfterClassJUnitPlugin)
- ✅ **@Ignore → @Disabled** (IgnoreJUnitPlugin)
  - Supports both marker annotation (@Ignore) and single-member annotation (@Ignore("reason"))
- ✅ **@Test annotation migration** (TestJUnitPlugin)
  - Migrates basic @Test from JUnit 4 to JUnit 5
- ✅ **@Test(expected=...) → assertThrows()** (TestExpectedJUnitPlugin)
  - Migrates expected parameter to assertThrows wrapping method body
  - Handles lambda expression creation
  - Works with combined parameters (e.g., timeout + expected)
- ✅ **@Test(timeout=...) → @Timeout** (TestTimeoutJUnitPlugin)
  - Migrates timeout parameter to separate @Timeout annotation
  - Optimizes TimeUnit (SECONDS vs MILLISECONDS)
  - Handles multiple timeout tests in same class
- ✅ **Assertions migration** (AssertJUnitPlugin)
  - assertEquals, assertTrue, assertFalse, assertNull, assertNotNull, etc.
  - Correctly reorders parameters (message parameter moves to last position in JUnit 5)
  - Static import conversions (Assert.* → Assertions.*)
- ✅ **Assumptions migration** (AssumeJUnitPlugin)
  - assumeTrue, assumeFalse, assumeNotNull (non-Hamcrest)
  - assumeThat with Hamcrest matchers (using org.hamcrest.junit.MatcherAssume)
- ✅ **TestName Rule → TestInfo** (RuleTestnameJUnitPlugin)
  - Migrates @Rule TestName field to TestInfo parameter
  - Adds @BeforeEach initialization method
- ✅ **ExternalResource extension** (ExternalResourceJUnitPlugin, RuleExternalResourceJUnitPlugin)
  - Migrates custom ExternalResource subclasses to use JUnit 5 extension pattern
- ✅ **@RunWith runners migration** (RunWithJUnitPlugin)
  - @RunWith(MockitoJUnitRunner.class) → @ExtendWith(MockitoExtension.class)
  - @RunWith(SpringRunner.class) → @ExtendWith(SpringExtension.class)
  - @RunWith(Suite.class) with @Suite.SuiteClasses → @Suite with @SelectClasses
  - Supports both old and new package names for Mockito runners
  - Supports both SpringRunner and SpringJUnit4ClassRunner

### In Progress / Partially Working
- 🔴 **TemporaryFolder → @TempDir** (RuleTemporayFolderJUnitPlugin exists but incomplete)
  - Plugin exists but transformation is not complete
  - Needs to update method calls (newFile/newFolder → resolve().toFile())
- 🟡 **assumeThat Hamcrest import cleanup** (minor bug)
  - Migration works but adds unnecessary `import org.junit.jupiter.api.Assumptions`
  - Should only use static import from `org.hamcrest.junit.MatcherAssume`

---

## How to Use This Document

1. **For Contributors:** Pick an item from the "Critical Missing Features" section to implement
2. **For Reviewers:** Check this list against test results to verify progress
3. **For Maintainers:** Update status as features are implemented and tests are enabled

When a feature is implemented:
1. Update the status in this document
2. Enable the corresponding disabled tests
3. Verify all tests pass
4. Move the item to "Completed Features" section

---

## Related Files

- Test Classes: `/sandbox_junit_cleanup_test/src/org/eclipse/jdt/ui/tests/quickfix/Java8/Migration*.java`
- Cleanup Constants: `/sandbox_common/src/org/sandbox/jdt/internal/corext/fix2/MYCleanUpConstants.java`
- Production Code: `/sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/*`
- Legacy Test Data: `JUnitCleanupCases.java`, `JUnit3CleanupCases.java`

---

---

## 📚 Implementation Architecture Guide

### How the Plugin Works

The JUnit cleanup is implemented using a plugin architecture with these key components:

1. **JUnitCleanUpFixCore** (Enum in `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/JUnitCleanUpFixCore.java`)
   - Central registry listing all cleanup plugins
   - Each enum value represents one transformation
   - Delegates to specific plugin classes

2. **Plugin Classes** (in `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/`)
   - Each plugin handles one specific migration pattern
   - Examples: `BeforeJUnitPlugin`, `AfterJUnitPlugin`, `IgnoreJUnitPlugin`
   
3. **Abstract Base Classes**:
   - `AbstractMarkerAnnotationJUnitPlugin` - For simple annotation replacements
   - `AbstractTool<T>` - Base for all plugins
   
4. **Constants** (in `sandbox_common/src/org/sandbox/jdt/internal/corext/fix2/MYCleanUpConstants.java`)
   - Defines cleanup options that can be enabled/disabled
   - Each constant corresponds to a specific migration feature

### Adding a New Migration

To implement a new migration (e.g., @RunWith(MockitoJUnitRunner) → @ExtendWith(MockitoExtension)):

1. **Create Plugin Class** (if simple annotation replacement):
   ```java
   public class MockitoRunnerJUnitPlugin extends AbstractMarkerAnnotationJUnitPlugin {
       @Override
       protected String getSourceAnnotation() {
           return "org.junit.runner.RunWith";  // Will need additional filtering
       }
       // ... implement other methods
   }
   ```

2. **Register in JUnitCleanUpFixCore**:
   ```java
   MOCKITO_RUNNER(new MockitoRunnerJUnitPlugin()),
   ```

3. **Add Constant** (in MYCleanUpConstants.java):
   ```java
   public static final String JUNIT_CLEANUP_4_MOCKITO_RUNNER = "cleanup.junitcleanup_4_mockito_runner";
   ```

4. **Create Tests** (in `Migration*Test.java` files):
   - Write test with JUnit 4 input and expected JUnit 5 output
   - Test with real Eclipse AST transformation

5. **Enable Cleanup** (in plugin.xml if needed)

### Complexity Levels

**Easy** (Can extend AbstractMarkerAnnotationJUnitPlugin):
- Simple annotation name changes
- No code body modifications
- Examples: @Ignore → @Disabled, @Before → @BeforeEach

**Medium** (Custom plugin, annotation + import changes):
- Annotation parameter transformations
- Import management
- Examples: @RunWith runners, @Suite migration

**Hard** (Requires AST body transformations):
- Method body wrapping/restructuring
- Field to parameter conversions
- Examples: @Test(expected), Parameterized tests, TemporaryFolder

### Tips for Implementation

1. **Start with tests**: Write failing tests first to clarify expected behavior
2. **Study existing plugins**: IgnoreJUnitPlugin handles both marker and single-member annotations
3. **Use HelperVisitor**: Provides AST node visitor helpers (callMarkerAnnotationVisitor, etc.)
4. **Handle imports carefully**: Always remove old imports and add new ones
5. **Test edge cases**: Empty methods, multiple annotations, complex scenarios

---

## 📊 Test Coverage Status

### Enabled Tests (Working Features)
- ✅ MigrationLifecycleTest - all tests passing
- ✅ MigrationIgnoreTest - all tests passing (except 5 known edge cases disabled)
- ✅ MigrationAssertionsTest - all tests passing
- ✅ MigrationAssumptionsTest - mostly passing (1 disabled for Hamcrest import issue)
- ✅ MigrationTestAnnotationTest - all tests passing including timeout
- ✅ MigrationExceptionsTest - @Test(expected) tests passing (ExpectedException tests still disabled)
- ✅ MigrationRulesToExtensionsTest - TestName, ExternalResource, and Timeout passing
- ✅ **MigrationAssertOptimizationTest** - **NEW** - all tests passing (migration + assert optimization)
- ✅ **MigrationAssumeOptimizationTest** - **NEW** - all tests passing (migration + assume optimization)
- ✅ **MigrationEdgeCasesTest** - **NEW** - all tests passing (edge cases and special scenarios)
- ✅ **TriggerPatternPluginTest** - **NEW** - all tests passing (V2 TriggerPattern plugin validation)
- ✅ **MigrationCombinationsTest** - **EXTENDED** - now includes tests for multiple rules combined

### Disabled Tests (Not Implemented)
- ❌ MigrationExceptionsTest - 3 tests disabled (ExpectedException rule with message/cause)
- ❌ MigrationRunnersTest - 2 tests disabled (Suite, Parameterized)

### Test Statistics (Updated 2026-01-30)
- **Total test methods**: ~75-80 (increased from ~50-60)
- **Enabled and passing**: ~70-75 (93-94%)
- **Disabled (not implemented)**: ~5-7 (6-7%)
- **Known bugs**: ~2-3 (3%)

### New Test Classes Added (2026-01-30)
1. **MigrationAssertOptimizationTest.java** - 7 test methods
   - Tests assert optimization during JUnit 4→5 migration
   - Parameter swapping, negation removal, message reordering
   
2. **MigrationAssumeOptimizationTest.java** - 5 test methods
   - Tests assume optimization during JUnit 4→5 migration
   - Negation removal for assumeTrue/assumeFalse
   
3. **MigrationEdgeCasesTest.java** - 7 test methods
   - Edge cases: combined @Test parameters, comments preservation
   - Wildcard imports, empty tests, mixed patterns
   
4. **TriggerPatternPluginTest.java** - 8 test methods
   - Validates V2 TriggerPattern-based plugin implementations
   - Tests all V2 plugins (Before, After, Test, BeforeClass, AfterClass, Ignore)

### Extended Test Classes (2026-01-30)
1. **MigrationRulesToExtensionsTest.java** - Added 2 methods
   - `migrates_timeout_rule_with_seconds()`
   - `migrates_timeout_rule_with_millis()`
   
2. **MigrationCombinationsTest.java** - Added 2 methods
   - `migrates_test_with_multiple_rules()` - TemporaryFolder + TestName + Timeout
   - `migrates_full_test_class_with_rules_and_lifecycle()` - Complex combination scenario



---

Last Updated: 2026-01-30
