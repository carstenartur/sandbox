/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.history;

import java.util.Collection;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.hibernate.SessionFactory;
import org.hibernate.search.mapper.orm.Search;

/** Application orchestration for derived history; Git remains authoritative. */
public interface SandboxHistoryMaintenance {

	/** Temporary compatibility behavior until the copied backend is removed. */
	SandboxHistoryMaintenance NONE = new SandboxHistoryMaintenance() {};

	/** Called with the canonical identity after publication; projection failures must not fail an accepted push. */
	default void published(String name, Repository repository, Collection<ReceiveCommand> commands) {}

	/** Reports any known projection failure in this server process. */
	default void checkHealthy() {}

	/** Refreshes Lucene from existing ORM projections, preserving the old admin operation. */
	default void refreshIndexes(SessionFactory sessionFactory) throws InterruptedException {
		try (var session = sessionFactory.openSession()) {
			Search.session(session).massIndexer().threadsToLoadObjects(4).batchSizeToLoadObjects(25)
					.typesToIndexInParallel(2).startAndWait();
		}
	}

}
