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
package org.eclipse.jgit.storage.hibernate.refs;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsReftableDatabase;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;

/**
 * A ref database backed by Hibernate, extending the DFS reftable approach.
 * <p>
 * This implementation uses the reftable storage mechanism from the DFS layer,
 * which stores reftable data as pack extensions. The pack data itself is
 * persisted to the database via
 * {@link org.eclipse.jgit.storage.hibernate.objects.HibernateObjDatabase}.
 * <p>
 * Atomic transactions are natively supported via database transactions.
 */
public class HibernateRefDatabase extends DfsReftableDatabase {

	/**
	 * Create a new Hibernate-backed ref database.
	 *
	 * @param repo
	 *            the DFS repository
	 */
	public HibernateRefDatabase(DfsRepository repo) {
		super(repo);
	}

	@Override
	public ReftableConfig getReftableConfig() {
		ReftableConfig cfg = new ReftableConfig();
		cfg.setAlignBlocks(false);
		cfg.setIndexObjects(false);
		cfg.fromConfig(getRepository().getConfig());
		return cfg;
	}

	@Override
	public boolean performsAtomicTransactions() {
		return true;
	}
}
