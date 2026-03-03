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
 * Strategy for extracting searchable metadata from XML files.
 */
public class XmlFileStrategy implements FileTypeStrategy {

	private static final Pattern NAMESPACE_PATTERN = Pattern
			.compile("xmlns[^\"]*=\"([^\"]+)\""); //$NON-NLS-1$

	private static final Pattern ELEMENT_PATTERN = Pattern
			.compile("<([a-zA-Z][\\w.:-]*)\\s"); //$NON-NLS-1$

	private static final int MAX_SNIPPET = 65535;

	@Override
	public Set<String> supportedExtensions() {
		return Set.of(".xml", ".xsd", ".exsd", ".xsl"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	@Override
	public Set<String> supportedFilenames() {
		return Collections.emptySet();
	}

	@Override
	public BlobIndexData extract(String source, String filePath) {
		BlobIndexData data = new BlobIndexData();
		data.setFileType("xml"); //$NON-NLS-1$
		data.setSourceSnippet(
				source.length() > MAX_SNIPPET
						? source.substring(0, MAX_SNIPPET)
						: source);

		Set<String> namespaces = new LinkedHashSet<>();
		Matcher nsMatcher = NAMESPACE_PATTERN.matcher(source);
		while (nsMatcher.find()) {
			namespaces.add(nsMatcher.group(1));
		}
		data.setFullyQualifiedNames(
				String.join("\n", namespaces)); //$NON-NLS-1$

		Set<String> elements = new LinkedHashSet<>();
		Matcher elemMatcher = ELEMENT_PATTERN.matcher(source);
		while (elemMatcher.find()) {
			elements.add(elemMatcher.group(1));
		}
		data.setDeclaredTypes(String.join("\n", elements)); //$NON-NLS-1$

		return data;
	}

	@Override
	public String fileType() {
		return "xml"; //$NON-NLS-1$
	}
}
