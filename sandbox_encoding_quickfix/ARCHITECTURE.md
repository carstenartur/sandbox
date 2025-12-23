# Encoding Quickfix Plugin - Architecture

## Overview

The encoding quickfix plugin automatically replaces platform-dependent default encoding with explicit `StandardCharsets.UTF_8` encoding. This cleanup improves code portability and prevents encoding-related bugs across different platforms and locales.

## Purpose

- Replace `FileReader`/`FileWriter` with explicit charset constructors
- Update `Files.readAllLines()` and similar methods to use explicit charsets
- Add `StandardCharsets.UTF_8` where default encoding is used
- Support Java 11+ with version-aware transformations

## Transformation Examples

### FileReader/FileWriter

**Before**:
```java
FileReader reader = new FileReader("file.txt");  // Uses platform default encoding
```

**After**:
```java
FileReader reader = new FileReader("file.txt", StandardCharsets.UTF_8);
```

### Files Methods

**Before**:
```java
List<String> lines = Files.readAllLines(path);  // Uses platform default encoding
```

**After**:
```java
List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
```

## Core Components

### ExplicitEncodingCleanUp

**Location**: `org.sandbox.jdt.internal.corext.fix.ExplicitEncodingCleanUp`

**Purpose**: Main cleanup implementation that identifies and transforms encoding-related code

**Key Methods**:
- `findEncodingIssues()` - Identifies method invocations and constructors using default encoding
- `rewrite()` - Applies transformations to add explicit charset parameters
- `createRewriteOperations()` - Generates AST rewrite operations

### Supported APIs

The plugin supports explicit encoding for:

1. **FileReader/FileWriter Constructors** (Java 11+)
2. **Files.readAllLines()** methods
3. **Files.readString()** methods (Java 11+)
4. **Files.writeString()** methods (Java 11+)
5. **Scanner** constructors
6. **PrintWriter** constructors
7. **InputStreamReader/OutputStreamWriter** constructors

### Version Compatibility

- **Java 11+**: Full support for all transformations
- **Java 7**: No longer supported (Eclipse no longer supports Java 7)

The cleanup checks the project's Java version and only applies transformations available in that version.

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.ui.*` - UI components and preferences

**Eclipse JDT Correspondence**:
- Maps to `org.eclipse.jdt.internal.corext.fix.*` in Eclipse JDT
- Can be ported by replacing `sandbox` with `eclipse` in package paths

## Design Patterns

### AST Visitor Pattern
Uses Eclipse JDT's AST visitor to identify encoding-related API calls:
```java
compilationUnit.accept(new ASTVisitor() {
    @Override
    public boolean visit(MethodInvocation node) {
        // Check if method uses default encoding
        return true;
    }
});
```

### Rewrite Pattern
Uses AST rewrite to add charset parameters without reformatting entire file:
```java
ListRewrite argumentsRewrite = rewrite.getListRewrite(methodInvocation, 
    MethodInvocation.ARGUMENTS_PROPERTY);
argumentsRewrite.insertLast(charsetArgument, null);
```

## Eclipse JDT Integration

### Current State
The implementation is in the sandbox for experimentation. The package structure mirrors Eclipse JDT for easy porting.

### Contribution Path
To contribute to Eclipse JDT:
1. Replace `org.sandbox` with `org.eclipse` in all package names
2. Move classes to `org.eclipse.jdt.core.manipulation` module
3. Register in Eclipse's cleanup extension points
4. Submit to Eclipse Gerrit for review

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse JDT Core APIs
  - Eclipse JDT UI APIs
  - `sandbox_common` for cleanup constants

## Testing

### Test Module
`sandbox_encoding_quickfix_test` contains comprehensive test cases covering:
- FileReader/FileWriter transformations
- Files method transformations
- Java version compatibility
- Edge cases and error handling

### Test Strategy
Tests use Eclipse's cleanup test framework with before/after code samples:
```java
@Test
public void testFileReaderTransformation() {
    String input = "FileReader r = new FileReader(\"file.txt\");";
    String expected = "FileReader r = new FileReader(\"file.txt\", StandardCharsets.UTF_8);";
    assertRefactoring(input, expected);
}
```

## Known Limitations

1. **Only UTF-8**: Currently only adds `StandardCharsets.UTF_8`, not other charsets
2. **Java 11+ Only**: No support for Java 7/8 (aligned with current Eclipse support)
3. **No Custom Encoding Detection**: Doesn't detect when custom encoding is already specified
4. **No Charset Configuration**: Users cannot choose which charset to apply

## Future Enhancements

- Support for configuring which charset to use (UTF-8, ISO-8859-1, etc.)
- Detection of existing charset parameters to avoid duplicate transformation
- Support for more encoding-related APIs
- Option to preserve existing custom charsets vs. standardizing all to UTF-8

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_encoding_quickfix_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.
