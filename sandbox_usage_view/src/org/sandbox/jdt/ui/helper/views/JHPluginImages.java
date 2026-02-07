/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
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

package org.sandbox.jdt.ui.helper.views;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.FrameworkUtil;

public class JHPluginImages {

	private static final URL fgIconBaseURL= FrameworkUtil.getBundle(JHPluginImages.class).getEntry("/icons/"); //$NON-NLS-1$

	public static final String CHILDREN= "children.png"; //$NON-NLS-1$
	public static final String INFO= "info.png"; //$NON-NLS-1$
	public static final String PROPERTIES= "properties.png"; //$NON-NLS-1$
	public static final String REFRESH= "refresh.png"; //$NON-NLS-1$
	public static final String SET_FOCUS= "setfocus.png"; //$NON-NLS-1$
	public static final String CODE_SELECT= "codeSelect.png"; //$NON-NLS-1$
	public static final String FILTER_CONFLICTS= "filterConflicts.png"; //$NON-NLS-1$

	public static final ImageDescriptor IMG_CHILDREN= create(CHILDREN);
	public static final ImageDescriptor IMG_INFO= create(INFO);

	public static final ImageDescriptor IMG_PROPERTIES= create(PROPERTIES);
	public static final ImageDescriptor IMG_REFRESH= create(REFRESH);
	public static final ImageDescriptor IMG_SET_FOCUS= create(SET_FOCUS);
	public static final ImageDescriptor IMG_SET_FOCUS_CODE_SELECT= create(CODE_SELECT);
	public static final ImageDescriptor IMG_FILTER_CONFLICTS= createFilterConflictsIcon();

	private static ImageDescriptor create(String name) {
		try {
			return ImageDescriptor.createFromURL(new URL(fgIconBaseURL, name));
		} catch (MalformedURLException e) {
			return ImageDescriptor.getMissingImageDescriptor();
		}
	}

	/**
	 * Creates the filter conflicts icon descriptor.
	 * Falls back to a platform warning icon if custom icon is not available.
	 */
	private static ImageDescriptor createFilterConflictsIcon() {
		try {
			URL iconURL = new URL(fgIconBaseURL, FILTER_CONFLICTS);
			// Check if file exists by trying to open it
			iconURL.openStream().close();
			return ImageDescriptor.createFromURL(iconURL);
		} catch (Exception e) {
			// Fall back to platform warning icon
			return PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJS_WARN_TSK);
		}
	}

	private JHPluginImages() {
	}
}
