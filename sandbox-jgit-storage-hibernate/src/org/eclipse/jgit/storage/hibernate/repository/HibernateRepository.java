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
package org.eclipse.jgit.storage.hibernate.repository;

import java.io.IOException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.storage.hibernate.objects.HibernateObjDatabase;
import org.eclipse.jgit.storage.hibernate.refs.HibernateRefDatabase;
import org.eclipse.jgit.storage.hibernate.refs.HibernateReflogReader;
import org.eclipse.jgit.storage.hibernate.refs.HibernateReflogWriter;
import org.hibernate.SessionFactory;

/**
 * A Git repository stored in a relational database via Hibernate.
 * <p>
 * This implementation extends the DFS (Distributed File System) repository
 * abstraction, replacing in-memory or filesystem storage with database-backed
 * storage using Hibernate ORM.
 * <p>
 * Objects, refs, and pack data are stored in database tables. The reftable
 * format is used for reference storage, persisted as pack extensions in the
 * database.
 */
public class HibernateRepository extends DfsRepository {

	private final HibernateObjDatabase objdb;

	private final HibernateRefDatabase refdb;

	private final HibernateReflogWriter reflogWriter;

	private final SessionFactory sessionFactory;

	private final String repositoryName;

	private String gitwebDescription;

	/**
	 * Create a new database-backed repository.
	 *
	 * @param builder
	 *            the repository builder with configuration
	 */
	public HibernateRepository(HibernateRepositoryBuilder builder) {
		super(builder);
		this.sessionFactory = builder.getSessionFactory();
		this.repositoryName = builder.getRepositoryName();
		if (this.repositoryName == null) {
			DfsRepositoryDescription desc = builder.getRepositoryDescription();
			throw new IllegalArgumentException(
					"Repository name is required; description=" //$NON-NLS-1$
							+ (desc != null ? desc.getRepositoryName()
									: "null")); //$NON-NLS-1$
		}
		this.objdb = new HibernateObjDatabase(this, new DfsReaderOptions(),
				sessionFactory, repositoryName);
		this.refdb = new HibernateRefDatabase(this);
		this.reflogWriter = new HibernateReflogWriter(sessionFactory,
				repositoryName);
	}

	@Override
	public HibernateObjDatabase getObjectDatabase() {
		return objdb;
	}

	@Override
	public RefDatabase getRefDatabase() {
		return refdb;
	}

	/**
	 * Get the repository name used for database partitioning.
	 *
	 * @return the repository name
	 */
	public String getRepositoryName() {
		return repositoryName;
	}

	/**
	 * Get the Hibernate session factory.
	 *
	 * @return the session factory
	 */
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	/**
	 * Get the reflog writer for persisting reflog entries to the database.
	 *
	 * @return the reflog writer
	 */
	public HibernateReflogWriter getReflogWriter() {
		return reflogWriter;
	}

	@Override
	public ReflogReader getReflogReader(String refName) throws IOException {
		return new HibernateReflogReader(sessionFactory, repositoryName,
				refName);
	}

	@Override
	@Nullable
	public String getGitwebDescription() {
		return gitwebDescription;
	}

	@Override
	public void setGitwebDescription(@Nullable String d) {
		gitwebDescription = d;
	}
}
