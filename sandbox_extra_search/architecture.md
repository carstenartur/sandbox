# Extra Search Plugin - Architecture

## Overview

The extra search plugin provides advanced search capabilities for critical classes during Eclipse and Java version upgrades. This tool helps developers identify deprecated APIs, problematic patterns, and migration opportunities when upgrading Eclipse or Java versions.

## Purpose

- Search for specific class usages across the codebase
- Identify deprecated Eclipse APIs during version upgrades
- Find Java API usage that needs migration (e.g., Java 8 → 11)
- Support upgrade planning and migration tracking

## Core Components

### ExtraSearchView

**Location**: `org.sandbox.jdt.internal.ui.views.ExtraSearchView`

**Purpose**: Provides UI for searching and displaying results

**Key Features**:
- Search input for class/API names
- Results table with file locations
- Jump-to-definition from results
- Filter and sort capabilities

### SearchEngine Integration

The plugin integrates with Eclipse's search engine to find class references:
- Leverages Eclipse JDT search infrastructure
- Searches across workspace projects
- Filters by scope (workspace, project, package)
- Returns precise match locations

## Use Cases

### 1. Eclipse Version Upgrade

When upgrading Eclipse versions, search for deprecated APIs:
```
Search: org.eclipse.ui.IWorkbenchPage
Purpose: Find usage of deprecated methods in newer Eclipse versions
```

### 2. Java Version Migration

When upgrading Java versions, find problematic APIs:
```
Search: java.util.Date
Purpose: Identify Date usage to migrate to java.time APIs
```

### 3. Dependency Analysis

Find all usages of a specific library class:
```
Search: com.google.common.collect.Lists
Purpose: Identify Guava usage before removing dependency
```

## Package Structure

- `org.sandbox.jdt.internal.ui.views.*` - UI views and components
- `org.sandbox.jdt.internal.core.search.*` - Search engine integration

**Eclipse JDT Correspondence**:
- Similar to Eclipse JDT's search views
- Could integrate as additional search filters in Eclipse's search dialog

## Design Patterns

### View-Model Pattern
Separates UI (view) from search logic (model):
- View displays results and handles user interaction
- Model performs searches and manages result data
- Clean separation allows testing search logic without UI

### Observer Pattern
Search results notify UI of updates:
- Asynchronous search operations
- Progress reporting during long searches
- Result streaming for large result sets

## Eclipse JDT Integration

### Current State
Standalone search view for specialized upgrade scenarios. Not directly integrated into Eclipse's main search framework.

### Integration Opportunities
Could be integrated into Eclipse JDT as:
1. Additional search page in Eclipse's search dialog
2. Quick fix provider for deprecated API warnings
3. Migration wizard component for version upgrades

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse JDT Core (search APIs)
  - Eclipse UI (view framework)
  - `sandbox_common` for shared utilities

## Testing

Testing focuses on search accuracy and UI responsiveness:
- Unit tests for search query construction
- Integration tests with sample workspace
- UI tests for result display and navigation

## Known Limitations

1. **Manual Search**: Requires user to know which classes to search for
2. **No Recommendations**: Doesn't suggest replacement APIs
3. **Workspace Only**: Doesn't search in external JARs or libraries
4. **Basic Filtering**: Limited filtering options for results

## Future Enhancements

- Integration with Eclipse's deprecation warnings
- Suggested replacements for deprecated APIs
- Search templates for common upgrade scenarios
- Export search results to CSV/report format
- Search history and saved searches
