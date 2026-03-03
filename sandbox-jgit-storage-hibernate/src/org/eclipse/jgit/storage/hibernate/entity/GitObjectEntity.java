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

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Entity representing a Git object stored in the database.
 */
@Entity
@Table(name = "git_objects", indexes = {
		@Index(name = "idx_git_obj_sha", columnList = "object_id", unique = true),
		@Index(name = "idx_git_obj_type", columnList = "object_type") })
public class GitObjectEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "object_id", length = 40, nullable = false, unique = true)
	private String objectId;

	@Column(name = "object_type", nullable = false)
	private int objectType;

	@Column(name = "object_size", nullable = false)
	private long objectSize;

	@Lob
	@Column(name = "data", nullable = false, length = Integer.MAX_VALUE)
	@Basic(fetch = FetchType.LAZY)
	private byte[] data;

	@Column(name = "repository_name", nullable = false, length = 255)
	private String repositoryName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** Default constructor for JPA. */
	public GitObjectEntity() {
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
	 * Get the SHA-1 hex string.
	 *
	 * @return the objectId
	 */
	public String getObjectId() {
		return objectId;
	}

	/**
	 * Set the SHA-1 hex string.
	 *
	 * @param objectId
	 *            the SHA-1 hex string
	 */
	public void setObjectId(String objectId) {
		this.objectId = objectId;
	}

	/**
	 * Get the object type constant.
	 *
	 * @return the objectType
	 */
	public int getObjectType() {
		return objectType;
	}

	/**
	 * Set the object type constant.
	 *
	 * @param objectType
	 *            the type constant (e.g. OBJ_BLOB, OBJ_TREE)
	 */
	public void setObjectType(int objectType) {
		this.objectType = objectType;
	}

	/**
	 * Get the size of the object.
	 *
	 * @return the objectSize
	 */
	public long getObjectSize() {
		return objectSize;
	}

	/**
	 * Set the size of the object.
	 *
	 * @param objectSize
	 *            the object size in bytes
	 */
	public void setObjectSize(long objectSize) {
		this.objectSize = objectSize;
	}

	/**
	 * Get the raw object data.
	 *
	 * @return the data
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * Set the raw object data.
	 *
	 * @param data
	 *            the raw data
	 */
	public void setData(byte[] data) {
		this.data = data;
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
