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
- **[Main README](../README.md#9-sandbox_junit)** - Comprehensive examples

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
