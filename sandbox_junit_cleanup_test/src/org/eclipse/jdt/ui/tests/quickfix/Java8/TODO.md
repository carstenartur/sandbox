# JUnit Migration Test Suite - Implementation Tracking

This document tracks missing features and bugs in the JUnit migration cleanup implementation that were discovered during test suite refactoring.

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

## 🟢 Already Disabled (Future Work)

These features are documented as not yet implemented and already have disabled tests:

### 4. @Test(expected=Exception.class) → assertThrows()
**Status:** Not Implemented  
**Tracked in:** `MigrationExceptionsTest` (all tests disabled)

### 5. ExpectedException Rule → assertThrows()
**Status:** Not Implemented  
**Tracked in:** `MigrationExceptionsTest` (all tests disabled)

### 6. @Test(timeout=...) → @Timeout
**Status:** Not Implemented  
**Tracked in:** `MigrationTestAnnotationTest.migrates_test_timeout_parameter` (disabled)

### 7. @Rule Timeout → @Timeout
**Status:** Not Implemented  
**Tracked in:** `MigrationRulesToExtensionsTest.migrates_timeout_rule` (disabled)

### 8. @RunWith(Parameterized) → @ParameterizedTest
**Status:** Not Implemented  
**Tracked in:** `MigrationRunnersTest.migrates_runWith_parameterized` (disabled)

### 9. @RunWith(MockitoJUnitRunner) → @ExtendWith(MockitoExtension)
**Status:** Not Implemented  
**Tracked in:** `MigrationRunnersTest.migrates_runWith_mockito` (disabled)

### 10. @RunWith(SpringRunner) → @ExtendWith(SpringExtension)
**Status:** Not Implemented  
**Tracked in:** `MigrationRunnersTest.migrates_runWith_spring` (disabled)

---

## ✅ Implementation Progress

### Completed Features
- ✅ Assertions migration (assertEquals, assertTrue, etc.) with parameter order changes
- ✅ Static import conversions (Assert.* → Assertions.*)
- ✅ Lifecycle annotations (@Before/@After/@BeforeClass/@AfterClass)
- ✅ @Ignore → @Disabled
- ✅ TestName Rule → TestInfo with @BeforeEach initialization
- ✅ @Test annotation migration
- ✅ assumeTrue, assumeFalse, assumeNotNull (non-Hamcrest)

### In Progress
- 🔴 TemporaryFolder → @TempDir
- 🔴 @RunWith(Suite.class) → @Suite
- 🟡 assumeThat Hamcrest import cleanup

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

## 📊 Repository Status (Updated 2025-12-16)

### Test Infrastructure
- **All test files already use JUnit 5**: The actual test classes (`Migration*Test.java`) use `org.junit.jupiter.api.*` annotations
- **JUnit 4 code in test data is intentional**: Files like `JUnitCleanupCases.java` contain JUnit 4 code as **test inputs** (the "given" strings) to validate the cleanup transformations
- **Test pattern**: Each test creates a JUnit 4 code sample, runs the cleanup, and verifies it produces the expected JUnit 5 output

### Implementation Architecture
The JUnit cleanup is implemented using a plugin architecture:
- **`JUnitCleanUpFixCore`**: Enum listing all cleanup plugins
- **Plugin classes** (in `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/`):
  - `BeforeJUnitPlugin`, `AfterJUnitPlugin` - Lifecycle annotations
  - `TestJUnitPlugin` - @Test annotation migration
  - `AssertJUnitPlugin` - Assertion method migration
  - `AssumeJUnitPlugin` - Assumption method migration
  - `RuleTemporayFolderJUnitPlugin` - TemporaryFolder rule (currently incomplete)
  - `RunWithJUnitPlugin` - @RunWith annotation (currently incomplete)
  - And more...
- **Abstract base classes**:
  - `AbstractMarkerAnnotationJUnitPlugin` - For simple annotation replacements
  - `AbstractTool<T>` - Base for all plugins

### What's Working Well
The cleanup successfully handles:
- Simple annotation replacements (@Before → @BeforeEach, @After → @AfterEach, etc.)
- Assertion parameter reordering (message-last in JUnit 5)
- Static vs. instance import management
- TestName Rule → TestInfo migration
- ExternalResource extension migration
- Basic assumption methods (assumeTrue, assumeFalse, assumeNotNull)

### What Needs Implementation
The missing features require more complex AST transformations:
1. **@Test(expected)** - Requires wrapping method body in assertThrows lambda
2. **TemporaryFolder** - Requires changing field type AND updating method calls
3. **@RunWith(Suite)** - Requires annotation parameter transformation
4. **Parameterized tests** - Complex transformation to @ParameterizedTest
5. **Timeout** - Similar to expected, needs method body wrapping or annotation migration

---

Last Updated: 2025-12-16
