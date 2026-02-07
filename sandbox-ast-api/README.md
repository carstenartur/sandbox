# Sandbox AST API

## Overview

The `sandbox-ast-api` module provides a fluent, type-safe wrapper API for Abstract Syntax Tree (AST) operations using Java 21 features. This is a pure Maven module with no Eclipse/JDT runtime dependencies, enabling reuse outside Eclipse context.

## Purpose

Replace verbose, error-prone instanceof checks and deeply nested visitor patterns with a modern, readable fluent API:

```java
// Before: Verbose, error-prone (16 lines)
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

// After: Fluent, type-safe (7 lines)
node.asMethodInvocation()
    .flatMap(MethodInvocationExpr::receiver)
    .filter(ASTExpr::isSimpleName)
    .flatMap(e -> e.asSimpleName())
    .flatMap(SimpleNameExpr::resolveVariable)
    .filter(var -> var.hasType("java.util.List"))
    .ifPresent(var -> { /* logic */ });
```

**Key Benefits:**
- **50% less code** - Reduce boilerplate by half
- **Type-safe** - No manual casting, compiler-checked types
- **Composable** - Chain operations using Optional and Stream APIs
- **Testable** - Pure Java records, easy to unit test
- **Zero JDT dependencies** - Reusable outside Eclipse context

## Architecture

### Package Structure

- `org.sandbox.ast.api.info` - Immutable info records (no JDT dependencies)
- `org.sandbox.ast.api.core` - Base wrapper interfaces  
- `org.sandbox.ast.api.expr` - Expression wrappers (NEW in Phase 2)

### Core Classes (Phase 1 - COMPLETED)

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

### Expression Wrappers (Phase 2 - COMPLETED)

#### ASTExpr
Base interface for expression wrappers with type-safe casting methods:
- `asMethodInvocation()`, `asSimpleName()`, `asFieldAccess()`, etc.
- `type()` - Get expression type
- `hasType(String)` / `hasType(Class)` - Type checking
- `isSimpleName()`, `isMethodInvocation()`, `isFieldAccess()` - Type checks

#### MethodInvocationExpr
Immutable record for method invocations:
- `receiver()` - Optional receiver expression
- `arguments()` - List of arguments
- `method()` - Resolved method information
- `isMethodCall(String, int)` - Method name and parameter count check
- `isStatic()`, `isChained()` - Method properties
- `receiverHasType(String)` - Receiver type check

#### SimpleNameExpr
Immutable record for simple names with binding resolution:
- `identifier()` - Name identifier
- `resolveVariable()`, `resolveMethod()`, `resolveType()` - Binding resolution
- `isVariable()`, `isMethod()`, `isType()` - Binding type checks
- `isFinalVariable()`, `isStaticVariable()` - Variable modifier checks
- `isField()`, `isParameter()` - Variable kind checks

#### FieldAccessExpr
Immutable record for field access:
- `receiver()` - Receiver expression  
- `fieldName()` - Field name
- `field()` - Resolved field information
- `isStatic()`, `isFinal()` - Field modifier checks
- `receiverHasType(String)`, `fieldHasType(String)` - Type checks

#### CastExpr
Immutable record for cast expressions:
- `castType()` - Type to cast to
- `expression()` - Expression being cast
- `castsTo(String)` / `castsTo(Class)` - Cast target check
- `expressionHasType(String)` - Cast source type check
- `isDowncast()` - Downcast detection

#### InfixExpr
Immutable record for infix expressions (binary operations):
- `leftOperand()`, `rightOperand()` - Operands
- `operator()` - Infix operator (enum)
- `extendedOperands()` - For chained operations (a + b + c)
- `isArithmetic()`, `isComparison()`, `isLogical()` - Operator category checks
- `isStringConcatenation()`, `isNumeric()` - Operation type checks

#### InfixOperator
Type-safe enum for binary operators:
- `PLUS`, `MINUS`, `TIMES`, `DIVIDE`, etc.
- `EQUALS`, `NOT_EQUALS`, `LESS`, `GREATER`, etc.
- `CONDITIONAL_AND`, `CONDITIONAL_OR`
- `symbol()` - Get operator symbol
- `isArithmetic()`, `isComparison()`, `isLogical()` - Category checks
- `fromSymbol(String)` - Parse operator from string

### Statement Wrappers (Phase 3 - COMPLETED)

#### EnhancedForStmt
Immutable record for enhanced for loops:
- `parameter()` - Loop variable
- `iterable()` - Expression being iterated
- `body()` - Optional loop body statements

#### WhileLoopStmt
Immutable record for while loops:
- `condition()` - Loop condition
- `body()` - Optional loop body statements

#### ForLoopStmt
Immutable record for traditional for loops:
- `initializers()` - List of initialization expressions
- `condition()` - Optional loop condition
- `updaters()` - List of update expressions
- `body()` - Optional loop body statements

#### IfStmt
Immutable record for if statements:
- `condition()` - If condition
- `thenStatement()` - Then branch body
- `elseStatement()` - Optional else branch body

### FluentVisitor (Phase 4 - COMPLETED)

#### FluentVisitor
Type-safe visitor builder for AST traversal without verbose instanceof checks:

**Basic usage:**
```java
FluentVisitor visitor = FluentVisitor.builder()
    .onMethodInvocation(mi -> {
        System.out.println("Found method: " + mi.methodName());
    })
    .onSimpleName(sn -> {
        System.out.println("Found name: " + sn.identifier());
    })
    .build();

visitor.visit(expression);
```

**Conditional handlers with predicates:**
```java
FluentVisitor visitor = FluentVisitor.builder()
    .onMethodInvocation()
        .when(mi -> mi.methodName().equals(Optional.of("add")))
        .when(mi -> mi.argumentCount() > 0)
        .then(mi -> System.out.println("Found add call"))
    .onSimpleName()
        .filter(sn -> sn.identifier().startsWith("get"))
        .then(sn -> System.out.println("Found getter"))
    .build();
```

**Visitor composition:**
```java
FluentVisitor visitor1 = FluentVisitor.builder()
    .onMethodInvocation(mi -> results.add("method"))
    .build();

FluentVisitor visitor2 = FluentVisitor.builder()
    .onIfStatement(is -> results.add("if"))
    .build();

FluentVisitor combined = visitor1.andThen(visitor2);
combined.visitAll(nodes);
```

**Available handlers:**
- **Expression handlers**: `onMethodInvocation()`, `onSimpleName()`, `onFieldAccess()`, `onCast()`, `onInfix()`, `onExpression()`
- **Statement handlers**: `onEnhancedFor()`, `onWhileLoop()`, `onForLoop()`, `onIfStatement()`, `onStatement()`
- **Generic handler**: `onAny()` - matches all node types

**Conditional methods:**
- `when(predicate)` - Add condition that must be satisfied
- `filter(predicate)` - Alias for `when()`
- `then(handler)` - Register handler action when conditions are met

**Visitor methods:**
- `visit(node)` - Visit single node
- `visitAll(nodes)` - Visit multiple nodes
- `andThen(visitor)` - Combine with another visitor

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

- 98 unit tests covering all classes
- 89% code coverage (exceeds 80% requirement)
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

## Real-World Examples

The `org.sandbox.ast.api.examples` package contains practical examples demonstrating the API's value:

**Example 1: Find List.add() calls**
```java
public static List<MethodInvocationExpr> findListAddCalls(List<ASTExpr> expressions) {
    return expressions.stream()
        .flatMap(expr -> expr.asMethodInvocation().stream())
        .filter(mi -> mi.method().map(MethodInfo::isListAdd).orElse(false))
        .toList();
}
```

**Example 2: Find string concatenations**
```java
public static List<InfixExpr> findStringConcatenations(List<ASTExpr> expressions) {
    return expressions.stream()
        .flatMap(expr -> expr.asInfix().stream())
        .filter(InfixExpr::isStringConcatenation)
        .toList();
}
```

**Example 3: Find static method calls**
```java
public static List<MethodInvocationExpr> findStaticMethodCalls(List<ASTExpr> expressions) {
    return expressions.stream()
        .flatMap(expr -> expr.asMethodInvocation().stream())
        .filter(MethodInvocationExpr::isStatic)
        .toList();
}
```

**Example 4: Using FluentVisitor for pattern matching**
```java
List<String> results = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
    .onMethodInvocation()
        .when(mi -> mi.methodName().equals(Optional.of("add")))
        .when(mi -> mi.argumentCount() > 0)
        .then(mi -> results.add("Found List.add() call"))
    .onInfix()
        .filter(InfixExpr::isStringConcatenation)
        .then(ie -> results.add("Found string concatenation"))
    .build();

visitor.visitAll(expressions);
```

**Example 5: Combining multiple visitors**
```java
FluentVisitor methodAnalyzer = FluentVisitor.builder()
    .onMethodInvocation(mi -> System.out.println("Method: " + mi.methodName()))
    .build();

FluentVisitor controlFlowAnalyzer = FluentVisitor.builder()
    .onIfStatement(is -> System.out.println("If statement"))
    .onWhileLoop(wl -> System.out.println("While loop"))
    .build();

FluentVisitor combined = methodAnalyzer.andThen(controlFlowAnalyzer);
combined.visitAll(nodes);
```

See `FluentVisitorExamples.java` for 10+ complete working examples.

## Future Enhancements

See [TODO.md](TODO.md) for planned features and improvements.
