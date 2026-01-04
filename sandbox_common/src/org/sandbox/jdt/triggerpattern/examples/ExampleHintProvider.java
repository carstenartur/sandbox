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
package org.sandbox.jdt.triggerpattern.examples;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

/**
 * Example hint provider demonstrating trigger pattern usage.
 * 
 * <p>This class shows how to create hints using annotations.</p>
 * 
 * @since 1.2.2
 */
public class ExampleHintProvider {

/**
 * Suggests replacing {@code $x + 1} with {@code ++$x} or {@code $x++}.
 * 
 * <p>Example: {@code a + 1} becomes {@code ++a}</p>
 */
@TriggerPattern(value = "$x + 1", kind = PatternKind.EXPRESSION)
@Hint(displayName = "Replace with increment operator", 
      description = "Replaces addition by 1 with the increment operator (++)")
public static IJavaCompletionProposal simplifyIncrement(HintContext ctx) {
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

	Expression variable = (Expression) xNode;

	// Create the replacement: ++variable
	AST ast = ctx.getASTRewrite().getAST();
	PrefixExpression prefixExpr = ast.newPrefixExpression();
	prefixExpr.setOperator(PrefixExpression.Operator.INCREMENT);
	prefixExpr.setOperand((Expression) ASTNode.copySubtree(ast, variable));

	// Apply the rewrite
	ctx.getASTRewrite().replace(infixExpr, prefixExpr, null);

	// Create the proposal
	String label = "Replace '" + infixExpr + "' with '++' operator"; //$NON-NLS-1$ //$NON-NLS-2$
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
 * Suggests replacing {@code $x - 1} with {@code --$x} or {@code $x--}.
 * 
 * <p>Example: {@code a - 1} becomes {@code --a}</p>
 */
@TriggerPattern(value = "$x - 1", kind = PatternKind.EXPRESSION)
@Hint(displayName = "Replace with decrement operator",
      description = "Replaces subtraction by 1 with the decrement operator (--)")
public static IJavaCompletionProposal simplifyDecrement(HintContext ctx) {
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

	Expression variable = (Expression) xNode;

	// Create the replacement: --variable
	AST ast = ctx.getASTRewrite().getAST();
	PrefixExpression prefixExpr = ast.newPrefixExpression();
	prefixExpr.setOperator(PrefixExpression.Operator.DECREMENT);
	prefixExpr.setOperand((Expression) ASTNode.copySubtree(ast, variable));

	// Apply the rewrite
	ctx.getASTRewrite().replace(infixExpr, prefixExpr, null);

	// Create the proposal
	String label = "Replace '" + infixExpr + "' with '--' operator"; //$NON-NLS-1$ //$NON-NLS-2$
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
