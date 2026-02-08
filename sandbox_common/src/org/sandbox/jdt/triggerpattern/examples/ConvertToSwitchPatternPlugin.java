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
package org.sandbox.jdt.triggerpattern.examples;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.sandbox.jdt.triggerpattern.api.PatternContext;
import org.sandbox.jdt.triggerpattern.api.PatternHandler;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.cleanup.ReflectivePatternCleanupPlugin;

/**
 * Example plugin demonstrating the reflective pattern handling framework.
 * 
 * <p>This plugin converts a chain of {@code if-else instanceof} statements
 * into a modern switch pattern expression (Java 21+).</p>
 * 
 * <p><b>Before:</b></p>
 * <pre>
 * if (obj instanceof String s) {
 *     return s.length();
 * } else if (obj instanceof Integer i) {
 *     return i;
 * } else {
 *     return 0;
 * }
 * </pre>
 * 
 * <p><b>After:</b></p>
 * <pre>
 * return switch (obj) {
 *     case String s -> s.length();
 *     case Integer i -> i;
 *     default -> 0;
 * };
 * </pre>
 * 
 * <p><b>Note:</b> This is a simplified example for demonstration purposes.
 * A production implementation would need to handle more edge cases.</p>
 * 
 * @since 1.3.0
 */
public class ConvertToSwitchPatternPlugin extends ReflectivePatternCleanupPlugin {
	
	/**
	 * Handles {@code if (expr instanceof Type)} patterns.
	 * 
	 * <p>This method is automatically invoked when the pattern matches.
	 * It checks if the if-statement is part of an instanceof chain and
	 * converts the entire chain to a switch expression.</p>
	 * 
	 * @param context the pattern context with match information and AST tools
	 */
	@PatternHandler(value = "if ($expr instanceof $type) $then", kind = PatternKind.STATEMENT)
	public void handleInstanceOfChain(PatternContext context) {
		// Get the matched if statement
		Statement matchedStmt = (Statement) context.getMatchedNode();
		if (!(matchedStmt instanceof IfStatement ifStmt)) {
			return;
		}
		
		// Check if this is the start of an instanceof chain
		// (i.e., it's not the 'else' part of another if statement)
		if (isPartOfInstanceOfChain(ifStmt)) {
			return; // Skip if this is nested - we only process the root
		}
		
		// Collect all instanceof cases in the chain
		List<InstanceOfCase> cases = collectInstanceOfCases(ifStmt);
		
		// Need at least 2 cases to make a switch worthwhile
		if (cases.size() < 2) {
			return;
		}
		
		// Get the expression being tested (should be same for all cases)
		Expression switchExpr = cases.get(0).expression;
		
		// Build the switch statement
		AST ast = context.getAST();
		SwitchStatement switchStmt = buildSwitchStatement(ast, switchExpr, cases);
		
		// Replace the if-else chain with the switch statement
		ASTRewrite rewrite = context.getRewrite();
		rewrite.replace(matchedStmt, switchStmt, context.getEditGroup());
	}
	
	/**
	 * Checks if this if-statement is part of an instanceof chain but not the root.
	 */
	private boolean isPartOfInstanceOfChain(IfStatement ifStmt) {
		ASTNode parent = ifStmt.getParent();
		if (parent instanceof IfStatement parentIf) {
			// Check if we're the else statement of a parent if
			if (parentIf.getElseStatement() == ifStmt) {
				// Check if parent is also an instanceof check
				if (parentIf.getExpression() instanceof InstanceofExpression) {
					return true; // We're nested in an instanceof chain
				}
			}
		}
		return false;
	}
	
	/**
	 * Collects all instanceof cases from an if-else chain.
	 */
	private List<InstanceOfCase> collectInstanceOfCases(IfStatement root) {
		List<InstanceOfCase> cases = new ArrayList<>();
		IfStatement current = root;
		
		while (current != null) {
			Expression condition = current.getExpression();
			if (condition instanceof InstanceofExpression instanceOf) {
				Expression expr = instanceOf.getLeftOperand();
				Type type = instanceOf.getRightOperand();
				Statement thenStmt = current.getThenStatement();
				
				cases.add(new InstanceOfCase(expr, type, thenStmt));
				
				// Move to else statement if it's another if
				Statement elseStmt = current.getElseStatement();
				if (elseStmt instanceof IfStatement nextIf) {
					current = nextIf;
				} else {
					// Found the default case or end of chain
					if (elseStmt != null) {
						cases.add(new InstanceOfCase(null, null, elseStmt)); // default
					}
					break;
				}
			} else {
				break; // Not an instanceof expression, stop
			}
		}
		
		return cases;
	}
	
	/**
	 * Builds a switch statement from the collected cases.
	 */
	private SwitchStatement buildSwitchStatement(AST ast, Expression switchExpr, List<InstanceOfCase> cases) {
		SwitchStatement switchStmt = ast.newSwitchStatement();
		switchStmt.setExpression((Expression) ASTNode.copySubtree(ast, switchExpr));
		
		for (InstanceOfCase instanceOfCase : cases) {
			if (instanceOfCase.type != null) {
				// Regular case
				SwitchCase switchCase = ast.newSwitchCase();
				// Note: Simplified - a real implementation would need to handle
				// pattern variables and other Java 21 features properly
				switchCase.expressions().add(ASTNode.copySubtree(ast, instanceOfCase.type));
				switchStmt.statements().add(switchCase);
				
				// Add the statement
				Statement stmt = (Statement) ASTNode.copySubtree(ast, instanceOfCase.statement);
				switchStmt.statements().add(stmt);
			} else {
				// Default case
				SwitchCase defaultCase = ast.newSwitchCase();
				defaultCase.setDefault(true);
				switchStmt.statements().add(defaultCase);
				
				Statement stmt = (Statement) ASTNode.copySubtree(ast, instanceOfCase.statement);
				switchStmt.statements().add(stmt);
			}
		}
		
		return switchStmt;
	}
	
	/**
	 * Holds information about a single instanceof case.
	 */
	private static class InstanceOfCase {
		final Expression expression; // The expression being tested (e.g., obj)
		final Type type;             // The type being checked (e.g., String)
		final Statement statement;    // The statement to execute (then or else body)
		
		InstanceOfCase(Expression expression, Type type, Statement statement) {
			this.expression = expression;
			this.type = type;
			this.statement = statement;
		}
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				return switch (obj) {
					case String s -> s.length();
					case Integer i -> i;
					default -> 0;
				};
				""";
		} else {
			return """
				if (obj instanceof String s) {
					return s.length();
				} else if (obj instanceof Integer i) {
					return i;
				} else {
					return 0;
				}
				""";
		}
	}
}
