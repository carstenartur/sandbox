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
package org.eclipse.jgit.storage.hibernate.service;

import java.util.List;

import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;

/**
 * Result of an API diff comparison between two commits.
 * <p>
 * Aggregates files that were added, removed, or changed (in terms of method
 * signatures or visibility) between two Git commit object IDs.
 * </p>
 */
public class ApiDiffResult {

	private List<JavaBlobIndex> addedFiles;

	private List<JavaBlobIndex> removedFiles;

	private List<ApiChangeEntry> changedFiles;

	/** Default constructor. */
	public ApiDiffResult() {
	}

	/**
	 * Create an API diff result.
	 *
	 * @param addedFiles
	 *            files present in commit B but not A
	 * @param removedFiles
	 *            files present in commit A but not B
	 * @param changedFiles
	 *            files present in both commits but with API changes
	 */
	public ApiDiffResult(List<JavaBlobIndex> addedFiles,
			List<JavaBlobIndex> removedFiles,
			List<ApiChangeEntry> changedFiles) {
		this.addedFiles = addedFiles;
		this.removedFiles = removedFiles;
		this.changedFiles = changedFiles;
	}

	/**
	 * Get files added in commit B (not present in commit A).
	 *
	 * @return the added files
	 */
	public List<JavaBlobIndex> getAddedFiles() {
		return addedFiles;
	}

	/**
	 * Set files added in commit B.
	 *
	 * @param addedFiles
	 *            the added files
	 */
	public void setAddedFiles(List<JavaBlobIndex> addedFiles) {
		this.addedFiles = addedFiles;
	}

	/**
	 * Get files removed in commit B (present in commit A but not B).
	 *
	 * @return the removed files
	 */
	public List<JavaBlobIndex> getRemovedFiles() {
		return removedFiles;
	}

	/**
	 * Set files removed in commit B.
	 *
	 * @param removedFiles
	 *            the removed files
	 */
	public void setRemovedFiles(List<JavaBlobIndex> removedFiles) {
		this.removedFiles = removedFiles;
	}

	/**
	 * Get files that exist in both commits but have API changes.
	 *
	 * @return the changed files
	 */
	public List<ApiChangeEntry> getChangedFiles() {
		return changedFiles;
	}

	/**
	 * Set files that exist in both commits but have API changes.
	 *
	 * @param changedFiles
	 *            the changed files
	 */
	public void setChangedFiles(List<ApiChangeEntry> changedFiles) {
		this.changedFiles = changedFiles;
	}
}
