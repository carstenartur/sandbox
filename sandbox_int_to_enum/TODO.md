# TODO: Int to Enum/Switch Refactoring Plugin

## Current Implementation Status

### Completed
- [x] Basic plugin structure created
- [x] Documentation files (ARCHITECTURE.md, TODO.md, README.md)
- [x] Refactored to follow repository patterns
- [x] Helper structure (AbstractTool, IntToEnumHelper) following JFace pattern
- [x] Using ReferenceHolder from sandbox_common
- [x] CompilationUnitRewriteOperationWithSourceRange implementation
- [x] Proper static imports and code organization
- [x] IntConstantHolder data structure defined

### Implementation Complexity Note

**The actual transformation logic is marked as a placeholder for the following reasons:**

1. **Complexity of Transformation**: Converting int constants to enum with switch statements requires:
   - Complex AST pattern matching across multiple node types
   - Sophisticated scope and type analysis
   - Coordinated multi-step AST rewrites
   - Careful handling of data flow and control flow
   
2. **Required Analysis**:
   - Find all int constant declarations with common prefixes
   - Analyze if-else chains to identify which constants are related
   - Determine variable types and propagate enum type through code
   - Handle edge cases (constants used in calculations, comparisons, etc.)
   
3. **Multiple Coordinated Changes**:
   - Generate new enum declaration
   - Remove or replace int constant fields
   - Convert if-else to switch (with proper break statements)
   - Update method parameters from `int` to enum type
   - Update variable declarations
   - Add imports if needed
   
4. **Edge Cases**:
   - Constants used outside of if-else chains
   - Mixed usage (some constants in if-else, others in calculations)
   - Constants from different scopes or classes
   - Integer values used without named constants

### Current State
- [ ] Core transformation implementation (Placeholder with extensive comments)
  - Structure is in place but returns no operations
  - Prevents incorrect transformations
  - Demonstrates intended architecture

### Next Steps for Full Implementation
- [ ] Implement pattern detection in find() method
  - [ ] Use AstProcessorBuilder for field and if-statement visitors
  - [ ] Detect public static final int constants
  - [ ] Find if-else chains comparing against constants
  - [ ] Group related constants by common prefixes
  
- [ ] Implement transformation in rewrite() method
  - [ ] Generate enum declaration from constants
  - [ ] Create switch statement from if-else chain
  - [ ] Update variable types
  - [ ] Handle break statements and fall-through
  
- [ ] Comprehensive testing
  - [ ] Test with various constant patterns
  - [ ] Test edge cases
  - [ ] Verify no regressions


## Known Limitations

1. **Pattern Detection**
   - Currently only detects simple if-else chains with direct equality comparisons
   - Does not handle switch statements on integers (already optimal)
   - Does not handle complex boolean expressions

2. **Scope Analysis**
   - Only processes constants in the same class
   - Does not follow constant references across compilation units
   - Conservative approach may miss some valid transformation opportunities

3. **Naming**
   - Enum name generation is heuristic-based
   - May not always produce ideal names
   - No user interaction for name selection in automated mode

## Future Enhancements

### High Priority

1. **Improved Pattern Detection**
   - Handle nested if-else chains
   - Detect constants used in multiple methods
   - Support for package-private and private constants

2. **Better Enum Naming**
   - Analyze constant name patterns (e.g., STATUS_*, ERROR_*)
   - Generate more meaningful enum names
   - Detect common prefixes and use them for enum name

3. **Safety Checks**
   - Verify no arithmetic operations on constants
   - Check for external references before transformation
   - Validate that all constant usages can be converted

### Medium Priority

4. **Configuration Options**
   - Minimum number of constants to trigger transformation (default: 2)
   - Allow/disallow transformation of public constants
   - Option to preserve original constants as deprecated

5. **Enhanced Transformations**
   - Support for int constants with associated string values (name/value pairs)
   - Handle constants with bit flags (suggest EnumSet instead)
   - Support for ordinal-based enums when order matters

### Low Priority

6. **IDE Integration**
   - Quick fix for individual if-else chains
   - Preview dialog showing before/after
   - Undo/redo support in IDE

7. **Advanced Features**
   - Support for migrating existing enum-like patterns
   - Generate enum methods (toString, fromValue, etc.)
   - Handle legacy code with minimal disruption

## Testing Requirements

### Test Cases Needed

1. **Basic Transformation**
   - Simple if-else with 2 constants
   - If-else with 3+ constants
   - If-else with else clause

2. **Edge Cases**
   - Constants with no usages (should not transform)
   - Constants used in arithmetic (should not transform)
   - Mixed usage (if-else + arithmetic) (should not transform)

3. **Negative Tests**
   - Single constant (should not trigger)
   - Constants from different classes
   - Switch statement on int (already optimal)

4. **Complex Scenarios**
   - Multiple if-else chains using same constants
   - Nested if-else structures
   - Constants with annotations

## Open Questions

1. Should we transform constants that are part of a public API?
2. How to handle constants with associated data (name-value pairs)?
3. Should we generate equals/hashCode for the enum?
4. How to handle backward compatibility when constants are in public API?

## Performance Considerations

- AST traversal should be efficient for large files
- Pattern matching should be optimized
- Transformation should be atomic (all or nothing)

## Documentation Needs

- User-facing documentation for Eclipse Help
- Example transformations in README.md
- Configuration guide for cleanup preferences
