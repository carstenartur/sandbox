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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Eclipse-independent store for loading, indexing and managing
 * {@code .sandbox-hint} files.
 *
 * <p>This class contains pure-Java storage and loading logic that does
 * <b>not</b> depend on the Eclipse runtime, OSGi, or workspace APIs.
 * It can be used stand-alone in tests, CLI tools, or any JVM application
 * that needs to work with hint files.</p>
 *
 * <p>This class is <b>not</b> a singleton&mdash;callers may create as many
 * instances as required.  Thread safety is provided by
 * {@link ConcurrentHashMap} and {@link AtomicBoolean}.</p>
 *
 * @since 1.2.6
 */
public final class HintFileStore {

	/**
	 * Bundled library resource names.
	 */
	private static final String[] BUNDLED_LIBRARIES = {
		"collections.sandbox-hint", //$NON-NLS-1$
		"modernize-java9.sandbox-hint", //$NON-NLS-1$
		"modernize-java11.sandbox-hint", //$NON-NLS-1$
		"performance.sandbox-hint" //$NON-NLS-1$
	};

	/**
	 * Classpath resource prefix for bundled library files.
	 */
	private static final String BUNDLED_RESOURCE_PREFIX =
		"org/sandbox/jdt/triggerpattern/internal/"; //$NON-NLS-1$

	private final Map<String, HintFile> hintFiles = new ConcurrentHashMap<>();

	/** Secondary index: declared {@code <!id: ...>} &rarr; HintFile, for efficient include resolution. */
	private final Map<String, HintFile> hintFilesByDeclaredId = new ConcurrentHashMap<>();

	private final HintFileParser parser = new HintFileParser();

	private final AtomicBoolean bundledLoaded = new AtomicBoolean(false);

	/**
	 * Creates a new, empty hint-file store.
	 */
	public HintFileStore() {
		// public, non-singleton constructor
	}

	// ------------------------------------------------------------
	// Loading
	// ------------------------------------------------------------

	/**
	 * Loads and registers a hint file from a string.
	 *
	 * @param id      the unique ID for this hint file
	 * @param content the hint file content
	 * @throws HintParseException if parsing fails
	 */
	public void loadFromString(String id, String content) throws HintParseException {
		HintFile hintFile = parser.parse(content);
		if (hintFile.getId() == null) {
			hintFile.setId(id);
		}
		hintFiles.put(id, hintFile);
		indexByDeclaredId(hintFile);
	}

	/**
	 * Loads and registers a hint file from a reader.
	 *
	 * @param id     the unique ID for this hint file
	 * @param reader the reader to read from
	 * @throws HintParseException if parsing fails
	 * @throws IOException        if an I/O error occurs
	 */
	public void loadFromReader(String id, Reader reader) throws HintParseException, IOException {
		HintFile hintFile = parser.parse(reader);
		if (hintFile.getId() == null) {
			hintFile.setId(id);
		}
		hintFiles.put(id, hintFile);
		indexByDeclaredId(hintFile);
	}

	/**
	 * Loads and registers a hint file from a classpath resource.
	 *
	 * @param id           the unique ID for this hint file
	 * @param resourcePath the classpath resource path
	 * @param classLoader  the class loader to use for loading
	 * @return {@code true} if the resource was found and loaded
	 * @throws HintParseException if parsing fails
	 * @throws IOException        if an I/O error occurs
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

	// ------------------------------------------------------------
	// Queries
	// ------------------------------------------------------------

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

	// ------------------------------------------------------------
	// Mutation
	// ------------------------------------------------------------

	/**
	 * Removes a registered hint file.
	 *
	 * @param id the hint file ID to remove
	 * @return the removed hint file, or {@code null} if not found
	 */
	public HintFile unregister(String id) {
		HintFile removed = hintFiles.remove(id);
		if (removed != null && removed.getId() != null) {
			hintFilesByDeclaredId.remove(removed.getId());
		}
		return removed;
	}

	/**
	 * Removes all registered hint files and resets the bundled-loaded flag.
	 */
	public void clear() {
		hintFiles.clear();
		hintFilesByDeclaredId.clear();
		bundledLoaded.set(false);
	}

	// ------------------------------------------------------------
	// Include resolution
	// ------------------------------------------------------------

	/**
	 * Resolves include directives for a hint file by collecting all rules
	 * from referenced hint files.
	 *
	 * <p>When a hint file has {@code <!include: other-id>} directives, this method
	 * looks up the referenced hint files by ID and returns a combined list of all
	 * rules (the file's own rules plus all included rules).</p>
	 *
	 * <p>Circular includes are detected and silently broken to prevent infinite loops.</p>
	 *
	 * @param hintFile the hint file whose includes should be resolved
	 * @return list of all rules including those from included files
	 */
	public List<TransformationRule> resolveIncludes(HintFile hintFile) {
		List<TransformationRule> allRules = new ArrayList<>(hintFile.getRules());
		Set<String> visited = new HashSet<>();
		if (hintFile.getId() != null) {
			visited.add(hintFile.getId());
		}
		resolveIncludesRecursive(hintFile, allRules, visited);
		return Collections.unmodifiableList(allRules);
	}

	/**
	 * Recursively resolves includes, tracking visited IDs to prevent cycles.
	 *
	 * <p>Looks up included files first by registry key, then by declared
	 * {@code <!id: ...>} so that {@code <!include:>} directives work
	 * consistently regardless of how the hint file was registered.</p>
	 */
	private void resolveIncludesRecursive(HintFile hintFile,
			List<TransformationRule> allRules, Set<String> visited) {
		for (String includeId : hintFile.getIncludes()) {
			if (visited.contains(includeId)) {
				continue; // Break circular reference
			}
			visited.add(includeId);
			HintFile included = findByKeyOrDeclaredId(includeId);
			if (included != null) {
				allRules.addAll(included.getRules());
				resolveIncludesRecursive(included, allRules, visited);
			}
		}
	}

	// ------------------------------------------------------------
	// Bundled libraries
	// ------------------------------------------------------------

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
	 * <p>This method is idempotent&mdash;subsequent calls after a successful
	 * first invocation return the currently registered IDs without reloading.</p>
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
			}
		}
		return loaded;
	}

	// ------------------------------------------------------------
	// Inferred rules
	// ------------------------------------------------------------

	/**
	 * Registers a set of inferred rules as a new hint file in the store.
	 *
	 * <p>The rules are wrapped in a {@link HintFile} with the tag
	 * {@code "inferred"} and the given source commit ID for traceability.
	 * The hint file is immediately available for look-up.</p>
	 *
	 * @param hintFile     the hint file containing inferred rules
	 * @param sourceCommit the commit ID from which the rules were derived
	 * @since 1.2.6
	 */
	public void registerInferredRules(HintFile hintFile, String sourceCommit) {
		String id = "inferred:" + sourceCommit; //$NON-NLS-1$
		hintFile.setId(id);
		if (hintFile.getTags() == null || hintFile.getTags().isEmpty()) {
			hintFile.setTags(List.of("inferred", "mining", sourceCommit)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		hintFiles.put(id, hintFile);
		hintFilesByDeclaredId.put(id, hintFile);
	}

	/**
	 * Returns all hint files that were inferred (have the {@code "inferred:"} key prefix).
	 *
	 * @return list of inferred hint files
	 * @since 1.2.6
	 */
	public List<HintFile> getInferredHintFiles() {
		List<HintFile> inferred = new ArrayList<>();
		for (Map.Entry<String, HintFile> entry : hintFiles.entrySet()) {
			if (entry.getKey().startsWith("inferred:")) { //$NON-NLS-1$
				inferred.add(entry.getValue());
			}
		}
		return inferred;
	}

	/**
	 * Promotes an inferred hint file to a manual (user-authored) one by
	 * removing the {@code "inferred:"} prefix from its ID and re-registering it.
	 *
	 * @param hintFileId the original inferred hint file ID
	 * @since 1.2.6
	 */
	public void promoteToManual(String hintFileId) {
		HintFile hintFile = hintFiles.remove(hintFileId);
		if (hintFile != null) {
			hintFilesByDeclaredId.remove(hintFileId);
			String newId = hintFileId.replace("inferred:", "manual:"); //$NON-NLS-1$ //$NON-NLS-2$
			hintFile.setId(newId);
			hintFiles.put(newId, hintFile);
			hintFilesByDeclaredId.put(newId, hintFile);
		}
	}

	// ------------------------------------------------------------
	// Internal helpers
	// ------------------------------------------------------------

	/**
	 * Updates the secondary index for declared IDs.
	 */
	private void indexByDeclaredId(HintFile hintFile) {
		if (hintFile.getId() != null) {
			hintFilesByDeclaredId.put(hintFile.getId(), hintFile);
		}
	}

	/**
	 * Finds a hint file by registry key first, then falls back to the
	 * secondary index of declared {@link HintFile#getId()} values.
	 *
	 * @param id the ID to look up
	 * @return the matching hint file, or {@code null} if not found
	 */
	private HintFile findByKeyOrDeclaredId(String id) {
		HintFile result = hintFiles.get(id);
		if (result != null) {
			return result;
		}
		// Fall back: lookup by declared <!id: ...> via secondary index
		return hintFilesByDeclaredId.get(id);
	}
}
