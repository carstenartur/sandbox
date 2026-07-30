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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Immutable binding-stable authorization plan for plan-aware hint rules.
 *
 * <p>A named contract identifies the semantic planner that produced the plan.
 * Type and method targets are identified by declaration binding keys. Method
 * invocations additionally retain their source range so multiple calls to the
 * same API remain distinct. Missing contracts or bindings never authorize a
 * plan-aware rewrite.</p>
 */
public record SemanticRewritePlan(String contractId, Map<NodeKey, Set<String>> rolesByNode) {

	/** Creates a defensive immutable plan. */
	public SemanticRewritePlan {
		contractId= contractId == null || contractId.isBlank() ? null : contractId.trim();
		Map<NodeKey, Set<String>> copy= new LinkedHashMap<>();
		if (rolesByNode != null) {
			rolesByNode.forEach((key, roles) -> copy.put(Objects.requireNonNull(key),
					Set.copyOf(roles == null ? Set.of() : roles)));
		}
		rolesByNode= Map.copyOf(copy);
	}

	/** Backward-compatible constructor for uncontracted guard-only plans. */
	public SemanticRewritePlan(Map<NodeKey, Set<String>> rolesByNode) {
		this(null, rolesByNode);
	}

	/** Returns an empty, uncontracted plan. */
	public static SemanticRewritePlan empty() {
		return new SemanticRewritePlan(null, Map.of());
	}

	/** Creates a mutable builder without an execution contract. */
	public static Builder builder() {
		return new Builder(null);
	}

	/** Creates a mutable builder bound to a plan-aware hint contract. */
	public static Builder builder(String contractId) {
		return new Builder(contractId);
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

	/** Stable supported semantic node kinds. */
	public enum NodeKind {
		TYPE,
		METHOD,
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

		public static NodeKey invocation(String bindingKey, int sourceStart, int sourceLength) {
			return bindingKey == null ? null
					: new NodeKey(NodeKind.INVOCATION, bindingKey, sourceStart, sourceLength);
		}

		/** Resolves the canonical plan key for a supported AST node. */
		public static NodeKey from(ASTNode node) {
			if (node instanceof TypeDeclaration type) {
				return type(typeKey(type.resolveBinding()));
			}
			if (node instanceof MethodDeclaration method) {
				return method(methodKey(method.resolveBinding()));
			}
			if (node instanceof MethodInvocation invocation) {
				return invocation(methodKey(invocation.resolveMethodBinding()), invocation.getStartPosition(),
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
			}
			return null;
		}

		private static String typeKey(ITypeBinding binding) {
			if (binding == null) {
				return null;
			}
			ITypeBinding declaration= binding.getErasure().getTypeDeclaration();
			return declaration == null ? null : declaration.getKey();
		}

		private static String methodKey(IMethodBinding binding) {
			return binding == null ? null : binding.getMethodDeclaration().getKey();
		}
	}

	/** Builder used by semantic planners. */
	public static final class Builder {
		private final String contractId;
		private final Map<NodeKey, Set<String>> roles= new LinkedHashMap<>();

		private Builder(String contractId) {
			this.contractId= contractId;
		}

		public Builder add(NodeKey key, String role) {
			if (key != null && role != null && !role.isBlank()) {
				roles.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(role);
			}
			return this;
		}

		public SemanticRewritePlan build() {
			return new SemanticRewritePlan(contractId, roles);
		}
	}
}