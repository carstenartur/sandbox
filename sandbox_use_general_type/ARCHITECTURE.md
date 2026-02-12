# sandbox_use_general_type - Architecture

## Overview

This plugin implements a cleanup that widens variable declaration types to more general supertypes or interfaces based on actual usage. The core idea is: **if a variable is only used through methods/fields available in a supertype, the declaration type can be generalized**.

## Design Principles

### 1. **Usage-Based Type Analysis**
The plugin analyzes all usages of each local variable to determine which methods and fields are actually accessed. It then walks the type hierarchy to find the most general type (highest in the hierarchy) that still declares all those members.

### 2. **Safety-First Approach**
The transformation is ONLY applied when it's provably safe:
- **Skip if**: variable is declared with `var`, is primitive, is a field/parameter
- **Skip if**: variable is cast to a specific subtype
- **Skip if**: variable is used with `instanceof`
- **Skip if**: Any usage pattern suggests the specific type is required

### 3. **Type Hierarchy Walking**
For each variable, the plugin:
1. Collects all method calls where the variable is the receiver
2. Collects all field accesses on the variable
3. Walks up the superclass chain and interface hierarchy
4. Finds the highest type that declares all required members

## Implementation Structure

### Core Components

#### 1. `UseGeneralTypePlugin.java`
The main transformation logic. Contains:
- `TypeWidenHolder` - Data structure holding variable info and analysis results
- `find()` - Visitor that identifies candidate variables
- `analyzeVariableUsage()` - Analyzes variable references
- `findMostGeneralType()` - Walks type hierarchy
- `declaresAllMembers()` - Checks if a type provides required members
- `rewrite()` - Applies the type change via AST rewrite

#### 2. `UseGeneralTypeFixCore.java`
Enum with cleanup instances. Follows the standard sandbox pattern where each enum value represents a transformation variant. Currently contains only `USE_GENERAL_TYPE`.

#### 3. `UseGeneralTypeCleanUpCore.java`
Main cleanup class. Integrates with Eclipse's cleanup framework:
- Implements `AbstractCleanUp`
- Checks if cleanup is enabled
- Calls `findOperations()` on each enum instance
- Returns `CompilationUnitRewriteOperationsFixCore` with all transformations

#### 4. `UseGeneralTypeCleanUp.java`
UI wrapper. Extends `AbstractCleanUpCoreWrapper` to connect the core logic to the Eclipse UI.

### UI and Preferences

#### 1. `SandboxCodeTabPage.java`
Preferences UI page. Creates checkbox for enabling/disabling the cleanup.

#### 2. `DefaultCleanUpOptionsInitializer.java` & `SaveActionCleanUpOptionsInitializer.java`
Initialize cleanup options to `FALSE` by default.

#### 3. Message Bundles
- `MultiFixMessages.properties` - User-visible refactoring messages
- `CleanUpMessages.properties` - Preferences UI labels

## Algorithm Details

### Variable Analysis Flow

```
For each VariableDeclarationStatement:
  1. Check if type is explicit (not var), non-primitive, non-array
  2. For each fragment (variable), check if it's a local variable
  3. Find all SimpleName nodes that reference this variable
  4. For each reference:
     - If receiver of MethodInvocation: collect method name
     - If FieldAccess expression: collect field name
     - If CastExpression operand: mark unsafe, abort
     - If InstanceofExpression operand: mark unsafe, abort
  5. If no unsafe patterns found:
     - Walk type hierarchy (superclass + interfaces)
     - Find highest type that declares all collected members
     - If found type != current type: propose rewrite
```

### Type Hierarchy Walking

The `findMostGeneralType()` method uses a recursive strategy:
1. Start with current type
2. Check superclass: if it declares all members, recurse on it
3. Check interfaces: prefer interfaces over classes (more general)
4. Return the most general type found

### Member Declaration Check

`declaresAllMembers()` checks:
- Methods declared in the type
- Methods inherited from superclass
- Methods inherited from interfaces
- Fields declared in the type (fields from supertypes checked separately)

## Integration with Eclipse JDT

### AST Rewrite
Uses `ASTRewrite` and `ImportRewrite` to:
- Replace the Type node in VariableDeclarationStatement
- Add necessary imports for the new type
- Preserve type arguments (generics)

### TightSourceRangeComputer
Ensures minimal diff by computing tight source ranges for changed nodes.

## Limitations and Future Work

### Current Limitations
1. **Method Signature Analysis**: Currently only checks method names, not full signatures (parameter types, return type)
2. **Single Variable per Statement**: If multiple variables in one statement, might not handle optimally
3. **Assignment Constraints**: Not yet checking if variable is assigned to a more specific typed variable
4. **Method Parameter Constraints**: Not yet checking parameter type requirements of methods variable is passed to
5. **Return Type Constraints**: Not yet checking if variable is returned from a method with specific return type

### Planned Enhancements (see TODO.md)
- Full method signature matching
- Assignment target type analysis
- Method parameter type constraint checking
- Return type constraint checking
- Support for method chaining patterns
- Support for lambda expressions and method references

## Design Rationale

### Why Not Use TriggerPattern Framework?
Initially considered, but variable declaration type widening doesn't fit the pattern-matching model well because:
- The transformation depends on **global analysis** of all variable usages
- The "pattern" is really "any variable declaration where usage is more general than declaration"
- The analysis requires walking the entire compilation unit to collect all references

### Why Separate Plugin Class?
Follows the existing sandbox architecture where:
- `*FixCore` enum provides the public API
- `*Plugin` class contains the actual transformation logic
- This separation allows multiple transformation variants in the future

### Why Direct AST Traversal?
- Type binding analysis requires full AST with resolved bindings
- Need to correlate variable declarations with all their usages
- Type hierarchy walking requires `ITypeBinding` which is only available in full AST

## Testing Strategy

Test cases should cover:
1. **Positive cases**: ArrayList → List, LinkedHashMap → Map, FileInputStream → InputStream
2. **Negative cases**: Specific methods (LinkedList.addFirst), casts, instanceof
3. **Edge cases**: var, primitives, arrays, fields, parameters
4. **Generics**: Type arguments preserved correctly
5. **Imports**: New imports added correctly

## References

- Eclipse JDT AbstractCleanUp API
- JDT ITypeBinding and type hierarchy
- Existing sandbox plugins (jface, junit, encoding) for architecture patterns
