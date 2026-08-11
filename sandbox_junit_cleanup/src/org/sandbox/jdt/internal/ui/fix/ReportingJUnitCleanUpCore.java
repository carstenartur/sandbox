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
package org.sandbox.jdt.internal.ui.fix;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupport.Analysis;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMigrationPlan;

/** JUnit cleanup core that retains privacy-preserving planning evidence. */
public final class ReportingJUnitCleanUpCore extends JUnitCleanUpCore {

	private final Map<IJavaProject, String> diagnosticsByProject= new HashMap<>();

	/** Creates a reporting cleanup core without initial options. */
	public ReportingJUnitCleanUpCore() {
	}

	/** Creates a reporting cleanup core with the supplied cleanup options. */
	public ReportingJUnitCleanUpCore(Map<String, String> options) {
		super(options);
	}

	@Override
	protected MultiFileCleanUpPlanResult<JUnitMigrationPlan> createPlan(IJavaProject project,
			ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
		diagnosticsByProject.remove(project);
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= super.createPlan(project, compilationUnits, monitor);
		String plannerJson= result.diagnostics().toJson();
		if (isEnabled(JUnitMigrationOptions.BEST_EFFORT)) {
			Analysis analysis= getBestEffortAnalysis(project);
			diagnosticsByProject.put(project, analysis.toJson(plannerJson));
		} else {
			diagnosticsByProject.put(project, plannerJson);
		}
		return result;
	}

	/** Returns the most recent structured diagnostics for a project. */
	public String getLastPlanningDiagnosticsJson(IJavaProject project) {
		return diagnosticsByProject.getOrDefault(project, ""); //$NON-NLS-1$
	}
}
