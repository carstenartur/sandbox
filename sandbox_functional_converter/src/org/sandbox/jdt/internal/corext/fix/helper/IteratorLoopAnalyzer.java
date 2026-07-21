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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;

/** Analyzes iterator loops for safety and convertibility. */
public class IteratorLoopAnalyzer {

	/**
	 * @param isSafe whether the loop is safe for stream conversion
	 * @param reason reason when unsafe
	 * @param hasRemove whether {@code iterator.remove()} is used
	 * @param hasMultipleNext whether multiple {@code next()} calls are used
	 * @param hasBreak whether a break is present
	 * @param hasLabeledContinue whether a labeled continue is present
	 */
	public record SafetyAnalysis(boolean isSafe, String reason, boolean hasRemove,
			boolean hasMultipleNext, boolean hasBreak, boolean hasLabeledContinue) {

		public static SafetyAnalysis safe() {
			return new SafetyAnalysis(true, null, false, false, false, false);
		}

		public static SafetyAnalysis unsafe(String reason, boolean hasRemove,
				boolean hasMultipleNext, boolean hasBreak, boolean hasLabeledContinue) {
			return new SafetyAnalysis(false, reason, hasRemove, hasMultipleNext, hasBreak, hasLabeledContinue);
		}
	}

	/**
	 * Rejects external state mutation except for one supported reduction expression
	 * that is the final top-level body statement. The rewrite still has to prove a
	 * matching fresh local accumulator declaration before applying that reduction.
	 */
	public SafetyAnalysis analyze(Statement loopBody, String iteratorVarName) {
		IteratorUsageVisitor visitor= new IteratorUsageVisitor(iteratorVarName);
		loopBody.accept(visitor);

		if (visitor.hasRemove) {
			return SafetyAnalysis.unsafe("Iterator.remove() is not supported in stream operations", //$NON-NLS-1$
					true, visitor.hasMultipleNext, visitor.hasBreak, visitor.hasLabeledContinue);
		}
		if (visitor.hasMultipleNext) {
			return SafetyAnalysis.unsafe("Multiple iterator.next() calls detected", //$NON-NLS-1$
					false, true, visitor.hasBreak, visitor.hasLabeledContinue);
		}
		if (visitor.hasLabeledContinue) {
			return SafetyAnalysis.unsafe("Labeled continue statement is not supported", //$NON-NLS-1$
					false, false, visitor.hasBreak, true);
		}
		if (visitor.hasBreak) {
			return SafetyAnalysis.unsafe("break statements in the loop body are not yet supported for stream conversion", //$NON-NLS-1$
					false, false, true, false);
		}
		if (visitor.externalStateMutationCount > 0
				&& !isSingleTerminalReduction(loopBody, visitor.externalStateMutationCount, visitor.externalStateMutation)) {
			return SafetyAnalysis.unsafe("The iterator loop mutates state declared outside its body", //$NON-NLS-1$
					false, false, false, false);
		}
		return SafetyAnalysis.safe();
	}

	private static boolean isSingleTerminalReduction(Statement loopBody, int count, ASTNode mutation) {
		if (count != 1 || mutation == null || !(loopBody instanceof Block block)) {
			return false;
		}
		@SuppressWarnings("unchecked") //$NON-NLS-1$
		List<Statement> statements= block.statements();
		if (statements.isEmpty()) {
			return false;
		}
		ASTNode current= mutation;
		while (current != null && current.getParent() != block) {
			current= current.getParent();
		}
		if (current != statements.get(statements.size() - 1) || !(current instanceof ExpressionStatement expressionStatement)) {
			return false;
		}
		Expression expression= expressionStatement.getExpression();
		if (expression instanceof PostfixExpression) {
			return true;
		}
		if (expression instanceof PrefixExpression prefix) {
			return prefix.getOperator() == PrefixExpression.Operator.INCREMENT
					|| prefix.getOperator() == PrefixExpression.Operator.DECREMENT;
		}
		if (!(expression instanceof Assignment assignment)) {
			return false;
		}
		Assignment.Operator operator= assignment.getOperator();
		if (operator == Assignment.Operator.PLUS_ASSIGN || operator == Assignment.Operator.MINUS_ASSIGN
				|| operator == Assignment.Operator.TIMES_ASSIGN) {
			return true;
		}
		if (operator != Assignment.Operator.ASSIGN) {
			return false;
		}
		Expression right= assignment.getRightHandSide();
		if (right instanceof InfixExpression infix) {
			InfixExpression.Operator infixOperator= infix.getOperator();
			return infixOperator == InfixExpression.Operator.PLUS || infixOperator == InfixExpression.Operator.MINUS
					|| infixOperator == InfixExpression.Operator.TIMES;
		}
		return right instanceof MethodInvocation invocation
				&& ("max".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
						|| "min".equals(invocation.getName().getIdentifier())); //$NON-NLS-1$
	}

	private static final class IteratorUsageVisitor extends ASTVisitor {
		private final String iteratorVarName;
		private final Set<String> variablesDeclaredInBody= new HashSet<>();
		private boolean hasRemove;
		private int nextCallCount;
		private boolean hasMultipleNext;
		private boolean hasBreak;
		private boolean hasLabeledContinue;
		private int externalStateMutationCount;
		private ASTNode externalStateMutation;

		IteratorUsageVisitor(String iteratorVarName) {
			this.iteratorVarName= iteratorVarName;
		}

		@Override
		public boolean visit(VariableDeclarationFragment node) {
			IVariableBinding binding= node.resolveBinding();
			if (binding != null) {
				variablesDeclaredInBody.add(binding.getVariableDeclaration().getKey());
			}
			return true;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			Expression expression= node.getExpression();
			if (expression instanceof SimpleName name && name.getIdentifier().equals(iteratorVarName)) {
				String methodName= node.getName().getIdentifier();
				if ("remove".equals(methodName)) { //$NON-NLS-1$
					hasRemove= true;
				} else if ("next".equals(methodName)) { //$NON-NLS-1$
					nextCallCount++;
					hasMultipleNext= nextCallCount > 1;
				}
			}
			return true;
		}

		@Override
		public boolean visit(Assignment node) {
			recordExternalMutation(node.getLeftHandSide(), node);
			return true;
		}

		@Override
		public boolean visit(PostfixExpression node) {
			recordExternalMutation(node.getOperand(), node);
			return true;
		}

		@Override
		public boolean visit(PrefixExpression node) {
			PrefixExpression.Operator operator= node.getOperator();
			if (operator == PrefixExpression.Operator.INCREMENT || operator == PrefixExpression.Operator.DECREMENT) {
				recordExternalMutation(node.getOperand(), node);
			}
			return true;
		}

		private void recordExternalMutation(Expression expression, ASTNode mutation) {
			if (mutatesExternalState(expression)) {
				externalStateMutationCount++;
				externalStateMutation= mutation;
			}
		}

		private boolean mutatesExternalState(Expression expression) {
			if (expression instanceof ArrayAccess) {
				return true;
			}
			IVariableBinding binding= variableBinding(expression);
			if (binding == null) {
				return true;
			}
			IVariableBinding declaration= binding.getVariableDeclaration();
			return declaration.isField() || !variablesDeclaredInBody.contains(declaration.getKey());
		}

		private IVariableBinding variableBinding(Expression expression) {
			IBinding binding= null;
			if (expression instanceof SimpleName name) {
				binding= name.resolveBinding();
			} else if (expression instanceof FieldAccess fieldAccess) {
				binding= fieldAccess.resolveFieldBinding();
			} else if (expression instanceof QualifiedName qualifiedName) {
				binding= qualifiedName.resolveBinding();
			} else if (expression instanceof SuperFieldAccess superFieldAccess) {
				binding= superFieldAccess.resolveFieldBinding();
			}
			return binding instanceof IVariableBinding variableBinding ? variableBinding : null;
		}

		@Override
		public boolean visit(BreakStatement node) {
			hasBreak= true;
			return false;
		}

		@Override
		public boolean visit(ContinueStatement node) {
			if (node.getLabel() != null) {
				hasLabeledContinue= true;
			}
			return false;
		}

		@Override
		public boolean visit(WhileStatement node) {
			return false;
		}

		@Override
		public boolean visit(ForStatement node) {
			return false;
		}

		@Override
		public boolean visit(EnhancedForStatement node) {
			return false;
		}

		@Override
		public boolean visit(DoStatement node) {
			return false;
		}
	}
}
