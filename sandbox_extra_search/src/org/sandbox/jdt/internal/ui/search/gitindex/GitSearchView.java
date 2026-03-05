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
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

/**
 * Eclipse view for full-text search over Git commits and Java source code.
 *
 * <p>
 * Provides search across:
 * </p>
 * <ul>
 * <li>Commit messages</li>
 * <li>Java types (class names, interfaces)</li>
 * <li>File paths</li>
 * <li>Annotations</li>
 * <li>Methods and fields</li>
 * </ul>
 *
 * <p>
 * Internally uses {@code GitDatabaseQueryService} from
 * sandbox-jgit-storage-hibernate for search operations.
 * </p>
 */
public class GitSearchView extends ViewPart {

	/** The view ID as registered in plugin.xml */
	public static final String ID= "org.sandbox.jdt.internal.ui.search.gitindex.GitSearchView"; //$NON-NLS-1$

	private Text searchText;
	private Combo searchTypeCombo;
	private TableViewer tableViewer;

	@Override
	public void createPartControl(Composite parent) {
		Composite container= new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(3, false));

		Label searchLabel= new Label(container, SWT.NONE);
		searchLabel.setText("Search:"); //$NON-NLS-1$

		searchText= new Text(container, SWT.BORDER | SWT.SEARCH);
		searchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		searchText.setMessage("Enter search query..."); //$NON-NLS-1$

		searchTypeCombo= new Combo(container, SWT.DROP_DOWN | SWT.READ_ONLY);
		searchTypeCombo.setItems("Commits", //$NON-NLS-1$
				"Java Types", //$NON-NLS-1$
				"Paths", //$NON-NLS-1$
				"Annotations", //$NON-NLS-1$
				"Methods", //$NON-NLS-1$
				"Fields", //$NON-NLS-1$
				"Semantic", //$NON-NLS-1$
				"Hybrid" //$NON-NLS-1$
		);
		searchTypeCombo.select(0);

		tableViewer= new TableViewer(container,
				SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		Table table= tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridData tableData= new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		table.setLayoutData(tableData);

		createColumns();
		tableViewer.setContentProvider(ArrayContentProvider.getInstance());

		searchText.addListener(SWT.DefaultSelection, e -> performSearch());
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

		TableViewerColumn colMessage= new TableViewerColumn(tableViewer, SWT.NONE);
		colMessage.getColumn().setText("Message"); //$NON-NLS-1$
		colMessage.getColumn().setWidth(400);
		colMessage.setLabelProvider(new ColumnLabelProvider());
	}

	private void performSearch() {
		String query= searchText.getText().trim();
		if (query.isEmpty()) {
			return;
		}
		GitDatabaseQueryService queryService= EmbeddedSearchService.getInstance().getQueryService();
		if (queryService == null) {
			tableViewer.setInput(new Object[0]);
			return;
		}
		int searchType= searchTypeCombo.getSelectionIndex();
		List<?> results;
		switch (searchType) {
			case 1: // Java Types
				results= queryService.searchByType("", query); //$NON-NLS-1$
				break;
			case 2: // Paths
				results= queryService.searchByChangedPath("", query); //$NON-NLS-1$
				break;
			case 3: // Annotations
			case 4: // Methods
			case 5: // Fields
				results= queryService.searchBySymbol("", query); //$NON-NLS-1$
				break;
			case 6: // Semantic
				// Note: result count for semantic search is fixed at 10 in this view.
				// Use SemanticCodeSearchPage for configurable result counts.
				results= queryService.semanticSearch("", query, 10); //$NON-NLS-1$
				break;
			case 7: // Hybrid
				// Note: result count for hybrid search is fixed at 10 in this view.
				// Use SemanticCodeSearchPage for configurable result counts.
				results= queryService.hybridSearch("", query, 10); //$NON-NLS-1$
				break;
			default: // Commits
				results= queryService.searchCommitMessages("", query); //$NON-NLS-1$
				break;
		}
		tableViewer.setInput(results);
	}

	@Override
	public void setFocus() {
		searchText.setFocus();
	}
}
