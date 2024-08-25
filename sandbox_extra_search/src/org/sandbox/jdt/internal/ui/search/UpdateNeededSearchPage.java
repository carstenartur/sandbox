/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.search.JavaSearchQuery;
import org.eclipse.jdt.internal.ui.search.JavaSearchScopeFactory;
import org.eclipse.jdt.internal.ui.search.SearchUtil;
import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.QuerySpecification;
import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.search.ui.ISearchPage;
import org.eclipse.search.ui.ISearchPageContainer;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkingSet;

public class UpdateNeededSearchPage extends DialogPage implements ISearchPage {

	public static class SearchSettingsData extends HashMap<String, Boolean> {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		public SearchSettingsData(int limitTo, int includeMask, int scope) {
			this.limitTo = limitTo;
			this.includeMask = includeMask;
			this.scope = scope;
		}

		public int getLimitTo() {
			return limitTo;
		}

		public void setLimitTo(int limitTo) {
			this.limitTo = limitTo;
		}

		public int getIncludeMask() {
			return includeMask;
		}

		public void setIncludeMask(int includeMask) {
			this.includeMask = includeMask;
		}

		public int getScope() {
			return scope;
		}

		public void setScope(int scope) {
			this.scope = scope;
		}

		int limitTo;

		int includeMask;

		int scope;

		public static SearchSettingsData create(IDialogSettings settings, Map<String, Set<String>> listofClassLists) {
			int limitto;
			try {
				limitto = settings.getInt("limitTo"); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				limitto = IJavaSearchConstants.REFERENCES;
			}
			int includemask;
			try {
				includemask = settings.getInt("includeMask"); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				includemask = JavaSearchScopeFactory.NO_JRE;
			}
			int myscope;
			try {
				myscope = settings.getInt("scope"); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				myscope = ISearchPageContainer.WORKSPACE_SCOPE;
			}
			SearchSettingsData ssd = new SearchSettingsData(limitto, includemask, myscope);
			listofClassLists.keySet().forEach(lc -> {
				ssd.put(lc, settings.getBoolean(lc));
			});
			return ssd;
		}

		public void store(IDialogSettings settings, Map<String, Set<String>> listofClassLists) {
			settings.put("scope", scope); //$NON-NLS-1$
			settings.put("limitTo", limitTo); //$NON-NLS-1$
			settings.put("includeMask", includeMask); //$NON-NLS-1$
			listofClassLists.keySet().forEach(lc -> {
				settings.put(lc, this.get(lc));
			});
		}

	}

	private final static String PAGE_NAME = "UpdateNeededSearchPage"; //$NON-NLS-1$

	private ISearchPageContainer fContainer;

	Map<String, Set<String>> listofClassLists;

	private IDialogSettings fDialogSettings;

	private SearchSettingsData leaveoutsearch;

	@Override
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);
		listofClassLists = readClassList();
		readConfiguration();

		ScrolledComposite scrolledComposite = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.BORDER);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);

		Composite composite = new Composite(scrolledComposite, SWT.NONE);
		composite.setLayout(new RowLayout(SWT.VERTICAL));

		Label lblNewLabel = new Label(composite, SWT.NONE);
		lblNewLabel.setText("Select legacy classes to search for"); //$NON-NLS-1$
		listofClassLists.keySet().forEach(lc -> {
			Button btnCheckButton = new Button(composite, SWT.CHECK);
			btnCheckButton.setSelection(!leaveoutsearch.get(lc));
			btnCheckButton.setText(lc);
			btnCheckButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Button button = (Button) e.widget;
					if (button.getSelection()) {
						leaveoutsearch.put(lc, Boolean.FALSE);
					} else {
						leaveoutsearch.put(lc, Boolean.TRUE);
					}
				}
			});
		});
		scrolledComposite.setContent(composite);
		scrolledComposite.setMinSize(composite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		setControl(scrolledComposite);
	}

	@Override
	public void dispose() {
		writeConfiguration();
		super.dispose();
	}

	private IDialogSettings getDialogSettings() {
		if (fDialogSettings == null) {
			fDialogSettings = JavaPlugin.getDefault().getDialogSettingsSection(PAGE_NAME);
		}
		return fDialogSettings;
	}

	/**
	 * Initializes itself from the stored page settings.
	 */
	private void readConfiguration() {
		leaveoutsearch = SearchSettingsData.create(getDialogSettings(), listofClassLists);
	}

	/**
	 * Stores the current configuration in the dialog store.
	 */
	private void writeConfiguration() {
		leaveoutsearch.store(getDialogSettings(), listofClassLists);
	}

	@Override
	public boolean performAction() {
		return performNewSearch();
	}

	/**
	 * Returns the search page's container.
	 *
	 * @return the search page container
	 */
	private ISearchPageContainer getContainer() {
		return fContainer;
	}

	private boolean performNewSearch() {
		// Setup search scope
		IJavaSearchScope scope = null;
		String scopeDescription = ""; //$NON-NLS-1$
		int limitTo = IJavaSearchConstants.ALL_OCCURRENCES;
		int includeMask = IJavaSearchScope.SOURCES;
		JavaSearchScopeFactory factory = JavaSearchScopeFactory.getInstance();

		switch (getContainer().getSelectedScope()) {
		case ISearchPageContainer.WORKSPACE_SCOPE:
			scopeDescription = factory.getWorkspaceScopeDescription(includeMask);
			scope = factory.createWorkspaceScope(includeMask);
			break;

		case ISearchPageContainer.SELECTION_SCOPE:
			IJavaElement[] javaElements = {};

			if (getContainer().getActiveEditorInput() != null) {
				IFile file = getContainer().getActiveEditorInput().getAdapter(IFile.class);

				if (file != null && file.exists()) {
					IJavaElement javaElement = JavaCore.create(file);

					if (javaElement != null) {
						javaElements = new IJavaElement[] { javaElement };
					}
				}
			} else {
				javaElements = factory.getJavaElements(getContainer().getSelection());
			}

			scope = factory.createJavaSearchScope(javaElements, includeMask);
			scopeDescription = factory.getSelectionScopeDescription(javaElements, includeMask);
			break;

		case ISearchPageContainer.SELECTED_PROJECTS_SCOPE:
			String[] projectNames = getContainer().getSelectedProjectNames();
			scope = factory.createJavaProjectSearchScope(projectNames, includeMask);
			scopeDescription = factory.getProjectScopeDescription(projectNames, includeMask);
			break;

		case ISearchPageContainer.WORKING_SET_SCOPE:
			IWorkingSet[] workingSets = getContainer().getSelectedWorkingSets();

			// Should not happen - just to be sure
			if (workingSets == null || workingSets.length < 1) {
				return false;
			}

			scopeDescription = factory.getWorkingSetScopeDescription(workingSets, includeMask);
			scope = factory.createJavaSearchScope(workingSets, includeMask);
			SearchUtil.updateLRUWorkingSets(workingSets);
			break;

		default:
			break;
		}

		IJavaElement element = null;
		IWorkspaceRoot wspace = ResourcesPlugin.getWorkspace().getRoot();

		List<QuerySpecification> arl = new ArrayList<>();

		for (Entry<String, Set<String>> lc : listofClassLists.entrySet()) {
			if (leaveoutsearch.get(lc.getKey())) {
				continue;
			}

			for (String checkclass : lc.getValue()) {
				for (IProject pr : wspace.getProjects()) {
					try {
						if (pr.isNatureEnabled("org.eclipse.jdt.core.javanature")) { //$NON-NLS-1$
							IJavaProject project = JavaCore.create(pr);
							element = JavaModelUtil.findTypeContainer(project, checkclass);

							if (element instanceof IType) {
								break;
							}
						}
					} catch (CoreException e) {
						e.printStackTrace();
					}
				}

				if (element != null) {
					QuerySpecification querySpec = new ElementQuerySpecification(element, limitTo, scope,
							scopeDescription);
					arl.add(querySpec);
				}
			}
		}

		if (arl.isEmpty()) {
			return false;
		}

		JavaSearchQuery textSearchJob = new JavaSearchQuery(arl);
		NewSearchUI.runQueryInBackground(textSearchJob);
		return true;
	}

	/*
	 * Implements method from ISearchPage
	 */
	@Override
	public void setContainer(ISearchPageContainer container) {
		fContainer = container;
	}

	private Map<String, Set<String>> readClassList() {
		listofClassLists = new HashMap<>();
		String filename = "/org/sandbox/jdt/internal/ui/search/classlist.properties"; //$NON-NLS-1$
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(filename)) {

			Properties prop = new Properties();

			if (input == null) {
				return Collections.emptyMap();
			}

			prop.load(input);
			@SuppressWarnings({ "rawtypes", "unchecked" })
			Map<String, String> sprop = new HashMap(prop);
			sprop.forEach((key, value) -> listofClassLists.put(value, new HashSet<>()));
			sprop.forEach((key, value) -> listofClassLists.get(value).add(key));

		} catch (IOException e) {
			return Collections.emptyMap();
		}
		return listofClassLists;
	}
}
