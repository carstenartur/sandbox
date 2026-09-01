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
package org.sandbox.jdt.internal.ui.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
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
import org.sandbox.jdt.internal.corext.fix.helper.PdeXmlCleanupMarkerService;

/** Finds safe PDE XML cleanup opportunities and publishes them in Problems. */
public class XMLCleanupMarkerHandler extends AbstractHandler {

	/** Job family used by SWTBot and callers that need deterministic completion. */
	public static final Object JOB_FAMILY= new Object();

	private static final String PLUGIN_ID= "sandbox_xml_cleanup"; //$NON-NLS-1$

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		List<IResource> resources= resources(HandlerUtil.getCurrentSelection(event));
		if (resources.isEmpty()) {
			MessageDialog.openInformation(HandlerUtil.getActiveShell(event),
					"PDE XML Cleanup", //$NON-NLS-1$
					"Select a supported PDE XML file or project first."); //$NON-NLS-1$
			return null;
		}

		Job job= new Job("Find PDE XML cleanup problems") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				PdeXmlCleanupMarkerService service= new PdeXmlCleanupMarkerService();
				int markerCount= 0;
				try {
					monitor.beginTask("Analyzing PDE XML formatting", resources.size()); //$NON-NLS-1$
					for (IResource resource : resources) {
						if (monitor.isCanceled()) {
							return Status.CANCEL_STATUS;
						}
						markerCount+= service.refresh(resource, monitor);
						monitor.worked(1);
					}
					return new Status(IStatus.OK, PLUGIN_ID,
							"PDE XML analysis created " + markerCount + " cleanup problem(s)."); //$NON-NLS-1$ //$NON-NLS-2$
				} catch (CoreException e) {
					return new Status(IStatus.ERROR, PLUGIN_ID,
							"Could not analyze PDE XML formatting", e); //$NON-NLS-1$
				} finally {
					monitor.done();
				}
			}

			@Override
			public boolean belongsTo(Object family) {
				return family == JOB_FAMILY;
			}
		};
		job.setUser(true);
		job.schedule();
		return null;
	}

	private static List<IResource> resources(ISelection selection) {
		if (!(selection instanceof IStructuredSelection structured)) {
			return List.of();
		}
		List<IResource> result= new ArrayList<>();
		for (Object element : structured.toList()) {
			IResource resource= resource(element);
			if (resource != null) {
				result.add(resource);
			}
		}
		return List.copyOf(result);
	}

	private static IResource resource(Object element) {
		if (element instanceof IResource resource) {
			return resource;
		}
		if (element instanceof IAdaptable adaptable) {
			return adaptable.getAdapter(IResource.class);
		}
		return null;
	}
}
