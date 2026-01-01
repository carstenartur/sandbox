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
package org.sandbox.jdt.internal.corext.fix.helper;

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
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodReuse;
import org.sandbox.jdt.internal.corext.fix.helper.lib.InlineCodeSequenceFinder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.InlineCodeSequenceFinder.InlineSequenceMatch;
import org.sandbox.jdt.internal.corext.fix.helper.lib.MethodCallReplacer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.VariableMapping;

/**
 * Plugin for detecting and replacing inline code sequences with method calls
 */
public class InlineSequencesPlugin extends AbstractMethodReuse<MethodDeclaration> {

	@Override
	public void find(Object fixcore, CompilationUnit compilationUnit,
			Set<?> operations, Set<ASTNode> nodesprocessed) {
		
		// Cast to the correct type
		@SuppressWarnings("unchecked")
		Set<CompilationUnitRewriteOperation> ops = (Set<CompilationUnitRewriteOperation>) operations;
		MethodReuseCleanUpFixCore fixCore = (MethodReuseCleanUpFixCore) fixcore;
		
		// Use HelperVisitor to visit all methods in the compilation unit
		ReferenceHolder<ASTNode, Object> dataholder = new ReferenceHolder<>();
		
		HelperVisitor.callMethodDeclarationVisitor(compilationUnit, dataholder, nodesprocessed,
				(node, holder) -> {
					// Search for inline sequences matching this method
					List<InlineSequenceMatch> matches = InlineCodeSequenceFinder.findInlineSequences(compilationUnit, node);
					
					// Create operations for each match found
					for (InlineSequenceMatch match : matches) {
						List<Statement> matchingStatements = match.getMatchingStatements();
						if (matchingStatements.isEmpty()) {
							continue;
						}
						if (!nodesprocessed.contains(matchingStatements.get(0))) {
							// Create a ReferenceHolder to store both the method and the match
							ReferenceHolder<ASTNode, Object> matchHolder = new ReferenceHolder<>();
							matchHolder.put(node, match);
							ops.add(fixCore.rewrite(matchHolder));
							// Mark all statements in the match as processed
							matchingStatements.forEach(nodesprocessed::add);
						}
					}
					return false;
				}, null);
	}

	@Override
	public void rewrite(Object fixcore, ReferenceHolder<?, ?> holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		
		// Cast to the correct types
		MethodReuseCleanUpFixCore fixCore = (MethodReuseCleanUpFixCore) fixcore;
		@SuppressWarnings("unchecked")
		ReferenceHolder<ASTNode, Object> typedHolder = 
			(ReferenceHolder<ASTNode, Object>) holder;
		
		// Get the method declaration (first key in the map)
		MethodDeclaration targetMethod = null;
		InlineSequenceMatch match = null;
		for (var entry : typedHolder.entrySet()) {
			targetMethod = (MethodDeclaration) entry.getKey();
			match = (InlineSequenceMatch) entry.getValue();
			break; // We only have one entry
		}
		
		if (targetMethod == null || match == null) {
			return;
		}
		
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
			
			// Defensive check: ensure there is at least one fragment before accessing index 0
			if (varDecl.fragments().isEmpty()) {
				// Fallback to simple replacement with a method call expression
				ExpressionStatement expressionStatement = ast.newExpressionStatement((Expression) ASTNode.copySubtree(ast, methodCall));
				listRewrite.replace(firstStatement, expressionStatement, group);
				
				// Remove remaining statements
				for (int i = 1; i < statementsToReplace.size(); i++) {
					listRewrite.remove(statementsToReplace.get(i), group);
				}
				return;
			}
			
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
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				// After: Inline code replaced with method call
				String name = formatName(firstName, lastName);
				System.out.println(name);
				""";
		} else {
			return """
				// Before: Duplicated code inline
				String name = firstName.trim() + " " + lastName.trim();
				System.out.println(name);
				""";
		}
	}
}
