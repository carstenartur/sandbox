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
package org.eclipse.jgit.storage.hibernate.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Entity representing a Git pack file stored in the database.
 */
@Entity
@Table(name = "git_packs", indexes = {
		@Index(name = "idx_pack_repo", columnList = "repository_name"),
		@Index(name = "idx_pack_repo_name", columnList = "repository_name, pack_name") })
public class GitPackEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "repository_name", nullable = false)
	private String repositoryName;

	@Column(name = "pack_name", nullable = false)
	private String packName;

	@Column(name = "pack_extension", nullable = false)
	private String packExtension;

	@JdbcTypeCode(SqlTypes.LONG32VARBINARY)
	@Basic(fetch = FetchType.LAZY)
	@Column(name = "data", nullable = false)
	private byte[] data;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** Default constructor for JPA. */
	public GitPackEntity() {
	}

	/**
	 * Get the primary key.
	 *
	 * @return the id
	 */
	public Long getId() {
		return id;
	}

	/**
	 * Set the primary key.
	 *
	 * @param id
	 *            the id
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * Get the repository name.
	 *
	 * @return the repositoryName
	 */
	public String getRepositoryName() {
		return repositoryName;
	}

	/**
	 * Set the repository name.
	 *
	 * @param repositoryName
	 *            the repository name
	 */
	public void setRepositoryName(String repositoryName) {
		this.repositoryName = repositoryName;
	}

	/**
	 * Get the pack name.
	 *
	 * @return the packName
	 */
	public String getPackName() {
		return packName;
	}

	/**
	 * Set the pack name.
	 *
	 * @param packName
	 *            the pack name
	 */
	public void setPackName(String packName) {
		this.packName = packName;
	}

	/**
	 * Get the pack extension.
	 *
	 * @return the packExtension
	 */
	public String getPackExtension() {
		return packExtension;
	}

	/**
	 * Set the pack extension.
	 *
	 * @param packExtension
	 *            the extension (e.g. PACK, IDX)
	 */
	public void setPackExtension(String packExtension) {
		this.packExtension = packExtension;
	}

	/**
	 * Get the pack data.
	 *
	 * @return the data
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * Set the pack data.
	 *
	 * @param data
	 *            the raw pack data
	 */
	public void setData(byte[] data) {
		this.data = data;
	}

	/**
	 * Get the file size.
	 *
	 * @return the fileSize
	 */
	public long getFileSize() {
		return fileSize;
	}

	/**
	 * Set the file size.
	 *
	 * @param fileSize
	 *            the file size in bytes
	 */
	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	/**
	 * Get the creation timestamp.
	 *
	 * @return the createdAt
	 */
	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Set the creation timestamp.
	 *
	 * @param createdAt
	 *            the timestamp
	 */
	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
