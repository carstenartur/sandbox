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
package org.sandbox.jdt.internal.ui.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.sandbox.jdt.internal.corext.fix.helper.XMLCleanupService;

/**
 * Command handler for XML cleanup that works independently of JDT.
 * This allows XML cleanup to be triggered directly on PDE files or projects.
 */
public class XMLCleanupHandler extends AbstractHandler {
	
	private static final String PLUGIN_ID = "sandbox_xml_cleanup";
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		
		if (!(selection instanceof IStructuredSelection)) {
			return null;
		}
		
		IStructuredSelection structuredSelection = (IStructuredSelection) selection;
		List<IResource> resources = new ArrayList<>();
		
		// Collect all resources from selection
		for (Object element : structuredSelection.toList()) {
			IResource resource = getResource(element);
			if (resource != null) {
				resources.add(resource);
			}
		}
		
		if (resources.isEmpty()) {
			MessageDialog.openInformation(
				HandlerUtil.getActiveShell(event),
				"XML Cleanup",
				"No valid files or projects selected.");
			return null;
		}
		
		// Run cleanup in a background job
		Job job = new Job("XML Cleanup") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				return performCleanup(resources, monitor);
			}
		};
		job.setUser(true);
		job.schedule();
		
		return null;
	}
	
	/**
	 * Get IResource from a selection element.
	 */
	private IResource getResource(Object element) {
		if (element instanceof IResource) {
			return (IResource) element;
		}
		if (element instanceof IAdaptable) {
			return ((IAdaptable) element).getAdapter(IResource.class);
		}
		return null;
	}
	
	/**
	 * Create and configure the XML cleanup service.
	 */
	private XMLCleanupService createXMLCleanupService() {
		XMLCleanupService service = new XMLCleanupService();
		// TODO: Add preference support for indent option
		service.setEnableIndent(false);
		return service;
	}
	
	/**
	 * Perform the actual cleanup on selected resources.
	 */
	private IStatus performCleanup(List<IResource> resources, IProgressMonitor monitor) {
		XMLCleanupService service = createXMLCleanupService();
		
		int totalFilesProcessed = 0;
		int totalProjects = 0;
		int totalFiles = 0;
		
		// Count total work
		for (IResource resource : resources) {
			if (resource instanceof IProject) {
				totalProjects++;
			} else if (resource instanceof IFile) {
				totalFiles++;
			}
		}
		
		// Estimate work units (projects are more expensive)
		int totalWork = totalProjects * 50 + totalFiles;
		monitor.beginTask("Cleaning up PDE XML files", totalWork);
		
		try {
			for (IResource resource : resources) {
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				
				if (resource instanceof IProject) {
					IProject project = (IProject) resource;
					monitor.subTask("Processing project: " + project.getName());
					
					int filesChanged = service.processProject(project, monitor);
					totalFilesProcessed += filesChanged;
					
					monitor.worked(50);
				} else if (resource instanceof IFile) {
					IFile file = (IFile) resource;
					monitor.subTask("Processing file: " + file.getName());
					
					try {
						boolean changed = service.processFile(file, monitor);
						if (changed) {
							totalFilesProcessed++;
						}
					} catch (CoreException e) {
						return new Status(IStatus.ERROR, PLUGIN_ID,
							"Error processing file: " + file.getName(), e);
					}
					
					monitor.worked(1);
				}
			}
			
			String message = String.format("XML Cleanup completed. %d file(s) processed and updated.", 
				totalFilesProcessed);
			
			return new Status(IStatus.OK, PLUGIN_ID, message);
			
		} catch (Exception e) {
			return new Status(IStatus.ERROR, PLUGIN_ID, 
				"Error during XML cleanup", e);
		} finally {
			monitor.done();
		}
	}
}
