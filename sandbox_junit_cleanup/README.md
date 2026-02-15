# JUnit Cleanup Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **JUnit Cleanup** plugin provides automated migration from JUnit 3/4 to JUnit 5 (Jupiter). It transforms test classes, methods, annotations, and assertions to use the modern JUnit 5 API, making test code more maintainable and feature-rich.

## Key Features

- 🔄 **Automated JUnit 3 → 5 Migration** - Transform `extends TestCase` to annotations
- 🔄 **JUnit 4 → 5 Migration** - Update annotations and assertions
- 🧪 **Assertion Migration** - Convert `assertEquals()` to message-last pattern
- 📦 **Lifecycle Methods** - Transform `setUp()`/`tearDown()` to `@BeforeEach`/`@AfterEach`
- 🎯 **Exception Testing** - Convert `@Test(expected=...)` to `assertThrows()`
- 🔌 **Eclipse Integration** - Works seamlessly with Eclipse JDT

## Quick Start

### Enable in Eclipse

1. Open **Source** → **Clean Up...**
2. Navigate to the **JUnit** category
3. Enable **Migrate to JUnit 5**

### Example Transformations

**JUnit 3 Class Migration:**
```java
// Before
public class MyTest extends TestCase {
    protected void setUp() throws Exception {
        super.setUp();
        // setup code
    }
    
    public void testSomething() {
        assertEquals("message", expected, actual);
    }
}

// After
public class MyTest {
    @BeforeEach
    void setUp() {
        // setup code
    }
    
    @Test
    void testSomething() {
        assertEquals(expected, actual, "message");
    }
}
```

**Exception Testing:**
```java
// Before
@Test(expected = IllegalArgumentException.class)
public void testException() {
    methodThatThrows();
}

// After
@Test
void testException() {
    assertThrows(IllegalArgumentException.class, () -> {
        methodThatThrows();
    });
}
```

**Assertion Parameter Order:**
```java
// Before (JUnit 3/4 - message first)
assertEquals("Values must match", expected, actual);

// After (JUnit 5 - message last)
assertEquals(expected, actual, "Values must match");
```

## Migration Summary

### JUnit 3 Transformations

| JUnit 3 | JUnit 5 |
|---------|---------|
| `extends TestCase` | Remove inheritance, add `@Test` annotations |
| `setUp()` | `@BeforeEach` |
| `tearDown()` | `@AfterEach` |
| `public void testXxx()` | `@Test void testXxx()` |
| Method visibility `public` | Can be package-private |

### JUnit 4 Transformations

| JUnit 4 | JUnit 5 |
|---------|---------|
| `@Before` | `@BeforeEach` |
| `@After` | `@AfterEach` |
| `@BeforeClass` | `@BeforeAll` |
| `@AfterClass` | `@AfterAll` |
| `@Ignore` | `@Disabled` |
| `@Test(expected=...)` | `assertThrows(...)` |

### Assertion Transformations

| Old Pattern | New Pattern |
|------------|-------------|
| `assertEquals(String message, expected, actual)` | `assertEquals(expected, actual, String message)` |
| `assertTrue(String message, condition)` | `assertTrue(condition, String message)` |
| `assertNull(String message, object)` | `assertNull(object, String message)` |

## Supported Features

### Test Class Structure
- ✅ Remove `extends TestCase` inheritance
- ✅ Add `@Test` annotations to test methods
- ✅ Remove unnecessary `public` modifiers
- ✅ Convert lifecycle methods (`setUp`, `tearDown`, `setUpBeforeClass`, `tearDownAfterClass`)

### Assertions
- ✅ Reorder assertion parameters (message-last pattern)
- ✅ Update assertion method names
- ✅ Handle assertions with/without messages
- ✅ Preserve assertion logic and behavior

### Exception Handling
- ✅ Convert `@Test(expected=...)` to `assertThrows()`
- ✅ Wrap test body in lambda expression
- ✅ Handle multi-statement test bodies

### Imports
- ✅ Remove JUnit 3/4 imports
- ✅ Add JUnit 5 (Jupiter) imports
- ✅ Update static imports for assertions

## Detailed Migration Guide

This section provides comprehensive examples and details for all supported migration patterns.

### JUnit 3 Migration Details

#### Class Structure Transformations

The cleanup removes the `extends TestCase` inheritance and eliminates the need for JUnit 3's base class.

**Before:**
```java
import junit.framework.TestCase;

public class MyTest extends TestCase {
    public MyTest(String name) {
        super(name);
    }
}
```

**After:**
```java
import org.junit.jupiter.api.Test;

public class MyTest {
    // Constructor removed - no longer needed
}
```

**Changes Applied:**
- Remove `extends TestCase` from class declaration
- Remove constructor that calls `super(name)`
- Remove `import junit.framework.TestCase`
- Add appropriate JUnit 5 imports

#### Test Method Transformations

JUnit 3 uses naming conventions (`testXxx`) to identify test methods. JUnit 5 uses the `@Test` annotation.

**Before:**
```java
public void testBasicAssertions() {
    assertEquals("Values should match", 42, 42);
    assertTrue("Condition should be true", true);
}
```

**After:**
```java
@Test
@Order(1)
public void testBasicAssertions() {
    assertEquals(42, 42, "Values should match");
    assertTrue(true, "Condition should be true");
}
```

**Changes Applied:**
- Add `@Test` annotation
- Add `@Order` annotation if part of a suite (maintains test execution order)
- Reorder assertion parameters (message moves to last position)
- Optionally rename to more descriptive names (removing `test` prefix)

#### Setup and Teardown Methods

JUnit 3 uses method name conventions for setup and teardown. JUnit 5 uses annotations.

**Before:**
```java
@Override
protected void setUp() throws Exception {
    // Setup before each test
}

@Override
protected void tearDown() throws Exception {
    // Cleanup after each test
}
```

**After:**
```java
@BeforeEach
public void setUp() throws Exception {
    // Setup before each test
}

@AfterEach
public void tearDown() throws Exception {
    // Cleanup after each test
}
```

**Changes Applied:**
- Replace implicit naming convention with `@BeforeEach` annotation
- Replace implicit naming convention with `@AfterEach` annotation
- Remove `@Override` annotation (no longer overriding from base class)
- Methods can remain `protected` or become `public`

#### Test Suite Migration

JUnit 3 uses `suite()` methods and `TestSuite` class. JUnit 5 uses `@TestMethodOrder` with ordering annotations.

**Before:**
```java
public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(new MyTest("testBasicAssertions"));
    suite.addTest(new MyTest("testArrayAssertions"));
    suite.addTest(new MyTest("testWithAssume"));
    return suite;
}
```

**After:**
```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MyTest {
    @Test
    @Order(1)
    public void testBasicAssertions() { }

    @Test
    @Order(2)
    public void testArrayAssertions() { }

    @Test
    @Order(3)
    public void testWithAssume() { }
}
```

**Changes Applied:**
- Remove `suite()` method completely
- Add `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` to class
- Add `@Order(n)` annotations to maintain execution order
- Add required imports:
  - `org.junit.jupiter.api.TestMethodOrder`
  - `org.junit.jupiter.api.MethodOrderer`
  - `org.junit.jupiter.api.Order`

### JUnit 4 Migration Details

#### Lifecycle Annotations

JUnit 4 lifecycle annotations are replaced with JUnit 5 equivalents.

**Before:**
```java
import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;

@BeforeClass
public static void setUpBeforeClass() throws Exception {
    // Setup before all tests
}

@AfterClass
public static void tearDownAfterClass() throws Exception {
    // Cleanup after all tests
}

@Before
public void setUp() throws Exception {
    // Setup before each test
}

@After
public void tearDown() throws Exception {
    // Cleanup after each test
}
```

**After:**
```java
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

@BeforeAll
public static void setUpBeforeClass() throws Exception {
    // Setup before all tests
}

@AfterAll
public static void tearDownAfterClass() throws Exception {
    // Cleanup after all tests
}

@BeforeEach
public void setUp() throws Exception {
    // Setup before each test
}

@AfterEach
public void tearDown() throws Exception {
    // Cleanup after each test
}
```

**Changes Applied:**
- `@Before` → `@BeforeEach`
- `@After` → `@AfterEach`
- `@BeforeClass` → `@BeforeAll`
- `@AfterClass` → `@AfterAll`
- Update imports accordingly

#### Test Annotations

**@Ignore / @Disabled:**

**Before:**
```java
import org.junit.Ignore;
import org.junit.Test;

@Ignore
@Test
public void ignoredTestWithoutMessage() {
    fail("This test is ignored");
}

@Ignore("Ignored with message")
@Test
public void ignoredTestWithMessage() {
    fail("This test is ignored with a message");
}
```

**After:**
```java
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
@Test
public void ignoredTestWithoutMessage() {
    Assertions.fail("This test is ignored");
}

@Disabled("Ignored with message")
@Test
public void ignoredTestWithMessage() {
    Assertions.fail("This test is ignored with a message");
}
```

**Changes Applied:**
- `@Ignore` → `@Disabled`
- `@Ignore("reason")` → `@Disabled("reason")`
- Update imports

#### Test Suite Annotations

**Before:**
```java
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    MyTest.class,
    OtherTest.class
})
public class MyTestSuite {
}
```

**After:**
```java
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectClasses;

@Suite
@SelectClasses({
    MyTest.class,
    OtherTest.class
})
public class MyTestSuite {
}
```

**Changes Applied:**
- `@RunWith(Suite.class)` → `@Suite`
- `@Suite.SuiteClasses({...})` → `@SelectClasses({...})`
- Update imports to JUnit Platform Suite API

#### Rule Annotations

JUnit 4 `@Rule` and `@ClassRule` are migrated to JUnit 5 `@RegisterExtension`.

**TemporaryFolder Rule:**

**Before:**
```java
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

@Rule
public TemporaryFolder tempFolder = new TemporaryFolder();

@Test
public void test() throws IOException {
    File newFile = tempFolder.newFile("myfile.txt");
}
```

**After:**
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@TempDir
Path tempFolder;

@Test
public void test() throws IOException {
    File newFile = tempFolder.resolve("myfile.txt").toFile();
}
```

**TestName Rule:**

**Before:**
```java
import org.junit.Rule;
import org.junit.rules.TestName;

@Rule
public TestName tn = new TestName();

@Test
public void test() {
    System.out.println("Test name: " + tn.getMethodName());
}
```

**After:**
```java
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.BeforeEach;

private String testName;

@BeforeEach
void init(TestInfo testInfo) {
    this.testName = testInfo.getDisplayName();
}

@Test
public void test() {
    System.out.println("Test name: " + testName);
}
```

**ExternalResource Rule:**

**Before:**
```java
import org.junit.Rule;
import org.junit.rules.ExternalResource;

@Rule
public ExternalResource er = new ExternalResource() {
    @Override
    protected void before() throws Throwable {
        // setup
    }

    @Override
    protected void after() {
        // cleanup
    }
};
```

**After:**
```java
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@RegisterExtension
public Er_5b8b4 er = new Er_5b8b4();

class Er_5b8b4 implements BeforeEachCallback, AfterEachCallback {
    public void beforeEach(ExtensionContext context) {
        // setup
    }

    public void afterEach(ExtensionContext context) {
        // cleanup
    }
}
```

**Changes Applied:**
- `@Rule` → `@RegisterExtension`
- `@ClassRule` (static) → `@RegisterExtension` (static)
- `TemporaryFolder` → `@TempDir Path`
- `TestName` → `TestInfo` parameter in `@BeforeEach`
- `ExternalResource` → Custom extension implementing `BeforeEachCallback` / `AfterEachCallback`
- For class rules: `BeforeAllCallback` / `AfterAllCallback`

### Assertion Migration

#### Supported Assertion Methods

The cleanup handles the following assertion methods from JUnit 3/4:

| Assertion Method       | Parameter Count | Description                           |
|------------------------|-----------------|---------------------------------------|
| `assertEquals`         | 2 or 3          | Assert two values are equal           |
| `assertNotEquals`      | 2 or 3          | Assert two values are not equal       |
| `assertArrayEquals`    | 2 or 3          | Assert two arrays are equal           |
| `assertSame`           | 2 or 3          | Assert two objects are the same       |
| `assertNotSame`        | 2 or 3          | Assert two objects are not the same   |
| `assertTrue`           | 1 or 2          | Assert condition is true              |
| `assertFalse`          | 1 or 2          | Assert condition is false             |
| `assertNull`           | 1 or 2          | Assert object is null                 |
| `assertNotNull`        | 1 or 2          | Assert object is not null             |
| `fail`                 | 0 or 1          | Explicitly fail the test              |
| `assertThat`           | 2 or 3          | Assert with Hamcrest matcher          |

**Note**: The parameter count includes the optional message parameter.

#### Parameter Order Differences

| Framework | Signature Format                            |
|-----------|---------------------------------------------|
| JUnit 3   | `assertEquals("message", expected, actual)` |
| JUnit 4   | `assertEquals("message", expected, actual)` |
| JUnit 5   | `assertEquals(expected, actual, "message")` |

> **Note**: In JUnit 5, the message is always the **last** argument.  
> The cleanup ensures correct reordering **only if it's safe** (i.e., the first argument is a String literal).

#### Assertion Mapping Table

| JUnit 3/4 Assertion                        | JUnit 5 Equivalent                         |
|-------------------------------------------|--------------------------------------------|
| `assertEquals(expected, actual)`          | `assertEquals(expected, actual)`           |
| `assertEquals("msg", expected, actual)`   | `assertEquals(expected, actual, "msg")`    |
| `assertSame(expected, actual)`            | `assertSame(expected, actual)`             |
| `assertSame("msg", expected, actual)`     | `assertSame(expected, actual, "msg")`      |
| `assertNotSame(expected, actual)`         | `assertNotSame(expected, actual)`          |
| `assertNotSame("msg", expected, actual)`  | `assertNotSame(expected, actual, "msg")`   |
| `assertTrue(condition)`                   | `assertTrue(condition)`                    |
| `assertTrue("msg", condition)`            | `assertTrue(condition, "msg")`             |
| `assertFalse(condition)`                  | `assertFalse(condition)`                   |
| `assertFalse("msg", condition)`           | `assertFalse(condition, "msg")`            |
| `assertNull(object)`                      | `assertNull(object)`                       |
| `assertNull("msg", object)`               | `assertNull(object, "msg")`                |
| `assertNotNull(object)`                   | `assertNotNull(object)`                    |
| `assertNotNull("msg", object)`            | `assertNotNull(object, "msg")`             |
| `fail()`                                  | `fail()`                                   |
| `fail("msg")`                              | `fail("msg")`                               |
| `assertArrayEquals("msg", expected, actual)` | `assertArrayEquals(expected, actual, "msg")` |
| `assertNotEquals("msg", expected, actual)` | `assertNotEquals(expected, actual, "msg")` |

### Assumption Migration

The cleanup also handles JUnit 4 assumption methods, which are used for conditional test execution.

#### Supported Assumption Methods

| Assumption Method  | Parameter Count | Description                              |
|--------------------|-----------------|------------------------------------------|
| `assumeTrue`       | 1 or 2          | Assume condition is true                 |
| `assumeFalse`      | 1 or 2          | Assume condition is false                |
| `assumeNotNull`    | 1 or 2          | Assume object is not null                |
| `assumeThat`       | 2 or 3          | Assume with Hamcrest matcher             |

#### Assumption Mapping Table

| JUnit 4 Assumption                         | JUnit 5 Equivalent                         |
|-------------------------------------------|--------------------------------------------|
| `Assume.assumeTrue(condition)`            | `Assumptions.assumeTrue(condition)`        |
| `Assume.assumeTrue("msg", condition)`     | `Assumptions.assumeTrue(condition, "msg")` |
| `Assume.assumeFalse(condition)`           | `Assumptions.assumeFalse(condition)`       |
| `Assume.assumeFalse("msg", condition)`    | `Assumptions.assumeFalse(condition, "msg")`|
| `Assume.assumeNotNull(object)`            | `Assumptions.assumeNotNull(object)`        |
| `Assume.assumeNotNull("msg", object)`     | `Assumptions.assumeNotNull(object, "msg")` |
| `Assume.assumeThat(value, matcher)`       | `assumeThat(value, matcher)` (Hamcrest)    |
| `Assume.assumeThat("msg", value, matcher)`| `assumeThat("msg", value, matcher)` (Hamcrest) |

**Example:**

**Before:**
```java
import org.junit.Assume;

@Test
public void testWithAssume() {
    Assume.assumeTrue("Precondition failed", true);
    Assume.assumeFalse("Precondition not met", false);
    Assume.assumeNotNull("Value should not be null", new Object());
}
```

**After:**
```java
import org.junit.jupiter.api.Assumptions;

@Test
public void testWithAssume() {
    Assumptions.assumeTrue(true, "Precondition failed");
    Assumptions.assumeFalse(false, "Precondition not met");
    Assumptions.assumeNotNull(new Object(), "Value should not be null");
}
```

**Changes Applied:**
- `org.junit.Assume` → `org.junit.jupiter.api.Assumptions`
- Parameter order changed (message moved to last position)
- `assumeThat` uses Hamcrest's static import from `org.hamcrest.junit.MatcherAssume`

## Architecture

The plugin uses a helper class architecture for maintainability:

- **AbstractTool** - Orchestration layer
- **JUnitConstants** - Annotation names and FQCNs
- **ImportHelper** - Import management
- **AssertionRefactorer** - Assertion parameter reordering
- **LifecycleMethodAdapter** - Lifecycle annotation conversion

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design documentation.

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details and class structure
- **[TODO](TODO.md)** - Pending features and known issues
- **[Testing Guide](../sandbox_junit_cleanup_test/TESTING.md)** - Test organization
- **[Main README](../README.md#junit-5-migration-cleanup-sandbox_junit_cleanup)** - Comprehensive examples

## Testing

Comprehensive test suite in `sandbox_junit_cleanup_test`:
- JUnit 3 migration tests
- JUnit 4 migration tests
- Assertion transformation tests
- Exception testing patterns
- Edge cases and regressions

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_junit_cleanup_test
```

## Known Limitations

- Does not migrate JUnit 4 `@RunWith` annotations (requires manual review)
- Parameterized tests need manual migration
- Custom test runners may need adjustment
- Some advanced JUnit 4 features have no direct JUnit 5 equivalent

See [TODO.md](TODO.md) for planned enhancements.

## Contributing to Eclipse JDT

This plugin is designed for easy integration into Eclipse JDT:
1. Replace `org.sandbox` with `org.eclipse` in package names
2. Move classes to `org.eclipse.jdt.internal.corext.fix`
3. Update test infrastructure

The helper class architecture makes the code easy to maintain and contribute upstream.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Functional Converter](../sandbox_functional_converter/), [Method Reuse](../sandbox_method_reuse/)
