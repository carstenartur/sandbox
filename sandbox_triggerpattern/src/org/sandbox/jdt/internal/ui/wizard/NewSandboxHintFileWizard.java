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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.sandbox.jdt.internal.ui.preferences.LlmPreferencePage;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.HintFileSerializer;
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

	@Override
	public void addPages() {
		filePage = new NewSandboxHintFileWizardPage(selection);
		rulePage = new NewRuleWizardPage();

		if (initialCodeSnippet != null && !initialCodeSnippet.isBlank()) {
			rulePage.setInitialSourcePattern(initialCodeSnippet);

			// Auto-trigger AI only if both configured AND available
			IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PLUGIN_ID);
			boolean autoAi = prefs.getBoolean(LlmPreferencePage.PREF_WIZARD_AUTO_AI, false);
			if (autoAi && EclipseLlmService.getInstance().isAvailable()) {
				rulePage.setAutoTriggerAi(true);
			}
		}

		addPage(filePage);
		addPage(rulePage);
	}

	@Override
	public boolean canFinish() {
		// Allow finish from page 1 when template is EMPTY
		if (getContainer().getCurrentPage() == filePage) {
			return filePage.isPageComplete()
					&& filePage.getSelectedTemplate() == SandboxHintTemplates.EMPTY;
		}
		// On the rule page, require both pages to be complete
		return filePage.isPageComplete() && rulePage.isPageComplete();
	}

	@Override
	public boolean performFinish() {
		String containerPath = filePage.getContainerPath();
		String fileName = filePage.getFileName();

		// Build HintFile model from page 1 metadata
		HintFile hintFile = new HintFile();
		hintFile.setId(filePage.getHintId());
		hintFile.setDescription(filePage.getHintDescription());
		hintFile.setSeverity(filePage.getSeverityValue());
		int minJava = filePage.getMinJavaVersion();
		if (minJava > 0) {
			hintFile.setMinJavaVersion(minJava);
		}
		String tagsText = filePage.getTagsText();
		if (tagsText != null && !tagsText.isBlank()) {
			List<String> tags = Arrays.stream(tagsText.split(",")) //$NON-NLS-1$
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.toList();
			if (!tags.isEmpty()) {
				hintFile.setTags(tags);
			}
		}

		// Serialize metadata
		HintFileSerializer serializer = new HintFileSerializer();
		String metadataContent = serializer.serialize(hintFile);

		// Append rule content from page 2 or template
		String ruleContent;
		SandboxHintTemplates template = filePage.getSelectedTemplate();
		if (template == SandboxHintTemplates.EMPTY) {
			ruleContent = ""; //$NON-NLS-1$
		} else if (rulePage.hasCustomContent()) {
			ruleContent = rulePage.getFullRuleBlock();
		} else {
			ruleContent = template.getRuleContent();
		}

		String fullContent = metadataContent + ruleContent;

		try {
			getContainer().run(true, false,
					monitor -> createFile(containerPath, fileName, fullContent, monitor));
		} catch (InvocationTargetException e) {
			MessageDialog.openError(getShell(), "Error", //$NON-NLS-1$
					"Could not create file: " + e.getTargetException().getMessage()); //$NON-NLS-1$
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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

	private void createFile(String containerPath, String fileName, String content,
			IProgressMonitor monitor) throws InvocationTargetException {
		monitor.beginTask("Creating " + fileName, 2); //$NON-NLS-1$
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IResource resource = root.findMember(new Path(containerPath));
		if (!(resource instanceof IContainer container) || !resource.exists()) {
			throw new InvocationTargetException(
					new CoreException(new Status(IStatus.ERROR, PLUGIN_ID,
							"Container \"" + containerPath + "\" does not exist."))); //$NON-NLS-1$ //$NON-NLS-2$
		}
		IFile file = container.getFile(new Path(fileName));
		try (InputStream stream = new ByteArrayInputStream(
				content.getBytes(StandardCharsets.UTF_8))) {
			if (file.exists()) {
				file.setContents(stream, true, true, monitor);
			} else {
				file.create(stream, true, monitor);
			}
		} catch (CoreException | java.io.IOException e) {
			throw new InvocationTargetException(e);
		}
		monitor.worked(1);
		monitor.done();
	}
}
