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
package org.sandbox.jdt.container.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;

class ResolvedContainerFlowSearchPlanTest {

	@Test
	void preservesExactSearchIntentAndIsImmutable() {
		ResolvedSearchTarget target= methodTarget(SearchKind.METHOD_OVERRIDE_FAMILY, "method-handle"); //$NON-NLS-1$
		ResolvedContainerFlowSearchPlan plan=
				new ResolvedContainerFlowSearchPlan(List.of(target));

		assertEquals(List.of(target), plan.targets());
		assertTrue(!plan.isEmpty());
		assertThrows(UnsupportedOperationException.class,
				() -> plan.targets().clear());
	}

	@Test
	void rejectsDuplicateResolvedTargets() {
		ResolvedSearchTarget target= methodTarget(SearchKind.METHOD_CALLERS, "method-handle"); //$NON-NLS-1$

		assertThrows(IllegalArgumentException.class,
				() -> new ResolvedContainerFlowSearchPlan(List.of(target, target)));
	}

	@Test
	void validatesFieldAndMethodContracts() {
		assertThrows(IllegalArgumentException.class,
				() -> new ResolvedSearchTarget(
						"field:values", //$NON-NLS-1$
						SearchKind.METHOD_CALLERS,
						TargetKind.FIELD,
						"field-binding", //$NON-NLS-1$
						"owner", //$NON-NLS-1$
						"field-handle", //$NON-NLS-1$
						-1,
						"Invalid field search")); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> new ResolvedSearchTarget(
						"field:values", //$NON-NLS-1$
						SearchKind.FIELD_REFERENCES,
						TargetKind.FIELD,
						"field-binding", //$NON-NLS-1$
						"owner", //$NON-NLS-1$
						"field-handle", //$NON-NLS-1$
						0,
						"Invalid field position")); //$NON-NLS-1$
	}

	@Test
	void emptyFactoryProducesReusableEmptyPlan() {
		ResolvedContainerFlowSearchPlan plan= ResolvedContainerFlowSearchPlan.empty();

		assertTrue(plan.isEmpty());
		assertEquals(List.of(), plan.targets());
	}

	private static ResolvedSearchTarget methodTarget(SearchKind kind, String handle) {
		return new ResolvedSearchTarget(
				"parameter:method:0", //$NON-NLS-1$
				kind,
				TargetKind.METHOD,
				"parameter-binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				handle,
				0,
				"Continue method flow"); //$NON-NLS-1$
	}
}
