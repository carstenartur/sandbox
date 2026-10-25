/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.history;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.hibernate.SessionFactory;

import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitIndexer;

/** Uses released indexing services, with bounded application-owned writer locks. */
public final class ReleasedSandboxHistoryMaintenance implements SandboxHistoryMaintenance {
	private static final Logger LOG = Logger.getLogger(ReleasedSandboxHistoryMaintenance.class.getName());

	private final SessionFactory sessionFactory;
	private final ReentrantLock[] locks = IntStream.range(0, 32).mapToObj(i -> new ReentrantLock())
			.toArray(ReentrantLock[]::new);
	private final Set<String> failures = ConcurrentHashMap.newKeySet();

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
		try (RevWalk walk = new RevWalk(repository)) {
			CommitIndexer indexer = new CommitIndexer(sessionFactory, normalized);
			for (ObjectId tip : tips) {
				// Annotated tags may refer to commits, trees or blobs.
				var target = walk.peel(walk.parseAny(tip));
				if (target instanceof RevCommit commit) {
					indexer.indexCommitsFrom(repository, commit, -1);
				}
			}
		} catch (IOException | RuntimeException failure) {
			failures.add(normalized);
			LOG.log(Level.SEVERE, "Published Git history requires a projection rebuild for " + normalized, failure);
		} finally {
			lock.unlock();
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
