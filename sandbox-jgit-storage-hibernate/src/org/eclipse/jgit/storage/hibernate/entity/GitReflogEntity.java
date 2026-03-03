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

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entity representing a Git reflog entry stored in the database.
 */
@Entity
@Table(name = "git_reflog", indexes = {
		@Index(name = "idx_reflog_repo", columnList = "repository_name"),
		@Index(name = "idx_reflog_repo_ref", columnList = "repository_name, ref_name") })
public class GitReflogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Version
	@Column(name = "version")
	private Long version;

	@Column(name = "repository_name", nullable = false)
	private String repositoryName;

	@Nationalized
	@Column(name = "ref_name", nullable = false)
	private String refName;

	@Column(name = "old_id", length = 40)
	private String oldId;

	@Column(name = "new_id", length = 40)
	private String newId;

	@Nationalized
	@Column(name = "who_name")
	private String whoName;

	@Nationalized
	@Column(name = "who_email")
	private String whoEmail;

	@Column(name = "who_when", nullable = false)
	private Instant when;

	@Nationalized
	@Column(name = "message", length = 2048)
	private String message;

	/** Default constructor for JPA. */
	public GitReflogEntity() {
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
	 * Get the optimistic locking version.
	 *
	 * @return the version
	 */
	public Long getVersion() {
		return version;
	}

	/**
	 * Set the optimistic locking version.
	 *
	 * @param version
	 *            the version
	 */
	public void setVersion(Long version) {
		this.version = version;
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
	 *            the reference name
	 */
	public void setRefName(String refName) {
		this.refName = refName;
	}

	/**
	 * Get the old object id.
	 *
	 * @return the oldId
	 */
	public String getOldId() {
		return oldId;
	}

	/**
	 * Set the old object id.
	 *
	 * @param oldId
	 *            the SHA-1 hex string
	 */
	public void setOldId(String oldId) {
		this.oldId = oldId;
	}

	/**
	 * Get the new object id.
	 *
	 * @return the newId
	 */
	public String getNewId() {
		return newId;
	}

	/**
	 * Set the new object id.
	 *
	 * @param newId
	 *            the SHA-1 hex string
	 */
	public void setNewId(String newId) {
		this.newId = newId;
	}

	/**
	 * Get the author name.
	 *
	 * @return the whoName
	 */
	public String getWhoName() {
		return whoName;
	}

	/**
	 * Set the author name.
	 *
	 * @param whoName
	 *            the author name
	 */
	public void setWhoName(String whoName) {
		this.whoName = whoName;
	}

	/**
	 * Get the author email.
	 *
	 * @return the whoEmail
	 */
	public String getWhoEmail() {
		return whoEmail;
	}

	/**
	 * Set the author email.
	 *
	 * @param whoEmail
	 *            the email address
	 */
	public void setWhoEmail(String whoEmail) {
		this.whoEmail = whoEmail;
	}

	/**
	 * Get the timestamp.
	 *
	 * @return the when
	 */
	public Instant getWhen() {
		return when;
	}

	/**
	 * Set the timestamp.
	 *
	 * @param when
	 *            the timestamp
	 */
	public void setWhen(Instant when) {
		this.when = when;
	}

	/**
	 * Get the reflog message.
	 *
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the reflog message.
	 *
	 * @param message
	 *            the message
	 */
	public void setMessage(String message) {
		this.message = message;
	}
}
