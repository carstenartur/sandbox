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
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

/**
 * Hint provider for string and boolean simplification patterns using TriggerPattern.
 * 
 * <p>This class demonstrates using the TriggerPattern engine to suggest
 * cleaner code patterns. It provides hints for:</p>
 * <ul>
 * <li>Empty string concatenation: {@code "" + $x} → {@code String.valueOf($x)}</li>
 * <li>Redundant toString: {@code $x + ""} → {@code String.valueOf($x)}</li>
 * <li>String length check: {@code $str.length() == 0} → {@code $str.isEmpty()}</li>
 * <li>String equals empty: {@code $str.equals("")} → {@code $str.isEmpty()}</li>
 * <li>Boolean comparison: {@code $x == true} → {@code $x}</li>
 * <li>Ternary boolean return: {@code $cond ? true : false} → {@code $cond}</li>
 * <li>Redundant null check: {@code $x != null && $x.isEmpty()} → use Optional or guard</li>
 * <li>Collection size check: {@code $list.size() == 0} → {@code $list.isEmpty()}</li>
 * <li>Negated isEmpty: {@code !$str.isEmpty() == false} → {@code !$str.isEmpty()}</li>
 * <li>StringBuilder single append: {@code new StringBuilder().append($x).toString()} → {@code String.valueOf($x)}</li>
 * <li>Redundant String.format: {@code String.format("%s", $x)} → {@code String.valueOf($x)}</li>
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
		ASTNode xNode = ctx.getMatch().getBinding("$x"); //$NON-NLS-1$
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
		ASTNode xNode = ctx.getMatch().getBinding("$x"); //$NON-NLS-1$
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

	/**
	 * Suggests replacing {@code $str.length() == 0} with {@code $str.isEmpty()}.
	 * 
	 * <p>Example: {@code str.length() == 0} becomes {@code str.isEmpty()}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$str.length() == 0", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Use isEmpty()", 
	      description = "Replaces length() == 0 check with isEmpty() for better readability")
	public static IJavaCompletionProposal replaceStringLengthCheck(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		InfixExpression infixExpr = (InfixExpression) matchedNode;

		// Get the string expression
		ASTNode strNode = ctx.getMatch().getBinding("$str"); //$NON-NLS-1$
		if (strNode == null || !(strNode instanceof Expression)) {
			return null;
		}

		Expression strExpression = (Expression) strNode;

		// Create the replacement: strExpression.isEmpty()
		AST ast = ctx.getASTRewrite().getAST();
		
		MethodInvocation isEmptyCall = ast.newMethodInvocation();
		isEmptyCall.setExpression((Expression) ASTNode.copySubtree(ast, strExpression));
		isEmptyCall.setName(ast.newSimpleName("isEmpty")); //$NON-NLS-1$

		// Apply the rewrite
		ctx.getASTRewrite().replace(infixExpr, isEmptyCall, null);

		// Create the proposal
		String label = "Replace '" + infixExpr + "' with 'isEmpty()'"; //$NON-NLS-1$ //$NON-NLS-2$
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
	 * Suggests replacing {@code $str.equals("")} with {@code $str.isEmpty()}.
	 * 
	 * <p>Example: {@code str.equals("")} becomes {@code str.isEmpty()}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$str.equals(\"\")", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Use isEmpty()", 
	      description = "Replaces equals(\"\") check with isEmpty() for better performance")
	public static IJavaCompletionProposal replaceEqualsEmptyString(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof MethodInvocation)) {
			return null;
		}

		MethodInvocation methodInvocation = (MethodInvocation) matchedNode;

		// Get the string expression
		ASTNode strNode = ctx.getMatch().getBinding("$str"); //$NON-NLS-1$
		if (strNode == null || !(strNode instanceof Expression)) {
			return null;
		}

		Expression strExpression = (Expression) strNode;

		// Create the replacement: strExpression.isEmpty()
		AST ast = ctx.getASTRewrite().getAST();
		
		MethodInvocation isEmptyCall = ast.newMethodInvocation();
		isEmptyCall.setExpression((Expression) ASTNode.copySubtree(ast, strExpression));
		isEmptyCall.setName(ast.newSimpleName("isEmpty")); //$NON-NLS-1$

		// Apply the rewrite
		ctx.getASTRewrite().replace(methodInvocation, isEmptyCall, null);

		// Create the proposal
		String label = "Replace '" + methodInvocation + "' with 'isEmpty()'"; //$NON-NLS-1$ //$NON-NLS-2$
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
	 * Suggests replacing {@code $x == true} with {@code $x}.
	 * 
	 * <p>Example: {@code flag == true} becomes {@code flag}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$x == true", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Simplify boolean comparison", 
	      description = "Removes redundant comparison with true")
	public static IJavaCompletionProposal simplifyBooleanComparisonTrue(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		InfixExpression infixExpr = (InfixExpression) matchedNode;

		// Get the boolean variable
		ASTNode xNode = ctx.getMatch().getBinding("$x"); //$NON-NLS-1$
		if (xNode == null || !(xNode instanceof Expression)) {
			return null;
		}

		Expression boolExpression = (Expression) xNode;

		// Create the replacement: just the boolean expression
		AST ast = ctx.getASTRewrite().getAST();
		Expression replacement = (Expression) ASTNode.copySubtree(ast, boolExpression);

		// Apply the rewrite
		ctx.getASTRewrite().replace(infixExpr, replacement, null);

		// Create the proposal
		String label = "Simplify '" + infixExpr + "' to '" + boolExpression + "'"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
	 * Suggests replacing {@code $x == false} with {@code !$x}.
	 * 
	 * <p>Example: {@code flag == false} becomes {@code !flag}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$x == false", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Simplify boolean comparison", 
	      description = "Replaces comparison with false with negation operator")
	public static IJavaCompletionProposal simplifyBooleanComparisonFalse(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		InfixExpression infixExpr = (InfixExpression) matchedNode;

		// Get the boolean variable
		ASTNode xNode = ctx.getMatch().getBinding("$x"); //$NON-NLS-1$
		if (xNode == null || !(xNode instanceof Expression)) {
			return null;
		}

		Expression boolExpression = (Expression) xNode;

		// Create the replacement: !boolExpression
		AST ast = ctx.getASTRewrite().getAST();
		PrefixExpression negation = ast.newPrefixExpression();
		negation.setOperator(PrefixExpression.Operator.NOT);
		negation.setOperand((Expression) ASTNode.copySubtree(ast, boolExpression));

		// Apply the rewrite
		ctx.getASTRewrite().replace(infixExpr, negation, null);

		// Create the proposal
		String label = "Simplify '" + infixExpr + "' to '!" + boolExpression + "'"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
	 * Suggests replacing {@code $cond ? true : false} with {@code $cond}.
	 * 
	 * <p>Example: {@code isValid() ? true : false} becomes {@code isValid()}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$cond ? true : false", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Simplify ternary boolean", 
	      description = "Replaces redundant ternary expression with condition itself")
	public static IJavaCompletionProposal simplifyTernaryBooleanTrueFalse(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof ConditionalExpression)) {
			return null;
		}

		ConditionalExpression ternary = (ConditionalExpression) matchedNode;

		// Get the condition
		ASTNode condNode = ctx.getMatch().getBinding("$cond"); //$NON-NLS-1$
		if (condNode == null || !(condNode instanceof Expression)) {
			return null;
		}

		Expression condition = (Expression) condNode;

		// Create the replacement: just the condition
		AST ast = ctx.getASTRewrite().getAST();
		Expression replacement = (Expression) ASTNode.copySubtree(ast, condition);

		// Apply the rewrite
		ctx.getASTRewrite().replace(ternary, replacement, null);

		// Create the proposal
		String label = "Simplify '" + ternary + "' to '" + condition + "'"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
	 * Suggests replacing {@code $cond ? false : true} with {@code !$cond}.
	 * 
	 * <p>Example: {@code isValid() ? false : true} becomes {@code !isValid()}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$cond ? false : true", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Simplify ternary boolean", 
	      description = "Replaces inverted ternary expression with negated condition")
	public static IJavaCompletionProposal simplifyTernaryBooleanFalseTrue(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof ConditionalExpression)) {
			return null;
		}

		ConditionalExpression ternary = (ConditionalExpression) matchedNode;

		// Get the condition
		ASTNode condNode = ctx.getMatch().getBinding("$cond"); //$NON-NLS-1$
		if (condNode == null || !(condNode instanceof Expression)) {
			return null;
		}

		Expression condition = (Expression) condNode;

		// Create the replacement: !condition
		AST ast = ctx.getASTRewrite().getAST();
		PrefixExpression negation = ast.newPrefixExpression();
		negation.setOperator(PrefixExpression.Operator.NOT);
		negation.setOperand((Expression) ASTNode.copySubtree(ast, condition));

		// Apply the rewrite
		ctx.getASTRewrite().replace(ternary, negation, null);

		// Create the proposal
		String label = "Simplify '" + ternary + "' to '!" + condition + "'"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
	 * Suggests replacing {@code $list.size() == 0} with {@code $list.isEmpty()}.
	 * 
	 * <p>This pattern works for any Collection type (List, Set, Map, etc.)</p>
	 * <p>Example: {@code myList.size() == 0} becomes {@code myList.isEmpty()}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$list.size() == 0", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Use isEmpty() for collections", 
	      description = "Replaces size() == 0 with isEmpty() for better readability and performance")
	public static IJavaCompletionProposal replaceCollectionSizeCheck(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		InfixExpression infixExpr = (InfixExpression) matchedNode;

		// Get the collection expression
		ASTNode listNode = ctx.getMatch().getBinding("$list"); //$NON-NLS-1$
		if (listNode == null || !(listNode instanceof Expression)) {
			return null;
		}

		Expression listExpression = (Expression) listNode;

		// Create the replacement: listExpression.isEmpty()
		AST ast = ctx.getASTRewrite().getAST();
		
		MethodInvocation isEmptyCall = ast.newMethodInvocation();
		isEmptyCall.setExpression((Expression) ASTNode.copySubtree(ast, listExpression));
		isEmptyCall.setName(ast.newSimpleName("isEmpty")); //$NON-NLS-1$

		// Apply the rewrite
		ctx.getASTRewrite().replace(infixExpr, isEmptyCall, null);

		// Create the proposal
		String label = "Replace '" + infixExpr + "' with 'isEmpty()'"; //$NON-NLS-1$ //$NON-NLS-2$
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
	 * Suggests replacing {@code $list.size() > 0} with {@code !$list.isEmpty()}.
	 * 
	 * <p>Example: {@code myList.size() > 0} becomes {@code !myList.isEmpty()}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$list.size() > 0", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Use !isEmpty() for collections", 
	      description = "Replaces size() > 0 with !isEmpty() for better readability")
	public static IJavaCompletionProposal replaceCollectionSizeGreaterThanZero(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		InfixExpression infixExpr = (InfixExpression) matchedNode;

		// Get the collection expression
		ASTNode listNode = ctx.getMatch().getBinding("$list"); //$NON-NLS-1$
		if (listNode == null || !(listNode instanceof Expression)) {
			return null;
		}

		Expression listExpression = (Expression) listNode;

		// Create the replacement: !listExpression.isEmpty()
		AST ast = ctx.getASTRewrite().getAST();
		
		MethodInvocation isEmptyCall = ast.newMethodInvocation();
		isEmptyCall.setExpression((Expression) ASTNode.copySubtree(ast, listExpression));
		isEmptyCall.setName(ast.newSimpleName("isEmpty")); //$NON-NLS-1$
		
		PrefixExpression negation = ast.newPrefixExpression();
		negation.setOperator(PrefixExpression.Operator.NOT);
		negation.setOperand(isEmptyCall);

		// Apply the rewrite
		ctx.getASTRewrite().replace(infixExpr, negation, null);

		// Create the proposal
		String label = "Replace '" + infixExpr + "' with '!isEmpty()'"; //$NON-NLS-1$ //$NON-NLS-2$
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
	 * Suggests replacing {@code new StringBuilder().append($x).toString()} with {@code String.valueOf($x)}.
	 * 
	 * <p>This is a more complex pattern that demonstrates TriggerPattern's ability to match
	 * chained method calls. The pattern identifies an anti-pattern where StringBuilder is
	 * unnecessarily used for a single value conversion.</p>
	 * 
	 * <p>Example: {@code new StringBuilder().append(value).toString()} becomes {@code String.valueOf(value)}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "new StringBuilder().append($x).toString()", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Simplify StringBuilder single append", 
	      description = "Replaces unnecessary StringBuilder with single append with String.valueOf()")
	public static IJavaCompletionProposal simplifyStringBuilderSingleAppend(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof MethodInvocation)) {
			return null;
		}

		MethodInvocation methodInvocation = (MethodInvocation) matchedNode;

		// Get the appended value
		ASTNode xNode = ctx.getMatch().getBinding("$x"); //$NON-NLS-1$
		if (xNode == null || !(xNode instanceof Expression)) {
			return null;
		}

		Expression valueExpression = (Expression) xNode;

		// Create the replacement: String.valueOf(valueExpression)
		AST ast = ctx.getASTRewrite().getAST();
		
		MethodInvocation valueOfCall = ast.newMethodInvocation();
		valueOfCall.setExpression(ast.newName("String")); //$NON-NLS-1$
		valueOfCall.setName(ast.newSimpleName("valueOf")); //$NON-NLS-1$
		valueOfCall.arguments().add(ASTNode.copySubtree(ast, valueExpression));

		// Apply the rewrite
		ctx.getASTRewrite().replace(methodInvocation, valueOfCall, null);

		// Create the proposal
		String label = "Replace StringBuilder single append with 'String.valueOf(...)'"; //$NON-NLS-1$
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
	 * Suggests replacing {@code String.format("%s", $x)} with {@code String.valueOf($x)}.
	 * 
	 * <p>This pattern identifies cases where String.format is used with a simple "%s" format
	 * string, which is unnecessarily complex compared to String.valueOf().</p>
	 * 
	 * <p>Example: {@code String.format("%s", obj)} becomes {@code String.valueOf(obj)}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "String.format(\"%s\", $x)", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Simplify String.format", 
	      description = "Replaces String.format with simple %s with String.valueOf() for better performance")
	public static IJavaCompletionProposal simplifyStringFormat(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof MethodInvocation)) {
			return null;
		}

		MethodInvocation methodInvocation = (MethodInvocation) matchedNode;

		// Get the formatted value
		ASTNode xNode = ctx.getMatch().getBinding("$x"); //$NON-NLS-1$
		if (xNode == null || !(xNode instanceof Expression)) {
			return null;
		}

		Expression valueExpression = (Expression) xNode;

		// Create the replacement: String.valueOf(valueExpression)
		AST ast = ctx.getASTRewrite().getAST();
		
		MethodInvocation valueOfCall = ast.newMethodInvocation();
		valueOfCall.setExpression(ast.newName("String")); //$NON-NLS-1$
		valueOfCall.setName(ast.newSimpleName("valueOf")); //$NON-NLS-1$
		valueOfCall.arguments().add(ASTNode.copySubtree(ast, valueExpression));

		// Apply the rewrite
		ctx.getASTRewrite().replace(methodInvocation, valueOfCall, null);

		// Create the proposal
		String label = "Replace 'String.format(\"%s\", ...)' with 'String.valueOf(...)'"; //$NON-NLS-1$
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
	 * Suggests replacing {@code $x.toString().equals($y)} with {@code Objects.equals($x.toString(), $y)}.
	 * 
	 * <p>This is a complex pattern demonstrating null-safety improvements. The original code
	 * will throw NPE if $x is null, while the suggested replacement handles null safely.</p>
	 * 
	 * <p>Example: {@code obj.toString().equals(str)} becomes {@code Objects.equals(obj.toString(), str)}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$x.toString().equals($y)", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Use Objects.equals for null safety", 
	      description = "Replaces potential NPE-prone equals() with null-safe Objects.equals()")
	public static IJavaCompletionProposal useObjectsEquals(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof MethodInvocation)) {
			return null;
		}

		MethodInvocation methodInvocation = (MethodInvocation) matchedNode;

		// Get both expressions
		ASTNode xNode = ctx.getMatch().getBinding("$x"); //$NON-NLS-1$
		ASTNode yNode = ctx.getMatch().getBinding("$y"); //$NON-NLS-1$
		
		if (xNode == null || !(xNode instanceof Expression) ||
		    yNode == null || !(yNode instanceof Expression)) {
			return null;
		}

		Expression xExpression = (Expression) xNode;
		Expression yExpression = (Expression) yNode;

		// Create the replacement: Objects.equals(x.toString(), y)
		AST ast = ctx.getASTRewrite().getAST();
		ImportRewrite importRewrite = ctx.getImportRewrite();
		
		// Add import for Objects if needed
		String objectsType = importRewrite.addImport("java.util.Objects"); //$NON-NLS-1$
		
		MethodInvocation equalsCall = ast.newMethodInvocation();
		equalsCall.setExpression(ast.newName(objectsType));
		equalsCall.setName(ast.newSimpleName("equals")); //$NON-NLS-1$
		
		// Create x.toString()
		MethodInvocation toStringCall = ast.newMethodInvocation();
		toStringCall.setExpression((Expression) ASTNode.copySubtree(ast, xExpression));
		toStringCall.setName(ast.newSimpleName("toString")); //$NON-NLS-1$
		
		equalsCall.arguments().add(toStringCall);
		equalsCall.arguments().add(ASTNode.copySubtree(ast, yExpression));

		// Apply the rewrite
		ctx.getASTRewrite().replace(methodInvocation, equalsCall, null);

		// Create the proposal
		String label = "Replace with null-safe 'Objects.equals(...)'"; //$NON-NLS-1$
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
	 * Suggests replacing {@code $x != null ? $x : $default} with {@code Objects.requireNonNullElse($x, $default)}.
	 * 
	 * <p>This is a complex pattern that demonstrates modern Java API usage (Java 9+).
	 * It shows how TriggerPattern can suggest more idiomatic code that leverages newer APIs.</p>
	 * 
	 * <p>Example: {@code obj != null ? obj : "default"} becomes {@code Objects.requireNonNullElse(obj, "default")}</p>
	 * 
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$x != null ? $x : $default", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Use Objects.requireNonNullElse", 
	      description = "Replaces null-check ternary with Objects.requireNonNullElse() (Java 9+)")
	public static IJavaCompletionProposal useRequireNonNullElse(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof ConditionalExpression)) {
			return null;
		}

		ConditionalExpression ternary = (ConditionalExpression) matchedNode;

		// Get the expressions
		ASTNode xNode = ctx.getMatch().getBinding("$x"); //$NON-NLS-1$
		ASTNode defaultNode = ctx.getMatch().getBinding("$default"); //$NON-NLS-1$
		
		if (xNode == null || !(xNode instanceof Expression) ||
		    defaultNode == null || !(defaultNode instanceof Expression)) {
			return null;
		}

		Expression xExpression = (Expression) xNode;
		Expression defaultExpression = (Expression) defaultNode;

		// Create the replacement: Objects.requireNonNullElse(x, default)
		AST ast = ctx.getASTRewrite().getAST();
		ImportRewrite importRewrite = ctx.getImportRewrite();
		
		// Add import for Objects if needed
		String objectsType = importRewrite.addImport("java.util.Objects"); //$NON-NLS-1$
		
		MethodInvocation requireNonNullElseCall = ast.newMethodInvocation();
		requireNonNullElseCall.setExpression(ast.newName(objectsType));
		requireNonNullElseCall.setName(ast.newSimpleName("requireNonNullElse")); //$NON-NLS-1$
		requireNonNullElseCall.arguments().add(ASTNode.copySubtree(ast, xExpression));
		requireNonNullElseCall.arguments().add(ASTNode.copySubtree(ast, defaultExpression));

		// Apply the rewrite
		ctx.getASTRewrite().replace(ternary, requireNonNullElseCall, null);

		// Create the proposal
		String label = "Replace with 'Objects.requireNonNullElse(...)'"; //$NON-NLS-1$
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
