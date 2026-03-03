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

import java.time.Instant;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.storage.hibernate.entity.GitReflogEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Writes reflog entries to the {@code git_reflog} database table.
 * <p>
 * This provides a persistent, queryable reflog stored in the relational
 * database, complementing the reftable-based reflog that the DFS layer
 * maintains.
 */
public class HibernateReflogWriter {

	private final SessionFactory sessionFactory;

	private final String repositoryName;

	/**
	 * Create a new reflog writer.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 * @param repositoryName
	 *            the repository name for partitioning
	 */
	public HibernateReflogWriter(SessionFactory sessionFactory,
			String repositoryName) {
		this.sessionFactory = sessionFactory;
		this.repositoryName = repositoryName;
	}

	/**
	 * Write a reflog entry.
	 *
	 * @param refName
	 *            the reference name (e.g. "refs/heads/main")
	 * @param oldId
	 *            the old object ID
	 * @param newId
	 *            the new object ID
	 * @param who
	 *            the person making the change
	 * @param message
	 *            the reflog message
	 */
	public void log(String refName, ObjectId oldId, ObjectId newId,
			PersonIdent who, String message) {
		GitReflogEntity entry = new GitReflogEntity();
		entry.setRepositoryName(repositoryName);
		entry.setRefName(refName);
		entry.setOldId(oldId != null ? oldId.name() : ObjectId.zeroId().name());
		entry.setNewId(newId != null ? newId.name() : ObjectId.zeroId().name());
		if (who != null) {
			entry.setWhoName(who.getName());
			entry.setWhoEmail(who.getEmailAddress());
			entry.setWhen(who.getWhenAsInstant());
		} else {
			entry.setWhen(Instant.now());
		}
		entry.setMessage(message);

		try (Session session = sessionFactory.openSession()) {
			session.beginTransaction();
			session.persist(entry);
			session.getTransaction().commit();
		}
	}
}
