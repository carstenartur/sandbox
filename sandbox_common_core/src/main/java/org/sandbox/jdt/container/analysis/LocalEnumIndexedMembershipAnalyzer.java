/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AccessProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/**
 * Proves a narrow local enum-membership table encoded as {@code boolean[]}.
 *
 * <p>The array must be created with exactly {@code SomeEnum.values().length} slots,
 * and every element access must use {@code value.ordinal()} for that same enum type.
 * Reads represent membership queries; literal {@code true}/{@code false} writes
 * represent enable/disable operations. Array length, numeric indexing, aliases,
 * escapes, captures and computed writes reject the profile.</p>
 *
 * <p>The source profile deliberately remains physically accurate: the current
 * container is a primitive {@code boolean[]} with indexed reads/writes. The fact
 * that those positions encode enum membership is retained as explicit evidence and
 * consumed only by the specialized contract inferrer.</p>
 */
public final class LocalEnumIndexedMembershipAnalyzer {

	/** Returns source-ordered complete or rejected local enum-membership profiles. */
	public List<ContainerUsageProfile> analyze(CompilationUnit unit) {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		List<ContainerUsageProfile> result= new ArrayList<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment fragment) {
				candidate(fragment).map(LocalEnumIndexedMembershipAnalyzer::analyzeCandidate)
						.ifPresent(result::add);
				return true;
			}
		});
		result.sort(Comparator.comparingInt(profile -> profile.identity().sourceStart()));
		return List.copyOf(result);
	}

	private static Optional<Candidate> candidate(VariableDeclarationFragment fragment) {
		IVariableBinding binding= fragment.resolveBinding();
		if (binding == null || binding.isField() || binding.isParameter()
				|| !(fragment.getParent() instanceof VariableDeclarationStatement declaration)
				|| declaration.fragments().size() != 1
				|| !(fragment.getInitializer() instanceof ArrayCreation creation)
				|| creation.getInitializer() != null
				|| creation.dimensions().size() != 1
				|| !isBooleanArray(declaration.getType().resolveBinding())
				|| !isBooleanArray(creation.getType().resolveBinding())) {
			return Optional.empty();
		}
		Expression dimension= (Expression) creation.dimensions().get(0);
		ITypeBinding enumType= enumTypeFromValuesLength(dimension);
		MethodDeclaration method= enclosingMethod(fragment);
		String bindingKey= binding.getVariableDeclaration().getKey();
		if (enumType == null || method == null || method.getBody() == null
				|| bindingKey == null || bindingKey.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new Candidate(
				fragment, method, binding.getVariableDeclaration(), bindingKey,
				enumType.getTypeDeclaration(), dimension));
	}

	private static ContainerUsageProfile analyzeCandidate(Candidate candidate) {
		Observations observations= new Observations(candidate);
		candidate.method().getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (hasBinding(name.resolveBinding(), candidate.bindingKey())) {
					classify(name, candidate, observations);
				}
				return true;
			}
		});
		return observations.profile();
	}

	private static void classify(
			SimpleName name,
			Candidate candidate,
			Observations observations) {
		observations.bindingSeen= true;
		if (candidate.fragment().getName() == name) {
			return;
		}
		if (crossesExecutableBoundary(name, candidate.binding())) {
			observations.reject(Kind.CAPTURED_USAGE,
					"Enum membership table is captured across an executable boundary", name); //$NON-NLS-1$
			return;
		}

		Expression reference= completeReferenceExpression(name, candidate.bindingKey());
		ASTNode parent= reference.getParent();
		if (parent instanceof ArrayAccess access && access.getArray() == reference) {
			classifyArrayAccess(access, candidate, observations);
		} else if (isLengthRead(reference, parent)) {
			observations.reject(Kind.ARRAY_LENGTH_READ,
					"Array cardinality is observed; set size would represent enabled members instead", //$NON-NLS-1$
					reference);
		} else if (parent instanceof VariableDeclarationFragment fragment
				&& fragment.getInitializer() == reference) {
			observations.reject(Kind.UNSAFE_ESCAPE,
					"Enum membership table is aliased to another local variable", reference); //$NON-NLS-1$
		} else if (parent instanceof Assignment assignment
				&& assignment.getRightHandSide() == reference) {
			observations.reject(Kind.UNSAFE_ESCAPE,
					"Enum membership table is assigned to another variable or field", reference); //$NON-NLS-1$
		} else if (parent instanceof ReturnStatement) {
			observations.reject(Kind.UNSAFE_ESCAPE,
					"Enum membership table escapes through a return statement", reference); //$NON-NLS-1$
		} else if (parent instanceof MethodInvocation invocation
				&& invocation.arguments().contains(reference)) {
			observations.reject(Kind.UNSAFE_ESCAPE,
					"Enum membership table is passed to another method", reference); //$NON-NLS-1$
		} else if (parent instanceof InfixExpression infix && isIdentityComparison(infix)) {
			observations.reject(Kind.ARRAY_IDENTITY,
					"Array identity is observed by a reference comparison", reference); //$NON-NLS-1$
		} else if (parent instanceof SynchronizedStatement synchronizedStatement
				&& synchronizedStatement.getExpression() == reference) {
			observations.reject(Kind.ARRAY_IDENTITY,
					"Array identity is used as a synchronization monitor", reference); //$NON-NLS-1$
		} else {
			observations.reject(Kind.UNCLASSIFIED_USAGE,
					"Array use is outside the enum-membership contract", reference); //$NON-NLS-1$
		}
	}

	private static void classifyArrayAccess(
			ArrayAccess access,
			Candidate candidate,
			Observations observations) {
		if (!isOrdinalIndex(access.getIndex(), candidate.enumType())) {
			observations.reject(Kind.REJECTION_BOUNDARY,
					"Array index is not ordinal() of the enum that defines table cardinality", //$NON-NLS-1$
					access.getIndex());
			return;
		}
		observations.add(Kind.ENUM_ORDINAL_INDEX,
				"Membership slot is selected by ordinal() of " //$NON-NLS-1$
						+ candidate.enumType().getQualifiedName(),
				access.getIndex());

		ASTNode parent= access.getParent();
		if (parent instanceof Assignment assignment
				&& assignment.getLeftHandSide() == access) {
			if (assignment.getOperator() != Assignment.Operator.ASSIGN
					|| !(unwrap(assignment.getRightHandSide()) instanceof BooleanLiteral literal)) {
				observations.reject(Kind.REJECTION_BOUNDARY,
						"Membership writes must assign literal true or false", assignment); //$NON-NLS-1$
				return;
			}
			observations.write(literal.booleanValue(), assignment);
			return;
		}
		observations.read(access);
	}

	private static ITypeBinding enumTypeFromValuesLength(Expression expression) {
		Expression unwrapped= unwrap(expression);
		Expression receiver;
		if (unwrapped instanceof FieldAccess field
				&& "length".equals(field.getName().getIdentifier())) { //$NON-NLS-1$
			receiver= field.getExpression();
		} else {
			return null;
		}
		Expression valuesExpression= unwrap(receiver);
		if (!(valuesExpression instanceof MethodInvocation values)
				|| !"values".equals(values.getName().getIdentifier()) //$NON-NLS-1$
				|| !values.arguments().isEmpty()) {
			return null;
		}
		IMethodBinding method= values.resolveMethodBinding();
		if (method == null || method.getDeclaringClass() == null
				|| !method.getDeclaringClass().isEnum()) {
			return null;
		}
		ITypeBinding returnType= method.getReturnType();
		if (returnType == null || !returnType.isArray()) {
			return null;
		}
		ITypeBinding component= returnType.getComponentType();
		return component != null && sameType(component, method.getDeclaringClass())
				? component.getTypeDeclaration() : null;
	}

	private static boolean isOrdinalIndex(Expression expression, ITypeBinding enumType) {
		Expression unwrapped= unwrap(expression);
		if (!(unwrapped instanceof MethodInvocation ordinal)
				|| !"ordinal".equals(ordinal.getName().getIdentifier()) //$NON-NLS-1$
				|| !ordinal.arguments().isEmpty()
				|| ordinal.getExpression() == null) {
			return false;
		}
		ITypeBinding receiverType= ordinal.getExpression().resolveTypeBinding();
		IMethodBinding method= ordinal.resolveMethodBinding();
		return receiverType != null && sameType(receiverType, enumType)
				&& method != null && method.getDeclaringClass() != null
				&& "java.lang.Enum".equals( //$NON-NLS-1$
						method.getDeclaringClass().getErasure().getQualifiedName());
	}

	private static boolean isBooleanArray(ITypeBinding type) {
		if (type == null || !type.isArray() || type.getDimensions() != 1) {
			return false;
		}
		ITypeBinding component= type.getComponentType();
		return component != null && component.isPrimitive()
				&& "boolean".equals(component.getName()); //$NON-NLS-1$
	}

	private static boolean sameType(ITypeBinding left, ITypeBinding right) {
		if (left == null || right == null) {
			return false;
		}
		return left.getTypeDeclaration().isEqualTo(right.getTypeDeclaration());
	}

	private static MethodDeclaration enclosingMethod(ASTNode node) {
		for (ASTNode current= node.getParent(); current != null; current= current.getParent()) {
			if (current instanceof MethodDeclaration) {
				return (MethodDeclaration) current;
			}
		}
		return null;
	}

	private static boolean crossesExecutableBoundary(
			ASTNode reference,
			IVariableBinding declaration) {
		IMethodBinding declaringMethod= declaration.getDeclaringMethod();
		if (declaringMethod == null) {
			return true;
		}
		for (ASTNode current= reference.getParent(); current != null; current= current.getParent()) {
			if (current instanceof LambdaExpression
					|| current instanceof AnonymousClassDeclaration
					|| current instanceof AbstractTypeDeclaration) {
				return true;
			}
			if (current instanceof MethodDeclaration method) {
				return !sameMethod(declaringMethod, method.resolveBinding());
			}
		}
		return true;
	}

	private static boolean sameMethod(IMethodBinding left, IMethodBinding right) {
		if (left == null || right == null) {
			return false;
		}
		String leftKey= left.getMethodDeclaration().getKey();
		String rightKey= right.getMethodDeclaration().getKey();
		return leftKey != null && leftKey.equals(rightKey);
	}

	private static boolean hasBinding(IBinding binding, String key) {
		return binding instanceof IVariableBinding variable
				&& key.equals(variable.getVariableDeclaration().getKey());
	}

	private static Expression completeReferenceExpression(SimpleName name, String bindingKey) {
		Expression reference= name;
		ASTNode parent= reference.getParent();
		if (parent instanceof QualifiedName qualified
				&& qualified.getName() == reference
				&& hasBinding(qualified.resolveBinding(), bindingKey)) {
			reference= qualified;
		} else if (parent instanceof FieldAccess field
				&& field.getName() == reference
				&& hasBinding(field.resolveFieldBinding(), bindingKey)) {
			reference= field;
		}
		while (reference.getParent() instanceof ParenthesizedExpression parenthesized) {
			reference= parenthesized;
		}
		return reference;
	}

	private static boolean isLengthRead(Expression reference, ASTNode parent) {
		if (parent instanceof QualifiedName qualified) {
			return qualified.getQualifier() == reference
					&& "length".equals(qualified.getName().getIdentifier()); //$NON-NLS-1$
		}
		return parent instanceof FieldAccess field
				&& field.getExpression() == reference
				&& "length".equals(field.getName().getIdentifier()); //$NON-NLS-1$
	}

	private static boolean isIdentityComparison(InfixExpression expression) {
		return expression.getOperator() == InfixExpression.Operator.EQUALS
				|| expression.getOperator() == InfixExpression.Operator.NOT_EQUALS;
	}

	private static Expression unwrap(Expression expression) {
		Expression current= expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current= parenthesized.getExpression();
		}
		return current;
	}

	private record Candidate(
			VariableDeclarationFragment fragment,
			MethodDeclaration method,
			IVariableBinding binding,
			String bindingKey,
			ITypeBinding enumType,
			Expression cardinalityExpression) {
	}

	private static final class Observations {
		private final Candidate candidate;
		private final List<UsageEvidence> evidence= new ArrayList<>();
		private boolean rejected;
		private boolean bindingSeen;
		private int reads;
		private int writes;

		Observations(Candidate candidate) {
			this.candidate= candidate;
			add(Kind.ENUM_CARDINALITY,
					"Boolean table cardinality is defined by " //$NON-NLS-1$
							+ candidate.enumType().getQualifiedName() + ".values().length", //$NON-NLS-1$
					candidate.cardinalityExpression());
		}

		void read(ASTNode node) {
			reads++;
			add(Kind.ENUM_MEMBERSHIP_QUERY,
					"Boolean table lookup observes membership of one enum constant", node); //$NON-NLS-1$
		}

		void write(boolean enabled, ASTNode node) {
			writes++;
			add(enabled ? Kind.ENUM_MEMBERSHIP_ENABLE : Kind.ENUM_MEMBERSHIP_DISABLE,
					enabled
						? "Literal true enables membership of one enum constant" //$NON-NLS-1$
						: "Literal false disables membership of one enum constant", //$NON-NLS-1$
					node);
		}

		void reject(Kind kind, String summary, ASTNode node) {
			rejected= true;
			add(kind, summary, node);
		}

		void add(Kind kind, String summary, ASTNode node) {
			evidence.add(new UsageEvidence(kind, summary, node.getStartPosition(), node.getLength()));
		}

		ContainerUsageProfile profile() {
			boolean complete= bindingSeen && reads > 0 && writes > 0 && !rejected;
			if (reads == 0) {
				reject(Kind.REJECTION_BOUNDARY,
						"No enum membership query was observed", candidate.fragment()); //$NON-NLS-1$
				complete= false;
			}
			if (writes == 0) {
				reject(Kind.REJECTION_BOUNDARY,
						"No enum membership state write was observed", candidate.fragment()); //$NON-NLS-1$
				complete= false;
			}
			if (complete) {
				add(Kind.LOCAL_USAGE_COMPLETE,
						"Every use of the enum-indexed membership table was classified", //$NON-NLS-1$
						candidate.fragment());
			}
			evidence.sort(Comparator.comparingInt(UsageEvidence::sourceStart)
					.thenComparing(item -> item.kind().ordinal()));
			return new ContainerUsageProfile(
					new ContainerIdentity(
							candidate.bindingKey(), candidate.fragment().getName().getIdentifier(),
							candidate.fragment().getStartPosition(), candidate.fragment().getLength()),
					ContainerShape.ARRAY,
					ElementDomain.PRIMITIVE,
					new AccessProfile(reads > 0, writes > 0, false, false, false, reads > 0, false),
					OrderRequirement.NONE,
					UniquenessRequirement.REQUIRED,
					MutationLifecycle.CONTINUOUSLY_MUTABLE,
					NullContract.NOT_APPLICABLE,
					complete ? AliasingContract.NO_OBSERVED_ALIAS : AliasingContract.UNKNOWN,
					EscapeLevel.LOCAL,
					complete ? threadConfined() : ConcurrencyProfile.unknown(),
					complete ? AnalysisCompleteness.LOCAL_USAGE_COMPLETE
							: AnalysisCompleteness.REJECTED,
					evidence);
		}
	}

	private static ConcurrencyProfile threadConfined() {
		return new ConcurrencyProfile(
				ThreadExposure.THREAD_CONFINED,
				SynchronizationKind.NONE,
				IterationSemantics.UNKNOWN,
				AtomicityRequirement.INDIVIDUAL_OPERATIONS,
				WorkloadShape.UNKNOWN);
	}
}
