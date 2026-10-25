/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.net.URI;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/** Explicit server addresses shared by the Git search clients. */
public class GitSearchPreferences extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private static final String QUALIFIER= "sandbox_extra_search"; //$NON-NLS-1$
	private static final String REST= "restUrl"; //$NON-NLS-1$
	private static final String GIT= "gitUrl"; //$NON-NLS-1$
	private static final String DEFAULT_GIT= "http://localhost:8443/git"; //$NON-NLS-1$
	private StringFieldEditor rest;
	private StringFieldEditor git;

	public GitSearchPreferences() {
		super(GRID);
		var store= new ScopedPreferenceStore(InstanceScope.INSTANCE, QUALIFIER);
		store.setDefault(REST, SemanticSearchClient.DEFAULT_BASE_URL);
		store.setDefault(GIT, DEFAULT_GIT);
		setPreferenceStore(store);
		setDescription("Addresses of the separate Sandbox Git server. Admin tokens are requested per rebuild and are not saved."); //$NON-NLS-1$
	}

	@Override public void init(IWorkbench workbench) { }

	@Override protected void createFieldEditors() {
		rest= new StringFieldEditor(REST, "REST base URL:", getFieldEditorParent()); //$NON-NLS-1$
		git= new StringFieldEditor(GIT, "Git base URL:", getFieldEditorParent()); //$NON-NLS-1$
		addField(rest);
		addField(git);
	}

	@Override public boolean performOk() {
		try {
			validateUrl(rest.getStringValue());
			validateUrl(git.getStringValue());
			if (!super.performOk()) return false;
			SemanticSearchClient.initialize(rest.getStringValue());
			return true;
		} catch (IllegalArgumentException invalid) {
			setErrorMessage(invalid.getMessage());
			return false;
		}
	}

	static String restUrl() {
		return InstanceScope.INSTANCE.getNode(QUALIFIER).get(REST, SemanticSearchClient.DEFAULT_BASE_URL);
	}

	static String gitUrl() {
		return validateUrl(InstanceScope.INSTANCE.getNode(QUALIFIER).get(GIT, DEFAULT_GIT));
	}

	static String validateUrl(String value) {
		URI uri= URI.create(value.strip());
		if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null //$NON-NLS-1$ //$NON-NLS-2$
				|| uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
			throw new IllegalArgumentException("Enter an HTTP(S) server URL without credentials, query or fragment."); //$NON-NLS-1$
		}
		return uri.toString().replaceAll("/+$", ""); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
