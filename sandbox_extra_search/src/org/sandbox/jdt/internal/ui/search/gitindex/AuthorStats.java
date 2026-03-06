/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.ui.search.gitindex;

/**
 * Lightweight DTO representing per-author commit statistics returned by the
 * REST analytics backend ({@code sandbox-jgit-server-webapp}).
 *
 * <p>No dependency on Hibernate, DJL, or Lucene.</p>
 */
public class AuthorStats {

	private String author;
	private String authorEmail;
	private int commitCount;
	private int filesChanged;

	/** Creates an empty AuthorStats. */
	public AuthorStats() {
		// default constructor
	}

	/**
	 * Creates an AuthorStats with all fields.
	 *
	 * @param author       the author name
	 * @param authorEmail  the author email
	 * @param commitCount  number of commits
	 * @param filesChanged number of files changed
	 */
	public AuthorStats(String author, String authorEmail, int commitCount,
			int filesChanged) {
		this.author= author;
		this.authorEmail= authorEmail;
		this.commitCount= commitCount;
		this.filesChanged= filesChanged;
	}

	/** @return the author name */
	public String getAuthor() {
		return author;
	}

	/** @param author the author name */
	public void setAuthor(String author) {
		this.author= author;
	}

	/** @return the author email */
	public String getAuthorEmail() {
		return authorEmail;
	}

	/** @param authorEmail the author email */
	public void setAuthorEmail(String authorEmail) {
		this.authorEmail= authorEmail;
	}

	/** @return the commit count */
	public int getCommitCount() {
		return commitCount;
	}

	/** @param commitCount the commit count */
	public void setCommitCount(int commitCount) {
		this.commitCount= commitCount;
	}

	/** @return the number of files changed */
	public int getFilesChanged() {
		return filesChanged;
	}

	/** @param filesChanged the number of files changed */
	public void setFilesChanged(int filesChanged) {
		this.filesChanged= filesChanged;
	}

	@Override
	public String toString() {
		return author + " (" + commitCount + " commits)"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
