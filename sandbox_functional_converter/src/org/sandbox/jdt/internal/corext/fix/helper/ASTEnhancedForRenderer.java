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

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.model.LoopModel;

/**
 * ULR-based renderer that converts a {@link LoopModel} into an enhanced for-loop.
 * 
 * <p>This renderer takes the abstract ULR model and produces JDT AST nodes for
 * the enhanced for-loop pattern:</p>
 * <pre>
 * for (T item : collection) {
 *     // body statements
 * }
 * </pre>
 * 
 * <p>This class is the enhanced-for counterpart to {@link ASTIteratorWhileRenderer},
 * enabling the ULR pipeline to target enhanced for-loops.</p>
 * 
 * @see LoopModel
 * @see ASTIteratorWhileRenderer
 * @see ASTStreamRenderer
 */
public class ASTEnhancedForRenderer {

	private final AST ast;
	private final ASTRewrite rewrite;

	public ASTEnhancedForRenderer(AST ast, ASTRewrite rewrite) {
		this.ast = ast;
		this.rewrite = rewrite;
	}

	/** Lowers ULR filter/map/forEach operations using their original JDT attachments. */
	public void renderPipeline(LoopModel model, JdtStreamContext context, ImportRewrite imports, TextEditGroup group) {
		new ASTImperativeLoopRenderer(rewrite, imports, group).render(model, context, false);
	}

	/** Preserve the declared element type, modifiers, dimensions and complete body trivia. */
	@SuppressWarnings("unchecked")
	public void renderIteratorLoop(LoopModel model, Statement original, Statement iteratorDeclaration,
			Expression source, VariableDeclarationStatement elementDeclaration, ImportRewrite imports, TextEditGroup group) {
		VariableDeclarationFragment element = (VariableDeclarationFragment) elementDeclaration.fragments().get(0);
		SingleVariableDeclaration parameter = ast.newSingleVariableDeclaration();
		parameter.setName(ast.newSimpleName(model.getElement().variableName()));
		if (elementDeclaration.getType().isVar()) {
			var context = new ContextSensitiveImportRewriteContext((CompilationUnit) original.getRoot(), original.getStartPosition(), imports);
			parameter.setType(imports.addImport(element.resolveBinding().getType(), ast, context));
		} else {
			parameter.setType((Type) rewrite.createCopyTarget(elementDeclaration.getType()));
		}
		for (Object modifier : elementDeclaration.modifiers()) parameter.modifiers().add(rewrite.createCopyTarget((ASTNode) modifier));
		for (Object dimension : element.extraDimensions()) parameter.extraDimensions().add(rewrite.createCopyTarget((Dimension) dimension));
		EnhancedForStatement loop = ast.newEnhancedForStatement();
		loop.setParameter(parameter);
		loop.setExpression((Expression) rewrite.createCopyTarget(source));
		Statement body = original instanceof WhileStatement whileLoop ? whileLoop.getBody() : ((ForStatement) original).getBody();
		elementDeclaration.setProperty(ASTNodes.UNTOUCH_COMMENT, Boolean.TRUE);
		rewrite.remove(elementDeclaration, group);
		loop.setBody((Statement) rewrite.createCopyTarget(body));
		if (iteratorDeclaration != null) {
			iteratorDeclaration.setProperty(ASTNodes.UNTOUCH_COMMENT, Boolean.TRUE);
			rewrite.remove(iteratorDeclaration, group);
		}
		rewrite.replace(original, loop, group);
	}

	/**
	 * Renders the given LoopModel as an enhanced for-loop, replacing the original statement
	 * and removing the iterator declaration.
	 * 
	 * @param model the ULR LoopModel to render
	 * @param whileStatement the original while-loop statement to replace
	 * @param iteratorDecl the iterator declaration statement to remove (may be null)
	 * @param bodyStatements the original body statements to copy (skipping the item = it.next() declaration)
	 * @param group the text edit group
	 */
	@SuppressWarnings("unchecked")
	public void render(LoopModel model, WhileStatement whileStatement, Statement iteratorDecl,
			java.util.List<Statement> bodyStatements, TextEditGroup group) {
		EnhancedForStatement forStmt = buildEnhancedFor(model, bodyStatements);

		// Replace while with for-loop and remove iterator declaration
		Block parentBlock = (Block) whileStatement.getParent();
		ListRewrite listRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
		if (iteratorDecl != null) {
			listRewrite.remove(iteratorDecl, group);
		}
		listRewrite.replace(whileStatement, forStmt, group);
	}

	/**
	 * Renders the given LoopModel as an enhanced for-loop, replacing an ExpressionStatement
	 * (e.g., a forEach call).
	 * 
	 * @param model the ULR LoopModel to render
	 * @param originalStatement the original statement to replace (e.g., ExpressionStatement containing forEach)
	 * @param bodyStatements the body statements to include in the for-loop
	 * @param group the text edit group
	 */
	@SuppressWarnings("unchecked")
	public void renderReplace(LoopModel model, Statement originalStatement,
			java.util.List<Statement> bodyStatements, TextEditGroup group) {
		EnhancedForStatement forStmt = buildEnhancedFor(model, bodyStatements);
		rewrite.replace(originalStatement, forStmt, group);
	}

	@SuppressWarnings("unchecked")
	private EnhancedForStatement buildEnhancedFor(LoopModel model, java.util.List<Statement> bodyStatements) {
		String elementType = model.getElement().typeName();
		String elementName = model.getElement().variableName();
		String collectionExpr = model.getSource().expression();

		// Create the enhanced for-loop parameter: T item
		Type paramType;
		if (elementType != null && !"Object".equals(elementType)) { //$NON-NLS-1$
			paramType = (Type) rewrite.createStringPlaceholder(elementType, ASTNode.SIMPLE_TYPE);
		} else {
			paramType = ast.newSimpleType(ast.newName("Object")); //$NON-NLS-1$
		}

		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setType(paramType);
		param.setName(ast.newSimpleName(elementName));

		// Create enhanced for-loop
		EnhancedForStatement forStmt = ast.newEnhancedForStatement();
		forStmt.setParameter(param);
		forStmt.setExpression(createExpression(collectionExpr));

		// Copy body statements: use createCopyTarget for original AST nodes,
		// add directly for newly created nodes (e.g., from expression lambdas)
		Block forBody = ast.newBlock();
		for (Statement stmt : bodyStatements) {
			if (stmt.getParent() != null) {
				forBody.statements().add(rewrite.createCopyTarget(stmt));
			} else {
				forBody.statements().add(stmt);
			}
		}
		forStmt.setBody(forBody);
		return forStmt;
	}

	private Expression createExpression(String expressionStr) {
		return ExpressionHelper.createExpression(ast, rewrite, expressionStr);
	}
}
