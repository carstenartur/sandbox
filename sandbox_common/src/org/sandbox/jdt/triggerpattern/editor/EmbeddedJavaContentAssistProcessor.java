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
package org.sandbox.jdt.triggerpattern.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.text.java.CompletionProposalCollector;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PlatformUI;

/**
 * Content assist processor for embedded Java code ({@code <? ?>}) regions
 * in {@code .sandbox-hint} files.
 *
 * <p>Delegates to JDT's {@link CompletionProposalCollector} by creating a
 * synthetic {@link ICompilationUnit} working copy from the embedded Java source.
 * This provides full context-aware Java completions including all keywords,
 * types from the project classpath, methods, fields, and local variables.</p>
 *
 * <p>The synthetic compilation unit wraps the embedded code in a class body,
 * matching the structure used by {@link org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler}.</p>
 *
 * @since 1.5.0
 */
public class EmbeddedJavaContentAssistProcessor implements IContentAssistProcessor {

	private static final Logger LOGGER = Logger.getLogger(EmbeddedJavaContentAssistProcessor.class.getName());

	private static final String SYNTHETIC_PACKAGE = "org.sandbox.generated"; //$NON-NLS-1$
	private static final String SYNTHETIC_CLASS_NAME = "HintCode_assist"; //$NON-NLS-1$

	/**
	 * Header prepended to the embedded Java source to form a valid compilation unit.
	 * The offset into this header is used to map completion positions.
	 */
	private static final String SYNTHETIC_HEADER =
			"package " + SYNTHETIC_PACKAGE + ";\n" + //$NON-NLS-1$ //$NON-NLS-2$
			"import org.eclipse.jdt.core.dom.*;\n" + //$NON-NLS-1$
			"public class " + SYNTHETIC_CLASS_NAME + " {\n"; //$NON-NLS-1$ //$NON-NLS-2$

	private static final String SYNTHETIC_FOOTER = "\n}\n"; //$NON-NLS-1$

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		IDocument document = viewer.getDocument();

		// Extract the embedded Java source from the <? ?> partition
		String javaSource = extractEmbeddedJavaSource(document, offset);
		if (javaSource == null) {
			return new ICompletionProposal[0];
		}

		// Calculate offset within the embedded Java source
		int partitionStart = getPartitionStart(document, offset);
		int offsetInEmbedded = offset - partitionStart;

		// Build synthetic compilation unit source
		String syntheticSource = SYNTHETIC_HEADER + javaSource + SYNTHETIC_FOOTER;
		int syntheticOffset = SYNTHETIC_HEADER.length() + offsetInEmbedded;

		// Try to get an IJavaProject from the active editor
		IJavaProject javaProject = getJavaProject();
		if (javaProject == null) {
			LOGGER.log(Level.FINE, "No IJavaProject available, cannot provide JDT content assist"); //$NON-NLS-1$
			return new ICompletionProposal[0];
		}

		return computeJdtProposals(javaProject, syntheticSource, syntheticOffset,
				offset, offsetInEmbedded);
	}

	/**
	 * Delegates to JDT's code completion engine via a synthetic working copy.
	 */
	private ICompletionProposal[] computeJdtProposals(IJavaProject javaProject,
			String syntheticSource, int syntheticOffset, int documentOffset, int offsetInEmbedded) {
		ICompilationUnit workingCopy = null;
		try {
			// Find or create a source folder for the synthetic unit
			IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
			IPackageFragmentRoot sourceRoot = null;
			for (IPackageFragmentRoot root : roots) {
				if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
					sourceRoot = root;
					break;
				}
			}
			if (sourceRoot == null) {
				LOGGER.log(Level.FINE, "No source root found in project"); //$NON-NLS-1$
				return new ICompletionProposal[0];
			}

			IPackageFragment pkg = sourceRoot.getPackageFragment(SYNTHETIC_PACKAGE);
			ICompilationUnit originalUnit = pkg.getCompilationUnit(SYNTHETIC_CLASS_NAME + ".java"); //$NON-NLS-1$
			workingCopy = originalUnit.getWorkingCopy(null);
			workingCopy.getBuffer().setContents(syntheticSource);

			// Collect proposals via JDT's CompletionProposalCollector
			CompletionProposalCollector collector = new CompletionProposalCollector(workingCopy);
			collector.setReplacementLength(0);

			workingCopy.codeComplete(syntheticOffset, collector);

			ICompletionProposal[] jdtProposals = collector.getJavaCompletionProposals();

			// Remap proposal offsets from synthetic source back to the hint document
			int offsetDelta = documentOffset - syntheticOffset;
			List<ICompletionProposal> remapped = new ArrayList<>(jdtProposals.length);
			for (ICompletionProposal proposal : jdtProposals) {
				remapped.add(new OffsetRemappingProposal(proposal, offsetDelta));
			}
			return remapped.toArray(new ICompletionProposal[0]);

		} catch (JavaModelException e) {
			LOGGER.log(Level.WARNING, "JDT code completion failed", e); //$NON-NLS-1$
			return new ICompletionProposal[0];
		} finally {
			if (workingCopy != null) {
				try {
					workingCopy.discardWorkingCopy();
				} catch (JavaModelException e) {
					LOGGER.log(Level.FINE, "Failed to discard working copy", e); //$NON-NLS-1$
				}
			}
		}
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return new IContextInformation[0];
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { '.' };
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	/**
	 * Extracts the full embedded Java source from the {@code <? ?>} partition
	 * containing the given offset.
	 */
	private String extractEmbeddedJavaSource(IDocument document, int offset) {
		try {
			ITypedRegion partition = document.getPartition(offset);
			if (!SandboxHintPartitionScanner.JAVA_CODE.equals(partition.getType())) {
				return null;
			}
			String partitionText = document.get(partition.getOffset(), partition.getLength());
			// Strip the <? and ?> delimiters
			if (partitionText.startsWith("<?") && partitionText.endsWith("?>")) { //$NON-NLS-1$ //$NON-NLS-2$
				return partitionText.substring(2, partitionText.length() - 2);
			}
			return partitionText;
		} catch (BadLocationException e) {
			return null;
		}
	}

	/**
	 * Returns the start offset of the embedded Java content (after the {@code <?} delimiter).
	 */
	private int getPartitionStart(IDocument document, int offset) {
		try {
			ITypedRegion partition = document.getPartition(offset);
			String partitionText = document.get(partition.getOffset(), partition.getLength());
			// Account for the <? delimiter
			int delimiterLength = partitionText.startsWith("<?") ? 2 : 0; //$NON-NLS-1$
			return partition.getOffset() + delimiterLength;
		} catch (BadLocationException e) {
			return offset;
		}
	}

	/**
	 * Attempts to obtain the {@link IJavaProject} from the active editor.
	 */
	private IJavaProject getJavaProject() {
		try {
			IEditorPart editor = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow()
					.getActivePage()
					.getActiveEditor();
			if (editor == null) {
				return null;
			}
			IEditorInput input = editor.getEditorInput();
			if (input instanceof IFileEditorInput fileInput) {
				IFile file = fileInput.getFile();
				IProject project = file.getProject();
				if (project.hasNature(JavaCore.NATURE_ID)) {
					return JavaCore.create(project);
				}
			}
		} catch (Exception e) {
			LOGGER.log(Level.FINE, "Could not determine Java project", e); //$NON-NLS-1$
		}
		return null;
	}
}
