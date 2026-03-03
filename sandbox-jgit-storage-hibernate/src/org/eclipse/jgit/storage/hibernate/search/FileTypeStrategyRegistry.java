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

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.storage.hibernate.search.strategies.GenericTextFileStrategy;
import org.eclipse.jgit.storage.hibernate.search.strategies.JavaFileStrategy;
import org.eclipse.jgit.storage.hibernate.search.strategies.ManifestFileStrategy;
import org.eclipse.jgit.storage.hibernate.search.strategies.PluginXmlFileStrategy;
import org.eclipse.jgit.storage.hibernate.search.strategies.PomFileStrategy;
import org.eclipse.jgit.storage.hibernate.search.strategies.PropertiesFileStrategy;
import org.eclipse.jgit.storage.hibernate.search.strategies.XmlFileStrategy;

/**
 * Registry that maps file paths to the appropriate
 * {@link FileTypeStrategy}.
 * <p>
 * Resolution order:
 * <ol>
 * <li>Exact filename match (e.g., "pom.xml", "MANIFEST.MF")</li>
 * <li>Extension match (e.g., ".java", ".properties")</li>
 * <li>Fallback to {@link GenericTextFileStrategy}</li>
 * </ol>
 * </p>
 */
public class FileTypeStrategyRegistry {

	private final Map<String, FileTypeStrategy> byFilename = new LinkedHashMap<>();

	private final Map<String, FileTypeStrategy> byExtension = new LinkedHashMap<>();

	private final FileTypeStrategy fallback = new GenericTextFileStrategy();

	/**
	 * Create a registry with all built-in strategies.
	 */
	public FileTypeStrategyRegistry() {
		register(new JavaFileStrategy());
		register(new PropertiesFileStrategy());
		register(new PomFileStrategy());
		register(new XmlFileStrategy());
		register(new ManifestFileStrategy());
		register(new PluginXmlFileStrategy());
	}

	/**
	 * Register a strategy in the registry.
	 *
	 * @param strategy
	 *            the strategy to register
	 */
	public void register(FileTypeStrategy strategy) {
		for (String filename : strategy.supportedFilenames()) {
			byFilename.put(filename.toLowerCase(), strategy);
		}
		for (String ext : strategy.supportedExtensions()) {
			byExtension.put(ext.toLowerCase(), strategy);
		}
	}

	/**
	 * Get the strategy for a given file path.
	 *
	 * @param filePath
	 *            the file path within the repository
	 * @return the matching strategy, or the fallback
	 *         {@link GenericTextFileStrategy}
	 */
	public FileTypeStrategy getStrategy(String filePath) {
		String filename = extractFilename(filePath);

		// 1. Exact filename match
		FileTypeStrategy strategy = byFilename
				.get(filename.toLowerCase());
		if (strategy != null) {
			return strategy;
		}

		// 2. Extension match
		int dot = filename.lastIndexOf('.');
		if (dot >= 0) {
			String ext = filename.substring(dot).toLowerCase();
			strategy = byExtension.get(ext);
			if (strategy != null) {
				return strategy;
			}
		}

		// 3. Fallback
		return fallback;
	}

	/**
	 * Get the fallback strategy.
	 *
	 * @return the {@link GenericTextFileStrategy}
	 */
	public FileTypeStrategy getFallback() {
		return fallback;
	}

	private static String extractFilename(String filePath) {
		int slash = filePath.lastIndexOf('/');
		if (slash >= 0) {
			return filePath.substring(slash + 1);
		}
		return filePath;
	}
}
