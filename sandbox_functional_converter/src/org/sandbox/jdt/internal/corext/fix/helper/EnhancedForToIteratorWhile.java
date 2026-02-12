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
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Transformer for converting enhanced for-loops to iterator-based while-loops.
 * 
 * <p>Transformation: {@code for (T item : collection) { ... }} → {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</p>
 * 
 * <p>Uses the ULR pipeline: {@code LoopModelBuilder → LoopModel → ASTIteratorWhileRenderer}.</p>
 * 
 * <p><b>Safety rules:</b></p>
 * <ul>
 *   <li>Rejects array sources — arrays don't have .iterator() method</li>
 * </ul>
 * 
 * @see LoopModel
 * @see ASTIteratorWhileRenderer
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/549">Issue #549</a>
 */
public class EnhancedForToIteratorWhile extends AbstractFunctionalCall<ASTNode> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		org.sandbox.jdt.internal.common.HelperVisitor.callEnhancedForStatementVisitor(compilationUnit, 
			new ReferenceHolder<Integer, Object>(), nodesprocessed, (visited, aholder) -> {
				// Safety: reject arrays — arrays don't have .iterator() method
				Expression iterable = visited.getExpression();
				ITypeBinding typeBinding = iterable.resolveTypeBinding();
				if (typeBinding != null && typeBinding.isArray()) {
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
		if (!(visited instanceof EnhancedForStatement)) {
			return;
		}
		
		EnhancedForStatement forStmt = (EnhancedForStatement) visited;
		AST ast = cuRewrite.getAST();
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		
		// Build LoopModel from the enhanced for-loop using ULR pipeline
		LoopModel model = buildLoopModel(forStmt);
		
		// Render iterator-while loop using ULR-based renderer
		ASTIteratorWhileRenderer renderer = new ASTIteratorWhileRenderer(ast, rewrite);
		renderer.render(model, forStmt, forStmt.getBody(), group);
		
		// Add Iterator import
		cuRewrite.getImportRewrite().addImport("java.util.Iterator"); //$NON-NLS-1$
	}

	/**
	 * Builds a LoopModel from an enhanced for-loop using the ULR pipeline.
	 */
	private LoopModel buildLoopModel(EnhancedForStatement forStmt) {
		String paramName = forStmt.getParameter().getName().getIdentifier();
		String paramType = forStmt.getParameter().getType().toString();
		String collectionExpr = forStmt.getExpression().toString();
		
		// Extract body statements as expression strings
		List<String> bodyStatements = extractBodyStatements(forStmt.getBody());
		
		return new LoopModelBuilder()
			.source(SourceDescriptor.SourceType.COLLECTION, collectionExpr, paramType)
			.element(paramName, paramType, false)
			.terminal(new ForEachTerminal(bodyStatements, false))
			.build();
	}

	/**
	 * Extracts body statements as strings from the loop body.
	 */
	private List<String> extractBodyStatements(Statement body) {
		List<String> statements = new ArrayList<>();
		if (body instanceof Block block) {
			for (Object stmt : block.statements()) {
				statements.add(stripTrailingSemicolon(stmt.toString()));
			}
		} else if (body instanceof ExpressionStatement exprStmt) {
			statements.add(stripTrailingSemicolon(exprStmt.toString()));
		} else {
			statements.add(stripTrailingSemicolon(body.toString()));
		}
		return statements;
	}

	private static String stripTrailingSemicolon(String stmtStr) {
		String trimmed = stmtStr.trim();
		if (trimmed.endsWith(";")) { //$NON-NLS-1$
			trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
		}
		return trimmed;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					Iterator<String> it = items.iterator();
					while (it.hasNext()) {
						String item = it.next();
						System.out.println(item);
					}
					""";
		}
		return """
				for (String item : items) {
					System.out.println(item);
				}
				""";
	}
}
