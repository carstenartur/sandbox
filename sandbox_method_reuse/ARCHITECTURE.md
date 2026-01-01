# Method Reusability Finder - Architecture

## Overview

The Method Reusability Finder is an Eclipse JDT cleanup plugin that analyzes selected methods to identify potentially reusable code patterns across the codebase. This helps developers discover duplicate or similar code that could be refactored to improve code quality and maintainability.

## Design Goals

1. **Code Duplication Detection**: Identify similar code patterns using both token-based and AST-based analysis
2. **Intelligent Matching**: Recognize code similarity even when variable names differ
3. **Eclipse Integration**: Seamlessly integrate as a cleanup action in Eclipse JDT
4. **Performance**: Efficient analysis that scales to large codebases
5. **Portability**: Easy integration into Eclipse JDT core (following sandbox package structure)

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                 MethodReuseCleanUp                      │
│              (UI Integration Layer)                      │
│  - Wrapper for MethodReuseCleanUpCore                   │
│  - Extends AbstractCleanUpCoreWrapper                   │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ↓
┌─────────────────────────────────────────────────────────┐
│              MethodReuseCleanUpCore                      │
│                (Core Cleanup Logic)                      │
│  - find() - Identifies reusable methods                 │
│  - rewrite() - Applies transformations                  │
│  - Coordinates helper classes                           │
└─────────────────────┬───────────────────────────────────┘
                      │
                      │ delegates to
                      ↓
        ┌─────────────────────────────────┐
        │       Helper Classes             │
        │    (Analysis Services)           │
        └─────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        │                           │
        ↓                           ↓
┌──────────────────┐      ┌──────────────────┐
│ MethodReuseFinder│      │MethodSignature   │
│                  │      │   Analyzer       │
│ - findSimilar()  │      │                  │
│ - compare()      │      │ - analyze()      │
│ - similarity()   │      │ - compare()      │
└────────┬─────────┘      └──────────────────┘
         │
         ↓
┌──────────────────┐
│ CodePattern      │
│   Matcher        │
│                  │
│ - matchAST()     │
│ - tokenize()     │
│ - normalize()    │
└──────────────────┘
```

## Core Components

### 1. MethodReuseCleanUp (UI Integration)

**Location**: `org.sandbox.jdt.internal.ui.fix.MethodReuseCleanUp`

**Responsibilities**:
- UI wrapper for the core cleanup logic
- Extends `AbstractCleanUpCoreWrapper<MethodReuseCleanUpCore>`
- Handles Eclipse cleanup framework integration

**Usage**: Registered in `plugin.xml` as an Eclipse cleanup extension point

### 2. MethodReuseCleanUpCore (Core Logic)

**Location**: `org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpCore`

**Responsibilities**:
- Implements the core cleanup algorithm
- Coordinates helper classes for analysis
- Manages AST traversal and method detection
- Creates refactoring proposals

**Key Methods**:
- `find()` - Scans AST for method declarations to analyze
- `rewrite()` - Creates markers/warnings for similar methods found
- `process()` - Orchestrates the analysis workflow

### 3. MethodReuseFinder (Similarity Analysis)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.MethodReuseFinder`

**Responsibilities**:
- Searches the project for similar methods
- Computes similarity scores between methods
- Uses both AST-based and token-based comparison

**Analysis Techniques**:
- **Token-based similarity**: Compares normalized token sequences
- **AST-based similarity**: Compares abstract syntax tree structures
- **Variable name normalization**: Ignores variable name differences
- **Control flow analysis**: Matches similar control structures

**Key Methods**:
- `findSimilarMethods()` - Searches project for similar code
- `computeSimilarity()` - Calculates similarity percentage
- `isReusable()` - Determines if method is a good refactoring candidate

### 4. MethodSignatureAnalyzer (Signature Analysis)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.MethodSignatureAnalyzer`

**Responsibilities**:
- Analyzes method signatures
- Compares parameter types and return types
- Identifies compatible method signatures

**Key Methods**:
- `analyzeSignature()` - Extracts signature information
- `areCompatible()` - Determines if signatures are compatible
- `suggestRefactoring()` - Proposes signature harmonization

### 5. CodePatternMatcher (Pattern Matching)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.CodePatternMatcher`

**Responsibilities**:
- AST-based pattern matching
- Normalizes code structures for comparison
- Identifies common code patterns

**Key Methods**:
- `matchPattern()` - Matches AST patterns
- `normalizeAST()` - Normalizes AST for comparison
- `extractPattern()` - Extracts reusable patterns

### 6. InlineCodeSequenceFinder (Inline Detection)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.InlineCodeSequenceFinder`

**Responsibilities**:
- Searches method bodies for inline code sequences
- Finds code that matches a target method's body
- Identifies refactoring opportunities within methods

**Key Methods**:
- `findInlineSequences()` - Searches for matching inline code
- `searchInMethod()` - Examines individual methods for matches
- `InlineSequenceMatch` - Result class with match details

### 7. CodeSequenceMatcher (Sequence Matching)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.CodeSequenceMatcher`

**Responsibilities**:
- AST subtree matching with variable normalization
- Recognizes structurally equivalent code with different variable names
- Creates variable mappings between target and candidate code

**Key Methods**:
- `matchSequence()` - Matches statement sequences
- `matchStatement()` - Matches individual statements
- `VariableMappingMatcher` - Custom ASTMatcher for variable tracking

### 8. VariableMapping (Variable Tracking)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.VariableMapping`

**Responsibilities**:
- Tracks variable name mappings
- Ensures consistent bidirectional mappings
- Validates mapping consistency

**Key Methods**:
- `addMapping()` - Adds or verifies a variable mapping
- `getCandidateName()` - Looks up mapped name
- `isValid()` - Checks if mapping is valid

### 9. MethodCallReplacer (Code Generation)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.MethodCallReplacer`

**Responsibilities**:
- Generates method invocation replacement code
- Creates argument lists based on variable mapping
- Applies AST rewrites to replace code sequences

**Key Methods**:
- `createMethodCall()` - Creates a method invocation node
- `replaceWithMethodCall()` - Applies the replacement
- `canCreateMethodCall()` - Validates replacement feasibility

### 10. SideEffectAnalyzer (Safety Analysis)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.SideEffectAnalyzer`

**Responsibilities**:
- Analyzes semantic safety of replacements
- Detects field modifications and side effects
- Checks for complex control flow

**Key Methods**:
- `isSafeToReplace()` - Determines if replacement is safe
- `hasFieldModifications()` - Checks for field modifications
- `hasUnsafeMethodCalls()` - Checks for potentially unsafe calls

## Configuration

Cleanup options are defined in `MYCleanUpConstants`:
- `METHOD_REUSE_CLEANUP` - Enable/disable the cleanup
- `METHOD_REUSE_INLINE_SEQUENCES` - Enable inline code sequence detection

## Integration Points

### Eclipse Extension Points
- `org.eclipse.jdt.ui.cleanUps` - Registers the cleanup

### Dependencies
- `org.eclipse.jdt.core` - JDT core APIs
- `org.eclipse.jdt.ui` - JDT UI integration
- `org.eclipse.jdt.core.manipulation` - AST manipulation
- `sandbox_common` - Shared utilities

## Future Enhancements

1. **Machine Learning**: Use ML models to improve similarity detection
2. **Refactoring Automation**: Automatically extract common code into shared methods
3. **Cross-project Analysis**: Search for similar methods across multiple projects
4. **Performance Optimization**: Cache analysis results, incremental analysis
5. **Visual Diff**: Show side-by-side comparison of similar methods

## Implementation Status

The inline code sequence detection feature is now fully implemented and integrated:

### Completed Features
- **Inline Code Sequence Detection**: Identifies code sequences in method bodies that match the body of existing methods and can be replaced with method calls
- **Variable Mapping**: Normalizes variable names to recognize structurally equivalent code with different naming
- **Expression Mapping**: Supports matching complex expressions (like method calls) as method arguments
- **Return Statement Handling**: Matches return statements with variable declarations for seamless refactoring
- **AST Rewriting**: Properly replaces matched code sequences with appropriate method invocations
- **Test Coverage**: Three comprehensive test scenarios covering simple, expression-based, and multi-statement cases

### Implementation Highlights
1. **MethodReuseCleanUpFixCore**: Enum-based operation management following JUnitCleanUpFixCore pattern
2. **Enhanced CodeSequenceMatcher**: Special handling for return statement vs variable declaration matching
3. **Expression-Aware Variable Mapping**: Stores both simple name mappings and complex expression mappings
4. **Smart Method Call Generation**: Uses expression mappings to create proper argument lists

### Pending Work
- Basic method similarity detection across different methods (future enhancement)
- AST-based pattern matching for finding similar code patterns
- Integration with Eclipse cleanup framework
- Performance optimizations and caching

See TODO.md for detailed pending features and improvements.
