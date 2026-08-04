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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.sandbox.jdt.container.analysis.ContainerFlowIndex.BindingInfo;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowDiagnostic;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile;

/** Mutable graph assembly used only during one flow-analysis pass. */
final class ContainerFlowGraphAccumulator {

	private final ContainerUsageProfile profile;
	private final String compilationUnitHandle;
	private final Map<String, FlowNode> nodes= new LinkedHashMap<>();
	private final List<FlowEdge> edges= new ArrayList<>();
	private final Set<String> edgeKeys= new LinkedHashSet<>();
	private final List<FlowDiagnostic> diagnostics= new ArrayList<>();
	private final Set<String> diagnosticKeys= new LinkedHashSet<>();
	private ClosureStatus closureStatus= ClosureStatus.LOCAL_CLOSED;
	private String rootNodeId;

	ContainerFlowGraphAccumulator(
			ContainerUsageProfile profile,
			String compilationUnitHandle) {
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
				info.signatureIndex(),
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
				-1,
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
		String methodKey= methodKey(method);
		String stableId= "parameter:" + methodKey + ':' + parameterIndex; //$NON-NLS-1$
		FlowNode target= nodes.computeIfAbsent(stableId, ignored -> new FlowNode(
				stableId,
				sourceType ? NodeKind.PARAMETER : NodeKind.EXTERNAL_PARAMETER,
				"", //$NON-NLS-1$
				methodKey,
				"", //$NON-NLS-1$
				parameterIndex,
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
				-1,
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
			addDiagnostic(
					kind,
					message,
					profile.identity().sourceStart(),
					profile.identity().sourceLength());
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
			return methodKey(declaringMethod);
		}
		ITypeBinding declaringClass= binding.getDeclaringClass();
		return declaringClass == null ? "" : declaringClass.getTypeDeclaration().getKey(); //$NON-NLS-1$
	}

	private static String methodKey(IMethodBinding method) {
		String key= method.getMethodDeclaration().getKey();
		if (key != null && !key.isBlank()) {
			return key;
		}
		ITypeBinding owner= method.getDeclaringClass();
		String ownerName= owner == null ? "" : owner.getErasure().getQualifiedName(); //$NON-NLS-1$
		return ownerName + '#' + method.getName() + '/' + method.getParameterTypes().length;
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
