/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.history;

import java.util.List;
import java.util.Objects;

import org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;
import org.hibernate.SessionFactory;
import org.hibernate.search.mapper.orm.Search;

/** Temporary adapter for normal startup until the remaining resources migrate. */
public final class CopiedSandboxHistorySearch implements SandboxHistorySearch {

	private final SessionFactory sessionFactory;
	private final GitDatabaseQueryService queries;

	public CopiedSandboxHistorySearch(SessionFactory sessionFactory) {
		this.sessionFactory = Objects.requireNonNull(sessionFactory);
		queries = new GitDatabaseQueryService(sessionFactory);
	}

	@Override
	public List<CommitHit> searchMessages(String repository, String query, int offset, int limit) {
		return queries.searchCommitMessages(repository, query, offset, limit).stream()
				.map(hit -> new CommitHit(hit.getObjectId(), hit.getCommitMessage(), hit.getAuthorName(), hit.getAuthorEmail())).toList();
	}

	@Override
	public List<PathHit> searchChangedPaths(String repository, String query, int offset, int limit) {
		return queries.searchByChangedPath(repository, query, offset, limit).stream()
				.map(hit -> new PathHit(hit.getObjectId(), hit.getCommitMessage(), hit.getChangedPaths())).toList();
	}

	@Override
	public List<AuthorCount> authors(String repository) {
		return queries.getAuthorStatistics(repository).stream()
				.map(author -> new AuthorCount(author.getAuthorName(), author.getAuthorEmail(), author.getCommitCount())).toList();
	}

	@Override
	public void checkSearch() {
		try (var session = sessionFactory.openSession()) {
			Search.session(session).search(GitCommitIndex.class).where(f -> f.matchAll()).fetch(0, 0);
		}
	}
}
