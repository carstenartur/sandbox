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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Detects consecutive for-loops that add to the same collection variable.
 * 
 * <p>This detector identifies patterns where multiple consecutive enhanced for-loops
 * all add elements to the same collection. These can be converted to Stream.concat()
 * to preserve all elements instead of overwriting the collection.</p>
 * 
 * <p><b>Pattern Example:</b></p>
 * <pre>{@code
 * List<RuleEntry> entries = new ArrayList<>();
 * for (MethodRule rule : methodRules) {
 *     entries.add(new RuleEntry(rule, TYPE_METHOD));
 * }
 * for (TestRule rule : testRules) {
 *     entries.add(new RuleEntry(rule, TYPE_TEST));
 * }
 * 
 * // Should convert to:
 * List<RuleEntry> entries = Stream.concat(
 *     methodRules.stream().map(rule -> new RuleEntry(rule, TYPE_METHOD)),
 *     testRules.stream().map(rule -> new RuleEntry(rule, TYPE_TEST))
 * ).collect(Collectors.toList());
 * }</pre>
 * 
 * <p><b>Detection Requirements:</b></p>
 * <ul>
 * <li>Loops must be consecutive (only comments allowed between them)</li>
 * <li>All loops must add to the same collection variable</li>
 * <li>The target collection must not be read between loops</li>
 * <li>Each loop body must contain only a simple add operation</li>
 * </ul>
 * 
 * @see EnhancedForHandler
 * @see StreamPipelineBuilder
 */
public class ConsecutiveLoopGroupDetector {

	/**
	 * Represents a group of consecutive loops adding to the same collection.
	 */
	public static class ConsecutiveLoopGroup {
		private final String targetVariable;
		private final List<EnhancedForStatement> loops;

		public ConsecutiveLoopGroup(String targetVariable, List<EnhancedForStatement> loops) {
			this.targetVariable = targetVariable;
			this.loops = new ArrayList<>(loops);
		}

		public String getTargetVariable() {
			return targetVariable;
		}

		public List<EnhancedForStatement> getLoops() {
			return new ArrayList<>(loops);
		}

		public int size() {
			return loops.size();
		}
	}

	/**
	 * Detects all consecutive loop groups in the given block.
	 * 
	 * @param block the block to analyze
	 * @return list of detected consecutive loop groups (size 2+)
	 */
	public static List<ConsecutiveLoopGroup> detectGroups(Block block) {
		if (block == null) {
			return new ArrayList<>();
		}

		List<ConsecutiveLoopGroup> groups = new ArrayList<>();
		List<Statement> statements = block.statements();

		int i = 0;
		while (i < statements.size()) {
			Statement stmt = statements.get(i);

			// Check if this is an EnhancedForStatement with simple add pattern
			if (stmt instanceof EnhancedForStatement) {
				EnhancedForStatement firstLoop = (EnhancedForStatement) stmt;
				String targetVar = getTargetAddVariable(firstLoop);

				if (targetVar != null) {
					// Found a loop that adds to a variable - check for consecutive similar loops
					List<EnhancedForStatement> group = new ArrayList<>();
					group.add(firstLoop);

					// Scan forward for consecutive loops adding to same variable
					int j = i + 1;
					while (j < statements.size()) {
						Statement nextStmt = statements.get(j);

						if (nextStmt instanceof EnhancedForStatement) {
							EnhancedForStatement nextLoop = (EnhancedForStatement) nextStmt;
							String nextTargetVar = getTargetAddVariable(nextLoop);

							if (targetVar.equals(nextTargetVar)) {
								// Same target variable - add to group
								group.add(nextLoop);
								j++;
							} else {
								// Different target or not an add pattern - stop group
								break;
							}
						} else {
							// Non-loop statement breaks the consecutive sequence
							break;
						}
					}

					// Only create a group if we found 2+ consecutive loops
					if (group.size() >= 2) {
						groups.add(new ConsecutiveLoopGroup(targetVar, group));
						i = j; // Skip past all loops in this group
						continue;
					}
				}
			}

			i++;
		}

		return groups;
	}

	/**
	 * Extracts the target variable name if the loop body is a simple add pattern.
	 * 
	 * <p>Detects patterns like:</p>
	 * <pre>{@code
	 * for (Type item : collection) {
	 *     targetList.add(expression);
	 * }
	 * }</pre>
	 * 
	 * @param loop the enhanced for-loop to check
	 * @return the target variable name, or null if not a simple add pattern
	 */
	private static String getTargetAddVariable(EnhancedForStatement loop) {
		Statement body = loop.getBody();

		// Handle both Block and single statement
		List<Statement> bodyStatements = new ArrayList<>();
		if (body instanceof Block) {
			List<Statement> stmts = ((Block) body).statements();
			bodyStatements.addAll(stmts);
		} else {
			bodyStatements.add(body);
		}

		// Must be exactly one statement
		if (bodyStatements.size() != 1) {
			return null;
		}

		Statement stmt = bodyStatements.get(0);
		if (!(stmt instanceof ExpressionStatement)) {
			return null;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		if (!(exprStmt.getExpression() instanceof MethodInvocation)) {
			return null;
		}

		MethodInvocation methodInv = (MethodInvocation) exprStmt.getExpression();

		// Check method name is "add"
		if (!"add".equals(methodInv.getName().getIdentifier())) {
			return null;
		}

		// Check invoked on a SimpleName (the collection variable)
		if (!(methodInv.getExpression() instanceof SimpleName)) {
			return null;
		}

		SimpleName receiver = (SimpleName) methodInv.getExpression();
		return receiver.getIdentifier();
	}
}
