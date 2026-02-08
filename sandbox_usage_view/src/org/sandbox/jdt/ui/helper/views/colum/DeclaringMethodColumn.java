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

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;

/**
 * Column displaying the declaring method of a variable (for local variables).
 */
public class DeclaringMethodColumn extends AbstractColumn {

	private static final int MINIMUM_WIDTH = 100;
	private static final String TITLE = "Declaring Method"; //$NON-NLS-1$
	private static final int COLUMN_WEIGHT = 2;

	@Override
	public void createColumn(TableViewer viewer, int pos) {
		createTableViewerColumn(viewer, TITLE, MINIMUM_WIDTH, pos).setLabelProvider(new ConflictHighlightingLabelProvider() {
			@Override
			public String getText(Object element) {
				return getDisplayText((IVariableBinding) element);
			}
		});
	}
	
	@Override
	public void createColumn(TableViewer viewer, int pos, TableColumnLayout tableColumnLayout) {
		TableViewerColumn viewerColumn = createTableViewerColumn(viewer, TITLE, MINIMUM_WIDTH, pos);
		viewerColumn.setLabelProvider(new ConflictHighlightingLabelProvider() {
			@Override
			public String getText(Object element) {
				return getDisplayText((IVariableBinding) element);
			}
		});
		tableColumnLayout.setColumnData(viewerColumn.getColumn(), new ColumnWeightData(COLUMN_WEIGHT, MINIMUM_WIDTH, true));
	}
	
	private String getDisplayText(IVariableBinding binding) {
		IMethodBinding declaringMethod = binding.getDeclaringMethod();
		if (declaringMethod != null) {
			return declaringMethod.getName();
		}
		return ""; //$NON-NLS-1$
	}
	
	@Override
	public int getColumnWeight() {
		return COLUMN_WEIGHT;
	}
	
	@Override
	public int getMinimumWidth() {
		return MINIMUM_WIDTH;
	}

	@Override
	protected int compare(IVariableBinding p1, IVariableBinding p2) {
		IMethodBinding declaringMethod1 = p1.getDeclaringMethod();
		IMethodBinding declaringMethod2 = p2.getDeclaringMethod();
		if (declaringMethod1 != null && declaringMethod2 != null) {
			return declaringMethod1.getName().compareTo(declaringMethod2.getName());
		}
		return 0;
	}
}