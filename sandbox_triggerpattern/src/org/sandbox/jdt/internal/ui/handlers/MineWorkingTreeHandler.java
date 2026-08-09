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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;
import org.sandbox.jdt.internal.ui.RuleInferenceUiFeedback;
import org.sandbox.jdt.internal.ui.views.mining.CommitAnalysisJob;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;
import org.sandbox.jdt.triggerpattern.mining.git.WorkingTreeDiffProvider;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/**
 * Mines Java source changes between {@code HEAD} and the active project's
 * current working tree for reusable TriggerPattern DSL rules.
 *
 * <p>Both staged and unstaged filesystem content are represented, because the
 * comparison is made directly from the committed HEAD tree to the working tree.
 * The target project is derived from the active editor instead of silently using
 * the first project in the workspace.</p>
 *
 * @since 1.2.6
 */
public class MineWorkingTreeHandler extends AbstractHandler {

	private static final ILog LOG = Platform.getLog(MineWorkingTreeHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorInput input = HandlerUtil.getActiveEditorInput(event);
		IFile activeFile = input != null ? input.getAdapter(IFile.class) : null;
		if (activeFile == null || activeFile.getProject().getLocation() == null) {
			return null;
		}
		IProject project = activeFile.getProject();
		Path repositoryPath = project.getLocation().toFile().toPath();

		EclipseLlmService llmService = EclipseLlmService.getInstance();
		if (!llmService.isAvailable()) {
			RuleInferenceUiFeedback.showConfigurationRequired();
			return null;
		}

		Job job = new Job("Mining working tree for DSL rules") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					return mineWorkingTree(repositoryPath, project, monitor);
				} catch (RuntimeException e) {
					LOG.error("Failed to infer DSL rules from the working tree", e); //$NON-NLS-1$
					RuleInferenceUiFeedback.showFailure("the Java working-tree changes", e); //$NON-NLS-1$
					return Status.error("Working-tree DSL inference failed", e); //$NON-NLS-1$
				}
			}
		};
		job.setUser(true);
		job.schedule();
		return null;
	}

	private static IStatus mineWorkingTree(Path repositoryPath, IProject project,
			IProgressMonitor monitor) {
		List<FileDiff> diffs = new WorkingTreeDiffProvider().getDiffs(repositoryPath);
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		if (diffs.isEmpty()) {
			RuleInferenceUiFeedback.showInformation(
					"No Java working-tree changes relative to HEAD were found in the active project."); //$NON-NLS-1$
			return Status.OK_STATUS;
		}

		AiRuleInferenceEngine engine = EclipseLlmService.getInstance().getEngine();
		List<String> rules = new ArrayList<>();
		monitor.beginTask("Inferring rules from working-tree Java changes", diffs.size()); //$NON-NLS-1$
		try {
			for (FileDiff diff : diffs) {
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				monitor.subTask(diff.filePath());
				String unifiedDiff = CommitAnalysisJob.buildUnifiedDiff(diff);
				engine.inferRuleFromDiff(unifiedDiff)
						.map(CommitEvaluation::dslRule)
						.filter(rule -> rule != null && !rule.isBlank())
						.ifPresent(rules::add);
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}

		if (rules.isEmpty()) {
			RuleInferenceUiFeedback.showNoRule("the Java working-tree changes"); //$NON-NLS-1$
			return Status.OK_STATUS;
		}

		String content = String.join("\n\n;;\n\n", rules); //$NON-NLS-1$
		openHintFileOnUi(project, content);
		return Status.OK_STATUS;
	}

	private static void openHintFileOnUi(IProject project, String ruleContent) {
		Display.getDefault().asyncExec(() -> {
			try {
				String fileName = "mined-workingtree-" //$NON-NLS-1$
						+ System.currentTimeMillis() + ".sandbox-hint"; //$NON-NLS-1$
				IFile file = project.getFile(new org.eclipse.core.runtime.Path(fileName));
				file.create(
						new ByteArrayInputStream(ruleContent.getBytes(StandardCharsets.UTF_8)),
						true, null);
				IWorkbenchPage page = PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage();
				if (page != null) {
					IDE.openEditor(page, file);
				}
			} catch (Exception e) {
				LOG.error("Failed to open hint file for working tree rules", e); //$NON-NLS-1$
				RuleInferenceUiFeedback.showFailure("the generated working-tree hint file", e); //$NON-NLS-1$
			}
		});
	}
}
