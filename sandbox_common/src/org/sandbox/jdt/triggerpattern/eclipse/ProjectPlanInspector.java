/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.eclipse;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;

/** Read-only discovery for browsing the facts behind a plan-aware rule in its source project. */
public interface ProjectPlanInspector {

	String EXTENSION_POINT= "sandbox_common.planInspectors"; //$NON-NLS-1$

	record Component(String label, SemanticRewritePlan plan) {
		public Component {
			Objects.requireNonNull(label);
			Objects.requireNonNull(plan);
		}
	}

	record Report(List<Component> components, List<String> diagnostics) {
		public Report {
			components= List.copyOf(components);
			diagnostics= List.copyOf(diagnostics);
		}
	}

	/** Discover immutable evidence without running a rewrite or a source-level test/provider. */
	Report discover(String contract, IJavaProject project, IProgressMonitor monitor) throws CoreException;

	static Report inspect(String contract, IJavaProject project, IProgressMonitor monitor) throws CoreException {
		Objects.requireNonNull(contract);
		Objects.requireNonNull(project);
		var entries= Arrays.stream(Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT))
				.filter(entry -> contract.equals(entry.getAttribute("contract"))).toList(); //$NON-NLS-1$
		if (entries.size() != 1) {
			throw new CoreException(Status.error(entries.isEmpty()
					? "PLAN_INSPECTOR_MISSING: Install a planner for " + contract //$NON-NLS-1$
					: "PLAN_INSPECTOR_AMBIGUOUS: Multiple planners claim " + contract)); //$NON-NLS-1$
		}
		Object provider= entries.getFirst().createExecutableExtension("class"); //$NON-NLS-1$
		if (!(provider instanceof ProjectPlanInspector inspector)) {
			throw new CoreException(Status.error("PLAN_INSPECTOR_INVALID: Check the planner registration for " + contract)); //$NON-NLS-1$
		}
		Report report= inspector.discover(contract, project, monitor);
		if (report.components().stream().anyMatch(component -> !component.plan().satisfiesContract(contract))) {
			throw new CoreException(Status.error("PLAN_INSPECTOR_WRONG_CONTRACT: Check the planner for " + contract)); //$NON-NLS-1$
		}
		return report;
	}
}
