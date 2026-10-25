/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.SessionFactory;
import org.hibernate.search.mapper.orm.Search;

import io.github.carstenartur.jgit.storage.hibernate.search.entity.GitCommitIndex;
import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitHistoryQuery;
import io.github.carstenartur.jgit.storage.hibernate.search.service.GitHistorySearchService;

/** Maps released Search results to the established Sandbox REST contract. */
public final class ReleasedSandboxHistorySearch implements SandboxHistorySearch {

	private final SessionFactory sessionFactory;
	private final GitHistorySearchService queries;

	public ReleasedSandboxHistorySearch(SessionFactory sessionFactory) {
		this.sessionFactory = Objects.requireNonNull(sessionFactory);
		queries = new GitHistorySearchService(sessionFactory);
	}

	@Override
	public List<CommitHit> searchMessages(String repository, String query, int offset, int limit) {
		if (blank(repository) || blank(query)) {
			return List.of();
		}
		return page(CommitHistoryQuery.forRepository(repository).matchingMessages(literal(query)), offset, limit).stream()
				.map(hit -> new CommitHit(hit.getObjectId(), hit.getFullMessage(), hit.getAuthorName(), hit.getAuthorEmail())).toList();
	}

	@Override
	public List<PathHit> searchChangedPaths(String repository, String query, int offset, int limit) {
		if (blank(repository) || blank(query)) {
			return List.of();
		}
		return page(CommitHistoryQuery.forRepository(repository).touchingPathTerms(literal(query)), offset, limit).stream()
				.map(hit -> new PathHit(hit.getObjectId(), hit.getFullMessage(), hit.getChangedPaths())).toList();
	}

	private List<GitCommitIndex> page(CommitHistoryQuery.Builder builder, int offset, int limit) {
		if (offset < 0 || limit < 0) {
			throw new IllegalArgumentException("offset and limit must not be negative");
		}
		if (limit == 0) {
			return List.of();
		}
		CommitHistoryQuery query = builder.unbounded().build();
		List<String> ids = new ArrayList<>();
		// Use the same stable ordering for shallow and deep pages, without the
		// library's offset ceiling or materializing all skipped entity payloads.
		try (var cursor = queries.scrollChangeSummaries(query)) {
			long remaining = offset;
			while (ids.size() < limit) {
				var chunk = cursor.nextChunk();
				if (chunk.isEmpty()) {
					break;
				}
				for (var hit : chunk) {
					if (remaining > 0) {
						remaining--;
					} else if (ids.size() < limit) {
						ids.add(hit.objectId());
					}
				}
			}
		}
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<String, GitCommitIndex> entities = new HashMap<>();
		queries.findChanges(CommitHistoryQuery.forRepository(query.repositoryName())
				.restrictedToObjectIds(ids).limit(ids.size()).build())
				.forEach(hit -> entities.put(hit.getObjectId(), hit));
		return ids.stream().map(entities::get).filter(Objects::nonNull).toList();
	}

	@Override
	public List<AuthorCount> authors(String repository) {
		if (blank(repository)) {
			return List.of();
		}
		record Identity(String name, String email) { }
		Map<Identity, Long> counts = new HashMap<>();
		try (var cursor = queries.scrollChangeSummaries(CommitHistoryQuery.forRepository(repository).unbounded().build())) {
			while (true) {
				var chunk = cursor.nextChunk();
				if (chunk.isEmpty()) {
					break;
				}
				for (var hit : chunk) {
					counts.merge(new Identity(hit.authorName(), hit.authorEmail()), 1L, Long::sum);
				}
			}
		}
		Comparator<String> textOrder = Comparator.nullsFirst(Comparator.naturalOrder());
		return counts.entrySet().stream()
				.map(entry -> new AuthorCount(entry.getKey().name(), entry.getKey().email(), entry.getValue()))
				.sorted(Comparator.comparingLong(AuthorCount::commits).reversed()
						.thenComparing(AuthorCount::name, textOrder).thenComparing(AuthorCount::email, textOrder)).toList();
	}

	@Override
	public void checkSearch() {
		try (var session = sessionFactory.openSession()) {
			Search.session(session).search(GitCommitIndex.class).where(f -> f.matchAll()).fetch(0, 0);
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String literal(String query) {
		StringBuilder escaped = new StringBuilder();
		for (int i = 0; i < query.length(); i++) {
			char c = query.charAt(i);
			if ("+-|*\"()\\~".indexOf(c) >= 0) {
				escaped.append('\\');
			}
			escaped.append(c);
		}
		return escaped.toString();
	}
}
