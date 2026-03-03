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
package org.eclipse.jgit.storage.hibernate.search;

import java.util.Set;

/**
 * Strategy interface for extracting searchable metadata from different file
 * types.
 * <p>
 * Implementations handle specific file types (e.g., Java, XML, properties)
 * and populate a {@link BlobIndexData} with the structural metadata relevant
 * to that file type.
 * </p>
 */
public interface FileTypeStrategy {

	/**
	 * Get the file extensions this strategy handles.
	 *
	 * @return a set of file extensions including the dot (e.g., ".java",
	 *         ".xml")
	 */
	Set<String> supportedExtensions();

	/**
	 * Get the exact filenames this strategy handles.
	 *
	 * @return a set of exact filenames (e.g., "pom.xml", "MANIFEST.MF"),
	 *         or an empty set if only extension-based matching is used
	 */
	Set<String> supportedFilenames();

	/**
	 * Extract searchable metadata from file content.
	 *
	 * @param source
	 *            the file content as a string
	 * @param filePath
	 *            the file path within the repository
	 * @return a populated {@link BlobIndexData}
	 */
	BlobIndexData extract(String source, String filePath);

	/**
	 * Get the file type identifier for this strategy.
	 *
	 * @return the file type (e.g., "java", "xml", "properties")
	 */
	String fileType();
}
