/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

/** Exact file history. The historical view ID is retained for saved perspectives. */
public class JavaTypeHistoryView extends GitResultsView {
	public static final String ID= "org.sandbox.jdt.internal.ui.search.gitindex.JavaTypeHistoryView"; //$NON-NLS-1$
	private Text path;

	@Override protected void createQuery(Composite parent) {
		path= input(parent, "File path:", "src/example/Clock.java (exact repository-relative path)"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override protected void createColumns() {
		column("Commit", 180, row -> ((SearchHit) row).getCommitHash()); //$NON-NLS-1$
		column("Path", 300, row -> ((SearchHit) row).getPath()); //$NON-NLS-1$
		column("Author time", 220, row -> ((SearchHit) row).getCommitTime()); //$NON-NLS-1$
		column("Blob", 260, row -> ((SearchHit) row).getBlobId()); //$NON-NLS-1$
	}

	@Override protected Supplier<? extends List<?>> query(String repo, SemanticSearchClient client) {
		String exactPath= path.getText();
		if (exactPath.isEmpty()) throw new IllegalArgumentException("File path is required."); //$NON-NLS-1$
		return () -> client.getFileHistory(repo, exactPath);
	}

	@Override protected String actionLabel() { return "History"; } //$NON-NLS-1$
}
