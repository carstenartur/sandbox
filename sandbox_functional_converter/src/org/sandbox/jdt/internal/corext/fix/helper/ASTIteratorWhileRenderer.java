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

import java.util.List;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.terminal.ForEachTerminal;

/**
 * ULR-based renderer that converts a {@link LoopModel} into an iterator-based while-loop.
 * 
 * <p>This renderer takes the abstract ULR model and produces JDT AST nodes for
 * the iterator-while pattern:</p>
 * <pre>
 * Iterator&lt;T&gt; it = collection.iterator();
 * while (it.hasNext()) {
 *     T item = it.next();
 *     // body statements
 * }
 * </pre>
 * 
 * <p>This class is the iterator-while counterpart to {@link ASTStreamRenderer},
 * enabling the ULR pipeline to target iterator-while loops instead of streams.</p>
 * 
 * @see LoopModel
 * @see ASTStreamRenderer
 */
public class ASTIteratorWhileRenderer {

	private static final String ITERATOR_NAME = "it"; //$NON-NLS-1$

	private final AST ast;
	private final ASTRewrite rewrite;

	public ASTIteratorWhileRenderer(AST ast, ASTRewrite rewrite) {
		this.ast = ast;
		this.rewrite = rewrite;
	}

	/**
	 * Renders the given LoopModel as an iterator-while loop, replacing the original statement.
	 * 
	 * @param model the ULR LoopModel to render
	 * @param originalStatement the original enhanced for-loop to replace
	 * @param originalBody the original loop body (for createCopyTarget comment preservation)
	 * @param group the text edit group
	 */
	public void render(LoopModel model, Statement originalStatement, Statement originalBody, TextEditGroup group) {
		String elementType = model.getElement().typeName();
		String elementName = model.getElement().variableName();
		String collectionExpr = model.getSource().expression();

		// Create Iterator<T> it = collection.iterator();
		VariableDeclarationStatement iteratorDecl = createIteratorDeclaration(elementType, collectionExpr);

		// Create while (it.hasNext()) { T item = it.next(); body... }
		WhileStatement whileStmt = createWhileStatement(elementType, elementName, originalBody);

		// Replace the original for-loop with iterator decl + while
		ASTNode parent = originalStatement.getParent();
		if (parent instanceof Block parentBlock) {
			ListRewrite listRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
			listRewrite.insertBefore(iteratorDecl, originalStatement, group);
			listRewrite.replace(originalStatement, whileStmt, group);
		} else {
			Block newBlock = ast.newBlock();
			newBlock.statements().add(iteratorDecl);
			newBlock.statements().add(whileStmt);
			rewrite.replace(originalStatement, newBlock, group);
		}
	}

	/**
	 * Renders the given LoopModel as an iterator-while loop using string-based body statements.
	 * Used when the original AST body is not available for createCopyTarget.
	 * 
	 * @param model the ULR LoopModel to render
	 * @param originalStatement the original enhanced for-loop to replace
	 * @param group the text edit group
	 */
	public void renderFromModel(LoopModel model, Statement originalStatement, TextEditGroup group) {
		String elementType = model.getElement().typeName();
		String elementName = model.getElement().variableName();
		String collectionExpr = model.getSource().expression();

		// Create Iterator<T> it = collection.iterator();
		VariableDeclarationStatement iteratorDecl = createIteratorDeclaration(elementType, collectionExpr);

		// Create while statement with body from model terminal
		WhileStatement whileStmt = createWhileStatementFromModel(elementType, elementName, model);

		// Replace the original for-loop with iterator decl + while
		ASTNode parent = originalStatement.getParent();
		if (parent instanceof Block parentBlock) {
			ListRewrite listRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
			listRewrite.insertBefore(iteratorDecl, originalStatement, group);
			listRewrite.replace(originalStatement, whileStmt, group);
		} else {
			Block newBlock = ast.newBlock();
			newBlock.statements().add(iteratorDecl);
			newBlock.statements().add(whileStmt);
			rewrite.replace(originalStatement, newBlock, group);
		}
	}

	@SuppressWarnings("unchecked")
	private VariableDeclarationStatement createIteratorDeclaration(String elementType, String collectionExpr) {
		// Iterator<T>
		ParameterizedType iteratorType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Iterator"))); //$NON-NLS-1$
		iteratorType.typeArguments().add(ast.newSimpleType(ast.newName(elementType)));

		// it = collection.iterator()
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(ITERATOR_NAME));

		MethodInvocation iteratorCall = ast.newMethodInvocation();
		iteratorCall.setExpression(createExpression(collectionExpr));
		iteratorCall.setName(ast.newSimpleName("iterator")); //$NON-NLS-1$
		fragment.setInitializer(iteratorCall);

		VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(fragment);
		decl.setType(iteratorType);
		return decl;
	}

	@SuppressWarnings("unchecked")
	private WhileStatement createWhileStatement(String elementType, String elementName, Statement originalBody) {
		// while (it.hasNext())
		MethodInvocation hasNextCall = ast.newMethodInvocation();
		hasNextCall.setExpression(ast.newSimpleName(ITERATOR_NAME));
		hasNextCall.setName(ast.newSimpleName("hasNext")); //$NON-NLS-1$

		WhileStatement whileStmt = ast.newWhileStatement();
		whileStmt.setExpression(hasNextCall);

		// T item = it.next();
		Block whileBody = ast.newBlock();
		whileBody.statements().add(createItemDeclaration(elementType, elementName));

		// Copy original body statements, preserving comments via createCopyTarget
		if (originalBody instanceof Block origBlock) {
			for (Object stmt : origBlock.statements()) {
				whileBody.statements().add(rewrite.createCopyTarget((Statement) stmt));
			}
		} else {
			whileBody.statements().add(rewrite.createCopyTarget(originalBody));
		}

		whileStmt.setBody(whileBody);
		return whileStmt;
	}

	@SuppressWarnings("unchecked")
	private WhileStatement createWhileStatementFromModel(String elementType, String elementName, LoopModel model) {
		// while (it.hasNext())
		MethodInvocation hasNextCall = ast.newMethodInvocation();
		hasNextCall.setExpression(ast.newSimpleName(ITERATOR_NAME));
		hasNextCall.setName(ast.newSimpleName("hasNext")); //$NON-NLS-1$

		WhileStatement whileStmt = ast.newWhileStatement();
		whileStmt.setExpression(hasNextCall);

		// T item = it.next();
		Block whileBody = ast.newBlock();
		whileBody.statements().add(createItemDeclaration(elementType, elementName));

		// Add body from model terminal
		if (model.getTerminal() instanceof ForEachTerminal forEachTerminal) {
			List<String> bodyStatements = forEachTerminal.bodyStatements();
			for (String stmtStr : bodyStatements) {
				String withSemicolon = stmtStr.endsWith(";") ? stmtStr : stmtStr + ";"; //$NON-NLS-1$ //$NON-NLS-2$
				ASTNode parsedStmt = rewrite.createStringPlaceholder(withSemicolon, ASTNode.EXPRESSION_STATEMENT);
				whileBody.statements().add(parsedStmt);
			}
		}

		whileStmt.setBody(whileBody);
		return whileStmt;
	}

	private VariableDeclarationStatement createItemDeclaration(String elementType, String elementName) {
		// T item = it.next();
		MethodInvocation nextCall = ast.newMethodInvocation();
		nextCall.setExpression(ast.newSimpleName(ITERATOR_NAME));
		nextCall.setName(ast.newSimpleName("next")); //$NON-NLS-1$

		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(elementName));
		fragment.setInitializer(nextCall);

		VariableDeclarationStatement decl = ast.newVariableDeclarationStatement(fragment);
		decl.setType(ast.newSimpleType(ast.newName(elementType)));
		return decl;
	}

	/**
	 * Creates an AST Expression from a string expression.
	 */
	private Expression createExpression(String expressionStr) {
		// For simple names, create SimpleName directly
		if (expressionStr.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) { //$NON-NLS-1$
			return ast.newSimpleName(expressionStr);
		}
		// For qualified names (e.g., "this.items"), create QualifiedName
		if (expressionStr.matches("[a-zA-Z_$][a-zA-Z0-9_$.]*")) { //$NON-NLS-1$
			return ast.newName(expressionStr);
		}
		// For complex expressions, use string placeholder
		return (Expression) rewrite.createStringPlaceholder(expressionStr, ASTNode.SIMPLE_NAME);
	}
}
