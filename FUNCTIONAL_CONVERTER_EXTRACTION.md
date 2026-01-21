# Extracting Functional Converter Analysis Logic to OSGi-Free Core Modules

## ⚠️ Implementation Status

**This implementation serves as a proof-of-concept and architectural reference.** The modules created (`sandbox_common_core`, `sandbox_functional_converter_core`, `sandbox_functional_converter_fast_test`) are not compiled in the regular Maven build due to dependency resolution constraints.

### Why the Build Fails

Eclipse JDT artifacts are distributed through P2 repositories (Eclipse update sites), not Maven Central. The build infrastructure has the following constraints:

1. **P2 repositories** defined in parent POM work for Tycho modules (`eclipse-plugin` packaging)
2. **JAR modules** cannot access P2 repositories - they need dependencies from Maven Central
3. **Eclipse JDT** is not published to Maven Central
4. **Provided scope** still requires artifacts to be resolvable during compilation

### Current Workaround

The modules have been changed to `pom` packaging and include comments explaining they are not built. They remain in the repository as:
- Architectural reference for future improvements
- Documentation of what a fast-testing solution could look like
- Code that demonstrates API replacements needed

The actual functional implementations remain in:
- `sandbox_functional_converter` - Full working implementation
- `sandbox_functional_converter_test` - Working Tycho-based tests

## Overview (Original Intent)

This implementation creates three new OSGi-free JAR modules that extract the analysis logic from `sandbox_functional_converter`, enabling fast unit testing without Tycho or Eclipse runtime dependencies.

## Motivation

### Problem
- Testing functional converter analysis requires full Eclipse/Tycho runtime
- Tests take minutes to run (need Xvfb, Eclipse startup, P2 resolution)
- Difficult to debug and iterate on analysis logic
- Slow CI/CD pipelines

### Solution
- Extract pure analysis logic to OSGi-free modules
- Replace Eclipse internal APIs with standalone utilities
- Enable standard JUnit 5 tests that run in seconds
- Keep original modules for integration testing

## Architecture

### Module Hierarchy

```
sandbox_common_core (jar)
    └── OSGi-free utilities (ASTNodeUtils, ScopeAnalyzerUtils, ReferenceHolder)
         └── sandbox_functional_converter_core (jar)
              └── Analysis logic (StreamPipelineBuilder, PreconditionsChecker, etc.)
                   ├── sandbox_functional_converter (eclipse-plugin)
                   │    └── Uses core for analysis, adds OSGi/UI integration
                   └── sandbox_functional_converter_fast_test (jar, test-only)
                        └── Fast JUnit 5 tests (no Tycho)
```

## Created Modules

### 1. sandbox_common_core

**Purpose**: OSGi-free common utilities

**Contents**:
- `ASTNodeUtils` - Replacement for `org.eclipse.jdt.internal.corext.dom.ASTNodes`
- `ScopeAnalyzerUtils` - Replacement for `org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer`
- `ReferenceHolder` - Simplified version without HelperVisitorProvider
- `AstProcessorBuilder` - Fluent AST traversal (copied from sandbox_common)

**Key Features**:
- No OSGi dependencies
- Pure Java utilities
- Thread-safe where appropriate

### 2. sandbox_functional_converter_core

**Purpose**: Core analysis logic without OSGi

**Contents**: 14 analysis classes copied from sandbox_functional_converter:
- `ProspectiveOperation` - Stream operation representation
- `OperationType` - Operation types with lambda generation
- `StreamPipelineBuilder` - Main pipeline builder
- `PreconditionsChecker` - Safety validation
- `LoopBodyParser` - Statement parsing
- `ReducePatternDetector` - Reducer detection
- `ReducerType` - Reducer type enum
- `IfStatementAnalyzer` - Filter/match analysis
- `LambdaGenerator` - Lambda expression generation
- `StreamConstants` - Method name constants
- `SideEffectChecker` - Side effect detection
- `StatementParsingContext` - Parsing context
- `StatementHandlerContext` - Handler context
- `StatementHandlerType` - Handler type enum

**Adaptations**:
- `ASTNodes.getFirstAncestorOrNull()` → `ASTNodeUtils.getFirstAncestorOrNull()`
- `new ScopeAnalyzer(root).getUsedVariableNames()` → `ScopeAnalyzerUtils.getUsedVariableNames()`

### 3. sandbox_functional_converter_fast_test

**Purpose**: Fast unit tests without Tycho

**Contents**:
- `TestASTHelper` - AST parsing for tests
- `StreamPipelineBuilderTest` - Analysis tests (5 test cases)

**Test Cases**:
1. `testSimpleForEachIsAnalyzable()` - Basic forEach pattern
2. `testLoopWithBreakIsNotSafe()` - Break statement detection
3. `testLoopWithContinueIsSafe()` - Continue statement handling
4. `testLoopWithReducerIsDetected()` - Reducer pattern detection
5. `testNestedLoopIsNotSafe()` - Nested loop prevention

**Benefits**:
- Tests run in seconds (vs minutes with Tycho)
- No Xvfb required
- Standard Maven workflow
- Faster TDD cycle

## Implementation Details

### Replaced Eclipse Internal APIs

| Original | Replacement | Location |
|----------|-------------|----------|
| `org.eclipse.jdt.internal.corext.dom.ASTNodes` | `ASTNodeUtils` | sandbox_common_core |
| `org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer` | `ScopeAnalyzerUtils` | sandbox_common_core |

### Key Design Decisions

1. **Copied Files Instead of Moving**
   - Original files remain in sandbox_functional_converter
   - Allows gradual migration
   - Maintains backward compatibility

2. **Provided Scope for JDT Dependencies**
   - Avoids Maven Central resolution issues
   - Expects JDT classes from Eclipse target platform
   - Works around network/repository issues

3. **Simplified ReferenceHolder**
   - Removed HelperVisitorProvider coupling
   - Pure ConcurrentHashMap implementation
   - Sufficient for analysis needs

4. **Complete Analysis Chain**
   - All 14 analysis classes included
   - Full dependency graph satisfied
   - No missing pieces

## Build Configuration

### Parent POM Changes

Added three modules in correct order:
```xml
<module>sandbox_common_core</module>          <!-- Before sandbox_common -->
<module>sandbox_common</module>
...
<module>sandbox_functional_converter_core</module>  <!-- Before sandbox_functional_converter -->
<module>sandbox_functional_converter</module>
...
<module>sandbox_functional_converter_fast_test</module>  <!-- After all plugins -->
```

### Module POMs

All three modules use:
- `<packaging>jar</packaging>` - Standard JAR modules
- `${java-version}` from parent POM (Java 21)
- `<scope>provided</scope>` for Eclipse JDT dependencies
- Standard Maven compiler plugin (not Tycho)

## Usage

### Running Fast Tests

```bash
# Run fast tests only (no Tycho)
mvn test -pl sandbox_functional_converter_fast_test

# Full build with all tests
mvn verify
```

### Expected Results

Fast tests should:
- Run in seconds
- Not require Xvfb
- Pass without Eclipse runtime
- Validate core analysis logic

## Future Work

### Next Steps

1. **Update sandbox_functional_converter**
   - Add dependency on sandbox_functional_converter_core
   - Remove duplicate analysis class files
   - Keep only OSGi/UI integration code

2. **Expand Test Coverage**
   - Add PreconditionsCheckerTest
   - Add ReducePatternDetectorTest
   - Add LoopBodyParserTest
   - Add IfStatementAnalyzerTest

3. **Performance Validation**
   - Measure test execution time improvement
   - Compare with Tycho test times
   - Document CI/CD benefits

4. **Documentation**
   - Update ARCHITECTURE.md files
   - Document migration path
   - Create developer guide

## Benefits

### Development Workflow
- ✅ Fast TDD cycle (seconds instead of minutes)
- ✅ Easy debugging without Eclipse runtime
- ✅ Standard IDE integration (no Tycho quirks)
- ✅ Faster iteration on analysis logic

### CI/CD Pipeline
- ✅ Faster builds (skip Tycho for fast tests)
- ✅ Earlier feedback (run fast tests first)
- ✅ Parallel testing (no Xvfb contention)
- ✅ Cleaner logs (no Eclipse noise)

### Code Quality
- ✅ Better test coverage (easier to write tests)
- ✅ Cleaner architecture (separation of concerns)
- ✅ Less coupling (no OSGi in analysis logic)
- ✅ Easier porting to Eclipse JDT (pure analysis)

## Constraints and Limitations

### Known Limitations

1. **No Binding Resolution**
   - Fast tests don't resolve bindings
   - Some analysis may be less accurate
   - Full tests still needed for integration

2. **Provided Scope Dependencies**
   - JDT dependencies not resolved from Maven Central
   - May cause issues in some environments
   - Works around current network issues

3. **Duplicate Code**
   - Analysis classes exist in two places
   - Temporary state during migration
   - Will be cleaned up in future PR

### Not Included

- UI code (stays in sandbox_functional_converter)
- CompilationUnitRewrite integration
- Eclipse preference handling
- P2 update site integration

## Validation Checklist

- [x] Phase 1: sandbox_common_core created (reference code)
- [x] Phase 2: sandbox_functional_converter_core created (reference code)
- [x] Phase 3: sandbox_functional_converter_fast_test created (reference code)
- [x] Phase 4: Parent POM updated (modules set to pom packaging)
- [x] Phase 5: Validation
  - [x] Modules skip during regular build (no compilation errors)
  - [x] Full build works without these modules
  - [ ] Fast tests cannot run (dependency resolution blocked)

## What Would Be Needed to Make This Work

To enable these modules in a future iteration, one of the following approaches would be needed:

### Option 1: Separate Build Profile

Create a separate Maven profile that:
1. Excludes these modules from the main reactor build
2. Provides a standalone build script that:
   - Downloads JDT JARs from Eclipse download site
   - Installs them to local Maven repository
   - Builds these modules independently

### Option 2: Use Eclipse Tycho for JAR Modules

Convert these modules to use Tycho even though they're JARs:
1. Change to `eclipse-plugin` packaging
2. Add proper OSGi manifests (MANIFEST.MF)
3. Let Tycho resolve dependencies from P2
4. Accept that they become Eclipse plugins (defeating the "OSGi-free" goal)

### Option 3: Custom Maven Plugin

Create a custom Maven plugin that:
1. Resolves artifacts from P2 repositories
2. Makes them available to regular JAR modules
3. Bridges the gap between P2 and Maven dependency resolution

### Option 4: Wait for JDT on Maven Central

If Eclipse JDT ever publishes to Maven Central, these modules would work as-is (with dependencies uncommented).

## Recommendations

For now, these modules should remain as architectural reference code. The working implementation in `sandbox_functional_converter` with Tycho-based tests is the recommended approach until the build infrastructure can support this use case.

If fast testing is critical, consider:
1. Optimizing the Tycho test setup
2. Running tests in parallel where possible
3. Using test categories to run quick tests first
4. Investing in CI/CD pipeline optimization

## References

- Problem Statement: Issue description
- Eclipse JDT: https://github.com/eclipse-jdt/eclipse.jdt.ui
- Maven Surefire: https://maven.apache.org/surefire/maven-surefire-plugin/
- JUnit 5: https://junit.org/junit5/
