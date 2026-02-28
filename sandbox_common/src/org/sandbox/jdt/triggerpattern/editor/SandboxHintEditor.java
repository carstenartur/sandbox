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

import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

/**
 * Editor for {@code .sandbox-hint} files with syntax highlighting,
 * content assist, and validation.
 *
 * <p>Provides an editor with:</p>
 * <ul>
 *   <li>Syntax highlighting for comments, metadata, guards, operators, placeholders</li>
 *   <li>Content assist after {@code ::} for guard functions from {@link org.sandbox.jdt.triggerpattern.internal.GuardRegistry}</li>
 *   <li>JDT-powered content assist inside {@code <? ?>} embedded Java blocks</li>
 *   <li>Validation via {@link org.sandbox.jdt.triggerpattern.internal.HintFileParser} with error markers</li>
 *   <li>Breakpoint support in embedded Java blocks via {@link SandboxHintBreakpointAdapter}</li>
 *   <li>Code folding for {@code <? ?>} blocks and multi-line comments</li>
 *   <li>Outline view showing rules and embedded methods</li>
 *   <li>Hyperlink navigation from guard references to their definitions</li>
 * </ul>
 *
 * @since 1.3.6
 */
public class SandboxHintEditor extends TextEditor {

	/**
	 * The editor ID used in the {@code plugin.xml} registration.
	 */
	public static final String EDITOR_ID = "org.sandbox.jdt.triggerpattern.editor.sandboxHint"; //$NON-NLS-1$

	private SandboxHintOutlinePage outlinePage;
	private ProjectionSupport projectionSupport;

	public SandboxHintEditor() {
		setSourceViewerConfiguration(new SandboxHintSourceViewerConfiguration(this));
		setDocumentProvider(new SandboxHintDocumentProvider());
	}

	@Override
	protected ISourceViewer createSourceViewer(Composite parent,
			IVerticalRuler ruler, int styles) {
		ProjectionViewer viewer = new ProjectionViewer(parent, ruler,
				getOverviewRuler(), isOverviewRulerVisible(), styles);
		getSourceViewerDecorationSupport(viewer);
		return viewer;
	}

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		// Enable projection (code folding)
		ISourceViewer viewer = getSourceViewer();
		if (viewer instanceof ProjectionViewer projectionViewer) {
			projectionSupport = new ProjectionSupport(projectionViewer,
					getAnnotationAccess(), getSharedColors());
			projectionSupport.install();
			projectionViewer.doOperation(ProjectionViewer.TOGGLE);

			// Initial folding computation
			SandboxHintFoldingProvider.updateFolding(projectionViewer);
		}
	}

	/**
	 * Updates the code folding annotations.
	 * Called from the reconciler after document changes.
	 */
	public void updateFolding() {
		ISourceViewer viewer = getSourceViewer();
		if (viewer instanceof ProjectionViewer projectionViewer) {
			SandboxHintFoldingProvider.updateFolding(projectionViewer);
		}
	}

	/**
	 * Updates the outline view content.
	 * Called from the reconciler after document changes.
	 *
	 * @since 1.5.0
	 */
	public void updateOutline() {
		if (outlinePage != null) {
			outlinePage.update();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (IContentOutlinePage.class.equals(adapter)) {
			if (outlinePage == null) {
				outlinePage = new SandboxHintOutlinePage(this);
			}
			return (T) outlinePage;
		}
		if (IToggleBreakpointsTarget.class.equals(adapter)) {
			return (T) new SandboxHintBreakpointAdapter();
		}
		return super.getAdapter(adapter);
	}

	@Override
	public void dispose() {
		if (projectionSupport != null) {
			projectionSupport.dispose();
			projectionSupport = null;
		}
		outlinePage = null;
		super.dispose();
	}
}
