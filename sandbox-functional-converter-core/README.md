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

## OSGi Integration

This module is built as an OSGi bundle using the bnd-maven-plugin. After building with `mvn install`, the resulting JAR can be used as an Eclipse plugin dependency.

### Bundle Information
- **Bundle-SymbolicName:** org.sandbox.functional.core
- **Export-Package:** org.sandbox.functional.core.*

### ⚠️ Important: Required Pre-Build for Eclipse Workspace

**This module MUST be built with `mvn install` before opening the Eclipse workspace** or importing projects. The Eclipse plugin `sandbox_functional_converter` depends on this module as an OSGi bundle (`org.sandbox.functional.core`).

If you open the Eclipse workspace without building this module first, you will see errors:
- `Bundle 'org.sandbox.functional.core' cannot be resolved` in MANIFEST.MF
- `The import org.sandbox.functional cannot be resolved` in Java files
- Various types like `LoopModel`, `SourceDescriptor`, etc. cannot be resolved

**Solution**: Run this command from the repository root before opening Eclipse:
```bash
mvn install -pl sandbox-functional-converter-core,sandbox-ast-api,sandbox-ast-api-jdt -am -DskipTests
```

This installs the Maven modules into your local Maven repository (`~/.m2/repository`), allowing Tycho to resolve them as OSGi bundles when building the Eclipse plugins.

**Note**: This is also documented in the main [README.md](../README.md#development-environment-setup), and the Oomph setup creates an instruction file prompting you to run this command if you use Eclipse Installer.

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

## CI/CD

The module has its own GitHub Actions workflow that runs on every push/PR affecting the core module:
- Builds the module with Maven
- Runs all unit tests
- Uploads test results as artifacts

[![Core Module Build](https://github.com/carstenartur/sandbox/actions/workflows/core-module-build.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/core-module-build.yml)

## References

- [Issue #450 - Unified Loop Representation](https://github.com/carstenartur/sandbox/issues/450)
- [sandbox_functional_converter](../sandbox_functional_converter/) - Eclipse integration module
- [LoopToFunctional.java](../sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/LoopToFunctional.java) - Current V1 implementation
