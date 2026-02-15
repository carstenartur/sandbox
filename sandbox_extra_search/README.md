# Extra Search Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Extra Search** plugin provides specialized search capabilities for Eclipse projects, particularly useful during Eclipse or Java version upgrades. It helps identify critical classes and dependencies that may require attention when migrating to newer versions.

## Key Features

- 🔍 **Critical Class Detection** - Find usage of important Eclipse/Java classes
- 📦 **Upgrade Assistance** - Identify code that may need updates during migrations
- 🎯 **Targeted Search** - Focus on specific packages or class patterns
- 🔌 **Eclipse Integration** - Works within Eclipse IDE search infrastructure

## Use Cases

### Eclipse Version Upgrades

When upgrading Eclipse versions:
- Identify deprecated API usage
- Find classes that have changed behavior
- Locate code using removed or moved classes

### Java Version Upgrades

When upgrading Java versions:
- Find usage of deprecated Java APIs
- Identify code using removed APIs (e.g., Java 8 → 11 → 17 → 21)
- Locate security manager usage (removed in Java 17+)
- Find uses of Applet classes (removed in Java 17+)

### Dependency Analysis

- Find all usages of specific library classes
- Analyze dependencies before library upgrades
- Identify tight coupling to specific APIs

## Quick Start

### Using in Eclipse

1. Open **Search** → **Extra Search...**
2. Enter class name or pattern to search for
3. Specify search scope (workspace, project, selection)
4. Review results and plan necessary changes

## Search Capabilities

The plugin can search for:
- Specific class names
- Package patterns
- Method invocations
- Field accesses
- Type references

## Typical Search Targets

### Eclipse API Changes

Common searches when upgrading Eclipse:
- `IWorkbenchPreferencePage` (deprecated in favor of `PreferencePage`)
- `UIJob` behavior changes
- Platform-specific classes that changed

### Java API Deprecations

Common searches when upgrading Java:
- `SecurityManager` (removed in Java 17+)
- `Applet` classes (removed in Java 17+)
- `finalize()` methods (deprecated for removal)
- Constructor methods deprecated in newer Java versions

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details
- **[TODO](TODO.md)** - Future enhancements
- **[Main README](../README.md#extra-search-sandbox_extra_search)** - Overview

## Integration

The Extra Search plugin integrates with:
- Eclipse Search framework
- Java Search API
- AST-based analysis

## Testing

Tests are in `sandbox_extra_search_test` (if available).

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_extra_search_test
```

## Future Enhancements

See [TODO.md](TODO.md) for planned features:
- Predefined search templates for common upgrade scenarios
- Export search results to reports
- Integration with migration planning tools

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Usage View](../sandbox_usage_view/) (complementary analysis tool)
