# GitHub Copilot Instructions for Sandbox Project

## Quick Reference - Common Issues

⚠️ **Before doing ANYTHING**: Set Java 21
```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
java -version  # Must show "21"
```

⚠️ **Test failures**: ALWAYS check CI logs first, never guess the expected output
```bash
# Download CI log (user will provide URL)
curl -s "<log_url>" > /tmp/ci_log.txt
grep -A 100 "expected:" /tmp/ci_log.txt
```

⚠️ **Running tests**: Must use xvfb-run and specify module
```bash
xvfb-run --auto-servernum mvn test -Dtest=TestClass -pl module_name_test
```

⚠️ **Class version errors**: You're using Java 17 instead of Java 21
```
UnsupportedClassVersionError: class file version 65.0
→ Fix: Set JAVA_HOME to temurin-21-jdk-amd64
```

⚠️ **Fast builds**: Use profiles and parallel builds
```bash
mvn -T 1C verify                    # Standard (without Product/Updatesite)
mvn -Pproduct,repo -T 1C verify     # Full build
```

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

### Java Version Requirements

**CRITICAL**: This project requires **Java 21** to build and test.

- **CI Environment**: Uses Java 21 (Temurin) as configured in `.github/workflows/maven.yml`
- **Local Development**: Must use Java 21 to match CI environment
- **Target Platform**: Eclipse 2025-09 requires Java 21

#### Setting Java Version Locally

If you have multiple Java versions installed (common in GitHub Actions runners):

```bash
# Check available Java versions
ls /usr/lib/jvm/

# Available versions typically include:
# - temurin-8-jdk-amd64
# - temurin-11-jdk-amd64  
# - temurin-17-jdk-amd64 (often the default)
# - temurin-21-jdk-amd64 (REQUIRED for this project)
# - temurin-25-jdk-amd64

# Set JAVA_HOME to Java 21
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Verify Java version
java -version  # Should show "openjdk version "21..."
```

#### Why Java 21 is Required

- **Tycho 5.0.1** requires Java 21 (class file version 65.0)
- **Eclipse 2025-09** target platform requires Java 21
- Running with Java 17 will cause: `UnsupportedClassVersionError: ...class file version 65.0, this version only recognizes up to 61.0`

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

### Maven Profiles for Build Acceleration

The project uses Maven profiles to optimize build times. The "heavy" modules (`sandbox_product`, `sandbox_updatesite`) are not included in the default build.

| Profile | Description | Command |
|---------|-------------|---------|
| `dev` (default) | Fast development - without Product/Updatesite | `mvn verify` |
| `product` | With Eclipse Product | `mvn -Pproduct verify` |
| `repo` | With P2 Update Site | `mvn -Prepo verify` |
| `jacoco` | With Code Coverage + sandbox_coverage module | `mvn -Pjacoco verify` |
| `web` | With WAR file (sandbox_product + sandbox_web) | `mvn -Dinclude=web verify` |

**Combined profiles for full build:**
```bash
mvn -Pproduct,repo,jacoco verify  # Build everything (like before)
```

**Parallel builds for faster iteration:**
```bash
mvn -T 1C verify              # Parallel build (1 thread per CPU core)
mvn -T 1C -DskipTests verify  # Without tests for fast iteration
```

For detailed information see [BUILD_ACCELERATION.md](../BUILD_ACCELERATION.md).

### Running Tests

Tests are Eclipse plugin tests that require X virtual framebuffer (Xvfb) on Linux:

```bash
# IMPORTANT: Set Java 21 first!
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Run tests with display server
xvfb-run --auto-servernum mvn -Pjacoco verify

# Run specific test class
xvfb-run --auto-servernum mvn test -Dtest=Java22CleanUpTest -pl sandbox_functional_converter_test

# Run specific test method
xvfb-run --auto-servernum mvn test -Dtest=Java22CleanUpTest#testSimpleForEachConversion -pl sandbox_functional_converter_test

# Tests are located in modules ending with `_test`
# Test files use JUnit 5
```

**Common Test Issues**:
- **Java version mismatch**: Ensure Java 21 is active (`java -version` should show "21")
- **Class version errors**: Usually means Java 17 or older is being used instead of Java 21
- **Xvfb issues**: Tests need a display server; use `xvfb-run --auto-servernum`

**When tests fail in CI**: Always check the CI logs to see the actual vs expected output. See [Accessing CI Logs](#accessing-ci-logs) section below.

### Linting and Code Quality

```bash
# SpotBugs runs automatically during compile phase
mvn compile

# SpotBugs configuration:
# - Effort: Max
# - Threshold: medium
# - Exclusions: ../spotbugs-exclude.xml
```

**Note**: SpotBugs is configured to fail the build on issues.

**Note**: The repository also contains `pmd-suppressions.xml` which is a separate PMD suppressions file, not used by SpotBugs.

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
 * Copyright (c) 2021 Carsten Hammer.
 * // Update the year to the current year when creating new files.
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

**Directory Correspondence Examples**:
| Sandbox Directory | Eclipse JDT Equivalent |
|------------------|------------------------|
| `sandbox_encoding_quickfix/src/org/sandbox/jdt/internal/corext/fix` | [`org.eclipse.jdt.core.manipulation/core extension/org/eclipse/jdt/internal/corext/fix`](https://github.com/eclipse-jdt/eclipse.jdt.ui/tree/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix) |
| `sandbox_common/src/org/sandbox/jdt/internal/corext/fix2/MYCleanUpConstants.java` | [`org.eclipse.jdt.core.manipulation/core extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java`](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java) |
| `sandbox_*/src/org/sandbox/jdt/internal/ui/*` | `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/*` |
| `sandbox_*_test/src/org/eclipse/jdt/ui/tests/quickfix/*` | `org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/*` |

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

### Module Types

The project contains different module types:

1. **Eclipse Plugin Modules** (`sandbox_*`) - Tycho/Eclipse Plugin Packaging
2. **Standard Java Modules** (`sandbox-*-core`) - Maven JAR Packaging without Eclipse dependencies
3. **Feature Modules** (`sandbox_*_feature`) - Eclipse Feature for installation
4. **Test Modules** (`sandbox_*_test`) - Eclipse Plugin Tests (require Xvfb)
5. **Infrastructure Modules** - `sandbox_target`, `sandbox_coverage`, `sandbox_product`, `sandbox_updatesite`

**Important**: Modules with `-` in the name (e.g., `sandbox-functional-converter-core`) are standard Maven modules and can be built and tested without an Eclipse environment.

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

#### sandbox-functional-converter-core
AST-independent core module for loop transformations (Unified Loop Representation - ULR):
- **Packaging**: Standard Maven JAR (not Eclipse Plugin)
- **Build**: Uses `maven-compiler-plugin` and `bnd-maven-plugin` for OSGi bundle generation
- **Tests**: JUnit 5 with AssertJ - can run without Xvfb
- **Purpose**: Reusable transformation logic without Eclipse dependencies
- **Special Note**: This module can be used independently from the Tycho build

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

**Eclipse Target Platform Compilation**:

The entire sandbox project, particularly the Eclipse plugins, is compiled against an Eclipse target platform defined in the `sandbox_target/eclipse.target` file. This target platform specifies:
- Eclipse 2025-09 as the primary platform version
- Required Eclipse features (JDT, SDK, PDE, etc.)
- External dependencies from Eclipse Orbit
- Additional components (EGit, JustJ, license features)

The target platform ensures consistent compilation across all environments and pins specific Eclipse versions and dependencies for reproducible builds. When building the project, Maven Tycho resolves dependencies from the P2 repositories specified in the target platform rather than from Maven Central.

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
- **When test expectations don't match cleanup output**: Check CI logs to see what the cleanup actually produces - never guess the expected formatting. Eclipse's formatter may add spaces, line breaks, or indentation differently than expected.

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
- **Dependency resolution uses target platform, not Maven Central**
  - All Eclipse plugins in the sandbox project are compiled against the Eclipse target platform defined in `sandbox_target/eclipse.target`
  - Tycho resolves dependencies from P2 repositories specified in the target platform
  - This ensures version consistency and reproducible builds across different environments
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

## Branch Conventions

- **Default Branch**: `main` - All new development should be based on and target the `main` branch
- **Branch Naming**: Use descriptive names (e.g., `feature/new-cleanup`, `fix/encoding-issue`)
- **Multi-Version Support**: See the Multi-Version Support section for information on backporting to release-specific branches

## Plugin Documentation Requirements

All plugin directories (e.g., `sandbox_encoding_quickfix`, `sandbox_junit_cleanup`) **MUST** contain two mandatory documentation files:

1. **`architecture.md`** (or `ARCHITECTURE.md`): Design and architecture overview
   - Describes the plugin's purpose, structure, and key components
   - Documents design patterns and implementation approaches
   - Explains integration points with Eclipse JDT
   - Should be read before making changes to understand the codebase

2. **`todo.md`** (or `TODO.md`): Open tasks and follow-ups
   - Lists pending features, known issues, and planned improvements
   - Tracks implementation milestones and progress
   - Documents future enhancements and technical debt
   - Should be updated when new tasks are identified or completed

**Note**: File naming may use either lowercase (`architecture.md`, `todo.md`) or uppercase (`ARCHITECTURE.md`, `TODO.md`). Both are acceptable, but consistency within each plugin is preferred.

**Pull Request Requirements**:
- When touching plugin code, PRs **MUST** mention that these files were reviewed and updated if necessary
- New plugins **MUST** include both files before being merged
- Significant architectural changes **MUST** update `architecture.md`
- New features or identified issues **MUST** update `todo.md`

## Documentation Structure Guidelines

To keep the project understandable and maintainable, follow these documentation organization principles:

### 1. Audience-Based Documentation

Documentation should be split by **target audience**, not just by technical topic:

| Audience | Document | Purpose |
|----------|----------|---------|
| **Project Users** | `README.md` | How to use/install the project, overview, FAQ, important links |
| **Eclipse Developers** | `ARCHITECTURE.md` | Developer/maintainer guide, architecture, technical decisions, design patterns |
| **GitHub Project Developers** | `.github/workflows/README.md` or workflow docs | Build, test, CI/CD details, contributing guidelines |
| **Security/Legal** | `SECURITY.md`, `LICENSE.txt`, `CODE_OF_CONDUCT.md` | Required GitHub standard files |

### 2. Top-Level Markdown File Policy

- Keep top-level markdown files to a **minimum**
- More than 3 primary docs is allowed but **must be justified**
- Prefer adding a **new section/chapter** to an existing document when the audience overlaps
- New standalone files should only be created if:
  - The content addresses a clearly distinct audience, AND
  - Having a separate file significantly improves clarity and discoverability
- When proposing a new top-level doc, ask: "Can this be a section in README.md, ARCHITECTURE.md, or another existing file?"

### 3. Plugin-Specific Documentation

- Plugin/module documentation **MUST** live inside the plugin folder (e.g., `sandbox_encoding_quickfix/README.md`)
- Do **NOT** place plugin-specific docs at the repository root
- Each plugin should have: `README.md`, `ARCHITECTURE.md`, `TODO.md` (as already documented in Plugin Documentation Requirements)

### 4. Documentation Consolidation Principles

- Split documentation by **audience**, not just by technical topic
- Regularly review and consolidate documentation
- For small topics, add chapters/sections to the most relevant existing document instead of creating new files
- Consider whether future maintainers will easily find the information

## Feature Module Documentation Requirements

All feature module directories (e.g., `sandbox_encoding_quickfix_feature`, `sandbox_junit_cleanup_feature`) **MUST** contain internationalization property files:

1. **`feature.properties`** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities in plain text
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License (https://www.eclipse.org/legal/epl-2.0/)
   - `license` - Eclipse Public License text or reference
   - Use `\n\` for line continuations in multi-line properties

2. **`feature_de.properties`** - German translation of all properties
   - Must contain the same keys as feature.properties
   - Translations should be accurate and maintain the same meaning
   - Technical terms (e.g., "JUnit", "Eclipse", "Java") typically remain unchanged

**Purpose**: These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE Update Manager and feature installation dialogs.

**Update Requirements**:
- When adding new features to a plugin, update the corresponding feature module's property files
- When changing feature capabilities, ensure both English and German descriptions are updated
- PRs affecting feature functionality **SHOULD** review and update feature.properties files accordingly

## Development Workflow

1. **Commits**: Write clear commit messages explaining the change
2. **Testing**: Always run tests before committing
3. **CI**: All checks must pass (Maven build, SpotBugs, CodeQL, Codacy)
   - **On CI failures**: Download and analyze CI logs to understand actual vs expected behavior
   - **Never assume**: Always verify what the code actually produces in CI environment
4. **Pull Requests**: 
   - **Keep PRs small and focused**: Each PR should address a single aspect or concern
   - **Avoid mixing changes**: Don't combine formatting changes with logic changes, or multiple unrelated features
   - **Split large changes**: If many changes are needed, split them into multiple PRs that belong to the same issue
   - **Goal**: Make changes easy to understand and review - large diffs mixing different concerns are difficult to review
   - Include description of changes and test results
   - For backporting features, PRs may need to target multiple branches (see Multi-Version Support above)
   - **Plugin changes**: Confirm that `architecture.md` and `todo.md` were reviewed and updated as needed
   - **Test failures**: If tests fail, access CI logs before attempting fixes

## Common Commands

```bash
# IMPORTANT: Always set Java 21 first!
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Verify Java version (should show "21")
java -version

# Fast development build (without Product/Updatesite)
mvn -T 1C verify

# Full release build
mvn -Pproduct,repo,jacoco -T 1C verify

# Full build with coverage
mvn clean verify -Pjacoco

# Skip tests
mvn clean install -DskipTests

# Only core modules test (without Xvfb)
mvn test -pl sandbox-functional-converter-core

# Run specific test
xvfb-run --auto-servernum mvn test -Dtest=ExplicitEncodingCleanUpTest -pl sandbox_encoding_quickfix_test

# Run specific test method
xvfb-run --auto-servernum mvn test -Dtest=Java22CleanUpTest#testSimpleForEachConversion -pl sandbox_functional_converter_test

# Update license headers (disabled by default)
mvn license:update-file-header

# Build product
mvn clean verify -Pjacoco
# Output: sandbox_product/target/products/
```

### Makefile Commands (Alternative to Maven)

The project contains a `Makefile` for commonly used commands:

```bash
make dev         # Fast build with tests
make dev-notests # Fast build without tests
make product     # Build with Eclipse Product
make repo        # Build with P2 Repository
make release     # Full release build
make test        # Tests with coverage
make clean       # Clean artifacts
```

## Troubleshooting

### Build Issues

- **Tycho Resolution Errors**: Check target platform in `sandbox_target/sandbox_target.target`
- **SpotBugs Failures**: Check exclusion file or add suppressions
- **Java Version Errors**: 
  - **Error**: `UnsupportedClassVersionError: class file version 65.0, this version only recognizes up to 61.0`
  - **Cause**: Using Java 17 instead of Java 21
  - **Fix**: Set `JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64` and update PATH
- **Test Failures**: Ensure Xvfb is running for UI tests
  - **If tests fail in CI but not locally**: Download and analyze the CI logs to see actual output
  - **Formatting mismatches**: Eclipse formatter may produce different whitespace, line breaks, or indentation than expected

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

## Accessing CI Logs

When debugging test failures or CI issues, **ALWAYS** access the actual CI logs rather than relying on assumptions:

### Why CI Logs Are Critical

**YOU CANNOT RUN TESTS LOCALLY IN THE SANDBOX ENVIRONMENT** due to:
- Java version issues (sandbox may have Java 17, but project needs Java 21)
- Missing Eclipse runtime dependencies
- Tycho/P2 repository resolution issues
- Limited execution time for long-running builds

**Therefore**: When tests fail, you **MUST** access CI logs to see actual output. Do not try to run tests locally first.

### How to Access CI Logs

1. **From PR comments**: When a user provides a CI log URL, download and analyze it:
   ```bash
   curl -s "<log_url>" > /tmp/ci_log.txt
   grep -A 100 "<test_name>" /tmp/ci_log.txt
   ```

2. **Using GitHub Actions API**: Access workflow run logs programmatically:
   ```bash
   # List workflow runs for a branch
   gh api repos/carstenartur/sandbox/actions/workflows/maven.yml/runs?branch=<branch_name>
   
   # Get job logs
   gh api repos/carstenartur/sandbox/actions/jobs/<job_id>/logs
   ```

3. **Finding test failures in logs**: Look for comparison patterns:
   ```bash
   # Find "expected:" vs "but was:" sections
   grep -B 5 -A 50 "expected:" /tmp/ci_log.txt
   ```

### Why This Matters

- **Formatting differences**: Eclipse's formatter may produce different output than expected (e.g., line breaks, indentation, spaces around operators)
- **Actual vs assumed behavior**: The cleanup code may behave differently than anticipated
- **Exact whitespace**: Tests may fail due to tabs vs spaces, extra newlines, or indentation levels

**CRITICAL**: Never guess the expected formatting. Always check the CI log to see what the cleanup actually produces, then update test expectations to match.

## Notes for AI Assistants

- This is an Eclipse plugin project, not a standard Java project
- Always consider Eclipse platform APIs and patterns
- Tests require Eclipse runtime and cannot be run as plain JUnit tests
- Maven dependencies are resolved via P2, not Maven Central
- Code must be compatible with Eclipse plugin classloading
- When adding new cleanups, follow the existing pattern in other modules
- **When test failures occur, ALWAYS access CI logs to see actual vs expected output before making changes**
- **ALWAYS use Java 21**: Set `JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64` before running any Maven commands
- **Class version errors = wrong Java version**: If you see `UnsupportedClassVersionError`, you're not using Java 21
