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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.sandbox.jdt.internal.ui.preferences.LlmPreferencePage;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/**
 * Wizard for creating new {@code .sandbox-hint} files.
 *
 * <p>The wizard provides two pages:
 * <ol>
 *   <li>{@link NewSandboxHintFileWizardPage} &ndash; container/file name, metadata, and template selection</li>
 *   <li>{@link NewRuleWizardPage} &ndash; optional rule editor with live validation, preview, and AI generation</li>
 * </ol>
 *
 * <p>When opened from a code selection (via {@link #setInitialCodeSnippet(String)}),
 * the source pattern field on page 2 is pre-filled with the selected code.</p>
 *
 * @since 1.5.0
 */
public class NewSandboxHintFileWizard extends Wizard implements INewWizard {

	private static final String PLUGIN_ID = "sandbox_triggerpattern"; //$NON-NLS-1$

	private NewSandboxHintFileWizardPage filePage;
	private NewRuleWizardPage rulePage;
	private IStructuredSelection selection;
	private IWorkbench workbench;
	private String initialCodeSnippet;
	private String initialReplacement;
	private RuleExampleSuite initialExamples;
	private Consumer<String> projectPreviewAction;

	public NewSandboxHintFileWizard() {
		setWindowTitle("New Sandbox Hint File"); //$NON-NLS-1$
		setNeedsProgressMonitor(true);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;
		this.selection = selection;
	}

	/**
	 * Sets an initial code snippet that will pre-fill the source pattern
	 * field on the rule editor page. Called by
	 * {@code NewHintFromSelectionHandler} when the wizard is opened from a
	 * code selection.
	 *
	 * @param code the selected code snippet
	 */
	public void setInitialCodeSnippet(String code) {
		this.initialCodeSnippet = code;
	}

	/** Sets the actual Java change to generalize without inferring placeholders. */
	public void setInitialChange(String before, String after) {
		initialCodeSnippet = before;
		initialReplacement = after;
	}

	/**
	 * Seeds evidence with the complete saved and current compilation unit.
	 * Blank units cannot be replayed; their change remains editable in the rule draft.
	 */
	public void setInitialExample(String unitName, String before, String after) {
		if (before.isBlank() || after.isBlank()) {
			return;
		}
		initialExamples = new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, List.of(
				new RuleExampleSuite.Example("Saved edit", unitName, before, after, true))); //$NON-NLS-1$
	}

	/** Overrides the project preview entry point, for embedding in another context. */
	public void setProjectPreviewAction(Consumer<String> action) {
		projectPreviewAction = action;
		refreshProjectPreviewAction();
	}

	@Override
	public void addPages() {
		filePage = new NewSandboxHintFileWizardPage(selection);
		rulePage = new NewRuleWizardPage();
		rulePage.setExamples(initialExamples);
		rulePage.setExampleProjectSupplier(this::selectedJavaProject);
		filePage.setContainerChangeListener(this::refreshProjectPreviewAction);
		rulePage.setMetadataSupplier(filePage::getEditedMetadata);
		rulePage.setDefaultContentSupplier(this::getTemplateContent);

		if (initialCodeSnippet != null && (!initialCodeSnippet.isBlank() || initialReplacement != null)) {
			if (initialReplacement == null) {
				rulePage.setInitialSourcePattern(initialCodeSnippet);
			} else {
				rulePage.setInitialChange(initialCodeSnippet, initialReplacement);
			}

			// Auto-trigger AI only if both configured AND available
			IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PLUGIN_ID);
			boolean autoAi = prefs.getBoolean(LlmPreferencePage.PREF_WIZARD_AUTO_AI, false);
			if (autoAi && EclipseLlmService.getInstance().isAvailable()) {
				rulePage.setAutoTriggerAi(true);
			}
		}

		refreshProjectPreviewAction();

		addPage(filePage);
		addPage(rulePage);
	}

	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		if (page == filePage) {
			refreshProjectPreviewAction();
		}
		return super.getNextPage(page);
	}

	private void refreshProjectPreviewAction() {
		if (rulePage == null) {
			return;
		}
		if (projectPreviewAction == null && selectedJavaProject() == null) {
			rulePage.setProjectPreviewAction(null);
			return;
		}
		rulePage.setProjectPreviewAction(ignored -> {
			if (projectPreviewAction != null) {
				projectPreviewAction.accept(getFullDslDocument());
				return;
			}
			IJavaProject project = selectedJavaProject();
			if (project == null) {
				refreshProjectPreviewAction();
				return;
			}
			ProjectRulePreview.open(getShell(), project, getFullDslDocument());
		});
	}

	@Override
	public boolean canFinish() {
		// Allow finish from page 1 when template is EMPTY
		if (getContainer().getCurrentPage() == filePage) {
			return filePage.isPageComplete()
					&& filePage.getSelectedTemplate() == SandboxHintTemplates.EMPTY
					&& (!rulePage.hasCustomContent() || rulePage.isPageComplete());
		}
		// On the rule page, require both pages to be complete
		return filePage.isPageComplete() && rulePage.isPageComplete();
	}

	@Override
	public boolean performFinish() {
		String containerPath = filePage.getContainerPath();
		String fileName = filePage.getFileName();
		String fullContent = getFullDslDocument();
		RuleExampleSuite examples = rulePage.getExamples();

		try {
			getContainer().run(true, true,
					monitor -> createFile(containerPath, fileName, fullContent, examples, monitor));
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof OperationCanceledException) {
				return false;
			}
			MessageDialog.openError(getShell(), "Error", //$NON-NLS-1$
					"Could not create file: " + e.getTargetException().getMessage()); //$NON-NLS-1$
			return false;
		} catch (InterruptedException | OperationCanceledException e) {
			return false;
		}

		// Open the newly created file in the editor
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IResource resource = root.findMember(new Path(containerPath));
		if (resource instanceof IContainer container) {
			IFile file = container.getFile(new Path(fileName));
			IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
			if (page != null) {
				try {
					IDE.openEditor(page, file, true);
				} catch (PartInitException e) {
					// ignore - file was created successfully
				}
			}
		}

		return true;
	}

	/** The same complete document is previewed, copied and finally written. */
	public String getFullDslDocument() {
		if (rulePage.hasCustomContent()) {
			rulePage.applyPendingMetadata();
			return rulePage.getFullDslDocument();
		}
		return new RuleAuthoringDocument(getTemplateContent()).withMetadata(filePage.getEditedMetadata()).content();
	}

	private String getTemplateContent() {
		SandboxHintTemplates template = filePage.getSelectedTemplate();
		String content = template.getRuleContent();
		if (template != SandboxHintTemplates.PLAN_AWARE && template != SandboxHintTemplates.EMPTY) {
			content = new RuleAuthoringDocument(content)
					.withMetadata(Map.of("binding-policy", "required")).content(); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return content;
	}

	/** Resolves preview scope from the destination currently entered on the first page. */
	private IJavaProject selectedJavaProject() {
		if (filePage == null || filePage.getContainerPath().isEmpty()) {
			return null;
		}
		IResource resource = ResourcesPlugin.getWorkspace().getRoot()
				.findMember(new Path(filePage.getContainerPath()));
		if (!(resource instanceof IContainer container) || !container.isAccessible()
				|| container.getProject() == null) {
			return null;
		}
		IJavaProject project = JavaCore.create(container.getProject());
		return project != null && project.exists() ? project : null;
	}

	private void createFile(String containerPath, String fileName, String content,
			RuleExampleSuite examples, IProgressMonitor monitor) throws InvocationTargetException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IResource resource = root.findMember(new Path(containerPath));
		if (!(resource instanceof IContainer container) || !resource.exists()) {
			throw new InvocationTargetException(
					new CoreException(new Status(IStatus.ERROR, PLUGIN_ID,
							"Container \"" + containerPath + "\" does not exist."))); //$NON-NLS-1$ //$NON-NLS-2$
		}
		try {
			HintFileCreation.create(container, fileName, content, examples, monitor);
		} catch (CoreException e) {
			throw new InvocationTargetException(e);
		}
	}
}
