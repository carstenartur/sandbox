# TriggerPattern String Simplification Plugin - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#triggerpattern) | [TODO](TODO.md)

## Overview

This plugin demonstrates using the TriggerPattern hint engine to provide string simplification suggestions. Unlike traditional Eclipse cleanups that require substantial boilerplate code, this plugin uses declarative pattern matching to identify and suggest string-related improvements.

## Purpose

- Demonstrate TriggerPattern usage in a real plugin
- Simplify string concatenation patterns
- Provide cleaner string conversion alternatives
- Show how patterns can be more concise than traditional visitor-based cleanups

## Pattern-Based Approach

### Traditional Cleanup Approach
Traditional Eclipse cleanups require:
1. Extending `CleanUpCore` classes
2. Implementing complex AST visitors
3. Writing rewrite logic
4. Managing UI preferences
5. Registering via extension points

This typically requires hundreds of lines of code per cleanup.

### TriggerPattern Approach
With TriggerPattern, the same functionality requires:
1. A simple pattern string (e.g., `"" + $x`)
2. A single method to create the replacement
3. Registration via `@TriggerPattern` annotation

This dramatically reduces code complexity and improves maintainability.

## Transformation Examples

### Empty String Concatenation

**Pattern**: `"" + $x`

**Before**:
```java
String result = "" + value;
String message = "" + count;
```

**After**:
```java
String result = String.valueOf(value);
String message = String.valueOf(count);
```

**Benefits**:
- More explicit intent
- Clearer code
- Handles null values correctly (doesn't produce "null" string for null objects)

### Trailing Empty String

**Pattern**: `$x + ""`

**Before**:
```java
String result = value + "";
String message = count + "";
```

**After**:
```java
String result = String.valueOf(value);
String message = String.valueOf(count);
```

## Core Components

### StringSimplificationHintProvider

**Location**: `org.sandbox.jdt.triggerpattern.string.StringSimplificationHintProvider`

**Purpose**: Provides hint methods for string simplification patterns

**Key Methods**:
- `replaceEmptyStringConcatenation(HintContext)` - Handles `"" + $x` pattern
- `replaceTrailingEmptyString(HintContext)` - Handles `$x + ""` pattern

Each method:
1. Receives a `HintContext` with matched AST nodes
2. Extracts placeholder bindings
3. Creates a replacement using `ASTRewrite`
4. Returns an `IJavaCompletionProposal`

### Pattern Registration

Patterns are registered via plugin.xml:

```xml
<extension point="org.sandbox.jdt.triggerpattern.hints">
   <hintProvider class="org.sandbox.jdt.triggerpattern.string.StringSimplificationHintProvider"/>
</extension>
```

This registers all methods annotated with `@TriggerPattern` in the provider class.

## Package Structure

```
org.sandbox.jdt.triggerpattern.string
├── StringSimplificationHintProvider.java  (Quick Assist hints)

org.sandbox.jdt.internal.corext.fix
└── StringSimplificationFixCore.java  (Cleanup operations)

org.sandbox.jdt.internal.ui.fix
├── StringSimplificationCleanUp.java      (Cleanup wrapper)
├── StringSimplificationCleanUpCore.java  (Cleanup core)
├── MultiFixMessages.java                 (Localization)
└── MultiFixMessages.properties

org.sandbox.jdt.internal.ui.preferences.cleanup
├── DefaultCleanUpOptionsInitializer.java
├── SaveActionCleanUpOptionsInitializer.java
├── SandboxCodeTabPage.java              (UI preferences)
├── CleanUpMessages.java
└── CleanUpMessages.properties
```

**Note**: This plugin demonstrates a hybrid approach:
- **Quick Assist**: Uses simple TriggerPattern hint provider
- **Cleanup**: Uses traditional Eclipse cleanup infrastructure with TriggerPattern-based operations

This provides both interactive quick fixes (Ctrl+1) and automated batch cleanup capabilities.

## Design Patterns

### Declarative Pattern Matching
Instead of imperative AST visitors, patterns are declared:

```java
@TriggerPattern(value = "\"\" + $x", kind = PatternKind.EXPRESSION)
```

The engine handles:
- AST traversal
- Pattern matching
- Placeholder binding
- Quick Assist integration

### Hint Context Pattern
All hint methods receive a `HintContext` providing:
- `CompilationUnit` - The AST
- `ICompilationUnit` - The compilation unit
- `Match` - The matched pattern with bindings
- `ASTRewrite` - For creating transformations
- `ImportRewrite` - For managing imports

This provides everything needed to create a proposal without complex setup.

## Eclipse Integration

### Quick Assist Integration
The TriggerPattern engine automatically integrates with Eclipse's Quick Assist (Ctrl+1):

1. User places cursor on matching code
2. TriggerPattern engine finds matching patterns
3. Hint methods are invoked to create proposals
4. Proposals appear in Quick Assist menu

No additional UI code is required for Quick Assist functionality.

### Cleanup Integration
**Status**: ✅ Implemented in v1.2.2

The string simplification patterns are now fully integrated with Eclipse's cleanup infrastructure:

#### Save Actions
- Configured via "Java → Editor → Save Actions" preferences
- Automatically applies patterns when saving Java files
- Enables consistent code style across the project

#### Source → Clean Up
- Available via "Source → Clean Up..." menu
- Batch applies patterns to selected files, packages, or entire projects
- Provides preview of changes before applying

#### UI Components
- **`StringSimplificationCleanUp`** - Wrapper cleanup class
- **`StringSimplificationCleanUpCore`** - Core cleanup implementation
- **`StringSimplificationFixCore`** - TriggerPattern-based rewrite operations
- **`SandboxCodeTabPage`** - Preference page for cleanup configuration
- **`DefaultCleanUpOptionsInitializer`** - Default cleanup options
- **`SaveActionCleanUpOptionsInitializer`** - Save action default options

#### Configuration
Users can enable/disable string simplification via:
- Eclipse Preferences → Java → Code Style → Clean Up
- Project-specific settings → Java Code Style → Clean Up
- Save Actions preferences

This implementation demonstrates how TriggerPattern-based hints can be integrated into Eclipse's cleanup framework, providing both quick fixes (Ctrl+1) and automated refactoring capabilities.

## Advantages Over Traditional Cleanups

### Code Simplicity
- **Traditional**: ~500-1000 lines per cleanup
- **TriggerPattern**: ~50-100 lines per hint

### Maintainability
- Patterns are easy to read and understand
- No complex visitor logic to maintain
- Changes to patterns don't require extensive testing

### Discoverability
- Patterns are self-documenting
- Easy to add new patterns
- Hints automatically appear in Quick Assist

### Extensibility
- Other plugins can define their own patterns
- Patterns compose well
- Easy to share pattern libraries

## Comparison to Traditional Cleanup

For reference, compare this TriggerPattern-based plugin to traditional cleanups:

**Traditional Examples**:
- `sandbox_platform_helper` - Platform API simplification
- `sandbox_encoding_quickfix` - Encoding corrections
- `sandbox_functional_converter` - Loop to stream conversion

These plugins require:
- CleanUp classes
- CleanUpCore implementations
- AST visitors
- UI preference pages
- Extensive boilerplate

**TriggerPattern Example**:
- This plugin - String simplification

Requires only:
- Hint provider class with annotated methods
- Pattern registration in plugin.xml

## Testing

Tests are located in `sandbox_triggerpattern_string_test` module.

Test cases verify:
1. Patterns match expected code structures
2. Proposals are generated correctly
3. Replacements produce correct code
4. Edge cases are handled (nulls, complex expressions)

The TriggerPattern engine itself is tested in `sandbox_common_test`, so plugin tests can focus on hint-specific logic.

## Build Configuration

- **Module Type**: Eclipse Plugin (OSGi bundle)
- **Packaging**: `eclipse-plugin`
- **Dependencies**: 
  - Eclipse JDT core and UI
  - `sandbox_common` (for TriggerPattern engine)

## Version Compatibility

- **Java**: 21+ (required by Eclipse 2025-09)
- **Eclipse**: 2025-09 or later
- **TriggerPattern Engine**: Requires sandbox_common 1.2.2+

## Future Enhancements

Potential additional string simplification patterns:

1. **Null-safe concatenation**: 
   - Pattern: `$x != null ? $x : ""`
   - Replacement: `String.valueOf($x)`

2. **StringBuilder for single concat**:
   - Pattern: `new StringBuilder().append($x).toString()`
   - Replacement: `String.valueOf($x)`

3. **Redundant toString**:
   - Pattern: `$x.toString() + ""`
   - Replacement: `String.valueOf($x)`

4. **String.format simplification**:
   - Pattern: `String.format("%s", $x)`
   - Replacement: `String.valueOf($x)`

See [TODO.md](TODO.md) for more details.

## Contributing

To add new string simplification patterns:

1. Add a new method to `StringSimplificationHintProvider`
2. Annotate with `@TriggerPattern` and `@Hint`
3. Implement the replacement logic
4. Add test cases in the test module
5. Document the pattern in this file

## References

- [TriggerPattern Documentation](../sandbox_common/TRIGGERPATTERN.md)
- [TriggerPattern Architecture](../sandbox_common/ARCHITECTURE.md#triggerpattern-hint-engine)
- [Eclipse Quick Assist](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.user/reference/ref-java-editor-quickassist.htm)

## License

Eclipse Public License 2.0
