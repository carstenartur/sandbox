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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.CheckoutEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.storage.hibernate.entity.GitReflogEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Reads reflog entries from the {@code git_reflog} database table.
 * <p>
 * Returns entries in reverse chronological order (newest first),
 * consistent with the standard reflog reader contract.
 */
public class HibernateReflogReader implements ReflogReader {

	private final SessionFactory sessionFactory;

	private final String repositoryName;

	private final String refName;

	/**
	 * Create a new reflog reader.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 * @param repositoryName
	 *            the repository name
	 * @param refName
	 *            the reference name to read reflog for
	 */
	public HibernateReflogReader(SessionFactory sessionFactory,
			String repositoryName, String refName) {
		this.sessionFactory = sessionFactory;
		this.repositoryName = repositoryName;
		this.refName = refName;
	}

	@Override
	public ReflogEntry getLastEntry() throws IOException {
		List<ReflogEntry> entries = getReverseEntries(1);
		return entries.isEmpty() ? null : entries.get(0);
	}

	@Override
	public List<ReflogEntry> getReverseEntries() throws IOException {
		return getReverseEntries(Integer.MAX_VALUE);
	}

	@Override
	public ReflogEntry getReverseEntry(int number) throws IOException {
		List<ReflogEntry> entries = getReverseEntries(number + 1);
		return number < entries.size() ? entries.get(number) : null;
	}

	@Override
	public List<ReflogEntry> getReverseEntries(int max) throws IOException {
		try (Session session = sessionFactory.openSession()) {
			List<GitReflogEntity> entities = session.createQuery(
					"FROM GitReflogEntity r WHERE r.repositoryName = :repo AND r.refName = :ref ORDER BY r.id DESC", //$NON-NLS-1$
					GitReflogEntity.class)
					.setParameter("repo", repositoryName) //$NON-NLS-1$
					.setParameter("ref", refName) //$NON-NLS-1$
					.setMaxResults(max).getResultList();
			List<ReflogEntry> result = new ArrayList<>(entities.size());
			for (GitReflogEntity e : entities) {
				result.add(new DbReflogEntry(e));
			}
			return Collections.unmodifiableList(result);
		}
	}

	/**
	 * A {@link ReflogEntry} backed by a database entity.
	 */
	static class DbReflogEntry implements ReflogEntry {
		private final ObjectId oldId;

		private final ObjectId newId;

		private final PersonIdent who;

		private final String comment;

		DbReflogEntry(GitReflogEntity entity) {
			this.oldId = entity.getOldId() != null
					? ObjectId.fromString(entity.getOldId())
					: ObjectId.zeroId();
			this.newId = entity.getNewId() != null
					? ObjectId.fromString(entity.getNewId())
					: ObjectId.zeroId();
			this.who = new PersonIdent(
					entity.getWhoName() != null ? entity.getWhoName() : "", //$NON-NLS-1$
					entity.getWhoEmail() != null ? entity.getWhoEmail() : "", //$NON-NLS-1$
					entity.getWhen(),
					java.time.ZoneOffset.UTC); //$NON-NLS-1$
			this.comment = entity.getMessage() != null ? entity.getMessage()
					: ""; //$NON-NLS-1$
		}

		@Override
		public ObjectId getOldId() {
			return oldId;
		}

		@Override
		public ObjectId getNewId() {
			return newId;
		}

		@Override
		public PersonIdent getWho() {
			return who;
		}

		@Override
		public String getComment() {
			return comment;
		}

		@Override
		public CheckoutEntry parseCheckout() {
			return null;
		}
	}
}
