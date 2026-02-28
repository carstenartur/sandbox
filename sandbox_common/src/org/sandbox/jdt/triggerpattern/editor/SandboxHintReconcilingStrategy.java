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

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sandbox.jdt.triggerpattern.api.EmbeddedJavaBlock;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Reconciling strategy for {@code .sandbox-hint} files.
 *
 * <p>Validates the document content using {@link HintFileParser} and creates
 * error markers for parse errors. Also compiles embedded Java ({@code <? ?>})
 * blocks via {@link EmbeddedJavaCompiler} and maps compilation errors back to
 * the hint file line numbers.</p>
 *
 * @since 1.3.6
 */
public class SandboxHintReconcilingStrategy implements IReconcilingStrategy {

	/**
	 * Marker type for hint file parse errors.
	 */
	private static final String MARKER_TYPE = "org.eclipse.core.resources.problemmarker"; //$NON-NLS-1$

	/**
	 * Marker type for embedded Java compilation errors.
	 *
	 * @since 1.5.0
	 */
	private static final String EMBEDDED_JAVA_MARKER_TYPE = "sandbox_common.org.sandbox.jdt.triggerpattern.embeddedJavaProblem"; //$NON-NLS-1$

	private IDocument document;
	private ISourceViewer sourceViewer;
	private SandboxHintEditor editor;

	/**
	 * Sets the source viewer for accessing the editor input.
	 *
	 * @param viewer the source viewer
	 */
	public void setSourceViewer(ISourceViewer viewer) {
		this.sourceViewer = viewer;
	}

	/**
	 * Sets the editor for triggering folding and outline updates after reconciliation.
	 *
	 * @param editor the hint editor
	 * @since 1.5.0
	 */
	public void setEditor(SandboxHintEditor editor) {
		this.editor = editor;
	}

	@Override
	public void setDocument(IDocument document) {
		this.document = document;
	}

	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		reconcile(subRegion);
	}

	@Override
	public void reconcile(IRegion partition) {
		if (document == null) {
			return;
		}

		IFile file = getFile();
		if (file == null || !file.exists()) {
			return;
		}

		// Clear old markers
		try {
			file.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
			file.deleteMarkers(EMBEDDED_JAVA_MARKER_TYPE, true, IResource.DEPTH_ZERO);
		} catch (CoreException e) {
			logError("Failed to clear markers", e); //$NON-NLS-1$
		}

		// Validate DSL
		String content = document.get();
		HintFileParser parser = new HintFileParser();
		HintFile hintFile = null;
		try {
			hintFile = parser.parse(content);
		} catch (HintParseException e) {
			createErrorMarker(file, e);
		}

		// Validate embedded Java blocks
		if (hintFile != null) {
			validateEmbeddedJavaBlocks(file, hintFile);
		}

		// Update folding and outline after reconciliation
		if (editor != null) {
			Display display = Display.getDefault();
			if (display != null && !display.isDisposed()) {
				display.asyncExec(() -> {
					editor.updateFolding();
					editor.updateOutline();
				});
			}
		}
	}

	/**
	 * Compiles each embedded Java block and creates markers for compilation errors.
	 *
	 * @since 1.5.0
	 */
	private void validateEmbeddedJavaBlocks(IFile file, HintFile hintFile) {
		List<EmbeddedJavaBlock> blocks = hintFile.getEmbeddedJavaBlocks();
		String ruleId = hintFile.getId();

		for (EmbeddedJavaBlock block : blocks) {
			if (block.getSource().isBlank()) {
				continue;
			}
			CompilationResult result = EmbeddedJavaCompiler.compile(block, ruleId);
			if (result.hasErrors()) {
				createEmbeddedJavaMarkers(file, block, result);
			}
		}
	}

	/**
	 * Creates markers for compilation problems in an embedded Java block.
	 * Maps synthetic class line numbers back to hint file line numbers and
	 * adjusts character positions by subtracting the synthetic header length.
	 */
	private void createEmbeddedJavaMarkers(IFile file, EmbeddedJavaBlock block,
			CompilationResult result) {
		for (IProblem problem : result.problems()) {
			if (!problem.isError()) {
				continue;
			}
			try {
				IMarker marker = file.createMarker(EMBEDDED_JAVA_MARKER_TYPE);
				marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
				marker.setAttribute(IMarker.MESSAGE, problem.getMessage());

				// Map synthetic class line back to hint file line
				int syntheticLine = problem.getSourceLineNumber();
				int hintLine = syntheticLine + result.lineOffset();
				if (hintLine > 0) {
					marker.setAttribute(IMarker.LINE_NUMBER, hintLine);
				}

				// Map character positions: problem offsets are in the synthetic
				// source which has a header before the embedded code. Subtract
				// the header length and add the block's start offset plus the
				// '<?' delimiter length to map back to the hint document.
				int sourceStart = problem.getSourceStart();
				int sourceEnd = problem.getSourceEnd();
				if (sourceStart >= 0 && sourceEnd >= 0) {
					int headerLength = result.syntheticHeaderLength();
					int delimiterLength = 2; // length of '<?' delimiter
					int hintStart = block.getStartOffset() + delimiterLength + (sourceStart - headerLength);
					int hintEnd = block.getStartOffset() + delimiterLength + (sourceEnd - headerLength) + 1;
					if (hintStart >= 0 && hintEnd > hintStart) {
						marker.setAttribute(IMarker.CHAR_START, hintStart);
						marker.setAttribute(IMarker.CHAR_END, hintEnd);
					}
				}
			} catch (CoreException e) {
				logError("Failed to create embedded Java marker", e); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Creates an error marker for a parse exception.
	 */
	private void createErrorMarker(IFile file, HintParseException e) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.MESSAGE, e.getMessage());
			int lineNumber = extractLineNumber(e.getMessage());
			if (lineNumber > 0) {
				marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			}
		} catch (CoreException ce) {
			logError("Failed to create marker", ce); //$NON-NLS-1$
		}
	}

	/**
	 * Extracts a line number from a parse error message.
	 *
	 * <p>The {@link HintFileParser} includes line numbers in its error messages
	 * in the format "Line N: ...".</p>
	 */
	private int extractLineNumber(String message) {
		if (message != null && message.startsWith("Line ")) { //$NON-NLS-1$
			int colonIdx = message.indexOf(':');
			if (colonIdx > 5) {
				try {
					return Integer.parseInt(message.substring(5, colonIdx).trim());
				} catch (NumberFormatException e) {
					// ignore
				}
			}
		}
		return -1;
	}

	/**
	 * Gets the {@link IFile} associated with the current editor.
	 */
	private IFile getFile() {
		if (sourceViewer == null) {
			return null;
		}
		Object adapter = sourceViewer.getTextWidget().getData("org.eclipse.ui.texteditor"); //$NON-NLS-1$
		if (adapter instanceof ITextEditor textEditor) {
			IEditorInput input = textEditor.getEditorInput();
			if (input instanceof IFileEditorInput fileInput) {
				return fileInput.getFile();
			}
		}
		return null;
	}

	private void logError(String message, CoreException e) {
		ILog log = Platform.getLog(SandboxHintReconcilingStrategy.class);
		log.error(message, e);
	}
}
