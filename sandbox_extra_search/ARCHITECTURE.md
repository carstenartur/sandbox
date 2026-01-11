# Extra Search Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#extra_search) | [TODO](TODO.md)

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

## Recent Changes

### Robustness Improvements (2024)
Enhanced error handling and defensive programming practices:
- Added try-with-resources for all resource streams to prevent leaks
- Implemented defensive null checks for workspace, projects, and dialog settings
- Replaced raw types with proper generics in class list management
- Improved error logging using `JavaPlugin.log()` instead of `printStackTrace()`
- Added graceful degradation when classlist.properties fails to load
- Enhanced user feedback with specific error messages for different failure scenarios

### Extended Deprecated Class List
Added more legacy/deprecated JDK classes for comprehensive migration support:
- Legacy collections: `Observable`, `Observer`, `Hashtable`, `Dictionary`, `Properties`
- Deprecated RMI classes: `LogStream`
- Deprecated security classes: `javax.security.auth.Policy`, `java.security.acl.*` package

## References

- [Eclipse JDT Search API](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/search/package-summary.html)
- [SearchEngine Documentation](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/search/SearchEngine.html)
- [Eclipse ViewPart Guide](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/workbench_basicext_views.htm)

## Integration Points

### Eclipse JDT Search Engine Integration

The plugin integrates deeply with Eclipse's search infrastructure:

1. **SearchEngine API**: Uses `org.eclipse.jdt.core.search.SearchEngine`
   - Searches for type references, method references
   - Supports scope filtering (workspace, project, package)
   - Returns `SearchMatch` objects with precise locations

2. **IJavaElement Integration**: Results are linked to Java model elements
   - Jump-to-definition works seamlessly
   - Hover tooltips show element information
   - Refactoring operations preserve search context

3. **Search Scope API**: Uses `SearchScope` for filtering
   - `SearchEngine.createWorkspaceScope()` for workspace-wide searches
   - `SearchEngine.createJavaSearchScope()` for project-specific searches
   - Honors user-defined working sets

### Eclipse UI Integration

The search view integrates with Eclipse's UI framework:

1. **ViewPart Extension**: Extends `org.eclipse.ui.part.ViewPart`
   - Registered in plugin.xml as `org.eclipse.ui.views` extension
   - Appears in Window → Show View menu
   - Supports perspective-based visibility

2. **TableViewer**: Uses JFace `TableViewer` for results display
   - Data binding to search result model
   - Sorting by file, line number, or match type
   - Content provider for efficient rendering

3. **Double-click Navigation**: Implements `IDoubleClickListener`
   - Opens editor at match location
   - Highlights matched line
   - Integrates with Eclipse navigation history

### Workspace Resource Integration

The plugin reads configuration from workspace:

1. **Bundle Resources**: Loads `classlist.properties` from plugin bundle
   - Uses `Platform.getBundle().getEntry()` for resource access
   - Defensive handling if file missing

2. **Dialog Settings**: Persists search history
   - Uses `IDialogSettings` API
   - Saves last search queries
   - Remembers view state between sessions

## Algorithms and Design Decisions

### Class List Management Algorithm

**Decision**: Pre-populate search with known deprecated classes from properties file

**Rationale**:
- Users often don't know which classes are deprecated
- Reduces cognitive load (common cases pre-listed)
- Faster workflow (select from dropdown vs. type full class name)

**Implementation**:
```
1. Load classlist.properties from bundle
2. Parse line-by-line (format: fully.qualified.ClassName)
3. Populate dropdown/combobox
4. User can select or type custom class name
5. Trigger search on selection/enter
```

**Trade-offs**:
- **Pro**: User-friendly for common cases
- **Pro**: Educational (shows examples of deprecated APIs)
- **Con**: List can become outdated (requires maintenance)
- **Con**: Not comprehensive (can't list all possible deprecated classes)

### Search Result Filtering Strategy

**Decision**: Search entire workspace, display all matches

**Alternative Considered**: Add filtering by project/package

**Current Approach**:
- Simple: No filter UI complexity
- Complete: Shows all usages
- Fast: Search engine handles large workspaces efficiently

**Future Enhancement**: Add filtering UI (see TODO.md)

### Error Handling Strategy

**Decision**: Graceful degradation with user feedback

**Key Decisions**:
1. **Missing classlist.properties**: Log warning, continue with empty list (user can still type)
2. **Null workspace**: Show error dialog, disable search
3. **Search failure**: Log error, show user-friendly message

**Rationale**:
- Plugin should never crash Eclipse
- User should understand what went wrong
- Partial functionality better than complete failure

### Asynchronous Search Design

**Decision**: Run searches in background job

**Rationale**:
- Searches can take seconds on large workspaces
- UI must remain responsive
- Progress reporting improves user experience

**Implementation**:
```java
Job searchJob = new Job("Searching for class references") {
    protected IStatus run(IProgressMonitor monitor) {
        // Perform search
        // Update UI on success (Display.asyncExec)
    }
};
searchJob.schedule();
```

## Cross-References

### Root README Sections

This architecture document relates to:

- [Eclipse Version Configuration](../README.md#eclipse-version-configuration) - Search uses Eclipse 2025-09 APIs
- [What's Included](../README.md#whats-included) - Listed as sandbox_extra_search
- [Projects](../README.md#3-sandbox_extra_search) - User-facing description

### Related Modules

- **sandbox_common** - Uses shared utilities for Eclipse API access
- **sandbox_usage_view** - Similar view-based plugin architecture
- **Eclipse JDT Core** - Search engine dependency
- **Eclipse JDT UI** - Java element navigation

## Future Enhancements

- Integration with Eclipse's deprecation warnings
- Suggested replacements for deprecated APIs
- Search templates for common upgrade scenarios
- Export search results to CSV/report format
- Search history and saved searches

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_extra_search_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.
