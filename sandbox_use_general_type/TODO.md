# sandbox_use_general_type - TODO

## High Priority (Core Functionality)

### 1. Full Method Signature Matching
- [ ] Current implementation only checks method names
- [ ] Need to match full signature: name + parameter types + return type
- [ ] Use `IMethodBinding.getParameterTypes()` and `getReturnType()`
- [ ] **Why**: Multiple overloads might exist; name alone is insufficient

### 2. Assignment Target Type Constraints
- [ ] Check if variable is assigned to another variable with specific type
- [ ] Example: `ArrayList<String> list = ...; Collection<String> col = list;`
- [ ] The assigned-to type (`Collection`) becomes a constraint
- [ ] **Implementation**: Visit AssignmentExpression nodes

### 3. Method Parameter Type Constraints
- [ ] Check if variable is passed as argument to a method
- [ ] The method's parameter type becomes a constraint
- [ ] Example: `takesList(ArrayList<String> list) { ... }; takesList(myVar);`
- [ ] **Implementation**: Check MethodInvocation arguments against parameter types

### 4. Return Type Constraints
- [ ] Check if variable is returned from a method
- [ ] The method's return type becomes a constraint
- [ ] Example: `ArrayList<String> getList() { return myLocalVar; }`
- [ ] **Implementation**: Visit ReturnStatement nodes

## Medium Priority (Robustness)

### 5. Handle Multiple Variables in One Statement
- [ ] Current code processes fragments individually
- [ ] What if: `ArrayList<String> a = ..., b = ...;`
- [ ] Should we split the statement? Or skip it?
- [ ] **Decision needed**: Define desired behavior

### 6. Generic Type Argument Preservation
- [ ] Verify type arguments are correctly preserved
- [ ] Test: `ArrayList<List<String>>` → `List<List<String>>`
- [ ] Ensure nested generics work correctly

### 7. Wildcard Type Handling
- [ ] Handle wildcards: `List<? extends Number>`, `Map<?, String>`
- [ ] Ensure widening preserves wildcard semantics
- [ ] Test with bounded and unbounded wildcards

### 8. Enhanced Field Access Analysis
- [ ] Current implementation collects field names
- [ ] Should also check field types and modifiers
- [ ] Handle static vs instance fields differently

## Low Priority (Nice to Have)

### 9. Method Chaining Support
- [ ] Analyze method chaining patterns
- [ ] Example: `list.stream().filter(...).collect(...)`
- [ ] Ensure chain is valid on widened type

### 10. Lambda and Method Reference Support
- [ ] Check if variable is used in lambda expressions
- [ ] Check if variable is used as method reference target
- [ ] Example: `list.forEach(System.out::println);`

### 11. Anonymous Class Support
- [ ] Check if variable is used inside anonymous classes
- [ ] Capture type constraints from anonymous class context

### 12. Prefer Interfaces Over Abstract Classes
- [ ] Current algorithm checks interfaces last
- [ ] Should prefer interfaces even if abstract class also matches
- [ ] **Rationale**: Interfaces are more general/flexible

### 13. Standard Library Type Preferences
- [ ] Prefer well-known interfaces: `List`, `Map`, `Set`, `Collection`
- [ ] Over custom interfaces or abstract classes
- [ ] **Configuration**: Allow users to configure preference order

## Testing Tasks

### 14. Comprehensive Test Suite
- [ ] Collections: ArrayList→List, LinkedHashMap→Map, HashSet→Set
- [ ] I/O: FileInputStream→InputStream, FileReader→Reader
- [ ] Custom Hierarchies: UserClass→UserInterface
- [ ] Negative: Cast, instanceof, specific methods
- [ ] Edge Cases: var, primitives, arrays, fields, parameters
- [ ] Generics: Type arguments, wildcards, nested generics

### 15. Performance Testing
- [ ] Measure performance on large compilation units
- [ ] Optimize if needed (cache type hierarchy?)

### 16. Integration Testing
- [ ] Test with real Eclipse projects
- [ ] Test with save actions
- [ ] Test with batch cleanup

## Documentation Tasks

### 17. User-Facing Documentation
- [ ] README.md with examples and use cases
- [ ] Screenshots of preferences UI
- [ ] Before/after examples for common patterns

### 18. Javadoc Completion
- [ ] Add Javadoc to all public methods
- [ ] Document algorithm details
- [ ] Add examples in comments

## Future Enhancements

### 19. Configuration Options
- [ ] Allow users to configure "aggressiveness" level
- [ ] Option: Only widen to standard library types
- [ ] Option: Only widen to interfaces (not abstract classes)
- [ ] Option: Specify allowed target types (whitelist)

### 20. Quick Fix Integration
- [ ] Provide quick fix on demand (not just cleanup)
- [ ] Show preview of proposed type change
- [ ] Allow applying to single variable

### 21. Marker/Warning Integration
- [ ] Optionally show warnings for "too specific" types
- [ ] Integrate with Eclipse problem view
- [ ] Provide "Explain" action showing why widening is possible

### 22. Multi-Variable Analysis
- [ ] Analyze groups of related variables together
- [ ] Example: `ArrayList<String> a, b, c;` - what if b needs ArrayList?
- [ ] Provide best common type for the group

## Known Issues

### 23. Import Conflicts
- [ ] If widened type name conflicts with existing type
- [ ] Need to use fully qualified name
- [ ] **Test**: Ensure ImportRewrite handles this

### 24. Array Types
- [ ] Currently skips arrays
- [ ] Should we support: `String[] → Object[]`?
- [ ] **Decision**: Probably too risky, leave as is

### 25. Primitive Wrappers
- [ ] Currently skips primitives
- [ ] Should we support: Variables using wrapper methods?
- [ ] **Example**: `Integer i = ...; i.intValue();` → could stay Integer
- [ ] **Decision**: Low value, skip for now

## Backlog (Long-term)

- [ ] Support for patterns (Java 16+)
- [ ] Support for records
- [ ] Support for sealed classes
- [ ] Integration with JDT refactoring framework
- [ ] Batch processing for entire project
- [ ] Preference page for excluded types
- [ ] Metrics reporting (how many types widened)

## Completed

(Tasks completed will be moved here with completion date)

---

**Last Updated**: 2026-02-12
**Plugin Version**: 1.2.6-SNAPSHOT
