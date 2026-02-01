# TODO - Sandbox AST API

## Phase 1: Info Records (COMPLETED)
- [x] Implement TypeInfo with fluent queries
- [x] Implement MethodInfo with pattern detection
- [x] Implement VariableInfo with modifier queries
- [x] Implement ParameterInfo record
- [x] Implement Modifier enum with JDT flag conversion
- [x] Add comprehensive unit tests (61 tests, 88% coverage)
- [x] Add performance benchmarks
- [x] Set up CI/CD workflows

## Phase 2: Expression Wrappers (PLANNED)
- [ ] Create base ASTExpr interface
- [ ] Implement MethodInvocationExpr wrapper
  - [ ] Fluent receiver() access
  - [ ] arguments() access
  - [ ] Method resolution to MethodInfo
- [ ] Implement FieldAccessExpr wrapper
  - [ ] Fluent field() access
  - [ ] receiver() access
  - [ ] Field resolution to VariableInfo
- [ ] Implement SimpleName wrapper
  - [ ] Binding resolution
  - [ ] Type-safe binding access
- [ ] Implement CastExpression wrapper
- [ ] Implement InfixExpression wrapper
- [ ] Add expression-level tests

## Phase 3: Statement Wrappers (PLANNED)
- [ ] Create base ASTStmt interface
- [ ] Implement EnhancedForWrapper
  - [ ] Fluent iterable() access
  - [ ] parameter() access
  - [ ] body() access
- [ ] Implement WhileLoopWrapper
- [ ] Implement ForLoopWrapper
- [ ] Implement IfStatementWrapper
- [ ] Add statement-level tests

## Phase 4: FluentVisitor Builder (PLANNED)
- [ ] Design fluent visitor API
- [ ] Implement type-safe visitor builder
- [ ] Support pattern matching on node types
- [ ] Add visitor composition support
- [ ] Add visitor tests and examples

## Phase 5: JDT Bridge Module (PLANNED)
- [ ] Create sandbox-ast-api-jdt module
- [ ] Implement converters from JDT AST to sandbox-ast-api
- [ ] Implement binding resolution using JDT
- [ ] Add integration tests with JDT
- [ ] Document migration path from JDT to fluent API

## Known Issues
- None currently

## Improvements and Enhancements

### Code Quality
- [ ] Consider using static Set<String> for collection type checks (better performance for large lists)
- [ ] Initialize Builder list fields as mutable ArrayList by default (avoid instanceof checks)
- [ ] Separate null vs empty validation messages for better debugging
- [ ] Add more convenience methods based on usage patterns

### Performance
- [ ] Profile record allocation overhead vs raw operations
- [ ] Optimize hot paths identified by benchmarks
- [ ] Consider caching commonly used TypeInfo instances

### Documentation
- [ ] Add more usage examples in README
- [ ] Create migration guide from raw AST to fluent API
- [ ] Document performance characteristics
- [ ] Add JavaDoc examples for all public methods

### Testing
- [ ] Add more edge case tests
- [ ] Add integration tests with real AST examples
- [ ] Add property-based tests for record invariants

### API Enhancements
- [ ] Add more known method detectors (e.g., String methods, Math methods)
- [ ] Add type hierarchy queries (isSubtypeOf, isSuperTypeOf)
- [ ] Add annotation support in MethodInfo and VariableInfo
- [ ] Add generic type bounds support

## Breaking Changes Planned
None - maintaining backward compatibility in Phase 1 API.

## Questions / Decisions Needed
- Should we add Optional return types for nullable fields?
- Should we support mutable builders or keep immutable?
- What's the migration strategy for existing code using raw JDT?
