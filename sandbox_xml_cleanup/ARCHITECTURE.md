# XML Cleanup Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#xml_cleanup) | [TODO](TODO.md)

## Overview

The XML cleanup plugin provides automated refactoring and optimization for PDE-relevant XML files in Eclipse projects. It focuses on reducing file size while maintaining semantic integrity through XSLT transformation, whitespace normalization, and optional indentation.

## Purpose

- Optimize PDE XML configuration files for size and consistency
- Apply secure XSLT transformations with whitespace normalization
- Convert leading spaces to tabs (4 spaces → 1 tab)
- Provide optional indentation control (default: OFF for size reduction)
- Integrate with Eclipse workspace APIs for safe file updates

## Supported XML Types (PDE Files Only)

The plugin **only** processes PDE-relevant XML files in typical Eclipse plugin locations:

### Supported File Names
- `plugin.xml` - Eclipse plugin manifests
- `feature.xml` - Eclipse feature definitions
- `fragment.xml` - Eclipse fragment manifests

### Supported File Extensions
- `*.exsd` - Extension point schema definitions
- `*.xsd` - XML schema definitions

### Supported Locations
Files must be in one of these locations to be processed:
- **Project root** - Files directly in project folder
- **OSGI-INF** - OSGi declarative services directory
- **META-INF** - Manifest and metadata directory

**Note**: All other XML files (e.g., `pom.xml`, `build.xml`, arbitrary `*.xml` files) are **ignored** to avoid unintended transformations.

## Transformation Process

### 1. XSLT Transformation
- Uses `formatter.xsl` stylesheet from classpath
- Applies secure XML processing (external DTD/entities disabled)
- Preserves XML structure, comments, and content
- **Default: `indent="no"`** - Produces compact output for size reduction
- **Optional: `indent="yes"`** - Enabled via `XML_CLEANUP_INDENT` preference

### 2. Whitespace Normalization
After XSLT transformation, the following post-processing is applied:

- **Reduce excessive empty lines** - Maximum 2 consecutive empty lines
- **Leading space to tab conversion** - Only at line start (not inline text)
  - Converts groups of 4 leading spaces to 1 tab
  - Preserves remainder spaces (e.g., 5 spaces → 1 tab + 1 space)
  - **Does NOT touch inline text or content nodes**

### 3. Change Detection
- Only writes file if content actually changed
- Uses Eclipse workspace APIs (`IFile.setContents()`)
- Maintains file history (`IResource.KEEP_HISTORY`)
- Refreshes resource after update

## Core Components

### XMLPlugin

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.XMLPlugin`

**Purpose**: Main cleanup processor for PDE XML files

**Key Features**:
- Filters files by name, extension, and location (PDE-relevant only)
- Scans project using Eclipse resource APIs
- Avoids duplicate processing via cache
- Creates meaningful `XMLCandidateHit` objects with file info
- Integrates with Eclipse `ILog` for proper logging (no `System.out`/`printStackTrace`)

### SchemaTransformationUtils

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils`

**Purpose**: XSLT transformation and post-processing utilities

**Key Features**:
- Loads `formatter.xsl` from classpath resources
- Configures secure `TransformerFactory` settings
- Supports configurable indentation (default: OFF)
- Performs whitespace normalization
- Converts leading 4-space indentation to tabs

### XMLCandidateHit

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.XMLCandidateHit`

**Purpose**: Represents a candidate XML file for cleanup

**Fields**:
- `IFile file` - The XML file to process
- `String originalContent` - Original file content
- `String transformedContent` - Transformed content after processing
- `ASTNode whileStatement` - Placeholder for Eclipse cleanup framework

### XMLCleanUpFixCore

**Location**: `org.sandbox.jdt.internal.corext.fix.XMLCleanUpFixCore`

**Purpose**: Enum-based registry of XML cleanup operations

**Features**:
- Registers `XMLPlugin` as `ECLIPSEPLUGIN` cleanup
- Provides static `setEnableIndent(boolean)` method
- Creates rewrite operations for Eclipse cleanup framework

### XMLCleanUpCore

**Location**: `org.sandbox.jdt.internal.ui.fix.XMLCleanUpCore`

**Purpose**: Eclipse cleanup integration

**Features**:
- Reads `XML_CLEANUP` and `XML_CLEANUP_INDENT` preferences
- Configures indent preference before processing
- Creates `CompilationUnitRewriteOperation` for file updates

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.corext.fix.helper.*` - Transformation utilities
- `org.sandbox.jdt.internal.ui.fix.*` - UI components and preferences

**Eclipse JDT Correspondence**:
- Maps to `org.eclipse.jdt.internal.corext.fix.*` pattern
- Can be ported by replacing `sandbox` with `eclipse`
- Follows Eclipse PDE conventions for plugin XML cleanup

## Design Patterns

### Secure XML Processing
```java
TransformerFactory factory = TransformerFactory.newInstance();
factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
```

### Leading Space to Tab Conversion
```java
Pattern leadingSpaces = Pattern.compile("^( {4})+", Pattern.MULTILINE);
Matcher matcher = leadingSpaces.matcher(content);
// Replace only at line start, not inline
```

### Eclipse Workspace Integration
```java
byte[] newContent = transformedContent.getBytes(StandardCharsets.UTF_8);
ByteArrayInputStream inputStream = new ByteArrayInputStream(newContent);
file.setContents(inputStream, IResource.KEEP_HISTORY, null);
file.refreshLocal(IResource.DEPTH_ZERO, null);
```

## Eclipse Integration

### Cleanup Preferences

**Default Behavior** (when `XML_CLEANUP` is enabled):
- `indent="no"` - Compact output, no extra whitespace
- Reduces file size by removing unnecessary whitespace
- Converts leading spaces to tabs
- Preserves semantic content

**Optional Behavior** (when `XML_CLEANUP_INDENT` is enabled):
- `indent="yes"` - Minimal indentation applied
- Still converts leading spaces to tabs
- Slightly larger file size but more readable

### Constants

Defined in `sandbox_common/src/org/sandbox/jdt/internal/corext/fix2/MYCleanUpConstants.java`:

- `XML_CLEANUP` - Enable XML cleanup (default: OFF)
- `XML_CLEANUP_INDENT` - Enable indentation (default: OFF)

### Integration Points

- Registered in `plugin.xml` as `org.eclipse.jdt.ui.cleanup.xmlcleanup`
- Uses Eclipse resource visitor pattern to scan projects
- Leverages Eclipse logging framework (`ILog`, `Status`)
- Integrates with Eclipse cleanup framework

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse Core Resources
  - Eclipse JDT Core
  - XML parsing libraries (built-in Java XML APIs)
  - `sandbox_common` for cleanup constants

## Testing

### Test Module
`sandbox_xml_cleanup_test` contains test cases for XML transformations with focus on:
- Size reduction verification
- Semantic equality (using XMLUnit, ignoring whitespace)
- Idempotency (second run produces no change)
- Leading-indent-only tab conversion
- PDE file filtering accuracy

## Known Limitations

1. **PDE Files Only**: Only processes plugin.xml, feature.xml, fragment.xml, *.exsd, *.xsd in specific locations
2. **Location Restricted**: Files must be in project root, OSGI-INF, or META-INF
3. **Leading Tabs Only**: Tab conversion only applies to leading whitespace, not inline content
4. **No Schema Validation**: Doesn't validate against XML schemas (relies on Eclipse PDE validation)
5. **Requires Compilation Unit**: Integration with Eclipse cleanup framework requires Java compilation unit context

## Security Considerations

### Secure XML Processing
- External DTD access disabled
- External entity resolution disabled
- DOCTYPE declarations disallowed
- Secure processing mode enabled

### No Arbitrary File Access
- Only processes PDE-relevant files in known locations
- Uses Eclipse workspace APIs (no direct filesystem writes)
- Maintains file history for undo capability

## Tab Conversion Rule

**Important**: Tab conversion is **only** applied to leading whitespace:

✅ **Converted**:
```xml
    <element>  <!-- 4 leading spaces → 1 tab -->
```

❌ **Not Converted**:
```xml
<element attr="value    with    spaces"/>  <!-- Inline spaces preserved -->
```

This ensures that:
- Indentation is normalized to tabs
- XML attribute values are not modified
- Text content spacing is preserved
- Only structural whitespace is affected

## Future Enhancements

- Support for build.properties formatting
- Integration with Eclipse XML editor for real-time cleanup
- Batch processing UI for multiple projects
- Configurable tab width
- Additional whitespace normalization rules
- Integration with Eclipse PDE validation framework

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_xml_cleanup_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.
