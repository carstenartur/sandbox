# HelperVisitor API Test Suite

> **📍 This documentation has been moved!**  
> Please see [TESTING.md](../../../../../TESTING.md) in the module root for the current documentation.
>
> This file is kept for backward compatibility and will be removed in a future version.

---

This directory contains comprehensive tests for the HelperVisitor API, organized by functionality and use case. The test suite serves dual purposes: validating the API functionality and documenting its usage patterns.

## Test Organization

### Documentation & Learning Path

For developers new to the HelperVisitor API, we recommend exploring the tests in this order:

1. **[VisitorApiDocumentationTest](VisitorApiDocumentationTest.java)** - Start here!
   - Complete API overview with executable examples
   - Covers all major patterns and use cases
   - Includes comparison tables with traditional ASTVisitor
   - Best as a reference guide

2. **[BasicVisitorUsageTest](BasicVisitorUsageTest.java)** - Essential patterns
   - Basic visitor registration and usage
   - Method references vs lambda expressions
   - Filtering by method name
   - Visit and visitEnd callbacks
   - Traditional vs modern approach comparison

3. **[ReferenceHolderTest](ReferenceHolderTest.java)** - Data collection patterns
   - Counting nodes
   - Collecting node information
   - Complex data structures per node
   - Shared data between callbacks
   - Thread-safe data sharing

4. **[ASTProcessorTest](ASTProcessorTest.java)** - Fluent API patterns
   - Method chaining
   - Hierarchical navigation
   - Filtering by receiver type
   - Ancestor navigation
   - Finding patterns across AST levels

5. **[AdvancedVisitorPatternsTest](AdvancedVisitorPatternsTest.java)** - Advanced techniques
   - Combining visitors with logical operators (AND, OR, NOT)
   - Dynamic visitor modification
   - Multi-phase processing
   - Stateful visitors
   - Performance optimization patterns

6. **[VisitorTest](VisitorTest.java)** - Integration tests
   - Complex real-world scenarios
   - Multi-component interactions
   - End-to-end patterns

### Test Class Purposes

#### VisitorApiDocumentationTest
**Purpose**: Comprehensive API documentation through executable code
**Contains**:
- API overview and component descriptions
- Complete usage patterns from basic to advanced
- Callback signature explanations
- Static helper method documentation
- Comparison table: Traditional ASTVisitor vs HelperVisitor

**Use this when**: You need to understand the full API surface or look up specific patterns.

#### BasicVisitorUsageTest
**Purpose**: Fundamental visitor patterns and basic API usage
**Contains**:
- Simple method reference usage
- Lambda expression patterns
- Method name filtering
- Multiple node type visitors
- Visit/endVisit callback patterns
- Direct comparisons with traditional ASTVisitor

**Use this when**: You're starting with the API and need to understand core concepts.

#### ReferenceHolderTest
**Purpose**: Data collection and sharing patterns during AST traversal
**Contains**:
- Node counting and statistics
- Position tracking
- Complex per-node data structures
- Shared state between callbacks
- Lazy initialization patterns

**Use this when**: You need to collect information during traversal or share data between callbacks.

#### ASTProcessorTest
**Purpose**: Fluent API patterns for building complex visitor chains
**Contains**:
- Method chaining syntax
- Navigation functions for hierarchical search
- Type-based filtering
- Ancestor navigation
- Iterator pattern analysis (real-world example)

**Use this when**: You need to find patterns across multiple AST levels or build complex search logic.

#### AdvancedVisitorPatternsTest
**Purpose**: Sophisticated patterns and dynamic behavior
**Contains**:
- BiPredicate composition (or, and, negate)
- Dynamic visitor registration/removal
- Multi-phase coordinated processing
- Stateful adaptive visitors
- Performance optimization techniques

**Use this when**: You need advanced control over visitor behavior or are implementing complex analyses.

#### VisitorTest
**Purpose**: Integration tests validating complex multi-component scenarios
**Contains**:
- Real-world refactoring patterns
- Iterator-to-stream conversion analysis
- Method invocation filtering by type
- Complex data collection scenarios
- Multi-level hierarchical searches

**Use this when**: You need examples of complete end-to-end patterns or are implementing similar refactoring tools.

## Core Concepts

### HelperVisitor
A builder for creating AST visitors using lambda expressions and method references instead of anonymous classes.

**Key Benefits**:
- More concise syntax
- Type-safe method references
- Composable with BiPredicate operators
- Built-in filtering capabilities
- Easier to test and maintain

### ReferenceHolder
A thread-safe map (extends ConcurrentHashMap) for sharing data between visitor callbacks.

**Common Patterns**:
- `holder.merge(key, 1, Integer::sum)` - Increment counters
- `holder.put(node, data)` - Store node-specific data
- `holder.computeIfAbsent(key, k -> new HashMap<>())` - Lazy initialization
- `holder.getOrDefault(key, defaultValue)` - Safe retrieval

### ASTProcessor
Provides a fluent API for chaining visitor operations with navigation functions.

**Fluent Pattern**:
```java
new ASTProcessor<>(dataHolder, null)
    .callVisitor1(callback1, navigationFunction1)
    .callVisitor2(callback2, navigationFunction2)
    .build(compilationUnit);
```

### VisitorEnum
Enum representing all AST node types for type-safe visitor registration.

**Usage**:
```java
VisitorEnum.stream().forEach(ve -> hv.add(ve, callback));
EnumSet.of(VisitorEnum.MethodInvocation, VisitorEnum.FieldDeclaration)
```

## Running Tests

### All Tests
```bash
mvn test -pl sandbox_common_test
```

### Specific Test Class
```bash
mvn test -Dtest=BasicVisitorUsageTest -pl sandbox_common_test
```

### With xvfb (required on Linux CI)
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_common_test
```

## Code Examples

### Example 1: Count Method Invocations
```java
ReferenceHolder<String, Integer> data = new ReferenceHolder<>();
HelperVisitor<...> hv = new HelperVisitor<>(null, data);
hv.addMethodInvocation((node, holder) -> {
    holder.merge("count", 1, Integer::sum);
    return true;
});
hv.build(compilationUnit);
System.out.println("Found " + data.get("count") + " method invocations");
```

### Example 2: Filter by Method Name
```java
hv.addMethodInvocation("println", (node, holder) -> {
    System.out.println("Found println: " + node);
    return true;
});
```

### Example 3: Hierarchical Search
```java
new ASTProcessor<>(data, null)
    .callVariableDeclarationStatementVisitor(Iterator.class,
        (node, holder) -> { /* process */ return true; },
        ASTNode::getParent)  // Navigate to parent
    .callWhileStatementVisitor(
        (node, holder) -> { /* process */ return true; },
        s -> ((WhileStatement)s).getBody())  // Navigate to body
    .build(compilationUnit);
```

### Example 4: Combine Visitors
```java
BiPredicate<MethodInvocation, ...> filter = (node, holder) -> 
    node.getName().startsWith("get");
BiPredicate<MethodInvocation, ...> processor = (node, holder) -> {
    System.out.println("Processing: " + node);
    return true;
};
hv.addMethodInvocation(filter.and(processor));
```

## Support Classes

### ExpectationTracer
Helper class for testing that tracks visitor execution order and state.

### NodeFound
Marker interface/class used in some test scenarios.

### MatcherTest
Tests for pattern matching utilities (separate from visitor tests).

## Contributing

When adding new tests:
1. Choose the appropriate test class based on the pattern being tested
2. Add comprehensive JavaDoc explaining the pattern and use case
3. Include both positive and negative test cases where appropriate
4. Provide comparison with traditional approach when helpful
5. Update this README if adding new test classes or major patterns

## Related Documentation

- [sandbox_common/ARCHITECTURE.md](../../../../../../../sandbox_common/ARCHITECTURE.md) - Architecture overview
- [sandbox_common/TODO.md](../../../../../../../sandbox_common/TODO.md) - Future enhancements
- [sandbox_common/TRIGGERPATTERN.md](../../../../../../../sandbox_common/TRIGGERPATTERN.md) - Pattern matching engine

## Additional Resources

### Eclipse JDT References
- [ASTVisitor](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTVisitor.html) - Traditional visitor pattern
- [AST](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/AST.html) - Abstract Syntax Tree
- [ASTNode](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTNode.html) - Base class for all AST nodes

### Functional Programming in Java
- [BiPredicate](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/BiPredicate.html) - Two-argument predicate
- [BiConsumer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/BiConsumer.html) - Two-argument consumer
- [Method References](https://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html) - Java method reference syntax
