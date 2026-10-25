/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.history;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.hibernate.SessionFactory;

import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitIndexer;
import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitProjectionRebuilder;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;

/** Uses released indexing services, with bounded application-owned writer locks. */
public final class ReleasedSandboxHistoryMaintenance implements SandboxHistoryMaintenance {
	private static final Logger LOG = Logger.getLogger(ReleasedSandboxHistoryMaintenance.class.getName());

	private final SessionFactory sessionFactory;
	private final ReentrantLock[] locks = IntStream.range(0, 32).mapToObj(i -> new ReentrantLock())
			.toArray(ReentrantLock[]::new);
	private final Set<String> failures = ConcurrentHashMap.newKeySet();
	private static final int MAX_REPOSITORIES = 256;
	private static final int MAX_COMPLETE_TIPS = 128;
	private final LinkedHashMap<String, LinkedHashSet<String>> completeTips = new LinkedHashMap<>(16, .75f, true);

	public ReleasedSandboxHistoryMaintenance(SessionFactory sessionFactory) {
		this.sessionFactory = Objects.requireNonNull(sessionFactory);
	}

	@Override
	public void published(String name, Repository repository, Collection<ReceiveCommand> commands) {
		String normalized = Objects.requireNonNull(name, "canonical repository name");
		var tips = commands.stream().filter(command -> command.getResult() == ReceiveCommand.Result.OK)
				.map(ReceiveCommand::getNewId).filter(id -> !ObjectId.zeroId().equals(id)).distinct().toList();
		if (tips.isEmpty()) {
			return;
		}
		ReentrantLock lock = lock(normalized);
		lock.lock();
		try {
			CommitIndexer indexer = new CommitIndexer(sessionFactory, normalized);
			var boundaries = boundaries(normalized);
			CommitIndexer.ReachableIndexResult result;
			try {
				result = indexer.indexReachableCommits(repository, tips, boundaries);
			} catch (MissingObjectException | RevWalkException possiblyPrunedBoundary) {
				// RevWalk's Iterator wraps checked read failures; other walk failures must not retry.
				if (boundaries.isEmpty() || (possiblyPrunedBoundary instanceof RevWalkException
						&& !(possiblyPrunedBoundary.getCause() instanceof MissingObjectException))) {
					throw possiblyPrunedBoundary;
				}
				invalidate(normalized);
				// Git maintenance may have pruned a former boundary's ancestry. A genuine missing
				// new tip/blob still fails this single retry and remains visible through health.
				result = indexer.indexReachableCommits(repository, tips, List.of());
			}
			certify(normalized, result.completedTipIds());
		} catch (IOException | RuntimeException failure) {
			invalidate(normalized);
			failures.add(normalized);
			LOG.log(Level.SEVERE, "Published Git history requires a projection rebuild for " + normalized, failure);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public RebuildResult rebuild(String canonicalName, Repository repository) throws IOException {
		ReentrantLock lock = lock(canonicalName);
		try {
			lock.lockInterruptibly();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("History rebuild interrupted before acquiring the writer lock");
		}
		try {
			// Purging invalidates every certificate before the first destructive projection write.
			invalidate(canonicalName);
			failures.add(canonicalName);
			// The scoped reader may have opened before another handle published and indexed a new tip.
			// Refresh under the writer lock so purging cannot replace that tip with a stale ref snapshot.
			repository.scanForRepoChanges();
			var head = repository.exactRef(Constants.HEAD);
			ObjectId detached = head != null && !head.isSymbolic() ? head.getObjectId() : null;
			var result = new CommitProjectionRebuilder(sessionFactory).rebuild(repository, new RepositoryName(canonicalName));
			long indexed = result.indexedCommits();
			// The library rebuild discovers refs/*; an independent detached HEAD is also authoritative here.
			if (detached != null) {
				indexed += new CommitIndexer(sessionFactory, canonicalName)
						.indexReachableCommits(repository, List.of(detached), List.of()).indexedCommits();
			}
			// Do not infer certificates from refs read after the rebuild: a concurrent Git push may have moved them.
			failures.remove(canonicalName);
			return new RebuildResult(result.removedProjections(), indexed);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void repositoryDeleted(String canonicalName) {
		withExclusiveProjectionAccess(canonicalName, () -> {
			invalidate(canonicalName);
			failures.remove(canonicalName);
			return null;
		});
	}

	private List<ObjectId> boundaries(String name) {
		synchronized (completeTips) {
			var tips = completeTips.get(name);
			return tips == null ? List.of() : tips.stream().map(ObjectId::fromString).toList();
		}
	}

	private void invalidate(String name) {
		synchronized (completeTips) { completeTips.remove(name); }
	}

	private void certify(String name, List<String> completed) {
		if (completed.isEmpty()) return;
		synchronized (completeTips) {
			var tips = completeTips.computeIfAbsent(name, ignored -> new LinkedHashSet<>());
			for (String tip : completed) {
				tips.remove(tip); tips.add(tip);
				while (tips.size() > MAX_COMPLETE_TIPS) tips.removeFirst();
			}
			while (completeTips.size() > MAX_REPOSITORIES) completeTips.pollFirstEntry();
		}
	}

	@Override
	public <T> T withExclusiveProjectionAccess(String canonicalName, Supplier<T> operation) {
		ReentrantLock lock = lock(canonicalName);
		lock.lock();
		try {
			return operation.get();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void checkHealthy() {
		if (!failures.isEmpty()) {
			throw new IllegalStateException("History projection rebuild required for " + failures.size() + " repositories");
		}
	}

	@Override
	public void refreshIndexes(SessionFactory factory) throws InterruptedException {
		if (factory != sessionFactory) {
			throw new IllegalArgumentException("Index maintenance belongs to another persistence context");
		}
		int acquired = 0;
		try {
			for (ReentrantLock lock : locks) {
				lock.lockInterruptibly();
				acquired++;
			}
			SandboxHistoryMaintenance.super.refreshIndexes(sessionFactory);
		} finally {
			while (acquired > 0) {
				locks[--acquired].unlock();
			}
		}
	}

	private ReentrantLock lock(String name) {
		return locks[Math.floorMod(name.hashCode(), locks.length)];
	}
}
