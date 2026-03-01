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

import java.util.Iterator;

import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;

/**
 * Annotation hover for the {@code .sandbox-hint} editor.
 *
 * <p>Shows detailed error information when hovering over embedded Java
 * compilation error markers, including the error text, line number,
 * and synthetic class name for debugging context.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintAnnotationHover implements IAnnotationHover {

	@Override
	public String getHoverInfo(ISourceViewer sourceViewer, int lineNumber) {
		IAnnotationModel model = sourceViewer.getAnnotationModel();
		if (model == null) {
			return null;
		}

		StringBuilder info = new StringBuilder();
		Iterator<Annotation> iter = model.getAnnotationIterator();

		while (iter.hasNext()) {
			Annotation annotation = iter.next();
			if (annotation.getText() != null) {
				org.eclipse.jface.text.Position position = model.getPosition(annotation);
				if (position != null) {
					try {
						int annotationLine = sourceViewer.getDocument().getLineOfOffset(position.getOffset());
						if (annotationLine == lineNumber) {
							if (info.length() > 0) {
								info.append('\n');
							}
							info.append(annotation.getText());
						}
					} catch (org.eclipse.jface.text.BadLocationException e) {
						// Skip annotations with invalid positions
					}
				}
			}
		}

		return info.length() > 0 ? info.toString() : null;
	}
}
