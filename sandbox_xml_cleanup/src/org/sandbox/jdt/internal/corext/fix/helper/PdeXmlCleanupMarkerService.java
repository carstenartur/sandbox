/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/** Creates Problems-view markers for safe PDE XML formatting changes. */
public final class PdeXmlCleanupMarkerService {

	/** Marker type consumed by {@link ExsdMarkerResolutionGenerator}. */
	public static final String MARKER_TYPE= "sandbox_xml_cleanup.pdeXmlCleanupProblem"; //$NON-NLS-1$

	/** Stable marker message used by tests, Help and the Problems view. */
	public static final String MARKER_MESSAGE= "PDE XML formatting can be normalized"; //$NON-NLS-1$

	private final XMLCleanupService cleanupService= new XMLCleanupService();

	/** Refreshes cleanup markers for one supported file or a complete project. */
	public int refresh(IResource resource, IProgressMonitor monitor) throws CoreException {
		if (resource instanceof IFile file) {
			return refreshFile(file, monitor);
		}
		if (resource instanceof IProject project) {
			project.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_INFINITE);
			int[] created= { 0 };
			project.accept(candidate -> {
				if (monitor != null && monitor.isCanceled()) {
					return false;
				}
				if (candidate instanceof IFile file && cleanupService.isPDERelevantFile(file)) {
					created[0]+= createMarkerIfNeeded(file, monitor);
				}
				return true;
			});
			return created[0];
		}
		return 0;
	}

	private int refreshFile(IFile file, IProgressMonitor monitor) throws CoreException {
		file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		if (!cleanupService.isPDERelevantFile(file)) {
			return 0;
		}
		return createMarkerIfNeeded(file, monitor);
	}

	private static int createMarkerIfNeeded(IFile file, IProgressMonitor monitor) throws CoreException {
		XMLResourceSupport.Transformation transformation= XMLResourceSupport.prepare(file, false, monitor);
		if (!transformation.changed()) {
			return 0;
		}

		SchemaTransformationUtils.IndentationFinding finding=
				SchemaTransformationUtils.firstConvertibleMarkupIndentation(
						transformation.snapshot().content());
		IMarker marker= file.createMarker(MARKER_TYPE);
		marker.setAttribute(IMarker.MESSAGE, MARKER_MESSAGE);
		marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
		marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_NORMAL);
		marker.setAttribute(IMarker.SOURCE_ID, "sandbox_xml_cleanup"); //$NON-NLS-1$
		if (finding != null) {
			marker.setAttribute(IMarker.LINE_NUMBER, finding.lineNumber());
			marker.setAttribute(IMarker.CHAR_START, finding.offset());
			marker.setAttribute(IMarker.CHAR_END, finding.offset() + finding.length());
		}
		return 1;
	}
}
