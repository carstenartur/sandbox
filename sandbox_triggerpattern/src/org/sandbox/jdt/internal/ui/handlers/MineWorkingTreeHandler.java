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
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunk;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;
import org.sandbox.jdt.triggerpattern.mining.git.JGitHistoryProvider;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/**
 * Eclipse command handler that mines the working tree (uncommitted changes)
 * for TriggerPattern DSL rules using AI-powered inference.
 *
 * <p>The handler uses {@link JGitHistoryProvider} to obtain diffs of
 * uncommitted changes (against {@code HEAD}), sends each file diff to
 * {@link AiRuleInferenceEngine#inferRuleFromDiff(String)}, and opens a
 * new {@code .sandbox-hint} file with all inferred rules.</p>
 *
 * <p>Analysis runs in a background {@link Job} so the UI thread is not
 * blocked.</p>
 *
 * @since 1.2.6
 */
public class MineWorkingTreeHandler extends AbstractHandler {

	private static final ILog LOG = Platform.getLog(MineWorkingTreeHandler.class);
	private static final String HEAD = "HEAD"; //$NON-NLS-1$

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		EclipseLlmService llmService = EclipseLlmService.getInstance();
		if (!llmService.isAvailable()) {
			return null;
		}

		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		if (projects.length == 0) {
			return null;
		}
		IProject project = projects[0];
		Path repositoryPath = project.getLocation().toFile().toPath();

		Job job = new Job("Mining working tree for DSL rules") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return mineWorkingTree(repositoryPath, project, monitor);
			}
		};
		job.setUser(true);
		job.schedule();
		return null;
	}

	private static IStatus mineWorkingTree(Path repositoryPath, IProject project,
			IProgressMonitor monitor) {
		JGitHistoryProvider gitProvider = new JGitHistoryProvider();
		List<FileDiff> diffs = gitProvider.getDiffs(repositoryPath, HEAD);

		if (diffs.isEmpty() || monitor.isCanceled()) {
			return Status.OK_STATUS;
		}

		AiRuleInferenceEngine engine = EclipseLlmService.getInstance().getEngine();
		List<String> rules = new ArrayList<>();

		for (FileDiff diff : diffs) {
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			String unifiedDiff = buildUnifiedDiff(diff);
			engine.inferRuleFromDiff(unifiedDiff)
					.map(CommitEvaluation::dslRule)
					.filter(rule -> rule != null && !rule.isBlank())
					.ifPresent(rules::add);
		}

		if (!rules.isEmpty()) {
			String content = String.join("\n\n;;\n\n", rules); //$NON-NLS-1$
			openHintFileOnUi(project, content);
		}

		return Status.OK_STATUS;
	}

	private static String buildUnifiedDiff(FileDiff diff) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- a/").append(diff.filePath()).append('\n'); //$NON-NLS-1$
		sb.append("+++ b/").append(diff.filePath()).append('\n'); //$NON-NLS-1$
		for (DiffHunk hunk : diff.hunks()) {
			sb.append("@@ -").append(hunk.beforeStartLine()) //$NON-NLS-1$
					.append(',').append(hunk.beforeLineCount())
					.append(" +").append(hunk.afterStartLine()) //$NON-NLS-1$
					.append(',').append(hunk.afterLineCount())
					.append(" @@\n"); //$NON-NLS-1$
			for (String line : hunk.beforeText().split("\n", -1)) { //$NON-NLS-1$
				if (!line.isEmpty()) {
					sb.append('-').append(line).append('\n');
				}
			}
			for (String line : hunk.afterText().split("\n", -1)) { //$NON-NLS-1$
				if (!line.isEmpty()) {
					sb.append('+').append(line).append('\n');
				}
			}
		}
		return sb.toString();
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
			}
		});
	}
}
