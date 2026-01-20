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
- **Strips whitespace-only text nodes** - Elements with only whitespace become empty
- **Default: `indent="no"`** - Produces compact output for size reduction
- **Optional: `indent="yes"`** - Enabled via `XML_CLEANUP_INDENT` preference

### 2. Whitespace Normalization
After XSLT transformation, the following post-processing is applied:

- **Reduce excessive empty lines** - Maximum 2 consecutive empty lines
- **Leading space to tab conversion** - Only at line start (not inline text)
  - Converts groups of 4 leading spaces to 1 tab
  - Preserves remainder spaces (e.g., 5 spaces → 1 tab + 1 space)
  - **Does NOT touch inline text or content nodes**

### 3. Empty Element Collapsing
Post-processing step to reduce file size by collapsing empty elements:

- **Pattern**: `<tagname attributes></tagname>` → `<tagname attributes/>`
- **Handles**: Elements with attributes, whitespace-only content, and namespaces
- **Preserves**: Elements with actual text content or child elements
- **Benefits**: Significant size reduction (typically 5-15% for PDE files)

**Examples**:
- `<extension point="org.eclipse.ui.views"></extension>` → `<extension point="org.eclipse.ui.views"/>`
- `<view id="v1">   </view>` → `<view id="v1"/>`
- `<description>Real content</description>` → Preserved unchanged

### 4. Change Detection
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
- Collapses empty XML elements to self-closing tags

**Post-Processing Pipeline**:
1. XSLT transformation (strips whitespace-only text nodes)
2. Whitespace normalization (reduce empty lines, convert spaces to tabs)
3. Empty element collapsing (regex-based post-processing)

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

**Purpose**: Eclipse cleanup integration (JDT-based)

**Features**:
- Reads `XML_CLEANUP` and `XML_CLEANUP_INDENT` preferences
- Configures indent preference before processing
- Creates `CompilationUnitRewriteOperation` for file updates

### XMLCleanupService

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.XMLCleanupService`

**Purpose**: Standalone XML cleanup service independent of JDT framework

**Key Features**:
- Works without requiring Java compilation unit context
- Processes single files or entire projects
- Uses same file filtering logic as XMLPlugin (PDE-relevant files only)
- Supports progress monitoring and cancellation
- Can be invoked directly from UI actions/handlers

**Methods**:
- `processFile(IFile, IProgressMonitor)` - Process single XML file
- `processProject(IProject, IProgressMonitor)` - Process all PDE files in project
- `isPDERelevantFile(IFile)` - Check if file should be processed

### XMLCleanupHandler

**Location**: `org.sandbox.jdt.internal.ui.handlers.XMLCleanupHandler`

**Purpose**: Command handler for PDE integration

**Key Features**:
- Implements `AbstractHandler` for Eclipse command framework
- Handles selections of files or projects from UI
- Runs cleanup in background job with progress dialog
- Shows completion status with file count
- Supports cancellation

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.corext.fix.helper.*` - Transformation utilities
- `org.sandbox.jdt.internal.ui.fix.*` - UI components and preferences (JDT-based)
- `org.sandbox.jdt.internal.ui.handlers.*` - Command handlers (PDE integration)

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

### Empty Element Collapsing
```java
// Pattern matches: <tagname attributes></tagname> or <tagname attributes>   </tagname>
// Captures the opening tag (without >) and ensures matching closing tag
// Supports namespaces (e.g., ns:element)
Pattern emptyElementPattern = Pattern.compile(
    "<([\\w:]+)((?:\\s+[^>]*?)?)>\\s*</\\1>",
    Pattern.MULTILINE
);

Matcher matcher = emptyElementPattern.matcher(content);
StringBuffer sb = new StringBuffer();

while (matcher.find()) {
    String tagName = matcher.group(1);
    String attributes = matcher.group(2);
    // Replace with self-closing tag
    String replacement = "<" + tagName + attributes + "/>";
    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
}
matcher.appendTail(sb);
```

**Key Points**:
- Uses backreference `\\1` to ensure opening and closing tags match
- `(?:\\s+[^>]*?)?` captures optional attributes
- `\\s*` matches any whitespace between tags (including newlines)
- `Matcher.quoteReplacement()` prevents regex interpretation of replacement string

### Eclipse Workspace Integration
```java
byte[] newContent = transformedContent.getBytes(StandardCharsets.UTF_8);
ByteArrayInputStream inputStream = new ByteArrayInputStream(newContent);
file.setContents(inputStream, IResource.KEEP_HISTORY, null);
file.refreshLocal(IResource.DEPTH_ZERO, null);
```

## Eclipse Integration

### Two Integration Paths

The plugin provides two ways to trigger XML cleanup:

#### 1. JDT Cleanup Integration (Original)
- Registered in `plugin.xml` as `org.eclipse.jdt.ui.cleanup.xmlcleanup`
- Requires Java compilation unit context
- Triggered via Source → Clean Up... (when Java files are present)
- Uses Eclipse cleanup framework
- Integrated with Eclipse cleanup preferences

#### 2. PDE Integration (New - Standalone)
- Registered as Eclipse command: `org.sandbox.jdt.xml.cleanup.command`
- Works independently without Java files
- Triggered via:
  - Right-click on PDE XML files → "Clean Up PDE XML"
  - Right-click on project → "Clean Up PDE XML"
  - Source menu → XML Cleanup → "Clean Up PDE XML Files"
- Runs in background job with progress dialog
- Supports cancellation
- Shows completion status

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

**JDT Integration**:
- Registered in `plugin.xml` as `org.eclipse.jdt.ui.cleanup.xmlcleanup`
- Uses Eclipse resource visitor pattern to scan projects
- Leverages Eclipse logging framework (`ILog`, `Status`)
- Integrates with Eclipse cleanup framework

**PDE Integration**:
- Command: `org.sandbox.jdt.xml.cleanup.command`
- Handler: `XMLCleanupHandler` (extends `AbstractHandler`)
- Menu contributions:
  - Context menu on PDE XML files
  - Context menu on projects
  - Source main menu entry
- Visibility conditions based on file name/extension

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse Core Resources
  - Eclipse Core Commands
  - Eclipse JDT Core (for JDT integration)
  - Eclipse UI, JFace, Workbench
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
5. **Requires Compilation Unit (JDT cleanup only)**: JDT integration with Eclipse cleanup framework requires Java compilation unit context. **The new PDE integration (XMLCleanupHandler/XMLCleanupAction) works independently without Java files.**

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
