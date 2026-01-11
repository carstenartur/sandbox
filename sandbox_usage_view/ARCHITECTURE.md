# Usage View Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#usage_view) | [TODO](TODO.md)

## Overview

The usage view plugin provides a table view for detecting inconsistent naming patterns in code. This tool helps developers identify naming inconsistencies that could lead to bugs or confusion.

## Purpose

- Detect inconsistent naming patterns across codebase
- Display naming violations in table view
- Help maintain consistent naming conventions
- Support code quality and maintainability
- **Automatically show view at startup** based on user preference
- **Auto-update view** when editor selection changes

## Use Cases

### Detecting Naming Inconsistencies

The plugin can identify patterns such as:
- Variables named similarly but inconsistently (e.g., `userId` vs `userID`)
- Method names not following conventions
- Class names with inconsistent patterns
- Field naming violations

### Table View Features

- Display naming issues in sortable table
- Group by violation type
- Navigate to code location from table
- Filter and search capabilities
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

### NamingAnalyzer

**Purpose**: Analyzes code to detect naming patterns

**Key Features**:
- Pattern matching for variable names
- Convention checking
- Similarity detection
- Violation reporting

## Package Structure

- `org.sandbox.jdt.ui.helper.views` - View components and plugin activator
- `org.sandbox.jdt.ui.helper.views.preferences` - Preference page and constants
- `org.sandbox.jdt.ui.helper.views.colum` - Table column implementations

**Eclipse JDT Correspondence**:
- Could integrate with Eclipse's code analysis framework
- Similar to Eclipse's marker views for displaying issues

## Design Patterns

### Model-View Pattern
Separates display (view) from analysis (model):
- View shows results in table
- Model performs naming analysis
- Clean separation for testability

### Observer Pattern
View updates when analysis completes or editor changes:
- Asynchronous analysis
- Progress reporting
- Result streaming
- **IPartListener2** for editor event monitoring

### Preference-Based Initialization
Startup behavior controlled by user preference:
- Check preference at startup
- Conditionally show view
- Non-blocking initialization

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

1. **Pattern-Based**: Uses pattern matching which may have false positives
2. **Manual Configuration**: Requires manual configuration of naming rules
3. **Limited Scope**: Only detects specific naming patterns
4. **No Auto-Fix**: Identifies issues but doesn't provide automatic fixes

## Future Enhancements

- Configurable naming conventions
- Quick fixes for common violations
- Integration with refactoring tools
- Batch renaming capabilities
- Export reports of naming issues
- IDE-wide naming consistency checks
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
