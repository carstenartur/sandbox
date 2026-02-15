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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.internal.common.NodeMatcher;
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
		final ProspectiveOperation[] result = {null};
		NodeMatcher.on(stmt)
			.ifExpressionStatement(exprStmt -> {
				Expression expr = exprStmt.getExpression();
				NodeMatcher.on(expr)
					.ifMethodInvocation(mi -> result[0] = detectAddMethodPattern(mi, stmt));
			});
		return result[0];
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
		final String[] result = {null};
		NodeMatcher.on(stmt)
			.ifVariableDeclaration(varDecl -> {
				if (varDecl.fragments().size() != 1) {
					return;
				}

				VariableDeclarationFragment fragment = (VariableDeclarationFragment) varDecl.fragments().get(0);
				Expression initializer = fragment.getInitializer();

				// Check if initialized with new ArrayList<>() or new HashSet<>()
				if (!(initializer instanceof ClassInstanceCreation creation)) {
					return;
				}

				// Check that no arguments are passed (empty collection)
				if (!creation.arguments().isEmpty()) {
					return;
				}

				ITypeBinding typeBinding = creation.resolveTypeBinding();
				if (typeBinding == null) {
					return;
				}

				// Check if it's a supported collection type
				String qualifiedName = typeBinding.getErasure().getQualifiedName();
				if (CollectorType.fromCollectionType(qualifiedName) != null) {
					result[0] = fragment.getName().getIdentifier();
				}
			});
		return result[0];
	}

	/**
	 * Extracts the expression being added from a COLLECT operation.
	 * For example, in "result.add(foo(item))", extracts "foo(item)".
	 * 
	 * @param stmt the statement containing the collect operation
	 * @return the expression to be collected, or null if none
	 */
	public Expression extractCollectExpression(Statement stmt) {
		final Expression[] result = {null};
		NodeMatcher.on(stmt)
			.ifExpressionStatement(exprStmt -> {
				NodeMatcher.on(exprStmt.getExpression())
					.ifMethodInvocation(methodInv -> {
						if ("add".equals(methodInv.getName().getIdentifier()) && !methodInv.arguments().isEmpty()) { //$NON-NLS-1$
							result[0] = (Expression) methodInv.arguments().get(0);
						}
					});
			});
		return result[0];
	}

	/**
	 * Checks if the target collection variable is read (not just written to) 
	 * within the loop body. Reading the collection during iteration prevents 
	 * safe conversion to stream collect.
	 * 
	 * <p><b>Example of unsafe read:</b></p>
	 * <pre>{@code
	 * for (Integer item : items) {
	 *     result.add(item);
	 *     System.out.println("Size: " + result.size());  // Read prevents conversion
	 * }
	 * }</pre>
	 * 
	 * @param loopBody the loop body statement
	 * @param collectTargetVar the name of the collection variable being collected to
	 * @return true if the target variable is read during iteration
	 */
	public boolean isTargetReadDuringIteration(Statement loopBody, String collectTargetVar) {
		if (loopBody == null || collectTargetVar == null) {
			return false;
		}
		
		final boolean[] hasRead = {false};
		
		loopBody.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				Expression receiver = node.getExpression();
				if (receiver instanceof SimpleName) {
					String varName = ((SimpleName) receiver).getIdentifier();
					if (varName.equals(collectTargetVar)) {
						String methodName = node.getName().getIdentifier();
						// "add" is a write operation, anything else is a read
						if (!"add".equals(methodName)) {
							hasRead[0] = true;
						}
					}
				}
				return true;
			}
			
			@Override
			public boolean visit(SimpleName node) {
				// Check if this is a direct reference to the target variable
				// that is not part of a method invocation on that variable
				if (node.getIdentifier().equals(collectTargetVar)) {
					// Check if parent is a method invocation where this is the receiver
					ASTNode parent = node.getParent();
					if (parent instanceof MethodInvocation) {
						MethodInvocation parentMethod = (MethodInvocation) parent;
						if (parentMethod.getExpression() == node) {
							// This is the receiver of a method call - handled above
							return true;
						}
					}
					// Direct reference to the variable (e.g., passing it to a method) is a read
					hasRead[0] = true;
				}
				return true;
			}
		});
		
		return hasRead[0];
	}
}