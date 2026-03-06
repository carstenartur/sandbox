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

import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.search.ui.ISearchPage;
import org.eclipse.search.ui.ISearchPageContainer;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.sandbox.jdt.internal.ui.search.SemanticCodeSearchQuery.SearchMode;
import org.sandbox.jdt.internal.ui.search.gitindex.EmbeddedSearchService;

/**
 * Eclipse search page for semantic code search using local AI embeddings.
 *
 * <p>
 * Provides a UI to search code by natural language description. Uses
 * {@link EmbeddedSearchService} to delegate to
 * {@code GitDatabaseQueryService.semanticSearch()} or
 * {@code hybridSearch()}.
 * </p>
 *
 * <p>
 * UI layout:
 * </p>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────┐
 * │ Search: [Describe what you're looking for...  ] │
 * │                                                 │
 * │ Mode:  ○ Semantic  ○ Hybrid  ○ Similar Code     │
 * │                                                 │
 * │ Repository: [text field                       ] │
 * │ Max results: [10 ▾]                             │
 * │                                                 │
 * │ ℹ Uses local AI embeddings (offline, ~1ms)      │
 * └─────────────────────────────────────────────────┘
 * </pre>
 */
public class SemanticCodeSearchPage extends DialogPage implements ISearchPage {

	/** The search page ID as registered in plugin.xml. */
	public static final String PAGE_ID= "org.sandbox.jdt.ui.SemanticCodeSearchPage"; //$NON-NLS-1$

	private ISearchPageContainer container;

	private Text queryText;
	private Button semanticRadio;
	private Button hybridRadio;
	private Button similarRadio;
	private Text repoText;
	private Spinner maxResultsSpinner;

	@Override
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(2, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		// Search query field
		Label searchLabel= new Label(composite, SWT.NONE);
		searchLabel.setText("Search:"); //$NON-NLS-1$

		queryText= new Text(composite, SWT.BORDER | SWT.SINGLE);
		queryText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		queryText.setMessage("Natural language query (or blob object ID for Similar mode)"); //$NON-NLS-1$

		// Mode section
		Label modeLabel= new Label(composite, SWT.NONE);
		modeLabel.setText("Mode:"); //$NON-NLS-1$

		Composite modeComposite= new Composite(composite, SWT.NONE);
		modeComposite.setLayout(new GridLayout(3, false));
		modeComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		semanticRadio= new Button(modeComposite, SWT.RADIO);
		semanticRadio.setText("Semantic"); //$NON-NLS-1$
		semanticRadio.setSelection(true);

		hybridRadio= new Button(modeComposite, SWT.RADIO);
		hybridRadio.setText("Hybrid"); //$NON-NLS-1$

		similarRadio= new Button(modeComposite, SWT.RADIO);
		similarRadio.setText("Similar Code"); //$NON-NLS-1$

		// Repository field
		Label repoLabel= new Label(composite, SWT.NONE);
		repoLabel.setText("Repository:"); //$NON-NLS-1$

		repoText= new Text(composite, SWT.BORDER | SWT.SINGLE);
		repoText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		repoText.setMessage("Repository name (leave empty for all)"); //$NON-NLS-1$

		// Max results
		Label maxLabel= new Label(composite, SWT.NONE);
		maxLabel.setText("Max results:"); //$NON-NLS-1$

		maxResultsSpinner= new Spinner(composite, SWT.BORDER);
		maxResultsSpinner.setMinimum(1);
		maxResultsSpinner.setMaximum(100);
		maxResultsSpinner.setSelection(10);

		// Info label
		Label infoLabel= new Label(composite, SWT.NONE);
		infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		infoLabel.setText("\u2139 Uses local AI embeddings (offline, no network required)"); //$NON-NLS-1$

		setControl(composite);
	}

	@Override
	public boolean performAction() {
		String text= queryText != null ? queryText.getText().trim() : ""; //$NON-NLS-1$
		if (text.isEmpty()) {
			return false;
		}

		if (EmbeddedSearchService.getInstance().getSearchClient() == null) {
			setErrorMessage("Semantic search service is not available. Please wait for the database to initialize."); //$NON-NLS-1$
			return false;
		}

		SearchMode mode;
		if (hybridRadio != null && hybridRadio.getSelection()) {
			mode= SearchMode.HYBRID;
		} else if (similarRadio != null && similarRadio.getSelection()) {
			mode= SearchMode.SIMILAR;
		} else {
			mode= SearchMode.SEMANTIC;
		}

		String repo= repoText != null ? repoText.getText().trim() : ""; //$NON-NLS-1$
		int maxResults= maxResultsSpinner != null ? maxResultsSpinner.getSelection() : 10;

		SemanticCodeSearchQuery query= new SemanticCodeSearchQuery(text, repo, maxResults, mode);
		NewSearchUI.runQueryInBackground(query);
		return true;
	}

	@Override
	public void setContainer(ISearchPageContainer container) {
		this.container= container;
	}

	/**
	 * Returns the search page container.
	 *
	 * @return the container
	 */
	protected ISearchPageContainer getContainer() {
		return container;
	}
}
