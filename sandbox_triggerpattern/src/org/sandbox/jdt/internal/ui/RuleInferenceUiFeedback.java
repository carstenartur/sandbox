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
package org.sandbox.jdt.internal.ui;

import java.util.function.Consumer;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/** User-visible, non-secret feedback for interactive TriggerPattern rule inference. */
public final class RuleInferenceUiFeedback {

	private static final String TITLE = "TriggerPattern Rule Inference"; //$NON-NLS-1$

	private RuleInferenceUiFeedback() {
	}

	/** Explains how to configure credentials instead of silently doing nothing. */
	public static void showConfigurationRequired() {
		show(shell -> MessageDialog.openWarning(shell, TITLE,
				"LLM rule inference is not configured. " //$NON-NLS-1$
						+ EclipseLlmService.getInstance().configurationHint()));
	}

	/** Reports a successful provider call that produced no usable DSL rule. */
	public static void showNoRule(String sourceDescription) {
		show(shell -> MessageDialog.openInformation(shell, TITLE,
				"No valid TriggerPattern DSL rule was inferred from " + sourceDescription + ". " //$NON-NLS-1$ //$NON-NLS-2$
						+ "Try a smaller, self-contained before/after change or author the rule with the Sandbox Hint File wizard.")); //$NON-NLS-1$
	}

	/** Reports a failed provider operation without echoing prompts, credentials, or response bodies. */
	public static void showFailure(String sourceDescription, Throwable failure) {
		String failureType = failure != null ? failure.getClass().getSimpleName() : "unknown error"; //$NON-NLS-1$
		show(shell -> MessageDialog.openError(shell, TITLE,
				"Rule inference failed for " + sourceDescription + " (" + failureType + "). " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ "Check the configured provider and Eclipse Error Log for technical details.")); //$NON-NLS-1$
	}

	private static void show(Consumer<Shell> dialog) {
		Display display = Display.getDefault();
		if (display == null || display.isDisposed()) {
			return;
		}
		display.asyncExec(() -> {
			if (display.isDisposed()) {
				return;
			}
			IWorkbenchWindow window = PlatformUI.isWorkbenchRunning()
					? PlatformUI.getWorkbench().getActiveWorkbenchWindow()
					: null;
			Shell shell = window != null ? window.getShell() : display.getActiveShell();
			if (shell != null && !shell.isDisposed()) {
				dialog.accept(shell);
			}
		});
	}
}
