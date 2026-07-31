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
import org.eclipse.swt.graphics.RGB;

/** Source viewer configuration for the {@code .sandbox-hint} editor. */
public class SandboxHintSourceViewerConfiguration extends SourceViewerConfiguration {

	private SandboxHintEditor editor;

	public SandboxHintSourceViewerConfiguration() {
	}

	public SandboxHintSourceViewerConfiguration(SandboxHintEditor editor) {
		this.editor= editor;
	}

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
		PresentationReconciler reconciler= new PresentationReconciler();

		DefaultDamagerRepairer codeDR= new DefaultDamagerRepairer(new SandboxHintCodeScanner());
		reconciler.setDamager(codeDR, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(codeDR, IDocument.DEFAULT_CONTENT_TYPE);

		Color commentColor= JavaPlugin.getDefault().getJavaTextTools()
				.getColorManager().getColor(new RGB(63, 127, 95));
		TextAttribute commentAttr= new TextAttribute(commentColor, null, SWT.ITALIC);
		DefaultDamagerRepairer commentDR= new DefaultDamagerRepairer(
				new SingleTokenScanner(new Token(commentAttr)));
		reconciler.setDamager(commentDR, SandboxHintPartitionScanner.COMMENT);
		reconciler.setRepairer(commentDR, SandboxHintPartitionScanner.COMMENT);

		DefaultDamagerRepairer metadataDR= new DefaultDamagerRepairer(new SandboxHintMetadataScanner());
		reconciler.setDamager(metadataDR, SandboxHintPartitionScanner.METADATA);
		reconciler.setRepairer(metadataDR, SandboxHintPartitionScanner.METADATA);

		ITokenScanner javaScanner= JavaPlugin.getDefault().getJavaTextTools().getCodeScanner();
		DefaultDamagerRepairer javaDR= new DefaultDamagerRepairer(javaScanner);
		reconciler.setDamager(javaDR, SandboxHintPartitionScanner.JAVA_CODE);
		reconciler.setRepairer(javaDR, SandboxHintPartitionScanner.JAVA_CODE);
		return reconciler;
	}

	@Override
	public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
		ContentAssistant assistant= new ContentAssistant();
		SandboxHintContentAssistProcessor hintProcessor= new SandboxHintContentAssistProcessor();
		assistant.setContentAssistProcessor(hintProcessor, IDocument.DEFAULT_CONTENT_TYPE);
		assistant.setContentAssistProcessor(hintProcessor, SandboxHintPartitionScanner.METADATA);
		assistant.setContentAssistProcessor(new EmbeddedJavaContentAssistProcessor(),
				SandboxHintPartitionScanner.JAVA_CODE);
		assistant.enableAutoActivation(true);
		assistant.setAutoActivationDelay(500);
		return assistant;
	}

	@Override
	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
		return new SandboxHintAnnotationHover();
	}

	@Override
	public IReconciler getReconciler(ISourceViewer sourceViewer) {
		SandboxHintReconcilingStrategy strategy= new SandboxHintReconcilingStrategy();
		strategy.setSourceViewer(sourceViewer);
		if (editor != null) {
			strategy.setEditor(editor);
		}
		MonoReconciler reconciler= new MonoReconciler(strategy, false);
		reconciler.setDelay(500);
		return reconciler;
	}

	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
		IHyperlinkDetector[] defaults= super.getHyperlinkDetectors(sourceViewer);
		if (defaults == null) {
			return new IHyperlinkDetector[] { new SandboxHintHyperlinkDetector() };
		}
		IHyperlinkDetector[] result= new IHyperlinkDetector[defaults.length + 1];
		System.arraycopy(defaults, 0, result, 0, defaults.length);
		result[defaults.length]= new SandboxHintHyperlinkDetector();
		return result;
	}
}
