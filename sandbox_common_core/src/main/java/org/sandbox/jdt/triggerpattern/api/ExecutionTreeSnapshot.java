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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable framework-neutral snapshot of one completed execution tree.
 *
 * <p>The model deliberately contains no JUnit or Eclipse launch types. Adapters
 * assign stable semantic identities to containers and tests while retaining
 * optional display names and attributes for diagnostics. Child order and
 * duplicate occurrences are preserved, so ordering and multiplicity remain
 * observable migration contracts.</p>
 */
public record ExecutionTreeSnapshot(List<Node> roots, boolean successful) {

	/** Creates a defensive immutable snapshot. */
	public ExecutionTreeSnapshot {
		roots= immutableNodes(roots);
	}

	/** Returns whether no execution element was captured. */
	public boolean isEmpty() {
		return roots.isEmpty();
	}

	/** Stable execution element kinds. */
	public enum NodeKind {
		CONTAINER,
		TEST,
		OTHER
	}

	/**
	 * One immutable execution element.
	 *
	 * @param kind semantic element kind
	 * @param identity stable comparison identity supplied by the adapter
	 * @param displayName optional framework display name, normalized to an empty string
	 * @param result optional result token, normalized to an empty string
	 * @param attributes immutable adapter-defined diagnostic attributes
	 * @param children ordered child occurrences
	 */
	public record Node(NodeKind kind, String identity, String displayName, String result,
			Map<String, String> attributes, List<Node> children) {

		/** Creates a defensive immutable node. */
		public Node {
			kind= Objects.requireNonNull(kind);
			identity= requireText(identity, "Execution node identity"); //$NON-NLS-1$
			displayName= displayName == null ? "" : displayName; //$NON-NLS-1$
			result= result == null ? "" : result; //$NON-NLS-1$
			attributes= immutableAttributes(attributes);
			children= immutableNodes(children);
			if (kind == NodeKind.TEST && !children.isEmpty()) {
				throw new IllegalArgumentException("Execution test nodes cannot contain children"); //$NON-NLS-1$
			}
		}

		/** Creates a container node without attributes. */
		public static Node container(String identity, String displayName, String result, List<Node> children) {
			return new Node(NodeKind.CONTAINER, identity, displayName, result, Map.of(), children);
		}

		/** Creates a test node without attributes. */
		public static Node test(String identity, String displayName, String result) {
			return new Node(NodeKind.TEST, identity, displayName, result, Map.of(), List.of());
		}

		/** Creates an adapter-specific node. */
		public static Node other(String identity, String displayName, String result,
				Map<String, String> attributes, List<Node> children) {
			return new Node(NodeKind.OTHER, identity, displayName, result, attributes, children);
		}
	}

	private static List<Node> immutableNodes(List<Node> nodes) {
		if (nodes == null || nodes.isEmpty()) {
			return List.of();
		}
		return nodes.stream().map(Objects::requireNonNull).toList();
	}

	private static Map<String, String> immutableAttributes(Map<String, String> attributes) {
		if (attributes == null || attributes.isEmpty()) {
			return Map.of();
		}
		Map<String, String> copy= new LinkedHashMap<>();
		attributes.forEach((key, value) -> copy.put(requireText(key, "Execution attribute name"), //$NON-NLS-1$
				Objects.requireNonNull(value)));
		return Map.copyOf(copy);
	}

	private static String requireText(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " must not be blank"); //$NON-NLS-1$
		}
		return value;
	}
}
