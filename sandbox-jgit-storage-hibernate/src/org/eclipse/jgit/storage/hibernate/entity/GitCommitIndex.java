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

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity for indexing Git commit metadata. Annotated with Hibernate Search
 * {@link Indexed} so that full-text search capabilities are automatically
 * maintained when entities are persisted through Hibernate ORM.
 */
@Indexed
@Entity
@Table(name = "git_commit_index")
public class GitCommitIndex {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@KeywordField
	@Column(name = "repository_name", nullable = false)
	private String repositoryName;

	@KeywordField
	@Column(name = "object_id", length = 40, nullable = false, unique = true)
	private String objectId;

	@FullTextField(analyzer = "commitMessage")
	@Column(name = "commit_message", length = 65535)
	private String commitMessage;

	@KeywordField
	@Column(name = "author_name")
	private String authorName;

	@KeywordField
	@Column(name = "author_email")
	private String authorEmail;

	@GenericField
	@Column(name = "commit_time")
	private Instant commitTime;

	@Column(name = "parent_ids", length = 65535)
	private String parentIds;

	@FullTextField(analyzer = "javaPath")
	@Column(name = "changed_paths", length = 65535)
	private String changedPaths;

	/** Default constructor for JPA. */
	public GitCommitIndex() {
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
	 * Get the commit SHA-1.
	 *
	 * @return the objectId
	 */
	public String getObjectId() {
		return objectId;
	}

	/**
	 * Set the commit SHA-1.
	 *
	 * @param objectId
	 *            the SHA-1 hex string
	 */
	public void setObjectId(String objectId) {
		this.objectId = objectId;
	}

	/**
	 * Get the commit message.
	 *
	 * @return the commitMessage
	 */
	public String getCommitMessage() {
		return commitMessage;
	}

	/**
	 * Set the commit message.
	 *
	 * @param commitMessage
	 *            the message
	 */
	public void setCommitMessage(String commitMessage) {
		this.commitMessage = commitMessage;
	}

	/**
	 * Get the author name.
	 *
	 * @return the authorName
	 */
	public String getAuthorName() {
		return authorName;
	}

	/**
	 * Set the author name.
	 *
	 * @param authorName
	 *            the name
	 */
	public void setAuthorName(String authorName) {
		this.authorName = authorName;
	}

	/**
	 * Get the author email.
	 *
	 * @return the authorEmail
	 */
	public String getAuthorEmail() {
		return authorEmail;
	}

	/**
	 * Set the author email.
	 *
	 * @param authorEmail
	 *            the email
	 */
	public void setAuthorEmail(String authorEmail) {
		this.authorEmail = authorEmail;
	}

	/**
	 * Get the commit time.
	 *
	 * @return the commitTime
	 */
	public Instant getCommitTime() {
		return commitTime;
	}

	/**
	 * Set the commit time.
	 *
	 * @param commitTime
	 *            the timestamp
	 */
	public void setCommitTime(Instant commitTime) {
		this.commitTime = commitTime;
	}

	/**
	 * Get the comma-separated parent IDs.
	 *
	 * @return the parentIds
	 */
	public String getParentIds() {
		return parentIds;
	}

	/**
	 * Set the comma-separated parent IDs.
	 *
	 * @param parentIds
	 *            comma-separated SHA-1 strings
	 */
	public void setParentIds(String parentIds) {
		this.parentIds = parentIds;
	}

	/**
	 * Get the newline-separated changed paths.
	 *
	 * @return the changedPaths
	 */
	public String getChangedPaths() {
		return changedPaths;
	}

	/**
	 * Set the newline-separated changed paths.
	 *
	 * @param changedPaths
	 *            newline-separated paths
	 */
	public void setChangedPaths(String changedPaths) {
		this.changedPaths = changedPaths;
	}
}
