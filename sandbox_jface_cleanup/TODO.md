# JFace Cleanup Plugin - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#jface_cleanup) | [Architecture](ARCHITECTURE.md)

## Status Summary

**Current State**: Active development of SubProgressMonitor → SubMonitor migration cleanup

### Completed
- ✅ Basic plugin structure
- ✅ Test infrastructure
- ✅ SubProgressMonitor → SubMonitor migration for beginTask pattern
- ✅ Flag translation (SUPPRESS_SUBTASK_LABEL → SUPPRESS_SUBTASK, PREPEND_MAIN_LABEL_TO_SUBTASK dropped)
- ✅ Standalone SubProgressMonitor conversion (without beginTask)
- ✅ ViewerSorter → ViewerComparator migration

### In Progress (Eclipse JDT UI SubMonitor Migration Gaps)
- [ ] Runtime instanceof SubMonitor checks
- [ ] Removal of .done() calls after SubMonitor migration
- [ ] Helper method detection and cleanup

### Pending
- [ ] Comprehensive JFace API coverage beyond progress monitoring
- [ ] SWT integration
- [ ] Community feedback on transformations

## Current Work: SubMonitor Migration Enhancements

### Reference
Based on Eclipse JDT UI PR #2641 analysis, addressing gaps in SubProgressMonitor → SubMonitor migration.

### Completed Features

#### 1. Standalone SubProgressMonitor Conversion ✅
**Status**: Implemented and tested

Transforms SubProgressMonitor instances that don't have an associated beginTask() call:
```java
// Before
IProgressMonitor sub = new SubProgressMonitor(monitor, 50);

// After  
IProgressMonitor sub = SubMonitor.convert(monitor).split(50);
```

**Test Coverage**:
- StandaloneSubProgressMonitor test case
- StandaloneSubProgressMonitorWithFlags test case

#### 2. Flag Translation ✅
**Status**: Implemented and tested

Maps SubProgressMonitor flags to SubMonitor equivalents:
- `SUPPRESS_SUBTASK_LABEL` → `SUPPRESS_SUBTASK`
- `PREPEND_MAIN_LABEL_TO_SUBTASK` → dropped (no equivalent)

**Test Coverage**:
- SuppressSubtaskLabelFlag test case
- PrependMainLabelToSubtaskFlag test case

#### 3. Type-Aware SubMonitor Detection (ITypeBinding-based type check) ✅
**Status**: Implemented and tested

Detects when SubProgressMonitor is created on a variable already typed as SubMonitor and uses split() directly:
```java
// Before
SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = new SubProgressMonitor(subMonitor, 50);

// After
SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor.split(50);
```

**Implementation Details**:
- Uses ITypeBinding to check variable type at compile-time
- Avoids unnecessary convert() when variable is already SubMonitor
- Preserves flag mapping when using direct split()

**Test Coverage**:
- SubProgressMonitorOnSubMonitorVariable test case

### Remaining Features

#### 4. Removal of .done() Calls
**Status**: Not implemented (deferred)
**Priority**: Low  
**Effort**: 6-8 hours
**Reason for deferral**: Complex scope and control flow tracking; may be better as separate cleanup pass

**Goal**: Remove redundant .done() calls on monitors after migration to SubMonitor

**Pattern to detect**:
```java
// Before migration
IProgressMonitor sub = new SubProgressMonitor(monitor, 50);
try {
    // work
} finally {
    sub.done(); // Should be removed after migration
}

// After migration
IProgressMonitor sub = subMonitor.split(50);
try {
    // work  
} finally {
    // .done() call removed - SubMonitor handles cleanup automatically
}
```

**Implementation approach**:
1. Track variables created from SubProgressMonitor migration
2. Find .done() method invocations on those variables
3. Remove the done() calls as they're redundant with SubMonitor
4. Consider scope and control flow to avoid removing unrelated done() calls

**Challenges**:
- Need to track variable usage across scope
- Must handle control flow (try-finally, if-else, loops)
- Don't remove done() on non-migrated monitors

#### 5. Helper Method Detection
**Status**: Not implemented (deferred)
**Priority**: Low  
**Effort**: 8-10 hours
**Reason for deferral**: Better suited as separate cleanup pass; requires method inlining analysis

**Goal**: Detect and refactor helper methods that only wrap SubProgressMonitor creation

**Pattern to detect**:
```java
// Helper method that becomes obsolete after migration
private IProgressMonitor getSubProgressMonitor(IProgressMonitor monitor, int work) {
    return new SubProgressMonitor(monitor, work);
}

// Usage
IProgressMonitor sub = getSubProgressMonitor(monitor, 50);
```

**After refactoring**:
```java
// Helper could be removed or updated
IProgressMonitor sub = SubMonitor.convert(monitor).split(50);
```

**Implementation approach**:
1. Detect private methods that only create and return SubProgressMonitor
2. Find all call sites
3. Either:
   - Inline the helper (replace calls with direct SubMonitor usage)
   - Update the helper to use SubMonitor
   - Suggest removal in code review

**Challenges**:
- Identifying "only wraps SubProgressMonitor" pattern
- Ensuring safe inlining across different call sites
- May be better as a separate cleanup pass

## Priority Tasks

### 1. Identify High-Value Transformations
**Priority**: High  
**Effort**: 4-6 hours

Survey JFace codebases to identify common cleanup opportunities:
- Deprecated API usage patterns
- Verbose code that can be simplified
- Common JFace anti-patterns
- Resource management improvements

**Approach**:
- Analyze Eclipse platform code
- Review JFace migration guides
- Survey community for pain points

### 2. Implement Core Transformations
**Priority**: High  
**Effort**: 12-15 hours

Implement most valuable JFace cleanups:
- Dialog API modernization
- Viewer pattern simplification
- Resource disposal patterns
- JFace databinding updates

### 3. Test with Real-World Code
**Priority**: Medium  
**Effort**: 6-8 hours

Validate transformations against real Eclipse plugins:
- Test with Eclipse platform code
- Test with popular Eclipse plugins
- Gather feedback from users
- Refine based on results

## Known Issues

Plugin is in early experimental stage. No specific issues identified yet.

## Future Enhancements

### JFace Databinding Modernization
**Priority**: Medium  
**Effort**: 10-12 hours

Update JFace databinding code to modern patterns:
- Simplify binding creation
- Update to new APIs
- Improve type safety

### SWT Integration
**Priority**: Low  
**Effort**: 15-20 hours

Extend to SWT (non-JFace) cleanups:
- Resource disposal patterns
- Layout modernization
- Widget creation simplification

### Wizard Modernization
**Priority**: Low  
**Effort**: 8-10 hours

Modernize wizard implementations:
- Simplify wizard page creation
- Update wizard lifecycle patterns
- Improve wizard validation

## Testing Strategy

### Current Coverage
Basic test infrastructure in place.

### Needed Tests
- Tests for each JFace transformation
- Integration tests with Eclipse workbench
- UI tests for JFace UI components
- Performance tests for large JFace codebases

## Eclipse Contribution

### Contribution Potential
JFace cleanups could be valuable to Eclipse community. Consider:
- Proposing as Eclipse Platform enhancement
- Integration with existing Eclipse cleanups
- Standalone Eclipse Marketplace plugin

### Prerequisites
- [ ] Identify and implement high-value transformations
- [ ] Comprehensive testing with real-world code
- [ ] User documentation
- [ ] Community feedback and iteration

## Technical Debt

Plugin is in early stage. Technical debt will be identified as implementation progresses.

## References

- [JFace Documentation](https://wiki.eclipse.org/JFace)
- [Eclipse Platform UI Guidelines](https://wiki.eclipse.org/User_Interface_Guidelines)

## Contact

For questions about JFace cleanup or suggestions for transformations, please open an issue in the repository.
