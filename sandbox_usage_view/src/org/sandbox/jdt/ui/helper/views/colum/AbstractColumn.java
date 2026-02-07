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
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.TableColumn;

/**
 * Abstract base class for table columns displaying variable binding information.
 * Supports both fixed-width columns and weighted columns via TableColumnLayout.
 */
public abstract class AbstractColumn {
	static int columnIndex = 0;
	private static final ColumnViewerComparator comparator = new ColumnViewerComparator();

	protected TableViewerColumn createTableViewerColumn(TableViewer viewer, String title, int minimumWidth,
			final int colNumber) {
		final TableViewerColumn viewerColumn = new TableViewerColumn(viewer, SWT.NONE);
		final TableColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(minimumWidth);
		column.setResizable(true);
		column.setMoveable(true);
		column.addSelectionListener(getSelectionAdapter(column, colNumber, viewer));
		return viewerColumn;
	}

	/**
	 * Creates the column with a fixed width.
	 * @param viewer the table viewer
	 * @param pos the column position
	 */
	public abstract void createColumn(TableViewer viewer, int pos);
	
	/**
	 * Creates the column with weighted layout support.
	 * @param viewer the table viewer
	 * @param pos the column position
	 * @param tableColumnLayout the table column layout for weighted sizing
	 */
	public abstract void createColumn(TableViewer viewer, int pos, TableColumnLayout tableColumnLayout);
	
	/**
	 * Returns the weight for this column when using TableColumnLayout.
	 * Override in subclasses to customize column width distribution.
	 * @return the column weight (default is 1)
	 */
	public int getColumnWeight() {
		return 1;
	}
	
	/**
	 * Returns the minimum width for this column.
	 * @return the minimum width in pixels
	 */
	public abstract int getMinimumWidth();

	private SelectionAdapter getSelectionAdapter(final TableColumn column, final int index, TableViewer viewer) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				comparator.setColumn(index);
				int dir = comparator.getDirection();
				viewer.getTable().setSortDirection(dir);
				viewer.getTable().setSortColumn(column);
				viewer.refresh();
			}
		};
	}

	public static ColumnViewerComparator getComparator() {
		return comparator;
	}

	/**
	 * Adds a column to the table viewer with fixed width.
	 * @param viewer the table viewer
	 * @param column the column to add
	 */
	public static void addColumn(TableViewer viewer, AbstractColumn column) {
		ColumnViewerComparator.addColumn(column);
		column.createColumn(viewer, columnIndex++);
	}
	
	/**
	 * Adds a column to the table viewer with weighted layout.
	 * @param viewer the table viewer
	 * @param column the column to add
	 * @param tableColumnLayout the table column layout for weighted sizing
	 */
	public static void addColumn(TableViewer viewer, AbstractColumn column, TableColumnLayout tableColumnLayout) {
		ColumnViewerComparator.addColumn(column);
		column.createColumn(viewer, columnIndex++, tableColumnLayout);
	}

	protected abstract int compare(IVariableBinding p1, IVariableBinding p2);
}