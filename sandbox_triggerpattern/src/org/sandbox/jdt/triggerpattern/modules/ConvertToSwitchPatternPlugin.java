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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

/**
 * Plugin to convert chains of {@code if-else if} statements with {@code instanceof}
 * checks into modern Java switch statements with pattern matching.
 * 
 * <p>This plugin detects patterns like:</p>
 * <pre>
 * if (obj instanceof String) {
 *     String s = (String) obj;
 *     // use s
 * } else if (obj instanceof Integer) {
 *     Integer i = (Integer) obj;
 *     // use i
 * } else {
 *     // default case
 * }
 * </pre>
 * 
 * <p>And transforms them to:</p>
 * <pre>
 * switch (obj) {
 *     case String s -> {
 *         // use s
 *     }
 *     case Integer i -> {
 *         // use i
 *     }
 *     default -> {
 *         // default case
 *     }
 * }
 * </pre>
 * 
 * <p>This transformation requires Java 17+ for pattern matching in switch.</p>
 * 
 * @since 1.3.0
 */
public class ConvertToSwitchPatternPlugin {

	/**
	 * Converts if-else-if chains with instanceof checks to switch statements.
	 * 
	 * @param ctx the hint context containing the matched AST node
	 * @return a completion proposal for the transformation, or null if not applicable
	 */
	@TriggerPattern(value = "if ($expr instanceof $type) $then", kind = PatternKind.STATEMENT)
	@Hint(displayName = "Convert instanceof chain to switch pattern", 
	      description = "Converts if-else-if chain with instanceof checks to switch statement with pattern matching (Java 17+)")
	public static IJavaCompletionProposal convertToSwitchPattern(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();
		
		if (!(matchedNode instanceof IfStatement)) {
			return null;
		}
		
		IfStatement rootIfStatement = (IfStatement) matchedNode;
		
		// Validate this is a suitable instanceof chain
		InstanceOfChain chain = analyzeInstanceOfChain(rootIfStatement);
		if (chain == null || !chain.isValid()) {
			return null;
		}
		
		// Create the switch statement
		AST ast = ctx.getASTRewrite().getAST();
		SwitchStatement switchStmt = createSwitchStatement(ast, chain);
		
		if (switchStmt == null) {
			return null;
		}
		
		// Apply the rewrite
		ctx.getASTRewrite().replace(rootIfStatement, switchStmt, null);
		
		// Create the proposal
		String label = "Convert instanceof chain to switch pattern"; //$NON-NLS-1$
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(
			label,
			ctx.getICompilationUnit(),
			ctx.getASTRewrite(),
			10,
			null
		);
		
		return proposal;
	}
	
	/**
	 * Analyzes an if statement to determine if it's a valid instanceof chain.
	 * 
	 * @param ifStatement the root if statement to analyze
	 * @return an InstanceOfChain if valid, null otherwise
	 */
	private static InstanceOfChain analyzeInstanceOfChain(IfStatement ifStatement) {
		InstanceOfChain chain = new InstanceOfChain();
		Statement current = ifStatement;
		
		while (current instanceof IfStatement) {
			IfStatement currentIf = (IfStatement) current;
			Expression condition = currentIf.getExpression();
			
			// Extract instanceof expression
			InstanceofExpression instanceofExpr = extractInstanceofExpression(condition);
			if (instanceofExpr == null) {
				break;
			}
			
			// Get the expression being checked
			Expression checkedExpr = instanceofExpr.getLeftOperand();
			
			// First branch - record the selector expression
			if (chain.selectorExpression == null) {
				chain.selectorExpression = checkedExpr;
			} else {
				// Verify it's the same expression being checked
				if (!isSameExpression(chain.selectorExpression, checkedExpr)) {
					return null; // Different expressions - not a valid chain
				}
			}
			
			// Extract type and optional variable name
			Type type = instanceofExpr.getRightOperand();
			SimpleName variableName = null;
			
			if (instanceofExpr instanceof PatternInstanceofExpression) {
				PatternInstanceofExpression patternInstanceof = (PatternInstanceofExpression) instanceofExpr;
				if (patternInstanceof.getPattern() instanceof TypePattern) {
					TypePattern typePattern = (TypePattern) patternInstanceof.getPattern();
					variableName = typePattern.getPatternVariable().getName();
				}
			}
			
			// Add this case to the chain
			Statement thenStatement = currentIf.getThenStatement();
			chain.addCase(new CaseInfo(type, variableName, thenStatement));
			
			// Move to else branch
			Statement elseStatement = currentIf.getElseStatement();
			if (elseStatement instanceof IfStatement) {
				current = elseStatement;
			} else {
				// This is the final else (default case)
				if (elseStatement != null) {
					chain.defaultCase = elseStatement;
				}
				break;
			}
		}
		
		return chain;
	}
	
	/**
	 * Extracts an InstanceofExpression from a condition, handling parenthesized expressions.
	 * 
	 * @param condition the condition expression
	 * @return the instanceof expression, or null if not found
	 */
	private static InstanceofExpression extractInstanceofExpression(Expression condition) {
		Expression expr = condition;
		
		// Unwrap parenthesized expressions
		while (expr instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression) {
			expr = ((org.eclipse.jdt.core.dom.ParenthesizedExpression) expr).getExpression();
		}
		
		if (expr instanceof InstanceofExpression) {
			return (InstanceofExpression) expr;
		}
		
		return null;
	}
	
	/**
	 * Checks if two expressions represent the same variable/expression.
	 * Simple implementation that compares string representation.
	 * 
	 * @param expr1 first expression
	 * @param expr2 second expression
	 * @return true if they represent the same expression
	 */
	private static boolean isSameExpression(Expression expr1, Expression expr2) {
		// Simple comparison based on string representation
		// A more sophisticated implementation would use binding comparison
		return expr1.toString().equals(expr2.toString());
	}
	
	/**
	 * Creates a switch statement from an instanceof chain.
	 * 
	 * @param ast the AST to create nodes
	 * @param chain the analyzed instanceof chain
	 * @return the created switch statement, or null if creation fails
	 */
	private static SwitchStatement createSwitchStatement(AST ast, InstanceOfChain chain) {
		if (chain.selectorExpression == null || chain.cases.isEmpty()) {
			return null;
		}
		
		SwitchStatement switchStmt = ast.newSwitchStatement();
		
		// Set the selector expression
		switchStmt.setExpression((Expression) ASTNode.copySubtree(ast, chain.selectorExpression));
		
		// Create switch cases
		List<Statement> statements = switchStmt.statements();
		
		for (CaseInfo caseInfo : chain.cases) {
			// Create case label with type pattern
			SwitchCase switchCase = ast.newSwitchCase();
			switchCase.setSwitchLabeledRule(true); // Use arrow syntax
			
			// Create type pattern
			TypePattern typePattern = ast.newTypePattern();
			typePattern.setPatternType((Type) ASTNode.copySubtree(ast, caseInfo.type));
			
			// Set pattern variable name
			if (caseInfo.variableName != null) {
				SimpleName varName = ast.newSimpleName(caseInfo.variableName.getIdentifier());
				typePattern.setPatternVariable(ast.newSingleVariableDeclaration());
				typePattern.getPatternVariable().setName(varName);
			} else {
				// Generate a variable name if not present
				SimpleName varName = ast.newSimpleName(generateVariableName(caseInfo.type));
				typePattern.setPatternVariable(ast.newSingleVariableDeclaration());
				typePattern.getPatternVariable().setName(varName);
			}
			
			switchCase.expressions().add(typePattern);
			statements.add(switchCase);
			
			// Add case body
			Statement body = (Statement) ASTNode.copySubtree(ast, caseInfo.thenStatement);
			statements.add(body);
		}
		
		// Add default case if present
		if (chain.defaultCase != null) {
			SwitchCase defaultCase = ast.newSwitchCase();
			defaultCase.setSwitchLabeledRule(true);
			defaultCase.setDefault(true);
			statements.add(defaultCase);
			
			Statement defaultBody = (Statement) ASTNode.copySubtree(ast, chain.defaultCase);
			statements.add(defaultBody);
		}
		
		return switchStmt;
	}
	
	/**
	 * Generates a variable name from a type for pattern matching.
	 * 
	 * @param type the type to generate a name for
	 * @return a simple variable name
	 */
	private static String generateVariableName(Type type) {
		String typeName = type.toString();
		// Simple heuristic: lowercase first character
		if (typeName.length() > 0) {
			return Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
		}
		return "var"; //$NON-NLS-1$
	}
	
	/**
	 * Represents a chain of instanceof checks that can be converted to a switch.
	 */
	private static class InstanceOfChain {
		Expression selectorExpression;
		List<CaseInfo> cases = new ArrayList<>();
		Statement defaultCase;
		
		void addCase(CaseInfo caseInfo) {
			cases.add(caseInfo);
		}
		
		boolean isValid() {
			// Need at least 2 cases to make a switch worthwhile
			return selectorExpression != null && cases.size() >= 2;
		}
	}
	
	/**
	 * Information about a single case in the instanceof chain.
	 */
	private static class CaseInfo {
		final Type type;
		final SimpleName variableName;
		final Statement thenStatement;
		
		CaseInfo(Type type, SimpleName variableName, Statement thenStatement) {
			this.type = Objects.requireNonNull(type);
			this.variableName = variableName;
			this.thenStatement = Objects.requireNonNull(thenStatement);
		}
	}
}
