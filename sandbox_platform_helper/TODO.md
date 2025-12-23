# Platform Helper Plugin - TODO

## Status Summary

**Current State**: Stable implementation for Status object simplification

### Completed
- ✅ Status factory method transformation (Java 11+)
- ✅ Java version detection
- ✅ Test coverage for Status transformations

### In Progress
- None currently

### Pending
- [ ] MultiStatus simplification support
- [ ] Custom Status subclass handling
- [ ] Optional plugin ID preservation
- [ ] Additional Platform API simplifications

## Priority Tasks

### 1. MultiStatus Support
**Priority**: Medium  
**Effort**: 6-8 hours

Simplify MultiStatus creation:
```java
// Before
IStatus status = new MultiStatus(pluginId, code, message, exception);
status.add(new Status(...));
status.add(new Status(...));

// After
IStatus status = MultiStatus.of(
    Status.error("Error 1"),
    Status.error("Error 2")
);
```

**Benefits**:
- Cleaner multi-error handling
- Less verbose status aggregation
- Better readability

### 2. Plugin ID Preservation Option
**Priority**: Low  
**Effort**: 4-6 hours

Add option to preserve plugin ID in transformations:
```java
// Current transformation loses plugin ID
new Status(IStatus.ERROR, "my.plugin", "message") 
  → Status.error("message", null)

// Optional transformation preserves plugin ID
new Status(IStatus.ERROR, "my.plugin", "message")
  → Status.error("my.plugin", "message", null)
```

**Implementation**:
- Add preference for plugin ID handling
- Update transformation logic
- Update tests

### 3. Expand to Other Platform APIs
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

### Plugin ID Context Loss
**Status**: Known Limitation  
**Priority**: Low

Factory methods don't include plugin ID, relying on context. In some cases, specific plugin ID is needed for debugging. See Priority Task #2 for potential solution.

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
