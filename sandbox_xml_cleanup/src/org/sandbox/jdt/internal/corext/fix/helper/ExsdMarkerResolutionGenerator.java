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

/**
 * Marker resolution generator for EXSD cleanup markers.
 */
public class ExsdMarkerResolutionGenerator implements IMarkerResolutionGenerator2 {

	private static final ILog LOG = Platform.getLog(ExsdMarkerResolutionGenerator.class);
	private static final String PLUGIN_ID = "org.sandbox.jdt.internal.corext.fix.helper";
	
	@Override
	public boolean hasResolutions(IMarker marker) {
		try {
			return "my.exsd.cleanup.marker".equals(marker.getType());
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, PLUGIN_ID,
				"Error checking marker type", e));
		}
		return false;
	}

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		return new IMarkerResolution[] {
			new ReplaceSpacesWithTabsQuickFix()
		};
	}
}
