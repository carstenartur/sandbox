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
package org.sandbox.jdt.container.analysis;

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.variableBinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationRoot;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.Relationship;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
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
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.internal.common.AstProcessing;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Finds exact, binding-resolved flow roots in compilation units admitted by workspace
 * scope expansion.
 *
 * <p>Only transparent transfers are followed: exact fields and parameters, variables
 * passed as arguments, variables returned by a method, and variables directly
 * initialized or assigned from a method result. More complex expressions remain
 * explicit diagnostics until a dedicated semantic rule can prove them safe.</p>
 */
public final class ContainerFlowContinuationDetector {

	/** Detects continuations using the compilation unit's Java-model handle. */
	public ContainerFlowContinuationPlan detect(
			CompilationUnit compilationUnit,
			ResolvedContainerFlowSearchPlan resolvedPlan) {
		return detect(compilationUnit, compilationUnitHandle(compilationUnit), resolvedPlan);
	}

	/** Detects continuations with an explicit stable compilation-unit handle. */
	public ContainerFlowContinuationPlan detect(
			CompilationUnit compilationUnit,
			String compilationUnitHandle,
			ResolvedContainerFlowSearchPlan resolvedPlan) {
		Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$
		Objects.requireNonNull(resolvedPlan, "resolvedPlan"); //$NON-NLS-1$
		TargetIndex targets= TargetIndex.create(resolvedPlan);
		Accumulator result= new Accumulator(requiredText(
				compilationUnitHandle, "compilationUnitHandle")); //$NON-NLS-1$

		AstProcessing.independent(ReferenceHolder.<String, Object>create())
				.on(SimpleName.class, (name, holder) -> {
					inspectField(name, targets, result);
					return true;
				})
				.on(MethodDeclaration.class, (method, holder) -> {
					inspectMethodDeclaration(method, targets, result);
					return true;
				})
				.on(MethodInvocation.class, (invocation, holder) -> {
					inspectExpressionCall(invocation, invocation.resolveMethodBinding(),
							invocation.arguments(), targets, result);
					return true;
				})
				.on(ClassInstanceCreation.class, (creation, holder) -> {
					inspectExpressionCall(creation, creation.resolveConstructorBinding(),
							creation.arguments(), targets, result);
					return true;
				})
				.on(ConstructorInvocation.class, (invocation, holder) -> {
					inspectStatementCall(invocation, invocation.resolveConstructorBinding(),
							invocation.arguments(), targets, result);
					return true;
				})
				.on(SuperConstructorInvocation.class, (invocation, holder) -> {
					inspectStatementCall(invocation, invocation.resolveConstructorBinding(),
							invocation.arguments(), targets, result);
					return true;
				})
				.on(ExpressionMethodReference.class, (reference, holder) -> {
					inspectMethodReference(reference, reference.resolveMethodBinding(), targets, result);
					return true;
				})
				.on(TypeMethodReference.class, (reference, holder) -> {
					inspectMethodReference(reference, reference.resolveMethodBinding(), targets, result);
					return true;
				})
				.on(SuperMethodReference.class, (reference, holder) -> {
					inspectMethodReference(reference, reference.resolveMethodBinding(), targets, result);
					return true;
				})
				.on(CreationReference.class, (reference, holder) -> {
					inspectMethodReference(reference, reference.resolveMethodBinding(), targets, result);
					return true;
				})
				.build(compilationUnit);

		return result.toPlan();
	}

	private static void inspectField(
			SimpleName name,
			TargetIndex targets,
			Accumulator result) {
		if (!(name.resolveBinding() instanceof IVariableBinding binding)) {
			return;
		}
		IVariableBinding declaration= binding.getVariableDeclaration();
		if (!declaration.isField()) {
			return;
		}
		String handle= javaElementHandle(declaration.getJavaElement());
		for (ResolvedSearchTarget target : targets.fields(handle)) {
			result.addRoot(target, ContinuationKind.FIELD, Relationship.SAME_NODE, null,
					declaration, name,
					"Continue container flow from the exact field binding."); //$NON-NLS-1$
		}
	}

	private static void inspectMethodDeclaration(
			MethodDeclaration method,
			TargetIndex targets,
			Accumulator result) {
		String handle= javaElementHandle(method.resolveBinding());
		for (ResolvedSearchTarget target : targets.methods(handle)) {
			if (target.searchKind() == SearchKind.METHOD_CALLERS) {
				continue;
			}
			if (target.signatureIndex() >= 0) {
				inspectParameter(method, target, result);
			} else {
				inspectReturns(method, target, result);
			}
		}
	}

	private static void inspectParameter(
			MethodDeclaration method,
			ResolvedSearchTarget target,
			Accumulator result) {
		int index= target.signatureIndex();
		if (index < 0 || index >= method.parameters().size()) {
			result.addDiagnostic(DiagnosticKind.INVALID_SIGNATURE_INDEX, target, method,
					"The resolved method does not contain parameter index " + index + '.'); //$NON-NLS-1$
			return;
		}
		SingleVariableDeclaration parameter=
				(SingleVariableDeclaration) method.parameters().get(index);
		IVariableBinding binding= parameter.resolveBinding();
		if (binding == null) {
			result.addDiagnostic(DiagnosticKind.UNRESOLVED_BINDING, target, parameter,
					"The requested method parameter binding could not be resolved."); //$NON-NLS-1$
			return;
		}
		result.addRoot(target, ContinuationKind.PARAMETER_DECLARATION,
				Relationship.SAME_NODE, null, binding.getVariableDeclaration(),
				parameter.getName(),
				"Continue container flow from the exact method parameter position."); //$NON-NLS-1$
	}

	private static void inspectReturns(
			MethodDeclaration method,
			ResolvedSearchTarget target,
			Accumulator result) {
		if (method.getBody() == null) {
			return;
		}
		AstProcessing.independent(ReferenceHolder.<String, Object>create())
				.on(ReturnStatement.class, (statement, holder) -> {
					if (!belongsToMethod(statement, method)) {
						return false;
					}
					Expression expression= statement.getExpression();
					if (expression == null) {
						return false;
					}
					Optional<IVariableBinding> returned= variableBinding(expression);
					if (returned.isEmpty()) {
						result.addDiagnostic(DiagnosticKind.UNSUPPORTED_RETURN_EXPRESSION,
								target, expression,
								"The method returns a non-variable expression that requires " //$NON-NLS-1$
										+ "a dedicated semantic migration rule."); //$NON-NLS-1$
						return false;
					}
					result.addRoot(target, ContinuationKind.RETURN_EXPRESSION,
							Relationship.ROOT_TO_BOUNDARY, EdgeKind.RETURN_TO_METHOD,
							returned.get().getVariableDeclaration(), expression,
							"Continue container flow from a variable returned by the method."); //$NON-NLS-1$
					return false;
				})
				.build(method.getBody());
	}

	private static boolean belongsToMethod(ReturnStatement statement, MethodDeclaration method) {
		ASTNode current= statement.getParent();
		while (current != null) {
			if (current instanceof LambdaExpression) {
				return false;
			}
			if (current instanceof MethodDeclaration declaration) {
				return declaration == method;
			}
			current= current.getParent();
		}
		return false;
	}

	private static void inspectExpressionCall(
			Expression invocation,
			IMethodBinding binding,
			List<?> arguments,
			TargetIndex targets,
			Accumulator result) {
		for (ResolvedSearchTarget target : targets.methods(javaElementHandle(binding))) {
			if (target.searchKind() != SearchKind.METHOD_CALLERS) {
				continue;
			}
			if (target.signatureIndex() >= 0) {
				inspectArguments(invocation, binding, arguments, target, result);
			} else {
				inspectReturnConsumer(invocation, target, result);
			}
		}
	}

	private static void inspectStatementCall(
			ASTNode invocation,
			IMethodBinding binding,
			List<?> arguments,
			TargetIndex targets,
			Accumulator result) {
		for (ResolvedSearchTarget target : targets.methods(javaElementHandle(binding))) {
			if (target.searchKind() == SearchKind.METHOD_CALLERS
					&& target.signatureIndex() >= 0) {
				inspectArguments(invocation, binding, arguments, target, result);
			}
		}
	}

	private static void inspectArguments(
			ASTNode invocation,
			IMethodBinding binding,
			List<?> arguments,
			ResolvedSearchTarget target,
			Accumulator result) {
		if (binding == null) {
			result.addDiagnostic(DiagnosticKind.UNRESOLVED_BINDING, target, invocation,
					"The call binding could not be resolved."); //$NON-NLS-1$
			return;
		}
		boolean matched= false;
		for (int argumentIndex= 0; argumentIndex < arguments.size(); argumentIndex++) {
			if (parameterIndex(binding, argumentIndex) != target.signatureIndex()) {
				continue;
			}
			matched= true;
			Expression argument= (Expression) arguments.get(argumentIndex);
			Optional<IVariableBinding> variable= variableBinding(argument);
			if (variable.isEmpty()) {
				result.addDiagnostic(DiagnosticKind.UNSUPPORTED_ARGUMENT, target, argument,
						"The call passes a non-variable argument that requires a " //$NON-NLS-1$
								+ "dedicated semantic migration rule."); //$NON-NLS-1$
				continue;
			}
			result.addRoot(target, ContinuationKind.CALL_ARGUMENT,
					Relationship.ROOT_TO_BOUNDARY, EdgeKind.ARGUMENT_TO_PARAMETER,
					variable.get().getVariableDeclaration(), argument,
					"Continue container flow from the variable passed to the parameter."); //$NON-NLS-1$
		}
		if (!matched) {
			result.addDiagnostic(DiagnosticKind.INVALID_SIGNATURE_INDEX, target, invocation,
					"The call contains no argument for parameter index " //$NON-NLS-1$
							+ target.signatureIndex() + '.');
		}
	}

	private static void inspectReturnConsumer(
			Expression invocation,
			ResolvedSearchTarget target,
			Accumulator result) {
		Expression complete= transparentParent(invocation);
		ASTNode parent= complete.getParent();
		if (parent instanceof VariableDeclarationFragment fragment
				&& fragment.getInitializer() == complete) {
			IVariableBinding binding= fragment.resolveBinding();
			if (binding == null) {
				result.addDiagnostic(DiagnosticKind.UNRESOLVED_BINDING, target, fragment,
						"The variable receiving the method result could not be resolved."); //$NON-NLS-1$
				return;
			}
			result.addRoot(target, ContinuationKind.RETURN_CONSUMER,
					Relationship.BOUNDARY_TO_ROOT, EdgeKind.INITIALIZER,
					binding.getVariableDeclaration(), fragment.getName(),
					"Continue container flow from the variable initialized by the result."); //$NON-NLS-1$
			return;
		}
		if (parent instanceof Assignment assignment
				&& assignment.getOperator() == Assignment.Operator.ASSIGN
				&& assignment.getRightHandSide() == complete) {
			Optional<IVariableBinding> binding= variableBinding(assignment.getLeftHandSide());
			if (binding.isEmpty()) {
				result.addDiagnostic(DiagnosticKind.UNRESOLVED_BINDING, target, assignment,
						"The assignment target receiving the result could not be resolved."); //$NON-NLS-1$
				return;
			}
			result.addRoot(target, ContinuationKind.RETURN_CONSUMER,
					Relationship.BOUNDARY_TO_ROOT, EdgeKind.ASSIGNMENT,
					binding.get().getVariableDeclaration(), assignment.getLeftHandSide(),
					"Continue container flow from the variable assigned the method result."); //$NON-NLS-1$
			return;
		}
		if (parent instanceof ExpressionStatement) {
			return;
		}
		result.addDiagnostic(DiagnosticKind.UNSUPPORTED_RETURN_CONSUMER, target, complete,
				"The method result is consumed by an unsupported expression shape."); //$NON-NLS-1$
	}

	private static void inspectMethodReference(
			ASTNode reference,
			IMethodBinding binding,
			TargetIndex targets,
			Accumulator result) {
		for (ResolvedSearchTarget target : targets.methods(javaElementHandle(binding))) {
			result.addDiagnostic(DiagnosticKind.METHOD_REFERENCE, target, reference,
					"A method reference participates in the migrated signature and " //$NON-NLS-1$
							+ "requires dedicated target-type analysis."); //$NON-NLS-1$
		}
	}

	private static int parameterIndex(IMethodBinding method, int argumentIndex) {
		int parameterCount= method.getParameterTypes().length;
		if (method.isVarargs() && parameterCount > 0 && argumentIndex >= parameterCount - 1) {
			return parameterCount - 1;
		}
		return argumentIndex;
	}

	private static Expression transparentParent(Expression expression) {
		Expression current= expression;
		while (current.getParent() instanceof ParenthesizedExpression parenthesized) {
			current= parenthesized;
		}
		return current;
	}

	private static String compilationUnitHandle(CompilationUnit compilationUnit) {
		IJavaElement element= compilationUnit.getJavaElement();
		return element == null
				? "in-memory-compilation-unit" //$NON-NLS-1$
				: requiredText(element.getHandleIdentifier(), "compilationUnitHandle"); //$NON-NLS-1$
	}

	private static String javaElementHandle(IMethodBinding binding) {
		return binding == null
				? "" //$NON-NLS-1$
				: javaElementHandle(binding.getMethodDeclaration().getJavaElement());
	}

	private static String javaElementHandle(IJavaElement element) {
		return element == null || element.getHandleIdentifier() == null
				? "" //$NON-NLS-1$
				: element.getHandleIdentifier();
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}

	private static final class TargetIndex {

		private final Map<String, List<ResolvedSearchTarget>> fieldsByHandle;
		private final Map<String, List<ResolvedSearchTarget>> methodsByHandle;

		private TargetIndex(
				Map<String, List<ResolvedSearchTarget>> fieldsByHandle,
				Map<String, List<ResolvedSearchTarget>> methodsByHandle) {
			this.fieldsByHandle= fieldsByHandle;
			this.methodsByHandle= methodsByHandle;
		}

		static TargetIndex create(ResolvedContainerFlowSearchPlan plan) {
			Map<String, List<ResolvedSearchTarget>> fields= new LinkedHashMap<>();
			Map<String, List<ResolvedSearchTarget>> methods= new LinkedHashMap<>();
			for (ResolvedSearchTarget target : plan.targets()) {
				Map<String, List<ResolvedSearchTarget>> selected=
						target.targetKind() == TargetKind.FIELD ? fields : methods;
				selected.computeIfAbsent(target.javaElementHandle(), ignored -> new ArrayList<>())
						.add(target);
			}
			return new TargetIndex(immutableLists(fields), immutableLists(methods));
		}

		List<ResolvedSearchTarget> fields(String handle) {
			return fieldsByHandle.getOrDefault(handle, List.of());
		}

		List<ResolvedSearchTarget> methods(String handle) {
			return methodsByHandle.getOrDefault(handle, List.of());
		}

		private static Map<String, List<ResolvedSearchTarget>> immutableLists(
				Map<String, List<ResolvedSearchTarget>> source) {
			Map<String, List<ResolvedSearchTarget>> result= new LinkedHashMap<>();
			source.forEach((key, value) -> result.put(key, List.copyOf(value)));
			return Map.copyOf(result);
		}
	}

	private static final class Accumulator {

		private final String compilationUnitHandle;
		private final Map<String, ContinuationRoot> rootsByKey= new LinkedHashMap<>();
		private final Map<String, ContinuationDiagnostic> diagnosticsByKey=
				new LinkedHashMap<>();

		Accumulator(String compilationUnitHandle) {
			this.compilationUnitHandle= compilationUnitHandle;
		}

		void addRoot(
				ResolvedSearchTarget target,
				ContinuationKind kind,
				Relationship relationship,
				EdgeKind transferKind,
				IVariableBinding binding,
				ASTNode anchor,
				String summary) {
			IVariableBinding declaration= binding.getVariableDeclaration();
			ITypeBinding type= declaration.getType();
			if (type == null || !type.isArray()) {
				addDiagnostic(DiagnosticKind.NON_ARRAY_VALUE, target, anchor,
						"The resolved continuation value is not an array."); //$NON-NLS-1$
				return;
			}
			String bindingKey= declaration.getKey();
			if (bindingKey == null || bindingKey.isBlank()) {
				addDiagnostic(DiagnosticKind.UNRESOLVED_BINDING, target, anchor,
						"The continuation variable has no stable binding key."); //$NON-NLS-1$
				return;
			}

			ContinuationRoot root= new ContinuationRoot(
					target.sourceNodeId(), kind, relationship, transferKind,
					compilationUnitHandle, target.javaElementHandle(),
					profile(declaration, anchor, summary));
			rootsByKey.putIfAbsent(root.stableKey(), root);
		}

		void addDiagnostic(
				DiagnosticKind kind,
				ResolvedSearchTarget target,
				ASTNode node,
				String message) {
			ContinuationDiagnostic diagnostic= new ContinuationDiagnostic(
					kind, compilationUnitHandle, target.javaElementHandle(), message,
					node.getStartPosition(), node.getLength());
			String key= kind + "|" + target.stableKey() + '|' + node.getStartPosition(); //$NON-NLS-1$
			diagnosticsByKey.putIfAbsent(key, diagnostic);
		}

		ContainerFlowContinuationPlan toPlan() {
			List<ContinuationRoot> roots= new ArrayList<>(rootsByKey.values());
			roots.sort(Comparator
					.comparingInt((ContinuationRoot root) -> root.profile().identity().sourceStart())
					.thenComparing(ContinuationRoot::stableKey));
			List<ContinuationDiagnostic> diagnostics=
					new ArrayList<>(diagnosticsByKey.values());
			diagnostics.sort(Comparator
					.comparingInt(ContinuationDiagnostic::sourceStart)
					.thenComparing(ContinuationDiagnostic::message));
			return new ContainerFlowContinuationPlan(roots, diagnostics);
		}

		private static ContainerUsageProfile profile(
				IVariableBinding binding,
				ASTNode anchor,
				String summary) {
			ITypeBinding component= binding.getType().getComponentType();
			ElementDomain domain= component == null
					? ElementDomain.UNKNOWN
					: component.isPrimitive()
							? ElementDomain.PRIMITIVE
							: component.isEnum() ? ElementDomain.ENUM : ElementDomain.REFERENCE;
			EscapeLevel escape= binding.isField()
					? EscapeLevel.FIELD
					: binding.isParameter() ? EscapeLevel.METHOD_BOUNDARY : EscapeLevel.LOCAL;
			return new ContainerUsageProfile(
					new ContainerIdentity(binding.getKey(), binding.getName(),
							anchor.getStartPosition(), anchor.getLength()),
					ContainerShape.ARRAY,
					domain,
					new AccessProfile(false, false, false, false, false, false, false),
					OrderRequirement.UNKNOWN,
					UniquenessRequirement.UNKNOWN,
					MutationLifecycle.UNKNOWN,
					NullContract.UNKNOWN,
					AliasingContract.UNKNOWN,
					escape,
					ConcurrencyProfile.unknown(),
					AnalysisCompleteness.LOCAL_SEED,
					List.of(new UsageEvidence(Kind.FLOW_CONTINUATION_ROOT, summary,
							anchor.getStartPosition(), anchor.getLength())));
		}
	}
}
