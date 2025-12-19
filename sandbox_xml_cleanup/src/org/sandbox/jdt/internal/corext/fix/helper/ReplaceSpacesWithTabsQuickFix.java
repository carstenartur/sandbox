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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IMarkerResolution;

/**
 * Quick fix to replace leading 4 spaces with tabs in XML files.
 * Only replaces spaces at the beginning of lines, not inside text content.
 */
public class ReplaceSpacesWithTabsQuickFix implements IMarkerResolution {
	
	private static final ILog LOG = Platform.getLog(ReplaceSpacesWithTabsQuickFix.class);
	
	@Override
	public String getLabel() {
		return "Replace leading 4 spaces with tabs";
	}

	@Override
	public void run(IMarker marker) {
		try {
			IResource resource = marker.getResource();
			if (!(resource instanceof IFile)) {
				LOG.log(new Status(IStatus.WARNING, "org.sandbox.jdt.internal.corext.fix.helper",
					"Marker resource is not a file: " + resource));
				return;
			}
			
			IFile file = (IFile) resource;
			
			// Read file content
			String content;
			try (InputStream is = file.getContents()) {
				content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}

			// Replace leading 4 spaces with tabs (line by line to avoid replacing inline spaces)
			String[] lines = content.split("\r?\n", -1); // -1 to preserve empty lines
			StringBuilder result = new StringBuilder();
			
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				
				// Count leading spaces
				int leadingSpaces = 0;
				while (leadingSpaces < line.length() && line.charAt(leadingSpaces) == ' ') {
					leadingSpaces++;
				}
				
				// Convert groups of 4 leading spaces to tabs
				int numTabs = leadingSpaces / 4;
				int remainingSpaces = leadingSpaces % 4;
				
				// Rebuild line with tabs
				if (numTabs > 0) {
					result.append("\t".repeat(numTabs));
				}
				if (remainingSpaces > 0) {
					result.append(" ".repeat(remainingSpaces));
				}
				result.append(line.substring(leadingSpaces));
				
				if (i < lines.length - 1) {
					result.append("\n");
				}
			}

			// Write back using Eclipse workspace API
			byte[] newContent = result.toString().getBytes(StandardCharsets.UTF_8);
			ByteArrayInputStream inputStream = new ByteArrayInputStream(newContent);
			file.setContents(inputStream, IResource.KEEP_HISTORY, null);
			
			// Refresh resource
			file.refreshLocal(IResource.DEPTH_ZERO, null);

			// Delete marker
			marker.delete();
			
			LOG.log(new Status(IStatus.INFO, "org.sandbox.jdt.internal.corext.fix.helper",
				"Replaced leading spaces with tabs in: " + file.getName()));
			
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, "org.sandbox.jdt.internal.corext.fix.helper",
				"Error applying quick fix", e));
		} catch (Exception e) {
			LOG.log(new Status(IStatus.ERROR, "org.sandbox.jdt.internal.corext.fix.helper",
				"Unexpected error applying quick fix", e));
		}
	}
}
