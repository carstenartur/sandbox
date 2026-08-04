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
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.IJavaElement;
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
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.internal.common.AstProcessing;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Discovers value-flow edges reachable from one container binding inside a compilation
 * unit.
 *
 * <p>The builder follows local variable aliases to a fixed point. Signature edges are
 * represented even when their declarations are outside the current compilation unit;
 * the closure status and diagnostics then instruct the multi-file planner to expand
 * scope or stop at an external boundary.</p>
 */
public final class LocalContainerFlowGraphBuilder {

	/** Builds a local flow graph for the supplied candidate profile. */
	public ContainerFlowGraph build(
			CompilationUnit compilationUnit,
			ContainerUsageProfile profile) {
		Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$
		Objects.requireNonNull(profile, "profile"); //$NON-NLS-1$

		FlowIndex index= FlowIndex.create(compilationUnit);
		GraphAccumulator graph= new GraphAccumulator(profile, index.compilationUnitHandle());
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
				graph.unresolvedBinding(bindingKey, profile.identity().sourceStart(),
						profile.identity().sourceLength());
				continue;
			}

			FlowNode sourceNode= graph.ensureVariableNode(source);
			graph.classifySignatureExposure(source);
			for (SimpleName referenceName : index.references().getOrDefault(bindingKey, List.of())) {
				if (isDeclarationName(referenceName)) {
					continue;
				}
				Expression reference= completeReferenceExpression(referenceName, bindingKey);
				classifyReference(
						reference,
						sourceNode,
						index,
						graph,
						pendingBindings);
			}
		}

		return graph.toGraph();
	}

	private static void classifyReference(
			Expression reference,
			FlowNode sourceNode,
			FlowIndex index,
			GraphAccumulator graph,
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
			FlowIndex index,
			GraphAccumulator graph,
			Deque<String> pendingBindings) {
		Optional<IVariableBinding> target= variableBinding(targetExpression);
		if (target.isPresent()) {
			connectVariable(sourceNode, target.get(), edgeKind, evidence,
					index, graph, pendingBindings);
		} else {
			graph.unclassifiedFlow(evidence);
		}
	}

	private static void connectVariable(
			FlowNode sourceNode,
			IVariableBinding targetBinding,
			EdgeKind edgeKind,
			ASTNode evidence,
			FlowIndex index,
			GraphAccumulator graph,
			Deque<String> pendingBindings) {
		if (targetBinding == null || targetBinding.getVariableDeclaration().getKey() == null) {
			graph.unresolvedBinding("", evidence.getStartPosition(), evidence.getLength()); //$NON-NLS-1$
			return;
		}
		String targetKey= targetBinding.getVariableDeclaration().getKey();
		BindingInfo target= index.bindings().get(targetKey);
		if (target == null) {
			graph.unresolvedBinding(targetKey, evidence.getStartPosition(), evidence.getLength());
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
			FlowIndex index,
			GraphAccumulator graph,
			Deque<String> pendingBindings) {
		if (invokedMethod == null) {
			graph.unresolvedBinding("", reference.getStartPosition(), reference.getLength()); //$NON-NLS-1$
			return;
		}
		IMethodBinding declaration= invokedMethod.getMethodDeclaration();
		String methodKey= declaration.getKey();
		for (int argumentIndex= 0; argumentIndex < arguments.size(); argumentIndex++) {
			if (arguments.get(argumentIndex) != reference) {
				continue;
			}
			int parameterIndex= parameterIndex(declaration, argumentIndex);
			MethodInfo localMethod= index.methods().get(methodKey);
			if (localMethod != null && parameterIndex < localMethod.parameterBindingKeys().size()) {
				String parameterKey= localMethod.parameterBindingKeys().get(parameterIndex);
				BindingInfo parameter= index.bindings().get(parameterKey);
				if (parameter != null) {
					FlowNode parameterNode= graph.ensureVariableNode(parameter);
					graph.addEdge(sourceNode, parameterNode,
							EdgeKind.ARGUMENT_TO_PARAMETER, reference);
					graph.requireScopeExpansion(
							"Method parameter signature participates in the container flow", reference); //$NON-NLS-1$
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
			FlowIndex index,
			GraphAccumulator graph) {
		MethodDeclaration method= ancestor(reference, MethodDeclaration.class);
		IMethodBinding methodBinding= method == null ? null : method.resolveBinding();
		if (methodBinding == null) {
			graph.unresolvedBinding("", reference.getStartPosition(), reference.getLength()); //$NON-NLS-1$
			return;
		}
		String methodKey= methodBinding.getMethodDeclaration().getKey();
		FlowNode returnNode= graph.ensureReturnNode(
				methodKey,
				index.compilationUnitHandle(),
				reference);
		graph.addEdge(sourceNode, returnNode, EdgeKind.RETURN_TO_METHOD, reference);
		graph.requireScopeExpansion(
				"Method return type and all call sites participate in the container flow", reference); //$NON-NLS-1$
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

	private record MethodInfo(String methodKey, List<String> parameterBindingKeys) {
		private MethodInfo {
			parameterBindingKeys= List.copyOf(parameterBindingKeys);
		}
	}

	private static final class BindingInfo {

		private final IVariableBinding binding;
		private SimpleName declarationName;
		private final String compilationUnitHandle;

		BindingInfo(
				IVariableBinding binding,
				SimpleName declarationName,
				String compilationUnitHandle) {
			this.binding= binding.getVariableDeclaration();
			this.declarationName= declarationName;
			this.compilationUnitHandle= compilationUnitHandle;
		}

		void recordDeclaration(SimpleName name) {
			declarationName= name;
		}

		IVariableBinding binding() {
			return binding;
		}

		SimpleName anchor() {
			return declarationName;
		}

		String compilationUnitHandle() {
			return compilationUnitHandle;
		}
	}

	private record FlowIndex(
			Map<String, BindingInfo> bindings,
			Map<String, List<SimpleName>> references,
			Map<String, MethodInfo> methods,
			String compilationUnitHandle) {

		static FlowIndex create(CompilationUnit compilationUnit) {
			Map<String, BindingInfo> bindings= new LinkedHashMap<>();
			Map<String, List<SimpleName>> references= new LinkedHashMap<>();
			Map<String, MethodInfo> methods= new LinkedHashMap<>();
			IJavaElement javaElement= compilationUnit.getJavaElement();
			String unitHandle= javaElement == null ? "" : javaElement.getHandleIdentifier(); //$NON-NLS-1$

			AstProcessing.independent(ReferenceHolder.<String, Object>create())
					.on(MethodDeclaration.class, (method, holder) -> {
						indexMethod(method, methods);
						return true;
					})
					.on(SimpleName.class, (name, holder) -> {
						indexVariable(name, bindings, references, unitHandle);
						return true;
					})
					.build(compilationUnit);

			return new FlowIndex(
					Map.copyOf(bindings),
					copyReferenceMap(references),
					Map.copyOf(methods),
					unitHandle);
		}

		private static void indexMethod(
				MethodDeclaration method,
				Map<String, MethodInfo> methods) {
			IMethodBinding binding= method.resolveBinding();
			if (binding == null) {
				return;
			}
			List<String> parameters= new ArrayList<>();
			for (Object parameterObject : method.parameters()) {
				SingleVariableDeclaration parameter= (SingleVariableDeclaration) parameterObject;
				IVariableBinding parameterBinding= parameter.resolveBinding();
				parameters.add(parameterBinding == null
						? "" : parameterBinding.getVariableDeclaration().getKey()); //$NON-NLS-1$
			}
			String methodKey= binding.getMethodDeclaration().getKey();
			methods.put(methodKey, new MethodInfo(methodKey, parameters));
		}

		private static void indexVariable(
				SimpleName name,
				Map<String, BindingInfo> bindings,
				Map<String, List<SimpleName>> references,
				String unitHandle) {
			IBinding resolved= name.resolveBinding();
			if (!(resolved instanceof IVariableBinding variable)) {
				return;
			}
			IVariableBinding declaration= variable.getVariableDeclaration();
			String key= declaration.getKey();
			if (key == null || key.isBlank()) {
				return;
			}
			BindingInfo info= bindings.computeIfAbsent(
					key, ignored -> new BindingInfo(declaration, null, unitHandle));
			if (isDeclarationName(name)) {
				info.recordDeclaration(name);
			}
			references.computeIfAbsent(key, ignored -> new ArrayList<>()).add(name);
		}

		private static Map<String, List<SimpleName>> copyReferenceMap(
				Map<String, List<SimpleName>> references) {
			Map<String, List<SimpleName>> copy= new LinkedHashMap<>();
			references.forEach((key, value) -> copy.put(key, List.copyOf(value)));
			return Map.copyOf(copy);
		}
	}

	private static final class GraphAccumulator {

		private final ContainerUsageProfile profile;
		private final String compilationUnitHandle;
		private final Map<String, FlowNode> nodes= new LinkedHashMap<>();
		private final List<FlowEdge> edges= new ArrayList<>();
		private final Set<String> edgeKeys= new LinkedHashSet<>();
		private final List<FlowDiagnostic> diagnostics= new ArrayList<>();
		private final Set<String> diagnosticKeys= new LinkedHashSet<>();
		private ClosureStatus closureStatus= ClosureStatus.LOCAL_CLOSED;
		private String rootNodeId;

		GraphAccumulator(ContainerUsageProfile profile, String compilationUnitHandle) {
			this.profile= profile;
			this.compilationUnitHandle= compilationUnitHandle;
		}

		FlowNode ensureVariableNode(BindingInfo info) {
			IVariableBinding binding= info.binding();
			String stableId= variableNodeId(binding.getKey());
			SimpleName anchor= info.anchor();
			int start= anchor == null ? profile.identity().sourceStart() : anchor.getStartPosition();
			int length= anchor == null ? profile.identity().sourceLength() : anchor.getLength();
			FlowNode node= nodes.computeIfAbsent(stableId, ignored -> new FlowNode(
					stableId,
					nodeKind(binding),
					binding.getKey(),
					ownerKey(binding),
					info.compilationUnitHandle(),
					true,
					start,
					length));
			if (rootNodeId == null && binding.getKey().equals(profile.identity().bindingKey())) {
				rootNodeId= stableId;
			}
			return node;
		}

		FlowNode ensureReturnNode(String methodKey, String unitHandle, ASTNode anchor) {
			String stableId= "return:" + methodKey; //$NON-NLS-1$
			return nodes.computeIfAbsent(stableId, ignored -> new FlowNode(
					stableId,
					NodeKind.RETURN_POSITION,
					"", //$NON-NLS-1$
					methodKey,
					unitHandle,
					true,
					anchor.getStartPosition(),
					anchor.getLength()));
		}

		void addParameterBoundary(
				FlowNode sourceNode,
				IMethodBinding method,
				int parameterIndex,
				ASTNode anchor) {
			ITypeBinding declaringClass= method.getDeclaringClass();
			boolean sourceType= declaringClass != null && declaringClass.isFromSource();
			String methodKey= method.getMethodDeclaration().getKey();
			String stableId= "parameter:" + methodKey + ':' + parameterIndex; //$NON-NLS-1$
			FlowNode target= nodes.computeIfAbsent(stableId, ignored -> new FlowNode(
					stableId,
					sourceType ? NodeKind.PARAMETER : NodeKind.EXTERNAL_PARAMETER,
					"", //$NON-NLS-1$
					methodKey,
					"", //$NON-NLS-1$
					false,
					anchor.getStartPosition(),
					anchor.getLength()));
			addEdge(sourceNode, target, EdgeKind.ARGUMENT_TO_PARAMETER, anchor);
			if (sourceType) {
				requireScopeExpansion(
						"Parameter declaration is in another source scope", anchor); //$NON-NLS-1$
			} else {
				raise(ClosureStatus.EXTERNAL_BOUNDARY);
				addDiagnostic(
						DiagnosticKind.EXTERNAL_OR_BINARY_TARGET,
						"Container value reaches an external or binary method parameter", anchor); //$NON-NLS-1$
			}
		}

		void addEdge(FlowNode source, FlowNode target, EdgeKind kind, ASTNode anchor) {
			String edgeKey= source.stableId() + '|' + target.stableId() + '|'
					+ kind + '|' + anchor.getStartPosition();
			if (edgeKeys.add(edgeKey)) {
				edges.add(new FlowEdge(
						source.stableId(),
						target.stableId(),
						kind,
						anchor.getStartPosition(),
						anchor.getLength()));
			}
		}

		void classifySignatureExposure(BindingInfo info) {
			IVariableBinding binding= info.binding();
			if (binding.isParameter()) {
				requireScopeExpansion(
						"Method parameter requires caller and override discovery", info.anchor()); //$NON-NLS-1$
			} else if (binding.isField() && !Modifier.isPrivate(binding.getModifiers())) {
				requireScopeExpansion(
						"Non-private field requires project-wide reference discovery", info.anchor()); //$NON-NLS-1$
			}
		}

		void requireScopeExpansion(String message, ASTNode anchor) {
			raise(ClosureStatus.REQUIRES_SCOPE_EXPANSION);
			addDiagnostic(DiagnosticKind.SCOPE_EXPANSION_REQUIRED, message, anchor);
		}

		void unresolvedBinding(String bindingKey, int start, int length) {
			raise(ClosureStatus.REJECTED);
			String suffix= bindingKey == null || bindingKey.isBlank()
					? "" : ": " + bindingKey; //$NON-NLS-1$ //$NON-NLS-2$
			addDiagnostic(
					DiagnosticKind.UNRESOLVED_BINDING,
					"A required flow binding could not be resolved" + suffix, //$NON-NLS-1$
					start,
					length);
		}

		void unclassifiedFlow(ASTNode anchor) {
			raise(ClosureStatus.REJECTED);
			addDiagnostic(
					DiagnosticKind.UNCLASSIFIED_FLOW,
					"Container flow reaches an unsupported expression shape", anchor); //$NON-NLS-1$
		}

		void addUnresolvedRoot() {
			String stableId= "unknown:" + profile.identity().stableId(); //$NON-NLS-1$
			rootNodeId= stableId;
			nodes.put(stableId, new FlowNode(
					stableId,
					NodeKind.UNKNOWN_BOUNDARY,
					profile.identity().bindingKey(),
					"", //$NON-NLS-1$
					compilationUnitHandle,
					false,
					profile.identity().sourceStart(),
					profile.identity().sourceLength()));
			unresolvedBinding(
					profile.identity().bindingKey(),
					profile.identity().sourceStart(),
					profile.identity().sourceLength());
		}

		ContainerFlowGraph toGraph() {
			return new ContainerFlowGraph(
					rootNodeId,
					List.copyOf(nodes.values()),
					edges,
					closureStatus,
					diagnostics);
		}

		private void addDiagnostic(DiagnosticKind kind, String message, ASTNode anchor) {
			if (anchor == null) {
				addDiagnostic(kind, message,
						profile.identity().sourceStart(), profile.identity().sourceLength());
			} else {
				addDiagnostic(kind, message, anchor.getStartPosition(), anchor.getLength());
			}
		}

		private void addDiagnostic(
				DiagnosticKind kind,
				String message,
				int start,
				int length) {
			String key= kind + "|" + message + '|' + start; //$NON-NLS-1$
			if (diagnosticKeys.add(key)) {
				diagnostics.add(new FlowDiagnostic(kind, message, start, length));
			}
		}

		private void raise(ClosureStatus candidate) {
			if (rank(candidate) > rank(closureStatus)) {
				closureStatus= candidate;
			}
		}
	}

	private static NodeKind nodeKind(IVariableBinding binding) {
		if (binding.isField()) {
			return NodeKind.FIELD;
		}
		if (binding.isParameter()) {
			return NodeKind.PARAMETER;
		}
		return NodeKind.LOCAL_VARIABLE;
	}

	private static String ownerKey(IVariableBinding binding) {
		IMethodBinding declaringMethod= binding.getDeclaringMethod();
		if (declaringMethod != null) {
			return declaringMethod.getMethodDeclaration().getKey();
		}
		ITypeBinding declaringClass= binding.getDeclaringClass();
		return declaringClass == null ? "" : declaringClass.getTypeDeclaration().getKey(); //$NON-NLS-1$
	}

	private static String variableNodeId(String bindingKey) {
		return "variable:" + bindingKey; //$NON-NLS-1$
	}

	private static int rank(ClosureStatus status) {
		return switch (status) {
			case LOCAL_CLOSED -> 0;
			case REQUIRES_SCOPE_EXPANSION -> 1;
			case EXTERNAL_BOUNDARY -> 2;
			case REJECTED -> 3;
		};
	}
}
