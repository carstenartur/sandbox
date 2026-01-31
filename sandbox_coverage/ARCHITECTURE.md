# Coverage Aggregation - Architecture

## Overview

The **Coverage Aggregation** module (`sandbox_coverage`) is a Maven POM-only module that aggregates JaCoCo code coverage reports from all test modules into a single unified coverage report. It doesn't contain any source code or Eclipse plugins—it serves purely as a Maven build infrastructure component.

## Purpose

- Aggregate code coverage data from all test modules
- Generate unified HTML coverage reports
- Provide single source of truth for project-wide coverage metrics
- Enable coverage tracking for CI/CD pipelines
- Support coverage quality gates

## Module Type

**Build Infrastructure Module** - POM only (`<packaging>pom</packaging>`)

This module:
- ✅ Contains only Maven configuration (pom.xml)
- ✅ No source code (src/ directory absent)
- ✅ No Eclipse plugin artifacts
- ✅ Activated only when `-Pjacoco` profile is used

## How It Works

### JaCoCo Report Aggregation

JaCoCo supports multi-module coverage aggregation through the `report-aggregate` goal:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals>
                <goal>report-aggregate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**How it works**:
1. Test modules run and generate `jacoco.exec` files
2. Coverage module depends on all test modules
3. Maven aggregates all `jacoco.exec` files
4. JaCoCo generates unified HTML report

### Dependency Structure

```
sandbox_coverage (aggregator)
    ├── depends on → sandbox_encoding_quickfix (implementation)
    ├── depends on → sandbox_encoding_quickfix_test (test)
    ├── depends on → sandbox_functional_converter (implementation)
    ├── depends on → sandbox_functional_converter_test (test)
    ├── depends on → sandbox_platform_helper (implementation)
    ├── depends on → sandbox_platform_helper_test (test)
    ├── depends on → sandbox_tools (implementation)
    ├── depends on → sandbox_tools_test (test)
    ├── depends on → sandbox_jface_cleanup (implementation)
    ├── depends on → sandbox_jface_cleanup_test (test)
    ├── depends on → sandbox_junit_cleanup (implementation)
    └── depends on → sandbox_junit_cleanup_test (test)
```

**Dependency Scopes**:
- Implementation modules: `<scope>compile</scope>`
- Test modules: `<scope>test</scope>`

**Rationale**: Both scopes are needed for JaCoCo to correlate test execution data with source code.

### Build Process

```
1. Maven Build (mvn verify -Pjacoco)
   ↓
2. Test Modules Execute
   ├─ Run JUnit tests
   └─ Generate jacoco.exec files
   ↓
3. Coverage Module Processes
   ├─ Collect all jacoco.exec files
   ├─ Load source code from implementation modules
   ├─ Map coverage data to source lines
   └─ Generate aggregate report
   ↓
4. Report Output
   └─ target/site/jacoco-aggregate/index.html
```

### Report Location

**Primary Report**:
```
sandbox_coverage/target/site/jacoco-aggregate/
├── index.html              # Coverage summary
├── jacoco-sessions.html    # Test sessions
└── org.sandbox.jdt/        # Package coverage
    └── internal/
        └── corext/
            └── fix/
                └── ExplicitEncodingCleanUp.html
```

**Individual Module Reports**:
```
sandbox_encoding_quickfix_test/target/site/jacoco/
sandbox_functional_converter_test/target/site/jacoco/
sandbox_platform_helper_test/target/site/jacoco/
... (one per test module)
```

## Maven Profile Configuration

### JaCoCo Profile

The module is activated only when the `jacoco` profile is active:

```bash
# Generate coverage reports
mvn clean verify -Pjacoco

# Skip coverage
mvn clean verify
```

**Profile Activation**:
```xml
<profile>
    <id>jacoco</id>
    <activation>
        <activeByDefault>false</activeByDefault>
    </activation>
</profile>
```

**Why Optional**: Coverage collection adds overhead (~10-20% build time), so it's opt-in.

## Coverage Metrics

### Types of Coverage

JaCoCo reports multiple coverage metrics:

| Metric | Description |
|--------|-------------|
| **Line Coverage** | % of executable lines executed |
| **Branch Coverage** | % of decision branches taken |
| **Instruction Coverage** | % of bytecode instructions executed |
| **Complexity Coverage** | % of cyclomatic complexity paths covered |
| **Method Coverage** | % of methods invoked |
| **Class Coverage** | % of classes instantiated |

### Report Visualization

The HTML report provides:
- **Package-level**: Coverage by package
- **Class-level**: Coverage by class
- **Method-level**: Coverage by method
- **Line-level**: Highlighted source code showing covered/uncovered lines

**Color Coding**:
- 🟢 **Green**: Line fully covered
- 🟡 **Yellow**: Line partially covered (some branches)
- 🔴 **Red**: Line not covered

## Integration with CI/CD

### GitHub Actions

Coverage reports can be used in CI pipelines:

```yaml
- name: Build with Coverage
  run: mvn verify -Pjacoco

- name: Check Coverage
  run: |
    # Extract coverage percentage
    COVERAGE=$(grep -oP 'Total.*?\K[0-9]+(?=%)' sandbox_coverage/target/site/jacoco-aggregate/index.html)
    echo "Coverage: $COVERAGE%"
    
    # Fail if below threshold
    if [ $COVERAGE -lt 80 ]; then
      echo "Coverage below 80%"
      exit 1
    fi
```

### Coverage Badges

Generate coverage badges for README:

```bash
# Extract coverage percentage
COVERAGE=$(xpath sandbox_coverage/target/site/jacoco-aggregate/index.html '//tfoot/tr/td[3]/text()')

# Create badge URL
https://img.shields.io/badge/coverage-${COVERAGE}%25-brightgreen
```

## Package Structure

```
sandbox_coverage/
├── pom.xml                 # Maven configuration
├── .gitignore              # Ignore target directory
└── target/                 # Build output (gitignored)
    └── site/
        └── jacoco-aggregate/   # Aggregated coverage report
```

**Note**: No source code, no src/ directory.

## Design Patterns

### Aggregator Pattern

The module uses Maven's aggregator pattern:
- Depends on all modules to aggregate
- Executes aggregation plugin
- Produces consolidated output

### Separation of Concerns

- **Test modules**: Run tests, generate coverage data
- **Coverage module**: Aggregate data, generate reports
- **Clear responsibility**: Each module has single purpose

## Dependencies

### Required Plugins

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
</plugin>
```

### Module Dependencies

All cleanup implementation modules and their corresponding test modules must be declared as dependencies for coverage aggregation to work.

**Pattern**:
```xml
<dependency>
    <groupId>org.sandbox</groupId>
    <artifactId>sandbox_XXX</artifactId>
    <scope>compile</scope>
</dependency>
<dependency>
    <groupId>org.sandbox</groupId>
    <artifactId>sandbox_XXX_test</artifactId>
    <scope>test</scope>
</dependency>
```

## Usage

### Generate Coverage Report

```bash
# Full build with coverage
mvn clean verify -Pjacoco

# Open coverage report
open sandbox_coverage/target/site/jacoco-aggregate/index.html
```

### View Specific Module Coverage

```bash
# Individual module report
open sandbox_encoding_quickfix_test/target/site/jacoco/index.html
```

### Command-Line Coverage Summary

```bash
# Extract coverage percentage
grep -A 5 "Total" sandbox_coverage/target/site/jacoco-aggregate/index.html
```

## Coverage Goals

### Project Coverage Targets

| Module Type | Target Coverage |
|-------------|----------------|
| Cleanup Implementations | 80-90% |
| UI Components | 60-70% |
| Test Infrastructure | 80%+ |
| Build Modules | N/A |

### Why High Coverage Matters

- **Eclipse JDT Contribution**: High coverage required for upstream contribution
- **Refactoring Safety**: Tests catch regressions during refactoring
- **Code Quality**: Coverage reveals untested code paths
- **Documentation**: Tests serve as usage examples

## Troubleshooting

### No Coverage Report Generated

**Cause**: JaCoCo profile not activated

**Solution**:
```bash
# Ensure -Pjacoco is specified
mvn verify -Pjacoco
```

### Coverage Report Empty

**Cause**: Tests didn't run or failed

**Solution**:
```bash
# Check test execution
mvn test -Pjacoco
# Verify jacoco.exec files exist
find . -name "jacoco.exec"
```

### Missing Modules in Report

**Cause**: Module not declared as dependency in sandbox_coverage/pom.xml

**Solution**: Add module dependency:
```xml
<dependency>
    <groupId>org.sandbox</groupId>
    <artifactId>sandbox_new_module</artifactId>
    <scope>compile</scope>
</dependency>
<dependency>
    <groupId>org.sandbox</groupId>
    <artifactId>sandbox_new_module_test</artifactId>
    <scope>test</scope>
</dependency>
```

## Best Practices

### 1. Always Run Coverage Before PR

```bash
mvn verify -Pjacoco
# Check coverage hasn't decreased
```

### 2. Investigate Uncovered Code

- Red lines indicate untested code
- Add tests for critical paths
- Document why some code can't be tested

### 3. Don't Game the Metrics

- Avoid "testing for coverage"
- Write meaningful assertions
- Test actual behavior, not just execution

### 4. Keep Coverage Data Private

```gitignore
# .gitignore
target/
*.exec
jacoco.xml
```

Don't commit coverage data—regenerate on each build.

## Comparison with Other Tools

### JaCoCo vs. Cobertura

| Feature | JaCoCo | Cobertura |
|---------|--------|-----------|
| Bytecode Instrumentation | ✅ On-the-fly | ❌ Requires modification |
| Performance | Fast | Slower |
| Maven Support | Excellent | Good |
| Branch Coverage | ✅ | ✅ |
| Eclipse Integration | ✅ | Limited |

**Choice**: JaCoCo is preferred for Eclipse projects due to better integration.

### JaCoCo vs. Clover

| Feature | JaCoCo | Clover |
|---------|--------|--------|
| License | Free (EPL) | Commercial |
| Reports | HTML, XML, CSV | HTML, XML, PDF |
| IDE Integration | Eclipse, IntelliJ | Eclipse, IntelliJ |

**Choice**: JaCoCo is free and open-source, suitable for sandbox project.

## Future Enhancements

### Coverage Trend Tracking

Track coverage over time:
```bash
# Store coverage percentage in file
echo "$(date),${COVERAGE}%" >> coverage-history.csv
```

### Coverage Quality Gates

Enforce minimum coverage:
```xml
<execution>
    <id>check</id>
    <goals>
        <goal>check</goal>
    </goals>
    <configuration>
        <rules>
            <rule>
                <limits>
                    <limit>
                        <minimum>0.80</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

### Coverage Diff Reports

Show coverage change between commits:
```bash
# Compare coverage before/after PR
git checkout main
mvn verify -Pjacoco
mv sandbox_coverage/target/site/jacoco-aggregate coverage-before

git checkout feature-branch
mvn verify -Pjacoco
mv sandbox_coverage/target/site/jacoco-aggregate coverage-after

# Generate diff report
jacoco-diff coverage-before coverage-after
```

## Eclipse JDT Contribution

### Coverage Requirements

Eclipse JDT expects high test coverage:
- New features: 80%+ coverage required
- Bug fixes: Add regression test
- Refactorings: Maintain or improve coverage

### Porting Coverage Module

When contributing to Eclipse JDT:
- Eclipse uses similar JaCoCo setup
- Aggregate reports at project level
- Same coverage metrics apply
- Coverage reports part of CI

## References

- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [JaCoCo Maven Plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- [Maven Aggregation](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Eclipse Code Coverage](https://wiki.eclipse.org/EclEmma)

## Summary

The coverage module is a simple but essential build infrastructure component that:
- Aggregates coverage from all test modules
- Generates unified HTML reports
- Supports CI/CD coverage checks
- Helps maintain code quality standards
- Provides visibility into test coverage gaps

Despite its simplicity (POM-only), it plays a critical role in the project's quality assurance strategy.
