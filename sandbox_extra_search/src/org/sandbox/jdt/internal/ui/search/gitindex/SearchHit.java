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
 * Lightweight DTO representing a single search result returned by the
 * REST search backend ({@code sandbox-jgit-server-webapp}).
 *
 * <p>No dependency on Hibernate, DJL, or Lucene.</p>
 */
public class SearchHit {

	private String path;
	private String className;
	private String methodName;
	private double score;
	private String repoName;
	private String commitHash;
	private String content;

	/** Creates an empty SearchHit. */
	public SearchHit() {
		// default constructor
	}

	/**
	 * Creates a SearchHit with all fields.
	 *
	 * @param path       file path
	 * @param className  declared class name(s)
	 * @param methodName declared method name(s)
	 * @param score      relevance score
	 * @param repoName   repository name
	 * @param commitHash commit object ID
	 * @param content    raw source content snippet
	 */
	public SearchHit(String path, String className, String methodName,
			double score, String repoName, String commitHash, String content) {
		this.path= path;
		this.className= className;
		this.methodName= methodName;
		this.score= score;
		this.repoName= repoName;
		this.commitHash= commitHash;
		this.content= content;
	}

	/** @return the file path */
	public String getPath() {
		return path;
	}

	/** @param path the file path */
	public void setPath(String path) {
		this.path= path;
	}

	/** @return the class name */
	public String getClassName() {
		return className;
	}

	/** @param className the class name */
	public void setClassName(String className) {
		this.className= className;
	}

	/** @return the method name */
	public String getMethodName() {
		return methodName;
	}

	/** @param methodName the method name */
	public void setMethodName(String methodName) {
		this.methodName= methodName;
	}

	/** @return the relevance score */
	public double getScore() {
		return score;
	}

	/** @param score the relevance score */
	public void setScore(double score) {
		this.score= score;
	}

	/** @return the repository name */
	public String getRepoName() {
		return repoName;
	}

	/** @param repoName the repository name */
	public void setRepoName(String repoName) {
		this.repoName= repoName;
	}

	/** @return the commit hash */
	public String getCommitHash() {
		return commitHash;
	}

	/** @param commitHash the commit hash */
	public void setCommitHash(String commitHash) {
		this.commitHash= commitHash;
	}

	/** @return the content snippet */
	public String getContent() {
		return content;
	}

	/** @param content the content snippet */
	public void setContent(String content) {
		this.content= content;
	}

	@Override
	public String toString() {
		return path + " [" + className + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
