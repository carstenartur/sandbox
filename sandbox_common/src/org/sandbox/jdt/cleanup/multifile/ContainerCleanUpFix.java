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
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan;

/** Combines independently planned local container migrations into one atomic cleanup fix. */
public final class ContainerCleanUpFix {

	private static final String DESCRIPTION= "Modernize local container contracts"; //$NON-NLS-1$

	private ContainerCleanUpFix() {
	}

	/**
	 * Revalidates every supplied plan against the current AST and returns one fix.
	 * Returning {@code null} for an empty plan set follows the Eclipse cleanup contract.
	 */
	public static ICleanUpFix create(
			ICompilationUnit unit,
			CompilationUnit root,
			Collection<ContainerLocalRewritePlan> arrayPlans,
			Collection<UniqueSequenceLocalRewritePlan> uniqueSequencePlans)
			throws CoreException {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		Objects.requireNonNull(arrayPlans, "arrayPlans"); //$NON-NLS-1$
		Objects.requireNonNull(uniqueSequencePlans, "uniqueSequencePlans"); //$NON-NLS-1$

		List<CompilationUnitRewriteOperationWithSourceRange> operations= new ArrayList<>(
				arrayPlans.size() + uniqueSequencePlans.size());
		for (ContainerLocalRewritePlan plan : arrayPlans) {
			operations.add(ContainerLocalRewriteFix.operation(unit, root, plan));
		}
		for (UniqueSequenceLocalRewritePlan plan : uniqueSequencePlans) {
			operations.add(UniqueSequenceLocalRewriteFix.operation(unit, root, plan));
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
