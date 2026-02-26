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
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/**
 * Quick assist processor that generates a TriggerPattern DSL rule from the
 * currently selected Java code using AI-powered inference.
 *
 * <p>When invoked on a text selection, it sends the selected code to
 * {@link AiRuleInferenceEngine#inferRule(String, String)} and opens
 * the resulting DSL rule as a new {@code .sandbox-hint} file.</p>
 *
 * @since 1.2.6
 */
public class DslFromSelectionAssistProcessor implements IQuickAssistProcessor {

	private static final ILog LOG = Platform.getLog(DslFromSelectionAssistProcessor.class);
	private static final String PROPOSAL_LABEL = "Generate DSL rule from selection"; //$NON-NLS-1$

	@Override
	public boolean hasAssists(IInvocationContext context) {
		return EclipseLlmService.getInstance().isAvailable()
				&& context.getSelectionLength() > 0;
	}

	@Override
	public IJavaCompletionProposal[] getAssists(IInvocationContext context,
			IProblemLocation[] locations) {
		if (!hasAssists(context)) {
			return new IJavaCompletionProposal[0];
		}
		int offset = context.getSelectionOffset();
		int length = context.getSelectionLength();
		return new IJavaCompletionProposal[] { new DslRuleProposal(offset, length) };
	}

	/**
	 * Completion proposal that infers a DSL rule from the selected code.
	 */
	private static class DslRuleProposal implements IJavaCompletionProposal {

		private final int selectionOffset;
		private final int selectionLength;

		DslRuleProposal(int offset, int length) {
			this.selectionOffset = offset;
			this.selectionLength = length;
		}

		@Override
		public void apply(IDocument document) {
			try {
				String selectedCode = document.get(selectionOffset, selectionLength);
				AiRuleInferenceEngine engine = EclipseLlmService.getInstance().getEngine();
				Optional<CommitEvaluation> result = engine.inferRule(selectedCode, selectedCode);
				if (result.isPresent()) {
					openHintFile(result.get().dslRule());
				}
			} catch (Exception e) {
				LOG.error("Failed to infer DSL rule from selection", e); //$NON-NLS-1$
			}
		}

		@Override
		public Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public String getAdditionalProposalInfo() {
			return "Uses the configured LLM to infer a TriggerPattern DSL rule from the selected code."; //$NON-NLS-1$
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

		private static void openHintFile(String ruleContent) throws CoreException {
			IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
			if (projects.length == 0) {
				return;
			}
			IProject project = projects[0];
			String fileName = "inferred-rule-" + System.currentTimeMillis() + ".sandbox-hint"; //$NON-NLS-1$ //$NON-NLS-2$
			IFile file = project.getFile(new Path(fileName));
			file.create(
					new ByteArrayInputStream(ruleContent.getBytes(StandardCharsets.UTF_8)),
					true, null);
			IWorkbenchPage page = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getActivePage();
			if (page != null) {
				IDE.openEditor(page, file);
			}
		}
	}
}
