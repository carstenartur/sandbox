# Usage View Plugin - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#usage_view) | [Architecture](ARCHITECTURE.md)

## Status Summary

**Current State**: Functional view for detecting naming conflicts (same name, different type) with preference support and auto-update

### Completed Features
- ✅ Basic table view UI with sortable columns
- ✅ Naming conflict detection (same name, different type)
- ✅ Navigation to code from table
- ✅ Preference page for auto-show at startup
- ✅ Automatic view updates when editor changes
- ✅ IPartListener2 integration for editor event tracking
- ✅ Link with Selection toggle for manual control
- ✅ Filter Naming Conflicts toggle for focused view
- ✅ AST-based variable binding collection using AstProcessorBuilder
- ✅ Proper error logging with Eclipse ILog
- ✅ Support for analyzing projects, packages, and compilation units

### In Progress
- None currently

### Pending High-Priority Features
- [ ] Configurable naming conventions (beyond current type-based conflicts)
- [ ] Enhanced pattern detection (case variations, abbreviations)
- [ ] Quick fix integration for automatic renaming
- [ ] Export to CSV/Excel for reporting

## Priority Tasks

### 1. Configurable Naming Conventions
**Priority**: High  
**Effort**: 6-8 hours

**Current State**: Only detects same-name/different-type conflicts

Allow users to configure additional naming rules:
- Define custom patterns beyond type conflicts
- Detect case inconsistencies (userId vs userID)
- Identify abbreviation variations (id vs identifier, temp vs temporary)
- Configure prefix/suffix matching rules (m_field vs field)
- Set severity levels for different violation types

**Benefits**:
- Adapt to project-specific conventions
- Catch more subtle naming issues
- More flexible analysis tool

**Implementation Notes**:
- Extend NamingConflictFilter with configurable pattern matchers
- Add preference page UI for rule configuration
- Persist rules in preference store

### 2. Quick Fix Integration
**Priority**: High  
**Effort**: 8-10 hours

**Current State**: RenameSupport is imported but not used; VariableNameSuggester exists but only for manual dialogs

Provide automated quick fixes for naming violations:
- Suggest correct naming based on type and conventions
- Integrate with Eclipse refactoring framework (RenameSupport)
- Rename variable with full reference updates
- Preview changes before applying
- Support for undo/redo

**Implementation**:
- Implement IMarkerResolution or IQuickFixProcessor
- Use existing VariableNameSuggester for name generation
- Integrate with JDT refactoring APIs (RenameSupport already imported)
- Provide multiple fix suggestions when applicable

### 3. Batch Operations
**Priority**: Medium  
**Effort**: 6-8 hours

Support batch processing of naming issues:
- Select multiple violations to fix
- Apply same fix to similar patterns
- Batch renaming across files
- Undo/redo for batch operations

**Benefits**:
- Faster cleanup of large codebases
- Consistent fixes across project
- Time savings

### 4. Enhanced Pattern Detection
**Priority**: Medium  
**Effort**: 8-10 hours

**Current State**: Only detects same-name/different-type conflicts

Improve naming pattern detection beyond current implementation:
- Detect similar names with different conventions (userId vs userID vs user_id)
- Identify typos and near-matches using edit distance
- Find inconsistent abbreviations (id vs identifier across codebase)
- Detect domain-specific naming issues
- Case variation detection (XML vs xml vs Xml)

**Examples to Detect**:
- `userId` vs `userID` vs `user_id` (case/style variations)
- `getUser` vs `fetchUser` vs `retrieveUser` (inconsistent verb usage)
- `temp` vs `tmp` vs `temporary` (abbreviation inconsistency)

**Implementation Notes**:
- Implement similarity algorithms (Levenshtein distance, case-insensitive matching)
- Group variables by semantic similarity
- Extend NamingConflictFilter with new pattern matchers
- Add configuration options for sensitivity thresholds

## Known Issues

### Scope of Detection
**Status**: Known Limitation  
**Priority**: Medium

**Current State**: Only detects "same name, different type" conflicts

**Issue**: The plugin does not detect:
- Case variations (userId vs userID) - same type, different casing
- Abbreviation inconsistencies (id vs identifier) - similar meaning, different names
- Prefix/suffix variations (m_field vs field) - style differences
- Method naming inconsistencies (getUser vs fetchUser)

**Planned Fix**: Enhanced pattern detection (see Priority Task #4 above)

### False Positives Not Applicable
**Status**: N/A for Current Implementation

**Note**: The current simple "same name, different type" detection has very few false positives because it relies on Eclipse JDT's type system for accurate type matching. Future enhanced pattern detection may introduce false positives that will need heuristics to filter.

### Performance with Large Codebases
**Status**: Open  
**Priority**: Low

Analysis can be slow for very large projects. Consider:
- Incremental analysis
- Caching results
- Background processing
- Scope limiting

## Future Enhancements

### Machine Learning Integration
**Priority**: Low  
**Effort**: 15-20 hours

Use ML to learn project-specific naming patterns:
- Analyze existing code to learn conventions
- Suggest names based on similar code
- Adapt to team's style over time
- Identify outliers automatically

### Team Naming Standards
**Priority**: Medium  
**Effort**: 10-12 hours

Support team-wide naming standards:
- Share naming configurations across team
- Version control for naming rules
- Enforce standards in CI/CD
- Generate compliance reports

### Integration with Code Reviews
**Priority**: Low  
**Effort**: 8-10 hours

Integrate with code review tools:
- Flag naming issues in pull requests
- Comment on violations automatically
- Track naming quality over time
- Provide metrics and trends

### Naming Refactoring Wizard
**Priority**: Medium  
**Effort**: 10-12 hours

Create wizard for systematic naming improvements:
1. Scan project for inconsistencies
2. Group by violation type
3. Suggest fixes
4. Preview all changes
5. Apply selectively or in bulk
6. Generate refactoring report

## Testing Strategy

### Current Coverage
- Basic functional tests for view creation and navigation
- Manual testing of conflict detection
- Preference page functionality verified

### Needed Tests
- **Unit tests** for NamingConflictFilter logic
- **Unit tests** for VariableBindingVisitor with various AST structures
- **Unit tests** for VariableNameSuggester suggestions
- **Integration tests** with sample codebases containing known conflicts
- **Performance tests** with large codebases (parallel processing evaluation)
- **UI tests** for table operations, filtering, and sorting
- **Regression tests** for quick fixes (when implemented)

## Eclipse Integration

### Integration Potential
Could be valuable as Eclipse JDT enhancement or standalone plugin.

### Current Prerequisites for Integration
- [x] Basic table view implementation
- [x] IPartListener2 and IStartup integration
- [x] Uses sandbox_common's AstProcessorBuilder
- [x] Proper error logging

### Remaining Prerequisites for Upstream Contribution
- [ ] Configurable conventions
- [ ] Quick fix support with RenameSupport
- [ ] Comprehensive testing (unit and integration tests)
- [ ] User documentation (in progress)
- [ ] Community feedback and validation

## Technical Debt

### Pattern Detection Architecture
**Current State**: Simple type-based conflict detection works well but is limited

**Issue**: Current implementation only detects one type of conflict. The architecture can support more sophisticated detection but needs:
- Pluggable pattern matcher interface
- Configuration framework for custom rules
- Severity and priority system for different violation types

**Future**: Consider refactoring to support:
- Multiple pattern matchers running concurrently
- Weighted scoring for conflict severity
- User-defined custom patterns via extension points

### Use of sandbox_common
**Status**: Partially Implemented

**Current**: Uses AstProcessorBuilder for AST traversal - ✅ Good!

**Potential**: Could leverage more sandbox_common utilities:
- `NamingUtils` - For case conversion and naming conventions
- `TypeCheckingUtils` - For more sophisticated type analysis
- `AnnotationUtils` - If extending to check annotation-based naming rules

### Error Handling
**Status**: Recently Improved

**Recent Changes**: 
- ✅ Replaced printStackTrace() with proper ILog logging
- ✅ Removed debug System.out.println statements
- ✅ Added descriptive error messages

**Remaining**: Consider more graceful handling of AST parsing errors in large projects

## Performance Optimization

### Incremental Analysis
Implement incremental analysis to improve performance:
- Only re-analyze changed files
- Cache analysis results
- Background processing
- Progressive result display

### Scope Optimization
Allow limiting analysis scope:
- Selected files only
- Current project only
- Specific packages
- Configurable file filters

## References

- [Java Naming Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-namingconventions.html)
- [Eclipse Code Style Guide](https://wiki.eclipse.org/Coding_Conventions)

## Contact

For questions about the usage view plugin or suggestions for improvements, please open an issue in the repository.
