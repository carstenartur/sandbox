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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Core storage and loading functionality is delegated to
 * {@link HintFileStore} (in {@code sandbox_common_core}), which has no
 * Eclipse/OSGi dependencies. This class adds Eclipse-specific functionality
 * such as extension-point loading, workspace project scanning, and
 * Eclipse logging.</p>
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

/** Delegate for Eclipse-independent storage and loading. */
private final HintFileStore store = new HintFileStore();

/** Tracks which projects have been scanned for workspace hint files. */
private final java.util.Set<String> loadedProjects = ConcurrentHashMap.newKeySet();

/**
 * File extension for hint files (including the dot).
 */
private static final String HINT_FILE_EXTENSION = ".sandbox-hint"; //$NON-NLS-1$

private HintFileRegistry() {
Singleton
}

/**
 * Returns the singleton instance.
 * 
 * @return the hint file registry
 */
public static HintFileRegistry getInstance() {
 INSTANCE;
}

/**
 * Returns the underlying {@link HintFileStore} for direct access to
 * Eclipse-independent functionality.
 * 
 * @return the hint file store
 * @since 1.2.6
 */
public HintFileStore getStore() {
 store;
}

/**
 * Loads and registers a hint file from a string.
 * 
 * @param id the unique ID for this hint file
 * @param content the hint file content
 * @throws HintParseException if parsing fails
 */
public void loadFromString(String id, String content) throws HintParseException {
g(id, content);
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
reader);
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
HintParseException, IOException {
 store.loadFromClasspath(id, resourcePath, classLoader);
}

/**
 * Returns a registered hint file by ID.
 * 
 * @param id the hint file ID
 * @return the hint file, or {@code null} if not found
 */
public HintFile getHintFile(String id) {
 store.getHintFile(id);
}

/**
 * Returns all registered hint files.
 * 
 * @return unmodifiable map of ID to hint file
 */
public Map<String, HintFile> getAllHintFiles() {
 store.getAllHintFiles();
}

/**
 * Returns the IDs of all registered hint files.
 * 
 * @return unmodifiable list of hint file IDs
 */
public List<String> getRegisteredIds() {
 store.getRegisteredIds();
}

/**
 * Removes a registered hint file.
 * 
 * @param id the hint file ID to remove
 * @return the removed hint file, or {@code null} if not found
 */
public HintFile unregister(String id) {
 store.unregister(id);
}

/**
 * Removes all registered hint files.
 */
public void clear() {
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
 store.resolveIncludes(hintFile);
}

/**
 * Returns the names of bundled pattern libraries.
 * 
 * @return array of bundled library resource names
 */
public static String[] getBundledLibraryNames() {
 HintFileStore.getBundledLibraryNames();
}

/**
 * Attempts to load all bundled pattern libraries from the classpath.
 * Libraries that are not found are silently skipped.
 * 
 * @param classLoader the class loader to use for loading resources
 * @return list of successfully loaded library IDs
 */
public List<String> loadBundledLibraries(ClassLoader classLoader) {
 store.loadBundledLibraries(classLoader);
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
g> loaded = new ArrayList<>();
sionRegistry registry = Platform.getExtensionRegistry();
(registry == null) {
 loaded;
figurationElement[] elements = registry.getConfigurationElementsFor(HINTS_EXTENSION_POINT_ID);
(IConfigurationElement element : elements) {
(!"hintFile".equals(element.getName())) { //$NON-NLS-1$
tinue;
g id = element.getAttribute("id"); //$NON-NLS-1$
g resource = element.getAttribute("resource"); //$NON-NLS-1$
(id == null || resource == null) {
tinue;
Check enabled attribute (default: true)
g enabledAttr = element.getAttribute("enabled"); //$NON-NLS-1$
(enabledAttr != null && "false".equalsIgnoreCase(enabledAttr)) { //$NON-NLS-1$
tinue;
Skip already loaded hint files
(store.getHintFile(id) != null) {
tinue;
dle bundle = Platform.getBundle(element.getContributor().getName());
(bundle == null) {
tinue;
 {
resourceUrl = bundle.getResource(resource);
(resourceUrl == null) {
log = Platform.getLog(HintFileRegistry.class);
ing(
t file resource not found: " + resource //$NON-NLS-1$
" in bundle " + bundle.getSymbolicName())); //$NON-NLS-1$
tinue;
 (InputStream is = resourceUrl.openStream();
reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
reader);
catch (HintParseException | IOException e) {
log = Platform.getLog(HintFileRegistry.class);
ing(
to load hint file from extension: " + id, e)); //$NON-NLS-1$
 loaded;
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
(project == null || !project.isAccessible()) {
 Collections.emptyList();
g projectKey = project.getName();
(!loadedProjects.add(projectKey)) {
 Collections.emptyList(); // Already scanned
g> loaded = new ArrayList<>();
 {
ew IResourceVisitor() {
boolean visit(IResource resource) throws CoreException {
(resource instanceof IFile file
file.getName().endsWith(HINT_FILE_EXTENSION)) {
g id = "project:" + projectKey + ":" //$NON-NLS-1$ //$NON-NLS-2$
file.getProjectRelativePath().toString();
 (InputStream is = file.getContents();
reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
reader);
catch (HintParseException | IOException e) {
log = Platform.getLog(HintFileRegistry.class);
ing(
to load hint file: " + file.getFullPath(), e)); //$NON-NLS-1$
Skip output folders and hidden directories
(resource instanceof IContainer container) {
g name = container.getName();
 !name.startsWith(".") //$NON-NLS-1$
!"bin".equals(name) //$NON-NLS-1$
!"target".equals(name); //$NON-NLS-1$
 true;
catch (CoreException e) {
log = Platform.getLog(HintFileRegistry.class);
ing(
to scan project for hint files: " + projectKey, e)); //$NON-NLS-1$
 loaded;
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
(project != null) {
ame());
* Registers a set of inferred rules as a new hint file in the registry.
 *
 * <p>The rules are wrapped in a {@link HintFile} with the tag
 * {@code "inferred"} and the given source commit ID for traceability.
 * The hint file is immediately available for CleanUp and QuickAssist.</p>
 *
 * @param hintFile     the hint file containing inferred rules
 * @param sourceCommit the commit ID from which the rules were derived
 * @since 1.2.6
 */
public void registerInferredRules(HintFile hintFile, String sourceCommit) {
ferredRules(hintFile, sourceCommit);
}

/**
 * Returns all hint files that were inferred (have the "inferred" tag prefix).
 *
 * @return list of inferred hint files
 * @since 1.2.6
 */
public List<HintFile> getInferredHintFiles() {
 store.getInferredHintFiles();
}

/**
 * Promotes an inferred hint file to a manual (user-authored) one by
 * removing the "inferred:" prefix from its ID and re-registering it.
 *
 * @param hintFileId the original inferred hint file ID
 * @since 1.2.6
 */
public void promoteToManual(String hintFileId) {
ual(hintFileId);
}
}
