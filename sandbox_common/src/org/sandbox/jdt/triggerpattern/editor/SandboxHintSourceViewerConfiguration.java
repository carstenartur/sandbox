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

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jface.text.DefaultAnnotationHover;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.MonoReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/**
 * Source viewer configuration for the {@code .sandbox-hint} editor.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Syntax highlighting via a {@link PresentationReconciler}</li>
 *   <li>Content assist for guard functions after {@code ::} and Java completions in {@code <? ?>} blocks</li>
 *   <li>Background reconciling for validation with error markers</li>
 *   <li>Annotation hover for embedded Java compile errors</li>
 *   <li>Hyperlink detection for navigating to guard function definitions</li>
 * </ul>
 *
 * @since 1.3.6
 */
public class SandboxHintSourceViewerConfiguration extends SourceViewerConfiguration {

	@Override
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] {
			IDocument.DEFAULT_CONTENT_TYPE,
			SandboxHintPartitionScanner.COMMENT,
			SandboxHintPartitionScanner.METADATA,
			SandboxHintPartitionScanner.JAVA_CODE
		};
	}

	@Override
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();

		// Default content (code) – uses the code scanner for keywords
		ITokenScanner codeScanner = new SandboxHintCodeScanner();
		DefaultDamagerRepairer codeDR = new DefaultDamagerRepairer(codeScanner);
		reconciler.setDamager(codeDR, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(codeDR, IDocument.DEFAULT_CONTENT_TYPE);

		// Comments – green italic
		Color commentColor = new Color(Display.getDefault(), 63, 127, 95);
		TextAttribute commentAttr = new TextAttribute(commentColor, null, SWT.ITALIC);
		DefaultDamagerRepairer commentDR = new DefaultDamagerRepairer(
				new SingleTokenScanner(new Token(commentAttr)));
		reconciler.setDamager(commentDR, SandboxHintPartitionScanner.COMMENT);
		reconciler.setRepairer(commentDR, SandboxHintPartitionScanner.COMMENT);

		// Metadata directives – dark blue bold
		Color metadataColor = new Color(Display.getDefault(), 0, 0, 128);
		TextAttribute metadataAttr = new TextAttribute(metadataColor, null, SWT.BOLD);
		DefaultDamagerRepairer metadataDR = new DefaultDamagerRepairer(
				new SingleTokenScanner(new Token(metadataAttr)));
		reconciler.setDamager(metadataDR, SandboxHintPartitionScanner.METADATA);
		reconciler.setRepairer(metadataDR, SandboxHintPartitionScanner.METADATA);

		// Embedded Java code – reuses JDT's Java code scanner for full keyword,
		// string, annotation, and operator highlighting with theme/preference support
		ITokenScanner javaScanner = JavaPlugin.getDefault().getJavaTextTools().getCodeScanner();
		DefaultDamagerRepairer javaDR = new DefaultDamagerRepairer(javaScanner);
		reconciler.setDamager(javaDR, SandboxHintPartitionScanner.JAVA_CODE);
		reconciler.setRepairer(javaDR, SandboxHintPartitionScanner.JAVA_CODE);

		return reconciler;
	}

	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		ContentAssistant assistant = new ContentAssistant();
		assistant.setContentAssistProcessor(
				new SandboxHintContentAssistProcessor(),
				IDocument.DEFAULT_CONTENT_TYPE);
		assistant.setContentAssistProcessor(
				new EmbeddedJavaContentAssistProcessor(),
				SandboxHintPartitionScanner.JAVA_CODE);
		assistant.enableAutoActivation(true);
		assistant.setAutoActivationDelay(500);
		return assistant;
	}

	@Override
	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
		return new DefaultAnnotationHover();
	}

	@Override
	public IReconciler getReconciler(ISourceViewer sourceViewer) {
		SandboxHintReconcilingStrategy strategy = new SandboxHintReconcilingStrategy();
		strategy.setSourceViewer(sourceViewer);
		MonoReconciler reconciler = new MonoReconciler(strategy, false);
		reconciler.setDelay(500);
		return reconciler;
	}

	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
		// Keep default detectors and add the guard function hyperlink detector
		IHyperlinkDetector[] defaults = super.getHyperlinkDetectors(sourceViewer);
		IHyperlinkDetector[] result;
		if (defaults != null) {
			result = new IHyperlinkDetector[defaults.length + 1];
			System.arraycopy(defaults, 0, result, 0, defaults.length);
			result[defaults.length] = new SandboxHintHyperlinkDetector();
		} else {
			result = new IHyperlinkDetector[] { new SandboxHintHyperlinkDetector() };
		}
		return result;
	}
}
