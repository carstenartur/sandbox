/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.triggerpattern.eclipse.ProjectPlanInspector;

/** Presents the coordinated planner's actual source discovery with execution disabled. */
public final class JUnitProjectPlanInspector implements ProjectPlanInspector {
	@Override
	public Report discover(String contract, IJavaProject project, IProgressMonitor monitor) throws CoreException {
		boolean parameterized= JUnit4ParameterizedPlan.CONTRACT.equals(contract);
		var options= new JUnitMultiFilePlanner.PlanningOptions(false, !parameterized, parameterized);
		ICompilationUnit[] units= JavaProjectCompilationUnits.collect(project).toArray(ICompilationUnit[]::new);
		var result= JUnitMultiFilePlanner.createCoordinated(project, units, options, monitor);
		List<String> diagnostics= new ArrayList<>();
		for (var entry : result.status().getEntries()) {
			diagnostics.add(entry.getMessage());
		}
		for (var candidate : result.diagnostics().candidates()) {
			diagnostics.add(candidate.reasonCode() + " " + candidate.candidateId() + " — " + candidate.message()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		List<Component> components= new ArrayList<>();
		if (result.plan() != null && !result.status().hasFatalError()) {
			for (var hierarchy : result.plan().junit3Hierarchies()) {
				// Invocation source ranges are local to one compilation unit, as in the executor.
				var groups= hierarchy.types().stream().collect(Collectors.groupingBy(
						JUnit3HierarchyMigration.TypeMigration::compilationUnitHandle,
						LinkedHashMap::new, Collectors.toList()));
				for (var group : groups.entrySet()) {
					components.add(new Component(hierarchy.rootTypeName() + " — " + group.getKey(), //$NON-NLS-1$
							JUnitMigrationPlan.hierarchyPlan(group.getValue())));
				}
			}
			for (var plan : result.plan().preparedParameterizedPlans()) {
				components.add(new Component(plan.testClass().bindingKey(), plan.semanticPlan()));
			}
		}
		return new Report(components, diagnostics);
	}
}
