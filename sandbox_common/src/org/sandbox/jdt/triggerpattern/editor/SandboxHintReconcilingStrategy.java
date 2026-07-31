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
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintProgramParser;

/**
 * Reconciling strategy for {@code .sandbox-hint} files.
 *
 * <p>Validates complete composed programs using {@link HintProgramParser} and
 * creates error markers for parse, predicate, arity and recursion errors. It
 * also compiles embedded Java ({@code <? ?>}) blocks via
 * {@link EmbeddedJavaCompiler} and maps compilation errors back to the hint
 * file line numbers.</p>
 *
 * @since 1.3.6
 */
public class SandboxHintReconcilingStrategy implements IReconcilingStrategy {

	private static final String MARKER_TYPE= "org.eclipse.core.resources.problemmarker"; //$NON-NLS-1$
	private static final String EMBEDDED_JAVA_MARKER_TYPE=
			"sandbox_common.org.sandbox.jdt.triggerpattern.embeddedJavaProblem"; //$NON-NLS-1$

	private IDocument document;
	private ISourceViewer sourceViewer;
	private SandboxHintEditor editor;

	public void setSourceViewer(ISourceViewer viewer) {
		this.sourceViewer= viewer;
	}

	public void setEditor(SandboxHintEditor editor) {
		this.editor= editor;
	}

	@Override
	public void setDocument(IDocument document) {
		this.document= document;
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
		IFile file= getFile();
		if (file == null || !file.exists()) {
			return;
		}
		try {
			file.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
			file.deleteMarkers(EMBEDDED_JAVA_MARKER_TYPE, true, IResource.DEPTH_ZERO);
		} catch (CoreException exception) {
			logError("Failed to clear markers", exception); //$NON-NLS-1$
		}

		HintFile hintFile= null;
		try {
			hintFile= new HintProgramParser().parse(document.get()).hintFile();
		} catch (HintParseException exception) {
			createErrorMarker(file, exception);
		}
		if (hintFile != null) {
			validateEmbeddedJavaBlocks(file, hintFile);
		}

		if (editor != null) {
			Display display= Display.getDefault();
			if (display != null && !display.isDisposed()) {
				display.asyncExec(() -> {
					editor.updateFolding();
					editor.updateOutline();
				});
			}
		}
	}

	private void validateEmbeddedJavaBlocks(IFile file, HintFile hintFile) {
		List<EmbeddedJavaBlock> blocks= hintFile.getEmbeddedJavaBlocks();
		String ruleId= hintFile.getId();
		for (EmbeddedJavaBlock block : blocks) {
			if (block.getSource().isBlank()) {
				continue;
			}
			CompilationResult result= EmbeddedJavaCompiler.compile(block, ruleId);
			if (result.hasErrors()) {
				createEmbeddedJavaMarkers(file, block, result);
			}
		}
	}

	private void createEmbeddedJavaMarkers(IFile file, EmbeddedJavaBlock block,
			CompilationResult result) {
		for (IProblem problem : result.problems()) {
			if (!problem.isError()) {
				continue;
			}
			try {
				IMarker marker= file.createMarker(EMBEDDED_JAVA_MARKER_TYPE);
				marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
				marker.setAttribute(IMarker.MESSAGE, problem.getMessage());
				int hintLine= problem.getSourceLineNumber() + result.lineOffset();
				if (hintLine > 0) {
					marker.setAttribute(IMarker.LINE_NUMBER, hintLine);
				}
				int sourceStart= problem.getSourceStart();
				int sourceEnd= problem.getSourceEnd();
				if (sourceStart >= 0 && sourceEnd >= 0) {
					int delimiterLength= 2;
					int hintStart= block.getStartOffset() + delimiterLength
							+ sourceStart - result.syntheticHeaderLength();
					int hintEnd= block.getStartOffset() + delimiterLength
							+ sourceEnd - result.syntheticHeaderLength() + 1;
					if (hintStart >= 0 && hintEnd > hintStart) {
						marker.setAttribute(IMarker.CHAR_START, hintStart);
						marker.setAttribute(IMarker.CHAR_END, hintEnd);
					}
				}
			} catch (CoreException exception) {
				logError("Failed to create embedded Java marker", exception); //$NON-NLS-1$
			}
		}
	}

	private void createErrorMarker(IFile file, HintParseException exception) {
		try {
			IMarker marker= file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.MESSAGE, exception.getMessage());
			if (exception.getLineNumber() > 0) {
				marker.setAttribute(IMarker.LINE_NUMBER, exception.getLineNumber());
			}
		} catch (CoreException markerFailure) {
			logError("Failed to create marker", markerFailure); //$NON-NLS-1$
		}
	}

	private IFile getFile() {
		if (sourceViewer == null) {
			return null;
		}
		Object adapter= sourceViewer.getTextWidget().getData("org.eclipse.ui.texteditor"); //$NON-NLS-1$
		if (adapter instanceof ITextEditor textEditor) {
			IEditorInput input= textEditor.getEditorInput();
			if (input instanceof IFileEditorInput fileInput) {
				return fileInput.getFile();
			}
		}
		return null;
	}

	private void logError(String message, CoreException exception) {
		ILog log= Platform.getLog(SandboxHintReconcilingStrategy.class);
		log.error(message, exception);
	}
}
