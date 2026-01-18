# Sandbox Common Fast Test - TODO

> **Navigation**: [Main README](../README.md)

## Completed

- [x] Create module structure with jar packaging
- [x] Set up JUnit 5 dependencies
- [x] Configure Maven Surefire plugin

## In Progress

- [ ] Add first test cases

## Pending Tasks

### Test Migration
- [ ] Review tests in sandbox_common_test
- [ ] Identify tests that don't require Eclipse runtime
- [ ] Port applicable tests to this module
- [ ] Update tests to use JUnit 5 syntax

### Test Coverage
- [ ] Add tests for HelperVisitor
- [ ] Add tests for ReferenceHolder
- [ ] Add tests for ASTProcessor
- [ ] Add tests for ASTNodeUtils
- [ ] Add tests for VisitorEnum

### Test Infrastructure
- [ ] Create test utilities for AST creation
- [ ] Add helper methods for common test patterns
- [ ] Consider adding AssertJ or other assertion libraries if needed

## Future Enhancements

- [ ] Add integration tests
- [ ] Add performance benchmarks
- [ ] Compare test execution speed with Tycho-based tests

## Notes

- Tests should run without Xvfb or Eclipse runtime
- Focus on unit tests for core logic
- Keep tests fast and focused
