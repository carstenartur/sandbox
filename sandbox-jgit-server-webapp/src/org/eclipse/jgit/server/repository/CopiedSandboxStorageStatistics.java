/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.repository;

import org.hibernate.SessionFactory;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;

/** Temporary inventory adapter for the copied startup path. */
public final class CopiedSandboxStorageStatistics implements SandboxStorageStatistics {
	private final GitDatabaseQueryService query;
	public CopiedSandboxStorageStatistics(SessionFactory factory) {
		query= new GitDatabaseQueryService(factory);
	}
	@Override
	public ObjectCounts objects(String repository) {
		return new ObjectCounts(query.countObjectsByType(repository, 1), query.countObjectsByType(repository, 2),
				query.countObjectsByType(repository, 3), query.countObjectsByType(repository, 4));
	}
	@Override
	public PackCounts packs(String repository) {
		return new PackCounts(query.countPacks(repository), query.getTotalPackSize(repository));
	}
}
