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
package org.sandbox.jdt.internal.ui.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.sandbox.jdt.internal.corext.fix.helper.XMLCleanupService;

/**
 * Action to trigger XML cleanup directly on PDE files or projects.
 * This allows XML cleanup to work without requiring Java compilation unit context.
 */
public class XMLCleanupAction implements IObjectActionDelegate {
	
	private static final String PLUGIN_ID = "sandbox_xml_cleanup";
	private ISelection selection;
	private IWorkbenchPart targetPart;
	
	@Override
	public void run(IAction action) {
		if (!(selection instanceof IStructuredSelection)) {
			return;
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
			if (targetPart != null) {
				MessageDialog.openInformation(
					targetPart.getSite().getShell(),
					"XML Cleanup",
					"No valid files or projects selected.");
			}
			return;
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
	 * Perform the actual cleanup on selected resources.
	 */
	private IStatus performCleanup(List<IResource> resources, IProgressMonitor monitor) {
		XMLCleanupService service = new XMLCleanupService();
		// TODO: Add preference support for indent option
		service.setEnableIndent(false);
		
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
					} catch (Exception e) {
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
	
	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;
	}
	
	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		this.targetPart = targetPart;
	}
}
