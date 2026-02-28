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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionViewer;

/**
 * Provides code folding for {@code .sandbox-hint} files.
 *
 * <p>Supports folding of:</p>
 * <ul>
 *   <li>Embedded Java blocks ({@code <? ... ?>})</li>
 *   <li>Multi-line comments ({@code /* ... * /})</li>
 *   <li>Metadata directive groups</li>
 * </ul>
 *
 * @since 1.5.0
 */
public final class SandboxHintFoldingProvider {

	private SandboxHintFoldingProvider() {
		// utility class
	}

	/**
	 * Updates the folding annotations in the given projection viewer based on
	 * the document's partitioning.
	 *
	 * @param viewer the projection viewer
	 */
	public static void updateFolding(ProjectionViewer viewer) {
		if (viewer == null) {
			return;
		}
		ProjectionAnnotationModel model = viewer.getProjectionAnnotationModel();
		if (model == null) {
			return;
		}
		IDocument document = viewer.getDocument();
		if (document == null) {
			return;
		}

		List<Position> newPositions = computeFoldingPositions(document);

		// Collect existing annotations for differential update
		Map<Annotation, Position> deletions = new HashMap<>();
		Iterator<Annotation> iter = model.getAnnotationIterator();
		while (iter.hasNext()) {
			Annotation annotation = iter.next();
			if (annotation instanceof ProjectionAnnotation) {
				deletions.put(annotation, model.getPosition(annotation));
			}
		}

		// Build additions
		Map<Annotation, Position> additions = new HashMap<>();
		for (Position pos : newPositions) {
			additions.put(new ProjectionAnnotation(), pos);
		}

		model.modifyAnnotations(
				deletions.keySet().toArray(new Annotation[0]),
				additions,
				null);
	}

	/**
	 * Computes the foldable positions from document partitions.
	 */
	private static List<Position> computeFoldingPositions(IDocument document) {
		List<Position> positions = new ArrayList<>();
		try {
			ITypedRegion[] partitions = document.computePartitioning(0, document.getLength());
			for (ITypedRegion partition : partitions) {
				String type = partition.getType();
				if (SandboxHintPartitionScanner.JAVA_CODE.equals(type)
						|| SandboxHintPartitionScanner.COMMENT.equals(type)) {
					// Only fold regions that span multiple lines
					int startLine = document.getLineOfOffset(partition.getOffset());
					int endOffset = partition.getOffset() + partition.getLength();
					int endLine = document.getLineOfOffset(Math.min(endOffset, document.getLength() - 1));
					if (endLine > startLine) {
						IRegion startLineInfo = document.getLineInformation(startLine);
						IRegion endLineInfo = document.getLineInformation(endLine);
						int foldStart = startLineInfo.getOffset();
						int foldEnd = endLineInfo.getOffset() + endLineInfo.getLength();
						positions.add(new Position(foldStart, foldEnd - foldStart));
					}
				}
			}
		} catch (BadLocationException e) {
			// ignore — return partial results
		}
		return positions;
	}
}
