# Usage View Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Usage View** plugin provides a table-based view for detecting naming conflicts in Java codebases. It analyzes Java code using AST (Abstract Syntax Tree) parsing to identify variables with the same name but different types across your codebase.

## Key Features

- 📊 **Table View** - Display all variables in organized, sortable tables
- 🔍 **Naming Conflict Detection** - Find variables with the same name but different types
- 🔄 **Automatic Updates** - View updates automatically when switching between files
- 🎯 **Link with Selection** - Synchronize with Project Explorer and editor selections
- 🔌 **Eclipse Integration** - Seamlessly integrated with Eclipse IDE
- ⚙️ **Startup Control** - Optional auto-show at Eclipse startup via preferences

## Use Cases

### Naming Conflict Detection

The primary use case is identifying naming conflicts where the same variable name is used with different types, which can lead to confusion and bugs:

```java
// Example of naming conflict that will be detected:
String userId = "123";          // Type: String
int userId = 456;               // Type: int - CONFLICT!
```

### Code Quality Review

- Identify potentially confusing variable naming patterns
- Find variables that might benefit from more descriptive names
- Support refactoring efforts by visualizing all variable bindings

### Codebase Exploration

- Browse all variables in a project, package, or file
- Navigate quickly to variable declarations
- Understand variable usage patterns across the codebase

## Quick Start

### Opening the View

1. Open **Window** → **Show View** → **Other...**
2. Navigate to **Java** category
3. Select **JavaHelper View** (or **Usage View**)

Alternatively, the view can be configured to automatically show at Eclipse startup via preferences.

### Analyzing Code

1. Select a project, package, or Java file in Project Explorer
2. The view automatically updates when "Link with Selection" is enabled (default)
3. View displays all variable bindings from the selected Java element
4. Enable "Filter Naming Conflicts" to show only variables with the same name but different types

## Detection Capabilities

### Current Implementation

The plugin currently detects **naming conflicts** - variables with the same name but different types. This helps identify potentially confusing naming patterns that could lead to bugs.

### Variable Types Analyzed

- **Local variables** - Variables declared within methods
- **Fields** - Class and instance fields
- **Parameters** - Method and constructor parameters

All variable bindings are extracted through AST parsing with full type resolution.

## View Features

### Table Columns

The view displays the following information for each variable binding:

- **Name** - The variable name
- **Qualified Name** - Full type information including package
- **Package** - The package containing the variable
- **Deprecated** - Indicates if the binding is marked as deprecated
- **Declaring Method** - The method where the variable is declared (for local variables)

### Filtering and Sorting

- **Link with Selection** - Toggle to enable/disable automatic updates based on selection
- **Filter Naming Conflicts** - Show only variables with naming conflicts (same name, different type)
- **Sort by Column** - Click any column header to sort the table
- **Refresh** - Manually refresh the view content

## Configuration

Configure view behavior through preferences:

1. **Window** → **Preferences** → **Java** → **Usage View**
2. **Show View at Startup** - Enable/disable automatic view display when Eclipse starts

Currently, the preference system supports controlling startup behavior. Additional configuration options for naming patterns and rules are planned for future releases (see [TODO.md](TODO.md)).

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details
- **[TODO](TODO.md)** - Future enhancements
- **[Main README](../README.md#4-sandbox_usage_view)** - Overview

## Integration

The Usage View integrates with:
- **Eclipse Views framework** - Standard Eclipse view with toolbar and menu integration
- **Java model API** - Uses IJavaElement for navigation
- **AST analysis** - Full AST parsing with binding resolution via AstProcessorBuilder from sandbox_common
- **IPartListener2** - Automatic updates when switching between editors
- **IStartup** - Optional automatic display at Eclipse startup

## Technical Implementation

### AST Processing

The view uses the `AstProcessorBuilder` API from `sandbox_common` for efficient AST traversal:

```java
AstProcessorBuilder.with(new ReferenceHolder<String, Object>())
    .onSimpleName((simpleName, dataHolder) -> {
        IBinding binding = simpleName.resolveBinding();
        if (binding instanceof IVariableBinding variableBinding) {
            collectedVariableBindings.add(variableBinding);
        }
        return true;
    })
    .build(astNode);
```

### Naming Conflict Detection

The `NamingConflictFilter` analyzes variable bindings and identifies conflicts where:
1. Multiple variables share the same name
2. But have different types (different ITypeBinding)

This helps developers identify potentially confusing naming patterns.

## Use with Other Tools

The Usage View complements:
- **Refactoring tools** - Identify candidates for renaming before refactoring
- **Extra Search** - Use together to find all usages before renaming
- **JDT Cleanup** - Part of the broader sandbox cleanup ecosystem

## Current Limitations

- **Pattern Detection**: Currently only detects "same name, different type" conflicts
- **No Quick Fixes**: Identifies issues but does not provide automatic refactoring
- **No Export**: Cannot export results to CSV or other formats
- **No Custom Rules**: Cannot define custom naming conventions or patterns

## Future Enhancements

See [TODO.md](TODO.md) for planned features:
- **Configurable naming conventions** - Define custom naming rules
- **Quick fixes** - Automatic refactoring suggestions
- **Enhanced pattern detection** - Detect more subtle naming inconsistencies (case variations, abbreviations, etc.)
- **Export functionality** - CSV/Excel export for reporting
- **Batch operations** - Apply fixes to multiple variables at once

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Extra Search](../sandbox_extra_search/), [Common Utilities](../sandbox_common/)
