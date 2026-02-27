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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.eclipse;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

/**
 * Utility for creating and clearing Eclipse problem markers for hint-only
 * findings.
 *
 * <p>Hint-only findings are code patterns that should be flagged but not
 * automatically rewritten. This reporter creates {@link IMarker} instances
 * so findings appear in the Problems view with editor underlines and
 * hover tooltips — matching the NetBeans Jackpot hint behavior.</p>
 *
 * <p>Uses the marker type registered by the {@code sandbox_common} bundle:
 * {@value HintMarkerManager#MARKER_TYPE}.</p>
 */
public final class HintMarkerReporter {

	private HintMarkerReporter() {
		// utility class
	}

	/**
	 * Removes all sandbox hint markers from the given resource.
	 *
	 * @param resource the resource to clear markers from
	 * @throws CoreException if marker deletion fails
	 */
	public static void clearMarkers(IResource resource) throws CoreException {
		resource.deleteMarkers(HintMarkerManager.MARKER_TYPE, true, IResource.DEPTH_ZERO);
	}

	/**
	 * Creates problem markers for each hint-only finding on the resource.
	 *
	 * @param resource the resource to attach markers to
	 * @param findings the hint-only findings to report
	 * @throws CoreException if marker creation fails
	 */
	public static void reportFindings(IResource resource,
			List<HintFinding> findings) throws CoreException {
		for (HintFinding finding : findings) {
			IMarker marker = resource.createMarker(HintMarkerManager.MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, finding.message());
			marker.setAttribute(IMarker.SEVERITY, finding.severity());
			marker.setAttribute(IMarker.LINE_NUMBER, finding.lineNumber());
			marker.setAttribute(IMarker.CHAR_START, finding.charStart());
			marker.setAttribute(IMarker.CHAR_END, finding.charEnd());
		}
	}
}
