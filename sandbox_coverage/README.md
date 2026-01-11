# Coverage Module

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Coverage** module aggregates JaCoCo code coverage reports from all test modules into a single comprehensive report. This provides a unified view of test coverage across the entire sandbox project.

## Key Features

- 📊 **Aggregated Coverage** - Combines coverage from all test modules
- 📈 **JaCoCo Integration** - Uses JaCoCo for coverage analysis
- 🎯 **Maven Profile** - Activated with `-Pjacoco` profile
- 📄 **HTML Reports** - Generates browsable coverage reports
- 🔍 **Line & Branch Coverage** - Detailed coverage metrics

## Quick Start

### Generate Coverage Report

```bash
# Build with coverage
mvn clean verify -Pjacoco

# View aggregated report
open sandbox_coverage/target/site/jacoco-aggregate/index.html
```

### View Coverage in Eclipse

1. **Install EclEmma** (if not already installed)
   - Help → Install New Software
   - Select "Eclipse Release Site"
   - Install "EclEmma Java Code Coverage"

2. **Run Tests with Coverage**
   - Right-click test class/project
   - Coverage As → JUnit Test

3. **View Results**
   - Coverage view shows results
   - Editor highlights covered/uncovered lines

## Coverage Reports

### Aggregated Report Location

```
sandbox_coverage/target/site/jacoco-aggregate/index.html
```

### Individual Module Reports

Each test module also generates its own report:
```
sandbox_*_test/target/site/jacoco/index.html
```

### Report Structure

The HTML report includes:

#### Overview Page
- Total line coverage percentage
- Total branch coverage percentage  
- Coverage by module
- Summary statistics

#### Module Pages
- Coverage by package
- Coverage by class
- Drill-down to individual files

#### Source Pages
- Line-by-line coverage
- Green = covered
- Red = not covered
- Yellow = partially covered (branches)

## Coverage Metrics

### Line Coverage
Percentage of executable lines that were executed during tests.

**Calculation**: `(executed lines / total lines) × 100`

### Branch Coverage
Percentage of conditional branches that were taken during tests.

**Calculation**: `(executed branches / total branches) × 100`

**Example**: 
```java
if (condition) {  // Branch coverage tracks both true and false paths
    // ...
}
```

### Instruction Coverage
Percentage of Java bytecode instructions executed.

### Complexity Coverage
Based on cyclomatic complexity of methods.

## Maven Configuration

### JaCoCo Profile

Defined in parent pom.xml:

```xml
<profile>
  <id>jacoco</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <executions>
          <execution>
            <goals>
              <goal>prepare-agent</goal>
              <goal>report</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```

### Coverage Module

The `sandbox_coverage` module uses the aggregation feature:

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

## Coverage Goals

### Target Coverage Levels

Recommended coverage goals for sandbox plugins:

| Category | Line Coverage | Branch Coverage |
|----------|--------------|-----------------|
| **Cleanup Logic** | ≥ 80% | ≥ 70% |
| **Utilities** | ≥ 90% | ≥ 80% |
| **UI Code** | ≥ 60% | ≥ 50% |
| **Overall Project** | ≥ 75% | ≥ 65% |

### Why High Coverage?

For Eclipse JDT contribution:
- Demonstrates thorough testing
- Builds confidence in transformations
- Catches edge cases
- Validates refactoring safety

## Improving Coverage

### Identify Gaps

1. **Generate Report**
   ```bash
   mvn verify -Pjacoco
   ```

2. **Open Report**
   - Navigate to uncovered classes
   - Identify uncovered lines

3. **Add Tests**
   - Write tests for uncovered code
   - Focus on critical paths first

### Coverage Best Practices

#### 1. Test Edge Cases
```java
@Test
void testNullInput() { ... }

@Test
void testEmptyList() { ... }

@Test
void testLargeInput() { ... }
```

#### 2. Test Error Paths
```java
@Test
void testInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> {
        // ...
    });
}
```

#### 3. Test Branches
```java
@ParameterizedTest
@ValueSource(booleans = {true, false})
void testConditional(boolean condition) {
    // Tests both branches
}
```

## CI Integration

### GitHub Actions

Coverage is generated in CI:

```yaml
- name: Build with coverage
  run: mvn -Pjacoco verify

- name: Upload coverage reports
  uses: codecov/codecov-action@v3
  with:
    files: ./sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml
```

### Coverage Trends

Track coverage over time:
- Codecov.io integration
- GitHub Actions artifacts
- Trend reports

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Coverage module design
- **[TODO](TODO.md)** - Coverage improvement plans
- **[JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)** - Official JaCoCo docs

## Excluding Code from Coverage

### When to Exclude

Exclude code that:
- Is generated automatically
- Is deprecated and not used
- Is infrastructure/boilerplate
- Cannot be tested (OS-specific, hardware-dependent)

### How to Exclude

Using annotations:
```java
@SuppressWarnings("jacoco") // Custom annotation
public void untestableMethod() {
    // ...
}
```

Or Maven configuration:
```xml
<configuration>
  <excludes>
    <exclude>**/generated/**</exclude>
    <exclude>**/*Exception.class</exclude>
  </excludes>
</configuration>
```

## Troubleshooting

### No Coverage Data

**Symptom**: Report shows 0% coverage

**Solutions**:
- Ensure `-Pjacoco` profile is active
- Verify tests are actually running
- Check JaCoCo agent is attached
- Review Maven output for errors

### Partial Coverage Data

**Symptom**: Some modules missing from report

**Solutions**:
- Verify all test modules are included in build
- Check dependencies in coverage module pom.xml
- Ensure all modules are built before coverage aggregation

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related**: All test modules contribute to the aggregated coverage report
