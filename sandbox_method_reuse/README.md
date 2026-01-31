# Method Reuse Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Method Reuse** plugin identifies opportunities to reuse existing methods instead of duplicating logic. It helps reduce code duplication by finding and suggesting method calls that can replace repeated code patterns.

## Key Features

- 🔍 **Duplication Detection** - Find code that duplicates existing method logic
- 🎯 **Method Suggestions** - Recommend existing methods to call instead
- ♻️ **Code Reuse** - Promote DRY (Don't Repeat Yourself) principle
- 🔌 **Eclipse Integration** - Works as cleanup or quick fix

## Use Cases

### Code Review

- Identify duplication during code reviews
- Suggest using existing utility methods
- Enforce code reuse standards

### Refactoring

- Find opportunities to consolidate duplicate code
- Replace inline logic with method calls
- Improve maintainability

### New Developer Onboarding

- Help developers discover existing utility methods
- Reduce accidental duplication
- Promote learning of codebase APIs

## Quick Start

### Enable in Eclipse

1. Open **Source** → **Clean Up...**
2. Navigate to the **Method Reuse** category
3. Enable **Suggest existing method calls**

### Example Detection

**Before (Duplicated Logic):**
```java
public void processOrder(Order order) {
    String formatted = order.getDate().format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    log.info("Processing order: " + formatted);
}

// Elsewhere in codebase, existing method:
public String formatDate(LocalDate date) {
    return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
}
```

**After (Using Existing Method):**
```java
public void processOrder(Order order) {
    String formatted = formatDate(order.getDate());
    log.info("Processing order: " + formatted);
}
```

## Detection Patterns

The plugin looks for:

### Exact Logic Matches
- Identical expressions in multiple places
- Same calculation patterns
- Repeated API call sequences

### Similar Logic
- Code with minor variations (parameterizable differences)
- Common patterns with different literals
- Utility-like operations repeated across classes

### Refactorable Patterns
- Inline code that matches existing method signatures
- Logic that could delegate to existing methods
- Repeated transformations

## Suggestions

When duplication is found, the plugin suggests:
- Calling the existing method
- Passing appropriate parameters
- Handling return values correctly

## Configuration

Configure detection sensitivity:
1. **Window** → **Preferences** → **Sandbox** → **Method Reuse**
2. Set minimum duplication threshold
3. Configure search scope (project, workspace)
4. Enable/disable specific patterns

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation and detection algorithms
- **[TODO](TODO.md)** - Future enhancements
- **[Main README](../README.md)** - Overview

## Testing

Tests are in `sandbox_method_reuse_test`:
- Duplication detection tests
- Method suggestion tests
- Refactoring transformation tests

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_method_reuse_test
```

## Limitations

- May not detect complex logic equivalences
- Requires methods to be in same workspace
- Performance impact on very large codebases

See [TODO.md](TODO.md) for planned improvements.

## Future Enhancements

Planned features:
- Machine learning for pattern detection
- Cross-project method discovery
- Automated refactoring with confidence scores
- Integration with code review tools

## Contributing to Eclipse JDT

This plugin demonstrates advanced AST analysis patterns suitable for Eclipse JDT contribution.

See [Architecture](ARCHITECTURE.md) for implementation details.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Functional Converter](../sandbox_functional_converter/) (another transformation pattern), [Common Utilities](../sandbox_common/)
