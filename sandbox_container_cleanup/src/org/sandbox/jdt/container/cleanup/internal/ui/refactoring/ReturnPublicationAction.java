/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.refactoring;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.handlers.HandlerUtil;

import org.sandbox.jdt.container.api.ReturnPublicationPolicy;

/** Opens the interactive return-publication refactoring from a Java file selection. */
public final class ReturnPublicationAction extends AbstractHandler {
	@Override
	public Object execute(ExecutionEvent commandEvent) {
		if (!(HandlerUtil.getCurrentSelection(commandEvent) instanceof IStructuredSelection items) || items.size() != 1
				|| !(items.getFirstElement() instanceof ICompilationUnit selected)) {
			return null;
		}
		var refactoring= new ReturnPublicationRefactoring(selected);
		try {
			new RefactoringWizardOpenOperation(createWizard(refactoring)).run(HandlerUtil.getActiveShell(commandEvent), refactoring.getName());
		} catch (InterruptedException cancelled) {
			// The workbench closes a cancelled refactoring without applying source edits.
		}
		return null;
	}

	static RefactoringWizard createWizard(ReturnPublicationRefactoring refactoring) {
		var wizard= new RefactoringWizard(refactoring, RefactoringWizard.DIALOG_BASED_USER_INTERFACE
				| RefactoringWizard.CHECK_INITIAL_CONDITIONS_ON_OPEN | RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE) {
			@Override
			protected void addUserInputPages() {
				addPage(new UserInputWizardPage("returnPublication") { //$NON-NLS-1$
					@Override
					public void createControl(Composite parent) {
						setTitle(refactoring.getName());
						setDescription("Publish the builder as a read-only list. Its internal return signature and all callers change together."); //$NON-NLS-1$
						var control= new Composite(parent, SWT.NONE);
						control.setLayout(new GridLayout());
						var preserve= new Button(control, SWT.RADIO);
						preserve.setText("Preserve null elements and encounter order in a read-only view"); //$NON-NLS-1$
						var reject= new Button(control, SWT.RADIO);
						reject.setText("Copy to an immutable list and throw NullPointerException at publication for any null element (Java 10+)"); //$NON-NLS-1$
						reject.setEnabled(refactoring.supportsCopy());
						preserve.addListener(SWT.Selection, event -> {
							if (preserve.getSelection()) {
								refactoring.select(ReturnPublicationPolicy.PRESERVE_NULLS);
								setPageComplete(true);
							}
						});
						reject.addListener(SWT.Selection, event -> {
							if (reject.getSelection()) {
								refactoring.select(ReturnPublicationPolicy.REJECT_NULLS);
								setPageComplete(true);
							}
						});
						setControl(control);
						setPageComplete(false);
					}
				});
			}
		};
		wizard.setForcePreviewReview(true);
		wizard.setChangeCreationCancelable(true);
		return wizard;
	}
}
