# Encoding Quickfix Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#2-sandbox_encoding_quickfix) | [TODO](TODO.md) | [Test Documentation](../sandbox_encoding_quickfix_test/README.md)

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

## Supported APIs - Technical Details

The plugin supports explicit encoding for a comprehensive set of Java APIs:

| Class / API | Encoding Behavior | Cleanup Action |
|-------------|------------------|----------------|
| `OutputStreamWriter` | Requires explicit encoding | Replace `"UTF-8"` or add missing `StandardCharsets.UTF_8` |
| `InputStreamReader` | Same | Add `StandardCharsets.UTF_8` where missing |
| `FileReader` / `FileWriter` | Implicit platform encoding | Replace with stream + `InputStreamReader` + charset |
| `Scanner(InputStream)` | Platform encoding | Add charset constructor if available (Java 10+) |
| `PrintWriter(OutputStream)` | Platform encoding | Use new constructor with charset if possible |
| `Files.newBufferedReader(Path)` | Platform encoding by default | Use overload with charset |
| `Files.newBufferedWriter(Path)` | Same | Use overload with charset |
| `Files.readAllLines(Path)` | Platform encoding | Use `readAllLines(path, charset)` if available |
| `Files.readString(Path)` | Available since Java 11 / 21+ | Use with charset overload |
| `Charset.forName("UTF-8")` | Literal resolution | Replace with `StandardCharsets.UTF_8` |
| `Channels.newReader(...)` | Charset overload available since Java 11 | Use it when applicable |
| `InputSource.setEncoding(String)` | Not a stream – SAX API | Replace string literal `"UTF-8"` with constant if possible |

### Version Compatibility

The cleanup implements Java version-aware transformations:

| Java Version | Supported Transformations |
|--------------|---------------------------|
| **Java < 7** | Cleanup is **disabled** – `StandardCharsets` not available |
| **Java 7–10** | Basic replacements using `StandardCharsets.UTF_8`, stream wrapping, and exception removal |
| **Java 11+** | Adds support for `Files.newBufferedReader(path, charset)` and `Channels.newReader(...)` |
| **Java 21+** | Enables usage of `Files.readString(...)` and `Files.writeString(...)` with charset |

### Cleanup Mode × Java Version Matrix

| Java Version | Prefer UTF-8 | Keep Behavior | Aggregate UTF-8 | Files.readString / Channels |
|--------------|--------------|---------------|-----------------|----------------------------|
| Java 7       | ✅            | ✅             | ✅               | ❌                          |
| Java 10      | ✅            | ✅             | ✅               | ❌                          |
| Java 11–20   | ✅            | ✅             | ✅               | ✅                          |
| Java 21+     | ✅            | ✅             | Optional        | ✅ (modern API encouraged)  |

The cleanup checks the project's Java version and only applies transformations available in that version.

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.ui.*` - UI components and preferences

**Eclipse JDT Correspondence**:
- Maps to `org.eclipse.jdt.internal.corext.fix.*` in Eclipse JDT
- Can be ported by replacing `sandbox` with `eclipse` in package paths

## Design Patterns

### Cleanup Strategy Implementation

The plugin implements three strategies for handling encoding transformations:

#### Strategy Pattern
Each cleanup strategy is implemented as a separate configuration:
- **PREFER_UTF8**: Aggressively replaces all platform-default and explicit UTF-8 references
- **KEEP**: Conservative approach, only replaces explicit `"UTF-8"` string literals
- **AGGREGATE**: Creates a class-level constant and redirects all references to it

#### Charset Literal Recognition

The cleanup recognizes these standard charset string literals:

| String Literal | Replacement Constant |
|----------------|---------------------|
| `"UTF-8"` | `StandardCharsets.UTF_8` |
| `"US-ASCII"` | `StandardCharsets.US_ASCII` |
| `"ISO-8859-1"` | `StandardCharsets.ISO_8859_1` |
| `"UTF-16"` | `StandardCharsets.UTF_16` |
| `"UTF-16BE"` | `StandardCharsets.UTF_16BE` |
| `"UTF-16LE"` | `StandardCharsets.UTF_16LE` |

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

### Test Files Referenced
- `Java22/ExplicitEncodingPatterns.java`
- `Java10/ExplicitEncodingPatternsPreferUTF8.java`
- `Java10/ExplicitEncodingPatternsKeepBehavior.java`
- `Java10/ExplicitEncodingPatternsAggregateUTF8.java`
- `Java10/ExplicitEncodingCleanUpTest.java`

## Known Limitations

1. **Dynamic Encodings**: Encodings read from configuration files or variables are left untouched
2. **Aggregation Field Conflicts**: Aggregation introduces class-level fields (may require conflict checks)
3. **Non-I/O Encoding Usage**: Cleanup logic avoids modifying non-I/O encoding usages
4. **Only Standard Charsets**: Currently only handles charsets available in `StandardCharsets`
5. **No Custom Encoding Detection**: Doesn't detect when custom encoding is already properly specified

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
