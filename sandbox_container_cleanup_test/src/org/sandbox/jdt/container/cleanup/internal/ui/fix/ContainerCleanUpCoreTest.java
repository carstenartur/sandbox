/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
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

class ContainerCleanUpCoreTest {

	@Test
	void localComponentDoesNotAdvertiseSyntheticJavaModelHandles() {
		ContainerUsageProfile profile= new ContainerUsageProfile(
				new ContainerIdentity("binding-key", "values", 12, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				AccessProfile.appendOnlyArraySeed(),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.ALLOWED,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.LOCAL,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of());
		String compilationUnitHandle= "=Sandbox/src<test{Sample.java"; //$NON-NLS-1$

		ContainerFlowComponent component= ContainerCleanUpCore.localComponent(
				compilationUnitHandle, profile);
		FlowNode node= component.nodes().get(0);

		assertEquals("variable:binding-key", node.stableId()); //$NON-NLS-1$
		assertEquals(node.stableId(), component.rootNodeId());
		assertEquals("binding-key", node.bindingKey()); //$NON-NLS-1$
		assertEquals("", node.ownerKey()); //$NON-NLS-1$
		assertEquals(compilationUnitHandle, node.compilationUnitHandle());
		assertEquals("", node.javaElementHandle()); //$NON-NLS-1$
		assertFalse(node.hasJavaElementHandle());
		assertEquals(ClosureStatus.LOCAL_CLOSED, component.closureStatus());
	}
}
