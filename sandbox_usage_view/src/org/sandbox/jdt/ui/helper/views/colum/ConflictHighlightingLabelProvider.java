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
package org.sandbox.jdt.ui.helper.views.colum;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/**
 * Column label provider that highlights rows with naming conflicts (same name, different type)
 * using a light red background. Also provides alternating row colors for non-conflict rows.
 */
public class ConflictHighlightingLabelProvider extends ColumnLabelProvider {
	
	private static final Color GRAY_COLOR = Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
	private static final Color CONFLICT_COLOR;
	
	/** Shared set of conflicting variable names - set by the view */
	private static Set<String> conflictingNames = Collections.emptySet();
	
	static {
		// Light red/pink color for conflict highlighting
		CONFLICT_COLOR = new Color(Display.getDefault(), 255, 200, 200);
	}
	
	private boolean alternatingColor = false;
	
	/**
	 * Sets the shared set of conflicting variable names.
	 * This should be called by the view when the conflict analysis is updated.
	 * 
	 * @param names the set of variable names that have type conflicts, or empty set to clear
	 */
	public static void setConflictingNames(Set<String> names) {
		conflictingNames = names != null ? names : Collections.emptySet();
	}
	
	/**
	 * Gets the current set of conflicting variable names.
	 * 
	 * @return the set of conflicting names
	 */
	public static Set<String> getConflictingNames() {
		return conflictingNames;
	}
	
	/**
	 * Clears the conflicting names.
	 */
	public static void clearConflictingNames() {
		conflictingNames = Collections.emptySet();
	}

	@Override
	public Color getBackground(Object element) {
		// Check if this element has a naming conflict
		if (element instanceof IVariableBinding variableBinding) {
			if (!conflictingNames.isEmpty() && conflictingNames.contains(variableBinding.getName())) {
				return CONFLICT_COLOR;
			}
		}
		
		// Fall back to alternating colors
		alternatingColor = !alternatingColor;
		if (alternatingColor) {
			return GRAY_COLOR;
		}
		return null;
	}
	
	/**
	 * Resets the alternating color state. Call this before refreshing the table
	 * to ensure consistent alternating pattern.
	 */
	public void resetAlternatingColor() {
		alternatingColor = false;
	}
}
