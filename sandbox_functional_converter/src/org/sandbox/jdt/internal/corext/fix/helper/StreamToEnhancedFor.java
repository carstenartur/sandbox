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
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Transformer for converting Stream forEach expressions to enhanced for-loops.
 * 
 * <p>Transformation: {@code collection.forEach(item -> ...)} → {@code for (T item : collection) { ... }}</p>
 * 
 * <p>Uses the ULR pipeline: {@code LoopModelBuilder → LoopModel → ASTEnhancedForRenderer}.</p>
 * 
 * @see LoopModel
 * @see ASTEnhancedForRenderer
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/549">Issue #549</a>
 */
public class StreamToEnhancedFor extends AbstractFunctionalCall<ASTNode> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, Object> dataHolder = ReferenceHolder.create();
		
		// Find forEach method calls
		HelperVisitor.callMethodInvocationVisitor("forEach", compilationUnit, dataHolder, nodesprocessed, //$NON-NLS-1$
			(visited, aholder) -> {
				// Check if this is collection.forEach(...) or collection.stream().forEach(...)
				if (visited.arguments().size() != 1) {
					return false;
				}
				
				Object arg = visited.arguments().get(0);
				if (!(arg instanceof LambdaExpression)) {
					return false; // Only handle simple lambda expressions
				}
				
				// Make sure parent is an ExpressionStatement (standalone forEach call)
				if (!(visited.getParent() instanceof ExpressionStatement)) {
					return false;
				}
				
				// Safety: reject chained stream operations (map/filter/reduce/flatMap/sorted/distinct)
				// Only simple collection.forEach() or collection.stream().forEach() can be converted
				if (StreamOperationDetector.hasChainedStreamOperations(visited)) {
					return false;
				}
				
				operations.add(fixcore.rewrite(visited, new ReferenceHolder<>()));
				nodesprocessed.add(visited);
				return false;
			});
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore useExplicitEncodingFixCore, ASTNode visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> data)
			throws CoreException {
		if (!(visited instanceof MethodInvocation)) {
			return;
		}
		
		MethodInvocation forEach = (MethodInvocation) visited;
		
		// Get the lambda expression
		if (forEach.arguments().isEmpty() || !(forEach.arguments().get(0) instanceof LambdaExpression)) {
			return;
		}
		
		LambdaExpression lambda = (LambdaExpression) forEach.arguments().get(0);
		
		// Extract collection expression (either collection or collection.stream())
		Expression collectionExpr = forEach.getExpression();
		if (collectionExpr instanceof MethodInvocation methodInv) {
			if ("stream".equals(methodInv.getName().getIdentifier())) { //$NON-NLS-1$
				collectionExpr = methodInv.getExpression();
			}
		}
		
		if (collectionExpr == null) {
			return;
		}
		
		AST ast = cuRewrite.getAST();
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		
		// Extract parameter name and type from lambda
		if (lambda.parameters().isEmpty()) {
			return;
		}
		
		VariableDeclaration param = (VariableDeclaration) lambda.parameters().get(0);
		String paramName = param.getName().getIdentifier();
		String paramType = extractParamType(param);
		
		// Build LoopModel using ULR pipeline
		LoopModel model = new LoopModelBuilder()
			.source(SourceDescriptor.SourceType.COLLECTION, collectionExpr.toString(), paramType)
			.element(paramName, paramType, false)
			.terminal(new ForEachTerminal(List.of(), false))
			.build();
		
		// Extract body statements from lambda
		List<Statement> bodyStatements = extractLambdaBodyStatements(lambda, ast);
		
		// Render enhanced for-loop using ULR-based renderer
		ExpressionStatement forEachStmt = (ExpressionStatement) forEach.getParent();
		ASTEnhancedForRenderer renderer = new ASTEnhancedForRenderer(ast, rewrite);
		renderer.renderReplace(model, forEachStmt, bodyStatements, group);
	}

	/**
	 * Extracts the parameter type name from a lambda parameter.
	 */
	private String extractParamType(VariableDeclaration param) {
		if (param instanceof SingleVariableDeclaration svd && svd.getType() != null) {
			return svd.getType().toString();
		}
		return "String"; //$NON-NLS-1$
	}

	/**
	 * Extracts body statements from a lambda expression as AST Statement nodes.
	 */
	private List<Statement> extractLambdaBodyStatements(LambdaExpression lambda, AST ast) {
		List<Statement> statements = new ArrayList<>();
		ASTNode lambdaBody = lambda.getBody();
		
		if (lambdaBody instanceof Block block) {
			for (Object stmt : block.statements()) {
				statements.add((Statement) stmt);
			}
		} else if (lambdaBody instanceof Expression expr) {
			// Expression body: wrap in ExpressionStatement (newly created, not part of original AST)
			ExpressionStatement exprStmt = ast.newExpressionStatement(
				(Expression) ASTNode.copySubtree(ast, expr));
			statements.add(exprStmt);
		}
		return statements;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					for (String item : items) {
						System.out.println(item);
					}
					""";
		}
		return """
				items.forEach(item -> System.out.println(item));
				""";
	}
}
