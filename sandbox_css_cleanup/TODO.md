# CSS Cleanup Plugin - TODO

## Completed Features

- [x] Basic plugin structure
- [x] NodeExecutor for process execution
- [x] PrettierRunner for CSS formatting
- [x] StylelintRunner for CSS validation
- [x] CSSCleanupAction for right-click menu
- [x] CSSPreferencePage for configuration
- [x] Support for .css, .scss, .less files
- [x] Graceful error handling when Node.js not installed

## High Priority

- [ ] **Improve JSON parsing in StylelintRunner**
  - Currently uses simple string matching
  - Should use Gson or Jackson for robust parsing
  - Extract line, column, severity, rule name from JSON

- [ ] **Progress indication for long operations**
  - Format/validate can take several seconds
  - Show Eclipse progress dialog with cancel button
  - Use `IRunnableWithProgress` and `ProgressMonitorDialog`

- [ ] **Batch processing support**
  - Format multiple files in one operation
  - Show summary dialog with results
  - Skip files that fail, continue with others

- [ ] **Eclipse markers for validation issues**
  - Create problem markers for Stylelint warnings
  - Show in Problems view
  - Link to line/column in editor
  - Use `IMarker` API

## Medium Priority

- [ ] **Support for custom Prettier config**
  - Read `.prettierrc` from project root
  - Allow specifying config path in preferences
  - Pass config to Prettier via `--config` flag

- [ ] **Support for custom Stylelint config**
  - Read `.stylelintrc` from project root
  - Allow specifying config path in preferences
  - Pass config to Stylelint via `--config` flag

- [ ] **Quick fixes for common Stylelint issues**
  - Implement `IMarkerResolution` for auto-fixes
  - Use `stylelint --fix` output
  - Show quick fix proposals in editor

- [ ] **Format on save integration**
  - Hook into Eclipse save action
  - Format CSS files automatically on save
  - Configurable in preferences (off by default)

- [ ] **Cache tool availability checks**
  - Don't check on every action invocation
  - Cache for 5 minutes or until preference change
  - Improves performance for repeated operations

## Low Priority

- [ ] **Support for PostCSS**
  - Add PostCSS integration alongside Prettier/Stylelint
  - Configurable PostCSS plugins
  - Requires additional npm package

- [ ] **Inline documentation for preferences**
  - Add tooltips explaining each preference
  - Link to Prettier/Stylelint documentation
  - Example configurations

- [ ] **Custom error messages per tool**
  - Different message for Prettier vs Stylelint failures
  - Show actual error from tool output
  - Suggest fixes based on error type

- [ ] **Format selection (not just whole file)**
  - Allow formatting selected text in editor
  - Requires different approach (text selection API)
  - Prettier supports range formatting

- [ ] **Integration with Eclipse's generic editor**
  - Add format action to CSS editor toolbar
  - Show validation markers in editor ruler
  - Requires hooking into editor lifecycle

## Known Issues

- **Prettier download delay on first use**
  - npx downloads Prettier on first invocation
  - User sees delay with no feedback
  - **Workaround**: Show message about initial setup
  - **Fix**: Pre-download in background or show progress

- **No cancel button during formatting**
  - User can't cancel long-running format
  - **Fix**: Use Job API with progress monitor

- **Limited error information from tools**
  - Currently only show exit code
  - **Fix**: Parse stderr for detailed errors

- **No validation while typing**
  - Only validates on manual action
  - **Enhancement**: Use reconciler for live validation

## Testing Needs

- [ ] Unit tests for NodeExecutor
- [ ] Unit tests for result parsing
- [ ] Integration tests with real tools
- [ ] Manual testing on Windows/Mac/Linux
- [ ] Testing with large CSS files
- [ ] Testing with syntax errors
- [ ] Testing without network access (npx cache)

## Documentation Needs

- [ ] User guide with screenshots
- [ ] Video tutorial showing installation and usage
- [ ] Troubleshooting guide for common issues
- [ ] Comparison with other CSS formatting plugins
- [ ] Contributing guide for developers

## Performance Optimizations

- [ ] **Parallel processing for multiple files**
  - Use thread pool for batch operations
  - Max 4 concurrent processes
  - Show aggregated progress

- [ ] **Incremental validation**
  - Only validate changed files
  - Use Eclipse resource change listeners
  - Skip unchanged files in batch operations

- [ ] **Reduce process startup overhead**
  - Consider using daemon processes
  - Or keep npx process alive between invocations
  - Requires IPC mechanism

## Future Ideas

- [ ] **Support for CSS preprocessors**
  - SASS compilation before formatting
  - LESS compilation before formatting
  - Integrate with existing build tools

- [ ] **Integration with browser dev tools**
  - Export formatted CSS to browser
  - Import CSS from browser for formatting
  - Requires browser extension

- [ ] **CSS minification support**
  - Add "Minify CSS" action
  - Use cssnano or clean-css
  - Useful for production builds

- [ ] **CSS complexity metrics**
  - Show cyclomatic complexity
  - Highlight overly specific selectors
  - Suggest simplifications

- [ ] **CSS code completion based on Stylelint**
  - Use Stylelint's schema for suggestions
  - Validate property values as you type
  - Requires language server protocol

## Research Needed

- [ ] Investigate LSP (Language Server Protocol) for CSS
  - Modern approach to language tooling
  - Might replace process execution
  - Requires significant refactoring

- [ ] Investigate WebAssembly for tools
  - Run Prettier/Stylelint in JVM via WASM
  - Eliminates Node.js dependency
  - May not be practical yet

- [ ] Investigate alternative to npx
  - Bundled Node.js runtime
  - Preinstalled tools
  - Reduces setup burden

## Breaking Changes (Future)

- None planned currently
- Plugin API is internal (`org.sandbox.jdt.internal.css`)
- No public API to maintain

## Migration Notes

If porting to Eclipse JDT core:
- This plugin is CSS-specific, not Java
- May not fit in JDT cleanup framework
- Consider separate Web Tools Platform integration
- Or standalone plugin in Eclipse marketplace
