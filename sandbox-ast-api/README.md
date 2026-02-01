# Sandbox AST API

## Overview

The `sandbox-ast-api` module provides a fluent, type-safe wrapper API for Abstract Syntax Tree (AST) operations using Java 21 features. This is a pure Maven module with no Eclipse/JDT runtime dependencies, enabling reuse outside Eclipse context.

## Purpose

Replace verbose, error-prone instanceof checks and deeply nested visitor patterns with a modern, readable fluent API:

```java
// Before: Verbose, error-prone
if (node.getExpression() instanceof SimpleName) {
    SimpleName name = (SimpleName) node.getExpression();
    IBinding binding = name.resolveBinding();
    if (binding instanceof IVariableBinding) {
        IVariableBinding varBinding = (IVariableBinding) binding;
        ITypeBinding typeBinding = varBinding.getType();
        if (typeBinding != null && "java.util.List".equals(typeBinding.getQualifiedName())) {
            // logic
        }
    }
}

// After: Fluent, type-safe
ASTNode.wrap(node)
    .asMethodInvocation()
    .receiver()
    .filter(expr -> expr.isSimpleName())
    .flatMap(ASTExpr::resolveVariable)
    .filter(var -> var.hasType("java.util.List"))
    .ifPresent(var -> { /* logic */ });
```

## Architecture

### Package Structure

- `org.sandbox.ast.api.info` - Immutable info records (no JDT dependencies)
- `org.sandbox.ast.api.core` - Base wrapper interfaces (future)
- `org.sandbox.ast.api.expr` - Expression wrappers (future)
- `org.sandbox.ast.api.stmt` - Statement wrappers (future)

### Core Classes (Phase 1)

#### TypeInfo
Immutable record representing a Java type with fluent query methods:
- `is(Class)` / `is(String)` - Type matching
- `isCollection()`, `isList()`, `isStream()`, `isOptional()` - Category checks
- `isNumeric()` - Numeric type detection
- `boxed()` - Get boxed version of primitive types
- `hasTypeArguments()`, `firstTypeArgument()` - Generic type queries

**Builder Pattern:**
```java
TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
TypeInfo listType = TypeInfo.Builder.of("java.util.List")
    .addTypeArgument(stringType)
    .build();
TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
```

#### MethodInfo
Immutable record representing a method with pattern detection:
- `isMathMax()`, `isMathMin()` - Known method detection
- `isListAdd()`, `isListGet()` - Collection method detection
- `isCollectionStream()` - Stream method detection
- `hasSignature(String name, String... paramTypes)` - Signature matching
- `isStatic()`, `isPublic()`, `isPrivate()`, etc. - Modifier queries

**Builder Pattern:**
```java
MethodInfo method = MethodInfo.Builder.named("substring")
    .returnType(stringType)
    .addParameter(ParameterInfo.of("start", intType))
    .addParameter(ParameterInfo.of("end", intType))
    .build();
```

#### VariableInfo
Immutable record representing a variable (field, local, parameter):
- `hasModifier(Modifier)` - Modifier checks
- `isStatic()`, `isFinal()`, `isPublic()`, `isPrivate()` - Common modifier queries
- `hasType(String)` / `hasType(Class)` - Type matching
- `isField()`, `isParameter()` - Variable kind checks

#### ParameterInfo
Immutable record for method parameters:
- `name()` - Parameter name
- `type()` - Parameter type (TypeInfo)
- `varargs()` - Whether this is a varargs parameter

**Factory Methods:**
```java
ParameterInfo param = ParameterInfo.of("name", type);
ParameterInfo varargs = ParameterInfo.varargs("args", type);
```

#### Modifier
Type-safe enum for Java modifiers replacing JDT bit flags:
- `PUBLIC`, `PRIVATE`, `PROTECTED`, `STATIC`, `FINAL`, etc.
- `fromJdtFlags(int)` - Convert from JDT flags to Set<Modifier>
- `toJdtFlags(Set<Modifier>)` - Convert back to JDT flags
- `isPresentIn(int)` - Check if modifier is in JDT flags

## Design Patterns

### Immutability
All records are immutable with defensive copies of collections in constructors.

### Builder Pattern
All complex records provide Builder classes for fluent construction.

### Validation
Records validate inputs in compact constructors, throwing IllegalArgumentException for invalid data.

## Building

```bash
cd sandbox-ast-api
mvn clean verify
```

## Testing

- 61 unit tests covering all classes
- 88% code coverage (exceeds 80% requirement)
- Uses JUnit 5 + AssertJ

```bash
mvn test
```

## Dependencies

**Compile:** None (pure Java)
**Test:** JUnit 5, AssertJ
**Build:** bnd-maven-plugin (for OSGi bundle generation)

## Integration

This module can be used:
1. As a standalone JAR in any Java 21+ project
2. As an OSGi bundle in Eclipse plugins (via bnd-maven-plugin)
3. As a foundation for future JDT bridge modules

## Performance

See `sandbox-benchmarks` module for performance comparisons between old and new styles. The fluent API has minimal overhead compared to raw operations.

## Future Enhancements

See [TODO.md](TODO.md) for planned features and improvements.
