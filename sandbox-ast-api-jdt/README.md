# Fluent AST API — JDT Bridge (`sandbox-ast-api-jdt`)

> **Navigation**: [Main README](../README.md) | [sandbox-ast-api README](../sandbox-ast-api/README.md)

## Overview

`sandbox-ast-api-jdt` is the bridge module between Eclipse JDT AST nodes (`org.eclipse.jdt.core.dom.*`) and the `sandbox-ast-api` fluent wrapper types. It allows sandbox plugins and tools to use the type-safe fluent API without changing their existing JDT-based infrastructure.

## Key Components

### `JDTConverter`

Converts JDT DOM nodes to fluent `sandbox-ast-api` wrapper types:

| JDT Type | Fluent Type |
|----------|-------------|
| `Expression` | `ASTExpr` |
| `InfixExpression` | `InfixExpr` |
| `MethodInvocation` | `MethodInvocationExpr` |
| `SimpleName` | `SimpleNameExpr` |
| `FieldAccess` | `FieldAccessExpr` |
| `CastExpression` | `CastExpr` |
| `IfStatement` | `IfStmt` |
| `ForStatement` | `ForLoopStmt` |
| `EnhancedForStatement` | `EnhancedForStmt` |
| `WhileStatement` | `WhileLoopStmt` |
| `IMethodBinding` | `MethodInfo` |
| `ITypeBinding` | `TypeInfo` |
| `IVariableBinding` | `VariableInfo` |

### `FluentASTVisitor`

JDT `ASTVisitor` subclass that integrates with the fluent API, allowing typed callbacks for common AST node types.

## Usage

```java
import org.sandbox.ast.api.jdt.JDTConverter;
import org.sandbox.ast.api.expr.InfixExpr;

// Convert a JDT InfixExpression to a fluent wrapper
InfixExpression jdtNode = ...; // from JDT visitor
Optional<InfixExpr> fluent = JDTConverter.convertExpression(jdtNode)
    .filter(e -> e instanceof InfixExpr)
    .map(e -> (InfixExpr) e);
```

## Dependencies

- `sandbox-ast-api` — the fluent wrapper type definitions
- `org.eclipse.jdt.core` — Eclipse JDT DOM types

## Related Modules

- **[sandbox-ast-api](../sandbox-ast-api/README.md)** — fluent wrapper API (no Eclipse runtime dependency)
- **[sandbox_functional_converter](../sandbox_functional_converter/README.md)** — uses fluent API via this bridge
