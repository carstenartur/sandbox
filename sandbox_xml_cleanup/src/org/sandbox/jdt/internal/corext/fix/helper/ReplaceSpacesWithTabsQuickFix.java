/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IMarkerResolution;

/** Applies the shared, conflict-safe PDE XML cleanup to a Problems-view marker. */
public class ReplaceSpacesWithTabsQuickFix implements IMarkerResolution {

	private static final ILog LOG= Platform.getLog(ReplaceSpacesWithTabsQuickFix.class);
	private static final String PLUGIN_ID= "sandbox_xml_cleanup"; //$NON-NLS-1$

	@Override
	public String getLabel() {
		return "Normalize PDE XML formatting"; //$NON-NLS-1$
	}

	@Override
	public void run(IMarker marker) {
		IResource resource= marker.getResource();
		if (!(resource instanceof IFile file)) {
			LOG.log(new Status(IStatus.WARNING, PLUGIN_ID,
					"Marker resource is not a file: " + resource)); //$NON-NLS-1$
			return;
		}

		try {
			XMLCleanupService service= new XMLCleanupService();
			service.setEnableIndent(false);
			service.processFile(file, null);
			file.deleteMarkers(PdeXmlCleanupMarkerService.MARKER_TYPE, false, IResource.DEPTH_ZERO);
			LOG.log(new Status(IStatus.INFO, PLUGIN_ID,
					"Normalized PDE XML formatting in: " + file.getFullPath())); //$NON-NLS-1$
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, PLUGIN_ID,
					"Error applying PDE XML quick fix", e)); //$NON-NLS-1$
		}
	}
}
