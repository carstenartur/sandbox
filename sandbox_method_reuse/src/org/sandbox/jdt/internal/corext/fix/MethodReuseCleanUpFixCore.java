/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.InlineCodeSequenceFinder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.InlineCodeSequenceFinder.InlineSequenceMatch;
import org.sandbox.jdt.internal.corext.fix.helper.lib.MethodCallReplacer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.VariableMapping;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

/**
 * Method Reuse Cleanup Fix Core - Enum for method reuse cleanup operations
 * 
 * This enum defines the types of method reuse cleanups available and provides
 * the logic for finding and rewriting code to use existing methods instead of
 * duplicating code inline.
 */
public enum MethodReuseCleanUpFixCore {

	INLINE_SEQUENCES(new InlineCodeSequenceFinder());

	private final InlineCodeSequenceFinder finder;

	MethodReuseCleanUpFixCore(InlineCodeSequenceFinder finder) {
		this.finder = finder;
	}

	/**
	 * Find operations - searches for inline code sequences that can be replaced
	 * with method calls
	 *
	 * @param compilationUnit The compilation unit to search
	 * @param operations Set to add found operations to
	 * @param nodesprocessed Set of already processed nodes
	 */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperation> operations, final Set<ASTNode> nodesprocessed) {
		
		// Visit all methods in the compilation unit to use as targets
		compilationUnit.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				// Search for inline sequences matching this method
				List<InlineSequenceMatch> matches = InlineCodeSequenceFinder.findInlineSequences(compilationUnit, node);
				
				// Create operations for each match found
				for (InlineSequenceMatch match : matches) {
					if (!nodesprocessed.contains(match.getMatchingStatements().get(0))) {
						operations.add(rewrite(node, match));
						// Mark all statements in the match as processed
						match.getMatchingStatements().forEach(nodesprocessed::add);
					}
				}
				return true;
			}
		});
	}

	/**
	 * Create a rewrite operation for replacing an inline sequence with a method call
	 *
	 * @param targetMethod The method whose body matches the inline sequence
	 * @param match The match information including the statements to replace
	 * @return A CompilationUnitRewriteOperation to perform the replacement
	 */
	public CompilationUnitRewriteOperation rewrite(final MethodDeclaration targetMethod, 
			final InlineSequenceMatch match) {
		return new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(final CompilationUnitRewrite cuRewrite,
					final LinkedProposalModelCore linkedModel) throws CoreException {
				TextEditGroup group = createTextEditGroup(MultiFixMessages.MethodReuseCleanUp_description, cuRewrite);
				ASTRewrite rewrite = cuRewrite.getASTRewrite();
				AST ast = rewrite.getAST();
				
				// Set up tight source range computer
				TightSourceRangeComputer rangeComputer;
				if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
					rangeComputer = (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
				} else {
					rangeComputer = new TightSourceRangeComputer();
				}
				
				List<Statement> statementsToReplace = match.getMatchingStatements();
				if (!statementsToReplace.isEmpty()) {
					rangeComputer.addTightSourceNode(statementsToReplace.get(0));
				}
				rewrite.setTargetSourceRangeComputer(rangeComputer);
				
				// Create the method invocation
				VariableMapping variableMapping = match.getVariableMapping();
				MethodInvocation methodCall = MethodCallReplacer.createMethodCall(ast, targetMethod, variableMapping);
				
				if (methodCall == null) {
					return;
				}
				
				// Determine how to replace based on the target method's return type
				replaceStatementsWithMethodCall(rewrite, ast, targetMethod, methodCall, statementsToReplace, group);
			}
		};
	}

	/**
	 * Replace the inline code sequence with a method call
	 * Handles both return statements and variable declarations
	 */
	@SuppressWarnings("unchecked")
	private void replaceStatementsWithMethodCall(ASTRewrite rewrite, AST ast, 
			MethodDeclaration targetMethod, MethodInvocation methodCall,
			List<Statement> statementsToReplace, TextEditGroup group) {
		
		if (statementsToReplace.isEmpty()) {
			return;
		}
		
		Statement firstStatement = statementsToReplace.get(0);
		ASTNode parent = firstStatement.getParent();
		
		if (!(parent instanceof Block)) {
			return;
		}
		
		ListRewrite listRewrite = rewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
		
		// Check if the target method has a return statement
		// If so, we need to handle variable declaration specially
		boolean targetReturnsValue = methodReturnsValue(targetMethod);
		Statement lastInlineStatement = statementsToReplace.get(statementsToReplace.size() - 1);
		
		if (targetReturnsValue && lastInlineStatement instanceof VariableDeclarationStatement) {
			// The last statement is a variable declaration that corresponds to the return value
			// Replace it with a variable declaration using the method call
			VariableDeclarationStatement varDecl = (VariableDeclarationStatement) lastInlineStatement;
			VariableDeclarationFragment fragment = (VariableDeclarationFragment) varDecl.fragments().get(0);
			
			// Create new variable declaration with method call as initializer
			VariableDeclarationFragment newFragment = ast.newVariableDeclarationFragment();
			newFragment.setName(ast.newSimpleName(fragment.getName().getIdentifier()));
			newFragment.setInitializer((Expression) ASTNode.copySubtree(ast, methodCall));
			
			VariableDeclarationStatement newVarDecl = ast.newVariableDeclarationStatement(newFragment);
			newVarDecl.setType((org.eclipse.jdt.core.dom.Type) ASTNode.copySubtree(ast, varDecl.getType()));
			
			// Replace the first statement with the new variable declaration
			listRewrite.replace(firstStatement, newVarDecl, group);
			
			// Remove intermediate statements (but not the last one which we're effectively replacing)
			for (int i = 1; i < statementsToReplace.size() - 1; i++) {
				listRewrite.remove(statementsToReplace.get(i), group);
			}
			
			// Remove the last statement
			if (statementsToReplace.size() > 1) {
				listRewrite.remove(lastInlineStatement, group);
			}
		} else {
			// Simple case: just replace with expression statement
			ExpressionStatement expressionStatement = ast.newExpressionStatement((Expression) ASTNode.copySubtree(ast, methodCall));
			listRewrite.replace(firstStatement, expressionStatement, group);
			
			// Remove remaining statements
			for (int i = 1; i < statementsToReplace.size(); i++) {
				listRewrite.remove(statementsToReplace.get(i), group);
			}
		}
	}

	/**
	 * Check if a method returns a value (has a return statement with an expression)
	 */
	@SuppressWarnings("unchecked")
	private boolean methodReturnsValue(MethodDeclaration method) {
		if (method.getBody() == null) {
			return false;
		}
		
		List<Statement> statements = method.getBody().statements();
		if (statements.isEmpty()) {
			return false;
		}
		
		// Check the last statement
		Statement lastStatement = statements.get(statements.size() - 1);
		if (lastStatement instanceof ReturnStatement) {
			ReturnStatement returnStmt = (ReturnStatement) lastStatement;
			return returnStmt.getExpression() != null;
		}
		
		return false;
	}

	@Override
	public String toString() {
		return "Inline Sequences";
	}
}
