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
 * Strategy for extracting searchable metadata from Maven POM files.
 */
public class PomFileStrategy implements FileTypeStrategy {

	private static final Pattern GROUP_ID = Pattern
			.compile("<groupId>\\s*([^<]+?)\\s*</groupId>"); //$NON-NLS-1$

	private static final Pattern ARTIFACT_ID = Pattern
			.compile("<artifactId>\\s*([^<]+?)\\s*</artifactId>"); //$NON-NLS-1$

	private static final Pattern MODULE_PATTERN = Pattern
			.compile("<module>\\s*([^<]+?)\\s*</module>"); //$NON-NLS-1$

	private static final int MAX_SNIPPET = 65535;

	@Override
	public Set<String> supportedExtensions() {
		return Collections.emptySet();
	}

	@Override
	public Set<String> supportedFilenames() {
		return Set.of("pom.xml"); //$NON-NLS-1$
	}

	@Override
	public BlobIndexData extract(String source, String filePath) {
		BlobIndexData data = new BlobIndexData();
		data.setFileType("pom"); //$NON-NLS-1$
		data.setSourceSnippet(
				source.length() > MAX_SNIPPET
						? source.substring(0, MAX_SNIPPET)
						: source);

		Set<String> gavs = new LinkedHashSet<>();
		Set<String> artifacts = new LinkedHashSet<>();

		Matcher gm = GROUP_ID.matcher(source);
		while (gm.find()) {
			gavs.add(gm.group(1));
		}

		Matcher am = ARTIFACT_ID.matcher(source);
		while (am.find()) {
			artifacts.add(am.group(1));
			gavs.add(am.group(1));
		}

		data.setFullyQualifiedNames(
				String.join("\n", gavs)); //$NON-NLS-1$
		data.setDeclaredTypes(
				String.join("\n", artifacts)); //$NON-NLS-1$

		// Extract modules
		Set<String> modules = new LinkedHashSet<>();
		Matcher mm = MODULE_PATTERN.matcher(source);
		while (mm.find()) {
			modules.add(mm.group(1));
		}
		if (!modules.isEmpty()) {
			data.setDeclaredFields(
					String.join("\n", modules)); //$NON-NLS-1$
		}

		return data;
	}

	@Override
	public String fileType() {
		return "pom"; //$NON-NLS-1$
	}
}
