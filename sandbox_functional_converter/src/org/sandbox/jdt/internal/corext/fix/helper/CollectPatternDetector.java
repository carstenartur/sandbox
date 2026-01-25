/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.sandbox.jdt.internal.corext.util.VariableResolver;

/**
 * Detects and handles COLLECT patterns in loop statements.
 * 
 * <p>This class is responsible for identifying collection accumulation patterns
 * that can be converted to stream collect operations:</p>
 * 
 * <ul>
 * <li><b>List.add():</b> {@code result.add(item)}</li>
 * <li><b>Set.add():</b> {@code set.add(item)}</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * CollectPatternDetector detector = new CollectPatternDetector(forLoop);
 * ProspectiveOperation collectOp = detector.detectCollectOperation(stmt);
 * if (collectOp != null) {
 *     String targetVar = detector.getTargetVariable();
 *     CollectorType collectorType = detector.getCollectorType();
 *     // ... use in stream pipeline
 * }
 * }</pre>
 * 
 * @see ProspectiveOperation
 * @see CollectorType
 * @see StreamPipelineBuilder
 */
public final class CollectPatternDetector {

	private final ASTNode contextNode;
	private String targetVariable = null;
	private CollectorType collectorType = null;

	/**
	 * Creates a new CollectPatternDetector.
	 * 
	 * @param contextNode the context node (typically the for-loop) for type resolution
	 * @throws IllegalArgumentException if contextNode is null
	 */
	public CollectPatternDetector(ASTNode contextNode) {
		if (contextNode == null) {
			throw new IllegalArgumentException("contextNode cannot be null");
		}
		this.contextNode = contextNode;
	}

	/**
	 * Returns the target collection variable name detected during the last
	 * {@link #detectCollectOperation(Statement)} call.
	 * 
	 * @return the target variable name, or null if no collect was detected
	 */
	public String getTargetVariable() {
		return targetVariable;
	}

	/**
	 * Returns the collector type detected during the last
	 * {@link #detectCollectOperation(Statement)} call.
	 * 
	 * @return the collector type (TO_LIST or TO_SET), or null if not detected
	 */
	public CollectorType getCollectorType() {
		return collectorType;
	}

	/**
	 * Detects if a statement contains a COLLECT pattern.
	 * 
	 * <p><b>Supported Patterns:</b></p>
	 * <ul>
	 * <li>Collection add: {@code result.add(item)}, {@code set.add(value)}</li>
	 * </ul>
	 * 
	 * <p><b>Examples:</b></p>
	 * <pre>{@code
	 * // TO_LIST pattern
	 * result.add(item);  // → .collect(Collectors.toList())
	 * 
	 * // TO_SET pattern
	 * set.add(value);  // → .collect(Collectors.toSet())
	 * }</pre>
	 * 
	 * @param stmt the statement to check
	 * @return a COLLECT operation if detected, null otherwise
	 */
	public ProspectiveOperation detectCollectOperation(Statement stmt) {
		if (!(stmt instanceof ExpressionStatement)) {
			return null;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Expression expr = exprStmt.getExpression();

		// Check for method invocation: result.add(item)
		if (expr instanceof MethodInvocation) {
			return detectAddMethodPattern((MethodInvocation) expr, stmt);
		}

		return null;
	}

	/**
	 * Detects collection.add() patterns.
	 * Pattern: result.add(item)
	 */
	private ProspectiveOperation detectAddMethodPattern(MethodInvocation methodInv, Statement stmt) {
		// Check if method name is "add"
		if (!"add".equals(methodInv.getName().getIdentifier())) {
			return null;
		}

		// Check if invoked on a SimpleName (the collection variable)
		Expression receiver = methodInv.getExpression();
		if (!(receiver instanceof SimpleName)) {
			return null;
		}

		String varName = ((SimpleName) receiver).getIdentifier();

		// Resolve the type of the collection variable
		ITypeBinding varType = VariableResolver.getTypeBinding(contextNode, varName);
		if (varType == null) {
			return null;
		}

		// Determine the collector type based on the collection type
		CollectorType type = CollectorType.fromCollectionType(varType.getErasure().getQualifiedName());
		if (type == null) {
			return null;
		}

		// Extract the expression being added (the argument to add())
		if (methodInv.arguments().isEmpty()) {
			return null;
		}

		Expression addedExpr = (Expression) methodInv.arguments().get(0);

		targetVariable = varName;
		collectorType = type;

		// Create a COLLECT operation with the added expression
		ProspectiveOperation op = new ProspectiveOperation(addedExpr, OperationType.COLLECT, null);
		op.setCollectorType(type);
		op.setTargetVariable(varName);

		return op;
	}

	/**
	 * Checks if a statement declares and initializes an empty collection.
	 * Pattern: List<T> result = new ArrayList<>();
	 * 
	 * @param stmt the statement to check
	 * @return the variable name if it's an empty collection initialization, null otherwise
	 */
	public static String isEmptyCollectionDeclaration(Statement stmt) {
		if (!(stmt instanceof VariableDeclarationStatement)) {
			return null;
		}

		VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
		if (varDecl.fragments().size() != 1) {
			return null;
		}

		VariableDeclarationFragment fragment = (VariableDeclarationFragment) varDecl.fragments().get(0);
		Expression initializer = fragment.getInitializer();

		// Check if initialized with new ArrayList<>() or new HashSet<>()
		if (!(initializer instanceof ClassInstanceCreation)) {
			return null;
		}

		ClassInstanceCreation creation = (ClassInstanceCreation) initializer;
		
		// Check that no arguments are passed (empty collection)
		if (!creation.arguments().isEmpty()) {
			return null;
		}

		ITypeBinding typeBinding = creation.resolveTypeBinding();
		if (typeBinding == null) {
			return null;
		}

		// Check if it's a supported collection type
		String qualifiedName = typeBinding.getErasure().getQualifiedName();
		if (CollectorType.fromCollectionType(qualifiedName) != null) {
			return fragment.getName().getIdentifier();
		}

		return null;
	}

	/**
	 * Extracts the expression being added from a COLLECT operation.
	 * For example, in "result.add(foo(item))", extracts "foo(item)".
	 * 
	 * @param stmt the statement containing the collect operation
	 * @return the expression to be collected, or null if none
	 */
	public Expression extractCollectExpression(Statement stmt) {
		if (!(stmt instanceof ExpressionStatement)) {
			return null;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Expression expr = exprStmt.getExpression();

		if (expr instanceof MethodInvocation) {
			MethodInvocation methodInv = (MethodInvocation) expr;
			if ("add".equals(methodInv.getName().getIdentifier()) && !methodInv.arguments().isEmpty()) {
				return (Expression) methodInv.arguments().get(0);
			}
		}

		return null;
	}
}
