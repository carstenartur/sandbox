/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.util.List;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

/**
 * Eclipse view showing the change history of a Java type across all commits.
 *
 * <p>
 * Displays which commits modified a given Java class, including which methods
 * were changed. Internally uses {@link SemanticSearchClient#getFileHistory(String, String)}
 * to call the REST API of {@code sandbox-jgit-server-webapp}.
 * </p>
 */
public class JavaTypeHistoryView extends ViewPart {

	/** The view ID as registered in plugin.xml */
	public static final String ID= "org.sandbox.jdt.internal.ui.search.gitindex.JavaTypeHistoryView"; //$NON-NLS-1$

	private Text typeNameText;
	private TableViewer tableViewer;

	@Override
	public void createPartControl(Composite parent) {
		Composite container= new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(2, false));

		Label typeLabel= new Label(container, SWT.NONE);
		typeLabel.setText("Class:"); //$NON-NLS-1$

		typeNameText= new Text(container, SWT.BORDER | SWT.SEARCH);
		typeNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		typeNameText.setMessage("e.g. org.eclipse.jdt.core.dom.ASTVisitor"); //$NON-NLS-1$

		tableViewer= new TableViewer(container,
				SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		Table table= tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridData tableData= new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		table.setLayoutData(tableData);

		createColumns();
		tableViewer.setContentProvider(ArrayContentProvider.getInstance());

		typeNameText.addListener(SWT.DefaultSelection, e -> performSearch());
	}

	private void createColumns() {
		TableViewerColumn colCommit= new TableViewerColumn(tableViewer, SWT.NONE);
		colCommit.getColumn().setText("Commit"); //$NON-NLS-1$
		colCommit.getColumn().setWidth(100);
		colCommit.setLabelProvider(new ColumnLabelProvider());

		TableViewerColumn colAuthor= new TableViewerColumn(tableViewer, SWT.NONE);
		colAuthor.getColumn().setText("Author"); //$NON-NLS-1$
		colAuthor.getColumn().setWidth(150);
		colAuthor.setLabelProvider(new ColumnLabelProvider());

		TableViewerColumn colDate= new TableViewerColumn(tableViewer, SWT.NONE);
		colDate.getColumn().setText("Date"); //$NON-NLS-1$
		colDate.getColumn().setWidth(120);
		colDate.setLabelProvider(new ColumnLabelProvider());

		TableViewerColumn colMethods= new TableViewerColumn(tableViewer, SWT.NONE);
		colMethods.getColumn().setText("Changed Methods"); //$NON-NLS-1$
		colMethods.getColumn().setWidth(300);
		colMethods.setLabelProvider(new ColumnLabelProvider());
	}

	private void performSearch() {
		String typeName= typeNameText.getText().trim();
		if (typeName.isEmpty()) {
			return;
		}
		SemanticSearchClient client= EmbeddedSearchService.getInstance().getSearchClient();
		if (client == null) {
			tableViewer.setInput(new Object[0]);
			return;
		}
		List<SearchHit> results= client.getFileHistory("", typeName); //$NON-NLS-1$
		tableViewer.setInput(results);
	}

	@Override
	public void setFocus() {
		typeNameText.setFocus();
	}
}
