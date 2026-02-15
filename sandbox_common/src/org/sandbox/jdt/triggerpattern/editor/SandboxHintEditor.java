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

import org.eclipse.ui.editors.text.TextEditor;

/**
 * Editor for {@code .sandbox-hint} files with syntax highlighting,
 * content assist, and validation.
 *
 * <p>Provides an editor with:</p>
 * <ul>
 *   <li>Syntax highlighting for comments, metadata, guards, operators, placeholders</li>
 *   <li>Content assist after {@code ::} for guard functions from {@link org.sandbox.jdt.triggerpattern.internal.GuardRegistry}</li>
 *   <li>Validation via {@link org.sandbox.jdt.triggerpattern.internal.HintFileParser} with error markers</li>
 * </ul>
 *
 * @since 1.3.6
 */
public class SandboxHintEditor extends TextEditor {

	/**
	 * The editor ID used in the {@code plugin.xml} registration.
	 */
	public static final String EDITOR_ID = "org.sandbox.jdt.triggerpattern.editor.sandboxHint"; //$NON-NLS-1$

	public SandboxHintEditor() {
		setSourceViewerConfiguration(new SandboxHintSourceViewerConfiguration());
		setDocumentProvider(new SandboxHintDocumentProvider());
	}
}
