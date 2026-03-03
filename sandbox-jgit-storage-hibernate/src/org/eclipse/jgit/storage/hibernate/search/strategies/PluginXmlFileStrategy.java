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
package org.eclipse.jgit.storage.hibernate.search.strategies;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.storage.hibernate.search.BlobIndexData;
import org.eclipse.jgit.storage.hibernate.search.FileTypeStrategy;

/**
 * Strategy for extracting searchable metadata from Eclipse plugin.xml and
 * fragment.xml files.
 */
public class PluginXmlFileStrategy implements FileTypeStrategy {

	private static final Pattern EXT_POINT_PATTERN = Pattern
			.compile("point=\"([^\"]+)\""); //$NON-NLS-1$

	private static final Pattern CLASS_ATTR_PATTERN = Pattern
			.compile("class=\"([^\"]+)\""); //$NON-NLS-1$

	private static final int MAX_SNIPPET = 65535;

	@Override
	public Set<String> supportedExtensions() {
		return Collections.emptySet();
	}

	@Override
	public Set<String> supportedFilenames() {
		return Set.of("plugin.xml", "fragment.xml"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public BlobIndexData extract(String source, String filePath) {
		BlobIndexData data = new BlobIndexData();
		data.setFileType("plugin-xml"); //$NON-NLS-1$
		data.setSourceSnippet(
				source.length() > MAX_SNIPPET
						? source.substring(0, MAX_SNIPPET)
						: source);

		Set<String> extPoints = new LinkedHashSet<>();
		Matcher epMatcher = EXT_POINT_PATTERN.matcher(source);
		while (epMatcher.find()) {
			extPoints.add(epMatcher.group(1));
		}
		data.setFullyQualifiedNames(
				String.join("\n", extPoints)); //$NON-NLS-1$

		Set<String> classes = new LinkedHashSet<>();
		Matcher classMatcher = CLASS_ATTR_PATTERN.matcher(source);
		while (classMatcher.find()) {
			classes.add(classMatcher.group(1));
		}
		data.setDeclaredTypes(
				String.join("\n", classes)); //$NON-NLS-1$

		return data;
	}

	@Override
	public String fileType() {
		return "plugin-xml"; //$NON-NLS-1$
	}
}
