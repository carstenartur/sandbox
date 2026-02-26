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
package org.sandbox.jdt.internal.ui.handlers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/**
 * Eclipse command handler that infers a TriggerPattern DSL rule from a
 * unified diff pasted or selected in the active editor.
 *
 * <p>The handler reads the current text selection, sends it to
 * {@link AiRuleInferenceEngine#inferRuleFromDiff(String)}, and opens the
 * resulting DSL rule as a new {@code .sandbox-hint} file.</p>
 *
 * @since 1.2.6
 */
public class MineDiffHandler extends AbstractHandler {

	private static final ILog LOG = Platform.getLog(MineDiffHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (!(editor instanceof ITextEditor textEditor)) {
			return null;
		}

		ISelection sel = textEditor.getSelectionProvider().getSelection();
		if (!(sel instanceof ITextSelection textSelection)) {
			return null;
		}

		String diffText = textSelection.getText();
		if (diffText == null || diffText.isBlank()) {
			return null;
		}

		EclipseLlmService llmService = EclipseLlmService.getInstance();
		if (!llmService.isAvailable()) {
			return null;
		}

		AiRuleInferenceEngine engine = llmService.getEngine();
		Optional<CommitEvaluation> result = engine.inferRuleFromDiff(diffText);
		if (result.isPresent()) {
			String dslRule = result.get().dslRule();
			if (dslRule != null && !dslRule.isBlank()) {
				openHintFile(event, dslRule);
			}
		}
		return null;
	}

	private static void openHintFile(ExecutionEvent event, String ruleContent) {
		try {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			if (projects.length == 0) {
				return;
			}
			IProject project = projects[0];
			String fileName = "mined-diff-" + System.currentTimeMillis() + ".sandbox-hint"; //$NON-NLS-1$ //$NON-NLS-2$
			IFile file = project.getFile(new Path(fileName));
			file.create(
					new ByteArrayInputStream(ruleContent.getBytes(StandardCharsets.UTF_8)),
					true, null);
			IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
			if (page != null) {
				IDE.openEditor(page, file);
			}
		} catch (Exception e) {
			LOG.error("Failed to open hint file for mined diff rule", e); //$NON-NLS-1$
		}
	}
}
