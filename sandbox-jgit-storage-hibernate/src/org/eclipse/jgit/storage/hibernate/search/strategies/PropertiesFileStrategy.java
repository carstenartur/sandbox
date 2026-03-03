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

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.storage.hibernate.search.BlobIndexData;
import org.eclipse.jgit.storage.hibernate.search.FileTypeStrategy;

/**
 * Strategy for extracting searchable metadata from {@code .properties} files.
 * <p>
 * Extracts property keys as declared symbols, and property values that look
 * like Java FQNs or file paths.
 * </p>
 */
public class PropertiesFileStrategy implements FileTypeStrategy {

	private static final Logger LOG = Logger
			.getLogger(PropertiesFileStrategy.class.getName());

	private static final int MAX_SNIPPET_LENGTH = 65535;

	private static final Pattern FQN_PATTERN = Pattern.compile(
			"[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*\\.[A-Z]\\w*"); //$NON-NLS-1$

	@Override
	public Set<String> supportedExtensions() {
		return Set.of(".properties"); //$NON-NLS-1$
	}

	@Override
	public Set<String> supportedFilenames() {
		return Collections.emptySet();
	}

	@Override
	public BlobIndexData extract(String source, String filePath) {
		BlobIndexData data = new BlobIndexData();
		data.setFileType("properties"); //$NON-NLS-1$
		data.setSourceSnippet(truncate(source, MAX_SNIPPET_LENGTH));

		try {
			Properties props = new Properties();
			props.load(new StringReader(source));

			StringJoiner keys = new StringJoiner("\n"); //$NON-NLS-1$
			StringJoiner fqns = new StringJoiner("\n"); //$NON-NLS-1$

			Enumeration<?> names = props.propertyNames();
			while (names.hasMoreElements()) {
				String key = (String) names.nextElement();
				keys.add(key);
				String value = props.getProperty(key);
				if (value != null) {
					Matcher m = FQN_PATTERN.matcher(value);
					while (m.find()) {
						fqns.add(m.group());
					}
				}
			}

			String keyStr = keys.toString();
			if (!keyStr.isEmpty()) {
				data.setDeclaredFields(keyStr);
			}
			String fqnStr = fqns.toString();
			if (!fqnStr.isEmpty()) {
				data.setFullyQualifiedNames(fqnStr);
			}
		} catch (IOException e) {
			LOG.log(Level.WARNING,
					"Failed to parse properties file: {0}: {1}", //$NON-NLS-1$
					new Object[] { filePath, e.getMessage() });
		}

		return data;
	}

	@Override
	public String fileType() {
		return "properties"; //$NON-NLS-1$
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
