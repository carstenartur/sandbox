/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

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

/** Cleanup wrapper for JUnit migration. */
public class JUnitCleanUp extends AbstractCleanUpCoreWrapper<ReportingJUnitCleanUpCore>
		implements IMultiFileCleanUpScopeProvider, IMultiFileCleanUpDiagnosticsProvider {
	public JUnitCleanUp(final Map<String, String> options) {
		super(options, new ReportingJUnitCleanUpCore());
	}

	public JUnitCleanUp() {
		this(Collections.emptyMap());
	}

	@Override
	public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		return cleanUpCore.expandCleanUpScope(project, currentScope, monitor);
	}

	/**
	 * Optional dependency-free contract discovered reflectively by the patched
	 * JDT Cleanup host. Coordinated JUnit migrations must expose the candidate
	 * metadata held by the planned cleanup core so declarations and consumers are
	 * represented by one atomic preview selection unit.
	 *
	 * @param project current Java project
	 * @return immutable coordinated-candidate metadata
	 * @throws CoreException if the planned preview metadata is inconsistent
	 */
	public Collection<Map<String, Object>> getCoordinatedCleanUpPreview(IJavaProject project)
			throws CoreException {
		return cleanUpCore.getCoordinatedCleanUpPreview(project);
	}

	@Override
	public String getLastPlanningDiagnosticsJson(IJavaProject project) {
		return cleanUpCore.getLastPlanningDiagnosticsJson(project);
	}
}
