# JFace Cleanup Plugin - Architecture

## Overview

The JFace cleanup plugin provides automated refactorings and modernizations for Eclipse JFace code. JFace is Eclipse's UI toolkit built on top of SWT (Standard Widget Toolkit).

## Purpose

- Modernize JFace API usage
- Replace deprecated JFace methods with current alternatives
- Apply JFace best practices automatically
- Simplify common JFace patterns

## Transformation Examples

### Example Transformations

The plugin focuses on JFace-specific cleanup opportunities such as:
- Simplifying viewer creation patterns
- Updating deprecated dialog APIs
- Modernizing resource management
- Applying JFace coding conventions

## Core Components

### JFaceCleanUp

**Location**: `org.sandbox.jdt.internal.corext.fix.JFaceCleanUp`

**Purpose**: Main cleanup implementation for JFace code modernization

**Key Features**:
- Identifies deprecated JFace API usage
- Applies JFace best practices
- Updates to newer JFace patterns

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.ui.*` - UI components and preferences

**Eclipse JDT Correspondence**:
- Maps to `org.eclipse.jdt.internal.corext.fix.*` pattern
- Can be ported by replacing `sandbox` with `eclipse`

## Design Patterns

### AST Visitor Pattern
Identifies JFace API calls that need modernization:
```java
compilationUnit.accept(new ASTVisitor() {
    @Override
    public boolean visit(MethodInvocation node) {
        // Check for deprecated JFace API
        return true;
    }
});
```

## Eclipse JDT Integration

### Current State
Experimental JFace cleanup implementations. May benefit from community feedback before Eclipse contribution.

### Contribution Path
1. Gather feedback on useful JFace transformations
2. Implement high-value cleanups
3. Test thoroughly with real-world JFace code
4. Port to Eclipse JDT structure
5. Submit for community review

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse JDT Core APIs
  - Eclipse JFace APIs
  - `sandbox_common` for cleanup constants

## Testing

### Test Module
`sandbox_jface_cleanup_test` contains test cases for JFace transformations.

## Known Limitations

1. **Limited Coverage**: Currently supports a subset of JFace APIs
2. **Version-Specific**: Some transformations may be Eclipse version-specific
3. **Manual Review Needed**: Complex JFace patterns may require manual review

## Future Enhancements

- Expand coverage to more JFace APIs
- Support for SWT cleanup as well
- Integration with Eclipse Platform cleanups
- JFace databinding modernization

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_jface_cleanup_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.
