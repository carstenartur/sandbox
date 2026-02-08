# Usage View Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#usage_view) | [TODO](TODO.md)

## Overview

The usage view plugin provides a table view for detecting inconsistent naming patterns in code. This tool helps developers identify naming inconsistencies that could lead to bugs or confusion.

## Purpose

- Detect naming conflicts where the same variable name is used with different types
- Display all variable bindings in a sortable table view
- Help identify potentially confusing naming patterns
- Support code quality and maintainability
- **Automatically show view at startup** based on user preference
- **Auto-update view** when editor selection changes

## Use Cases

### Detecting Naming Conflicts

The plugin identifies specific patterns where naming conflicts occur:
- Variables with the same name but different types (e.g., `String userId` vs `int userId`)
- Helps catch potential confusion where similar names refer to different types
- Aids in refactoring by visualizing all variable bindings

### Table View Features

- Display all variable bindings in sortable table
- Filter to show only naming conflicts
- Navigate to code location from table
- Automatic view updates when navigating between files
- **Automatic view updates** when navigating between files
- **Preference-based auto-show** at Eclipse startup

## Core Components

### JavaHelperView (UsageView)

**Location**: `org.sandbox.jdt.ui.helper.views.JavaHelperView`

**Purpose**: Main view component displaying naming inconsistencies

**Key Features**:
- Table display of naming issues
- Sort and filter capabilities
- Jump to code location
- Violation categorization
- **Automatic content updates** via IPartListener2
- **Responds to editor activation** events

### UsageViewPlugin

**Location**: `org.sandbox.jdt.ui.helper.views.UsageViewPlugin`

**Purpose**: Plugin activator managing lifecycle and preferences

**Key Features**:
- Manages preference store
- Initializes default preference values
- Provides access to plugin instance

### UsageViewStartup

**Location**: `org.sandbox.jdt.ui.helper.views.UsageViewStartup`

**Purpose**: Handles automatic view display at Eclipse startup

**Key Features**:
- Implements IStartup for early activation
- Checks user preference before showing view
- Shows view asynchronously to avoid blocking startup

### UsageViewPreferencePage

**Location**: `org.sandbox.jdt.ui.helper.views.preferences.UsageViewPreferencePage`

**Purpose**: Preference page for configuring view behavior

**Key Features**:
- Boolean preference for auto-show at startup
- Integrated into Eclipse preferences under Java category
- User-friendly configuration interface

### NamingConflictFilter

**Location**: `org.sandbox.jdt.ui.helper.views.NamingConflictFilter`

**Purpose**: ViewFilter that identifies variables with naming conflicts

**Key Features**:
- Analyzes variable bindings to find same name with different types
- Implements ViewerFilter interface for table filtering
- Dynamically analyzes elements when filter is enabled
- Provides focused view of only conflicting names

### VariableBindingVisitor

**Location**: `org.sandbox.jdt.ui.helper.views.VariableBindingVisitor`

**Purpose**: AST visitor that collects all variable bindings using sandbox_common's AstProcessorBuilder

**Key Features**:
- Uses AstProcessorBuilder API for clean AST traversal
- Collects IVariableBinding from SimpleName nodes
- Processes entire AST trees to find all variable declarations
- Leverages sandbox_common utilities for maintainable code

### VariableNameSuggester

**Location**: `org.sandbox.jdt.ui.helper.views.VariableNameSuggester`

**Purpose**: Utility for generating type-aware variable name suggestions

**Key Features**:
- Suggests names based on variable type (e.g., `userId` → `stringUserId`)
- Helps developers rename variables to avoid conflicts
- Provides type-based naming conventions

### JHViewContentProvider

**Location**: `org.sandbox.jdt.ui.helper.views.JHViewContentProvider`

**Purpose**: Content provider that extracts variable bindings from Java elements

**Key Features**:
- Processes ICompilationUnit, IJavaProject, IPackageFragment, IPackageFragmentRoot
- Uses AST parsing with full binding resolution
- Parallel processing for large projects
- Proper error logging using Eclipse ILog

### NamingAnalyzer

**Purpose**: Analyzes code to detect naming patterns

**Key Features**:
- Pattern matching for variable names
- Convention checking
- Similarity detection
- Violation reporting

## Package Structure

- `org.sandbox.jdt.ui.helper.views` - View components and plugin activator
  - `JavaHelperView` - Main view implementation
  - `UsageViewPlugin` - Plugin activator with VIEW_ID constant
  - `UsageViewStartup` - IStartup implementation
  - `NamingConflictFilter` - ViewerFilter for conflict detection
  - `VariableBindingVisitor` - AST visitor using AstProcessorBuilder
  - `VariableNameSuggester` - Name suggestion utility
  - `JHViewContentProvider` - Content provider with proper logging
- `org.sandbox.jdt.ui.helper.views.preferences` - Preference page and constants
- `org.sandbox.jdt.ui.helper.views.colum` - Table column implementations

**Eclipse JDT Correspondence**:
- Could integrate with Eclipse's code analysis framework
- Similar to Eclipse's marker views for displaying issues
- Uses standard Eclipse ViewPart and preference frameworks

## Design Patterns

### Model-View Pattern
Separates display (view) from analysis (model):
- View shows results in table
- Model performs naming analysis
- Clean separation for testability

### Observer Pattern (IPartListener2)
The view implements `IPartListener2` to respond to editor changes:
- Listens for editor activation events
- Updates view content when editor selection changes
- Automatically refreshes when switching files
- Provides real-time feedback as developer navigates code
- **IPartListener2** for editor event monitoring

### Lazy Initialization Pattern (IStartup)
The startup component uses lazy initialization:
- Implements IStartup for controlled early activation
- Only shows view if user preference is enabled
- Uses Display.asyncExec to avoid blocking Eclipse startup
- Minimizes impact on startup time

### Preference Store Pattern
Configuration management follows Eclipse preference patterns:
- `PreferenceConstants` class defines preference keys
- `PreferenceInitializer` sets defaults
- `UsageViewPreferencePage` provides UI for configuration
- Changes persist across Eclipse sessions

## Integration Points

### Eclipse Workbench Integration

The usage view integrates with Eclipse's workbench framework:

1. **ViewPart Extension**: Extends `org.eclipse.ui.part.ViewPart`
   - Registered in plugin.xml as `org.eclipse.ui.views` extension
   - Appears in Window → Show View → Other → Java category
   - Supports perspective-based visibility
   - Can be docked, minimized, or detached

2. **IPartListener2**: Tracks editor lifecycle events
   - `partActivated()` - Triggered when editor gains focus
   - `partBroughtToTop()` - Triggered when editor tab selected
   - `partClosed()` - Cleans up when editor closed
   - Enables automatic view content updates

3. **IStartup Extension**: Early plugin activation
   - Registered in plugin.xml as `org.eclipse.ui.startup` extension
   - Runs after workbench fully initialized
   - Checks preferences before showing view
   - Asynchronous execution prevents blocking startup

### Eclipse JDT Integration

Integrates with Eclipse JDT for code analysis:

1. **IJavaElement API**: Navigates Java model
   - Analyzes types, methods, fields from Java model
   - Resolves bindings for accurate naming analysis
   - Respects project structure and dependencies

2. **ICompilationUnit**: Accesses source code
   - Reads Java files for naming analysis
   - Works with both saved and unsaved editors
   - Handles AST parsing for detailed analysis

3. **Editor Integration**: Synchronizes with Java editor
   - Responds to editor activation via IPartListener2
   - Detects current file and cursor position
   - Updates view based on active Java file

### Eclipse Preferences Integration

Uses Eclipse's preference framework:

1. **IPreferenceStore**: Stores configuration
   - Plugin-scoped preferences (workspace-level)
   - Persisted in `.metadata/.plugins/.../prefs`
   - Accessed via `UsageViewPlugin.getDefault().getPreferenceStore()`

2. **PreferencePage Extension**: Provides UI
   - Registered under Java preferences category
   - Standard Eclipse preference page styling
   - Immediate feedback (no restart required)

3. **PreferenceInitializer**: Sets defaults
   - Runs on first plugin activation
   - Provides sensible default values
   - Used when preferences not yet saved

## Algorithms and Design Decisions

### Auto-Show at Startup Strategy

**Decision**: Conditionally show view at startup based on user preference

**Algorithm**:
```
1. Eclipse calls IStartup.earlyStartup()
2. Check preference: PREF_AUTO_SHOW_ON_STARTUP
3. If true:
   - Use Display.asyncExec for non-blocking execution
   - Call workbench.getActiveWorkbenchWindow().getActivePage()
   - Call showView(VIEW_ID)
4. If false: Do nothing
```

**Rationale**:
- Users who rely on view want it always visible
- Users who don't use it shouldn't see it unexpectedly
- Preference provides control without code changes

**Trade-offs**:
- **Pro**: User choice (opt-in or opt-out)
- **Pro**: No performance impact if disabled
- **Con**: Requires user to know preference exists
- **Con**: Adds startup code even when disabled (minimal impact)

### Automatic View Update Strategy

**Decision**: Update view content when editor activates

**Algorithm**:
```
1. User switches to different Java file
2. IPartListener2.partActivated() called
3. Extract ICompilationUnit from editor
4. Analyze naming patterns in new file
5. Update table viewer with new results
6. Maintain scroll position if possible
```

**Rationale**:
- Context-sensitive feedback (shows issues for current file)
- Developer sees relevant information immediately
- Reduces manual refresh actions

**Trade-offs**:
- **Pro**: Always up-to-date with current file
- **Pro**: Less cognitive load (automatic)
- **Con**: Analysis may take time for large files
- **Con**: Could be distracting if switching files frequently

**Optimization**: Cache results per file to avoid re-analysis

### Naming Pattern Detection Algorithm

**Decision**: Use AST-based binding analysis to detect naming conflicts

**Current Implementation**:
```
1. Collect all IVariableBindings from AST using AstProcessorBuilder
2. Group bindings by variable name
3. Within each group, check if ITypeBinding differs
4. Flag as conflict if same name has different types
```

**Example Conflict**:
```java
String userId = "123";    // Type: String
int userId = 456;         // Type: int - CONFLICT DETECTED
```

**Rationale**:
- Precise detection using Eclipse JDT's type system
- Leverages binding resolution for accurate type information
- Focuses on real conflicts rather than style violations

**Future Enhancement**: The architecture supports extending this to detect additional patterns like case inconsistencies (userId vs userID) and abbreviation variations. See TODO.md for planned enhancements.

**Note**: The conceptual algorithm described in previous versions (edit distance, semantic similarity) represents future planned functionality, not current implementation.

### Why Table View Instead of Markers?

**Decision**: Display results in custom table view instead of Eclipse markers

**Alternative Considered**: Use Eclipse problem markers (like compiler errors)

**Chosen Approach - Table View**:
- **Pro**: Can show custom columns (pattern, suggestion, etc.)
- **Pro**: Can sort/filter/group dynamically
- **Pro**: Doesn't clutter Problems view with style issues
- **Pro**: User can close when not needed

**Alternative - Markers**:
- **Pro**: Integrated with Eclipse's problem navigation
- **Pro**: Appears in Problems view automatically
- **Con**: Mixes style issues with real errors/warnings
- **Con**: Limited customization of display format
- **Con**: Can't easily group or categorize

**Decision**: Table view provides better UX for naming analysis

## Cross-References

### Root README Sections

This architecture document relates to:

- [4. `sandbox_usage_view`](../README.md#4-sandbox_usage_view) - User-facing description
- [Eclipse Version Configuration](../README.md#eclipse-version-configuration) - Uses Eclipse 2025-09 APIs
- [Build Instructions](../README.md#build-instructions) - How to build and test

### Related Modules

- **sandbox_common** - Provides AstProcessorBuilder for AST traversal
  - `AstProcessorBuilder` - Used by VariableBindingVisitor
  - `ReferenceHolder` - Thread-safe map for AST node references
- **sandbox_extra_search** - Similar view-based plugin architecture
- **Eclipse JDT UI** - Editor integration APIs
- **Eclipse Workbench** - ViewPart and IStartup frameworks

## References

- [Eclipse ViewPart Guide](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/workbench_basicext_views.htm)
- [IPartListener2 API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/ui/IPartListener2.html)
- [IStartup Extension](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_startup.html)
- [Eclipse Preferences Guide](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/preferences.htm)

## Eclipse JDT Integration

### Current State
Standalone view for naming analysis with preference support and automatic updates. Could integrate with Eclipse's problem framework.

### Integration Opportunities
- Use Eclipse markers for naming issues
- Integrate with Problems view
- Add quick fixes for naming violations
- Link with refactoring tools

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Bundle Activator**: `org.sandbox.jdt.ui.helper.views.UsageViewPlugin`
- **Dependencies**: 
  - Eclipse JDT Core
  - Eclipse UI framework
  - Eclipse JFace (preferences)
  - `sandbox_common` for utilities
- **Extension Points Used**:
  - `org.eclipse.ui.views` - View registration
  - `org.eclipse.ui.preferencePages` - Preference page
  - `org.eclipse.ui.startup` - Early startup hook

## Testing

Testing focuses on naming analysis accuracy, view functionality, and preference behavior:
- Unit tests for naming pattern detection
- Integration tests with sample code
- UI tests for table view
- **Preference tests** for auto-show behavior
- **Update tests** for editor activation response

## Known Limitations

1. **Pattern-Based**: Currently only detects "same name, different type" conflicts
2. **No Configurable Rules**: Does not support custom naming pattern configuration
3. **Limited Scope**: Does not detect case variations (userId vs userID) or other subtle inconsistencies
4. **No Auto-Fix**: Identifies issues but doesn't provide automatic refactoring (quick fixes planned)

## Future Enhancements

- **Configurable naming conventions** - Support custom pattern definitions
- **Quick fixes** - Integrate with refactoring tools for automatic renaming
- **Enhanced pattern detection** - Detect case variations, abbreviations, and other inconsistencies
- **Integration with refactoring tools** - Use RenameSupport for safe variable renaming
- **Batch renaming capabilities** - Apply fixes to multiple variables at once
- **Export reports** - CSV/Excel export for naming issue reports
- **IDE-wide naming consistency checks** - Analyze entire workspace
- **Enhanced auto-update triggers** (e.g., on file save, on build)
- **Multiple view instances** with different filters

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_usage_view_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.
