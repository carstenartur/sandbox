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
package org.sandbox.jdt.container.cleanup.internal.ui.fix;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;

import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpDiagnosticsProvider;
import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider;

/** UI wrapper for the project-closed semantic container cleanup. */
public final class CoordinatedContainerCleanUp
		extends AbstractCleanUpCoreWrapper<CoordinatedContainerCleanUpCore>
		implements IMultiFileCleanUpScopeProvider, IMultiFileCleanUpDiagnosticsProvider {

	public CoordinatedContainerCleanUp(Map<String, String> options) {
		super(options, new CoordinatedContainerCleanUpCore());
	}

	public CoordinatedContainerCleanUp() {
		this(Collections.emptyMap());
	}

	@Override
	public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		return cleanUpCore.expandCleanUpScope(project, currentScope, monitor);
	}

	/** Returns candidate-level atomic preview metadata for the patched JDT host. */
	public Collection<Map<String, Object>> getCoordinatedCleanUpPreview(IJavaProject project)
			throws CoreException {
		return cleanUpCore.getCoordinatedCleanUpPreview(project);
	}

	@Override
	public String getLastPlanningDiagnosticsJson(IJavaProject project) {
		return cleanUpCore.getLastPlanningDiagnosticsJson(project);
	}
}
