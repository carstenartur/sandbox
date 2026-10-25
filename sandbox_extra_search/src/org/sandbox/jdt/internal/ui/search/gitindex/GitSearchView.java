/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Search a named server repository's history and historical source facets. */
public class GitSearchView extends GitResultsView {
	public static final String ID= "org.sandbox.jdt.internal.ui.search.gitindex.GitSearchView"; //$NON-NLS-1$
	private Text search;
	private Combo mode;

	@Override protected void createQuery(Composite parent) {
		search= input(parent, "Search:", "Enter search query…"); //$NON-NLS-1$ //$NON-NLS-2$
		new Label(parent, SWT.NONE).setText("Mode:"); //$NON-NLS-1$
		mode= new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
		mode.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		mode.setItems("Commits", "Java Types", "Paths", "Annotations", "Symbols", "Semantic", "Hybrid"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
		mode.select(0);
	}

	@Override protected void createColumns() {
		column("Commit", 160, row -> ((SearchHit) row).getCommitHash()); //$NON-NLS-1$
		column("Path / Type", 280, row -> { //$NON-NLS-1$
			SearchHit hit= (SearchHit) row;
			return hit.getPath().isEmpty() ? hit.getClassName() : hit.getPath();
		});
		column("Author", 140, row -> ((SearchHit) row).getAuthor()); //$NON-NLS-1$
		column("Message / Package", 360, row -> ((SearchHit) row).getContent()); //$NON-NLS-1$
	}

	@Override protected Supplier<? extends List<?>> query(String repo, SemanticSearchClient client) {
		String text= search.getText().strip();
		if (text.isEmpty()) throw new IllegalArgumentException("Search query is required."); //$NON-NLS-1$
		int selected= mode.getSelectionIndex();
		return () -> switch (selected) {
			case 1 -> client.searchByType(repo, text, 20);
			case 2 -> client.searchByChangedPath(repo, text, 20);
			case 3 -> client.searchAnnotations(repo, text, 20);
			case 4 -> client.searchBySymbol(repo, text, 20);
			case 5 -> client.semanticSearch(repo, text, 10);
			case 6 -> client.hybridSearch(repo, text, 10);
			default -> client.searchCommitMessages(repo, text, 20);
		};
	}

	@Override protected String actionLabel() { return "Search"; } //$NON-NLS-1$
}
