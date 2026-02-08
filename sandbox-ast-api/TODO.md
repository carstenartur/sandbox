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

## Phase 2: Expression Wrappers (COMPLETED)
- [x] Create base ASTExpr interface
- [x] Implement MethodInvocationExpr wrapper
  - [x] Fluent receiver() access
  - [x] arguments() access
  - [x] Method resolution to MethodInfo
  - [x] Comprehensive tests (17 tests)
- [x] Implement SimpleNameExpr wrapper
  - [x] Binding resolution (variable/method/type)
  - [x] Type-safe binding access
  - [x] Comprehensive tests (13 tests)
- [x] Implement FieldAccessExpr wrapper
  - [x] Fluent field() access
  - [x] receiver() access  
  - [x] Field resolution to VariableInfo
  - [x] Tests integrated with other tests
- [x] Implement CastExpression wrapper
  - [x] expression() access
  - [x] type access
  - [x] Tests integrated with other tests
- [x] Implement InfixExpression wrapper
  - [x] left/right operand access
  - [x] operator access (InfixOperator enum)
  - [x] Comprehensive tests (14 tests)
- [x] Add expression-level tests (37 new tests, total 98 tests)

## Phase 3: Statement Wrappers (COMPLETED)
- [x] Create base ASTStmt interface
- [x] Implement EnhancedForStmt wrapper
  - [x] Fluent iterable() access
  - [x] parameter() access
  - [x] body() access
  - [x] Comprehensive tests (10 tests)
- [x] Implement WhileLoopStmt wrapper
  - [x] Fluent condition() access
  - [x] body() access
  - [x] Comprehensive tests (10 tests)
- [x] Implement ForLoopStmt wrapper
  - [x] Fluent initializers() access
  - [x] condition() access
  - [x] updaters() access
  - [x] body() access
  - [x] Comprehensive tests (13 tests)
- [x] Implement IfStatementStmt wrapper
  - [x] Fluent condition() access
  - [x] thenStatement() access
  - [x] elseStatement() access
  - [x] Comprehensive tests (12 tests)
- [x] Add statement-level tests (45 new tests, total 143 tests)

## Phase 4: FluentVisitor Builder (COMPLETED)
- [x] Design fluent visitor API
- [x] Implement type-safe visitor builder
- [x] Support pattern matching on node types
- [x] Add visitor composition support
- [x] Add visitor tests and examples

## Phase 5: JDT Bridge Module (COMPLETED)
- [x] Create sandbox-ast-api-jdt module
- [x] Implement converters from JDT AST to sandbox-ast-api
- [x] Implement binding resolution using JDT
- [x] Add unit tests with synthetic JDT nodes and stub bindings
- [ ] Add integration tests with JDT (binding-resolving via ASTParser + classpath)
- [x] Document migration path from JDT to fluent API

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
