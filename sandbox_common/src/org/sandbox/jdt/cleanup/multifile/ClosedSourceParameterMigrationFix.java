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

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;

/** Resolves the compilation-unit-local member of one aggregate migration plan. */
public final class ClosedSourceParameterMigrationFix {

	private ClosedSourceParameterMigrationFix() {
	}

	/**
	 * Revalidates and creates the fix for the supplied unit, or returns {@code null}
	 * when the unit is not part of the immutable aggregate plan.
	 */
	public static ICleanUpFix create(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ClosedSourceParameterMigrationPlan plan) throws CoreException {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
		String handle= unit.getHandleIdentifier();
		if (handle.equals(plan.callerPlan().compilationUnitHandle())) {
			return ContainerLocalRewriteFix.create(unit, root, plan.callerPlan());
		}
		if (handle.equals(plan.parameterPlan().compilationUnitHandle())) {
			return ContainerParameterRewriteFix.create(unit, root, plan.parameterPlan());
		}
		return null;
	}
}
