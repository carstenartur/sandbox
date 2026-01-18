# Extracting Functional Converter Analysis Logic to OSGi-Free Core Modules

## Overview

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

- [x] Phase 1: sandbox_common_core created
- [x] Phase 2: sandbox_functional_converter_core created
- [x] Phase 3: sandbox_functional_converter_fast_test created
- [x] Phase 4: Parent POM updated
- [ ] Phase 5: Validation
  - [ ] Fast tests run successfully
  - [ ] Full build still works
  - [ ] No compilation errors

## References

- Problem Statement: Issue description
- Eclipse JDT: https://github.com/eclipse-jdt/eclipse.jdt.ui
- Maven Surefire: https://maven.apache.org/surefire/maven-surefire-plugin/
- JUnit 5: https://junit.org/junit5/
