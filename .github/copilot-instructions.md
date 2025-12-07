# GitHub Copilot Instructions for Sandbox Project

## Repository Overview

This is a sandbox repository for experimenting with Eclipse JDT cleanups, build strategies, and various Eclipse plugins. The project contains multiple Eclipse plugin modules focused on code quality improvements and automated refactoring.

### Purpose
- Experiment with Eclipse JDT cleanup implementations
- Test different build strategies and tools (Maven/Tycho, SpotBugs, JaCoCo)
- Develop custom Eclipse plugins for code modernization
- Support multiple Eclipse versions via GitHub Actions
- **Maintain a structure that can be easily ported into Eclipse JDT** - The codebase is designed for easy integration into Eclipse JDT core

### Project Goals
- Follow **GitHub best practices** for code quality, CI/CD, and project structure
- Maintain high **code coverage** standards using JaCoCo
- Provide a clean, maintainable codebase that serves as a reference implementation

## Technology Stack

- **Language**: Java 21 (target: JavaSE-21)
- **Build System**: Maven with Tycho 5.0.1
- **Testing**: JUnit 5 (migrated from JUnit 4/3)
- **IDE**: Eclipse RCP/JDT
- **CI/CD**: GitHub Actions
- **Code Quality**: SpotBugs, Codacy, CodeQL
- **Coverage**: JaCoCo
- **Eclipse Version**: 2025-09 (main branch)

## Build, Test, and Lint Instructions

### Building the Project

```bash
# Standard build with JaCoCo coverage
mvn -Pjacoco verify

# Build with WAR file (includes update site)
mvn -Dinclude=web -Pjacoco verify
```

**Build outputs:**
- Product: `sandbox_product/target`
- WAR file: `sandbox_web/target`

### Running Tests

Tests are Eclipse plugin tests that require X virtual framebuffer (Xvfb) on Linux:

```bash
# Run tests with display server
xvfb-run --auto-servernum mvn -Pjacoco verify

# Tests are located in modules ending with `_test`
# Test files use JUnit 5
```

### Linting and Code Quality

```bash
# SpotBugs runs automatically during compile phase
mvn compile

# SpotBugs configuration:
# - Effort: Max
# - Threshold: medium
# - Exclusions: ../spotbugs-exclude.xml
# - PMD Suppression: ../pmd-suppressions.xml (legacy file name, used for SpotBugs)
```

**Note**: SpotBugs is configured to fail the build on issues.

### Code Coverage

JaCoCo is used for code coverage reporting with the following goals:

```bash
# Generate coverage report
mvn -Pjacoco verify

# Coverage reports are generated in:
# - Individual modules: <module>/target/site/jacoco/
# - Aggregated report: sandbox_coverage/target/site/jacoco-aggregate/
```

**Coverage Best Practices**:
- Aim for high test coverage, especially for cleanup transformations
- Each cleanup should have comprehensive test cases covering edge cases
- Use parameterized tests to cover multiple Java versions efficiently
- Coverage reports help identify untested code paths before merging to Eclipse JDT

## Code Style and Conventions

### File Headers

All Java files must include the Eclipse Public License 2.0 header:

```java
/*******************************************************************************
 * Copyright (c) 2024 hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
```

### Package Structure

- `org.sandbox.jdt.internal.corext.*` - Core transformation/fix logic
- `org.sandbox.jdt.internal.ui.*` - UI components and preferences
- `org.eclipse.jdt.ui.tests.quickfix.*` - Test cases (test modules)

**Important**: The package structure is designed for easy porting to Eclipse JDT:
- To port code to Eclipse JDT, simply replace `sandbox` with `eclipse` in package paths
- Example: `org.sandbox.jdt.internal.corext` → `org.eclipse.jdt.internal.corext`
- Each package has a corresponding OSGi module with the same name (after the replacement)
- This naming convention ensures seamless integration into Eclipse JDT when features are ready for upstream contribution

### Java Conventions

- Use Java 21 features where appropriate
- Target JavaSE-21 execution environment
- Follow Eclipse JDT coding conventions
- Use `@Override` annotations
- Prefer `final` for immutable variables
- Use Java 8+ functional constructs where appropriate

### Testing Conventions

- Use JUnit 5 (`org.junit.jupiter.api.*`)
- Test classes end with `Test` suffix
- Use `@Test`, `@BeforeEach`, `@AfterEach` annotations
- Prefer `assertThrows()` over `@Test(expected=...)`
- Test files are in modules ending with `_test`

## Architecture and Project Structure

### Module Organization

The project follows an Eclipse plugin structure with paired modules:

1. **Implementation Module** (`sandbox_*`) - Contains the actual code
2. **Feature Module** (`sandbox_*_feature`) - Eclipse feature packaging
3. **Test Module** (`sandbox_*_test`) - JUnit 5 tests

### Key Modules

#### sandbox_encoding_quickfix
Replaces platform-dependent encoding with explicit `StandardCharsets.UTF_8`:
- Transforms `FileReader`/`FileWriter` to use explicit charsets
- Updates `Files.readAllLines()` and similar methods
- Supports Java 11+ with version-aware transformations (Java 7 no longer supported)

#### sandbox_platform_helper
Simplifies `new Status(...)` calls:
- Java 8: Uses `StatusHelper` pattern
- Java 11+: Uses `Status.error()`, `Status.warning()` factory methods

#### sandbox_functional_converter
Converts imperative loops to Java 8 Streams:
- Enhanced for-loops → `forEach()`
- Mapping/filtering → `stream().map().filter()`
- Reductions → `reduce()`

#### sandbox_junit_cleanup
Migrates JUnit 3/4 tests to JUnit 5:
- `extends TestCase` → annotations
- `setUp()`/`tearDown()` → `@BeforeEach`/`@AfterEach`
- `@Test(expected=...)` → `assertThrows()`

#### sandbox_tools
While-to-For loop converter. The implementation was successfully contributed to and merged into the Eclipse JDT project. This module remains in the repository for reference and testing purposes.

#### sandbox_usage_view
Table view for detecting inconsistent naming.

#### sandbox_extra_search
Search tool for critical classes during Eclipse/Java upgrades.

### Build Configuration

- **Parent POM**: `/pom.xml` - Defines common configuration
- **Target Platform**: `sandbox_target` module
- **Repositories**: Eclipse 2025-09, Orbit, JustJ, EGit
- **Tycho Configuration**: Version 5.0.1 with P2 repositories

## Important Patterns and Practices

### Eclipse JDT Cleanup Pattern

When creating new cleanups:

1. **Create the cleanup class** in module-specific `org.sandbox.jdt.internal.corext` package
2. **Define constants** in `MYCleanUpConstants` (located in `sandbox_common/src/org/sandbox/jdt/internal/corext/fix2`)
3. **Register in `plugin.xml`** under `org.eclipse.jdt.ui.cleanUps` extension point
4. **Create test cases** in corresponding `*_test` module
5. **Document in README.md** with examples and Java version compatibility

**Porting to Eclipse JDT**: The package structure allows for easy contribution to Eclipse JDT:
- Replace `org.sandbox` with `org.eclipse` in all package declarations and imports
- Each OSGi module name matches its primary package name
- This design enables seamless upstream integration when cleanups are mature

**MYCleanUpConstants Reference**: 
- `MYCleanUpConstants` in this sandbox corresponds to Eclipse JDT's [`CleanUpConstants`](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java)
- When porting to Eclipse JDT, constants from `MYCleanUpConstants` are copied/merged into `CleanUpConstants`
- Each plugin's constants in `MYCleanUpConstants` follow the same pattern and naming conventions as Eclipse JDT
- This ensures consistency and simplifies the integration process when contributing features upstream

### Test-Driven Development

- Write tests first in the `*_test` module
- Tests should cover multiple Java versions supported by Eclipse (currently Java 11, 17, 21)
- **Note**: Java 7 is no longer supported by Eclipse and should not be targeted
- Use parameterized tests with `@EnumSource` or `@ValueSource`
- Include disabled tests (`@Disabled`) for future features

### Version Compatibility

Always consider Eclipse and Java version compatibility:
- **Only support Java versions currently supported by Eclipse** (Java 11+, no Java 7 support)
- Check `min.java.version` for features
- Use Eclipse Platform 4.20+ features conditionally
- Document version requirements in comments and README

### Multi-Version Support

The project aims to support building against multiple Eclipse versions for backporting:

- **Goal**: Support the latest 3 Eclipse releases (e.g., 2025-09, 2024-12, 2024-09)
- **Current State**: Main branch targets Eclipse 2025-09; multi-version CI workflows need enhancement
- **Workflow Strategy**: GitHub Actions workflows in `.github/workflows/` should build against multiple Eclipse versions
- **Backporting**: When backporting features, PRs should target the appropriate Eclipse version branches
- **Future Enhancement**: Implement support for targeting multiple branches with a single PR for easier backporting

### Maven/Tycho Specifics

- Use `tycho-maven-plugin` for Eclipse builds
- P2 repositories are defined in parent POM
- Dependency resolution uses target platform, not Maven Central
- Use `eclipse-plugin` packaging type

### Code Quality

This project follows **GitHub best practices** for code quality:

- **SpotBugs**: Must pass (build fails on errors). Exclude specific visitors if needed in config
- **CodeQL**: Security scanning runs in CI to catch vulnerabilities
- **JaCoCo**: Code coverage tracking with aggregated reports in `sandbox_coverage`
- **Codacy**: Automated code review for style and quality issues
- **Test Coverage**: Comprehensive test suites for all cleanup implementations

**Best Practice Goals**:
- Maintain high code quality standards suitable for Eclipse JDT contribution
- Use suppression files only for validated false positives
- Ensure all security issues are addressed before merging
- Keep coverage high to validate cleanup logic correctness

## Development Workflow

1. **Branch Naming**: Use descriptive names (e.g., `feature/new-cleanup`, `fix/encoding-issue`)
2. **Commits**: Write clear commit messages explaining the change
3. **Testing**: Always run tests before committing
4. **CI**: All checks must pass (Maven build, SpotBugs, CodeQL, Codacy)
5. **Pull Requests**: 
   - **Keep PRs small and focused**: Each PR should address a single aspect or concern
   - **Avoid mixing changes**: Don't combine formatting changes with logic changes, or multiple unrelated features
   - **Split large changes**: If many changes are needed, split them into multiple PRs that belong to the same issue
   - **Goal**: Make changes easy to understand and review - large diffs mixing different concerns are difficult to review
   - Include description of changes and test results
   - For backporting features, PRs may need to target multiple branches (see Multi-Version Support below)

## Common Commands

```bash
# Full build with coverage
mvn clean verify -Pjacoco

# Skip tests
mvn clean install -DskipTests

# Run specific test
mvn test -Dtest=ExplicitEncodingCleanUpTest

# Update license headers (disabled by default)
mvn license:update-file-header

# Build product
mvn clean verify -Pjacoco
# Output: sandbox_product/target/products/
```

## Troubleshooting

### Build Issues

- **Tycho Resolution Errors**: Check target platform in `sandbox_target/sandbox_target.target`
- **SpotBugs Failures**: Check exclusion file or add suppressions
- **Test Failures**: Ensure Xvfb is running for UI tests

### Common Pitfalls

- Don't mix Maven and P2 dependencies
- Eclipse plugins require OSGi metadata (MANIFEST.MF, plugin.xml)
- Tests need Eclipse runtime environment
- Use `tycho-version` property for all Tycho plugins

## Resources

- [Eclipse JDT Documentation](https://help.eclipse.org/latest/)
- [Tycho Documentation](https://tycho.eclipseprojects.io/)
- [SpotBugs](https://spotbugs.github.io/)
- [JUnit 5](https://junit.org/junit5/)

## Notes for AI Assistants

- This is an Eclipse plugin project, not a standard Java project
- Always consider Eclipse platform APIs and patterns
- Tests require Eclipse runtime and cannot be run as plain JUnit tests
- Maven dependencies are resolved via P2, not Maven Central
- Code must be compatible with Eclipse plugin classloading
- When adding new cleanups, follow the existing pattern in other modules
