# Sandbox Functional Converter Core

## Purpose

This module provides an AST-independent representation of loop structures for transformation into functional/stream-based equivalents. It is part of the Unified Loop Representation (ULR) implementation described in [Issue #450](https://github.com/carstenartur/sandbox/issues/450).

## Key Features

- **AST-Independent**: No dependencies on Eclipse JDT or any AST implementation
- **Pure Java**: Built with Java 17, no Eclipse/OSGi dependencies
- **Testable**: Easy to unit test without Eclipse runtime
- **Reusable**: Can be used in other contexts beyond Eclipse JDT

## Architecture

The module follows a clean architecture approach:

```
org.sandbox.functional.core.model
  └── LoopModel - Core representation of loop structures
```

## Building and Testing

### Prerequisites

- Java 17 or later
- Maven 3.6+

### Build

```bash
cd sandbox-functional-converter-core
mvn clean install
```

### Run Tests

```bash
mvn test
```

### Run Tests with Coverage

```bash
mvn clean verify jacoco:report
```

## Dependencies

This module intentionally has **NO** Eclipse/JDT/Tycho dependencies to keep it lightweight and testable:

- **JUnit 5 (jupiter)** - For testing
- **AssertJ** - For fluent assertions (optional)

## Development Status

**Phase 1 (Current)**: Module structure and basic infrastructure
- ✅ Module created with Maven setup
- ✅ Basic LoopModel stub
- ⏳ Full ULR model implementation (future phases)

## Integration with Eclipse JDT

While this module has no Eclipse dependencies, it is designed to be used by Eclipse JDT cleanup implementations in the `sandbox_functional_converter` module. The JDT cleanups will:

1. Parse Java code using Eclipse AST
2. Extract loop information into a `LoopModel`
3. Apply transformations using this core module
4. Generate refactored code using Eclipse AST rewrite

## References

- [Issue #450 - Unified Loop Representation](https://github.com/carstenartur/sandbox/issues/450)
- [sandbox_functional_converter](../sandbox_functional_converter/) - Eclipse integration module
- [LoopToFunctional.java](../sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/LoopToFunctional.java) - Current V1 implementation
