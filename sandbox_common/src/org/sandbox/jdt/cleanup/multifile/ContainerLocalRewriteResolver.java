/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;

/** Binding-based, fail-closed resolution of a local container rewrite plan. */
final class ContainerLocalRewriteResolver {

	private static final String PLUGIN_ID= "sandbox_common"; //$NON-NLS-1$

	private ContainerLocalRewriteResolver() {
	}

	static ResolvedPlan resolve(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ContainerLocalRewritePlan plan) throws CoreException {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
		if (!plan.compilationUnitHandle().equals(unit.getHandleIdentifier())) {
			throw stale(unit, "compilation-unit handle changed"); //$NON-NLS-1$
		}

		CollectedAst collected= collectAst(unit, root, plan.bindingKey());
		VariableDeclarationFragment fragment= collected.declaration();
		if (fragment == null
				|| !(fragment.getParent() instanceof VariableDeclarationStatement statement)
				|| statement.fragments().size() != 1
				|| !(statement.getType() instanceof ArrayType arrayType)
				|| arrayType.dimensions().size() != 1
				|| !(fragment.getInitializer() instanceof ArrayCreation initializer)
				|| !isEmptyOneDimensionalArray(initializer)) {
			throw stale(unit, "local array declaration or empty initializer changed"); //$NON-NLS-1$
		}

		List<Assignment> growthAssignments= collected.assignments().stream()
				.filter(assignment -> isGrowthAssignment(assignment, plan.bindingKey()))
				.sorted(Comparator.comparingInt(ASTNode::getStartPosition))
				.toList();
		List<Assignment> appendAssignments= collected.assignments().stream()
				.filter(assignment -> isTailAppendAssignment(assignment, plan.bindingKey()))
				.sorted(Comparator.comparingInt(ASTNode::getStartPosition))
				.toList();
		validateOccurrenceCounts(unit, plan, growthAssignments, appendAssignments);

		List<AppendPair> pairs= pairAppends(unit, growthAssignments, appendAssignments);
		Set<Assignment> recognisedAssignments=
				java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (AppendPair pair : pairs) {
			recognisedAssignments.add(pair.growthAssignment());
			recognisedAssignments.add(pair.appendAssignment());
		}

		Set<Expression> lengthExpressions=
				java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (SimpleName reference : collected.references()) {
			if (fragment.getName() == reference) {
				continue;
			}
			Assignment containingAssignment= containingAssignment(reference);
			if (containingAssignment != null && recognisedAssignments.contains(containingAssignment)) {
				continue;
			}
			ResolvedLength length= lengthRead(reference, plan.bindingKey());
			if (length != null) {
				lengthExpressions.add(length.expression());
				continue;
			}
			if (isEnhancedForExpression(reference, plan.bindingKey())) {
				continue;
			}
			throw stale(unit, "unexpected use of local array binding at source offset " //$NON-NLS-1$
					+ reference.getStartPosition());
		}
		if (lengthExpressions.size() != editCount(plan, EditKind.REPLACE_LENGTH_WITH_SIZE)) {
			throw stale(unit, "array length occurrence count changed"); //$NON-NLS-1$
		}

		List<ResolvedLength> lengths= new ArrayList<>();
		for (Expression expression : lengthExpressions) {
			ResolvedLength length= resolvedLength(expression, plan.bindingKey());
			if (length == null) {
				throw stale(unit, "array length expression could not be re-resolved"); //$NON-NLS-1$
			}
			lengths.add(length);
		}
		lengths.sort(Comparator.comparingInt(length -> length.expression().getStartPosition()));
		return new ResolvedPlan(
				plan,
				statement,
				fragment,
				arrayType,
				initializer,
				pairs,
				lengths);
	}

	private static CollectedAst collectAst(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			String bindingKey) throws CoreException {
		List<SimpleName> references= new ArrayList<>();
		List<Assignment> assignments= new ArrayList<>();
		VariableDeclarationFragment[] declaration= { null };
		try {
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(SimpleName node) {
					IVariableBinding binding= variableBinding(node.resolveBinding());
					if (binding == null
							|| !bindingKey.equals(binding.getVariableDeclaration().getKey())) {
						return true;
					}
					references.add(node);
					if (node.getParent() instanceof VariableDeclarationFragment fragment
							&& fragment.getName() == node) {
						if (declaration[0] != null && declaration[0] != fragment) {
							throw new StalePlanRuntimeException(stale(
									unit, "multiple declarations match the planned binding")); //$NON-NLS-1$
						}
						declaration[0]= fragment;
					}
					return true;
				}

				@Override
				public boolean visit(Assignment node) {
					assignments.add(node);
					return true;
				}
			});
		} catch (StalePlanRuntimeException exception) {
			throw exception.coreException();
		}
		return new CollectedAst(declaration[0], references, assignments);
	}

	private static void validateOccurrenceCounts(
			ICompilationUnit unit,
			ContainerLocalRewritePlan plan,
			List<Assignment> growthAssignments,
			List<Assignment> appendAssignments) throws CoreException {
		int expectedGrowth= editCount(plan, EditKind.REMOVE_ARRAY_GROWTH);
		int expectedAppend= editCount(plan, EditKind.REPLACE_TAIL_WRITE_WITH_ADD);
		if (growthAssignments.size() != expectedGrowth
				|| appendAssignments.size() != expectedAppend
				|| growthAssignments.size() != appendAssignments.size()) {
			throw stale(unit, "array growth or append occurrence count changed"); //$NON-NLS-1$
		}
	}

	private static List<AppendPair> pairAppends(
			ICompilationUnit unit,
			List<Assignment> growthAssignments,
			List<Assignment> appendAssignments) throws CoreException {
		List<AppendPair> pairs= new ArrayList<>();
		for (int index= 0; index < growthAssignments.size(); index++) {
			Assignment growth= growthAssignments.get(index);
			Assignment append= appendAssignments.get(index);
			ExpressionStatement growthStatement= expressionStatement(growth);
			ExpressionStatement appendStatement= expressionStatement(append);
			if (growthStatement == null || appendStatement == null
					|| nextStatement(growthStatement) != appendStatement) {
				throw stale(unit, "array growth is no longer immediately followed by its tail write"); //$NON-NLS-1$
			}
			ArrayAccess access= (ArrayAccess) unwrap(append.getLeftHandSide());
			pairs.add(new AppendPair(
					growth,
					growthStatement,
					append,
					access,
					append.getRightHandSide()));
		}
		return List.copyOf(pairs);
	}

	static record ResolvedPlan(
			ContainerLocalRewritePlan plan,
			VariableDeclarationStatement declaration,
			VariableDeclarationFragment fragment,
			ArrayType arrayType,
			ArrayCreation initializer,
			List<AppendPair> appendPairs,
			List<ResolvedLength> lengths) {

		ResolvedPlan {
			appendPairs= List.copyOf(appendPairs);
			lengths= List.copyOf(lengths);
		}
	}

	static record AppendPair(
			Assignment growthAssignment,
			ExpressionStatement growthStatement,
			Assignment appendAssignment,
			ArrayAccess arrayAccess,
			Expression value) {
	}

	static record ResolvedLength(Expression expression, Expression arrayExpression) {
	}

	private record CollectedAst(
			VariableDeclarationFragment declaration,
			List<SimpleName> references,
			List<Assignment> assignments) {

		CollectedAst {
			references= List.copyOf(references);
			assignments= List.copyOf(assignments);
		}
	}

	private static boolean isEmptyOneDimensionalArray(ArrayCreation creation) {
		return creation.getType().dimensions().size() == 1
				&& creation.getInitializer() == null
				&& creation.dimensions().size() == 1
				&& creation.dimensions().get(0) instanceof NumberLiteral literal
				&& "0".equals(literal.getToken()); //$NON-NLS-1$
	}

	private static boolean isGrowthAssignment(Assignment assignment, String bindingKey) {
		if (assignment.getOperator() != Assignment.Operator.ASSIGN
				|| !sameVariable(assignment.getLeftHandSide(), bindingKey)) {
			return false;
		}
		Expression right= unwrap(assignment.getRightHandSide());
		if (!(right instanceof MethodInvocation invocation)
				|| !isArraysCopyOf(invocation)
				|| invocation.arguments().size() != 2
				|| !sameVariable((Expression) invocation.arguments().get(0), bindingKey)) {
			return false;
		}
		return isLengthPlusOne((Expression) invocation.arguments().get(1), bindingKey);
	}

	private static boolean isTailAppendAssignment(Assignment assignment, String bindingKey) {
		if (assignment.getOperator() != Assignment.Operator.ASSIGN
				|| !(unwrap(assignment.getLeftHandSide()) instanceof ArrayAccess access)
				|| !sameVariable(access.getArray(), bindingKey)) {
			return false;
		}
		return isLengthMinusOne(access.getIndex(), bindingKey);
	}

	private static boolean isArraysCopyOf(MethodInvocation invocation) {
		if (!"copyOf".equals(invocation.getName().getIdentifier())) { //$NON-NLS-1$
			return false;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		return binding != null
				&& binding.getDeclaringClass() != null
				&& "java.util.Arrays".equals( //$NON-NLS-1$
						binding.getDeclaringClass().getErasure().getQualifiedName());
	}

	private static boolean isLengthPlusOne(Expression expression, String bindingKey) {
		Expression unwrapped= unwrap(expression);
		if (!(unwrapped instanceof InfixExpression infix)
				|| infix.getOperator() != InfixExpression.Operator.PLUS
				|| !infix.extendedOperands().isEmpty()) {
			return false;
		}
		return isArrayLength(infix.getLeftOperand(), bindingKey) && isOne(infix.getRightOperand())
				|| isOne(infix.getLeftOperand()) && isArrayLength(infix.getRightOperand(), bindingKey);
	}

	private static boolean isLengthMinusOne(Expression expression, String bindingKey) {
		Expression unwrapped= unwrap(expression);
		return unwrapped instanceof InfixExpression infix
				&& infix.getOperator() == InfixExpression.Operator.MINUS
				&& infix.extendedOperands().isEmpty()
				&& isArrayLength(infix.getLeftOperand(), bindingKey)
				&& isOne(infix.getRightOperand());
	}

	private static boolean isArrayLength(Expression expression, String bindingKey) {
		return resolvedLength(unwrap(expression), bindingKey) != null;
	}

	private static ResolvedLength lengthRead(SimpleName reference, String bindingKey) {
		ASTNode parent= reference.getParent();
		if (parent instanceof QualifiedName qualified
				&& qualified.getQualifier() == reference
				&& "length".equals(qualified.getName().getIdentifier()) //$NON-NLS-1$
				&& sameVariable(reference, bindingKey)) {
			return new ResolvedLength(qualified, reference);
		}
		if (parent instanceof FieldAccess fieldAccess
				&& fieldAccess.getExpression() == reference
				&& "length".equals(fieldAccess.getName().getIdentifier()) //$NON-NLS-1$
				&& sameVariable(reference, bindingKey)) {
			return new ResolvedLength(fieldAccess, reference);
		}
		return null;
	}

	private static ResolvedLength resolvedLength(Expression expression, String bindingKey) {
		if (expression instanceof QualifiedName qualified
				&& "length".equals(qualified.getName().getIdentifier())) { //$NON-NLS-1$
			Expression array= (Expression) qualified.getQualifier();
			return sameVariable(array, bindingKey)
					? new ResolvedLength(qualified, array)
					: null;
		}
		if (expression instanceof FieldAccess fieldAccess
				&& "length".equals(fieldAccess.getName().getIdentifier()) //$NON-NLS-1$
				&& fieldAccess.getExpression() != null
				&& sameVariable(fieldAccess.getExpression(), bindingKey)) {
			return new ResolvedLength(fieldAccess, fieldAccess.getExpression());
		}
		return null;
	}

	private static boolean isEnhancedForExpression(SimpleName reference, String bindingKey) {
		return reference.getParent() instanceof EnhancedForStatement enhanced
				&& enhanced.getExpression() == reference
				&& sameVariable(reference, bindingKey);
	}

	private static Assignment containingAssignment(ASTNode node) {
		ASTNode current= node;
		while (current != null && !(current instanceof Statement)) {
			if (current instanceof Assignment assignment) {
				return assignment;
			}
			current= current.getParent();
		}
		return null;
	}

	private static ExpressionStatement expressionStatement(Assignment assignment) {
		ASTNode current= assignment;
		while (current.getParent() instanceof ParenthesizedExpression) {
			current= current.getParent();
		}
		return current.getParent() instanceof ExpressionStatement statement ? statement : null;
	}

	private static Statement nextStatement(Statement statement) {
		if (!(statement.getParent() instanceof Block block)) {
			return null;
		}
		List<?> statements= block.statements();
		int index= statements.indexOf(statement);
		return index >= 0 && index + 1 < statements.size()
				? (Statement) statements.get(index + 1)
				: null;
	}

	private static boolean sameVariable(Expression expression, String bindingKey) {
		IVariableBinding binding= variableBinding(unwrap(expression));
		return binding != null
				&& bindingKey.equals(binding.getVariableDeclaration().getKey());
	}

	private static IVariableBinding variableBinding(IBinding binding) {
		return binding instanceof IVariableBinding variable ? variable : null;
	}

	private static IVariableBinding variableBinding(Expression expression) {
		Expression unwrapped= unwrap(expression);
		if (unwrapped instanceof SimpleName name) {
			return variableBinding(name.resolveBinding());
		}
		if (unwrapped instanceof QualifiedName name) {
			return variableBinding(name.resolveBinding());
		}
		if (unwrapped instanceof FieldAccess access) {
			return access.resolveFieldBinding();
		}
		if (unwrapped instanceof SuperFieldAccess access) {
			return access.resolveFieldBinding();
		}
		return null;
	}

	private static Expression unwrap(Expression expression) {
		Expression current= expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current= parenthesized.getExpression();
		}
		return current;
	}

	private static boolean isOne(Expression expression) {
		Expression unwrapped= unwrap(expression);
		return unwrapped instanceof NumberLiteral literal
				&& "1".equals(literal.getToken()); //$NON-NLS-1$
	}

	private static int editCount(ContainerLocalRewritePlan plan, EditKind kind) {
		return Math.toIntExact(plan.edits().stream()
				.filter(edit -> edit.kind() == kind)
				.count());
	}

	private static CoreException stale(ICompilationUnit unit, String detail) {
		return new CoreException(new Status(
				IStatus.ERROR,
				PLUGIN_ID,
				"Container rewrite plan is stale for " + unit.getElementName() + ": " + detail)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static final class StalePlanRuntimeException extends RuntimeException {
		private static final long serialVersionUID= 1L;
		private final CoreException coreException;

		StalePlanRuntimeException(CoreException coreException) {
			this.coreException= coreException;
		}

		CoreException coreException() {
			return coreException;
		}
	}
}
