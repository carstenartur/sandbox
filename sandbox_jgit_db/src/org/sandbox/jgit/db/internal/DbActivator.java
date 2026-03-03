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

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle for the Git Database
 * Index plugin. Manages startup and shutdown of the embedded database and
 * EGit repository tracking.
 */
public class DbActivator extends AbstractUIPlugin {

	/** The plug-in ID */
	public static final String PLUGIN_ID = "sandbox_jgit_db"; //$NON-NLS-1$

	private static DbActivator plugin;

	private EGitRepositoryTracker repositoryTracker;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		repositoryTracker = new EGitRepositoryTracker();
		repositoryTracker.start();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		if (repositoryTracker != null) {
			repositoryTracker.stop();
			repositoryTracker = null;
		}
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static DbActivator getDefault() {
		return plugin;
	}
}
