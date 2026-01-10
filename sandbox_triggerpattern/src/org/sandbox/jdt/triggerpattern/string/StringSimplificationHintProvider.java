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
package org.sandbox.jdt.triggerpattern.string;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

/**
 * Hint provider for string simplification patterns using TriggerPattern.
 * 
 * <p>This class demonstrates using the TriggerPattern engine to suggest
 * cleaner string handling patterns. It provides hints for:</p>
 * <ul>
 * <li>Empty string concatenation: {@code "" + $x} → {@code String.valueOf($x)}</li>
 * <li>Redundant toString: {@code $x + ""} → {@code String.valueOf($x)}</li>
 * </ul>
 * 
 * @since 1.2.2
 */
public class StringSimplificationHintProvider {

	/**
	 * Suggests replacing {@code "" + $x} with {@code String.valueOf($x)}.
	 * 
	 * <p>Example: {@code "" + value} becomes {@code String.valueOf(value)}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "\"\" + $x", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Replace with String.valueOf()", 
	      description = "Replaces empty string concatenation with String.valueOf() for clarity")
	public static IJavaCompletionProposal replaceEmptyStringConcatenation(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		InfixExpression infixExpr = (InfixExpression) matchedNode;

		// Get the bound variable from placeholders
		ASTNode xNode = ctx.getMatch().getBindings().get("$x"); //$NON-NLS-1$
		if (xNode == null || !(xNode instanceof Expression)) {
			return null;
		}

		Expression valueExpression = (Expression) xNode;

		// Create the replacement: String.valueOf(valueExpression)
		AST ast = ctx.getASTRewrite().getAST();
		ImportRewrite importRewrite = ctx.getImportRewrite();
		
		MethodInvocation methodInvocation = ast.newMethodInvocation();
		methodInvocation.setExpression(ast.newName("String")); //$NON-NLS-1$
		methodInvocation.setName(ast.newSimpleName("valueOf")); //$NON-NLS-1$
		methodInvocation.arguments().add(ASTNode.copySubtree(ast, valueExpression));

		// Apply the rewrite
		ctx.getASTRewrite().replace(infixExpr, methodInvocation, null);

		// Create the proposal
		String label = "Replace '" + infixExpr + "' with 'String.valueOf(...)'"; //$NON-NLS-1$ //$NON-NLS-2$
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(
			label,
			ctx.getICompilationUnit(),
			ctx.getASTRewrite(),
			10, // relevance
			(Image) null
		);

		return proposal;
	}

	/**
	 * Suggests replacing {@code $x + ""} with {@code String.valueOf($x)}.
	 * 
	 * <p>Example: {@code value + ""} becomes {@code String.valueOf(value)}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$x + \"\"", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Replace with String.valueOf()", 
	      description = "Replaces concatenation with empty string with String.valueOf() for clarity")
	public static IJavaCompletionProposal replaceTrailingEmptyString(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		InfixExpression infixExpr = (InfixExpression) matchedNode;

		// Get the bound variable
		ASTNode xNode = ctx.getMatch().getBindings().get("$x"); //$NON-NLS-1$
		if (xNode == null || !(xNode instanceof Expression)) {
			return null;
		}

		Expression valueExpression = (Expression) xNode;

		// Create the replacement: String.valueOf(valueExpression)
		AST ast = ctx.getASTRewrite().getAST();
		
		MethodInvocation methodInvocation = ast.newMethodInvocation();
		methodInvocation.setExpression(ast.newName("String")); //$NON-NLS-1$
		methodInvocation.setName(ast.newSimpleName("valueOf")); //$NON-NLS-1$
		methodInvocation.arguments().add(ASTNode.copySubtree(ast, valueExpression));

		// Apply the rewrite
		ctx.getASTRewrite().replace(infixExpr, methodInvocation, null);

		// Create the proposal
		String label = "Replace '" + infixExpr + "' with 'String.valueOf(...)'"; //$NON-NLS-1$ //$NON-NLS-2$
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(
			label,
			ctx.getICompilationUnit(),
			ctx.getASTRewrite(),
			10, // relevance
			(Image) null
		);

		return proposal;
	}
}
