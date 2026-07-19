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
package org.sandbox.jdt.triggerpattern.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Quick Assist processor that creates proposals from {@code .sandbox-hint} files.
 *
 * <p>This processor finds matching transformation rules from registered
 * {@code .sandbox-hint} files at the cursor location and creates completion
 * proposals for each match that has a replacement. The editor AST is parsed
 * with binding resolution enabled so type-dependent guards do not fall back to
 * permissive unresolved-binding behavior when project type information exists.</p>
 *
 * <p>This is complementary to {@link TriggerPatternQuickAssistProcessor},
 * which handles annotation-based {@code @TriggerPattern} hints.
 * This processor handles DSL-based {@code .sandbox-hint} file rules.</p>
 *
 * @since 1.3.5
 */
public class HintFileQuickAssistProcessor implements IQuickAssistProcessor {

	@Override
	public boolean hasAssists(IInvocationContext context) throws CoreException {
		GuardRegistry.getInstance();
		HintFileRegistry registry = HintFileRegistry.getInstance();
		registry.loadBundledLibraries(HintFileQuickAssistProcessor.class.getClassLoader());
		return !registry.getAllHintFiles().isEmpty();
	}

	@Override
	public IJavaCompletionProposal[] getAssists(IInvocationContext context, IProblemLocation[] locations)
			throws CoreException {
		ICompilationUnit icu = context.getCompilationUnit();
		if (icu == null) {
			return null;
		}

		CompilationUnit cu = getCompilationUnit(icu);
		if (cu == null) {
			return null;
		}

		GuardRegistry.getInstance();
		HintFileRegistry registry = HintFileRegistry.getInstance();
		registry.loadBundledLibraries(HintFileQuickAssistProcessor.class.getClassLoader());

		if (icu.getJavaProject() != null) {
			org.eclipse.core.resources.IProject project = icu.getJavaProject().getProject();
			registry.loadProjectHintFiles(project);
		}

		return collectAssists(cu, context.getSelectionOffset());
	}

	/**
	 * Collects proposals from the hint files currently registered in the singleton
	 * registry. Kept package-visible so the proposal selection and application path
	 * can be exercised without creating an Eclipse editor in tests.
	 */
	IJavaCompletionProposal[] collectAssists(CompilationUnit cu, int offset) {
		if (cu == null || offset < 0) {
			return null;
		}
		GuardRegistry.getInstance();
		HintFileRegistry registry = HintFileRegistry.getInstance();
		List<IJavaCompletionProposal> proposals = new ArrayList<>();

		for (Map.Entry<String, HintFile> entry : registry.getAllHintFiles().entrySet()) {
			HintFile hintFile = entry.getValue();
			try {
				List<TransformationRule> resolvedRules = registry.resolveIncludes(hintFile);
				BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile, resolvedRules);
				for (TransformationResult result : processor.process(cu)) {
					if (result.hasReplacement() && containsOffset(result, offset)) {
						proposals.add(new HintFileProposal(result));
					}
				}
			} catch (Exception e) {
				ILog log = Platform.getLog(HintFileQuickAssistProcessor.class);
				log.log(Status.error("Error processing hint file: " + entry.getKey(), e)); //$NON-NLS-1$
			}
		}

		return proposals.isEmpty() ? null : proposals.toArray(new IJavaCompletionProposal[0]);
	}

	private static CompilationUnit getCompilationUnit(ICompilationUnit icu) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(icu);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static boolean containsOffset(TransformationResult result, int offset) {
		int start = result.match().getOffset();
		int end = start + result.match().getLength();
		return start <= offset && offset <= end;
	}

	/** Quick Assist proposal generated from a hint-file rule match. */
	private static class HintFileProposal implements IJavaCompletionProposal {

		private final TransformationResult result;

		HintFileProposal(TransformationResult result) {
			this.result = result;
		}

		@Override
		public void apply(IDocument document) {
			try {
				String replacement = result.replacement();
				int start = result.match().getOffset();
				int length = result.match().getLength();
				if (replacement != null && start >= 0 && length >= 0
						&& start + length <= document.getLength()) {
					document.replace(start, length, replacement);
				}
			} catch (Exception e) {
				ILog log = Platform.getLog(HintFileQuickAssistProcessor.class);
				log.log(Status.error("Error applying hint file proposal", e)); //$NON-NLS-1$
			}
		}

		@Override
		public String getDisplayString() {
			String description = result.description();
			if (description != null && !description.isEmpty()) {
				return description;
			}
			return "Apply hint: " + result.matchedText() + " → " + result.replacement(); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Override
		public Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public String getAdditionalProposalInfo() {
			StringBuilder sb = new StringBuilder();
			sb.append("Before: ").append(result.matchedText()).append('\n'); //$NON-NLS-1$
			sb.append("After:  ").append(result.replacement()); //$NON-NLS-1$
			return sb.toString();
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
			return 5;
		}
	}
}
