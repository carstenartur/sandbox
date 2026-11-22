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
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodReuse;
import org.sandbox.jdt.internal.corext.fix.helper.lib.InlineCodeSequenceFinder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.InlineCodeSequenceFinder.InlineSequenceMatch;
import org.sandbox.jdt.internal.corext.fix.helper.lib.MethodCallReplacer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.VariableMapping;
import org.sandbox.jdt.triggerpattern.cleanup.EncodingSourceRewrite;

/**
 * Plugin for detecting and replacing inline code sequences with method calls
 */
public class InlineSequencesPlugin extends AbstractMethodReuse<MethodDeclaration> {

	@Override
	public void find(Object fixcore, CompilationUnit compilationUnit,
			Set<?> operations, Set<ASTNode> nodesprocessed) throws CoreException {
		
		// Cast to the correct type
		@SuppressWarnings("unchecked")
		Set<CompilationUnitRewriteOperation> ops = (Set<CompilationUnitRewriteOperation>) operations;
		MethodReuseCleanUpFixCore fixCore = (MethodReuseCleanUpFixCore) fixcore;
		
		// Use HelperVisitor to visit all methods in the compilation unit
		ReferenceHolder<Integer, Object> dataholder = new ReferenceHolder<>();
		Set<MethodDeclaration> reusedMethods = new HashSet<>();
		Set<MethodDeclaration> rewrittenMethods = new HashSet<>();
		
		HelperVisitorFactory.callMethodDeclarationVisitor(compilationUnit, dataholder, nodesprocessed,
				(node, holder) -> {
					if (rewrittenMethods.contains(node)) {
						return false;
					}
					// Search for inline sequences matching this method
					List<InlineSequenceMatch> matches = InlineCodeSequenceFinder.findInlineSequences(compilationUnit, node);
					
					// Create operations for each match found
					for (InlineSequenceMatch match : matches) {
						List<Statement> matchingStatements = match.getMatchingStatements();
						if (matchingStatements.isEmpty()) {
							continue;
						}
						if (!reusedMethods.contains(match.getContainingMethod())
								&& matchingStatements.stream().noneMatch(nodesprocessed::contains)) {
							// Create a ReferenceHolder to store both the method and the match
							ReferenceHolder<Integer, Object> matchHolder = new ReferenceHolder<>();
							matchHolder.put(0, node);
							matchHolder.put(1, match);
							ops.add(fixCore.rewrite(matchHolder));
							// Mark all statements in the match as processed
							matchingStatements.forEach(nodesprocessed::add);
							// A reused body must remain intact: rewriting both duplicate methods
							// into calls to one another would introduce infinite recursion.
							reusedMethods.add(node);
							rewrittenMethods.add(match.getContainingMethod());
						}
					}
					return false;
				});
	}

	@Override
	public void rewrite(Object fixcore, ReferenceHolder<?, ?> holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		
		// Get the method declaration and match from the holder
		MethodDeclaration targetMethod = (MethodDeclaration) holder.get(0);
		InlineSequenceMatch match = (InlineSequenceMatch) holder.get(1);
		
		if (targetMethod == null || match == null) {
			return;
		}
		
		// Important: Get the AST from cuRewrite's root, not from the original nodes
		AST ast = cuRewrite.getRoot().getAST();
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		
		// Set up tight source range computer
		TightSourceRangeComputer rangeComputer;
		if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
			rangeComputer = (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
		} else {
			rangeComputer = new TightSourceRangeComputer();
		}
		
		List<Statement> statementsToReplace = match.getMatchingStatements();
		statementsToReplace.forEach(rangeComputer::addTightSourceNode);
		rewrite.setTargetSourceRangeComputer(rangeComputer);
		
		// Create the method invocation
		VariableMapping variableMapping = match.getVariableMapping();
		MethodInvocation methodCall = MethodCallReplacer.createMethodCall(rewrite, targetMethod, variableMapping);
		
		if (methodCall == null) {
			return;
		}
		MethodReuseNlsFix.record(cuRewrite, targetMethod, methodCall, statementsToReplace, variableMapping);
		for (Statement statement : statementsToReplace) statement.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (node instanceof StringLiteral || node instanceof TextBlock) EncodingSourceRewrite.record(cuRewrite, node);
			}
		});
		
		// Determine how to replace based on the target method's return type
		replaceStatementsWithMethodCall(rewrite, ast, targetMethod, methodCall, statementsToReplace, group);
	}

	/**
	 * Replace the inline code sequence with a method call
	 * Handles both return statements and variable declarations
	 */
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
		Statement replacement;
		if (targetReturnsValue && lastInlineStatement instanceof VariableDeclarationStatement) {
			// Keep final/annotations and fragment dimensions such as "String result[]".
			VariableDeclarationStatement declaration = (VariableDeclarationStatement) ASTNode.copySubtree(ast, lastInlineStatement);
			((VariableDeclarationFragment) declaration.fragments().get(0)).setInitializer(methodCall);
			replacement = declaration;
		} else if (targetReturnsValue && lastInlineStatement instanceof ReturnStatement) {
			ReturnStatement result = ast.newReturnStatement();
			result.setExpression(methodCall);
			replacement = result;
		} else {
			replacement = ast.newExpressionStatement(methodCall);
		}
		listRewrite.replace(firstStatement, replacement, group);
		for (int i = 1; i < statementsToReplace.size(); i++) {
			listRewrite.remove(statementsToReplace.get(i), group);
		}
	}

	/**
	 * Check if a method returns a value (has a return statement with an expression)
	 */
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
