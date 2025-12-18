# Sandbox Common Module - TODO

## Status Summary

**Current State**: Stable foundation module providing shared constants and utilities

### Completed
- ✅ `MYCleanUpConstants` - Central repository for cleanup constants
- ✅ Package structure mirrors Eclipse JDT for easy porting
- ✅ Integration with all sandbox plugins

### In Progress
- None currently

### Pending
- [ ] Extract additional shared utilities from individual plugins
- [ ] Create dedicated test module for common utilities
- [ ] Enhance documentation of available utilities

## Priority Tasks

### 1. Audit for Additional Shared Code
**Priority**: Medium  
**Effort**: 4-6 hours

Scan all sandbox plugins for duplicated utility code that could be centralized in `sandbox_common`:
- AST manipulation patterns
- Import management helpers
- Type resolution utilities
- Common transformation patterns

**Benefits**:
- Reduces code duplication
- Improves maintainability
- Easier to port to Eclipse JDT

### 2. Create Dedicated Test Module
**Priority**: Low  
**Effort**: 2-3 hours

Currently, shared utilities lack dedicated unit tests. Consider creating `sandbox_common_test` if the utility code grows.

**Requirements**:
- Test `MYCleanUpConstants` integrity
- Test any shared utility methods
- Follow existing test patterns from other modules

### 3. Document Available Utilities
**Priority**: Medium  
**Effort**: 2-3 hours

Create comprehensive documentation for shared utilities:
- Method signatures and purposes
- Usage examples from existing plugins
- Best practices for using common utilities
- Update this architecture.md with detailed utility descriptions

## Known Issues

None currently identified.

## Future Enhancements

### Utility Library Expansion
Consider adding more sophisticated shared utilities:
- **AST Builders**: Fluent builders for common AST node types
- **Matcher Library**: Reusable AST pattern matchers
- **Transformation Templates**: Common code transformation patterns
- **Validation Utilities**: Shared precondition checking logic

### Configuration Framework
Explore creating a configuration framework for cleanup preferences:
- Standard patterns for defining cleanup options
- UI generation helpers for preference pages
- Validation and default value management

### Documentation Generation
Consider auto-generating documentation from constant definitions:
- Extract cleanup descriptions from constant names
- Generate cleanup catalog from `MYCleanUpConstants`
- Create developer guide for adding new cleanups

## Technical Debt

### Constant Organization
**Priority**: Low

`MYCleanUpConstants` currently contains constants from all plugins. Consider whether to:
- Keep centralized (current approach - simpler)
- Split into plugin-specific constant classes (more modular)
- Use a hybrid approach (core constants centralized, plugin-specific constants distributed)

**Decision Criteria**:
- How does Eclipse JDT organize their constants?
- What makes porting to Eclipse JDT easier?
- What improves maintainability for sandbox development?

## Testing Strategy

Currently, testing is indirect through dependent plugins. Future improvements:
- Direct unit tests for utility methods
- Integration tests for constant integrity
- Validation that all cleanup IDs are unique
- Verification that constants follow naming conventions

## Contribution to Eclipse JDT

### Prerequisites
Before contributing shared utilities to Eclipse JDT:
1. Ensure utilities are well-tested and documented
2. Verify they follow Eclipse coding conventions
3. Confirm they don't duplicate existing Eclipse utilities
4. Get approval for the utility's inclusion in Eclipse codebase

### Porting Checklist
- [ ] Replace `org.sandbox` with `org.eclipse` in package names
- [ ] Merge `MYCleanUpConstants` into Eclipse's `CleanUpConstants`
- [ ] Move utilities to appropriate Eclipse modules
- [ ] Update all dependent plugins to use Eclipse constants
- [ ] Submit to Eclipse Gerrit for review
- [ ] Update documentation to reflect new Eclipse structure

## References

- [Eclipse CleanUpConstants](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java)
- [Eclipse JDT Core Manipulation](https://github.com/eclipse-jdt/eclipse.jdt.ui/tree/master/org.eclipse.jdt.core.manipulation)

## Contact

For questions about shared utilities or adding new common code, please open an issue or discussion in the repository.
