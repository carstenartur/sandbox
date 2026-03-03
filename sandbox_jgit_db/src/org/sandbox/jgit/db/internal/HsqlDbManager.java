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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

/**
 * Manages an embedded HSQLDB instance stored in the Eclipse workspace metadata.
 * <p>
 * The database files are located at:
 * {@code {workspace}/.metadata/.plugins/sandbox.jgit.db/git-index}
 * </p>
 * <p>
 * Phase 1 implementation: Manages the database location and lifecycle.
 * The actual HSQLDB connection will be added when the Hibernate dependencies
 * are bundled (Phase 1b).
 * </p>
 */
public class HsqlDbManager {

	private static final ILog LOG = Platform.getLog(HsqlDbManager.class);

	private static final String DB_SUBDIR = "git-index"; //$NON-NLS-1$

	private Path dbPath;

	private boolean initialized;

	/**
	 * Initializes the database directory in the workspace metadata area.
	 */
	public void initialize() {
		if (initialized) {
			return;
		}
		try {
			Path stateLocation = Platform.getStateLocation(
					Platform.getBundle(DbActivator.PLUGIN_ID)).toFile().toPath();
			dbPath = stateLocation.resolve(DB_SUBDIR);
			Files.createDirectories(dbPath);
			initialized = true;
			LOG.info("Git Database Index: DB directory initialized at " + dbPath); //$NON-NLS-1$
		} catch (IOException e) {
			LOG.error("Failed to initialize database directory", e); //$NON-NLS-1$
		}
	}

	/**
	 * Returns the path to the database directory.
	 *
	 * @return database directory path, or {@code null} if not initialized
	 */
	public Path getDbPath() {
		return dbPath;
	}

	/**
	 * Returns whether the database is initialized.
	 *
	 * @return {@code true} if initialized
	 */
	public boolean isInitialized() {
		return initialized;
	}

	/**
	 * Shuts down the database connection and releases resources.
	 */
	public void shutdown() {
		initialized = false;
		LOG.info("Git Database Index: DB shut down"); //$NON-NLS-1$
	}
}
