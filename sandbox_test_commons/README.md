# Test Commons Module

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Test Commons** module provides shared test infrastructure, utilities, and base classes used across all sandbox test modules. It centralizes common testing patterns to reduce duplication and ensure consistency in test implementations.

## Key Features

- 🧪 **JUnit 5 Extension Base Class** - `AbstractEclipseJava` manages temporary Eclipse projects per test
- 🔢 **Multi-Version Java Support** - Version-specific subclasses for Java 8, 9, 10, 17, 18 and 22
- 🔧 **Cleanup/Refactoring Helpers** - Methods for creating compilation units and asserting refactoring results
- 🔌 **JUnit 5 Integration** - Built on the Jupiter extension model (`BeforeEachCallback`, `AfterEachCallback`)

## Components

All classes live in the package `org.sandbox.jdt.ui.tests.quickfix.rules`.

### `AbstractEclipseJava`

The base JUnit 5 extension that provides the test infrastructure for Eclipse
JDT cleanup and refactoring tests. It implements
`org.junit.jupiter.api.extension.BeforeEachCallback` and `AfterEachCallback`,
and is responsible for:

- Creating and configuring a temporary Eclipse Java project for each test
- Setting the Java compiler compliance level based on the target version
- Loading the appropriate runtime stubs JAR (e.g. `testresources/rtstubs_17.jar`)
- Providing helper methods for creating compilation units and executing/asserting refactorings
- Cleaning up workspace resources after each test

### Version-Specific Extensions

Concrete subclasses select the Java compliance level and matching runtime stubs:

- `EclipseJava8` — Java 8 (1.8)
- `EclipseJava9` — Java 9
- `EclipseJava10` — Java 10
- `EclipseJava17` — Java 17
- `EclipseJava18` — Java 18
- `EclipseJava22` — Java 22

Each subclass simply calls the `AbstractEclipseJava` constructor with its
runtime-stubs path and the corresponding `JavaCore` version constant.

### `TestOptions`

Helper (ported from `org.eclipse.jdt.testplugin`) used to configure default
compiler/JDT options for the test environment.

## Usage

Register the desired version extension with `@RegisterExtension` and use it to
create compilation units and assert refactoring results:

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

class MyCleanUpTest {

    @RegisterExtension
    static EclipseJava17 context = new EclipseJava17();

    @Test
    void testMyCleanup() throws Exception {
        // Create a compilation unit in the temporary project, apply the
        // cleanup under test, and assert the resulting source.
        // (See AbstractEclipseJava for the available helper methods.)
    }
}
```

> **Note**: For the exact set of available helper methods, refer to
> [`ARCHITECTURE.md`](ARCHITECTURE.md) and the source of `AbstractEclipseJava`.

## Dependencies

Test modules that depend on test_commons:
- `sandbox_encoding_quickfix_test`
- `sandbox_junit_cleanup_test`
- `sandbox_functional_converter_test`
- `sandbox_platform_helper_test`
- `sandbox_jface_cleanup_test`
- All other `*_test` modules

## JUnit 5 Support

The module uses JUnit 5 (Jupiter):

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
```

### Migration from JUnit 4

For tests still using JUnit 4:
- Use JUnit 5 vintage engine
- Or migrate to JUnit 5 API
- See [JUnit Cleanup](../sandbox_junit_cleanup/) for migration tool

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Test infrastructure design
- **[TODO](TODO.md)** - Planned test utilities
- **[HelperVisitor API Tests](../sandbox_common_test/TESTING.md)** - Example test documentation

## Testing the Tests

The test commons module itself has tests to verify test infrastructure:

```bash
xvfb-run --auto-servernum mvn test -pl sandbox_test_commons
```

## Best Practices

### 1. Reuse Base Classes
Extend provided base classes instead of reimplementing common setup.

### 2. Use Test Utilities
Leverage utilities instead of writing boilerplate test code.

### 3. Follow Patterns
Use established test patterns for consistency.

### 4. Document Test Intent
Clear test names and comments explain what's being tested.

### 5. Test One Thing
Each test should verify one specific behavior.

## Contributing

To add new test utilities:

1. **Identify Common Pattern**
   - Find repeated code across test modules

2. **Create Utility**
   - Implement in test_commons
   - Document usage

3. **Add Tests**
   - Test the utility itself

4. **Update Tests**
   - Refactor existing tests to use utility

See [TODO.md](TODO.md) for planned test infrastructure improvements.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related**: All `sandbox_*_test` modules depend on this infrastructure
