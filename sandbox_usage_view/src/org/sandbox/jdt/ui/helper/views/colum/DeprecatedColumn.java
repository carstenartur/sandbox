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

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;

/**
 * Column displaying whether a variable is deprecated.
 */
public class DeprecatedColumn extends AbstractColumn {

	private static final int MINIMUM_WIDTH = 70;
	private static final String TITLE = "Deprecated"; //$NON-NLS-1$
	private static final int COLUMN_WEIGHT = 1; // Smaller weight - less space needed

	@Override
	public void createColumn(TableViewer viewer, int pos) {
		createTableViewerColumn(viewer, TITLE, MINIMUM_WIDTH, pos).setLabelProvider(new AlternatingColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IVariableBinding binding = (IVariableBinding) element;
				return String.valueOf(binding.isDeprecated());
			}
		});
	}
	
	@Override
	public void createColumn(TableViewer viewer, int pos, TableColumnLayout tableColumnLayout) {
		TableViewerColumn viewerColumn = createTableViewerColumn(viewer, TITLE, MINIMUM_WIDTH, pos);
		viewerColumn.setLabelProvider(new AlternatingColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IVariableBinding binding = (IVariableBinding) element;
				return String.valueOf(binding.isDeprecated());
			}
		});
		tableColumnLayout.setColumnData(viewerColumn.getColumn(), new ColumnWeightData(COLUMN_WEIGHT, MINIMUM_WIDTH, true));
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
		ITypeBinding declaringClass1 = p1.getDeclaringClass();
		ITypeBinding declaringClass2 = p2.getDeclaringClass();
		if (declaringClass1 != null && declaringClass2 != null) {
			return Boolean.compare(declaringClass1.isDeprecated(), declaringClass2.isDeprecated());
		}
		return 0;
	}
}