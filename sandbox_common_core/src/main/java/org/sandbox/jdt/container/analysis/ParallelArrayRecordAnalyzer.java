/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AccessProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/** Discovers local columns grown to the same row count and consumed together in source. */
public final class ParallelArrayRecordAnalyzer {
	private record Growth(SimpleName destination, SimpleName source, IVariableBinding array, IVariableBinding count) { }

	private ParallelArrayRecordAnalyzer() { }

	public static List<ContainerUsageProfile> analyze(CompilationUnit root, Runnable checkCanceled) {
		List<ContainerUsageProfile> profiles= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(Block block) {
				checkCanceled.run();
				List<?> statements= block.statements();
				for (int i= 0; i + 4 < statements.size(); i++) {
					if (i % 128 == 0) {
						checkCanceled.run();
					}
					Growth first= growth((Statement) statements.get(i));
					if (first != null) {
						Growth second= growth((Statement) statements.get(i + 1));
						if (second != null && !first.array().isEqualTo(second.array()) && first.count().isEqualTo(second.count())) {
							profile(root, first, second, statements.subList(i + 2, i + 5), checkCanceled).ifPresent(profiles::add);
						}
					}
				}
				return true;
			}
		});
		return List.copyOf(profiles);
	}

	private static Growth growth(Statement statement) {
		Assignment assignment= ContainerAstFacts.assignment(statement);
		if (assignment == null || assignment.getOperator() != Assignment.Operator.ASSIGN
				|| !(assignment.getLeftHandSide() instanceof SimpleName name)
				|| !(assignment.getRightHandSide() instanceof MethodInvocation copy) || copy.arguments().size() != 2
				|| !"copyOf".equals(copy.getName().getIdentifier()) //$NON-NLS-1$
				|| !(copy.arguments().get(0) instanceof SimpleName source)
				|| !(copy.arguments().get(1) instanceof InfixExpression size) || size.hasExtendedOperands()
				|| size.getOperator() != InfixExpression.Operator.PLUS || !ContainerAstFacts.isOne(size.getRightOperand())) {
			return null;
		}
		var method= copy.resolveMethodBinding();
		IVariableBinding array= local(name);
		IVariableBinding count= local(size.getLeftOperand());
		if (method == null || method.isRecovered() || method.getDeclaringClass() == null
				|| !"java.util.Arrays".equals(method.getDeclaringClass().getQualifiedName()) //$NON-NLS-1$
				|| array == null || !array.isEqualTo(local(source)) || array.getType().getDimensions() != 1
				|| array.getType().getElementType().isPrimitive() || count == null || !"int".equals(count.getType().getName())) { //$NON-NLS-1$
			return null;
		}
		return new Growth(name, source, array, count);
	}

	private static IVariableBinding local(Expression expression) {
		if (expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding variable
				&& !variable.isRecovered() && variable.getKey() != null && !variable.isField()
				&& !variable.isParameter() && variable.getType() != null && !variable.getType().isRecovered()) {
			return variable.getVariableDeclaration();
		}
		return null;
	}

	private static ArrayAccess write(Statement statement, Growth growth) {
		Assignment assignment= ContainerAstFacts.assignment(statement);
		if (assignment != null && assignment.getOperator() == Assignment.Operator.ASSIGN
				&& assignment.getLeftHandSide() instanceof ArrayAccess access && growth.array().isEqualTo(local(access.getArray()))
				&& growth.count().isEqualTo(local(access.getIndex()))) {
			return access;
		}
		return null;
	}

	private static VariableDeclarationFragment emptyArray(CompilationUnit root, IVariableBinding binding) {
		if (root.findDeclaringNode(binding) instanceof VariableDeclarationFragment decl
				&& decl.getInitializer() instanceof ArrayCreation init && init.dimensions().size() == 1
				&& init.resolveTypeBinding() != null && binding.getType().isEqualTo(init.resolveTypeBinding())
				&& Integer.valueOf(0).equals(((Expression) init.dimensions().get(0)).resolveConstantExpressionValue())) {
			return decl;
		}
		return null;
	}

	private static Optional<ContainerUsageProfile> profile(CompilationUnit root, Growth first, Growth second,
			List<?> tail, Runnable checkCanceled) {
		ArrayAccess firstWrite= write((Statement) tail.get(0), first);
		ArrayAccess secondWrite= write((Statement) tail.get(1), second);
		VariableDeclarationFragment left= emptyArray(root, first.array());
		VariableDeclarationFragment right= emptyArray(root, second.array());
		if (firstWrite == null || secondWrite == null || left == null || right == null
				|| !(root.findDeclaringNode(first.count()) instanceof VariableDeclarationFragment count) || count.getInitializer() == null
				|| !Integer.valueOf(0).equals(count.getInitializer().resolveConstantExpressionValue())
				|| !(left.getParent().getParent() instanceof Block scope) || right.getParent().getParent() != scope
				|| count.getParent().getParent() != scope || !(tail.get(2) instanceof ExpressionStatement finish)
				|| !(finish.getExpression() instanceof PostfixExpression increment) || increment.getOperator() != PostfixExpression.Operator.INCREMENT
				|| !first.count().isEqualTo(local(increment.getOperand()))) {
			return Optional.empty();
		}
		Set<ASTNode> admitted= new HashSet<>(List.of(left.getName(), right.getName(), first.destination(), first.source(),
				second.destination(), second.source(), firstWrite.getArray(), secondWrite.getArray()));
		List<UsageEvidence> evidence= new ArrayList<>();
		evidence.add(evidence(Kind.PAIRED_ARRAY_WRITE, firstWrite));
		evidence.add(evidence(Kind.PAIRED_ARRAY_WRITE, secondWrite));
		class Uses extends ASTVisitor {
			boolean closed= true;
			@Override
			public boolean visit(Block block) {
				checkCanceled.run();
				return closed;
			}
			@Override
			public boolean visit(MethodInvocation invocation) {
				if (invocation.arguments().size() == 2 && invocation.arguments().get(0) instanceof ArrayAccess a
						&& invocation.arguments().get(1) instanceof ArrayAccess b && first.array().isEqualTo(local(a.getArray()))
						&& second.array().isEqualTo(local(b.getArray())) && sameLocalIndex(a, b)
						&& invocation.resolveMethodBinding() != null && !invocation.resolveMethodBinding().isRecovered()) {
					admitted.add(a.getArray());
					admitted.add(b.getArray());
					evidence.add(evidence(Kind.PAIRED_ARRAY_READ, a));
					evidence.add(evidence(Kind.PAIRED_ARRAY_READ, b));
				}
				return closed;
			}
			@Override
			public boolean visit(SimpleName use) {
				if (!admitted.contains(use) && use.resolveBinding() instanceof IVariableBinding binding) {
					if (first.array().isEqualTo(binding) || second.array().isEqualTo(binding)) {
						closed= false;
					} else if (first.count().isEqualTo(binding) && use != increment.getOperand() && writes(use)) {
						closed= false;
					}
				}
				return closed;
			}
		}
		Uses uses= new Uses();
		scope.accept(uses);
		if (!uses.closed || evidence.size() <= 2 || hasErrors(root, scope)) {
			return Optional.empty();
		}
		evidence.sort(Comparator.comparingInt(UsageEvidence::sourceStart));
		var identity= new ContainerIdentity(first.array().getKey() + "|" + second.array().getKey(), //$NON-NLS-1$
				first.array().getName() + ", " + second.array().getName(), firstWrite.getStartPosition(), firstWrite.getLength()); //$NON-NLS-1$
		return Optional.of(new ContainerUsageProfile(identity, ContainerShape.ARRAY, ElementDomain.REFERENCE,
				new AccessProfile(true, true, true, false, false, false, false), OrderRequirement.POSITIONAL,
				UniquenessRequirement.DUPLICATES_ALLOWED, MutationLifecycle.UNKNOWN, NullContract.ALLOWED,
				AliasingContract.NO_OBSERVED_ALIAS, EscapeLevel.LOCAL, ConcurrencyProfile.unknown(), AnalysisCompleteness.LOCAL_SEED, evidence));
	}

	private static boolean hasErrors(CompilationUnit root, Block scope) {
		int start= scope.getStartPosition();
		int end= start + scope.getLength();
		for (var problem : root.getProblems()) {
			if (problem.isError() && problem.getSourceStart() < end && problem.getSourceEnd() >= start) {
				return true;
			}
		}
		return false;
	}

	private static boolean sameLocalIndex(ArrayAccess left, ArrayAccess right) {
		IVariableBinding index= local(left.getIndex());
		return index != null && index.isEqualTo(local(right.getIndex()));
	}

	private static boolean writes(SimpleName name) {
		ASTNode operand= name;
		while (operand.getParent() instanceof ParenthesizedExpression parentheses) {
			operand= parentheses;
		}
		ASTNode parent= operand.getParent();
		return parent instanceof Assignment assignment && assignment.getLeftHandSide() == operand
				|| parent instanceof PostfixExpression || parent instanceof PrefixExpression prefix
				&& (prefix.getOperator() == PrefixExpression.Operator.INCREMENT || prefix.getOperator() == PrefixExpression.Operator.DECREMENT);
	}

	private static UsageEvidence evidence(Kind kind, ArrayAccess access) {
		return new UsageEvidence(kind, kind == Kind.PAIRED_ARRAY_READ ? "Read a component at the shared row index." //$NON-NLS-1$
				: "Write a component at the shared append index.", access.getStartPosition(), access.getLength()); //$NON-NLS-1$
	}

	static Optional<ContainerRecommendation> recommendation(ContainerUsageProfile profile) {
		if (profile.currentShape() != ContainerShape.ARRAY || profile.completeness() != AnalysisCompleteness.LOCAL_SEED
				|| profile.evidence().stream().noneMatch(item -> item.kind() == Kind.PAIRED_ARRAY_READ)
				|| profile.evidence().stream().noneMatch(item -> item.kind() == Kind.PAIRED_ARRAY_WRITE)) {
			return Optional.empty();
		}
		var target= new TargetContainerContract(ContainerShape.RECORD_SEQUENCE, OrderRequirement.POSITIONAL,
				UniquenessRequirement.DUPLICATES_ALLOWED, Mutability.MUTABLE, NullContract.ALLOWED,
				"Store the paired components as an ordered sequence of records, one record per shared index."); //$NON-NLS-1$
		return Optional.of(new ContainerRecommendation(profile, target,
				ContainerRuleRegistry.find(ContainerRuleRegistry.PARALLEL_ARRAY_RECORD_SEQUENCE).orElseThrow(), Confidence.MEDIUM,
				AutomationLevel.REPORT_ONLY, List.of(
					new ContractAssessment(ContractProperty.ORDER, Preservation.PRESERVED, "Keep rows in their original indexed order, including duplicates."), //$NON-NLS-1$
					new ContractAssessment(ContractProperty.NULLS, Preservation.PRESERVED, "Reference components can retain their individual null values."), //$NON-NLS-1$
					new ContractAssessment(ContractProperty.MUTABILITY, Preservation.CHANGED, "Record components are final; individual array slots were writable."), //$NON-NLS-1$
					new ContractAssessment(ContractProperty.ALIASING, Preservation.REQUIRES_PROOF, "Retain component object identity and evaluation order, including partial writes if a later component throws.")))); //$NON-NLS-1$
	}
}
