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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entity representing a Git reference stored in the database.
 */
@Entity
@Table(name = "git_refs", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "repository_name", "ref_name" }) })
public class GitRefEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "repository_name", nullable = false)
	private String repositoryName;

	@Column(name = "ref_name", nullable = false, length = 512)
	private String refName;

	@Column(name = "object_id", length = 40)
	private String objectId;

	@Column(name = "symbolic_target", length = 512)
	private String symbolicTarget;

	@Column(name = "is_peeled")
	private boolean peeled;

	@Column(name = "peeled_object_id", length = 40)
	private String peeledObjectId;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Default constructor for JPA. */
	public GitRefEntity() {
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
	 * Get the reference name.
	 *
	 * @return the refName
	 */
	public String getRefName() {
		return refName;
	}

	/**
	 * Set the reference name.
	 *
	 * @param refName
	 *            the reference name (e.g. "refs/heads/main")
	 */
	public void setRefName(String refName) {
		this.refName = refName;
	}

	/**
	 * Get the target object SHA-1.
	 *
	 * @return the objectId
	 */
	public String getObjectId() {
		return objectId;
	}

	/**
	 * Set the target object SHA-1.
	 *
	 * @param objectId
	 *            the SHA-1 hex string
	 */
	public void setObjectId(String objectId) {
		this.objectId = objectId;
	}

	/**
	 * Get the symbolic target ref name.
	 *
	 * @return the symbolicTarget
	 */
	public String getSymbolicTarget() {
		return symbolicTarget;
	}

	/**
	 * Set the symbolic target ref name.
	 *
	 * @param symbolicTarget
	 *            the target ref name (null if not symbolic)
	 */
	public void setSymbolicTarget(String symbolicTarget) {
		this.symbolicTarget = symbolicTarget;
	}

	/**
	 * Whether this ref is peeled.
	 *
	 * @return true if peeled
	 */
	public boolean isPeeled() {
		return peeled;
	}

	/**
	 * Set whether this ref is peeled.
	 *
	 * @param peeled
	 *            true if peeled
	 */
	public void setPeeled(boolean peeled) {
		this.peeled = peeled;
	}

	/**
	 * Get the peeled object SHA-1.
	 *
	 * @return the peeledObjectId
	 */
	public String getPeeledObjectId() {
		return peeledObjectId;
	}

	/**
	 * Set the peeled object SHA-1.
	 *
	 * @param peeledObjectId
	 *            the SHA-1 hex string
	 */
	public void setPeeledObjectId(String peeledObjectId) {
		this.peeledObjectId = peeledObjectId;
	}

	/**
	 * Get the last updated timestamp.
	 *
	 * @return the updatedAt
	 */
	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Set the last updated timestamp.
	 *
	 * @param updatedAt
	 *            the timestamp
	 */
	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
