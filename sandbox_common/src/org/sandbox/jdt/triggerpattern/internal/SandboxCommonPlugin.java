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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

/**
 * Bundle lifecycle for Eclipse-specific TriggerPattern infrastructure in
 * {@code sandbox_common}.
 *
 * <p>The listener keeps project-local {@code .sandbox-hint} and NetBeans
 * {@code .hint} rules coherent with the workspace. A rule created or edited in
 * the same Eclipse session must be visible to both Hint File Quick Assist and
 * the Hint File cleanup without requiring a workbench restart.</p>
 */
public final class SandboxCommonPlugin extends Plugin {

	private IResourceChangeListener hintFileListener;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		hintFileListener= SandboxCommonPlugin::handleResourceChange;
		ResourcesPlugin.getWorkspace().addResourceChangeListener(hintFileListener,
				IResourceChangeEvent.POST_CHANGE | IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.PRE_DELETE);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		if (hintFileListener != null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(hintFileListener);
			hintFileListener= null;
		}
		super.stop(context);
	}

	private static void handleResourceChange(IResourceChangeEvent event) {
		IResource eventResource= event.getResource();
		if ((event.getType() == IResourceChangeEvent.PRE_CLOSE
				|| event.getType() == IResourceChangeEvent.PRE_DELETE)
				&& eventResource instanceof IProject project) {
			invalidateProjectRules(project);
			return;
		}

		IResourceDelta delta= event.getDelta();
		if (delta == null) {
			return;
		}

		Set<IProject> affectedProjects= new HashSet<>();
		try {
			delta.accept(resourceDelta -> {
				IResource resource= resourceDelta.getResource();
				if (resource instanceof IFile file && isHintFile(file.getName())) {
					affectedProjects.add(file.getProject());
				}
				addMovedProject(affectedProjects, resourceDelta.getMovedFromPath());
				addMovedProject(affectedProjects, resourceDelta.getMovedToPath());
				return true;
			});
		} catch (CoreException e) {
			ILog log= PlatformLogHolder.LOG;
			log.log(Status.warning("Failed to inspect workspace changes for Hint DSL files", e)); //$NON-NLS-1$
			return;
		}

		affectedProjects.forEach(SandboxCommonPlugin::invalidateProjectRules);
	}

	private static void addMovedProject(Set<IProject> projects, IPath path) {
		if (path == null || path.segmentCount() < 2 || !isHintFile(path.lastSegment())) {
			return;
		}
		projects.add(ResourcesPlugin.getWorkspace().getRoot().getProject(path.segment(0)));
	}

	private static boolean isHintFile(String name) {
		return name != null && (name.endsWith(".sandbox-hint") || name.endsWith(".hint")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void invalidateProjectRules(IProject project) {
		if (project == null) {
			return;
		}
		HintFileRegistry registry= HintFileRegistry.getInstance();
		String projectPrefix= "project:" + project.getName() + ":"; //$NON-NLS-1$ //$NON-NLS-2$
		for (String id : registry.getRegisteredIds()) {
			if (id.startsWith(projectPrefix)) {
				registry.unregister(id);
				EmbeddedGuardRegistrar.unregisterGuards(id);
				EmbeddedFixExecutor.unregisterFixes(id);
			}
		}
		registry.invalidateProject(project);
	}

	/**
	 * Defers logger lookup until a workspace change actually needs to report an
	 * error, avoiding unnecessary plugin activation work during normal startup.
	 */
	private static final class PlatformLogHolder {
		private static final ILog LOG= org.eclipse.core.runtime.Platform.getLog(SandboxCommonPlugin.class);

		private PlatformLogHolder() {
		}
	}
}
