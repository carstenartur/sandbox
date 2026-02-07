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

import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;

/**
 * Column displaying the qualified type name of a variable binding.
 * This column has a higher weight to use more available space.
 */
public class QualifiednameColumn extends AbstractColumn {

	private static final int MINIMUM_WIDTH = 150;
	private static final String TITLE = "Type"; //$NON-NLS-1$
	private static final int COLUMN_WEIGHT = 3; // Higher weight to get more space

	@Override
	public void createColumn(TableViewer viewer, int pos) {
		createTableViewerColumn(viewer, TITLE, MINIMUM_WIDTH, pos).setLabelProvider(new ConflictHighlightingLabelProvider() {
			@Override
			public String getText(Object element) {
				IVariableBinding binding = (IVariableBinding) element;
				return binding.getType().getQualifiedName();
			}
		});
	}
	
	@Override
	public void createColumn(TableViewer viewer, int pos, TableColumnLayout tableColumnLayout) {
		TableViewerColumn viewerColumn = createTableViewerColumn(viewer, TITLE, MINIMUM_WIDTH, pos);
		viewerColumn.setLabelProvider(new ConflictHighlightingLabelProvider() {
			@Override
			public String getText(Object element) {
				IVariableBinding binding = (IVariableBinding) element;
				return binding.getType().getQualifiedName();
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
		String qname1 = p1.getType().getQualifiedName();
		String qname2 = p2.getType().getQualifiedName();
		if (qname1 != null && qname2 != null) {
			return qname1.compareTo(qname2);
		}
		return 0;
	}
}