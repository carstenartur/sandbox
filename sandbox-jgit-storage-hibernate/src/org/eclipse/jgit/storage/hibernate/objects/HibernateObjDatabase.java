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
package org.eclipse.jgit.storage.hibernate.objects;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase;
import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream;
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription;
import org.eclipse.jgit.internal.storage.dfs.DfsReaderOptions;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.hibernate.entity.GitPackEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * A {@link DfsObjDatabase} implementation backed by Hibernate/JPA for
 * database-based Git object storage.
 * <p>
 * Pack data is stored in the {@code git_packs} table as BLOBs. Each pack
 * extension (PACK, IDX, REFTABLE, etc.) is stored as a separate row, keyed
 * by the pack base name and the extension name.
 */
public class HibernateObjDatabase extends DfsObjDatabase {

	private final SessionFactory sessionFactory;

	private final String repositoryName;

	private static final AtomicInteger PACK_ID_COUNTER = new AtomicInteger();

	private Set<ObjectId> shallowCommits = Collections.emptySet();

	/**
	 * Create a new Hibernate-backed object database.
	 *
	 * @param repo
	 *            the repository
	 * @param options
	 *            reader options
	 * @param sessionFactory
	 *            Hibernate session factory
	 * @param repositoryName
	 *            name of the repository in the database
	 */
	public HibernateObjDatabase(DfsRepository repo, DfsReaderOptions options,
			SessionFactory sessionFactory, String repositoryName) {
		super(repo, options);
		this.sessionFactory = sessionFactory;
		this.repositoryName = repositoryName;
	}

	/**
	 * Extract the base pack name from a DfsPackDescription.
	 * <p>
	 * DfsPackDescription stores the base name internally and
	 * {@code getFileName(ext)} appends the extension. We recover the
	 * base name by stripping the extension from any filename.
	 *
	 * @param desc
	 *            the pack description
	 * @return the base pack name without extension
	 */
	private static String baseName(DfsPackDescription desc) {
		String fn = desc.getFileName(PackExt.PACK);
		int dot = fn.lastIndexOf('.');
		return dot > 0 ? fn.substring(0, dot) : fn;
	}

	@Override
	protected List<DfsPackDescription> listPacks() throws IOException {
		try (Session session = sessionFactory.openSession()) {
			List<Object[]> rows = session.createQuery(
					"SELECT p.packName, p.packExtension FROM GitPackEntity p WHERE p.repositoryName = :repo", //$NON-NLS-1$
					Object[].class)
					.setParameter("repo", repositoryName) //$NON-NLS-1$
					.getResultList();
			LinkedHashMap<String, DfsPackDescription> descMap = new LinkedHashMap<>();
			for (Object[] row : rows) {
				String name = (String) row[0];
				String ext = (String) row[1];
				DfsPackDescription desc = descMap.computeIfAbsent(name,
						n -> new DfsPackDescription(
								getRepository().getDescription(), n,
								PackSource.INSERT));
				for (PackExt pe : PackExt.values()) {
					if (pe.getExtension().equals(ext)) {
						desc.addFileExt(pe);
						break;
					}
				}
			}
			return new ArrayList<>(descMap.values());
		}
	}

	@Override
	protected DfsPackDescription newPack(PackSource source) {
		int id = PACK_ID_COUNTER.incrementAndGet();
		String name = "pack-" + id + "-" + source.name(); //$NON-NLS-1$ //$NON-NLS-2$
		return new DfsPackDescription(getRepository().getDescription(), name,
				source);
	}

	@Override
	protected void commitPackImpl(Collection<DfsPackDescription> desc,
			Collection<DfsPackDescription> replace) throws IOException {
		try (Session session = sessionFactory.openSession()) {
			session.beginTransaction();
			if (replace != null) {
				for (DfsPackDescription d : replace) {
					session.createMutationQuery(
							"DELETE FROM GitPackEntity p WHERE p.repositoryName = :repo AND p.packName = :name") //$NON-NLS-1$
							.setParameter("repo", repositoryName) //$NON-NLS-1$
							.setParameter("name", baseName(d)) //$NON-NLS-1$
							.executeUpdate();
				}
			}
			session.getTransaction().commit();
		}
		clearCache();
	}

	@Override
	protected void rollbackPack(Collection<DfsPackDescription> desc) {
		try (Session session = sessionFactory.openSession()) {
			session.beginTransaction();
			for (DfsPackDescription d : desc) {
				session.createMutationQuery(
						"DELETE FROM GitPackEntity p WHERE p.repositoryName = :repo AND p.packName = :name") //$NON-NLS-1$
						.setParameter("repo", repositoryName) //$NON-NLS-1$
						.setParameter("name", baseName(d)) //$NON-NLS-1$
						.executeUpdate();
			}
			session.getTransaction().commit();
		} catch (Exception e) {
			// Rollback should not throw
		}
	}

	@Override
	protected ReadableChannel openFile(DfsPackDescription desc, PackExt ext)
			throws FileNotFoundException, IOException {
		try (Session session = sessionFactory.openSession()) {
			GitPackEntity entity = session.createQuery(
					"FROM GitPackEntity p WHERE p.repositoryName = :repo AND p.packName = :name AND p.packExtension = :ext", //$NON-NLS-1$
					GitPackEntity.class)
					.setParameter("repo", repositoryName) //$NON-NLS-1$
					.setParameter("name", baseName(desc)) //$NON-NLS-1$
					.setParameter("ext", ext.getExtension()).uniqueResult(); //$NON-NLS-1$
			if (entity == null) {
				throw new FileNotFoundException(desc.getFileName(ext));
			}
			return new ByteArrayReadableChannel(entity.getData());
		}
	}

	@Override
	protected DfsOutputStream writeFile(DfsPackDescription desc, PackExt ext)
			throws IOException {
		return new HibernatePackOutputStream(sessionFactory, repositoryName,
				baseName(desc), ext.getExtension());
	}

	@Override
	public Set<ObjectId> getShallowCommits() throws IOException {
		return shallowCommits;
	}

	@Override
	public void setShallowCommits(Set<ObjectId> shallowCommits) {
		this.shallowCommits = shallowCommits;
	}

	@Override
	public long getApproximateObjectCount() {
		try (Session session = sessionFactory.openSession()) {
			Long count = session.createQuery(
					"SELECT COUNT(o) FROM GitObjectEntity o WHERE o.repositoryName = :repo", //$NON-NLS-1$
					Long.class).setParameter("repo", repositoryName) //$NON-NLS-1$
					.uniqueResult();
			return count != null ? count : 0;
		}
	}

	/**
	 * DfsOutputStream that writes pack data to the database.
	 */
	static class HibernatePackOutputStream extends DfsOutputStream {
		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		private final SessionFactory sessionFactory;

		private final String repositoryName;

		private final String packName;

		private final String packExtension;

		private byte[] data;

		private boolean flushed;

		HibernatePackOutputStream(SessionFactory sessionFactory,
				String repositoryName, String packName,
				String packExtension) {
			this.sessionFactory = sessionFactory;
			this.repositoryName = repositoryName;
			this.packName = packName;
			this.packExtension = packExtension;
		}

		@Override
		public void write(byte[] buf, int off, int len) {
			data = null;
			buffer.write(buf, off, len);
		}

		@Override
		public int read(long position, ByteBuffer buf) {
			byte[] d = getData();
			int n = Math.min(buf.remaining(), d.length - (int) position);
			if (n == 0) {
				return -1;
			}
			buf.put(d, (int) position, n);
			return n;
		}

		byte[] getData() {
			if (data == null) {
				data = buffer.toByteArray();
			}
			return data;
		}

		@Override
		public void flush() {
			if (flushed) {
				return;
			}
			flushed = true;
			byte[] d = getData();
			try (Session session = sessionFactory.openSession()) {
				session.beginTransaction();
				GitPackEntity entity = new GitPackEntity();
				entity.setRepositoryName(repositoryName);
				entity.setPackName(packName);
				entity.setPackExtension(packExtension);
				entity.setData(d);
				entity.setFileSize(d.length);
				entity.setCreatedAt(Instant.now());
				session.persist(entity);
				session.getTransaction().commit();
			}
		}

		@Override
		public void close() {
			flush();
		}
	}

	/**
	 * ReadableChannel backed by a byte array.
	 */
	static class ByteArrayReadableChannel implements ReadableChannel {
		private final byte[] data;

		private int position;

		private boolean open = true;

		ByteArrayReadableChannel(byte[] buf) {
			data = buf;
		}

		@Override
		public int read(ByteBuffer dst) {
			int n = Math.min(dst.remaining(), data.length - position);
			if (n == 0) {
				return -1;
			}
			dst.put(data, position, n);
			position += n;
			return n;
		}

		@Override
		public void close() {
			open = false;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public long position() {
			return position;
		}

		@Override
		public void position(long newPosition) {
			position = (int) newPosition;
		}

		@Override
		public long size() {
			return data.length;
		}

		@Override
		public int blockSize() {
			return 0;
		}

		@Override
		public void setReadAheadBytes(int b) {
			// Unnecessary on a byte array.
		}
	}
}
