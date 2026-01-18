# Sandbox Common Core - TODO

> **Navigation**: [Main README](../README.md) | [ARCHITECTURE](ARCHITECTURE.md)

## Completed

- [x] Create OSGi-free core module with jar packaging
- [x] Extract HelperVisitor and related classes
- [x] Extract ReferenceHolder
- [x] Extract ASTProcessor and AstProcessorBuilder
- [x] Create ASTNodeUtils to replace org.eclipse.jdt.internal.corext.dom.ASTNodes
- [x] Fix LambdaASTVisitor to use ASTNodeUtils instead of ASTNodes
- [x] Add comprehensive documentation

## In Progress

- [ ] Add unit tests to sandbox_common_fast_test
- [ ] Verify builds work without Tycho

## Pending Tasks

### Testing
- [ ] Port applicable tests from sandbox_common_test to sandbox_common_fast_test
- [ ] Add tests for ASTNodeUtils methods
- [ ] Add tests for HelperVisitor usage patterns
- [ ] Verify all copied classes work correctly without OSGi

### Additional Utilities
- [ ] Identify other commonly-used JDT internal utilities that need OSGi-free equivalents
- [ ] Consider extracting annotation utilities if they don't require OSGi
- [ ] Consider extracting naming utilities if they don't require OSGi
- [ ] Consider extracting type checking utilities if they don't require OSGi

### Documentation
- [ ] Add usage examples in README.md
- [ ] Document all public APIs in ASTNodeUtils
- [ ] Add migration guide for converting OSGi code to use core module

### Integration
- [ ] Update sandbox_common (OSGi plugin) to optionally use sandbox_common_core
- [ ] Ensure no breaking changes to existing plugins
- [ ] Consider using sandbox_common_core as dependency in OSGi plugin

## Future Enhancements

### Functional Converter Core
- [ ] Create sandbox_functional_converter_core module
- [ ] Extract AST transformation logic from sandbox_functional_converter
- [ ] Create sandbox_functional_converter_fast_test
- [ ] Identify and recreate any OSGi dependencies

### Performance
- [ ] Benchmark test execution: Tycho vs standard Maven
- [ ] Measure build time improvements
- [ ] Document performance gains

### Additional Core Modules
- [ ] Consider extracting core logic from other cleanup plugins
- [ ] Identify common patterns that could be extracted
- [ ] Create reusable transformation utilities

## Known Issues

None currently.

## Questions/Decisions Needed

- **Q:** Should sandbox_common (OSGi) depend on sandbox_common_core to avoid code duplication?
  - **Current:** Both versions exist independently
  - **Alternative:** OSGi version imports from core
  - **Decision:** Keep independent for now, evaluate after validation

- **Q:** Should we extract more utility classes from org.sandbox.jdt.internal.corext.util?
  - **Current:** Only extracted what's needed for current classes
  - **Alternative:** Extract all util classes proactively
  - **Decision:** Extract incrementally as needed

## Notes

- The goal is minimal changes - we're extracting, not refactoring
- OSGi plugins continue to work unchanged
- Core modules enable faster testing and broader usage
- Focus on extracting logic that doesn't truly need OSGi
