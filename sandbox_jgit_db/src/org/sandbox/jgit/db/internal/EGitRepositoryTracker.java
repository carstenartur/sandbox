/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jgit.db.internal;

import java.io.File;
import java.util.Collection;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.jgit.lib.Repository;

/**
 * Tracks EGit-managed repositories and triggers indexing when changes are
 * detected. Listens for workspace resource changes that affect {@code .git}
 * directories (refs, objects) to detect new commits from EGit operations.
 *
 * <p>
 * Integration points with EGit:
 * </p>
 * <ul>
 * <li>{@code RepositoryCache.INSTANCE.getAllRepositories()} — all known repos</li>
 * <li>{@code IResourceChangeListener} on .git/refs and .git/objects — detects new commits</li>
 * </ul>
 */
public class EGitRepositoryTracker implements IResourceChangeListener {

	private static final ILog LOG = Platform.getLog(EGitRepositoryTracker.class);

	private static final String GIT_DIR = ".git"; //$NON-NLS-1$

	/**
	 * Starts tracking EGit repositories by registering a resource change
	 * listener.
	 */
	public void start() {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this,
				IResourceChangeEvent.POST_CHANGE);
		LOG.info("Git Database Index: Repository tracker started"); //$NON-NLS-1$
	}

	/**
	 * Stops tracking by removing the resource change listener.
	 */
	public void stop() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		LOG.info("Git Database Index: Repository tracker stopped"); //$NON-NLS-1$
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();
		if (delta == null) {
			return;
		}
		if (containsGitChanges(delta)) {
			scheduleIndexUpdate();
		}
	}

	private boolean containsGitChanges(IResourceDelta delta) {
		if (delta.getResource() != null
				&& delta.getResource().getName().equals(GIT_DIR)) {
			return true;
		}
		for (IResourceDelta child : delta.getAffectedChildren()) {
			if (containsGitChanges(child)) {
				return true;
			}
		}
		return false;
	}

	private void scheduleIndexUpdate() {
		Collection<Repository> repositories = getAllRepositories();
		if (repositories.isEmpty()) {
			return;
		}
		LOG.info("Git Database Index: Detected Git changes, " //$NON-NLS-1$
				+ repositories.size() + " repositories known"); //$NON-NLS-1$
	}

	/**
	 * Returns all EGit-managed repositories in the workspace.
	 *
	 * @return collection of repositories
	 */
	public Collection<Repository> getAllRepositories() {
		return RepositoryCache.INSTANCE.getAllRepositories().stream()
				.map(this::toRepository).filter(r -> r != null).toList();
	}

	private Repository toRepository(File gitDir) {
		try {
			return org.eclipse.jgit.storage.file.FileRepositoryBuilder
					.create(gitDir);
		} catch (Exception e) {
			LOG.error("Failed to open repository: " + gitDir, e); //$NON-NLS-1$
			return null;
		}
	}
}
