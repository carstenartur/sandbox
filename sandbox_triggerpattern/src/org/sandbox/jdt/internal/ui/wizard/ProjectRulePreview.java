/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Shared entry point for real project trials from the wizard and hint editor. */
public final class ProjectRulePreview {

	private ProjectRulePreview() {
	}

	public static void open(Shell shell, IJavaProject project, String dsl) {
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(project, dsl);
		RefactoringWizard wizard= new RefactoringWizard(refactoring,
				RefactoringWizard.DIALOG_BASED_USER_INTERFACE | RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE
						| RefactoringWizard.CHECK_INITIAL_CONDITIONS_ON_OPEN) {
			@Override
			protected void addUserInputPages() {
				addPage(new WizardPage("candidate-explanations") { //$NON-NLS-1$
					{
						setTitle("Try TriggerPattern on my project"); //$NON-NLS-1$
						setDescription("Review the candidates, then use Preview for the actual source changes."); //$NON-NLS-1$
					}

					@Override
					public void createControl(Composite parent) {
						Composite container= new Composite(parent, SWT.NONE);
						container.setLayout(new GridLayout());
						Text details= new Text(container, SWT.READ_ONLY | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
						GridData data= new GridData(SWT.FILL, SWT.FILL, true, true);
						data.widthHint= 740;
						data.heightHint= 440;
						details.setLayoutData(data);
						details.setText(refactoring.getExplanation());
						setControl(container);
					}
				});
			}
		};
		wizard.setForcePreviewReview(true);
		wizard.setChangeCreationCancelable(true);
		try {
			new RefactoringWizardOpenOperation(wizard).run(shell, refactoring.getName());
		} catch (InterruptedException exception) {
			// The refactoring dialog treats interruption as cancellation.
		} catch (RuntimeException exception) {
			MessageDialog.openError(shell, "TriggerPattern project trial", exception.getMessage()); //$NON-NLS-1$
		}
	}
}
