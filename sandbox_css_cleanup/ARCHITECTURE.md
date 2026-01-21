# CSS Cleanup Plugin - Architecture

## Overview

The CSS Cleanup plugin provides CSS validation and formatting capabilities in Eclipse by integrating with npm-based tools (Prettier and Stylelint) via process execution.

## Design Goals

1. **External Tool Integration**: Leverage existing, well-maintained CSS tools rather than reimplementing
2. **Graceful Degradation**: Work without npm tools installed, show helpful error messages
3. **Non-invasive**: Use stdin/stdout processing to avoid temporary files
4. **Safe Processing**: Preserve file history, handle errors gracefully
5. **Standard Eclipse Patterns**: Follow Eclipse plugin conventions

## Architecture

### Package Structure

```
org.sandbox.jdt.internal.css/
├── CSSCleanupPlugin.java          # Plugin activator
├── core/
│   ├── NodeExecutor.java          # Process execution utility
│   ├── PrettierRunner.java        # Prettier integration
│   ├── StylelintRunner.java       # Stylelint integration
│   └── CSSValidationResult.java   # Validation result model
├── ui/
│   └── CSSCleanupAction.java      # Right-click menu action
└── preferences/
    ├── CSSPreferencePage.java     # Preferences UI
    └── CSSPreferenceConstants.java # Preference keys
```

### Component Design

#### NodeExecutor

**Purpose**: Low-level process execution utility for Node.js commands.

**Responsibilities**:
- Check if Node.js and npx are available
- Execute npx commands with timeout handling
- Read stdout/stderr streams
- Return structured execution results

**Key Methods**:
- `isNodeAvailable()`: Check if `node` command exists
- `isNpxAvailable()`: Check if `npx` command exists
- `executeNpx(String... args)`: Run npx command, return ExecutionResult

**Design Decisions**:
- Uses `ProcessBuilder` for cross-platform compatibility
- 30-second timeout prevents hanging processes
- Separate stdout/stderr capture for error handling
- Non-blocking I/O for stream reading

#### PrettierRunner

**Purpose**: High-level interface to Prettier formatting.

**Responsibilities**:
- Format CSS content using Prettier
- Handle stdin/stdout communication
- Parse Prettier output and errors
- Check if Prettier is available

**Key Methods**:
- `format(IFile file)`: Format a CSS file, return formatted content
- `isPrettierAvailable()`: Check if Prettier can be run

**Design Decisions**:
- Uses stdin/stdout to avoid file modification race conditions
- Passes `--stdin-filepath` to help Prettier detect file type
- Returns null on failure (non-throwing for graceful degradation)
- Logs warnings for debugging without blocking users

#### StylelintRunner

**Purpose**: High-level interface to Stylelint validation.

**Responsibilities**:
- Validate CSS content using Stylelint
- Parse JSON output for validation results
- Provide auto-fix capability (optional)
- Check if Stylelint is available

**Key Methods**:
- `validate(IFile file)`: Validate CSS, return CSSValidationResult
- `fix(IFile file)`: Auto-fix issues, return fixed content
- `isStylelintAvailable()`: Check if Stylelint can be run

**Design Decisions**:
- Uses JSON formatter for structured output
- Simplified parsing (production version should use Gson/Jackson)
- Separate `validate()` and `fix()` for flexibility
- Returns structured results for UI display

#### CSSValidationResult

**Purpose**: Data model for validation results.

**Structure**:
- `boolean valid`: Overall validation status
- `List<Issue> issues`: List of validation issues
- `Issue`: Line, column, severity, rule, message

**Design Decisions**:
- Immutable design (final fields, unmodifiable list)
- Simple POJO for easy testing
- Extensible (can add more fields as needed)

#### CSSCleanupAction

**Purpose**: Eclipse UI action for right-click menu.

**Responsibilities**:
- Handle user action from context menu
- Check prerequisites (Node.js availability)
- Orchestrate formatting and validation
- Display results and errors to user

**Workflow**:
1. Check if Node.js is available (show error if not)
2. Filter selected files to CSS/SCSS/LESS
3. Format each file with Prettier
4. Validate each file with Stylelint
5. Display validation warnings if any

**Design Decisions**:
- Implements `IObjectActionDelegate` (Eclipse 3.x API)
- Uses `Adapters.adapt()` for resource conversion
- Shows modal dialogs for user feedback
- Updates file with `setContents()` to preserve history

#### CSSPreferencePage

**Purpose**: Eclipse preference page for configuration.

**Features**:
- Enable/disable Prettier and Stylelint
- Configure Prettier options (JSON)
- Specify Stylelint config file path
- Show tool availability status

**Design Decisions**:
- Extends `FieldEditorPreferencePage` for standard UI
- Shows real-time status of tools in description
- Provides sensible defaults (both enabled, empty config)
- Uses standard Eclipse preference store

## Extension Points

### plugin.xml

1. **org.eclipse.ui.popupMenus**: Right-click actions
   - Registered for `*.css`, `*.scss`, `*.less` files
   - Uses `nameFilter` for file type matching
   - Separate contributions for each file type

2. **org.eclipse.ui.preferencePages**: Preferences
   - Registered as "CSS Cleanup" page
   - No parent page (top-level)

## Data Flow

### Format CSS File Flow

```
User Right-Clicks File
    ↓
CSSCleanupAction.run()
    ↓
Check Node.js availability (NodeExecutor)
    ↓
PrettierRunner.format(file)
    ↓
NodeExecutor.executeNpx("prettier", ...)
    ↓
ProcessBuilder executes: npx prettier --parser css --stdin-filepath file.css
    ↓
Write file content to process stdin
    ↓
Read formatted content from process stdout
    ↓
Update file with formatted content (IFile.setContents)
    ↓
StylelintRunner.validate(file)
    ↓
Show validation warnings if any
```

## Error Handling

### Levels of Failure

1. **Node.js not installed**: Show error dialog, abort
2. **Tool not available**: npx will download on first use (may be slow)
3. **Process timeout**: Throw IOException after 30 seconds
4. **Formatting failure**: Log warning, don't update file
5. **Validation failure**: Show warnings, file already formatted

### Recovery Strategies

- **Missing Node.js**: Clear error message with installation link
- **Tool download delay**: User sees delay but npx handles it
- **Syntax errors**: Prettier fails, show error message
- **Large files**: Timeout prevents hanging

## Future Enhancements

See [TODO.md](TODO.md) for planned features.

## Testing Considerations

### Unit Testing

- Mock `ProcessBuilder` for NodeExecutor tests
- Test timeout handling
- Test stream reading edge cases
- Test JSON parsing with sample outputs

### Integration Testing

- Requires Node.js in test environment
- Test with actual Prettier/Stylelint
- Test various CSS syntax errors
- Test large file handling

### Manual Testing

1. Test without Node.js installed (error message)
2. Test with Node.js but no tools (npx downloads)
3. Test formatting valid CSS
4. Test formatting invalid CSS
5. Test SCSS and LESS files
6. Test preference page

## Dependencies

### Eclipse Platform

- `org.eclipse.core.runtime`: Plugin lifecycle, logging
- `org.eclipse.core.resources`: File operations
- `org.eclipse.jface`: Preferences, dialogs
- `org.eclipse.ui.workbench`: Actions, preferences
- `org.eclipse.swt`: UI widgets
- `org.eclipse.ui.ide`: IDE integration

### External Tools (Runtime)

- Node.js (required)
- npm/npx (required, comes with Node.js)
- Prettier (optional, downloaded by npx)
- Stylelint (optional, downloaded by npx)

## Maintenance Notes

### Updating to New Tool Versions

- No code changes needed (npx always uses latest)
- Update documentation if API changes
- Test with new versions before releasing

### Porting to Eclipse JDT

This plugin is standalone (not part of JDT cleanup framework). To integrate with JDT cleanups:

1. Create `CSSCleanUpCore` extending `AbstractMultiFix`
2. Register cleanup in `plugin.xml`
3. Add to `MYCleanUpConstants`
4. Adapt for ICompilationUnit instead of IFile

However, CSS is not Java source code, so this may not be appropriate for JDT.
