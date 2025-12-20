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
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.TableColumn;

public abstract class AbstractColumn {
	static int i= 0;
	private static final ColumnViewerComparator comparator= new ColumnViewerComparator();

	protected TableViewerColumn createTableViewerColumn(TableViewer viewer, String title, int bound,
			final int colNumber) {
		//		TableColumn column = WidgetFactory.tableColumn(SWT.NONE).create(table);
		final TableViewerColumn viewerColumn= new TableViewerColumn(viewer, SWT.NONE);
		final TableColumn column= viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(bound);
		column.setResizable(true);
		column.setMoveable(true);
		column.addSelectionListener(getSelectionAdapter(column, colNumber, viewer));
		return viewerColumn;
	}

	public abstract void createColumn(TableViewer viewer, int pos);

	private SelectionAdapter getSelectionAdapter(final TableColumn column, final int index, TableViewer viewer) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				comparator.setColumn(index);
				int dir= comparator.getDirection();
				viewer.getTable().setSortDirection(dir);
				viewer.getTable().setSortColumn(column);
				viewer.refresh();
			}
		};
	}

	public static ColumnViewerComparator getComparator() {
		return comparator;
	}

	public static void addColumn(TableViewer viewer, AbstractColumn column) {
		ColumnViewerComparator.addColumn(column);
		column.createColumn(viewer, i++);
	}

	protected abstract int compare(IVariableBinding p1, IVariableBinding p2);
}