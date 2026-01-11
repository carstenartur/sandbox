# Usage View Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Usage View** plugin provides a table-based view for detecting inconsistent naming conventions in Java codebases. It helps identify variables, methods, and classes that don't follow consistent naming patterns, improving code quality and maintainability.

## Key Features

- 📊 **Table View** - Display naming inconsistencies in organized tables
- 🔍 **Pattern Detection** - Find violations of naming conventions
- 🎯 **Customizable Rules** - Configure what constitutes "inconsistent" naming
- 🔌 **Eclipse Integration** - View panel integrates with Eclipse IDE

## Use Cases

### Code Quality Audits

- Identify variables not following camelCase convention
- Find constants not using UPPER_SNAKE_CASE
- Detect classes not following PascalCase naming

### Refactoring Planning

- Generate list of names to standardize
- Prioritize naming improvements
- Track refactoring progress

### Team Standards Enforcement

- Verify adherence to team naming conventions
- Identify legacy code needing updates
- Educate developers on standards

## Quick Start

### Opening the View

1. Open **Window** → **Show View** → **Other...**
2. Navigate to **Sandbox** category
3. Select **Usage View**

### Analyzing a Project

1. Select a project or package in Project Explorer
2. Right-click and choose **Analyze Naming** (if available)
3. Review inconsistencies in the Usage View table

## Naming Patterns Detected

### Variable Names
- **camelCase** - Local variables and fields
- **UPPER_SNAKE_CASE** - Constants (static final fields)

### Method Names
- **camelCase** - All methods
- Consistent verb prefixes (get, set, is, has, etc.)

### Class Names
- **PascalCase** - All classes and interfaces
- Meaningful, descriptive names

## View Features

### Table Columns

The view typically displays:
- **Element Name** - The variable/method/class name
- **Expected Pattern** - What naming convention should be used
- **Location** - File and line number
- **Suggestion** - Recommended name (if applicable)

### Filtering and Sorting

- Filter by pattern type
- Sort by location, name, or severity
- Group by project or package

## Configuration

Configure naming rules through preferences:
1. **Window** → **Preferences** → **Sandbox** → **Usage View**
2. Customize patterns and rules
3. Set severity levels for different violations

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details
- **[TODO](TODO.md)** - Future enhancements
- **[Main README](../README.md#4-sandbox_usage_view)** - Overview

## Integration

The Usage View integrates with:
- Eclipse Views framework
- Java model API
- AST analysis

## Use with Other Tools

Complements:
- **Code formatters** - Enforce style after naming is corrected
- **Refactoring tools** - Safely rename identified issues
- **Extra Search** - Find all usages before renaming

## Future Enhancements

See [TODO.md](TODO.md) for planned features:
- Quick fixes to automatically correct names
- Export to CSV/Excel for reporting
- Integration with save actions
- Team-shared configuration profiles

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Extra Search](../sandbox_extra_search/), [Common Utilities](../sandbox_common/)
