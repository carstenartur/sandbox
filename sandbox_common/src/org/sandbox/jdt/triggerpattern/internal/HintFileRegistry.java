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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Registry for loading and managing {@code .sandbox-hint} files.
 * 
 * <p>The registry provides access to bundled pattern libraries and
 * user-defined hint files. Bundled libraries are loaded from the
 * classpath, while user-defined files can be loaded from the filesystem.</p>
 * 
 * <h2>Bundled Pattern Libraries</h2>
 * <p>Generic libraries bundled with the framework:</p>
 * <ul>
 *   <li>{@code collections.sandbox-hint} — Collection API improvements</li>
 *   <li>{@code modernize-java9.sandbox-hint} — Java 9+ API modernization</li>
 *   <li>{@code modernize-java11.sandbox-hint} — Java 11+ API modernization</li>
 *   <li>{@code performance.sandbox-hint} — Performance optimizations</li>
 * </ul>
 * 
 * <p>Domain-specific libraries are provided by their dedicated plugins:</p>
 * <ul>
 *   <li>{@code encoding.sandbox-hint} — in {@code sandbox_encoding_quickfix}</li>
 *   <li>{@code junit5.sandbox-hint} — in {@code sandbox_junit_cleanup}</li>
 * </ul>
 * 
 * <p>This class is thread-safe. All mutable state is protected by
 * synchronization or concurrent data structures.</p>
 * 
 * @since 1.3.2
 */
public final class HintFileRegistry {
	
	private static final String HINTS_EXTENSION_POINT_ID = "org.sandbox.jdt.triggerpattern.hints"; //$NON-NLS-1$
	
	private static final HintFileRegistry INSTANCE = new HintFileRegistry();
	
	private final Map<String, HintFile> hintFiles = new ConcurrentHashMap<>();
	/** Secondary index: declared {@code <!id: ...>} → HintFile, for efficient include resolution. */
	private final Map<String, HintFile> hintFilesByDeclaredId = new ConcurrentHashMap<>();
	private final HintFileParser parser = new HintFileParser();
	private final AtomicBoolean bundledLoaded = new AtomicBoolean(false);
	/** Tracks which projects have been scanned for workspace hint files. */
	private final Set<String> loadedProjects = ConcurrentHashMap.newKeySet();
	
	/**
	 * File extension for hint files (including the dot).
	 */
	private static final String HINT_FILE_EXTENSION = ".sandbox-hint"; //$NON-NLS-1$
	
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
		indexByDeclaredId(hintFile);
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
		indexByDeclaredId(hintFile);
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
		HintFile removed = hintFiles.remove(id);
		if (removed != null && removed.getId() != null) {
			hintFilesByDeclaredId.remove(removed.getId());
		}
		return removed;
	}
	
	/**
	 * Removes all registered hint files.
	 */
	public void clear() {
		hintFiles.clear();
		hintFilesByDeclaredId.clear();
		loadedProjects.clear();
		bundledLoaded.set(false);
	}
	
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
	 * @since 1.3.4
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
	
	/**
	 * Updates the secondary index for declared IDs.
	 */
	private void indexByDeclaredId(HintFile hintFile) {
		if (hintFile.getId() != null) {
			hintFilesByDeclaredId.put(hintFile.getId(), hintFile);
		}
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
	
	/**
	 * Loads {@code .sandbox-hint} files registered via the
	 * {@code org.sandbox.jdt.triggerpattern.hints} extension point.
	 * 
	 * <p>This method queries the Eclipse extension registry for {@code hintFile}
	 * elements contributed by other plugins. Each element specifies an {@code id}
	 * and a {@code resource} path pointing to a {@code .sandbox-hint} file on the
	 * contributing bundle's classpath.</p>
	 * 
	 * @return list of successfully loaded hint file IDs
	 * @since 1.3.6
	 */
	public List<String> loadFromExtensions() {
		List<String> loaded = new ArrayList<>();
		
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		if (registry == null) {
			return loaded;
		}
		
		IConfigurationElement[] elements = registry.getConfigurationElementsFor(HINTS_EXTENSION_POINT_ID);
		
		for (IConfigurationElement element : elements) {
			if (!"hintFile".equals(element.getName())) { //$NON-NLS-1$
				continue;
			}
			String id = element.getAttribute("id"); //$NON-NLS-1$
			String resource = element.getAttribute("resource"); //$NON-NLS-1$
			
			if (id == null || resource == null) {
				continue;
			}
			
			// Skip already loaded hint files
			if (hintFiles.containsKey(id)) {
				loaded.add(id);
				continue;
			}
			
			Bundle bundle = Platform.getBundle(element.getContributor().getName());
			if (bundle == null) {
				continue;
			}
			
			try {
				URL resourceUrl = bundle.getResource(resource);
				if (resourceUrl == null) {
					ILog log = Platform.getLog(HintFileRegistry.class);
					log.log(Status.warning(
							"Hint file resource not found: " + resource //$NON-NLS-1$
							+ " in bundle " + bundle.getSymbolicName())); //$NON-NLS-1$
					continue;
				}
				try (InputStream is = resourceUrl.openStream();
						Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
					loadFromReader(id, reader);
					loaded.add(id);
				}
			} catch (HintParseException | IOException e) {
				ILog log = Platform.getLog(HintFileRegistry.class);
				log.log(Status.warning(
						"Failed to load hint file from extension: " + id, e)); //$NON-NLS-1$
			}
		}
		
		return loaded;
	}
	
	/**
	 * Discovers and loads {@code .sandbox-hint} files from a workspace project.
	 * 
	 * <p>Scans the project root for files with the {@code .sandbox-hint} extension
	 * and registers them. Each project is scanned at most once; subsequent calls
	 * with the same project are no-ops.</p>
	 * 
	 * <p>This enables users to define custom transformation rules per project
	 * by placing {@code .sandbox-hint} files in the project directory.</p>
	 * 
	 * @param project the Eclipse project to scan
	 * @return list of successfully loaded hint file IDs from this project
	 * @since 1.3.6
	 */
	public List<String> loadProjectHintFiles(IProject project) {
		if (project == null || !project.isAccessible()) {
			return Collections.emptyList();
		}
		
		String projectKey = project.getName();
		if (!loadedProjects.add(projectKey)) {
			return Collections.emptyList(); // Already scanned
		}
		
		List<String> loaded = new ArrayList<>();
		try {
			project.accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					if (resource instanceof IFile file
							&& file.getName().endsWith(HINT_FILE_EXTENSION)) {
						String id = "project:" + projectKey + ":" //$NON-NLS-1$ //$NON-NLS-2$
								+ file.getProjectRelativePath().toString();
						try (InputStream is = file.getContents();
								Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
							loadFromReader(id, reader);
							loaded.add(id);
						} catch (HintParseException | IOException e) {
							ILog log = Platform.getLog(HintFileRegistry.class);
							log.log(Status.warning(
									"Failed to load hint file: " + file.getFullPath(), e)); //$NON-NLS-1$
						}
					}
					// Skip output folders and hidden directories
					if (resource instanceof IContainer container) {
						String name = container.getName();
						return !name.startsWith(".") //$NON-NLS-1$
								&& !"bin".equals(name) //$NON-NLS-1$
								&& !"target".equals(name); //$NON-NLS-1$
					}
					return true;
				}
			});
		} catch (CoreException e) {
			ILog log = Platform.getLog(HintFileRegistry.class);
			log.log(Status.warning(
					"Failed to scan project for hint files: " + projectKey, e)); //$NON-NLS-1$
		}
		return loaded;
	}
	
	/**
	 * Forces a re-scan of the given project on the next call to
	 * {@link #loadProjectHintFiles(IProject)}.
	 * 
	 * <p>This is useful when a project's {@code .sandbox-hint} files have changed
	 * and need to be reloaded.</p>
	 * 
	 * @param project the project to invalidate
	 * @since 1.3.6
	 */
	public void invalidateProject(IProject project) {
		if (project != null) {
			loadedProjects.remove(project.getName());
		}
	}
}
