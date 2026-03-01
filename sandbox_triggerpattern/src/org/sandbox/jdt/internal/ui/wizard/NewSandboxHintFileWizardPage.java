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

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;

/**
 * First wizard page for the New Sandbox Hint File wizard.
 *
 * <p>Lets the user choose the target container (project/folder), file name,
 * metadata (ID, description, severity, min Java version, tags), and a
 * template.</p>
 *
 * @since 1.5.0
 */
public class NewSandboxHintFileWizardPage extends WizardPage {

	private static final String FILE_EXTENSION = ".sandbox-hint"; //$NON-NLS-1$

	private Text containerText;
	private Text fileText;
	private Text idText;
	private Text descriptionText;
	private Combo severityCombo;
	private Combo minJavaCombo;
	private Text tagsText;
	private SandboxHintTemplates selectedTemplate = SandboxHintTemplates.SIMPLE;

	private final IStructuredSelection selection;

	/**
	 * Creates the wizard page.
	 *
	 * @param selection the current workbench selection
	 */
	protected NewSandboxHintFileWizardPage(IStructuredSelection selection) {
		super("newSandboxHintFilePage"); //$NON-NLS-1$
		setTitle("New Sandbox Hint File"); //$NON-NLS-1$
		setDescription("Create a new .sandbox-hint file with transformation rules"); //$NON-NLS-1$
		this.selection = selection;
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(3, false);
		container.setLayout(layout);

		ModifyListener modifyListener = e -> dialogChanged();

		// --- Container selection ---
		Label containerLabel = new Label(container, SWT.NONE);
		containerLabel.setText("&Container:"); //$NON-NLS-1$

		containerText = new Text(container, SWT.BORDER | SWT.SINGLE);
		containerText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		containerText.addModifyListener(modifyListener);

		Button browseButton = new Button(container, SWT.PUSH);
		browseButton.setText("&Browse..."); //$NON-NLS-1$
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				handleBrowse();
			}
		});

		// --- File name ---
		Label fileLabel = new Label(container, SWT.NONE);
		fileLabel.setText("&File name:"); //$NON-NLS-1$

		fileText = new Text(container, SWT.BORDER | SWT.SINGLE);
		fileText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		fileText.addModifyListener(modifyListener);
		new Label(container, SWT.NONE); // spacer

		// --- Metadata group ---
		Group metadataGroup = new Group(container, SWT.NONE);
		metadataGroup.setText("Metadata"); //$NON-NLS-1$
		metadataGroup.setLayout(new GridLayout(2, false));
		GridData groupData = new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1);
		metadataGroup.setLayoutData(groupData);

		new Label(metadataGroup, SWT.NONE).setText("&ID:"); //$NON-NLS-1$
		idText = new Text(metadataGroup, SWT.BORDER | SWT.SINGLE);
		idText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		idText.addModifyListener(modifyListener);

		new Label(metadataGroup, SWT.NONE).setText("D&escription:"); //$NON-NLS-1$
		descriptionText = new Text(metadataGroup, SWT.BORDER | SWT.SINGLE);
		descriptionText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		new Label(metadataGroup, SWT.NONE).setText("&Severity:"); //$NON-NLS-1$
		severityCombo = new Combo(metadataGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
		severityCombo.setItems("info", "warning", "error", "hint"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		severityCombo.select(0);
		severityCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		new Label(metadataGroup, SWT.NONE).setText("Min &Java:"); //$NON-NLS-1$
		minJavaCombo = new Combo(metadataGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
		minJavaCombo.setItems("", "11", "17", "21"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		minJavaCombo.select(0);
		minJavaCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		new Label(metadataGroup, SWT.NONE).setText("&Tags:"); //$NON-NLS-1$
		tagsText = new Text(metadataGroup, SWT.BORDER | SWT.SINGLE);
		tagsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		tagsText.setMessage("encoding, modernization"); //$NON-NLS-1$

		// --- Template group ---
		Group templateGroup = new Group(container, SWT.NONE);
		templateGroup.setText("Template"); //$NON-NLS-1$
		templateGroup.setLayout(new GridLayout(1, false));
		GridData tplData = new GridData(SWT.FILL, SWT.FILL, true, false, 3, 1);
		templateGroup.setLayoutData(tplData);

		for (SandboxHintTemplates tpl : SandboxHintTemplates.values()) {
			Button radio = new Button(templateGroup, SWT.RADIO);
			radio.setText(tpl.getLabel());
			radio.setData(tpl);
			if (tpl == SandboxHintTemplates.SIMPLE) {
				radio.setSelection(true);
			}
			radio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (((Button) e.widget).getSelection()) {
						selectedTemplate = (SandboxHintTemplates) ((Button) e.widget).getData();
					}
				}
			});
		}

		// --- Initialize from selection ---
		initializeFromSelection();

		dialogChanged();
		setControl(container);
	}

	/**
	 * Pre-fills the container path from the workbench selection.
	 */
	private void initializeFromSelection() {
		if (selection == null || selection.isEmpty()) {
			return;
		}
		Object element = selection.getFirstElement();
		if (element instanceof IResource resource) {
			IContainer container;
			if (resource instanceof IContainer c) {
				container = c;
			} else {
				container = resource.getParent();
			}
			containerText.setText(container.getFullPath().toString());
		}
		fileText.setText("rules" + FILE_EXTENSION); //$NON-NLS-1$
	}

	private void handleBrowse() {
		ContainerSelectionDialog dialog = new ContainerSelectionDialog(
				getShell(), ResourcesPlugin.getWorkspace().getRoot(), false,
				"Select the target folder"); //$NON-NLS-1$
		if (dialog.open() == ContainerSelectionDialog.OK) {
			Object[] result = dialog.getResult();
			if (result.length == 1) {
				containerText.setText(((IPath) result[0]).toString());
			}
		}
	}

	private void dialogChanged() {
		String container = containerText.getText();
		String file = fileText.getText();

		if (container.isEmpty()) {
			updateStatus("Container must be specified"); //$NON-NLS-1$
			return;
		}
		IResource resource = ResourcesPlugin.getWorkspace().getRoot()
				.findMember(IPath.fromOSString(container));
		if (resource == null || !(resource instanceof IContainer) || !resource.isAccessible()) {
			updateStatus("Container must exist and be accessible"); //$NON-NLS-1$
			return;
		}
		if (file.isEmpty()) {
			updateStatus("File name must be specified"); //$NON-NLS-1$
			return;
		}
		if (!file.endsWith(FILE_EXTENSION)) {
			updateStatus("File name must end with " + FILE_EXTENSION); //$NON-NLS-1$
			return;
		}
		if (file.replace('\\', '/').indexOf('/') >= 0) {
			updateStatus("File name must not contain path separators"); //$NON-NLS-1$
			return;
		}
		updateStatus(null);
	}

	private void updateStatus(String message) {
		setErrorMessage(message);
		setPageComplete(message == null);
	}

	// --- Public accessors for the wizard ---

	/** Returns the container path entered by the user. */
	public String getContainerPath() {
		return containerText.getText();
	}

	/** Returns the file name entered by the user. */
	public String getFileName() {
		return fileText.getText();
	}

	/** Returns the hint ID entered by the user (may be empty). */
	public String getHintId() {
		String text = idText.getText().trim();
		return text.isEmpty() ? null : text;
	}

	/** Returns the description entered by the user (may be empty). */
	public String getHintDescription() {
		String text = descriptionText.getText().trim();
		return text.isEmpty() ? null : text;
	}

	/** Returns the selected severity string. */
	public String getSeverityValue() {
		return severityCombo.getText();
	}

	/** Returns the selected minimum Java version, or 0 if none. */
	public int getMinJavaVersion() {
		String text = minJavaCombo.getText().trim();
		if (text.isEmpty()) {
			return 0;
		}
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Returns the raw tags text (comma-separated). */
	public String getTagsText() {
		return tagsText.getText().trim();
	}

	/** Returns the template selected by the user. */
	public SandboxHintTemplates getSelectedTemplate() {
		return selectedTemplate;
	}
}
