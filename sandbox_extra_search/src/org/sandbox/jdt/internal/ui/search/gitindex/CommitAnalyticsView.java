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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;
import org.sandbox.jdt.internal.ui.search.Messages;

/**
 * Eclipse view displaying commit analytics and author statistics for Git
 * repositories.
 *
 * <p>
 * Shows:
 * </p>
 * <ul>
 * <li>Top authors by commit count</li>
 * <li>Commits per month</li>
 * <li>Object statistics (commits, trees, blobs)</li>
 * </ul>
 *
 * <p>
 * Internally uses {@link SemanticSearchClient} to call the REST API of
 * {@code sandbox-jgit-server-webapp}.
 * </p>
 */
public class CommitAnalyticsView extends ViewPart {

	/** The view ID as registered in plugin.xml */
	public static final String ID= "org.sandbox.jdt.internal.ui.search.gitindex.CommitAnalyticsView"; //$NON-NLS-1$

	private Combo repositoryCombo;
	private TableViewer authorTableViewer;
	private Label statsLabel;

	@Override
	public void createPartControl(Composite parent) {
		Composite container= new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(2, false));

		Label repoLabel= new Label(container, SWT.NONE);
		repoLabel.setText("Repository:"); //$NON-NLS-1$

		repositoryCombo= new Combo(container, SWT.DROP_DOWN | SWT.READ_ONLY);
		repositoryCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Label authorsLabel= new Label(container, SWT.NONE);
		authorsLabel.setText("Top Authors:"); //$NON-NLS-1$
		authorsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

		authorTableViewer= new TableViewer(container,
				SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		Table table= authorTableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridData tableData= new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		table.setLayoutData(tableData);

		createColumns();
		authorTableViewer.setContentProvider(ArrayContentProvider.getInstance());

		statsLabel= new Label(container, SWT.WRAP);
		statsLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		statsLabel.setText("Select a repository to view statistics."); //$NON-NLS-1$

		repositoryCombo.addListener(SWT.Selection, e -> refreshAnalytics());
	}

	private void createColumns() {
		TableViewerColumn colAuthor= new TableViewerColumn(authorTableViewer, SWT.NONE);
		colAuthor.getColumn().setText("Author"); //$NON-NLS-1$
		colAuthor.getColumn().setWidth(200);
		colAuthor.setLabelProvider(new ColumnLabelProvider());

		TableViewerColumn colCount= new TableViewerColumn(authorTableViewer, SWT.NONE);
		colCount.getColumn().setText("Commits"); //$NON-NLS-1$
		colCount.getColumn().setWidth(100);
		colCount.setLabelProvider(new ColumnLabelProvider());
	}

	private void refreshAnalytics() {
		String repoName= repositoryCombo.getText().trim();
		SemanticSearchClient client= EmbeddedSearchService.getInstance().getSearchClient();
		if (client == null) {
			statsLabel.setText(Messages.CommitAnalyticsView_ServiceNotInitialized);
			authorTableViewer.setInput(new Object[0]);
			return;
		}
		List<AuthorStats> authors= client.getAuthorStatistics(repoName);
		authorTableViewer.setInput(authors);
		statsLabel.setText(Messages.bind(Messages.CommitAnalyticsView_ShowingAuthors,
				new Object[] {Integer.valueOf(authors.size()), repoName}));
	}

	@Override
	public void setFocus() {
		repositoryCombo.setFocus();
	}
}
