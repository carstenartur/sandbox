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

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.expressionStatement;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.isArrayLength;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.isOne;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.nextStatement;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.sameVariable;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.unwrap;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.variableBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.Statement;
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
import org.sandbox.jdt.internal.common.ASTProcessor;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Finds local seeds where a reference array is grown by one element and the new tail
 * slot is assigned immediately afterwards.
 *
 * <p>The implementation uses the scoped {@link ASTProcessor} chain deliberately:
 * after a matching {@link Arrays#copyOf(Object[], int)} invocation, the next stage
 * searches only the immediately following statement for the corresponding tail write.</p>
 *
 * <p>This detector deliberately stops at {@link AnalysisCompleteness#LOCAL_SEED}.
 * It does not claim that all array uses are append-only, that aliases do not escape or
 * that method signatures may already be migrated. Those proofs belong to the later
 * usage and multi-file planning stages.</p>
 */
public final class AppendOnlyArraySeedDetector {

	private static final int CURRENT_GROWTH= 0;

	/**
	 * Finds deterministic, source-ordered append-only array seeds.
	 *
	 * @param compilationUnit compilation unit to inspect
	 * @return immutable list of local usage profiles
	 */
	public List<ContainerUsageProfile> findSeeds(CompilationUnit compilationUnit) {
		Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$

		List<ContainerUsageProfile> profiles= new ArrayList<>();
		ReferenceHolder<Integer, GrowthSeed> state= ReferenceHolder.createIndexed();
		ASTProcessor<ReferenceHolder<Integer, GrowthSeed>, Integer, GrowthSeed> processor=
				new ASTProcessor<>(state, new HashSet<>());

		processor
				.callMethodInvocationVisitor(Arrays.class, "copyOf", //$NON-NLS-1$
						AppendOnlyArraySeedDetector::rememberGrowth,
						AppendOnlyArraySeedDetector::followingStatementScope)
				.callArrayAccessVisitor((node, holder) ->
						collectTailWrite((ArrayAccess) node, holder, profiles))
				.build(compilationUnit);

		return List.copyOf(profiles);
	}

	private static boolean rememberGrowth(MethodInvocation copyOf,
			ReferenceHolder<Integer, GrowthSeed> state) {
		state.remove(CURRENT_GROWTH);
		growthSeed(copyOf).ifPresent(seed -> state.put(CURRENT_GROWTH, seed));
		return false;
	}

	private static Optional<GrowthSeed> growthSeed(MethodInvocation copyOf) {
		if (copyOf.arguments().size() != 2) {
			return Optional.empty();
		}

		Assignment growthAssignment= enclosingAssignment(copyOf);
		if (growthAssignment == null
				|| growthAssignment.getOperator() != Assignment.Operator.ASSIGN
				|| unwrap(growthAssignment.getRightHandSide()) != copyOf) {
			return Optional.empty();
		}

		Expression array= unwrap(growthAssignment.getLeftHandSide());
		Expression sourceArray= (Expression) copyOf.arguments().get(0);
		Expression requestedLength= (Expression) copyOf.arguments().get(1);
		if (!sameVariable(array, sourceArray) || !isLengthPlusOne(requestedLength, array)) {
			return Optional.empty();
		}

		Optional<IVariableBinding> binding= variableBinding(array);
		ITypeBinding arrayType= binding.map(IVariableBinding::getType)
				.orElseGet(array::resolveTypeBinding);
		ElementDomain elementDomain= elementDomain(arrayType);
		if (elementDomain == ElementDomain.PRIMITIVE) {
			return Optional.empty();
		}

		return Optional.of(new GrowthSeed(array, growthAssignment, binding, elementDomain));
	}

	private static ASTNode followingStatementScope(ASTNode node) {
		MethodInvocation copyOf= (MethodInvocation) node;
		Assignment assignment= enclosingAssignment(copyOf);
		ExpressionStatement statement= assignment == null ? null : expressionStatement(assignment);
		Statement next= statement == null ? null : nextStatement(statement);
		return next != null ? next : copyOf;
	}

	private static boolean collectTailWrite(ArrayAccess arrayAccess,
			ReferenceHolder<Integer, GrowthSeed> state,
			List<ContainerUsageProfile> profiles) {
		GrowthSeed seed= state.get(CURRENT_GROWTH);
		if (seed == null) {
			return true;
		}

		Assignment appendAssignment= enclosingAssignment(arrayAccess);
		if (appendAssignment == null
				|| unwrap(appendAssignment.getLeftHandSide()) != arrayAccess
				|| !sameVariable(seed.array(), arrayAccess.getArray())
				|| !isLengthMinusOne(arrayAccess.getIndex(), seed.array())) {
			return true;
		}

		profiles.add(createProfile(seed, appendAssignment));
		state.remove(CURRENT_GROWTH);
		return false;
	}

	private static ContainerUsageProfile createProfile(GrowthSeed seed, Assignment appendAssignment) {
		List<UsageEvidence> evidence= new ArrayList<>();
		evidence.add(evidence(Kind.ARRAY_GROWTH,
				"Array capacity is increased by one element", seed.growthAssignment())); //$NON-NLS-1$
		evidence.add(evidence(Kind.APPEND_WRITE,
				"The immediately following write targets the new tail slot", appendAssignment)); //$NON-NLS-1$
		if (seed.elementDomain() == ElementDomain.REFERENCE
				|| seed.elementDomain() == ElementDomain.ENUM) {
			evidence.add(evidence(Kind.REFERENCE_COMPONENT,
					"The resolved array component is a reference type", seed.array())); //$NON-NLS-1$
		} else {
			evidence.add(evidence(Kind.UNRESOLVED_BINDING,
					"The array component type still requires binding validation", seed.array())); //$NON-NLS-1$
		}

		ContainerIdentity identity= new ContainerIdentity(
				seed.binding().map(value -> value.getVariableDeclaration().getKey()).orElse(""), //$NON-NLS-1$
				seed.array().toString(), seed.array().getStartPosition(), seed.array().getLength());

		return new ContainerUsageProfile(
				identity,
				ContainerShape.ARRAY,
				seed.elementDomain(),
				ContainerUsageProfile.AccessProfile.appendOnlyArraySeed(),
				OrderRequirement.UNKNOWN,
				UniquenessRequirement.UNKNOWN,
				MutationLifecycle.UNKNOWN,
				NullContract.UNKNOWN,
				AliasingContract.UNKNOWN,
				EscapeLevel.UNKNOWN,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.LOCAL_SEED,
				evidence);
	}

	private static Assignment enclosingAssignment(ASTNode node) {
		ASTNode current= node;
		while (current.getParent() instanceof ParenthesizedExpression) {
			current= current.getParent();
		}
		return current.getParent() instanceof Assignment assignment ? assignment : null;
	}

	private static UsageEvidence evidence(Kind kind, String summary, ASTNode node) {
		return new UsageEvidence(kind, summary, node.getStartPosition(), node.getLength());
	}

	private static ElementDomain elementDomain(ITypeBinding arrayType) {
		if (arrayType == null || !arrayType.isArray()) {
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

	private record GrowthSeed(
			Expression array,
			Assignment growthAssignment,
			Optional<IVariableBinding> binding,
			ElementDomain elementDomain) {

		private GrowthSeed {
			Objects.requireNonNull(array, "array"); //$NON-NLS-1$
			Objects.requireNonNull(growthAssignment, "growthAssignment"); //$NON-NLS-1$
			Objects.requireNonNull(binding, "binding"); //$NON-NLS-1$
			Objects.requireNonNull(elementDomain, "elementDomain"); //$NON-NLS-1$
		}
	}
}
