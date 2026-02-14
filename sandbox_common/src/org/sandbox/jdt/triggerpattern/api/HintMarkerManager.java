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
package org.sandbox.jdt.triggerpattern.api;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;

/**
 * Manages Eclipse problem markers for transformation results.
 *
 * <p>Creates {@link IMarker} instances for {@link TransformationResult} entries
 * so that hint-file matches appear in the Eclipse Problems view. Markers are
 * created with the custom type {@value #MARKER_TYPE} and carry the standard
 * attributes ({@code SEVERITY}, {@code LINE_NUMBER}, {@code MESSAGE},
 * {@code CHAR_START}, {@code CHAR_END}).</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * HintMarkerManager manager = new HintMarkerManager();
 * manager.createMarkers(resource, results);
 * // later...
 * manager.clearMarkers(resource);
 * </pre>
 *
 * @since 1.3.6
 */
public final class HintMarkerManager {

	/**
	 * The marker type used for trigger-pattern hints.
	 */
	public static final String MARKER_TYPE = "org.sandbox.jdt.triggerpattern.hint"; //$NON-NLS-1$

	/**
	 * Creates problem markers for the given transformation results on the
	 * specified resource.
	 *
	 * <p>Each result produces one marker. The marker severity is determined by
	 * whether the result provides a replacement (warning) or not (info).</p>
	 *
	 * @param resource the resource to attach markers to
	 * @param results the transformation results
	 * @throws CoreException if marker creation fails
	 */
	public void createMarkers(IResource resource, List<TransformationResult> results) throws CoreException {
		for (TransformationResult result : results) {
			IMarker marker = resource.createMarker(MARKER_TYPE);
			
			// Severity: warning if a fix is available, info otherwise
			int severity = result.hasReplacement() ? IMarker.SEVERITY_WARNING : IMarker.SEVERITY_INFO;
			marker.setAttribute(IMarker.SEVERITY, severity);
			
			// Line number
			marker.setAttribute(IMarker.LINE_NUMBER, result.lineNumber());
			
			// Message
			String message = buildMessage(result);
			marker.setAttribute(IMarker.MESSAGE, message);
			
			// Character range
			int offset = result.match().getOffset();
			marker.setAttribute(IMarker.CHAR_START, offset);
			marker.setAttribute(IMarker.CHAR_END, offset + result.match().getLength());
		}
	}

	/**
	 * Removes all trigger-pattern hint markers from the given resource.
	 *
	 * @param resource the resource to clear markers from
	 * @throws CoreException if marker deletion fails
	 */
	public void clearMarkers(IResource resource) throws CoreException {
		resource.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
	}

	/**
	 * Builds a human-readable marker message from a transformation result.
	 */
	private String buildMessage(TransformationResult result) {
		String description = result.description();
		if (description != null && !description.isEmpty()) {
			return description;
		}
		if (result.hasReplacement()) {
			return "TriggerPattern: can be simplified"; //$NON-NLS-1$
		}
		return "TriggerPattern: code pattern detected"; //$NON-NLS-1$
	}
}
