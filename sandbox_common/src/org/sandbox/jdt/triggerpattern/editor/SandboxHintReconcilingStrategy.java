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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Reconciling strategy for {@code .sandbox-hint} files.
 *
 * <p>Validates the document content using {@link HintFileParser} and creates
 * error markers for parse errors. The markers appear as red underlines
 * in the editor and as entries in the Eclipse Problems view.</p>
 *
 * @since 1.3.6
 */
public class SandboxHintReconcilingStrategy implements IReconcilingStrategy {

	/**
	 * Marker type for hint file parse errors.
	 */
	private static final String MARKER_TYPE = "org.eclipse.core.resources.problemmarker"; //$NON-NLS-1$

	private IDocument document;
	private ISourceViewer sourceViewer;

	/**
	 * Sets the source viewer for accessing the editor input.
	 *
	 * @param viewer the source viewer
	 */
	public void setSourceViewer(ISourceViewer viewer) {
		this.sourceViewer = viewer;
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
		} catch (CoreException e) {
			logError("Failed to clear markers", e); //$NON-NLS-1$
		}

		// Validate
		String content = document.get();
		HintFileParser parser = new HintFileParser();
		try {
			parser.parse(content);
		} catch (HintParseException e) {
			createErrorMarker(file, e);
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
