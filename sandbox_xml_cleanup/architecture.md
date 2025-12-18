# XML Cleanup Plugin - Architecture

## Overview

The XML cleanup plugin provides automated refactoring and modernization for XML files in Eclipse projects. This includes formatting, structure optimization, and best practices enforcement for XML-based configuration files.

## Purpose

- Modernize XML configuration files
- Apply XML best practices automatically
- Simplify verbose XML patterns
- Ensure consistent XML formatting across projects

## Supported XML Types

The plugin focuses on XML files commonly found in Eclipse projects:
- plugin.xml (Eclipse plugin manifests)
- feature.xml (Eclipse feature definitions)
- Maven POM files (pom.xml)
- Build configuration files
- Other XML-based configuration

## Transformation Examples

### XML Simplification

The plugin can simplify verbose XML patterns such as:
- Removing unnecessary namespaces
- Consolidating redundant elements
- Applying formatting standards
- Updating deprecated XML patterns

## Core Components

### XMLCleanUp

**Location**: `org.sandbox.jdt.internal.corext.fix.XMLCleanUp`

**Purpose**: Main cleanup implementation for XML file modernization

**Key Features**:
- XML parsing and analysis
- Pattern-based transformations
- Structure optimization
- Formatting enforcement

## Package Structure

- `org.sandbox.jdt.internal.corext.fix.*` - Core cleanup logic
- `org.sandbox.jdt.internal.ui.*` - UI components and preferences

**Eclipse JDT Correspondence**:
- Maps to `org.eclipse.jdt.internal.corext.fix.*` pattern
- Can be ported by replacing `sandbox` with `eclipse`

## Design Patterns

### XML DOM Pattern
Uses DOM parsing for XML transformations:
```java
Document doc = parser.parse(xmlFile);
// Analyze and transform DOM
transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));
```

### Visitor Pattern
Traverses XML structure to identify cleanup opportunities:
```java
NodeList nodes = doc.getElementsByTagName("*");
for (int i = 0; i < nodes.getLength(); i++) {
    Node node = nodes.item(i);
    // Check for cleanup patterns
}
```

## Eclipse JDT Integration

### Current State
Experimental XML cleanup capabilities. May be more appropriate for Eclipse PDE (Plugin Development Environment) than JDT.

### Contribution Path
1. Refine XML cleanup transformations
2. Evaluate if better suited for Eclipse PDE
3. Gather community feedback
4. Consider integration with Eclipse XML editors

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse Core
  - XML parsing libraries
  - `sandbox_common` for cleanup constants

## Testing

### Test Module
`sandbox_xml_cleanup_test` contains test cases for XML transformations:
- XML parsing and transformation tests
- plugin.xml cleanup tests
- pom.xml optimization tests
- Formatting tests

## Known Limitations

1. **Limited XML Types**: Currently supports subset of XML file types
2. **Basic Transformations**: Simple pattern-based transformations only
3. **No Schema Validation**: Doesn't validate against XML schemas
4. **Manual Review Needed**: Complex XML may require manual review after cleanup

## Future Enhancements

- Expand to more XML file types
- Schema-aware transformations
- Integration with Eclipse XML editors
- XML validation and error detection
- Custom transformation rules
- Batch XML processing across projects
