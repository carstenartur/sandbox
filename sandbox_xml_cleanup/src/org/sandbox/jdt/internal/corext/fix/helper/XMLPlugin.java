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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.XMLCleanUpFixCore;

/** XML cleanup processor for PDE-relevant workspace resources. */
public class XMLPlugin extends AbstractTool<XMLCandidateHit> {

	private static final ILog LOG= Platform.getLog(XMLPlugin.class);
	private static final String PLUGIN_ID= "sandbox_xml_cleanup"; //$NON-NLS-1$
	private static final Set<String> PDE_FILE_NAMES= Set.of(
			"plugin.xml", "feature.xml", "fragment.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final Set<String> PDE_EXTENSIONS= Set.of("exsd", "xsd"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final Set<String> PDE_DIRECTORIES= Set.of("OSGI-INF", "META-INF"); //$NON-NLS-1$ //$NON-NLS-2$

	private final Set<IPath> processedFiles= new HashSet<>();
	private Set<CompilationUnitRewriteOperation> activeOperations;
	private boolean enableIndent;

	/** Sets whether transformed markup is indented. */
	public void setEnableIndent(boolean enable) {
		enableIndent= enable;
	}

	@Override
	public synchronized void find(XMLCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesProcessed,
			boolean createForOnlyIfVarUsed) {
		beginRun(operations);
		try {
			IResource resource= compilationUnit.getJavaElement().getResource();
			if (!(resource instanceof IFile) || !resource.exists()) {
				return;
			}
			IProject project= resource.getProject();
			project.accept(candidate -> {
				if (candidate instanceof IFile file && isPDERelevantFile(file)) {
					try {
						processFile(fixcore, file, operations, compilationUnit);
					} catch (CoreException e) {
						LOG.log(e.getStatus());
					}
				}
				return true;
			});
		} catch (CoreException e) {
			LOG.log(new Status(IStatus.ERROR, PLUGIN_ID, "Error during XML cleanup", e)); //$NON-NLS-1$
		}
	}

	private void beginRun(Set<CompilationUnitRewriteOperation> operations) {
		if (activeOperations != operations) {
			activeOperations= operations;
			processedFiles.clear();
		}
	}

	private void processFile(XMLCleanUpFixCore fixcore, IFile file,
			Set<CompilationUnitRewriteOperation> operations, CompilationUnit compilationUnit)
			throws CoreException {
		IPath resourcePath= file.getFullPath();
		if (!processedFiles.add(resourcePath)) {
			return;
		}
		try {
			XMLResourceSupport.Transformation transformation=
					XMLResourceSupport.prepare(file, enableIndent, null);
			if (!transformation.changed()) {
				return;
			}
			XMLCandidateHit hit= new XMLCandidateHit(file, transformation);
			hit.whileStatement= compilationUnit;
			operations.add(fixcore.rewrite(hit));
			LOG.log(new Status(IStatus.INFO, PLUGIN_ID,
					"Queued transformation for: " + file.getFullPath())); //$NON-NLS-1$
		} catch (CoreException e) {
			processedFiles.remove(resourcePath);
			throw e;
		}
	}

	@Override
	public void rewrite(XMLCleanUpFixCore fixcore, XMLCandidateHit hit,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		if (hit.file == null || hit.transformation == null) {
			LOG.log(new Status(IStatus.WARNING, PLUGIN_ID,
					"Invalid XML cleanup candidate")); //$NON-NLS-1$
			return;
		}
		try {
			XMLResourceSupport.write(hit.file, hit.transformation, null);
			LOG.log(new Status(IStatus.INFO, PLUGIN_ID,
					"Applied transformation to: " + hit.file.getFullPath())); //$NON-NLS-1$
		} catch (CoreException e) {
			LOG.log(e.getStatus());
		}
	}

	private static boolean isPDERelevantFile(IFile file) {
		String fileName= file.getName();
		String extension= file.getFileExtension();
		return (PDE_FILE_NAMES.contains(fileName)
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

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				/* XML Cleanup - After:
				 * - Empty elements collapsed to self-closing
				 * - Whitespace optimized
				 */
				// <?xml version="1.0" encoding="UTF-8"?>
				// <plugin>
				// <extension point="org.eclipse.ui.views"/>
				// <view id="my.view" name="My View"/>
				// </plugin>
				"""; //$NON-NLS-1$
		}
		return """
			/* XML Cleanup - Before:
			 * - Empty elements with closing tags
			 * - Extra whitespace
			 */
			// <?xml version="1.0" encoding="UTF-8"?>
			// <plugin>
			//     <extension point="org.eclipse.ui.views">
			//     </extension>
			//     <view id="my.view" name="My View">
			//     </view>
			// </plugin>
			"""; //$NON-NLS-1$
	}
}
