/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.api.ExecutionTreeComparator;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeComparator.Comparison;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeComparator.Policy;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.Node;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.NodeKind;

/** Contract tests for execution-tree equivalence policies. */
public class ExecutionTreeComparatorTest {

	@Test
	public void strictComparisonPreservesStructureOrderAndDuplicateOccurrences() {
		ExecutionTreeSnapshot before= snapshot(container("suite", //$NON-NLS-1$
				test("Sample#first"), test("Sample#first"), test("Sample#second"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ExecutionTreeSnapshot same= snapshot(container("suite", //$NON-NLS-1$
				test("Sample#first"), test("Sample#first"), test("Sample#second"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ExecutionTreeSnapshot reordered= snapshot(container("suite", //$NON-NLS-1$
				test("Sample#second"), test("Sample#first"), test("Sample#first"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		assertTrue(compare(before, same, Policy.strict()).equivalent());
		assertFalse(compare(before, reordered, Policy.strict()).equivalent());
		assertTrue(compare(before, reordered, Policy.leafMultiset()).equivalent());
	}

	@Test
	public void wrapperRelaxationIsExplicitAndDoesNotHideLeafChanges() {
		ExecutionTreeSnapshot legacy= snapshot(container("legacy-suite", //$NON-NLS-1$
				container("compliance-21", test("Sample#test")))); //$NON-NLS-1$ //$NON-NLS-2$
		ExecutionTreeSnapshot migrated= snapshot(container("jupiter-engine", //$NON-NLS-1$
				container("dynamic-container", test("Sample#test")))); //$NON-NLS-1$ //$NON-NLS-2$
		ExecutionTreeSnapshot changed= snapshot(container("jupiter-engine", //$NON-NLS-1$
				container("dynamic-container", test("Sample#other")))); //$NON-NLS-1$ //$NON-NLS-2$

		assertFalse(compare(legacy, migrated, Policy.strict()).equivalent());
		assertTrue(compare(legacy, migrated, Policy.sameShape()).equivalent());
		assertTrue(compare(legacy, migrated, Policy.leavesInOrder()).equivalent());
		assertFalse(compare(legacy, changed, Policy.leavesInOrder()).equivalent());
	}

	@Test
	public void unorderedLeafComparisonStillRequiresExactMultiplicity() {
		ExecutionTreeSnapshot before= snapshot(container("suite", //$NON-NLS-1$
				test("Sample#first"), test("Sample#first"), test("Sample#second"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ExecutionTreeSnapshot after= snapshot(container("suite", //$NON-NLS-1$
				test("Sample#first"), test("Sample#second"), test("Sample#second"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Comparison comparison= compare(before, after, Policy.leafMultiset());
		assertFalse(comparison.equivalent());
		assertFalse(comparison.difference().isBlank());
	}

	@Test
	public void resultAttributesAndSuccessfulExecutionArePolicyControlled() {
		Node beforeTest= new Node(NodeKind.TEST, "Sample#test", "test", "OK", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Map.of("matrix", "21"), List.of()); //$NON-NLS-1$ //$NON-NLS-2$
		Node failedTest= new Node(NodeKind.TEST, "Sample#test", "test", "FAILURE", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Map.of("matrix", "21"), List.of()); //$NON-NLS-1$ //$NON-NLS-2$
		Node changedAttribute= new Node(NodeKind.TEST, "Sample#test", "test", "OK", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Map.of("matrix", "17"), List.of()); //$NON-NLS-1$ //$NON-NLS-2$

		assertFalse(compare(snapshot(beforeTest), snapshot(failedTest), Policy.strict()).equivalent());
		assertTrue(compare(snapshot(beforeTest), snapshot(failedTest),
				Policy.strict().withResults(false)).equivalent());
		assertFalse(compare(snapshot(beforeTest), snapshot(changedAttribute), Policy.strict()).equivalent());
		assertTrue(compare(snapshot(beforeTest), snapshot(changedAttribute),
				Policy.strict().withAttributes(false)).equivalent());

		ExecutionTreeSnapshot unsuccessful= new ExecutionTreeSnapshot(List.of(beforeTest), false);
		assertFalse(compare(snapshot(beforeTest), unsuccessful, Policy.strict()).equivalent());
		assertTrue(compare(snapshot(beforeTest), unsuccessful,
				Policy.strict().requiringSuccessful(false)).equivalent());
	}

	@Test
	public void testNodesCannotContainChildren() {
		assertThrows(IllegalArgumentException.class, () -> new Node(NodeKind.TEST, "Sample#test", //$NON-NLS-1$
				"test", "OK", Map.of(), List.of(test("nested")))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static Comparison compare(ExecutionTreeSnapshot before, ExecutionTreeSnapshot after,
			Policy policy) {
		return ExecutionTreeComparator.compare(before, after, policy);
	}

	private static ExecutionTreeSnapshot snapshot(Node... roots) {
		return new ExecutionTreeSnapshot(List.of(roots), true);
	}

	private static Node container(String identity, Node... children) {
		return Node.container(identity, identity, "OK", List.of(children)); //$NON-NLS-1$
	}

	private static Node test(String identity) {
		return Node.test(identity, identity, "OK"); //$NON-NLS-1$
	}
}
