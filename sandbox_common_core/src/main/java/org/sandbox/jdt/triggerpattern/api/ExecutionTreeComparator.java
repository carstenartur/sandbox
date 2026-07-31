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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.Node;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.NodeKind;

/** Compares framework-neutral execution trees under an explicit safety policy. */
public final class ExecutionTreeComparator {

	/**
	 * Explicit comparison dimensions.
	 *
	 * <p>Multiplicity is always exact: canonical entries retain every occurrence.
	 * A migration may relax wrapper identity, nesting or order only by selecting a
	 * corresponding policy. There is deliberately no set-only mode that could hide
	 * duplicate or lost tests.</p>
	 */
	public record Policy(boolean compareNesting, boolean compareContainerIdentity,
			boolean compareOrder, boolean compareResults, boolean compareAttributes,
			boolean compareDisplayNames, boolean requireSuccessful) {

		/** Strict comparison of structure, semantic identities, order, results and attributes. */
		public static Policy strict() {
			return new Policy(true, true, true, true, true, false, true);
		}

		/** Compares tree shape and leaves while allowing framework container names to change. */
		public static Policy sameShape() {
			return new Policy(true, false, true, true, true, false, true);
		}

		/** Compares ordered leaf identities and multiplicity while ignoring all wrappers. */
		public static Policy leavesInOrder() {
			return new Policy(false, false, true, true, true, false, true);
		}

		/** Compares the unordered leaf multiset while preserving exact multiplicity. */
		public static Policy leafMultiset() {
			return new Policy(false, false, false, true, true, false, true);
		}

		/** Returns a copy with result comparison changed. */
		public Policy withResults(boolean enabled) {
			return new Policy(compareNesting, compareContainerIdentity, compareOrder, enabled,
					compareAttributes, compareDisplayNames, requireSuccessful);
		}

		/** Returns a copy with adapter attributes changed. */
		public Policy withAttributes(boolean enabled) {
			return new Policy(compareNesting, compareContainerIdentity, compareOrder, compareResults,
					enabled, compareDisplayNames, requireSuccessful);
		}

		/** Returns a copy with display-name comparison changed. */
		public Policy withDisplayNames(boolean enabled) {
			return new Policy(compareNesting, compareContainerIdentity, compareOrder, compareResults,
					compareAttributes, enabled, requireSuccessful);
		}

		/** Returns a copy with the successful-execution requirement changed. */
		public Policy requiringSuccessful(boolean enabled) {
			return new Policy(compareNesting, compareContainerIdentity, compareOrder, compareResults,
					compareAttributes, compareDisplayNames, enabled);
		}
	}

	/** Complete deterministic comparison result and diagnostic canonical forms. */
	public record Comparison(boolean equivalent, List<String> beforeEntries,
			List<String> afterEntries, String difference) {
		public Comparison {
			beforeEntries= List.copyOf(beforeEntries);
			afterEntries= List.copyOf(afterEntries);
			difference= difference == null ? "" : difference; //$NON-NLS-1$
		}
	}

	private ExecutionTreeComparator() {
	}

	/** Compares two snapshots under the supplied explicit policy. */
	public static Comparison compare(ExecutionTreeSnapshot before, ExecutionTreeSnapshot after,
			Policy policy) {
		Objects.requireNonNull(before);
		Objects.requireNonNull(after);
		Objects.requireNonNull(policy);
		List<String> beforeEntries= canonicalEntries(before, policy);
		List<String> afterEntries= canonicalEntries(after, policy);
		if (policy.requireSuccessful() && (!before.successful() || !after.successful())) {
			return new Comparison(false, beforeEntries, afterEntries,
					"Execution must be successful before and after migration: before=" //$NON-NLS-1$
							+ before.successful() + ", after=" + after.successful()); //$NON-NLS-1$
		}
		if (beforeEntries.equals(afterEntries)) {
			return new Comparison(true, beforeEntries, afterEntries, ""); //$NON-NLS-1$
		}
		return new Comparison(false, beforeEntries, afterEntries,
				firstDifference(beforeEntries, afterEntries));
	}

	private static List<String> canonicalEntries(ExecutionTreeSnapshot snapshot, Policy policy) {
		List<String> entries= new ArrayList<>();
		if (policy.compareNesting()) {
			for (Node root : snapshot.roots()) {
				entries.add(canonicalNode(root, policy));
			}
		} else {
			for (Node root : snapshot.roots()) {
				appendLeaves(root, policy, entries);
			}
		}
		if (!policy.compareOrder()) {
			entries.sort(Comparator.naturalOrder());
		}
		return List.copyOf(entries);
	}

	private static String canonicalNode(Node node, Policy policy) {
		List<String> children= node.children().stream()
				.map(child -> canonicalNode(child, policy))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		if (!policy.compareOrder()) {
			children.sort(Comparator.naturalOrder());
		}
		StringBuilder result= new StringBuilder();
		result.append(node.kind()).append('{');
		if (node.kind() != NodeKind.CONTAINER || policy.compareContainerIdentity()) {
			appendValue(result, node.identity());
		}
		if (policy.compareDisplayNames()) {
			appendValue(result, node.displayName());
		}
		if (policy.compareResults()) {
			appendValue(result, node.result());
		}
		if (policy.compareAttributes()) {
			appendAttributes(result, node.attributes());
		}
		result.append('}').append('[');
		for (String child : children) {
			appendValue(result, child);
		}
		return result.append(']').toString();
	}

	private static void appendLeaves(Node node, Policy policy, List<String> entries) {
		if (node.kind() == NodeKind.TEST || node.children().isEmpty()) {
			entries.add(canonicalLeaf(node, policy));
			return;
		}
		for (Node child : node.children()) {
			appendLeaves(child, policy, entries);
		}
	}

	private static String canonicalLeaf(Node node, Policy policy) {
		StringBuilder result= new StringBuilder(node.kind().name()).append('{');
		appendValue(result, node.identity());
		if (policy.compareDisplayNames()) {
			appendValue(result, node.displayName());
		}
		if (policy.compareResults()) {
			appendValue(result, node.result());
		}
		if (policy.compareAttributes()) {
			appendAttributes(result, node.attributes());
		}
		return result.append('}').toString();
	}

	private static void appendAttributes(StringBuilder target, Map<String, String> attributes) {
		Map<String, String> sorted= new TreeMap<>(attributes);
		for (Map.Entry<String, String> entry : sorted.entrySet()) {
			appendValue(target, entry.getKey());
			appendValue(target, entry.getValue());
		}
	}

	private static void appendValue(StringBuilder target, String value) {
		String text= value == null ? "" : value; //$NON-NLS-1$
		target.append(text.length()).append(':').append(text).append(';');
	}

	private static String firstDifference(List<String> before, List<String> after) {
		int common= Math.min(before.size(), after.size());
		for (int index= 0; index < common; index++) {
			if (!before.get(index).equals(after.get(index))) {
				return "Execution tree differs at canonical entry " + index //$NON-NLS-1$
						+ ": before=" + before.get(index) + ", after=" + after.get(index); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return "Execution tree occurrence count differs: before=" + before.size() //$NON-NLS-1$
				+ ", after=" + after.size(); //$NON-NLS-1$
	}
}
