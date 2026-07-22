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

import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/** Standalone service for conflict-safe XML workspace cleanup. */
public class XMLCleanupService {

	private static final ILog LOG= Platform.getLog(XMLCleanupService.class);
	private static final String PLUGIN_ID= "sandbox_xml_cleanup"; //$NON-NLS-1$
	private static final Set<String> PDE_FILE_NAMES= Set.of(
			"plugin.xml", "feature.xml", "fragment.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final Set<String> PDE_EXTENSIONS= Set.of("exsd", "xsd"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final Set<String> PDE_DIRECTORIES= Set.of("OSGI-INF", "META-INF"); //$NON-NLS-1$ //$NON-NLS-2$

	private boolean enableIndent;

	/** Sets whether transformed markup is indented. */
	public void setEnableIndent(boolean enable) {
		enableIndent= enable;
	}

	/**
	 * Processes one relevant XML resource.
	 *
	 * @return {@code true} when bytes were changed
	 * @throws CoreException when the resource is dirty, concurrently modified,
	 *             too large, ambiguously encoded, malformed, or cannot be written
	 */
	public boolean processFile(IFile file, IProgressMonitor monitor) throws CoreException {
		if (monitor != null && monitor.isCanceled() || !isPDERelevantFile(file)) {
			return false;
		}
		XMLResourceSupport.Transformation transformation=
				XMLResourceSupport.prepare(file, enableIndent, monitor);
		if (!transformation.changed()) {
			return false;
		}
		int originalBytes= transformation.snapshot().originalBytes().length;
		int newBytes= transformation.transformedBytes().length;
		XMLResourceSupport.write(file, transformation, monitor);
		int bytesSaved= originalBytes - newBytes;
		double percentSaved= originalBytes > 0 ? bytesSaved * 100.0 / originalBytes : 0.0;
		LOG.log(new Status(IStatus.INFO, PLUGIN_ID,
				"Applied transformation to: " + file.getFullPath() //$NON-NLS-1$
						+ String.format(" | Size: %d → %d bytes, saved: %d bytes (%.1f%%)", //$NON-NLS-1$
								originalBytes, newBytes, bytesSaved, percentSaved)));
		return true;
	}

	/** Processes all relevant XML files in a project. */
	public int processProject(IProject project, IProgressMonitor monitor) throws CoreException {
		int[] filesProcessed= { 0 };
		project.accept(resource -> {
			if (monitor != null && monitor.isCanceled()) {
				return false;
			}
			if (resource instanceof IFile file && isPDERelevantFile(file)) {
				try {
					if (monitor != null) {
						monitor.subTask("Processing: " + file.getFullPath()); //$NON-NLS-1$
					}
					if (processFile(file, monitor)) {
						filesProcessed[0]++;
					}
				} catch (CoreException e) {
					LOG.log(e.getStatus());
				}
				if (monitor != null) {
					monitor.worked(1);
				}
			}
			return true;
		});
		return filesProcessed[0];
	}

	/** Returns whether the resource belongs to the supported PDE XML scope. */
	public boolean isPDERelevantFile(IFile file) {
		String extension= file.getFileExtension();
		return (PDE_FILE_NAMES.contains(file.getName())
				|| extension != null && PDE_EXTENSIONS.contains(extension))
				&& isInPDELocation(file);
	}

	private static boolean isInPDELocation(IFile file) {
		IResource current= file.getParent();
		if (current instanceof IProject) {
			return true;
		}
		while (current != null && !(current instanceof IProject)) {
			if (current instanceof IFolder folder) {
				String name= folder.getName();
				if (PDE_DIRECTORIES.contains(name) || "schema".equalsIgnoreCase(name)) { //$NON-NLS-1$
					return true;
				}
			}
			current= current.getParent();
		}
		return false;
	}
}
