# Sandbox Tools Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#tools) | [TODO](TODO.md)

## Overview

The sandbox tools plugin contains the While-to-For loop converter cleanup. This implementation was successfully contributed to and merged into the Eclipse JDT project. The module remains in the sandbox repository for reference and testing purposes.

## Purpose

- Convert while loops to for loops where applicable
- Demonstrate successful Eclipse JDT contribution workflow
- Serve as reference implementation for future contributions
- Maintain test cases and examples

## Transformation Example

### While to For Conversion

**Before**:
```java
int i = 0;
while (i < 10) {
    System.out.println(i);
    i++;
}
```

**After**:
```java
for (int i = 0; i < 10; i++) {
    System.out.println(i);
}
```

## Core Components

### WhileToForConverter

**Location**: `org.sandbox.jdt.internal.corext.fix.WhileToForConverter`

**Purpose**: Identifies while loops that can be converted to for loops

**Key Methods**:
- `canConvert()` - Checks if while loop is convertible
- `convert()` - Transforms while to for loop
- `extractInitializer()` - Extracts loop initialization
- `extractIncrement()` - Extracts loop increment

## Conversion Criteria

A while loop can be converted to a for loop if:
1. Loop variable is initialized immediately before while
2. Loop variable is incremented at end of loop body
3. Loop condition uses the loop variable
4. Loop variable is not modified elsewhere in loop body

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.ui.*` - UI components (if any)

**Eclipse JDT Correspondence**:
- This code was contributed to Eclipse JDT
- Now exists in `org.eclipse.jdt.internal.corext.fix.*` in Eclipse JDT

## Eclipse JDT Contribution

### Contribution Status
**✅ SUCCESSFULLY MERGED INTO ECLIPSE JDT**

This implementation was contributed to the Eclipse JDT project and is now part of the official Eclipse JDT cleanup framework.

### Contribution Timeline
The feature was:
1. Developed in sandbox for experimentation
2. Tested extensively with comprehensive test cases
3. Refined based on feedback
4. Submitted to Eclipse Gerrit
5. Reviewed by Eclipse JDT maintainers
6. Merged into Eclipse JDT core

### Lessons Learned
This successful contribution demonstrates:
- Value of sandbox environment for experimentation
- Importance of comprehensive testing before contribution
- Benefits of following Eclipse code conventions from the start
- Effectiveness of the sandbox → Eclipse JDT workflow

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Status**: Reference implementation (active code is in Eclipse JDT)

## Testing

### Test Module
`sandbox_tools_test` contains the original test cases used during development and contribution.

### Test Value
These tests serve as:
- Reference for testing similar cleanups
- Examples of comprehensive test coverage
- Validation that sandbox version matches Eclipse version

## Current Status

### Repository Purpose
This module remains in the sandbox repository to:
1. **Demonstrate Success**: Show example of successful Eclipse contribution
2. **Reference Implementation**: Provide reference for future contributions
3. **Test Archive**: Preserve original test cases
4. **Documentation**: Document the contribution process

### Not Actively Developed
This module is **not actively developed** since the feature is now part of Eclipse JDT. New development happens in the Eclipse JDT repository.

## Integration Points

### Eclipse JDT Core Integration

The While-to-For converter integrates with Eclipse JDT's core transformation framework:

1. **AST Manipulation**: Uses Eclipse JDT AST APIs
   - Identifies `WhileStatement` nodes
   - Extracts initialization from preceding statement
   - Extracts increment from last statement in while body
   - Constructs `ForStatement` with extracted components

2. **Data Flow Analysis**: Tracks loop variable usage
   - Ensures loop variable only used for iteration
   - Detects modifications outside increment statement
   - Verifies safety of transformation

3. **Scope Analysis**: Handles variable scope correctly
   - Loop variable must be declared immediately before while
   - Ensures no other statements between initialization and while
   - Preserves variable visibility after transformation

### Cleanup Framework Integration

Successfully integrated into Eclipse JDT cleanup framework:

1. **Extension Point**: `org.eclipse.jdt.ui.cleanUps` (in Eclipse JDT)
   - Cleanup ID: Part of Eclipse's standard cleanups
   - Available in Eclipse → Source → Clean Up preferences
   - Can be enabled in Save Actions

2. **Quick Assist**: Also available as quick assist (Ctrl+1)
   - User can trigger on individual while loop
   - Shows "Convert to for loop" proposal
   - Single-click application

3. **Refactoring Support**: Part of Eclipse's refactoring infrastructure
   - Undo/redo support
   - Preview before applying
   - Batch processing across multiple files

## Algorithms and Design Decisions

### Loop Convertibility Algorithm

**Decision**: Only convert while loops matching specific pattern

**Pattern Requirements**:
```
1. Loop variable declared immediately before while
2. Simple increment at end of loop body (i++, i+=1, etc.)
3. Loop condition uses the loop variable
4. No break, continue, return in loop body
5. Loop variable not modified elsewhere in body
```

**Algorithm**:
```
1. Identify WhileStatement node
2. Check previous statement is variable declaration
3. Extract variable name and initializer
4. Parse while condition to extract comparison
5. Check last statement in body is increment
6. Verify loop variable not modified elsewhere
7. If all checks pass → convertible
```

**Why These Requirements?**:
- Ensures transformation is semantically equivalent
- Prevents breaking code with complex control flow
- Maintains readability (for loops are clearer for iteration)

### Variable Scope Preservation

**Decision**: Move loop variable declaration into for statement

**Example**:
```java
// Before
int i = 0;
while (i < 10) {
    System.out.println(i);
    i++;
}
// 'i' still accessible here

// After
for (int i = 0; i < 10; i++) {
    System.out.println(i);
}
// 'i' no longer accessible here
```

**Trade-off**:
- **Pro**: More idiomatic for loop (variable scoped to loop)
- **Con**: Changes variable scope (breaks code if 'i' used after loop)

**Solution**: Only convert if variable not used after loop

**Implementation**: Data flow analysis checks for variable usage after while statement

### Increment Expression Recognition

**Decision**: Support multiple increment patterns

**Supported Patterns**:
- Postfix: `i++`, `i--`
- Prefix: `++i`, `--i`
- Compound assignment: `i += 1`, `i -= 1`

**Not Supported** (intentionally):
- Complex expressions: `i += foo()` (side effects unclear)
- Multiple increments: `i++; j++` (multiple loop variables)
- Conditional increments: `if (...) i++` (not always executed)

**Rationale**: Conservative approach ensures correctness

### Why Contribution to Eclipse JDT Was Successful

**Key Success Factors**:

1. **Well-Tested**: Comprehensive test coverage in sandbox_tools_test
   - Edge cases covered
   - Both positive and negative tests
   - Clear documentation of expected behavior

2. **Followed Conventions**: Code style matched Eclipse JDT
   - Package structure: `org.sandbox.*` → `org.eclipse.*`
   - Naming conventions aligned
   - AST patterns familiar to JDT maintainers

3. **Clear Value Proposition**: Obvious benefit to users
   - Common code pattern improvement
   - Safe transformation (conservative checks)
   - Improves code readability

4. **Good Documentation**: Architecture and design explained
   - Rationale for design decisions documented
   - Limitations clearly stated
   - Examples provided

## Cross-References

### Root README Sections

This architecture document relates to:

- [Projects → sandbox_tools](../README.md#6-sandbox_tools) - User-facing description
- [Contributing](../README.md#contributing) - Contribution workflow demonstrated here
- [Build Instructions](../README.md#build-instructions) - Original build/test process

### Eclipse JDT Repository

- **Merged Code**: Now in `org.eclipse.jdt.internal.corext.fix.*` in Eclipse JDT
- **Issue/PR**: [Link to Eclipse Gerrit review if available]
- **Documentation**: Part of Eclipse JDT cleanup documentation

### Related Modules

- **sandbox_tools_test** - Original test cases (reference for testing other cleanups)
- **sandbox_common** - Pattern for cleanup registration

## Future Use

### For Contributors
Use this module as a reference when contributing new cleanups to Eclipse JDT:
- Study the code structure
- Review the test coverage approach
- Understand the contribution workflow
- Follow similar patterns for new cleanups

### For Sandbox Development
This module demonstrates:
- Quality standards for Eclipse contribution
- Test coverage requirements
- Code organization patterns
- Documentation standards

## References

- [Eclipse JDT Repository](https://github.com/eclipse-jdt/eclipse.jdt.ui)
- [Eclipse Gerrit](https://git.eclipse.org/r/)
- [Eclipse Contribution Guide](https://wiki.eclipse.org/Development_Resources/Contributing_via_Git)

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_tools_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.

## Contact

For questions about this module or the contribution process, please open an issue in the repository.
