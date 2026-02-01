# Sandbox JMH Benchmarks

This module contains JMH (Java Microbenchmark Harness) performance benchmarks for the Sandbox project. It provides continuous performance tracking and visualization through GitHub Actions and GitHub Pages.

## Overview

The benchmark suite includes:

- **ASTParsingBenchmark** - Measures Eclipse JDT AST parsing performance for various code sizes
- **PatternMatchingBenchmark** - Compares AST visitor vs regex approaches for pattern detection
- **LoopTransformBenchmark** - Benchmarks loop model construction and stream code generation

## Running Benchmarks Locally

### Prerequisites

- Java 21 or later
- Maven 3.6 or later

### Build the Benchmark JAR

```bash
# From project root
mvn clean package -pl sandbox-benchmarks

# Or from this directory
cd sandbox-benchmarks
mvn clean package
```

This creates `target/benchmarks.jar` (uber-jar with all dependencies).

### Run All Benchmarks

```bash
java -jar target/benchmarks.jar
```

### Run Specific Benchmark

```bash
# Run only ASTParsingBenchmark
java -jar target/benchmarks.jar ASTParsingBenchmark

# Run specific method
java -jar target/benchmarks.jar ASTParsingBenchmark.parseASTWithoutBindings
```

### Generate JSON Output

```bash
java -jar target/benchmarks.jar -rf json -rff results.json
```

### Common JMH Options

```bash
# Quick run with fewer iterations
java -jar target/benchmarks.jar -i 3 -wi 2 -f 1

# Run with specific thread count
java -jar target/benchmarks.jar -t 4

# List all available benchmarks
java -jar target/benchmarks.jar -l

# Help
java -jar target/benchmarks.jar -h
```

## Adding New Benchmarks

1. Create a new class in `src/main/java/org/sandbox/benchmarks/`
2. Add JMH annotations:

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class MyBenchmark {
    
    @Setup
    public void setup() {
        // Initialize benchmark state
    }
    
    @Benchmark
    public void myBenchmarkMethod() {
        // Code to benchmark
    }
}
```

3. Rebuild the benchmark JAR: `mvn clean package`
4. Run your new benchmark: `java -jar target/benchmarks.jar MyBenchmark`

## CI/CD Integration

### GitHub Actions Workflow

The `.github/workflows/benchmark.yml` workflow:

- Triggers on push/PR to main when benchmark or core modules change
- Can be manually triggered via `workflow_dispatch`
- Builds and runs all benchmarks with JSON output
- Uses `benchmark-action/github-action-benchmark@v1` to:
  - Store results in the `gh-pages` branch
  - Generate interactive performance charts
  - Alert on 15% performance regression
  - Comment on PRs when regressions are detected

### Viewing Performance Charts

Performance charts are available at:
```
https://carstenartur.github.io/sandbox/dev/bench/
```

The visualization shows:
- Performance trends over time
- Comparison between commits
- Regression alerts
- Historical data

### Triggering Benchmarks Manually

1. Go to Actions tab in GitHub
2. Select "Benchmark" workflow
3. Click "Run workflow"
4. Select branch and click "Run workflow"

## Benchmark Best Practices

1. **Avoid Dead Code Elimination**: JMH will warn if your benchmark result is unused
   - Return values from `@Benchmark` methods
   - Use `Blackhole.consume()` for side effects

2. **Warm-up is Essential**: JVM JIT compilation needs warm-up
   - Default: 3 warm-up iterations
   - Adjust with `@Warmup(iterations = N)`

3. **Fork JVMs**: Run benchmarks in separate JVM instances
   - Default: `@Fork(1)`
   - Use `@Fork(0)` only for debugging

4. **State Management**: Use `@State` for benchmark data
   - `Scope.Benchmark` - shared across threads
   - `Scope.Thread` - one instance per thread
   - `Scope.Group` - shared within thread group

5. **Time Units**: Choose appropriate units
   - `NANOSECONDS` for very fast operations (< 1μs)
   - `MICROSECONDS` for typical operations (1μs - 1ms)
   - `MILLISECONDS` for slow operations (> 1ms)

## Module Architecture

This is a standalone Maven module (not Tycho/OSGi):

- **Packaging**: JAR (not eclipse-plugin)
- **Build**: Standard Maven with Shade plugin
- **Dependencies**:
  - JMH 1.37 (core + annotation processor)
  - `sandbox-functional-converter-core` (for loop transformation benchmarks)
  - Eclipse JDT Core standalone (for AST operations without OSGi)

## Dependencies

The benchmark module depends on:

1. **sandbox-functional-converter-core** - Plain Java module for loop transformations
2. **Eclipse JDT Core** - Standalone JAR for AST parsing (no OSGi required)

Build the core module first:
```bash
mvn clean install -pl sandbox-functional-converter-core
```

## Troubleshooting

### Build Fails with "Could not find artifact"

Make sure the core module is installed:
```bash
mvn clean install -pl sandbox-functional-converter-core
```

### Benchmarks Run Too Slowly

Reduce iterations for quick testing:
```bash
java -jar target/benchmarks.jar -i 2 -wi 1 -f 1
```

### Out of Memory Errors

Increase JVM heap:
```bash
java -Xmx2g -jar target/benchmarks.jar
```

## References

- [JMH Documentation](https://github.com/openjdk/jmh)
- [JMH Samples](https://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/)
- [github-action-benchmark](https://github.com/benchmark-action/github-action-benchmark)
