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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

/** Utility for obtaining a stable list of all source compilation units. */
public final class JavaProjectCompilationUnits {

	private JavaProjectCompilationUnits() {
	}

	/**
	 * Collects all existing compilation units in source package-fragment roots.
	 * The result is sorted by Java element handle for deterministic planning.
	 *
	 * @param project Java project
	 * @return stable list of source compilation units
	 * @throws JavaModelException if the Java model cannot be read
	 */
	public static List<ICompilationUnit> collect(IJavaProject project) throws JavaModelException {
		List<ICompilationUnit> result= new ArrayList<>();
		for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
			if (root.getKind() != IPackageFragmentRoot.K_SOURCE || !root.exists()) {
				continue;
			}
			for (IJavaElement child : root.getChildren()) {
				if (child instanceof IPackageFragment fragment && fragment.exists()) {
					for (ICompilationUnit unit : fragment.getCompilationUnits()) {
						if (unit.exists()) {
							result.add(unit);
						}
					}
				}
			}
		}
		result.sort(Comparator.comparing(IJavaElement::getHandleIdentifier));
		return List.copyOf(result);
	}
}
