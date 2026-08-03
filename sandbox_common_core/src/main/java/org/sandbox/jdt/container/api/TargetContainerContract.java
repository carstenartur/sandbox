/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.api;

import java.util.Objects;

import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;

/**
 * A semantic target contract proposed from a {@link ContainerUsageProfile}.
 *
 * <p>The contract intentionally names properties before implementation classes.
 * A later migration planner may select a concrete implementation only after source
 * level, compatibility and concurrency checks.</p>
 *
 * @param shape proposed structural representation
 * @param orderRequirement required ordering
 * @param uniquenessRequirement required uniqueness
 * @param mutability required publication mutability
 * @param nullContract required null behaviour
 * @param rationale concise explanation of the proposal
 */
public record TargetContainerContract(
		ContainerShape shape,
		OrderRequirement orderRequirement,
		UniquenessRequirement uniquenessRequirement,
		Mutability mutability,
		NullContract nullContract,
		String rationale) {

	public TargetContainerContract {
		Objects.requireNonNull(shape, "shape"); //$NON-NLS-1$
		Objects.requireNonNull(orderRequirement, "orderRequirement"); //$NON-NLS-1$
		Objects.requireNonNull(uniquenessRequirement, "uniquenessRequirement"); //$NON-NLS-1$
		Objects.requireNonNull(mutability, "mutability"); //$NON-NLS-1$
		Objects.requireNonNull(nullContract, "nullContract"); //$NON-NLS-1$
		rationale= Objects.requireNonNull(rationale, "rationale").strip(); //$NON-NLS-1$
		if (rationale.isEmpty()) {
			throw new IllegalArgumentException("rationale must not be empty"); //$NON-NLS-1$
		}
	}

	public enum Mutability {
		MUTABLE,
		IMMUTABLE,
		BUILD_THEN_FREEZE,
		UNKNOWN
	}
}
