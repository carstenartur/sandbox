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

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;

/** Marker resolution generator for actionable PDE XML cleanup problems. */
public class ExsdMarkerResolutionGenerator implements IMarkerResolutionGenerator2 {

	private static final ILog LOG= Platform.getLog(ExsdMarkerResolutionGenerator.class);
	private static final String PLUGIN_ID= "sandbox_xml_cleanup"; //$NON-NLS-1$

	@Override
	public boolean hasResolutions(IMarker marker) {
		try {
			return PdeXmlCleanupMarkerService.MARKER_TYPE.equals(marker.getType());
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, PLUGIN_ID,
					"Error checking PDE XML marker type", e)); //$NON-NLS-1$
			return false;
		}
	}

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		return hasResolutions(marker)
				? new IMarkerResolution[] { new ReplaceSpacesWithTabsQuickFix() }
				: new IMarkerResolution[0];
	}
}
