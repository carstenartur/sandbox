# Platform Helper Plugin - TODO

## Status Summary

**Current State**: Stable implementation for Status and MultiStatus simplification

### Completed
- ✅ Status factory method transformation (Java 11+)
- ✅ Java version detection
- ✅ Test coverage for Status transformations
- ✅ Plugin ID preservation option
- ✅ MultiStatus code normalization (to IStatus.OK)

### In Progress
- None currently

### Pending
- [ ] Advanced MultiStatus pattern detection (detect .add() calls)
- [ ] Custom Status subclass handling
- [ ] Additional Platform API simplifications

## Priority Tasks

### 1. Advanced MultiStatus Pattern Detection
**Priority**: Low  
**Effort**: 8-10 hours

Detect and optimize MultiStatus usage with .add() calls:
```java
// Detect this pattern
MultiStatus status = new MultiStatus(pluginId, code, message, null);
status.add(new Status(IStatus.ERROR, pluginId, "error1", null));
status.add(new Status(IStatus.WARNING, pluginId, "warning1", null));

// Suggest optimization (future enhancement)
```

**Benefits**:
- More comprehensive MultiStatus handling
- Better pattern recognition
- Potential for suggesting alternative approaches

### 2. Expand to Other Platform APIs
**Priority**: Medium  
**Effort**: 10-12 hours

Simplify additional Eclipse Platform APIs:
- `CoreException` creation
- `OperationCanceledException` patterns
- `IProgressMonitor` boilerplate
- `IAdaptable` implementations

**Benefits**:
- More comprehensive Platform API cleanup
- Consistent modernization across plugin code

## Known Issues

### MultiStatus Factory Methods
**Status**: Not Available  
**Priority**: N/A

Eclipse Platform's MultiStatus class does not have factory methods like `Status.error()` or `Status.warning()`. The current implementation normalizes the status code to `IStatus.OK`, which is appropriate since MultiStatus overall status is determined by its child statuses.

### Plugin ID Preservation Default
**Status**: Configurable  
**Priority**: Low

The plugin ID preservation option defaults to `false` (plugin ID is omitted). Users can enable it via preferences if they need to preserve plugin IDs in factory method calls. This maintains backward compatibility with existing cleanup behavior.

## Configuration Notes

### Default Behavior
- **Status cleanup**: Enabled via preferences, disabled by default
- **Plugin ID preservation**: Disabled by default (plugin ID omitted in factory methods)
- **MultiStatus normalization**: Included when Status cleanup is enabled

### Preferences Location
1. **Preferences > Java > Code Style > Clean Up**
2. Select or create a cleanup profile
3. Navigate to **Platform Status** section
4. Options:
   - **Simplify Platform Status**: Enable Status/MultiStatus cleanups
   - **Preserve plugin ID in Status factory methods**: Keep plugin ID as first parameter

## Future Enhancements

### Eclipse Logging Integration
**Priority**: Low  
**Effort**: 8-10 hours

Integrate with Eclipse's logging framework:
- Transform Status creation + logging calls
- Use modern logging APIs
- Simplify error reporting patterns

### Status Utility Methods
**Priority**: Low  
**Effort**: 6-8 hours

Add more Status-related simplifications:
- Status severity checking
- Status merging and combining
- Status conversion utilities

### Platform Pattern Library
**Priority**: Medium  
**Effort**: 12-15 hours

Expand beyond Status to common Platform patterns:
- Extension point handling
- Preference store usage
- Resource management
- Job API patterns

## Testing Strategy

### Current Coverage
Good test coverage for Status factory transformations in `sandbox_platform_helper_test`.

### Future Testing
- Add tests for MultiStatus support
- Add tests for plugin ID preservation
- Performance tests for large codebases
- Integration tests with real Eclipse plugins

## Eclipse Contribution

### Contribution Potential
Platform API cleanups could benefit Eclipse plugin developers. Consider:
- Proposing as Eclipse Platform enhancement
- Integration with Eclipse JDT cleanups
- Documentation for Eclipse developers

### Prerequisites
- [ ] Expand beyond Status to cover more Platform APIs
- [ ] Comprehensive testing with real Eclipse plugins
- [ ] User documentation and examples
- [ ] Community feedback on transformation approach

### Porting Checklist
- [ ] Replace `org.sandbox` with `org.eclipse` in package names
- [ ] Move classes to appropriate Eclipse module
- [ ] Register cleanup in Eclipse's extension points
- [ ] Submit to Eclipse Gerrit for review

## Technical Debt

None currently identified. The codebase is focused and well-tested.

## Performance Considerations

### Current Performance
Efficient single-pass AST traversal. No performance issues identified.

### Optimization Opportunities
- Cache type bindings for Status class
- Early exit if file contains no Status usage
- Batch processing for multiple files

## References

- [Eclipse Platform Status API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/Status.html)
- [Eclipse Platform Error Handling](https://wiki.eclipse.org/FAQ_How_do_I_use_IStatus_to_handle_problems%3F)

## Contact

For questions about Platform Helper cleanup or suggestions for improvements, please open an issue in the repository.
