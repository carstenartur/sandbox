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
package org.sandbox.jdt.cleanup.multifile;

import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

/**
 * Optional capability implemented by a cleanup that can discover compilation
 * units related to the user's original cleanup selection.
 *
 * <p>The regular Eclipse cleanup API already lets one cleanup instance analyse
 * all selected compilation units in {@code checkPreConditions} and later return
 * one local fix per compilation unit. The missing capability is adding related
 * compilation units that were not selected initially. A patched cleanup
 * orchestrator can use this interface before precondition checking to calculate
 * the transitive target closure.</p>
 *
 * <p>Implementations must not modify source files. They should use Java model
 * handles and search results only, honour cancellation, and return a stable,
 * deterministic collection. The orchestrator is responsible for de-duplication,
 * editability checks, preview, application, and undo.</p>
 */
public interface IMultiFileCleanUpScopeProvider {

	/**
	 * Returns the desired cleanup scope after analysing the current scope.
	 *
	 * <p>The returned collection may contain the current scope, only newly
	 * discovered compilation units, or both. Callers must merge and de-duplicate
	 * it. The method may be invoked repeatedly until the scope reaches a fixed
	 * point.</p>
	 *
	 * @param project Java project currently being cleaned
	 * @param currentScope current, immutable target scope
	 * @param monitor progress monitor
	 * @return compilation units that should participate in the cleanup; never
	 *         {@code null}
	 * @throws CoreException if discovery cannot be completed safely
	 */
	Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException;
}
