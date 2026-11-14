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

import java.util.List;
import java.util.Objects;

/** A source-closed parameter migration with a verified, retained array overload. */
public record LosslessParameterBridgePlan(ClosedSourceParameterMigrationPlan migration, String methodKey, String sourceDeclaration)
		implements ClosedSourceContainerPlan {
	public LosslessParameterBridgePlan {
		Objects.requireNonNull(migration);
		if (methodKey.isBlank() || sourceDeclaration.isBlank() || migration.parameterPlans().size() != 1) {
			throw new IllegalArgumentException("A lossless overload requires one proven source declaration"); //$NON-NLS-1$
		}
	}

	@Override
	public List<String> affectedCompilationUnitHandles() {
		return migration.affectedCompilationUnitHandles();
	}
}
