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
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.storage.hibernate.search.BlobIndexData;
import org.eclipse.jgit.storage.hibernate.search.FileTypeStrategy;

/**
 * Fallback strategy for extracting searchable metadata from any text file.
 * <p>
 * Uses regex scanning to find strings matching Java FQN patterns. This
 * strategy is used for all file types that don't have a dedicated strategy.
 * </p>
 */
public class GenericTextFileStrategy implements FileTypeStrategy {

	private static final int MAX_SNIPPET_LENGTH = 65535;

	private static final Pattern FQN_PATTERN = Pattern.compile(
			"[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*\\.[A-Z]\\w*"); //$NON-NLS-1$

	@Override
	public Set<String> supportedExtensions() {
		return Collections.emptySet();
	}

	@Override
	public Set<String> supportedFilenames() {
		return Collections.emptySet();
	}

	@Override
	public BlobIndexData extract(String source, String filePath) {
		BlobIndexData data = new BlobIndexData();
		data.setFileType(detectFileType(filePath));
		data.setSourceSnippet(truncate(source, MAX_SNIPPET_LENGTH));

		StringJoiner fqns = new StringJoiner("\n"); //$NON-NLS-1$
		Matcher m = FQN_PATTERN.matcher(source);
		while (m.find()) {
			fqns.add(m.group());
		}
		String fqnStr = fqns.toString();
		if (!fqnStr.isEmpty()) {
			data.setFullyQualifiedNames(
					truncate(fqnStr, MAX_SNIPPET_LENGTH));
		}

		return data;
	}

	@Override
	public String fileType() {
		return "text"; //$NON-NLS-1$
	}

	private static String detectFileType(String filePath) {
		int dot = filePath.lastIndexOf('.');
		if (dot >= 0) {
			return filePath.substring(dot + 1).toLowerCase();
		}
		return "text"; //$NON-NLS-1$
	}

	private static String truncate(String text, int maxLength) {
		if (text == null) {
			return null;
		}
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength);
	}
}
