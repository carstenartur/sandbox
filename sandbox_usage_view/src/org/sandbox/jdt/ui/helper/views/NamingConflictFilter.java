/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

/**
 * A filter that shows only variables with naming conflicts - 
 * variables that have the same name but different types.
 * This helps identify potential naming inconsistencies in the codebase.
 */
public class NamingConflictFilter extends ViewerFilter {

	private Set<String> conflictingNames = new HashSet<>();

	/**
	 * Analyzes all elements to find which variable names have type conflicts.
	 * Must be called before the filter is applied.
	 * 
	 * @param elements the elements to analyze
	 */
	public void analyzeElements(Object[] elements) {
		// Map: variable name -> set of type qualified names
		Map<String, Set<String>> nameToTypes = new HashMap<>();
		
		for (Object element : elements) {
			if (element instanceof IVariableBinding variableBinding) {
				String name = variableBinding.getName();
				ITypeBinding typeBinding = variableBinding.getType();
				String typeName = typeBinding != null ? typeBinding.getQualifiedName() : "unknown"; //$NON-NLS-1$
				
				nameToTypes.computeIfAbsent(name, k -> new HashSet<>()).add(typeName);
			}
		}
		
		// Find names that have more than one type
		conflictingNames.clear();
		for (Map.Entry<String, Set<String>> entry : nameToTypes.entrySet()) {
			if (entry.getValue().size() > 1) {
				conflictingNames.add(entry.getKey());
			}
		}
	}

	/**
	 * Returns the set of variable names that have type conflicts.
	 * 
	 * @return set of conflicting variable names
	 */
	public Set<String> getConflictingNames() {
		return new HashSet<>(conflictingNames);
	}

	/**
	 * Returns true if there are any naming conflicts in the analyzed elements.
	 * 
	 * @return true if conflicts exist
	 */
	public boolean hasConflicts() {
		return !conflictingNames.isEmpty();
	}

	@Override
	public boolean select(Viewer viewer, Object parentElement, Object element) {
		if (element instanceof IVariableBinding variableBinding) {
			return conflictingNames.contains(variableBinding.getName());
		}
		return false;
	}
}
