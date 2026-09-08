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
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;

/** Resolves all compilation-unit-local members of one aggregate migration plan. */
public final class ClosedSourceParameterMigrationFix {

	private static final String DESCRIPTION=
			"Migrate closed caller and parameter container contract"; //$NON-NLS-1$

	private ClosedSourceParameterMigrationFix() {
	}

	/**
	 * Revalidates and combines every member for the supplied unit, or returns
	 * {@code null} when the unit is not part of the immutable aggregate plan.
	 */
	public static ICleanUpFix create(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ClosedSourceParameterMigrationPlan plan) throws CoreException {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$

		String handle= unit.getHandleIdentifier();
		List<CompilationUnitRewriteOperationWithSourceRange> operations=
				new ArrayList<>();
		if (handle.equals(plan.callerPlan().compilationUnitHandle())) {
			operations.add(ContainerLocalRewriteFix.operation(
					unit, root, plan.callerPlan()));
		}
		for (ContainerParameterRewritePlan parameterPlan : plan.parameterPlans()) {
			if (handle.equals(parameterPlan.compilationUnitHandle())) {
				operations.add(ContainerParameterRewriteFix.operation(
						unit, root, parameterPlan));
			}
		}
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(
				DESCRIPTION,
				root,
				operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
	}
}
