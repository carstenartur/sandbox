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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.XMLCleanUpFixCore;

/**
 * XML cleanup processor for PDE-relevant files (plugin.xml, feature.xml, fragment.xml, *.exsd, *.xsd).
 */
public class XMLPlugin extends AbstractTool<XMLCandidateHit> {

	private static final ILog LOG = Platform.getLog(XMLPlugin.class);
	
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
	
	// Cache for processed files to avoid duplicate processing
	private final Set<Path> processedFiles = new HashSet<>();
	
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

	@Override
	public void find(XMLCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesProcessed,
			boolean createForOnlyIfVarUsed) {
		try {
			// Get the resource associated with the compilation unit
			IResource resource = compilationUnit.getJavaElement().getResource();

			if (resource == null || !resource.exists() || !(resource instanceof IFile)) {
				return;
			}

			// Iterate over all resources in the project
			IProject project = resource.getProject();
			project.accept(myResource -> {
				if (myResource.getType() == IResource.FILE && myResource instanceof IFile) {
					IFile file = (IFile) myResource;
					
					// Filter to PDE-relevant files only
					if (isPDERelevantFile(file)) {
						try {
							processFile(fixcore, file, operations, compilationUnit);
						} catch (Exception e) {
							LOG.log(new Status(IStatus.ERROR, "org.sandbox.jdt.internal.corext.fix.helper", 
								"Error processing file: " + file.getName(), e));
						}
					}
				}
				return true; // Continue iteration
			});

		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, "org.sandbox.jdt.internal.corext.fix.helper", 
				"Error during XML cleanup", e));
		}
	}
	
	/**
	 * Check if a file is PDE-relevant based on name, extension, and location.
	 * 
	 * @param file the file to check
	 * @return true if the file should be processed
	 */
	private boolean isPDERelevantFile(IFile file) {
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

	/**
	 * Process a single XML file for cleanup.
	 * 
	 * @param fixcore the cleanup fix core
	 * @param file the file to process
	 * @param operations the set of operations to add to
	 * @param compilationUnit the compilation unit for creating operations
	 * @throws Exception if processing fails
	 */
	private void processFile(XMLCleanUpFixCore fixcore, IFile file, 
			Set<CompilationUnitRewriteOperation> operations, CompilationUnit compilationUnit) 
			throws Exception {
		
		// Check if file was already processed
		Path filePath = file.getLocation().toFile().toPath();
		if (processedFiles.contains(filePath)) {
			return;
		}

		// Read original content
		String originalContent;
		try (InputStream is = file.getContents()) {
			originalContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}

		// Transform the file
		String transformedContent = SchemaTransformationUtils.transform(filePath, enableIndent);

		// Only create operation if content actually changed
		if (!originalContent.equals(transformedContent)) {
			XMLCandidateHit hit = new XMLCandidateHit(file, originalContent);
			hit.transformedContent = transformedContent;
			
			// Use the compilation unit's first node as a placeholder for the operation
			// This is required by the Eclipse cleanup framework
			List<ASTNode> astNodes = Arrays.asList((ASTNode) compilationUnit);
			if (!astNodes.isEmpty()) {
				hit.whileStatement = astNodes.get(0);
			}
			
			operations.add(fixcore.rewrite(hit));
			
			// Mark file as processed
			processedFiles.add(filePath);
			
			LOG.log(new Status(IStatus.INFO, "org.sandbox.jdt.internal.corext.fix.helper",
				"Queued transformation for: " + file.getName()));
		}
	}

	@Override
	public void rewrite(XMLCleanUpFixCore upp, final XMLCandidateHit hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		
		if (hit.file == null || hit.transformedContent == null) {
			LOG.log(new Status(IStatus.WARNING, "org.sandbox.jdt.internal.corext.fix.helper",
				"Invalid XMLCandidateHit: missing file or transformed content"));
			return;
		}
		
		try {
			// Update file contents using Eclipse workspace API
			byte[] newContent = hit.transformedContent.getBytes(StandardCharsets.UTF_8);
			ByteArrayInputStream inputStream = new ByteArrayInputStream(newContent);
			
			// Update file (don't force, keep history)
			hit.file.setContents(inputStream, IResource.KEEP_HISTORY, null);
			
			// Refresh the resource to sync with filesystem
			hit.file.refreshLocal(IResource.DEPTH_ZERO, null);
			
			LOG.log(new Status(IStatus.INFO, "org.sandbox.jdt.internal.corext.fix.helper",
				"Applied transformation to: " + hit.file.getName()));
			
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, "org.sandbox.jdt.internal.corext.fix.helper",
				"Failed to write transformed content to: " + hit.file.getName(), e));
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				<extension point="org.eclipse.ui.views">
				<view id="my.view" name="My View" class="MyView"/>
				</extension>
				</plugin>
				"""; //$NON-NLS-1$
		}
		return """
			<?xml version="1.0" encoding="UTF-8"?>
			<plugin>
			    <extension point="org.eclipse.ui.views">
			        <view id="my.view" name="My View" class="MyView" />
			    </extension>
			</plugin>
			"""; //$NON-NLS-1$
	}
}
