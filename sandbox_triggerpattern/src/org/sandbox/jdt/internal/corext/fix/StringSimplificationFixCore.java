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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Fix core for string simplification using TriggerPattern hints.
 * 
 * <p>This class applies string simplification patterns as cleanup operations,
 * transforming patterns like {@code "" + x} and {@code x + ""} to {@code String.valueOf(x)}.</p>
 * 
 * @since 1.2.2
 */
public class StringSimplificationFixCore {
	
	private static final TriggerPatternEngine ENGINE = new TriggerPatternEngine();
	
	/**
	 * Finds string simplification operations in the compilation unit.
	 * 
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			java.util.Set<CompilationUnitRewriteOperation> operations) {
		
		// Pattern 1: "" + $x
		Pattern emptyPrefixPattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION); //$NON-NLS-1$ //$NON-NLS-2$
		List<Match> emptyPrefixMatches = ENGINE.findMatches(compilationUnit, emptyPrefixPattern);
		for (Match match : emptyPrefixMatches) {
			operations.add(new StringSimplificationOperation(match, "Empty string prefix")); //$NON-NLS-1$
		}
		
		// Pattern 2: $x + ""
		Pattern emptySuffixPattern = new Pattern("$x + \"\"", PatternKind.EXPRESSION); //$NON-NLS-1$ //$NON-NLS-2$
		List<Match> emptySuffixMatches = ENGINE.findMatches(compilationUnit, emptySuffixPattern);
		for (Match match : emptySuffixMatches) {
			operations.add(new StringSimplificationOperation(match, "Empty string suffix")); //$NON-NLS-1$
		}
	}
	
	/**
	 * Rewrite operation for string simplification.
	 */
	private static class StringSimplificationOperation extends CompilationUnitRewriteOperation {
		
		private final Match match;
		private final String description;
		
		public StringSimplificationOperation(Match match, String description) {
			this.match = match;
			this.description = description;
		}
		
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup(description, cuRewrite);
			
			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof InfixExpression)) {
				return;
			}
			
			InfixExpression infixExpr = (InfixExpression) matchedNode;
			
			// Get the bound variable from placeholders
			ASTNode xNode = match.getBindings().get("$x"); //$NON-NLS-1$
			if (xNode == null || !(xNode instanceof Expression)) {
				return;
			}
			
			Expression valueExpression = (Expression) xNode;
			
			// Create the replacement: String.valueOf(valueExpression)
			MethodInvocation methodInvocation = ast.newMethodInvocation();
			methodInvocation.setExpression(ast.newName("String")); //$NON-NLS-1$
			methodInvocation.setName(ast.newSimpleName("valueOf")); //$NON-NLS-1$
			methodInvocation.arguments().add(ASTNode.copySubtree(ast, valueExpression));
			
			// Apply the rewrite
			rewrite.replace(infixExpr, methodInvocation, group);
		}
	}
}
