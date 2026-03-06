/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.internal.ui.search;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.sandbox.jdt.internal.ui.search.gitindex.EmbeddedSearchService;
import org.sandbox.jdt.internal.ui.search.gitindex.SearchHit;
import org.sandbox.jdt.internal.ui.search.gitindex.SemanticSearchClient;

/**
 * Search query that executes a semantic or hybrid code search.
 *
 * <p>
 * Delegates to {@link SemanticSearchClient#semanticSearch(String, String, int)}
 * or {@link SemanticSearchClient#hybridSearch(String, String, int)} via
 * {@link EmbeddedSearchService}.
 * </p>
 */
public class SemanticCodeSearchQuery implements ISearchQuery {

	/** Search mode: semantic vector search, hybrid (fused), or similar code. */
	public enum SearchMode {
		/** Pure semantic (vector) search. */
		SEMANTIC,
		/** Hybrid: full-text + semantic, fused via RRF. */
		HYBRID,
		/** Find code similar to a given blob. */
		SIMILAR
	}

	private final String queryText;
	private final String repoName;
	private final int maxResults;
	private final SearchMode mode;
	private final SemanticCodeSearchResult result;

	/**
	 * Creates a new semantic code search query.
	 *
	 * @param queryText
	 *            the natural language query text
	 * @param repoName
	 *            the repository name to search (empty string for all repos)
	 * @param maxResults
	 *            maximum number of results to return
	 * @param mode
	 *            the search mode
	 */
	public SemanticCodeSearchQuery(String queryText, String repoName,
			int maxResults, SearchMode mode) {
		this.queryText= queryText;
		this.repoName= repoName;
		this.maxResults= maxResults;
		this.mode= mode;
		this.result= new SemanticCodeSearchResult(this);
	}

	@Override
	public IStatus run(IProgressMonitor monitor) {
		monitor.beginTask(getLabel(), IProgressMonitor.UNKNOWN);
		try {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			SemanticSearchClient client= EmbeddedSearchService.getInstance().getSearchClient();
			if (client == null) {
				return Status.error("Semantic search service is not available. " //$NON-NLS-1$
						+ "Please ensure the search backend is running."); //$NON-NLS-1$
			}
			List<SearchHit> hits;
			switch (mode) {
				case HYBRID:
					hits= client.hybridSearch(repoName, queryText, maxResults);
					break;
				case SIMILAR:
					// queryText is treated as a blob object ID for similar code search
					hits= client.findSimilarCode(repoName, queryText, maxResults);
					break;
				case SEMANTIC:
				default:
					hits= client.semanticSearch(repoName, queryText, maxResults);
					break;
			}
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			result.setMatches(hits);
			return Status.OK_STATUS;
		} finally {
			monitor.done();
		}
	}

	@Override
	public String getLabel() {
		return "Semantic Search: '" + queryText + "'"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public boolean canRerun() {
		return true;
	}

	@Override
	public boolean canRunInBackground() {
		return true;
	}

	@Override
	public ISearchResult getSearchResult() {
		return result;
	}

	/**
	 * Returns the query text.
	 *
	 * @return the natural language query text
	 */
	public String getQueryText() {
		return queryText;
	}
}
