/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.ast.api.jdt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

import org.sandbox.ast.api.core.ASTWrapper;
import org.sandbox.ast.api.expr.ASTExpr;
import org.sandbox.ast.api.expr.CastExpr;
import org.sandbox.ast.api.expr.FieldAccessExpr;
import org.sandbox.ast.api.expr.InfixExpr;
import org.sandbox.ast.api.expr.InfixOperator;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.info.Modifier;
import org.sandbox.ast.api.info.ParameterInfo;
import org.sandbox.ast.api.info.TypeInfo;
import org.sandbox.ast.api.info.VariableInfo;
import org.sandbox.ast.api.stmt.ASTStmt;
import org.sandbox.ast.api.stmt.EnhancedForStmt;
import org.sandbox.ast.api.stmt.ForLoopStmt;
import org.sandbox.ast.api.stmt.IfStmt;
import org.sandbox.ast.api.stmt.WhileLoopStmt;

/**
 * Bridge between Eclipse JDT AST nodes and sandbox-ast-api fluent types.
 *
 * <p>Converts JDT DOM nodes ({@code org.eclipse.jdt.core.dom.*}) to
 * immutable sandbox-ast-api records, enabling fluent, type-safe AST analysis
 * without direct JDT dependencies in consuming code.</p>
 *
 * <h2>Usage from sandbox plugins:</h2>
 * <pre>
 * // In a JDT cleanup or visitor:
 * MethodInvocation jdtNode = ...;
 * MethodInvocationExpr mi = JDTConverter.convert(jdtNode);
 * if (mi.isMethodCall("add", 1)) {
 *     // use fluent API
 * }
 *
 * // Convert any expression:
 * Expression expr = ...;
 * Optional&lt;ASTExpr&gt; fluentExpr = JDTConverter.convertExpression(expr);
 * fluentExpr.flatMap(ASTExpr::asMethodInvocation)
 *     .filter(m -&gt; m.isStatic())
 *     .ifPresent(m -&gt; { ... });
 * </pre>
 *
 * <h2>Design decisions:</h2>
 * <ul>
 *   <li>All methods are static (stateless utility class)</li>
 *   <li>Null bindings are handled gracefully (Optional.empty())</li>
 *   <li>Unsupported node types return generic wrappers</li>
 *   <li>Conversion is recursive for nested expressions/statements</li>
 * </ul>
 */
public final class JDTConverter {

	private JDTConverter() {
		// utility class
	}

	// -----------------------------------------------------------------------
	// JDT navigation convenience methods
	// -----------------------------------------------------------------------

	/**
	 * Finds the nearest ancestor of the given type. Wraps {@code ASTNodes.getTypedAncestor()}
	 * with Optional for null-safe usage.
	 *
	 * @param node the starting node
	 * @param ancestorType the ancestor class to search for
	 * @return the ancestor if found, otherwise empty
	 */
	public static <T extends ASTNode> Optional<T> ancestor(ASTNode node, Class<T> ancestorType) {
		return Optional.ofNullable(ASTNodes.getTypedAncestor(node, ancestorType));
	}

	/**
	 * Finds the first ancestor of the given type (may skip intermediate ancestors).
	 * Wraps {@code ASTNodes.getFirstAncestorOrNull()} with Optional.
	 *
	 * @param node the starting node
	 * @param ancestorType the ancestor class to search for
	 * @return the ancestor if found, otherwise empty
	 */
	public static <T extends ASTNode> Optional<T> firstAncestor(ASTNode node, Class<T> ancestorType) {
		return Optional.ofNullable(ASTNodes.getFirstAncestorOrNull(node, ancestorType));
	}

	/**
	 * Attempts to cast/unwrap a JDT expression to the given type.
	 * Wraps {@code ASTNodes.as()} with Optional for null-safe usage.
	 *
	 * @param expression the expression to cast
	 * @param type the target type
	 * @return the cast expression if successful, otherwise empty
	 */
	public static <T extends Expression> Optional<T> as(Expression expression, Class<T> type) {
		return Optional.ofNullable(ASTNodes.as(expression, type));
	}

	/**
	 * Attempts to cast/unwrap a JDT statement to the given type.
	 * Wraps {@code ASTNodes.as()} with Optional for null-safe usage.
	 *
	 * @param statement the statement to cast
	 * @param type the target type
	 * @return the cast statement if successful, otherwise empty
	 */
	public static <T extends Statement> Optional<T> as(Statement statement, Class<T> type) {
		return Optional.ofNullable(ASTNodes.as(statement, type));
	}

	/**
	 * Finds an implemented type (interface) on a method invocation's declaring class.
	 * Wraps the common pattern of resolving method binding → declaring class → findImplementedType.
	 *
	 * @param node the method invocation
	 * @param qualifiedTypeName the fully qualified type name to search for (e.g. "java.lang.Iterable")
	 * @return the implemented type binding if found, otherwise empty
	 */
	public static Optional<ITypeBinding> findImplementedType(MethodInvocation node, String qualifiedTypeName) {
		IMethodBinding binding = node.resolveMethodBinding();
		if (binding == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(ASTNodes.findImplementedType(binding.getDeclaringClass(), qualifiedTypeName));
	}

	// -----------------------------------------------------------------------
	// Expression converters
	// -----------------------------------------------------------------------

	/**
	 * Converts any JDT {@link Expression} to the appropriate {@link ASTExpr} wrapper.
	 *
	 * @param expression the JDT expression (may be {@code null})
	 * @return the fluent expression wrapper, or empty if expression is null
	 */
	public static Optional<ASTExpr> convertExpression(Expression expression) {
		if (expression == null) {
			return Optional.empty();
		}
		return Optional.of(convertExpressionNonNull(expression));
	}

	/**
	 * Converts a JDT {@link MethodInvocation} to a {@link MethodInvocationExpr}.
	 *
	 * @param node the JDT method invocation (must not be {@code null})
	 * @return the fluent method invocation wrapper
	 */
	public static MethodInvocationExpr convert(MethodInvocation node) {
		if (node == null) {
			throw new IllegalArgumentException("MethodInvocation must not be null");
		}
		Optional<ASTExpr> receiver = convertExpression(node.getExpression());
		List<Expression> jdtArgs = node.arguments();
		List<ASTExpr> arguments = jdtArgs.stream()
				.map(JDTConverter::convertExpressionNonNull)
				.toList();
		Optional<MethodInfo> method = convertMethodBinding(node.resolveMethodBinding());
		// Fallback: create minimal MethodInfo from node name when binding is unresolved
		if (method.isEmpty()) {
			method = createMinimalMethodInfo(node.getName().getIdentifier(), jdtArgs.size());
		}
		Optional<TypeInfo> type = convertTypeBinding(node.resolveTypeBinding());
		return new MethodInvocationExpr(receiver, arguments, method, type);
	}

	/**
	 * Converts a JDT {@link SimpleName} to a {@link SimpleNameExpr}.
	 *
	 * @param node the JDT simple name (must not be {@code null})
	 * @return the fluent simple name wrapper
	 */
	public static SimpleNameExpr convert(SimpleName node) {
		if (node == null) {
			throw new IllegalArgumentException("SimpleName must not be null");
		}
		String identifier = node.getIdentifier();
		IBinding binding = node.resolveBinding();

		Optional<VariableInfo> variableBinding = Optional.empty();
		Optional<MethodInfo> methodBinding = Optional.empty();
		Optional<TypeInfo> typeBinding = Optional.empty();

		if (binding instanceof IVariableBinding vb) {
			variableBinding = convertVariableBinding(vb);
		} else if (binding instanceof IMethodBinding mb) {
			methodBinding = convertMethodBinding(mb);
		} else if (binding instanceof ITypeBinding tb) {
			typeBinding = convertTypeBinding(tb);
		}

		Optional<TypeInfo> type = convertTypeBinding(node.resolveTypeBinding());
		return new SimpleNameExpr(identifier, variableBinding, methodBinding, typeBinding, type);
	}

	/**
	 * Extracts the identifier if the given expression is a SimpleName.
	 * 
	 * <p>This is a convenience shortcut for the common pattern of checking
	 * if an expression is a SimpleName and getting its identifier string.</p>
	 * 
	 * <p>Equivalent to:
	 * <pre>
	 * JDTConverter.convertExpression(expression)
	 *     .flatMap(ASTExpr::asSimpleName)
	 *     .map(SimpleNameExpr::identifier)
	 * </pre>
	 * </p>
	 *
	 * @param expression the JDT expression to check (may be {@code null})
	 * @return the identifier if expression is a SimpleName, otherwise empty
	 */
	public static Optional<String> identifierOf(Expression expression) {
		if (expression instanceof SimpleName sn) {
			return Optional.of(sn.getIdentifier());
		}
		return Optional.empty();
	}

	/**
	 * Converts a JDT {@link FieldAccess} to a {@link FieldAccessExpr}.
	 *
	 * @param node the JDT field access (must not be {@code null})
	 * @return the fluent field access wrapper
	 */
	public static FieldAccessExpr convert(FieldAccess node) {
		if (node == null) {
			throw new IllegalArgumentException("FieldAccess must not be null");
		}
		ASTExpr receiver = convertExpressionNonNull(node.getExpression());
		String fieldName = node.getName().getIdentifier();
		Optional<VariableInfo> field = Optional.ofNullable(node.resolveFieldBinding())
				.flatMap(JDTConverter::convertVariableBinding);
		Optional<TypeInfo> type = convertTypeBinding(node.resolveTypeBinding());
		return new FieldAccessExpr(receiver, fieldName, field, type);
	}

	/**
	 * Converts a JDT {@link CastExpression} to a {@link CastExpr}.
	 *
	 * @param node the JDT cast expression (must not be {@code null})
	 * @return the fluent cast expression wrapper
	 */
	public static CastExpr convert(CastExpression node) {
		if (node == null) {
			throw new IllegalArgumentException("CastExpression must not be null");
		}
		TypeInfo castType = convertTypeBindingOrUnresolved(node.getType().resolveBinding());
		ASTExpr expression = convertExpressionNonNull(node.getExpression());
		Optional<TypeInfo> type = convertTypeBinding(node.resolveTypeBinding());
		return new CastExpr(castType, expression, type);
	}

	/**
	 * Converts a JDT {@link InfixExpression} to an {@link InfixExpr}.
	 *
	 * @param node the JDT infix expression (must not be {@code null})
	 * @return the fluent infix expression wrapper
	 */
	public static InfixExpr convert(InfixExpression node) {
		if (node == null) {
			throw new IllegalArgumentException("InfixExpression must not be null");
		}
		ASTExpr left = convertExpressionNonNull(node.getLeftOperand());
		ASTExpr right = convertExpressionNonNull(node.getRightOperand());
		InfixOperator operator = convertOperator(node.getOperator());

		List<Expression> jdtExtended = node.extendedOperands();
		List<ASTExpr> extended = jdtExtended.stream()
				.map(JDTConverter::convertExpressionNonNull)
				.toList();

		Optional<TypeInfo> type = convertTypeBinding(node.resolveTypeBinding());
		return new InfixExpr(left, right, extended, operator, type);
	}

	// -----------------------------------------------------------------------
	// Statement converters
	// -----------------------------------------------------------------------

	/**
	 * Converts any JDT {@link Statement} to the appropriate {@link ASTStmt} wrapper.
	 *
	 * @param statement the JDT statement (may be {@code null})
	 * @return the fluent statement wrapper, or empty if statement is null
	 */
	public static Optional<ASTStmt> convertStatement(Statement statement) {
		if (statement == null) {
			return Optional.empty();
		}
		return Optional.of(convertStatementNonNull(statement));
	}

	/**
	 * Converts a JDT {@link EnhancedForStatement} to an {@link EnhancedForStmt}.
	 *
	 * @param node the JDT enhanced for statement (must not be {@code null})
	 * @return the fluent enhanced for wrapper
	 */
	public static EnhancedForStmt convert(EnhancedForStatement node) {
		if (node == null) {
			throw new IllegalArgumentException("EnhancedForStatement must not be null");
		}
		Optional<VariableInfo> parameter = convertSingleVariableDeclaration(node.getParameter());
		Optional<ASTExpr> iterable = convertExpression(node.getExpression());
		Optional<ASTStmt> body = convertStatement(node.getBody());
		return new EnhancedForStmt(parameter, iterable, body);
	}

	/**
	 * Converts a JDT {@link WhileStatement} to a {@link WhileLoopStmt}.
	 *
	 * @param node the JDT while statement (must not be {@code null})
	 * @return the fluent while loop wrapper
	 */
	public static WhileLoopStmt convert(WhileStatement node) {
		if (node == null) {
			throw new IllegalArgumentException("WhileStatement must not be null");
		}
		Optional<ASTExpr> condition = convertExpression(node.getExpression());
		Optional<ASTStmt> body = convertStatement(node.getBody());
		return new WhileLoopStmt(condition, body);
	}

	/**
	 * Converts a JDT {@link ForStatement} to a {@link ForLoopStmt}.
	 *
	 * @param node the JDT for statement (must not be {@code null})
	 * @return the fluent for loop wrapper
	 */
	public static ForLoopStmt convert(ForStatement node) {
		if (node == null) {
			throw new IllegalArgumentException("ForStatement must not be null");
		}
		List<Expression> jdtInits = node.initializers();
		List<ASTExpr> initializers = jdtInits.stream()
				.map(JDTConverter::convertExpressionNonNull)
				.toList();
		Optional<ASTExpr> condition = convertExpression(node.getExpression());
		List<Expression> jdtUpdaters = node.updaters();
		List<ASTExpr> updaters = jdtUpdaters.stream()
				.map(JDTConverter::convertExpressionNonNull)
				.toList();
		Optional<ASTStmt> body = convertStatement(node.getBody());
		return new ForLoopStmt(initializers, condition, updaters, body);
	}

	/**
	 * Converts a JDT {@link IfStatement} to an {@link IfStmt}.
	 *
	 * @param node the JDT if statement (must not be {@code null})
	 * @return the fluent if statement wrapper
	 */
	public static IfStmt convert(IfStatement node) {
		if (node == null) {
			throw new IllegalArgumentException("IfStatement must not be null");
		}
		Optional<ASTExpr> condition = convertExpression(node.getExpression());
		Optional<ASTStmt> thenStmt = convertStatement(node.getThenStatement());
		Optional<ASTStmt> elseStmt = convertStatement(node.getElseStatement());
		return new IfStmt(condition, thenStmt, elseStmt);
	}

	// -----------------------------------------------------------------------
	// Binding converters
	// -----------------------------------------------------------------------

	/**
	 * Converts an {@link ITypeBinding} to a {@link TypeInfo}.
	 *
	 * @param binding the JDT type binding (may be {@code null})
	 * @return the type info, or empty if binding is null
	 */
	public static Optional<TypeInfo> convertTypeBinding(ITypeBinding binding) {
		if (binding == null) {
			return Optional.empty();
		}
		return Optional.of(convertTypeBindingNonNull(binding));
	}

	/**
	 * Converts an {@link IMethodBinding} to a {@link MethodInfo}.
	 *
	 * @param binding the JDT method binding (may be {@code null})
	 * @return the method info, or empty if binding is null
	 */
	public static Optional<MethodInfo> convertMethodBinding(IMethodBinding binding) {
		if (binding == null) {
			return Optional.empty();
		}
		String name = binding.getName();
		TypeInfo declaringType = convertTypeBindingOrUnresolved(binding.getDeclaringClass());
		TypeInfo returnType = convertTypeBindingOrUnresolved(binding.getReturnType());

		ITypeBinding[] paramTypes = binding.getParameterTypes();
		String[] paramNames = parameterNames(paramTypes.length);
		List<ParameterInfo> parameters = new ArrayList<>();
		for (int i = 0; i < paramTypes.length; i++) {
			TypeInfo paramType = convertTypeBindingOrUnresolved(paramTypes[i]);
			parameters.add(ParameterInfo.of(paramNames[i], paramType));
		}

		Set<Modifier> modifiers = Modifier.fromJdtFlags(binding.getModifiers());
		return Optional.of(new MethodInfo(name, declaringType, returnType, parameters, modifiers));
	}

	/**
	 * Converts an {@link IVariableBinding} to a {@link VariableInfo}.
	 *
	 * @param binding the JDT variable binding (may be {@code null})
	 * @return the variable info, or empty if binding is null
	 */
	public static Optional<VariableInfo> convertVariableBinding(IVariableBinding binding) {
		if (binding == null) {
			return Optional.empty();
		}
		String name = binding.getName();
		TypeInfo type = convertTypeBindingOrUnresolved(binding.getType());
		Set<Modifier> modifiers = Modifier.fromJdtFlags(binding.getModifiers());
		boolean isField = binding.isField();
		boolean isParameter = binding.isParameter();
		boolean isRecordComponent = binding.isRecordComponent();
		return Optional.of(new VariableInfo(name, type, modifiers, isField, isParameter, isRecordComponent));
	}

	// -----------------------------------------------------------------------
	// Operator converter
	// -----------------------------------------------------------------------

	/**
	 * Converts a JDT {@link InfixExpression.Operator} to an {@link InfixOperator}.
	 *
	 * @param operator the JDT operator
	 * @return the fluent operator enum value
	 */
	public static InfixOperator convertOperator(InfixExpression.Operator operator) {
		if (operator == null) {
			throw new IllegalArgumentException("Operator must not be null");
		}
		return InfixOperator.fromSymbol(operator.toString())
				.orElseThrow(() -> new IllegalArgumentException(
						"Unsupported infix operator: " + operator));
	}

	// -----------------------------------------------------------------------
	// Generic ASTWrapper converter
	// -----------------------------------------------------------------------

	/**
	 * Converts any JDT AST node to an appropriate {@link ASTWrapper}.
	 * Dispatches to specific converters based on node type.
	 *
	 * @param node the JDT AST node (may be {@code null})
	 * @return the fluent wrapper, or empty if node is null
	 */
	public static Optional<ASTWrapper> convertNode(org.eclipse.jdt.core.dom.ASTNode node) {
		if (node == null) {
			return Optional.empty();
		}
		if (node instanceof Expression expr) {
			return Optional.of(convertExpressionNonNull(expr));
		}
		if (node instanceof Statement stmt) {
			return Optional.of(convertStatementNonNull(stmt));
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------
	// Internal helpers
	// -----------------------------------------------------------------------

	private static ASTExpr convertExpressionNonNull(Expression expression) {
		if (expression instanceof MethodInvocation mi) {
			return convert(mi);
		}
		if (expression instanceof SimpleName sn) {
			return convert(sn);
		}
		if (expression instanceof FieldAccess fa) {
			return convert(fa);
		}
		if (expression instanceof CastExpression ce) {
			return convert(ce);
		}
		if (expression instanceof InfixExpression ie) {
			return convert(ie);
		}
		// Unsupported expression type - wrap generically
		Optional<TypeInfo> type = convertTypeBinding(expression.resolveTypeBinding());
		return new UnsupportedExpr(type);
	}

	private static ASTStmt convertStatementNonNull(Statement statement) {
		if (statement instanceof EnhancedForStatement efs) {
			return convert(efs);
		}
		if (statement instanceof WhileStatement ws) {
			return convert(ws);
		}
		if (statement instanceof ForStatement fs) {
			return convert(fs);
		}
		if (statement instanceof IfStatement is) {
			return convert(is);
		}
		// Unsupported statement type - wrap generically
		return new UnsupportedStmt();
	}

	private static TypeInfo convertTypeBindingNonNull(ITypeBinding binding) {
		TypeInfo.Builder builder = TypeInfo.Builder.of(binding.getQualifiedName())
				.simpleName(binding.getName());
		if (binding.isPrimitive()) {
			builder.primitive();
		}
		if (binding.isArray()) {
			builder.array(binding.getDimensions());
		}
		ITypeBinding[] typeArgs = binding.getTypeArguments();
		if (typeArgs != null) {
			for (ITypeBinding arg : typeArgs) {
				if (arg != null) {
					builder.addTypeArgument(convertTypeBindingNonNull(arg));
				}
			}
		}
		// Add erased qualified name
		ITypeBinding erasure = binding.getErasure();
		if (erasure != null) {
			builder.erasedName(erasure.getQualifiedName());
		}
		return builder.build();
	}

	private static TypeInfo convertTypeBindingOrUnresolved(ITypeBinding binding) {
		if (binding == null) {
			return TypeInfo.Builder.of("<unresolved>")
					.simpleName("<unresolved>")
					.build();
		}
		return convertTypeBindingNonNull(binding);
	}

	private static Optional<VariableInfo> convertSingleVariableDeclaration(
			SingleVariableDeclaration decl) {
		if (decl == null) {
			return Optional.empty();
		}
		String name = decl.getName().getIdentifier();
		IVariableBinding binding = decl.resolveBinding();
		if (binding != null) {
			return convertVariableBinding(binding);
		}
		// Fallback: create VariableInfo from declaration without binding
		ITypeBinding typeBinding = decl.getType().resolveBinding();
		TypeInfo type = convertTypeBindingOrUnresolved(typeBinding);
		Set<Modifier> modifiers = Modifier.fromJdtFlags(decl.getModifiers());
		return Optional.of(new VariableInfo(name, type, modifiers, false, true, false));
	}

	private static String[] parameterNames(int count) {
		String[] names = new String[count];
		for (int i = 0; i < count; i++) {
			names[i] = "arg" + i;
		}
		return names;
	}

	private static final TypeInfo UNRESOLVED_TYPE = TypeInfo.Builder.of("<unresolved>")
			.simpleName("<unresolved>")
			.build();

	/**
	 * Creates a minimal MethodInfo when binding resolution is unavailable.
	 * Preserves the method name and parameter count from the JDT node.
	 */
	private static Optional<MethodInfo> createMinimalMethodInfo(String name, int argCount) {
		List<ParameterInfo> params = new ArrayList<>();
		for (int i = 0; i < argCount; i++) {
			params.add(ParameterInfo.of("arg" + i, UNRESOLVED_TYPE));
		}
		return Optional.of(new MethodInfo(name, null, UNRESOLVED_TYPE, params, Set.of()));
	}

	// -----------------------------------------------------------------------
	// Generic wrapper types for unsupported nodes
	// -----------------------------------------------------------------------

	/**
	 * Wrapper for JDT expression types not yet supported by sandbox-ast-api.
	 * Future phases can add specific converters for these types.
	 */
	record UnsupportedExpr(Optional<TypeInfo> type) implements ASTExpr {
		UnsupportedExpr {
			type = type == null ? Optional.empty() : type;
		}
	}

	/**
	 * Wrapper for JDT statement types not yet supported by sandbox-ast-api.
	 * Future phases can add specific converters for these types.
	 */
	record UnsupportedStmt() implements ASTStmt {
	}
}
