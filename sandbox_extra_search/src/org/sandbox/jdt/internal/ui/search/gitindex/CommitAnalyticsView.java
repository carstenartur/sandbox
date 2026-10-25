/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.swt.widgets.Composite;

/** Author commit statistics for an explicitly selected server repository. */
public class CommitAnalyticsView extends GitResultsView {
	public static final String ID= "org.sandbox.jdt.internal.ui.search.gitindex.CommitAnalyticsView"; //$NON-NLS-1$

	@Override protected void createQuery(Composite parent) { }

	@Override protected void createColumns() {
		column("Author", 240, row -> ((AuthorStats) row).getAuthor()); //$NON-NLS-1$
		column("Email", 320, row -> ((AuthorStats) row).getAuthorEmail()); //$NON-NLS-1$
		column("Commits", 120, row -> Integer.toString(((AuthorStats) row).getCommitCount())); //$NON-NLS-1$
	}

	@Override protected Supplier<? extends List<?>> query(String repo, SemanticSearchClient client) {
		return () -> client.getAuthorStatistics(repo);
	}

	@Override protected String actionLabel() { return "Refresh"; } //$NON-NLS-1$
}
