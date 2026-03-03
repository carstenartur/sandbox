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
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Entity tracking file paths across commit history.
 * <p>
 * Stores a record for every file present in each indexed commit's tree,
 * enabling cross-history file path searches and file history tracking.
 * </p>
 */
@Indexed
@Entity
@Table(name = "file_path_history", indexes = {
		@Index(name = "idx_fph_repo_path", columnList = "repository_name, file_path"),
		@Index(name = "idx_fph_repo_commit", columnList = "repository_name, commit_object_id"),
		@Index(name = "idx_fph_repo_blob", columnList = "repository_name, blob_object_id") })
public class FilePathHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@KeywordField
	@Column(name = "repository_name", nullable = false)
	private String repositoryName;

	@KeywordField
	@Column(name = "commit_object_id", length = 40, nullable = false)
	private String commitObjectId;

	@FullTextField(analyzer = "javaPath")
	@Nationalized
	@Column(name = "file_path", length = 500, nullable = false)
	private String filePath;

	@KeywordField
	@Column(name = "blob_object_id", length = 40, nullable = false)
	private String blobObjectId;

	@KeywordField
	@Column(name = "file_type", length = 32)
	private String fileType;

	@GenericField
	@Column(name = "commit_time")
	private Instant commitTime;

	/** Default constructor for JPA. */
	public FilePathHistory() {
	}

	// --- Getters and setters ---

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
	 * @return the repository name
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
	 * @return the commit object ID
	 */
	public String getCommitObjectId() {
		return commitObjectId;
	}

	/**
	 * Set the commit SHA-1.
	 *
	 * @param commitObjectId
	 *            the SHA-1 hex string
	 */
	public void setCommitObjectId(String commitObjectId) {
		this.commitObjectId = commitObjectId;
	}

	/**
	 * Get the file path.
	 *
	 * @return the file path
	 */
	public String getFilePath() {
		return filePath;
	}

	/**
	 * Set the file path.
	 *
	 * @param filePath
	 *            the relative file path
	 */
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	/**
	 * Get the blob SHA-1.
	 *
	 * @return the blob object ID
	 */
	public String getBlobObjectId() {
		return blobObjectId;
	}

	/**
	 * Set the blob SHA-1.
	 *
	 * @param blobObjectId
	 *            the SHA-1 hex string
	 */
	public void setBlobObjectId(String blobObjectId) {
		this.blobObjectId = blobObjectId;
	}

	/**
	 * Get the file type.
	 *
	 * @return the file type identifier
	 */
	public String getFileType() {
		return fileType;
	}

	/**
	 * Set the file type.
	 *
	 * @param fileType
	 *            the file type
	 */
	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	/**
	 * Get the commit time.
	 *
	 * @return the commit time
	 */
	public Instant getCommitTime() {
		return commitTime;
	}

	/**
	 * Set the commit time.
	 *
	 * @param commitTime
	 *            the commit timestamp
	 */
	public void setCommitTime(Instant commitTime) {
		this.commitTime = commitTime;
	}
}
