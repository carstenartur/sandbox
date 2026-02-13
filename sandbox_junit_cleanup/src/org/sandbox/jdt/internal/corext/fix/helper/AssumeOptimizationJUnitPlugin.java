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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Optimizes JUnit assumptions by removing unnecessary negations.
 * 
 * Examples:
 * - assumeTrue(!condition) → assumeFalse(condition)
 * - assumeFalse(!condition) → assumeTrue(condition)
 * 
 * Note: JUnit 5 Assumptions does not have assumeNull/assumeNotNull,
 * so those optimizations are not applicable.
 */
public class AssumeOptimizationJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();
		
		// NOTE: We only process JUnit 5 (Assumptions) calls here.
		// JUnit 4 (Assume) calls are handled by AssumeJUnitPlugin which does migration.
		// Processing JUnit 4 here would conflict with the migration rewrites.
		
		// Find assumeTrue and assumeFalse calls in JUnit 5 only
		HelperVisitorFactory.forMethodCalls(ORG_JUNIT_JUPITER_API_ASSUMPTIONS, Set.of("assumeTrue", "assumeFalse"))
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> processAssumption(fixcore, operations, visited, aholder));
	}

	private boolean processAssumption(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		
		if (!(node instanceof MethodInvocation)) {
			return false;
		}
		
		MethodInvocation mi = (MethodInvocation) node;
		List<?> arguments = mi.arguments();
		
		if (arguments.isEmpty()) {
			return false;
		}
		
		// Get the condition expression (first or last argument depending on message presence)
		Expression condition = null;
		
		// For assumptions, the condition can be in different positions
		// assumeTrue(condition) or assumeTrue(condition, message) or assumeTrue(condition, messageSupplier)
		for (Object arg : arguments) {
			Expression expr = (Expression) arg;
			// Check if this is a negated condition
			if (expr instanceof PrefixExpression) {
				PrefixExpression prefix = (PrefixExpression) expr;
				if (prefix.getOperator() == PrefixExpression.Operator.NOT) {
					condition = expr;
					break;
				}
			}
		}
		
		if (condition == null) {
			return false;
		}
		
		return addStandardRewriteOperation(fixcore, operations, node, dataHolder);
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		if (!(junitHolder.getMinv() instanceof MethodInvocation)) {
			return;
		}
		
		MethodInvocation mi = junitHolder.getMethodInvocation();
		List<?> arguments = mi.arguments();
		
		if (arguments.isEmpty()) {
			return;
		}
		
		// Determine if this is assumeTrue or assumeFalse
		String methodName = mi.getName().getIdentifier();
		
		// Find the negated condition
		for (int i = 0; i < arguments.size(); i++) {
			Expression arg = (Expression) arguments.get(i);
			
			if (arg instanceof PrefixExpression) {
				PrefixExpression prefix = (PrefixExpression) arg;
				if (prefix.getOperator() == PrefixExpression.Operator.NOT) {
					// Flip assumeTrue/assumeFalse and remove negation
					String newMethodName = "assumeTrue".equals(methodName) ? "assumeFalse" : "assumeTrue";
					rewriter.set(mi, MethodInvocation.NAME_PROPERTY, ast.newSimpleName(newMethodName), group);
					
					// Replace the negated condition with its operand
					ListRewrite argsRewrite = rewriter.getListRewrite(mi, MethodInvocation.ARGUMENTS_PROPERTY);
					argsRewrite.replace((ASTNode) arg, rewriter.createCopyTarget(prefix.getOperand()), group);
					
					// Only process the first negated condition found
					break;
				}
			}
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					Assumptions.assumeFalse(condition);
					Assumptions.assumeTrue(condition, "message");
					"""; //$NON-NLS-1$
		}
		return """
				Assumptions.assumeTrue(!condition);
				Assumptions.assumeFalse(!condition, "message");
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "AssumeOptimization"; //$NON-NLS-1$
	}
}
