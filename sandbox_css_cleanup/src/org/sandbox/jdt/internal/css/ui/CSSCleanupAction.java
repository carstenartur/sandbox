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
package org.sandbox.jdt.internal.css.ui;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import org.sandbox.jdt.internal.css.CSSCleanupPlugin;
import org.sandbox.jdt.internal.css.core.CSSValidationResult;
import org.sandbox.jdt.internal.css.core.NodeExecutor;
import org.sandbox.jdt.internal.css.core.PrettierRunner;
import org.sandbox.jdt.internal.css.core.StylelintRunner;
import org.sandbox.jdt.internal.css.preferences.CSSPreferenceConstants;

/**
 * Action to format and validate CSS files.
 */
public class CSSCleanupAction implements IObjectActionDelegate {

	private ISelection selection;
	private IWorkbenchPart targetPart;

	@Override
	public void run(IAction action) {
		if (!(selection instanceof IStructuredSelection)) {
			return;
		}

		Shell shell = targetPart.getSite().getShell();

		// Check if Node.js is available
		if (!NodeExecutor.isNodeAvailable()) {
			MessageDialog.openError(shell, "CSS Cleanup", //$NON-NLS-1$
					"Node.js is not installed or not in PATH.\n\n" + //$NON-NLS-1$
							"Please install Node.js from https://nodejs.org/\n" + //$NON-NLS-1$
							"and ensure 'node' and 'npx' are available in your PATH."); //$NON-NLS-1$
			return;
		}

		IStructuredSelection structuredSelection = (IStructuredSelection) selection;

		// Process each CSS file in a background job
		for (Object element : structuredSelection.toList()) {
			IResource resource = Adapters.adapt(element, IResource.class);
			if (resource instanceof IFile) {
				IFile file = (IFile) resource;
				if (isCSSFile(file)) {
					scheduleProcessingJob(file, shell);
				}
			}
		}
	}

	/**
	 * Schedule a background job to process the CSS file.
	 */
	private void scheduleProcessingJob(IFile file, Shell shell) {
		Job job = new Job("CSS Cleanup: " + file.getName()) { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					monitor.beginTask("Processing " + file.getName(), 100); //$NON-NLS-1$
					
					IPreferenceStore store = CSSCleanupPlugin.getDefault().getPreferenceStore();
					boolean enablePrettier = store.getBoolean(CSSPreferenceConstants.ENABLE_PRETTIER);
					boolean enableStylelint = store.getBoolean(CSSPreferenceConstants.ENABLE_STYLELINT);

					// Format with Prettier if enabled
					if (enablePrettier && !monitor.isCanceled()) {
						monitor.subTask("Formatting with Prettier..."); //$NON-NLS-1$
						String formatted = PrettierRunner.format(file);
						monitor.worked(50);

						if (formatted != null && !monitor.isCanceled()) {
							// Write back to file
							file.setContents(
									new java.io.ByteArrayInputStream(formatted.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
									IResource.KEEP_HISTORY,
									null);
							file.refreshLocal(IResource.DEPTH_ZERO, null);
						}
					} else {
						monitor.worked(50);
					}

					// Validate with Stylelint if enabled
					if (enableStylelint && !monitor.isCanceled()) {
						monitor.subTask("Validating with Stylelint..."); //$NON-NLS-1$
						CSSValidationResult result = StylelintRunner.validate(file);
						monitor.worked(50);

						if (!result.isValid() && !monitor.isCanceled()) {
							// Show validation warnings on UI thread
							Display.getDefault().asyncExec(() -> {
								StringBuilder msg = new StringBuilder("CSS validation issues:%n%n"); //$NON-NLS-1$
								for (CSSValidationResult.Issue issue : result.getIssues()) {
									msg.append(String.format("Line %d: [%s] %s%n", //$NON-NLS-1$
											Integer.valueOf(issue.line), issue.severity, issue.message));
								}
								MessageDialog.openWarning(shell, "CSS Validation", msg.toString()); //$NON-NLS-1$
							});
						}
					} else {
						monitor.worked(50);
					}

					monitor.done();
					return Status.OK_STATUS;

				} catch (Exception e) {
					// Show error dialog on UI thread
					Display.getDefault().asyncExec(() -> {
						MessageDialog.openError(shell, "CSS Cleanup Error", //$NON-NLS-1$
								"Failed to process CSS file: " + e.getMessage()); //$NON-NLS-1$
					});
					return new Status(IStatus.ERROR, CSSCleanupPlugin.PLUGIN_ID,
							"Failed to process CSS file", e); //$NON-NLS-1$
				}
			}
		};
		job.setUser(true); // Show in progress view
		job.schedule();
	}

	private boolean isCSSFile(IFile file) {
		String ext = file.getFileExtension();
		return ext != null && (ext.equals("css") || ext.equals("scss") || ext.equals("less")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}



	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;
	}

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		this.targetPart = targetPart;
	}
}
