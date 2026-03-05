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

import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;

/**
 * Represents a single API change between two commits.
 * <p>
 * Records the before and after states of a {@link JavaBlobIndex} entry along
 * with a human-readable description of what changed.
 * </p>
 */
public class ApiChangeEntry {

	private JavaBlobIndex before;

	private JavaBlobIndex after;

	private String changeDescription;

	/** Default constructor. */
	public ApiChangeEntry() {
	}

	/**
	 * Create an API change entry.
	 *
	 * @param before
	 *            the entry state before the change
	 * @param after
	 *            the entry state after the change
	 * @param changeDescription
	 *            human-readable description of what changed (e.g. "methods
	 *            changed", "visibility changed")
	 */
	public ApiChangeEntry(JavaBlobIndex before, JavaBlobIndex after,
			String changeDescription) {
		this.before = before;
		this.after = after;
		this.changeDescription = changeDescription;
	}

	/**
	 * Get the entry state before the change.
	 *
	 * @return the before entry
	 */
	public JavaBlobIndex getBefore() {
		return before;
	}

	/**
	 * Set the entry state before the change.
	 *
	 * @param before
	 *            the before entry
	 */
	public void setBefore(JavaBlobIndex before) {
		this.before = before;
	}

	/**
	 * Get the entry state after the change.
	 *
	 * @return the after entry
	 */
	public JavaBlobIndex getAfter() {
		return after;
	}

	/**
	 * Set the entry state after the change.
	 *
	 * @param after
	 *            the after entry
	 */
	public void setAfter(JavaBlobIndex after) {
		this.after = after;
	}

	/**
	 * Get the human-readable description of what changed.
	 *
	 * @return the change description
	 */
	public String getChangeDescription() {
		return changeDescription;
	}

	/**
	 * Set the human-readable description of what changed.
	 *
	 * @param changeDescription
	 *            the change description
	 */
	public void setChangeDescription(String changeDescription) {
		this.changeDescription = changeDescription;
	}
}
