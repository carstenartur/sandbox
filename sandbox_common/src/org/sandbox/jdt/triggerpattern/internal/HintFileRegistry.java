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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Registry for loading and managing {@code .sandbox-hint} files.
 * 
 * <p>The registry provides access to bundled pattern libraries and
 * user-defined hint files. Bundled libraries are loaded from the
 * classpath, while user-defined files can be loaded from the filesystem.</p>
 * 
 * <h2>Bundled Pattern Libraries</h2>
 * <ul>
 *   <li>{@code encoding.sandbox-hint} — StandardCharsets migration</li>
 *   <li>{@code collections.sandbox-hint} — Collection API improvements</li>
 *   <li>{@code modernize-java11.sandbox-hint} — Java 11+ API modernization</li>
 *   <li>{@code performance.sandbox-hint} — Performance optimizations</li>
 *   <li>{@code junit5.sandbox-hint} — JUnit 4 → 5 migration</li>
 * </ul>
 * 
 * <p>This class is thread-safe. All mutable state is protected by
 * synchronization or concurrent data structures.</p>
 * 
 * @since 1.3.2
 */
public final class HintFileRegistry {
	
	private static final HintFileRegistry INSTANCE = new HintFileRegistry();
	
	private final Map<String, HintFile> hintFiles = new ConcurrentHashMap<>();
	private final HintFileParser parser = new HintFileParser();
	private final AtomicBoolean bundledLoaded = new AtomicBoolean(false);
	
	/**
	 * Bundled library resource names.
	 */
	private static final String[] BUNDLED_LIBRARIES = {
		"encoding.sandbox-hint", //$NON-NLS-1$
		"collections.sandbox-hint", //$NON-NLS-1$
		"modernize-java11.sandbox-hint", //$NON-NLS-1$
		"performance.sandbox-hint", //$NON-NLS-1$
		"junit5.sandbox-hint" //$NON-NLS-1$
	};
	
	/**
	 * Classpath resource prefix for bundled library files.
	 */
	private static final String BUNDLED_RESOURCE_PREFIX = 
		"org/sandbox/jdt/triggerpattern/internal/"; //$NON-NLS-1$
	
	private HintFileRegistry() {
		// Singleton
	}
	
	/**
	 * Returns the singleton instance.
	 * 
	 * @return the hint file registry
	 */
	public static HintFileRegistry getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Loads and registers a hint file from a string.
	 * 
	 * @param id the unique ID for this hint file
	 * @param content the hint file content
	 * @throws HintParseException if parsing fails
	 */
	public void loadFromString(String id, String content) throws HintParseException {
		HintFile hintFile = parser.parse(content);
		if (hintFile.getId() == null) {
			hintFile.setId(id);
		}
		hintFiles.put(id, hintFile);
	}
	
	/**
	 * Loads and registers a hint file from a reader.
	 * 
	 * @param id the unique ID for this hint file
	 * @param reader the reader to read from
	 * @throws HintParseException if parsing fails
	 * @throws IOException if an I/O error occurs
	 */
	public void loadFromReader(String id, Reader reader) throws HintParseException, IOException {
		HintFile hintFile = parser.parse(reader);
		if (hintFile.getId() == null) {
			hintFile.setId(id);
		}
		hintFiles.put(id, hintFile);
	}
	
	/**
	 * Loads and registers a hint file from a classpath resource.
	 * 
	 * @param id the unique ID for this hint file
	 * @param resourcePath the classpath resource path
	 * @param classLoader the class loader to use for loading
	 * @return {@code true} if the resource was found and loaded
	 * @throws HintParseException if parsing fails
	 * @throws IOException if an I/O error occurs
	 */
	public boolean loadFromClasspath(String id, String resourcePath, ClassLoader classLoader) 
			throws HintParseException, IOException {
		InputStream is = classLoader.getResourceAsStream(resourcePath);
		if (is == null) {
			return false;
		}
		try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			loadFromReader(id, reader);
			return true;
		}
	}
	
	/**
	 * Returns a registered hint file by ID.
	 * 
	 * @param id the hint file ID
	 * @return the hint file, or {@code null} if not found
	 */
	public HintFile getHintFile(String id) {
		return hintFiles.get(id);
	}
	
	/**
	 * Returns all registered hint files.
	 * 
	 * @return unmodifiable map of ID to hint file
	 */
	public Map<String, HintFile> getAllHintFiles() {
		return Collections.unmodifiableMap(hintFiles);
	}
	
	/**
	 * Returns the IDs of all registered hint files.
	 * 
	 * @return unmodifiable list of hint file IDs
	 */
	public List<String> getRegisteredIds() {
		return Collections.unmodifiableList(new ArrayList<>(hintFiles.keySet()));
	}
	
	/**
	 * Removes a registered hint file.
	 * 
	 * @param id the hint file ID to remove
	 * @return the removed hint file, or {@code null} if not found
	 */
	public HintFile unregister(String id) {
		return hintFiles.remove(id);
	}
	
	/**
	 * Removes all registered hint files.
	 */
	public void clear() {
		hintFiles.clear();
		bundledLoaded.set(false);
	}
	
	/**
	 * Returns the names of bundled pattern libraries.
	 * 
	 * @return array of bundled library resource names
	 */
	public static String[] getBundledLibraryNames() {
		return BUNDLED_LIBRARIES.clone();
	}
	
	/**
	 * Attempts to load all bundled pattern libraries from the classpath.
	 * Libraries that are not found are silently skipped.
	 * 
	 * @param classLoader the class loader to use for loading resources
	 * @return list of successfully loaded library IDs
	 */
	public List<String> loadBundledLibraries(ClassLoader classLoader) {
		if (!bundledLoaded.compareAndSet(false, true)) {
			return getRegisteredIds();
		}
		List<String> loaded = new ArrayList<>();
		for (String libraryName : BUNDLED_LIBRARIES) {
			String id = libraryName.replace(".sandbox-hint", ""); //$NON-NLS-1$ //$NON-NLS-2$
			String resourcePath = BUNDLED_RESOURCE_PREFIX + libraryName;
			try {
				if (loadFromClasspath(id, resourcePath, classLoader)) {
					loaded.add(id);
				}
			} catch (HintParseException | IOException e) {
				// Silently skip libraries that fail to load
				// In a full Eclipse environment, this would be logged
			}
		}
		return loaded;
	}
}
