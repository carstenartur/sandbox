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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.analysis.ContainerFlowIndex.BindingInfo;
import org.sandbox.jdt.container.analysis.ContainerFlowIndex.MethodInfo;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerUsageProfile;

/**
 * Discovers value-flow edges reachable from one container binding inside a compilation
 * unit.
 *
 * <p>The builder follows local variable aliases to a fixed point. Signature edges are
 * represented even when their declarations are outside the current compilation unit;
 * the closure status and diagnostics then instruct the multi-file planner to expand
 * scope or stop at an external boundary.</p>
 *
 * <p>Index construction and graph accumulation are separate package-private
 * components so this class remains focused on flow rules.</p>
 */
public final class LocalContainerFlowGraphBuilder {

	/** Builds a local flow graph for the supplied candidate profile. */
	public ContainerFlowGraph build(
			CompilationUnit compilationUnit,
			ContainerUsageProfile profile) {
		Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$
		Objects.requireNonNull(profile, "profile"); //$NON-NLS-1$

		ContainerFlowIndex index= ContainerFlowIndex.create(compilationUnit);
		ContainerFlowGraphAccumulator graph=
				new ContainerFlowGraphAccumulator(profile, index.compilationUnitHandle());
		String rootBindingKey= profile.identity().bindingKey();
		if (rootBindingKey.isBlank()) {
			graph.addUnresolvedRoot();
			return graph.toGraph();
		}

		BindingInfo root= index.bindings().get(rootBindingKey);
		if (root == null) {
			graph.addUnresolvedRoot();
			return graph.toGraph();
		}

		Deque<String> pendingBindings= new ArrayDeque<>();
		Set<String> visitedBindings= new LinkedHashSet<>();
		pendingBindings.add(rootBindingKey);
		graph.ensureVariableNode(root);

		while (!pendingBindings.isEmpty()) {
			String bindingKey= pendingBindings.removeFirst();
			if (!visitedBindings.add(bindingKey)) {
				continue;
			}
			BindingInfo source= index.bindings().get(bindingKey);
			if (source == null) {
				graph.unresolvedBinding(
						bindingKey,
						profile.identity().sourceStart(),
						profile.identity().sourceLength());
				continue;
			}

			FlowNode sourceNode= graph.ensureVariableNode(source);
			graph.classifySignatureExposure(source);
			for (SimpleName referenceName :
					index.references().getOrDefault(bindingKey, List.of())) {
				if (isDeclarationName(referenceName)) {
					continue;
				}
				Expression reference= completeReferenceExpression(referenceName, bindingKey);
				classifyReference(reference, sourceNode, index, graph, pendingBindings);
			}
		}

		return graph.toGraph();
	}

	private static void classifyReference(
			Expression reference,
			FlowNode sourceNode,
			ContainerFlowIndex index,
			ContainerFlowGraphAccumulator graph,
			Deque<String> pendingBindings) {
		ASTNode parent= reference.getParent();
		if (parent instanceof VariableDeclarationFragment fragment
				&& fragment.getInitializer() == reference) {
			connectVariable(
					sourceNode,
					fragment.resolveBinding(),
					EdgeKind.INITIALIZER,
					reference,
					index,
					graph,
					pendingBindings);
		} else if (parent instanceof Assignment assignment
				&& assignment.getRightHandSide() == reference) {
			connectExpression(
					sourceNode,
					assignment.getLeftHandSide(),
					EdgeKind.ASSIGNMENT,
					reference,
					index,
					graph,
					pendingBindings);
		} else if (parent instanceof MethodInvocation invocation
				&& invocation.arguments().contains(reference)) {
			if (!isRepresentationInternalCopy(invocation, reference)) {
				connectArguments(
						sourceNode,
						invocation.resolveMethodBinding(),
						invocation.arguments(),
						reference,
						index,
						graph,
						pendingBindings);
			}
		} else if (parent instanceof ClassInstanceCreation creation
				&& creation.arguments().contains(reference)) {
			connectArguments(
					sourceNode,
					creation.resolveConstructorBinding(),
					creation.arguments(),
					reference,
					index,
					graph,
					pendingBindings);
		} else if (parent instanceof ReturnStatement) {
			connectReturn(sourceNode, reference, index, graph);
		} else if (!isInternalNonFlowUse(reference, parent)) {
			graph.unclassifiedFlow(reference);
		}
	}

	private static void connectExpression(
			FlowNode sourceNode,
			Expression targetExpression,
			EdgeKind edgeKind,
			ASTNode evidence,
			ContainerFlowIndex index,
			ContainerFlowGraphAccumulator graph,
			Deque<String> pendingBindings) {
		Optional<IVariableBinding> target= variableBinding(targetExpression);
		if (target.isPresent()) {
			connectVariable(
					sourceNode,
					target.get(),
					edgeKind,
					evidence,
					index,
					graph,
					pendingBindings);
		} else {
			graph.unclassifiedFlow(evidence);
		}
	}

	private static void connectVariable(
			FlowNode sourceNode,
			IVariableBinding targetBinding,
			EdgeKind edgeKind,
			ASTNode evidence,
			ContainerFlowIndex index,
			ContainerFlowGraphAccumulator graph,
			Deque<String> pendingBindings) {
		if (targetBinding == null || targetBinding.getVariableDeclaration().getKey() == null) {
			graph.unresolvedBinding(
					"", evidence.getStartPosition(), evidence.getLength()); //$NON-NLS-1$
			return;
		}
		String targetKey= targetBinding.getVariableDeclaration().getKey();
		BindingInfo target= index.bindings().get(targetKey);
		if (target == null) {
			graph.unresolvedBinding(
					targetKey, evidence.getStartPosition(), evidence.getLength());
			return;
		}
		FlowNode targetNode= graph.ensureVariableNode(target);
		graph.addEdge(sourceNode, targetNode, edgeKind, evidence);
		pendingBindings.addLast(targetKey);
	}

	private static void connectArguments(
			FlowNode sourceNode,
			IMethodBinding invokedMethod,
			List<?> arguments,
			Expression reference,
			ContainerFlowIndex index,
			ContainerFlowGraphAccumulator graph,
			Deque<String> pendingBindings) {
		if (invokedMethod == null) {
			graph.unresolvedBinding(
					"", reference.getStartPosition(), reference.getLength()); //$NON-NLS-1$
			return;
		}
		IMethodBinding declaration= invokedMethod.getMethodDeclaration();
		String methodKey= declaration.getKey();
		for (int argumentIndex= 0; argumentIndex < arguments.size(); argumentIndex++) {
			if (arguments.get(argumentIndex) != reference) {
				continue;
			}
			int parameterIndex= parameterIndex(declaration, argumentIndex);
			MethodInfo localMethod= methodKey == null ? null : index.methods().get(methodKey);
			if (localMethod != null && parameterIndex < localMethod.parameterBindingKeys().size()) {
				String parameterKey= localMethod.parameterBindingKeys().get(parameterIndex);
				BindingInfo parameter= index.bindings().get(parameterKey);
				if (parameter != null) {
					FlowNode parameterNode= graph.ensureVariableNode(parameter);
					graph.addEdge(
							sourceNode,
							parameterNode,
							EdgeKind.ARGUMENT_TO_PARAMETER,
							reference);
					graph.requireScopeExpansion(
							"Method parameter signature participates in the container flow", //$NON-NLS-1$
							reference);
					pendingBindings.addLast(parameterKey);
					continue;
				}
			}
			graph.addParameterBoundary(
					sourceNode,
					declaration,
					parameterIndex,
					reference);
		}
	}

	private static int parameterIndex(IMethodBinding method, int argumentIndex) {
		int parameterCount= method.getParameterTypes().length;
		if (method.isVarargs() && parameterCount > 0 && argumentIndex >= parameterCount - 1) {
			return parameterCount - 1;
		}
		return argumentIndex;
	}

	private static void connectReturn(
			FlowNode sourceNode,
			Expression reference,
			ContainerFlowIndex index,
			ContainerFlowGraphAccumulator graph) {
		MethodDeclaration method= ancestor(reference, MethodDeclaration.class);
		IMethodBinding methodBinding= method == null ? null : method.resolveBinding();
		if (methodBinding == null) {
			graph.unresolvedBinding(
					"", reference.getStartPosition(), reference.getLength()); //$NON-NLS-1$
			return;
		}
		String methodKey= methodBinding.getMethodDeclaration().getKey();
		if (methodKey == null || methodKey.isBlank()) {
			graph.unresolvedBinding(
					"", reference.getStartPosition(), reference.getLength()); //$NON-NLS-1$
			return;
		}
		FlowNode returnNode= graph.ensureReturnNode(
				methodKey,
				index.compilationUnitHandle(),
				reference);
		graph.addEdge(sourceNode, returnNode, EdgeKind.RETURN_TO_METHOD, reference);
		graph.requireScopeExpansion(
				"Method return type and all call sites participate in the container flow", //$NON-NLS-1$
				reference);
	}

	private static boolean isRepresentationInternalCopy(
			MethodInvocation invocation,
			Expression reference) {
		if (!"copyOf".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
				|| invocation.arguments().isEmpty()
				|| invocation.arguments().get(0) != reference) {
			return false;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		return binding != null
				&& binding.getDeclaringClass() != null
				&& "java.util.Arrays".equals( //$NON-NLS-1$
						binding.getDeclaringClass().getErasure().getQualifiedName());
	}

	private static boolean isInternalNonFlowUse(Expression reference, ASTNode parent) {
		return parent instanceof ArrayAccess access && access.getArray() == reference
				|| parent instanceof QualifiedName qualified
						&& qualified.getQualifier() == reference
						&& "length".equals(qualified.getName().getIdentifier()) //$NON-NLS-1$
				|| parent instanceof FieldAccess fieldAccess
						&& fieldAccess.getExpression() == reference
						&& "length".equals(fieldAccess.getName().getIdentifier()) //$NON-NLS-1$
				|| parent instanceof EnhancedForStatement enhancedFor
						&& enhancedFor.getExpression() == reference
				|| parent instanceof Assignment assignment
						&& assignment.getLeftHandSide() == reference;
	}

	private static boolean isDeclarationName(SimpleName name) {
		ASTNode parent= name.getParent();
		return parent instanceof VariableDeclarationFragment fragment && fragment.getName() == name
				|| parent instanceof SingleVariableDeclaration declaration
						&& declaration.getName() == name;
	}

	private static Expression completeReferenceExpression(SimpleName name, String bindingKey) {
		Expression reference= name;
		ASTNode parent= reference.getParent();
		if (parent instanceof QualifiedName qualified
				&& qualified.getName() == reference
				&& hasBindingKey(qualified.resolveBinding(), bindingKey)) {
			reference= qualified;
		} else if (parent instanceof FieldAccess fieldAccess
				&& fieldAccess.getName() == reference
				&& hasBindingKey(fieldAccess.resolveFieldBinding(), bindingKey)) {
			reference= fieldAccess;
		} else if (parent instanceof SuperFieldAccess superFieldAccess
				&& superFieldAccess.getName() == reference
				&& hasBindingKey(superFieldAccess.resolveFieldBinding(), bindingKey)) {
			reference= superFieldAccess;
		}
		while (reference.getParent() instanceof ParenthesizedExpression parenthesized) {
			reference= parenthesized;
		}
		return reference;
	}

	private static boolean hasBindingKey(IBinding binding, String bindingKey) {
		return binding instanceof IVariableBinding variable
				&& bindingKey.equals(variable.getVariableDeclaration().getKey());
	}

	private static <N extends ASTNode> N ancestor(ASTNode node, Class<N> type) {
		ASTNode current= node.getParent();
		while (current != null && !type.isInstance(current)) {
			current= current.getParent();
		}
		return type.isInstance(current) ? type.cast(current) : null;
	}
}
