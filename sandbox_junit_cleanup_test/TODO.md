# JUnit Migration Test Suite - Implementation Tracking

This document tracks missing features and bugs in the JUnit migration cleanup implementation that were discovered during test suite refactoring.

## 📋 Quick Summary (as of 2025-12-16)

### What's Working ✅
The plugin successfully handles most common JUnit 4 to JUnit 5 migrations:
- **Lifecycle annotations**: @Before/@After/@BeforeClass/@AfterClass → @BeforeEach/@AfterEach/@BeforeAll/@AfterAll
- **Test annotations**: @Ignore → @Disabled, basic @Test migration
- **Assertions**: All standard assertions with correct parameter reordering
- **Assumptions**: Basic assumptions and Hamcrest assumeThat
- **Rules**: TestName → TestInfo, ExternalResource extension pattern
- **Runners**: MockitoJUnitRunner → MockitoExtension, SpringRunner → SpringExtension

### What's Not Working ❌
Complex transformations that require code body changes:
- **Exception testing**: @Test(expected) and ExpectedException rule
- **Parameterized tests**: @RunWith(Parameterized.class)
- **Timeout handling**: @Test(timeout) and Timeout rule
- **TemporaryFolder**: Rule field migration incomplete
- **Suite migration**: @RunWith(Suite.class) (simple runner replacement works, but suite-specific migration incomplete)

### Migration Coverage
- **~70% of common patterns** are fully supported
- **~25% are complex** and require AST body transformations
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
**Status:** Not Implemented  
**Priority:** High  
**Affected Tests:**
- `MigrationRunnersTest.migrates_runWith_suite`
- `MigrationCombinationsTest.migrates_suite_with_assertions_and_lifecycle`

**Description:**
The cleanup should migrate JUnit 4's `@RunWith(Suite.class)` to JUnit 5's `@Suite`:

```java
// JUnit 4
@RunWith(Suite.class)
@Suite.SuiteClasses({TestClass1.class, TestClass2.class})
public class MyTestSuite {
}

// Should become JUnit 5
@Suite
@SelectClasses({TestClass1.class, TestClass2.class})
public class MyTestSuite {
}
```

**Implementation Notes:**
- Remove `@RunWith(Suite.class)` annotation
- Add `@Suite` annotation (from `org.junit.platform.suite.api.Suite`)
- Replace `@Suite.SuiteClasses` with `@SelectClasses` (from `org.junit.platform.suite.api.SelectClasses`)
- Update imports accordingly

---

## 🟡 Medium Priority Issues

### 3. assumeThat with Hamcrest - Unused Import
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
**Status:** Not Implemented  
**Priority:** High  
**Complexity:** High (requires wrapping method body in lambda)  
**Tracked in:** `MigrationExceptionsTest` (all 5 tests disabled)

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

#### 5. ExpectedException Rule → assertThrows()
**Status:** Not Implemented  
**Priority:** Medium  
**Complexity:** High (requires analyzing rule method calls and transforming test body)  
**Tracked in:** `MigrationExceptionsTest` (disabled)

#### 6. @Test(timeout=...) → @Timeout
**Status:** Not Implemented  
**Priority:** Medium  
**Complexity:** Medium  
**Tracked in:** `MigrationTestAnnotationTest.migrates_test_timeout_parameter` (disabled)

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

#### 7. @Rule Timeout → @Timeout
**Status:** Not Implemented  
**Priority:** Low  
**Complexity:** Medium  
**Tracked in:** `MigrationRulesToExtensionsTest.migrates_timeout_rule` (disabled)

#### 8. @RunWith(Parameterized.class) → @ParameterizedTest
**Status:** Not Implemented  
**Priority:** High (commonly used pattern)  
**Complexity:** Very High (requires major refactoring of test structure)  
**Tracked in:** `MigrationRunnersTest.migrates_runWith_parameterized` (disabled)

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
  - Note: @Test(expected) and @Test(timeout) parameters not yet migrated (see below)
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
- ✅ MigrationTestAnnotationTest - basic @Test passing (1 disabled for timeout)
- ✅ MigrationRulesToExtensionsTest - TestName and ExternalResource passing (5 disabled)

### Disabled Tests (Not Implemented)
- ❌ MigrationExceptionsTest - 5 tests disabled (@Test(expected), ExpectedException)
- ❌ MigrationRunnersTest - 4 tests disabled (Suite, Parameterized, Mockito, Spring)
- ❌ MigrationCombinationsTest - 3 tests disabled (complex combinations)
- ❌ MigrationRulesToExtensionsTest - 5 tests disabled (TemporaryFolder, Timeout)

### Test Statistics
- **Total test methods**: ~50-60
- **Enabled and passing**: ~35-40 (65-70%)
- **Disabled (not implemented)**: ~15-20 (25-30%)
- **Known bugs**: ~2-3 (5%)



---

Last Updated: 2025-12-16
