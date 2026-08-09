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
package org.sandbox.jdt.internal.ui.quickassist;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.sandbox.jdt.internal.ui.RuleInferenceUiFeedback;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/** AI-assisted authoring Quick Assist for generating TriggerPattern DSL from a Java selection. */
public class DslFromSelectionAssistProcessor implements IQuickAssistProcessor {

	private static final ILog LOG = Platform.getLog(DslFromSelectionAssistProcessor.class);
	private static final String PROPOSAL_LABEL = "Generate DSL rule from selection (AI)"; //$NON-NLS-1$

	@Override
	public boolean hasAssists(IInvocationContext context) {
		return EclipseLlmService.getInstance().isAvailable()
				&& context.getSelectionLength() > 0
				&& targetProject(context) != null;
	}

	@Override
	public IJavaCompletionProposal[] getAssists(IInvocationContext context,
			IProblemLocation[] locations) {
		IProject targetProject = targetProject(context);
		if (!hasAssists(context) || targetProject == null) {
			return new IJavaCompletionProposal[0];
		}
		return new IJavaCompletionProposal[] {
				new DslRuleProposal(context.getSelectionOffset(), context.getSelectionLength(), targetProject)
		};
	}

	private static IProject targetProject(IInvocationContext context) {
		ICompilationUnit unit = context.getCompilationUnit();
		return unit != null && unit.getJavaProject() != null
				? unit.getJavaProject().getProject()
				: null;
	}

	private static class DslRuleProposal implements IJavaCompletionProposal {

		private final int selectionOffset;
		private final int selectionLength;
		private final IProject targetProject;

		DslRuleProposal(int offset, int length, IProject targetProject) {
			this.selectionOffset = offset;
			this.selectionLength = length;
			this.targetProject = targetProject;
		}

		@Override
		public void apply(IDocument document) {
			try {
				String selectedCode = document.get(selectionOffset, selectionLength);
				Job job = new Job("Generating DSL rule from selection") { //$NON-NLS-1$
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						try {
							AiRuleInferenceEngine engine = EclipseLlmService.getInstance().getEngine();
							String[] lines = selectedCode.split("\n", -1); //$NON-NLS-1$
							StringBuilder sb = new StringBuilder();
							sb.append("--- a/snippet.java\n+++ b/snippet.java\n"); //$NON-NLS-1$
							sb.append("@@ -1,0 +1,").append(lines.length).append(" @@\n"); //$NON-NLS-1$ //$NON-NLS-2$
							for (String line : lines) {
								sb.append('+').append(line).append('\n');
							}
							Optional<CommitEvaluation> result = engine.inferRuleFromDiff(sb.toString());
							if (result.isPresent() && result.get().dslRule() != null
									&& !result.get().dslRule().isBlank()) {
								openHintFileOnUi(targetProject, result.get().dslRule());
								return Status.OK_STATUS;
							}
							RuleInferenceUiFeedback.showNoRule("the selected Java code"); //$NON-NLS-1$
							return Status.OK_STATUS;
						} catch (RuntimeException e) {
							LOG.error("Failed to infer a DSL rule from the Java selection", e); //$NON-NLS-1$
							RuleInferenceUiFeedback.showFailure("the selected Java code", e); //$NON-NLS-1$
							return Status.error("DSL inference from Java selection failed", e); //$NON-NLS-1$
						}
					}
				};
				job.setUser(true);
				job.schedule();
			} catch (Exception e) {
				LOG.error("Failed to infer DSL rule from selection", e); //$NON-NLS-1$
				RuleInferenceUiFeedback.showFailure("the selected Java code", e); //$NON-NLS-1$
			}
		}

		@Override
		public Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public String getAdditionalProposalInfo() {
			return "Uses the configured LLM to infer a TriggerPattern DSL rule from the selected code snippet."; //$NON-NLS-1$
		}

		@Override
		public String getDisplayString() {
			return PROPOSAL_LABEL;
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public IContextInformation getContextInformation() {
			return null;
		}

		@Override
		public int getRelevance() {
			return 1;
		}

		private static void openHintFileOnUi(IProject project, String ruleContent) {
			Display.getDefault().asyncExec(() -> {
				try {
					String fileName = "inferred-rule-" + System.currentTimeMillis() + ".sandbox-hint"; //$NON-NLS-1$ //$NON-NLS-2$
					IFile file = project.getFile(new Path(fileName));
					file.create(new ByteArrayInputStream(ruleContent.getBytes(StandardCharsets.UTF_8)), true, null);
					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					if (page != null) {
						IDE.openEditor(page, file);
					}
				} catch (Exception e) {
					LOG.error("Failed to open hint file for inferred rule", e); //$NON-NLS-1$
					RuleInferenceUiFeedback.showFailure("the generated hint file", e); //$NON-NLS-1$
				}
			});
		}
	}
}
