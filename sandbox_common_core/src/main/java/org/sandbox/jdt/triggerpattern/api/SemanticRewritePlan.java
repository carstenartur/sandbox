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
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Immutable binding-stable authorization and fact plan for plan-aware hints.
 *
 * <p>A named contract identifies the semantic planner that produced the plan.
 * Roles authorize broad rewrite categories. Typed values describe properties
 * such as runner kind, lifecycle scope, matrix coordinates or ordering policy.
 * Ordered directed relations describe graph structure such as suite membership,
 * providers, wrappers and inheritance without introducing project-specific
 * concepts into the shared engine.</p>
 *
 * <p>Type, method and field targets are identified by declaration binding keys.
 * Call sites additionally retain their source range so multiple calls to the
 * same API remain distinct. Missing contracts or bindings never authorize a
 * plan-aware rewrite.</p>
 */
public final class SemanticRewritePlan {

	private final String contractId;
	private final Map<NodeKey, Set<String>> rolesByNode;
	private final Map<NodeKey, Map<String, SemanticPlanValue>> valuesByNode;
	private final List<SemanticPlanRelation> relations;
	private final Map<NodeKey, Map<String, List<SemanticPlanRelation>>> outgoingRelations;
	private final Map<NodeKey, Map<String, List<SemanticPlanRelation>>> incomingRelations;

	/** Creates a defensive immutable plan. */
	public SemanticRewritePlan(String contractId, Map<NodeKey, Set<String>> rolesByNode,
			Map<NodeKey, Map<String, SemanticPlanValue>> valuesByNode,
			List<SemanticPlanRelation> relations) {
		this.contractId= contractId == null || contractId.isBlank() ? null : contractId.trim();
		this.rolesByNode= immutableRoles(rolesByNode);
		this.valuesByNode= immutableValues(valuesByNode);
		this.relations= List.copyOf(relations == null ? List.of() : relations);
		validateValueKinds(this.valuesByNode, this.relations);
		this.outgoingRelations= indexRelations(this.relations, true);
		this.incomingRelations= indexRelations(this.relations, false);
	}

	/** Backward-compatible constructor for role-only contracted plans. */
	public SemanticRewritePlan(String contractId, Map<NodeKey, Set<String>> rolesByNode) {
		this(contractId, rolesByNode, Map.of(), List.of());
	}

	/** Creates a contracted plan with roles and typed values but no relations. */
	public SemanticRewritePlan(String contractId, Map<NodeKey, Set<String>> rolesByNode,
			Map<NodeKey, Map<String, SemanticPlanValue>> valuesByNode) {
		this(contractId, rolesByNode, valuesByNode, List.of());
	}

	/** Backward-compatible constructor for uncontracted guard-only plans. */
	public SemanticRewritePlan(Map<NodeKey, Set<String>> rolesByNode) {
		this(null, rolesByNode, Map.of(), List.of());
	}

	/** Returns an empty, uncontracted plan. */
	public static SemanticRewritePlan empty() {
		return new SemanticRewritePlan(null, Map.of(), Map.of(), List.of());
	}

	/** Creates a mutable builder without an execution contract. */
	public static Builder builder() {
		return new Builder(null);
	}

	/** Creates a mutable builder bound to a plan-aware hint contract. */
	public static Builder builder(String contractId) {
		return new Builder(contractId);
	}

	public String contractId() {
		return contractId;
	}

	public Map<NodeKey, Set<String>> rolesByNode() {
		return rolesByNode;
	}

	public Map<NodeKey, Map<String, SemanticPlanValue>> valuesByNode() {
		return valuesByNode;
	}

	/** Returns all relation occurrences in planner order, including duplicates. */
	public List<SemanticPlanRelation> relations() {
		return relations;
	}

	/** Returns whether this plan contains no roles, values or relations. */
	public boolean isEmpty() {
		return rolesByNode.isEmpty() && valuesByNode.isEmpty() && relations.isEmpty();
	}

	/** Returns whether this plan was produced for the exact named contract. */
	public boolean satisfiesContract(String requiredContractId) {
		return contractId != null && requiredContractId != null
				&& contractId.equals(requiredContractId.trim());
	}

	/** Returns whether the exact AST target has the requested role. */
	public boolean hasRole(ASTNode node, String role) {
		NodeKey key= NodeKey.from(node);
		return key != null && role != null && rolesByNode.getOrDefault(key, Set.of()).contains(role);
	}

	/** Returns whether an enclosing semantic declaration has the requested role. */
	public boolean hasEnclosingRole(ASTNode node, String role) {
		ASTNode current= node;
		while (current != null) {
			if (hasRole(current, role)) {
				return true;
			}
			current= current.getParent();
		}
		return false;
	}

	/** Returns one typed value for the exact stable node key. */
	public Optional<SemanticPlanValue> value(NodeKey key, String name) {
		if (key == null || name == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(valuesByNode.getOrDefault(key, Map.of()).get(name));
	}

	/** Returns one typed value for the exact AST target. */
	public Optional<SemanticPlanValue> value(ASTNode node, String name) {
		return value(NodeKey.from(node), name);
	}

	/** Returns the first typed value found on the node or an enclosing declaration. */
	public Optional<SemanticPlanValue> enclosingValue(ASTNode node, String name) {
		ASTNode current= node;
		while (current != null) {
			Optional<SemanticPlanValue> value= value(current, name);
			if (value.isPresent()) {
				return value;
			}
			current= current.getParent();
		}
		return Optional.empty();
	}

	/** Returns whether the exact AST target has the supplied typed value. */
	public boolean hasValue(ASTNode node, String name, SemanticPlanValue expected) {
		return expected != null && value(node, name).filter(expected::equals).isPresent();
	}

	/** Returns whether the node or an enclosing declaration has the supplied value. */
	public boolean hasEnclosingValue(ASTNode node, String name, SemanticPlanValue expected) {
		return expected != null && enclosingValue(node, name).filter(expected::equals).isPresent();
	}

	/** Returns all outgoing relations of the requested kind in declared order. */
	public List<SemanticPlanRelation> outgoing(NodeKey source, String kind) {
		if (source == null || kind == null) {
			return List.of();
		}
		return outgoingRelations.getOrDefault(source, Map.of()).getOrDefault(kind, List.of());
	}

	/** Returns all outgoing relations for an AST target in declared order. */
	public List<SemanticPlanRelation> outgoing(ASTNode source, String kind) {
		return outgoing(NodeKey.from(source), kind);
	}

	/** Returns all incoming relations of the requested kind in declared order. */
	public List<SemanticPlanRelation> incoming(NodeKey target, String kind) {
		if (target == null || kind == null) {
			return List.of();
		}
		return incomingRelations.getOrDefault(target, Map.of()).getOrDefault(kind, List.of());
	}

	/** Returns all incoming relations for an AST target in declared order. */
	public List<SemanticPlanRelation> incoming(ASTNode target, String kind) {
		return incoming(NodeKey.from(target), kind);
	}

	/** Returns whether an exact directed relation exists. */
	public boolean hasRelation(ASTNode source, String kind, ASTNode target) {
		NodeKey sourceKey= NodeKey.from(source);
		NodeKey targetKey= NodeKey.from(target);
		return sourceKey != null && targetKey != null && outgoing(sourceKey, kind).stream()
				.anyMatch(relation -> targetKey.equals(relation.target()));
	}

	/** Returns whether an exact relation has the supplied typed attribute. */
	public boolean hasRelationValue(ASTNode source, String kind, ASTNode target, String name,
			SemanticPlanValue expected) {
		NodeKey sourceKey= NodeKey.from(source);
		NodeKey targetKey= NodeKey.from(target);
		return sourceKey != null && targetKey != null && expected != null
				&& outgoing(sourceKey, kind).stream()
						.filter(relation -> targetKey.equals(relation.target()))
						.anyMatch(relation -> relation.hasAttribute(name, expected));
	}

	private static Map<NodeKey, Set<String>> immutableRoles(Map<NodeKey, Set<String>> source) {
		Map<NodeKey, Set<String>> copy= new LinkedHashMap<>();
		if (source != null) {
			source.forEach((key, roles) -> copy.put(Objects.requireNonNull(key),
					Set.copyOf(roles == null ? Set.of() : roles)));
		}
		return Map.copyOf(copy);
	}

	private static Map<NodeKey, Map<String, SemanticPlanValue>> immutableValues(
			Map<NodeKey, Map<String, SemanticPlanValue>> source) {
		Map<NodeKey, Map<String, SemanticPlanValue>> copy= new LinkedHashMap<>();
		if (source != null) {
			source.forEach((key, values) -> {
				Map<String, SemanticPlanValue> nodeValues= new LinkedHashMap<>();
				if (values != null) {
					values.forEach((name, value) -> nodeValues.put(requireName(name, "Plan value"), //$NON-NLS-1$
							Objects.requireNonNull(value)));
				}
				copy.put(Objects.requireNonNull(key), Map.copyOf(nodeValues));
			});
		}
		return Map.copyOf(copy);
	}

	private static Map<NodeKey, Map<String, List<SemanticPlanRelation>>> indexRelations(
			List<SemanticPlanRelation> relations, boolean outgoing) {
		Map<NodeKey, Map<String, List<SemanticPlanRelation>>> mutable= new LinkedHashMap<>();
		for (SemanticPlanRelation relation : relations) {
			NodeKey node= outgoing ? relation.source() : relation.target();
			mutable.computeIfAbsent(node, ignored -> new LinkedHashMap<>())
					.computeIfAbsent(relation.kind(), ignored -> new ArrayList<>()).add(relation);
		}
		Map<NodeKey, Map<String, List<SemanticPlanRelation>>> result= new LinkedHashMap<>();
		mutable.forEach((node, byKind) -> {
			Map<String, List<SemanticPlanRelation>> immutableByKind= new LinkedHashMap<>();
			byKind.forEach((kind, indexedRelations) ->
					immutableByKind.put(kind, List.copyOf(indexedRelations)));
			result.put(node, Map.copyOf(immutableByKind));
		});
		return Map.copyOf(result);
	}

	private static void validateValueKinds(Map<NodeKey, Map<String, SemanticPlanValue>> values,
			List<SemanticPlanRelation> relations) {
		Map<String, String> factKinds= new LinkedHashMap<>();
		values.values().forEach(nodeValues -> nodeValues.forEach((name, value) ->
				validateKind(factKinds, name, typeSignature(value), "plan value"))); //$NON-NLS-1$
		Map<String, String> relationKinds= new LinkedHashMap<>();
		for (SemanticPlanRelation relation : relations) {
			relation.attributes().forEach((name, value) -> validateKind(relationKinds,
					relation.kind() + "." + name, typeSignature(value), "relation attribute")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static void validateKind(Map<String, String> kinds, String name, String kind, String label) {
		String previous= kinds.putIfAbsent(name, kind);
		if (previous != null && !previous.equals(kind)) {
			throw new IllegalArgumentException("Conflicting types for " + label + " " + name //$NON-NLS-1$ //$NON-NLS-2$
					+ ": " + previous + " and " + kind); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static String typeSignature(SemanticPlanValue value) {
		return value.kind().name();
	}

	private static String requireName(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " name must not be blank"); //$NON-NLS-1$
		}
		return value.trim();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof SemanticRewritePlan plan)) {
			return false;
		}
		return Objects.equals(contractId, plan.contractId)
				&& rolesByNode.equals(plan.rolesByNode)
				&& valuesByNode.equals(plan.valuesByNode)
				&& relations.equals(plan.relations);
	}

	@Override
	public int hashCode() {
		return Objects.hash(contractId, rolesByNode, valuesByNode, relations);
	}

	@Override
	public String toString() {
		return "SemanticRewritePlan[contractId=" + contractId + ", rolesByNode=" + rolesByNode //$NON-NLS-1$ //$NON-NLS-2$
				+ ", valuesByNode=" + valuesByNode + ", relations=" + relations + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/** Stable supported semantic node kinds. */
	public enum NodeKind {
		TYPE,
		METHOD,
		FIELD,
		INVOCATION
	}

	/** Stable key used to re-identify an authorized AST target. */
	public record NodeKey(NodeKind kind, String bindingKey, int sourceStart, int sourceLength) {
		public NodeKey {
			Objects.requireNonNull(kind);
			Objects.requireNonNull(bindingKey);
		}

		public static NodeKey type(String bindingKey) {
			return bindingKey == null ? null : new NodeKey(NodeKind.TYPE, bindingKey, -1, -1);
		}

		public static NodeKey method(String bindingKey) {
			return bindingKey == null ? null : new NodeKey(NodeKind.METHOD, bindingKey, -1, -1);
		}

		public static NodeKey field(String bindingKey) {
			return bindingKey == null ? null : new NodeKey(NodeKind.FIELD, bindingKey, -1, -1);
		}

		public static NodeKey invocation(String bindingKey, int sourceStart, int sourceLength) {
			return bindingKey == null ? null
					: new NodeKey(NodeKind.INVOCATION, bindingKey, sourceStart, sourceLength);
		}

		/** Resolves the canonical plan key for a supported AST node. */
		public static NodeKey from(ASTNode node) {
			if (node instanceof TypeDeclaration type) {
				return type(typeKey(type.resolveBinding()));
			}
			if (node instanceof EnumDeclaration type) {
				return type(typeKey(type.resolveBinding()));
			}
			if (node instanceof AnnotationTypeDeclaration type) {
				return type(typeKey(type.resolveBinding()));
			}
			if (node instanceof RecordDeclaration type) {
				return type(typeKey(type.resolveBinding()));
			}
			if (node instanceof MethodDeclaration method) {
				return method(methodKey(method.resolveBinding()));
			}
			if (node instanceof FieldDeclaration field) {
				return field.fragments().size() == 1 ? from((ASTNode) field.fragments().get(0)) : null;
			}
			if (node instanceof VariableDeclarationFragment field) {
				return field(fieldKey(field.resolveBinding()));
			}
			if (node instanceof MethodInvocation invocation) {
				return invocation(methodKey(invocation.resolveMethodBinding()), invocation.getStartPosition(),
						invocation.getLength());
			}
			if (node instanceof SuperMethodInvocation invocation) {
				return invocation(methodKey(invocation.resolveMethodBinding()), invocation.getStartPosition(),
						invocation.getLength());
			}
			if (node instanceof ClassInstanceCreation invocation) {
				return invocation(methodKey(invocation.resolveConstructorBinding()), invocation.getStartPosition(),
						invocation.getLength());
			}
			if (node instanceof ConstructorInvocation invocation) {
				return invocation(methodKey(invocation.resolveConstructorBinding()), invocation.getStartPosition(),
						invocation.getLength());
			}
			if (node instanceof SuperConstructorInvocation invocation) {
				return invocation(methodKey(invocation.resolveConstructorBinding()), invocation.getStartPosition(),
						invocation.getLength());
			}
			if (node instanceof SimpleName name) {
				IBinding binding= name.resolveBinding();
				if (binding instanceof IMethodBinding methodBinding) {
					return method(methodKey(methodBinding));
				}
				if (binding instanceof ITypeBinding typeBinding) {
					return type(typeKey(typeBinding));
				}
				if (binding instanceof IVariableBinding variableBinding) {
					return field(fieldKey(variableBinding));
				}
			}
			return null;
		}

		private static String typeKey(ITypeBinding binding) {
			if (binding == null) {
				return null;
			}
			ITypeBinding erasure= binding.getErasure();
			ITypeBinding declaration= (erasure == null ? binding : erasure).getTypeDeclaration();
			return declaration == null ? null : declaration.getKey();
		}

		private static String methodKey(IMethodBinding binding) {
			return binding == null ? null : binding.getMethodDeclaration().getKey();
		}

		private static String fieldKey(IVariableBinding binding) {
			if (binding == null || !binding.isField()) {
				return null;
			}
			return binding.getVariableDeclaration().getKey();
		}
	}

	/** Builder used by semantic planners. */
	public static final class Builder {
		private final String contractId;
		private final Map<NodeKey, Set<String>> roles= new LinkedHashMap<>();
		private final Map<NodeKey, Map<String, SemanticPlanValue>> values= new LinkedHashMap<>();
		private final List<SemanticPlanRelation> relations= new ArrayList<>();

		private Builder(String contractId) {
			this.contractId= contractId;
		}

		/** Adds one authorization role to a stable node. */
		public Builder add(NodeKey key, String role) {
			if (key != null && role != null && !role.isBlank()) {
				roles.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(role);
			}
			return this;
		}

		/** Adds one typed fact and rejects conflicting duplicate definitions. */
		public Builder put(NodeKey key, String name, SemanticPlanValue value) {
			if (key == null) {
				return this;
			}
			String factName= requireName(name, "Plan value"); //$NON-NLS-1$
			SemanticPlanValue factValue= Objects.requireNonNull(value);
			Map<String, SemanticPlanValue> nodeValues=
					values.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
			SemanticPlanValue previous= nodeValues.putIfAbsent(factName, factValue);
			if (previous != null && !previous.equals(factValue)) {
				throw new IllegalArgumentException("Conflicting values for " + factName + " on " + key); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return this;
		}

		public Builder putString(NodeKey key, String name, String value) {
			return put(key, name, SemanticPlanValue.string(value));
		}

		public Builder putBoolean(NodeKey key, String name, boolean value) {
			return put(key, name, SemanticPlanValue.bool(value));
		}

		public Builder putInteger(NodeKey key, String name, long value) {
			return put(key, name, SemanticPlanValue.integer(value));
		}

		public Builder putNode(NodeKey key, String name, NodeKey value) {
			return put(key, name, SemanticPlanValue.node(value));
		}

		public Builder putList(NodeKey key, String name, SemanticPlanValue... value) {
			return put(key, name, SemanticPlanValue.list(value));
		}

		/** Adds one ordered relation occurrence. Duplicate occurrences are preserved. */
		public Builder relate(NodeKey source, String kind, NodeKey target) {
			return relate(source, kind, target, Map.of());
		}

		/** Adds one ordered relation occurrence with typed attributes. */
		public Builder relate(NodeKey source, String kind, NodeKey target,
				Map<String, SemanticPlanValue> attributes) {
			relations.add(new SemanticPlanRelation(kind, Objects.requireNonNull(source),
					Objects.requireNonNull(target), attributes));
			return this;
		}

		public SemanticRewritePlan build() {
			return new SemanticRewritePlan(contractId, roles, values, relations);
		}
	}
}
