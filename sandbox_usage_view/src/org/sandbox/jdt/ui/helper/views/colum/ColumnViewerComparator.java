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

import java.util.ArrayList;

import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;

public class ColumnViewerComparator extends ViewerComparator {
	private int propertyIndex;
	private static final int DESCENDING = 1;
	private int direction = DESCENDING;
	static ArrayList<AbstractColumn> ar=new ArrayList<>();

	public ColumnViewerComparator() {
		this.propertyIndex = 0;
		direction = DESCENDING;
	}

	public int getDirection() {
		return direction == 1 ? SWT.DOWN : SWT.UP;
	}

	public void setColumn(int column) {
		if (column == this.propertyIndex) {
			// Same column as last sort; toggle the direction
			direction = 1 - direction;
		} else {
			// New column; do an ascending sort
			this.propertyIndex = column;
			direction = DESCENDING;
		}
	}

	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		int rc = 0;
		if (direction == DESCENDING) {
		   rc=ar.get(propertyIndex).compare((IVariableBinding) e2,(IVariableBinding) e1);
		} else {
			rc=ar.get(propertyIndex).compare((IVariableBinding) e1,(IVariableBinding) e2);
		}
		return rc;
	}

	public static void addColumn(AbstractColumn column) {
		ar.add(column);
	}
}