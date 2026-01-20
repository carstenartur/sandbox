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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

/**
 * Service for XML cleanup that can be used independently of JDT cleanup framework.
 * This allows XML cleanup to work without requiring Java compilation unit context.
 */
public class XMLCleanupService {
	
	private static final ILog LOG = Platform.getLog(XMLCleanupService.class);
	private static final String PLUGIN_ID = "sandbox_xml_cleanup";
	
	// PDE-relevant file names
	private static final Set<String> PDE_FILE_NAMES = Set.of(
		"plugin.xml",
		"feature.xml", 
		"fragment.xml"
	);
	
	// PDE-relevant file extensions
	private static final Set<String> PDE_EXTENSIONS = Set.of("exsd", "xsd");
	
	// PDE-typical directories
	private static final Set<String> PDE_DIRECTORIES = Set.of("OSGI-INF", "META-INF");
	
	// Indentation preference (default: false for size reduction)
	private boolean enableIndent = false;

	/**
	 * Set whether to enable indentation in XML output.
	 * 
	 * @param enable true to enable indentation, false for compact output (default)
	 */
	public void setEnableIndent(boolean enable) {
		this.enableIndent = enable;
	}

	/**
	 * Process a single XML file.
	 * 
	 * @param file the file to process
	 * @param monitor progress monitor (can be null)
	 * @return true if file was processed and changed, false otherwise
	 * @throws Exception if transformation fails
	 */
	public boolean processFile(IFile file, IProgressMonitor monitor) throws Exception {
		if (monitor != null && monitor.isCanceled()) {
			return false;
		}
		
		if (!isPDERelevantFile(file)) {
			return false;
		}
		
		// Read original content
		String originalContent;
		try (InputStream is = file.getContents()) {
			originalContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
		
		// Transform the file
		Path filePath = file.getLocation().toFile().toPath();
		String transformedContent = SchemaTransformationUtils.transform(filePath, enableIndent);
		
		// Only write if content actually changed
		if (!originalContent.equals(transformedContent)) {
			byte[] newContent = transformedContent.getBytes(StandardCharsets.UTF_8);
			ByteArrayInputStream inputStream = new ByteArrayInputStream(newContent);
			
			// Update file (don't force, keep history)
			file.setContents(inputStream, IResource.KEEP_HISTORY, monitor);
			
			// Refresh the resource to sync with filesystem
			file.refreshLocal(IResource.DEPTH_ZERO, monitor);
			
			LOG.log(new Status(IStatus.INFO, PLUGIN_ID,
				"Applied transformation to: " + file.getName()));
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Process all PDE XML files in a project.
	 * 
	 * @param project the project to process
	 * @param monitor progress monitor (can be null)
	 * @return number of files processed and changed
	 * @throws CoreException if resource traversal fails
	 */
	public int processProject(IProject project, IProgressMonitor monitor) throws CoreException {
		final int[] filesProcessed = {0};
		
		project.accept(new IResourceVisitor() {
			@Override
			public boolean visit(IResource resource) throws CoreException {
				if (monitor != null && monitor.isCanceled()) {
					return false;
				}
				
				if (resource.getType() == IResource.FILE && resource instanceof IFile) {
					IFile file = (IFile) resource;
					
					if (isPDERelevantFile(file)) {
						try {
							if (monitor != null) {
								monitor.subTask("Processing: " + file.getName());
							}
							
							boolean changed = processFile(file, monitor);
							if (changed) {
								filesProcessed[0]++;
							}
							
							if (monitor != null) {
								monitor.worked(1);
							}
						} catch (Exception e) {
							LOG.log(new Status(IStatus.ERROR, PLUGIN_ID, 
								"Error processing file: " + file.getName(), e));
						}
					}
				}
				return true; // Continue iteration
			}
		});
		
		return filesProcessed[0];
	}
	
	/**
	 * Check if file is PDE-relevant.
	 * 
	 * @param file the file to check
	 * @return true if the file should be processed
	 */
	public boolean isPDERelevantFile(IFile file) {
		String fileName = file.getName();
		String extension = file.getFileExtension();
		
		// Check if it's a known PDE file name
		if (PDE_FILE_NAMES.contains(fileName)) {
			// Must be in project root, OSGI-INF, or META-INF
			return isInPDELocation(file);
		}
		
		// Check if it's a PDE extension (exsd, xsd)
		if (extension != null && PDE_EXTENSIONS.contains(extension)) {
			return isInPDELocation(file);
		}
		
		return false;
	}
	
	/**
	 * Check if file is in a PDE-typical location.
	 * 
	 * @param file the file to check
	 * @return true if in root, OSGI-INF, or META-INF
	 */
	private boolean isInPDELocation(IFile file) {
		IResource parent = file.getParent();
		
		// Check if in project root
		if (parent instanceof IProject) {
			return true;
		}
		
		// Check if in OSGI-INF or META-INF
		if (parent instanceof IFolder) {
			String folderName = parent.getName();
			if (PDE_DIRECTORIES.contains(folderName)) {
				return true;
			}
			
			// Also check parent's parent (for nested structures)
			IResource grandParent = parent.getParent();
			if (grandParent instanceof IFolder) {
				if (PDE_DIRECTORIES.contains(grandParent.getName())) {
					return true;
				}
			}
		}
		
		return false;
	}
}
