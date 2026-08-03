/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.assignment;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.expressionStatement;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.isArrayLength;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.isOne;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.nextStatement;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.sameVariable;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.unwrap;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.variableBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
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
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Finds local seeds where a reference array is grown by one element and the new tail
 * slot is assigned immediately afterwards.
 *
 * <p>This detector deliberately stops at {@link AnalysisCompleteness#LOCAL_SEED}.
 * It does not claim that all array uses are append-only, that aliases do not escape or
 * that method signatures may already be migrated. Those proofs belong to the later
 * usage and multi-file planning stages.</p>
 */
public final class AppendOnlyArraySeedDetector {

	/**
	 * Finds deterministic, source-ordered append-only array seeds.
	 *
	 * @param compilationUnit compilation unit to inspect
	 * @return immutable list of local usage profiles
	 */
	public List<ContainerUsageProfile> findSeeds(CompilationUnit compilationUnit) {
		Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$
		ReferenceHolder<Integer, ContainerUsageProfile> profiles= ReferenceHolder.createIndexed();

		AstProcessorBuilder.with(profiles)
				.onAssignment((candidate, holder) -> {
					detectSeed(candidate).ifPresent(profile ->
						holder.put(profile.identity().sourceStart(), profile));
					return true;
				})
				.build(compilationUnit);

		return profiles.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
	}

	private Optional<ContainerUsageProfile> detectSeed(Assignment growthAssignment) {
		if (growthAssignment.getOperator() != Assignment.Operator.ASSIGN) {
			return Optional.empty();
		}

		Expression array= unwrap(growthAssignment.getLeftHandSide());
		MethodInvocation copyOf= methodInvocation(growthAssignment.getRightHandSide());
		if (copyOf == null || !isArraysCopyOf(copyOf) || copyOf.arguments().size() != 2) {
			return Optional.empty();
		}

		Expression sourceArray= (Expression) copyOf.arguments().get(0);
		Expression requestedLength= (Expression) copyOf.arguments().get(1);
		if (!sameVariable(array, sourceArray) || !isLengthPlusOne(requestedLength, array)) {
			return Optional.empty();
		}

		ExpressionStatement growthStatement= expressionStatement(growthAssignment);
		Assignment appendAssignment= growthStatement == null
				? null
				: assignment(nextStatement(growthStatement));
		if (!isTailWrite(appendAssignment, array)) {
			return Optional.empty();
		}

		Optional<IVariableBinding> binding= variableBinding(array);
		ITypeBinding arrayType= binding.map(IVariableBinding::getType)
				.orElseGet(array::resolveTypeBinding);
		ElementDomain elementDomain= elementDomain(arrayType);
		if (elementDomain == ElementDomain.PRIMITIVE) {
			return Optional.empty();
		}

		List<UsageEvidence> evidence= new ArrayList<>();
		evidence.add(evidence(Kind.ARRAY_GROWTH,
				"Array capacity is increased by one element", growthAssignment)); //$NON-NLS-1$
		evidence.add(evidence(Kind.APPEND_WRITE,
				"The immediately following write targets the new tail slot", appendAssignment)); //$NON-NLS-1$
		if (elementDomain == ElementDomain.REFERENCE || elementDomain == ElementDomain.ENUM) {
			evidence.add(evidence(Kind.REFERENCE_COMPONENT,
					"The resolved array component is a reference type", array)); //$NON-NLS-1$
		} else {
			evidence.add(evidence(Kind.UNRESOLVED_BINDING,
					"The array component type still requires binding validation", array)); //$NON-NLS-1$
		}

		ContainerIdentity identity= new ContainerIdentity(
				binding.map(value -> value.getVariableDeclaration().getKey()).orElse(""), //$NON-NLS-1$
				array.toString(), array.getStartPosition(), array.getLength());

		return Optional.of(new ContainerUsageProfile(
				identity,
				ContainerShape.ARRAY,
				elementDomain,
				ContainerUsageProfile.AccessProfile.appendOnlyArraySeed(),
				OrderRequirement.UNKNOWN,
				UniquenessRequirement.UNKNOWN,
				MutationLifecycle.UNKNOWN,
				NullContract.UNKNOWN,
				AliasingContract.UNKNOWN,
				EscapeLevel.UNKNOWN,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.LOCAL_SEED,
				evidence));
	}

	private static UsageEvidence evidence(Kind kind, String summary, ASTNode node) {
		return new UsageEvidence(kind, summary, node.getStartPosition(), node.getLength());
	}

	private static ElementDomain elementDomain(ITypeBinding arrayType) {
		if (arrayType == null) {
			return ElementDomain.UNKNOWN;
		}
		if (!arrayType.isArray()) {
			return ElementDomain.UNKNOWN;
		}
		ITypeBinding componentType= arrayType.getComponentType();
		if (componentType == null) {
			return ElementDomain.UNKNOWN;
		}
		if (componentType.isPrimitive()) {
			return ElementDomain.PRIMITIVE;
		}
		return componentType.isEnum() ? ElementDomain.ENUM : ElementDomain.REFERENCE;
	}

	private static boolean isTailWrite(Assignment appendAssignment, Expression array) {
		if (appendAssignment == null || appendAssignment.getOperator() != Assignment.Operator.ASSIGN) {
			return false;
		}
		Expression leftHandSide= unwrap(appendAssignment.getLeftHandSide());
		if (!(leftHandSide instanceof ArrayAccess arrayAccess)) {
			return false;
		}
		return sameVariable(array, arrayAccess.getArray())
				&& isLengthMinusOne(arrayAccess.getIndex(), array);
	}

	private static MethodInvocation methodInvocation(Expression expression) {
		Expression unwrapped= unwrap(expression);
		return unwrapped instanceof MethodInvocation invocation ? invocation : null;
	}

	private static boolean isArraysCopyOf(MethodInvocation invocation) {
		if (!"copyOf".equals(invocation.getName().getIdentifier())) { //$NON-NLS-1$
			return false;
		}

		IMethodBinding binding= invocation.resolveMethodBinding();
		if (binding != null && binding.getDeclaringClass() != null) {
			return "java.util.Arrays".equals(binding.getDeclaringClass().getErasure().getQualifiedName()); //$NON-NLS-1$
		}

		Expression expression= invocation.getExpression();
		if (expression == null) {
			return false;
		}
		String owner= expression.toString();
		return "Arrays".equals(owner) || "java.util.Arrays".equals(owner); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean isLengthPlusOne(Expression expression, Expression array) {
		Expression unwrapped= unwrap(expression);
		if (!(unwrapped instanceof InfixExpression infix)
				|| infix.getOperator() != InfixExpression.Operator.PLUS
				|| !infix.extendedOperands().isEmpty()) {
			return false;
		}
		return isArrayLength(infix.getLeftOperand(), array) && isOne(infix.getRightOperand())
				|| isOne(infix.getLeftOperand()) && isArrayLength(infix.getRightOperand(), array);
	}

	private static boolean isLengthMinusOne(Expression expression, Expression array) {
		Expression unwrapped= unwrap(expression);
		if (!(unwrapped instanceof InfixExpression infix)
				|| infix.getOperator() != InfixExpression.Operator.MINUS
				|| !infix.extendedOperands().isEmpty()) {
			return false;
		}
		return isArrayLength(infix.getLeftOperand(), array) && isOne(infix.getRightOperand());
	}
}
