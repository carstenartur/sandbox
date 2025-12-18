# Usage View Plugin - Architecture

## Overview

The usage view plugin provides a table view for detecting inconsistent naming patterns in code. This tool helps developers identify naming inconsistencies that could lead to bugs or confusion.

## Purpose

- Detect inconsistent naming patterns across codebase
- Display naming violations in table view
- Help maintain consistent naming conventions
- Support code quality and maintainability

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

## Core Components

### UsageView

**Location**: `org.sandbox.jdt.internal.ui.views.UsageView`

**Purpose**: Main view component displaying naming inconsistencies

**Key Features**:
- Table display of naming issues
- Sort and filter capabilities
- Jump to code location
- Violation categorization

### NamingAnalyzer

**Purpose**: Analyzes code to detect naming patterns

**Key Features**:
- Pattern matching for variable names
- Convention checking
- Similarity detection
- Violation reporting

## Package Structure

- `org.sandbox.jdt.internal.ui.views.*` - View components
- `org.sandbox.jdt.internal.core.naming.*` - Naming analysis logic

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
View updates when analysis completes:
- Asynchronous analysis
- Progress reporting
- Result streaming

## Eclipse JDT Integration

### Current State
Standalone view for naming analysis. Could integrate with Eclipse's problem framework.

### Integration Opportunities
- Use Eclipse markers for naming issues
- Integrate with Problems view
- Add quick fixes for naming violations
- Link with refactoring tools

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse JDT Core
  - Eclipse UI framework
  - `sandbox_common` for utilities

## Testing

Testing focuses on naming analysis accuracy and view functionality:
- Unit tests for naming pattern detection
- Integration tests with sample code
- UI tests for table view

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
