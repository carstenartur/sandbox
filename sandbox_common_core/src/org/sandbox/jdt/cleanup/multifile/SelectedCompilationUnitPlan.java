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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;

/**
 * Minimal immutable plan describing the compilation units participating in one
 * project cleanup run.
 *
 * @param projectHandle Java project handle
 * @param compilationUnitHandles deterministic set of compilation-unit handles
 */
public record SelectedCompilationUnitPlan(String projectHandle, Set<String> compilationUnitHandles) {

	/** Defensively copies plan data. */
	public SelectedCompilationUnitPlan {
		compilationUnitHandles= Set.copyOf(compilationUnitHandles);
	}

	/**
	 * Creates a plan from the selected units.
	 *
	 * @param project Java project
	 * @param units selected compilation units
	 * @return immutable selected-scope plan
	 */
	public static SelectedCompilationUnitPlan of(IJavaProject project, ICompilationUnit[] units) {
		Set<String> handles= new LinkedHashSet<>();
		Arrays.stream(units).map(IJavaElement::getHandleIdentifier).sorted().forEach(handles::add);
		return new SelectedCompilationUnitPlan(project.getHandleIdentifier(), handles);
	}

	/** Returns whether the unit belongs to this plan. */
	public boolean contains(ICompilationUnit unit) {
		return compilationUnitHandles.contains(unit.getHandleIdentifier());
	}
}
