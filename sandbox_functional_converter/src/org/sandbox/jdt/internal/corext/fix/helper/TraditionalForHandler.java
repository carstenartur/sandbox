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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.CollectionThreadSafetyAnalyzer.SafetyLevel;

/**
 * Converts traditional index-based for-loops to functional equivalents.
 *
 * <p>This handler supports two conversion patterns:</p>
 * <ul>
 *   <li><b>IntStream.range():</b> {@code for (int i = 0; i < 10; i++) { println(i); }}
 *       → {@code IntStream.range(0, 10).forEach(i -> println(i))}</li>
 *   <li><b>Index elimination:</b> {@code for (int i = 0; i < items.size(); i++) { String s = items.get(i); println(s); }}
 *       → {@code items.forEach(s -> println(s))}</li>
 * </ul>
 *
 * <p>Index elimination is only applied when:</p>
 * <ol>
 *   <li>The loop variable {@code i} is used <b>only</b> in {@code collection.get(i)} calls</li>
 *   <li>The collection matches the one in the condition ({@code collection.size()})</li>
 *   <li>The collection is thread-safe for conversion (local, immutable, or concurrent-safe)</li>
 * </ol>
 */
public class TraditionalForHandler extends AbstractFunctionalCall<ForStatement> {

	private final CollectionThreadSafetyAnalyzer threadSafetyAnalyzer = new CollectionThreadSafetyAnalyzer();

	/**
	 * Holds the analysis result for a traditional for-loop pattern.
	 */
	static class ForLoopPattern {
		Expression startExpr;
		Expression endExpr;
		String loopVarName;
		boolean indexEliminable;
		Expression collectionExpr;
		String elementVarName;
		/** The statements in the body that should be transformed */
		List<Statement> bodyStatements;
	}

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(ForStatement node) {
				if (nodesprocessed.contains(node)) {
					return false;
				}

				ForLoopPattern pattern = analyzeForLoop(node);
				if (pattern == null) {
					return true;
				}

				nodesprocessed.add(node);

				ReferenceHolder<ASTNode, Object> holder = ReferenceHolder.create();
				holder.put(node, pattern);
				operations.add(fixcore.rewrite(node, holder));

				return false;
			}
		});
	}

	/**
	 * Analyzes a ForStatement to determine if it matches the traditional for-loop pattern.
	 *
	 * @param forStmt the for statement to analyze
	 * @return a ForLoopPattern if the loop matches, or null if it doesn't
	 */
	ForLoopPattern analyzeForLoop(ForStatement forStmt) {
		// Skip for-loops nested inside enhanced-for loops to avoid converting
		// inner loops that the LoopToFunctional handler expects to remain unchanged
		if (isNestedInsideLoop(forStmt)) {
			return null;
		}

		// Step 1: Check initializer - must be a single variable declaration like "int i = start"
		if (forStmt.initializers().size() != 1) {
			return null;
		}
		Object init = forStmt.initializers().get(0);
		if (!(init instanceof VariableDeclarationExpression varDeclExpr)) {
			return null;
		}
		if (varDeclExpr.fragments().size() != 1) {
			return null;
		}
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) varDeclExpr.fragments().get(0);
		String loopVarName = fragment.getName().getIdentifier();
		Expression startExpr = fragment.getInitializer();
		if (startExpr == null) {
			return null;
		}

		// Step 2: Check condition - must be "i < end" or "i <= end"
		Expression condition = forStmt.getExpression();
		if (!(condition instanceof InfixExpression infixExpr)) {
			return null;
		}
		if (infixExpr.getOperator() != InfixExpression.Operator.LESS
				&& infixExpr.getOperator() != InfixExpression.Operator.LESS_EQUALS) {
			return null;
		}
		Expression leftOperand = infixExpr.getLeftOperand();
		if (!(leftOperand instanceof SimpleName leftName) || !leftName.getIdentifier().equals(loopVarName)) {
			return null;
		}
		Expression endExpr = infixExpr.getRightOperand();

		// Step 3: Check updater - must be "i++" or "i += 1" or "++i"
		if (forStmt.updaters().size() != 1) {
			return null;
		}
		if (!isIncrementByOne(forStmt.updaters().get(0), loopVarName)) {
			return null;
		}

		// Step 4: Get body statements
		Statement body = forStmt.getBody();
		List<Statement> bodyStatements = getBodyStatements(body);
		if (bodyStatements == null || bodyStatements.isEmpty()) {
			return null;
		}

		// Step 5: Check for break/continue statements which prevent conversion
		if (containsBreakOrContinue(body)) {
			return null;
		}

		// Build basic pattern
		ForLoopPattern pattern = new ForLoopPattern();
		pattern.startExpr = startExpr;
		pattern.endExpr = endExpr;
		pattern.loopVarName = loopVarName;
		pattern.bodyStatements = bodyStatements;
		pattern.indexEliminable = false;

		// Step 6: Analyze index usage for potential elimination
		analyzeIndexUsage(forStmt, pattern);

		return pattern;
	}

	/**
	 * Analyzes whether the index variable can be eliminated by converting
	 * to {@code collection.forEach()} instead of {@code IntStream.range()}.
	 */
	private void analyzeIndexUsage(ForStatement forStmt, ForLoopPattern pattern) {
		Expression endExpr = pattern.endExpr;

		// Check if the condition right operand is collection.size()
		if (!(endExpr instanceof MethodInvocation sizeCall)) {
			return;
		}
		if (!"size".equals(sizeCall.getName().getIdentifier())) { //$NON-NLS-1$
			return;
		}
		Expression collectionExpr = sizeCall.getExpression();
		if (collectionExpr == null) {
			return;
		}

		// Check start expression is 0
		if (!isZeroLiteral(pattern.startExpr)) {
			return;
		}

		// Collect all references to the loop variable in the body
		String loopVarName = pattern.loopVarName;
		List<SimpleName> loopVarRefs = new ArrayList<>();
		forStmt.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (node.getIdentifier().equals(loopVarName)) {
					loopVarRefs.add(node);
				}
				return true;
			}
		});

		if (loopVarRefs.isEmpty()) {
			return;
		}

		// Check if ALL references to the loop variable are inside collection.get(i) calls
		String collectionName = getExpressionText(collectionExpr);
		for (SimpleName ref : loopVarRefs) {
			if (!isCollectionGetCall(ref, collectionName)) {
				return; // found a non-get(i) usage, cannot eliminate index
			}
		}

		// Thread-safety check: ensure the collection is safe for conversion
		ASTNode enclosingMethod = getEnclosingMethod(forStmt);
		SafetyLevel safetyLevel = threadSafetyAnalyzer.analyze(collectionExpr, enclosingMethod);
		if (!threadSafetyAnalyzer.isSafeForConversion(safetyLevel)) {
			return;
		}

		// All checks passed - verify first body statement is a variable declaration with get(i)
		// This ensures the remaining statements already use the element variable name
		List<Statement> bodyStmts = getBodyStatements(forStmt.getBody());
		if (bodyStmts == null || bodyStmts.isEmpty()) {
			return;
		}
		Statement firstStmt = bodyStmts.get(0);
		if (!(firstStmt instanceof VariableDeclarationStatement varDeclStmt)
				|| varDeclStmt.fragments().size() != 1) {
			return;
		}
		VariableDeclarationFragment frag = (VariableDeclarationFragment) varDeclStmt.fragments().get(0);
		if (!(frag.getInitializer() instanceof MethodInvocation getCall)
				|| !"get".equals(getCall.getName().getIdentifier())) { //$NON-NLS-1$
			return;
		}

		// Index can be eliminated
		pattern.indexEliminable = true;
		pattern.collectionExpr = collectionExpr;
		pattern.elementVarName = frag.getName().getIdentifier();
	}

	/**
	 * Checks if a SimpleName reference to the loop variable is used as
	 * the argument of a {@code collection.get(i)} call.
	 */
	private boolean isCollectionGetCall(SimpleName ref, String collectionName) {
		ASTNode parent = ref.getParent();
		if (!(parent instanceof MethodInvocation methodInv)) {
			return false;
		}
		if (!"get".equals(methodInv.getName().getIdentifier())) { //$NON-NLS-1$
			return false;
		}
		if (methodInv.arguments().size() != 1 || methodInv.arguments().get(0) != ref) {
			return false;
		}
		Expression receiver = methodInv.getExpression();
		if (receiver == null) {
			return false;
		}
		return collectionName.equals(getExpressionText(receiver));
	}

	/**
	 * Derives an element variable name from the loop body or collection name.
	 * If the body starts with {@code Type elem = collection.get(i);}, use that variable name.
	 * Otherwise, derive from the collection name (e.g., "items" → "item").
	 */
	private String deriveElementVarName(ForStatement forStmt, String collectionName) {
		// Check if the first statement is a variable declaration like: Type elem = collection.get(i);
		List<Statement> bodyStmts = getBodyStatements(forStmt.getBody());
		if (bodyStmts != null && !bodyStmts.isEmpty()) {
			Statement firstStmt = bodyStmts.get(0);
			if (firstStmt instanceof VariableDeclarationStatement varDeclStmt) {
				List<?> fragments = varDeclStmt.fragments();
				if (fragments.size() == 1) {
					VariableDeclarationFragment frag = (VariableDeclarationFragment) fragments.get(0);
					Expression initializer = frag.getInitializer();
					if (initializer instanceof MethodInvocation methodInv
							&& "get".equals(methodInv.getName().getIdentifier())) { //$NON-NLS-1$
						return frag.getName().getIdentifier();
					}
				}
			}
		}

		// Derive from collection name: remove trailing 's' if present
		if (collectionName.endsWith("s") && collectionName.length() > 1) { //$NON-NLS-1$
			return collectionName.substring(0, collectionName.length() - 1);
		}
		return "element"; //$NON-NLS-1$
	}

	@SuppressWarnings("unchecked")
	@Override
	public void rewrite(UseFunctionalCallFixCore upp, ForStatement visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> data) throws CoreException {

		Object obj = data.get(visited);
		if (!(obj instanceof ForLoopPattern pattern)) {
			return;
		}

		AST ast = cuRewrite.getRoot().getAST();
		ASTRewrite rewrite = cuRewrite.getASTRewrite();

		if (pattern.indexEliminable) {
			rewriteAsForEach(ast, rewrite, visited, pattern, group);
		} else {
			rewriteAsIntStreamRange(ast, rewrite, cuRewrite, visited, pattern, group);
		}
	}

	/**
	 * Rewrites the loop as {@code collection.forEach(element -> ...)}.
	 */
	@SuppressWarnings("unchecked")
	private void rewriteAsForEach(AST ast, ASTRewrite rewrite, ForStatement visited,
			ForLoopPattern pattern, TextEditGroup group) {

		// collection.forEach(elementVar -> { ... })
		MethodInvocation forEachCall = ast.newMethodInvocation();
		forEachCall.setExpression((Expression) ASTNode.copySubtree(ast, pattern.collectionExpr));
		forEachCall.setName(ast.newSimpleName("forEach")); //$NON-NLS-1$

		// Create lambda: elementVar -> { ... }
		LambdaExpression lambda = ast.newLambdaExpression();
		VariableDeclarationFragment lambdaParam = ast.newVariableDeclarationFragment();
		lambdaParam.setName(ast.newSimpleName(pattern.elementVarName));
		lambda.parameters().add(lambdaParam);
		lambda.setParentheses(false);

		// Build lambda body - transform the original body
		List<Statement> transformedBody = transformBodyForIndexElimination(
				ast, pattern.bodyStatements, pattern.loopVarName,
				getExpressionText(pattern.collectionExpr), pattern.elementVarName);

		if (transformedBody.size() == 1 && transformedBody.get(0) instanceof ExpressionStatement exprStmt) {
			lambda.setBody((Expression) ASTNode.copySubtree(ast, exprStmt.getExpression()));
		} else {
			Block lambdaBlock = ast.newBlock();
			for (Statement stmt : transformedBody) {
				lambdaBlock.statements().add(ASTNode.copySubtree(ast, stmt));
			}
			lambda.setBody(lambdaBlock);
		}

		forEachCall.arguments().add(lambda);
		ExpressionStatement newStmt = ast.newExpressionStatement(forEachCall);
		rewrite.replace(visited, newStmt, group);
	}

	/**
	 * Rewrites the loop as {@code IntStream.range(start, end).forEach(i -> ...)}.
	 */
	@SuppressWarnings("unchecked")
	private void rewriteAsIntStreamRange(AST ast, ASTRewrite rewrite,
			CompilationUnitRewrite cuRewrite, ForStatement visited,
			ForLoopPattern pattern, TextEditGroup group) {

		// IntStream.range(start, end)
		MethodInvocation rangeCall = ast.newMethodInvocation();
		rangeCall.setExpression(ast.newSimpleName("IntStream")); //$NON-NLS-1$
		rangeCall.setName(ast.newSimpleName("range")); //$NON-NLS-1$
		rangeCall.arguments().add(ASTNode.copySubtree(ast, pattern.startExpr));
		rangeCall.arguments().add(ASTNode.copySubtree(ast, pattern.endExpr));

		// .forEach(i -> { ... })
		MethodInvocation forEachCall = ast.newMethodInvocation();
		forEachCall.setExpression(rangeCall);
		forEachCall.setName(ast.newSimpleName("forEach")); //$NON-NLS-1$

		// Lambda: i -> { ... }
		LambdaExpression lambda = ast.newLambdaExpression();
		VariableDeclarationFragment lambdaParam = ast.newVariableDeclarationFragment();
		lambdaParam.setName(ast.newSimpleName(pattern.loopVarName));
		lambda.parameters().add(lambdaParam);
		lambda.setParentheses(false);

		// Lambda body
		if (pattern.bodyStatements.size() == 1
				&& pattern.bodyStatements.get(0) instanceof ExpressionStatement exprStmt) {
			lambda.setBody((Expression) ASTNode.copySubtree(ast, exprStmt.getExpression()));
		} else {
			Block lambdaBlock = ast.newBlock();
			for (Statement stmt : pattern.bodyStatements) {
				lambdaBlock.statements().add(ASTNode.copySubtree(ast, stmt));
			}
			lambda.setBody(lambdaBlock);
		}

		forEachCall.arguments().add(lambda);
		ExpressionStatement newStmt = ast.newExpressionStatement(forEachCall);
		rewrite.replace(visited, newStmt, group);

		// Add IntStream import
		cuRewrite.getImportRewrite().addImport("java.util.stream.IntStream"); //$NON-NLS-1$
	}

	/**
	 * Transforms the body statements for index elimination.
	 * Removes the initial variable declaration {@code Type elem = collection.get(i);} since
	 * the element variable becomes the lambda parameter.
	 * 
	 * <p>Note: Index elimination is only supported when the first body statement is a
	 * variable declaration initialized with {@code collection.get(i)}. This ensures the
	 * remaining statements already reference the element variable name, not the index.</p>
	 */
	private List<Statement> transformBodyForIndexElimination(AST ast,
			List<Statement> originalBody, String loopVarName,
			String collectionName, String elementVarName) {

		List<Statement> result = new ArrayList<>();

		// The first statement must be the element declaration (enforced by analyzeIndexUsage)
		// Skip it since the element variable becomes the lambda parameter
		int startIdx = 1;
		for (int i = startIdx; i < originalBody.size(); i++) {
			result.add(originalBody.get(i));
		}

		return result;
	}

	// ===== Helper methods =====

	/**
	 * Checks if the for-loop is nested inside another loop (enhanced-for, while, for, do-while).
	 * Nested traditional for-loops are skipped to avoid interfering with the LoopToFunctional
	 * handler's analysis of outer enhanced-for loops.
	 */
	private boolean isNestedInsideLoop(ForStatement forStmt) {
		ASTNode parent = forStmt.getParent();
		while (parent != null) {
			if (parent instanceof EnhancedForStatement
					|| parent instanceof ForStatement
					|| parent instanceof WhileStatement
					|| parent instanceof DoStatement) {
				return true;
			}
			if (parent instanceof MethodDeclaration || parent instanceof TypeDeclaration) {
				break;
			}
			parent = parent.getParent();
		}
		return false;
	}

	private boolean isIncrementByOne(Object updater, String varName) {
		if (updater instanceof PostfixExpression postfix) {
			return postfix.getOperator() == PostfixExpression.Operator.INCREMENT
					&& postfix.getOperand() instanceof SimpleName name
					&& name.getIdentifier().equals(varName);
		}
		if (updater instanceof PrefixExpression prefix) {
			return prefix.getOperator() == PrefixExpression.Operator.INCREMENT
					&& prefix.getOperand() instanceof SimpleName name
					&& name.getIdentifier().equals(varName);
		}
		if (updater instanceof Assignment assignment) {
			if (assignment.getOperator() == Assignment.Operator.PLUS_ASSIGN
					&& assignment.getLeftHandSide() instanceof SimpleName name
					&& name.getIdentifier().equals(varName)
					&& isOneLiteral(assignment.getRightHandSide())) {
				return true;
			}
		}
		return false;
	}

	private boolean isZeroLiteral(Expression expr) {
		if (expr instanceof NumberLiteral numLiteral) {
			return "0".equals(numLiteral.getToken()); //$NON-NLS-1$
		}
		return false;
	}

	private boolean isOneLiteral(Expression expr) {
		if (expr instanceof NumberLiteral numLiteral) {
			return "1".equals(numLiteral.getToken()); //$NON-NLS-1$
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private List<Statement> getBodyStatements(Statement body) {
		if (body instanceof Block block) {
			return block.statements();
		}
		List<Statement> list = new ArrayList<>();
		list.add(body);
		return list;
	}

	private boolean containsBreakOrContinue(Statement body) {
		boolean[] found = { false };
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(BreakStatement node) {
				found[0] = true;
				return false;
			}

			@Override
			public boolean visit(ContinueStatement node) {
				found[0] = true;
				return false;
			}

			// Don't descend into nested loops/switches
			@Override
			public boolean visit(ForStatement node) {
				return false;
			}

			@Override
			public boolean visit(EnhancedForStatement node) {
				return false;
			}

			@Override
			public boolean visit(WhileStatement node) {
				return false;
			}

			@Override
			public boolean visit(DoStatement node) {
				return false;
			}

			@Override
			public boolean visit(SwitchStatement node) {
				return false;
			}
		});
		return found[0];
	}

	private String getExpressionText(Expression expr) {
		if (expr instanceof SimpleName simpleName) {
			return simpleName.getIdentifier();
		}
		return expr.toString();
	}

	private ASTNode getEnclosingMethod(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodDeclaration || current instanceof Initializer) {
				return current;
			}
			current = current.getParent();
		}
		return node.getRoot();
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "IntStream.range(0, 10).forEach(i -> System.out.println(i));\n"; //$NON-NLS-1$
		}
		return "for (int i = 0; i < 10; i++)\n    System.out.println(i);\n"; //$NON-NLS-1$
	}
}
