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
