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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.ISearchResultListener;
import org.sandbox.jdt.internal.ui.search.gitindex.SearchHit;

/**
 * Holds the results of a semantic code search query.
 *
 * <p>
 * Wraps the list of {@link SearchHit} entries returned by
 * {@code SemanticSearchClient.semanticSearch()} or
 * {@code hybridSearch()}.
 * </p>
 */
public class SemanticCodeSearchResult implements ISearchResult {

	private final SemanticCodeSearchQuery query;

	private volatile List<SearchHit> matches= List.of();

	private final List<ISearchResultListener> listeners= new CopyOnWriteArrayList<>();

	/**
	 * Creates a new search result container for the given query.
	 *
	 * @param query
	 *            the owning search query
	 */
	public SemanticCodeSearchResult(SemanticCodeSearchQuery query) {
		this.query= query;
	}

	/**
	 * Sets the list of matching search hits.
	 *
	 * @param matches
	 *            the search results
	 */
	public void setMatches(List<SearchHit> matches) {
		this.matches= matches != null ? List.copyOf(matches) : List.of();
		var event= new org.eclipse.search.ui.SearchResultEvent(this) {
			private static final long serialVersionUID= 1L;
		};
		listeners.forEach(listener -> listener.searchResultChanged(event));
	}

	/**
	 * Returns the list of matching search hits.
	 *
	 * @return unmodifiable list of results
	 */
	public List<SearchHit> getMatches() {
		return matches;
	}

	@Override
	public String getLabel() {
		return "Semantic Search: '" + query.getQueryText() + "' — " //$NON-NLS-1$ //$NON-NLS-2$
				+ matches.size() + " result(s)"; //$NON-NLS-1$
	}

	@Override
	public String getTooltip() {
		return "Semantic code search results for: " + query.getQueryText(); //$NON-NLS-1$
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return null;
	}

	@Override
	public ISearchQuery getQuery() {
		return query;
	}

	@Override
	public void addListener(ISearchResultListener l) {
		listeners.add(l);
	}

	@Override
	public void removeListener(ISearchResultListener l) {
		listeners.remove(l);
	}
}
