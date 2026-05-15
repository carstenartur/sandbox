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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

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

	private static final Logger LOGGER = Logger.getLogger(HintFileStore.class.getName());

	/** ID prefix for AI-inferred hint files. */
	static final String INFERRED_PREFIX = "inferred:"; //$NON-NLS-1$

	/** File name prefix for persisted AI-inferred hint files. */
	private static final String AI_INFERRED_FILE_PREFIX = "ai-inferred-"; //$NON-NLS-1$

	/** File extension for sandbox hint files. */
	private static final String SANDBOX_HINT_EXTENSION = ".sandbox-hint"; //$NON-NLS-1$

	/** Directory name within a project for persisted hint files. */
	static final String HINTS_DIRECTORY = ".hints"; //$NON-NLS-1$

	/**
	 * Bundled library resource names.
	 */
	private static final String[] BUNDLED_LIBRARIES = {
		"collections.sandbox-hint", //$NON-NLS-1$
		"modernize-java9.sandbox-hint", //$NON-NLS-1$
		"modernize-java11.sandbox-hint", //$NON-NLS-1$
		"performance.sandbox-hint", //$NON-NLS-1$
		"stream-performance.sandbox-hint", //$NON-NLS-1$
		"io-performance.sandbox-hint", //$NON-NLS-1$
		"collection-performance.sandbox-hint", //$NON-NLS-1$
		"number-compare.sandbox-hint", //$NON-NLS-1$
		"string-equals.sandbox-hint", //$NON-NLS-1$
		"string-isblank.sandbox-hint", //$NON-NLS-1$
		"arrays.sandbox-hint", //$NON-NLS-1$
		"collection-toarray.sandbox-hint", //$NON-NLS-1$
		"probable-bugs.sandbox-hint", //$NON-NLS-1$
		"misc.sandbox-hint", //$NON-NLS-1$
		"deprecations.sandbox-hint", //$NON-NLS-1$
		"classfile-api.sandbox-hint", //$NON-NLS-1$
		"serialization.sandbox-hint", //$NON-NLS-1$
		"stringbuffer-to-stringbuilder.sandbox-hint", //$NON-NLS-1$
		"platform-logging.sandbox-hint", //$NON-NLS-1$
		"type-inference.sandbox-hint", //$NON-NLS-1$
		"try-with-resources.sandbox-hint", //$NON-NLS-1$
		"string-modernization.sandbox-hint", //$NON-NLS-1$
		"optional-modernization.sandbox-hint" //$NON-NLS-1$
	};

	/**
	 * Bundled library resource names that are shipped for maintenance and validation,
	 * but are not loaded by default or exposed as active cleanup bundles.
	 */
	private static final String[] DISABLED_BUNDLED_LIBRARIES = {
		"anonymous-to-lambda.sandbox-hint", //$NON-NLS-1$
		"array-initialization.sandbox-hint", //$NON-NLS-1$
		"code-style.sandbox-hint", //$NON-NLS-1$
		"collections-immutable.sandbox-hint", //$NON-NLS-1$
		"comparable-compareto-cleanup.sandbox-hint", //$NON-NLS-1$
		"concurrency.sandbox-hint", //$NON-NLS-1$
		"deprecated-api.sandbox-hint", //$NON-NLS-1$
		"eclipse-api-configuration.sandbox-hint", //$NON-NLS-1$
		"eclipse-api-deprecations.sandbox-hint", //$NON-NLS-1$
		"eclipse-api-modernization.sandbox-hint", //$NON-NLS-1$
		"eclipse-platform-ui-mined.sandbox-hint", //$NON-NLS-1$
		"icu-migration.sandbox-hint", //$NON-NLS-1$
		"java19-deprecations.sandbox-hint", //$NON-NLS-1$
		"jdt-api-modernization.sandbox-hint", //$NON-NLS-1$
		"jdt-formatter-modernization.sandbox-hint", //$NON-NLS-1$
		"jdt-internal-refactoring.sandbox-hint", //$NON-NLS-1$
		"jdt-internal-ui-browser-fixes.sandbox-hint", //$NON-NLS-1$
		"jdt-internal-ui-javadoc-fixes.sandbox-hint", //$NON-NLS-1$
		"jface-deprecations.sandbox-hint", //$NON-NLS-1$
		"lambda-simplification.sandbox-hint", //$NON-NLS-1$
		"logical-simplification.sandbox-hint", //$NON-NLS-1$
		"modernize-java14.sandbox-hint", //$NON-NLS-1$
		"null-safety.sandbox-hint", //$NON-NLS-1$
		"string-object-compare.sandbox-hint", //$NON-NLS-1$
		"type-safety.sandbox-hint" //$NON-NLS-1$
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
	 * Describes a validation problem found in a bundled hint file resource.
	 *
	 * @param resourceName the {@code .sandbox-hint} resource name, or {@code <registry>} for list-level errors
	 * @param message      human-readable problem description
	 */
	public record HintFileValidationProblem(String resourceName, String message) {
		// value object
	}

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
	 * {@code <!id: ...>} so that {@code <!include:} directives work
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
	 * Returns bundled pattern libraries that are shipped but disabled by default.
	 *
	 * @return array of disabled bundled library resource names
	 */
	public static String[] getDisabledBundledLibraryNames() {
		return DISABLED_BUNDLED_LIBRARIES.clone();
	}

	/**
	 * Returns all bundled pattern library resource names that should be validated.
	 *
	 * <p>This includes the active libraries returned by {@link #getBundledLibraryNames()}
	 * and additional disabled libraries that are intentionally not loaded by
	 * {@link #loadBundledLibraries(ClassLoader)}.</p>
	 *
	 * @return array of all bundled library resource names to validate
	 */
	public static String[] getAllBundledLibraryNames() {
		String[] result = new String[BUNDLED_LIBRARIES.length + DISABLED_BUNDLED_LIBRARIES.length];
		System.arraycopy(BUNDLED_LIBRARIES, 0, result, 0, BUNDLED_LIBRARIES.length);
		System.arraycopy(DISABLED_BUNDLED_LIBRARIES, 0, result, BUNDLED_LIBRARIES.length,
				DISABLED_BUNDLED_LIBRARIES.length);
		return result;
	}

	/**
	 * Attempts to load all active bundled pattern libraries from the classpath.
	 * Disabled bundled libraries are intentionally not registered by this method;
	 * they are covered by {@link #validateBundledLibraries(ClassLoader)} instead.
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
			String id = toLibraryId(libraryName);
			String resourcePath = BUNDLED_RESOURCE_PREFIX + libraryName;
			try {
				if (loadFromClasspath(id, resourcePath, classLoader)) {
					loaded.add(id);
				}
			} catch (HintParseException | IOException e) {
				LOGGER.log(Level.WARNING,
						"Failed to load bundled hint library: " + libraryName, e); //$NON-NLS-1$
			}
		}
		return loaded;
	}

	/**
	 * Validates all active and disabled bundled hint file resources.
	 *
	 * <p>The validation intentionally does not register disabled libraries in this store.
	 * It only parses them and checks invariants that should fail PR builds when broken.</p>
	 *
	 * @param classLoader the class loader to use for loading resources
	 * @return validation problems; empty when all bundled resources are valid
	 */
	public List<HintFileValidationProblem> validateBundledLibraries(ClassLoader classLoader) {
		List<HintFileValidationProblem> problems = new ArrayList<>();
		Map<String, HintFile> parsedByResource = new LinkedHashMap<>();
		Map<String, String> resourceByDeclaredId = new LinkedHashMap<>();
		Set<String> resourceNames = new HashSet<>();
		Set<String> registryIds = new HashSet<>();

		for (String libraryName : getAllBundledLibraryNames()) {
			String registryId = toLibraryId(libraryName);
			if (!resourceNames.add(libraryName)) {
				problems.add(new HintFileValidationProblem("<registry>", //$NON-NLS-1$
						"Duplicate bundled hint resource: " + libraryName)); //$NON-NLS-1$
			}
			if (!registryIds.add(registryId)) {
				problems.add(new HintFileValidationProblem(libraryName,
						"Duplicate bundled hint registry ID: " + registryId)); //$NON-NLS-1$
			}

			String resourcePath = BUNDLED_RESOURCE_PREFIX + libraryName;
			try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
				if (is == null) {
					problems.add(new HintFileValidationProblem(libraryName,
							"Bundled hint resource not found: " + resourcePath)); //$NON-NLS-1$
					continue;
				}
				try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
					HintFile hintFile = parser.parse(reader);
					parsedByResource.put(libraryName, hintFile);
					validateHintFileId(libraryName, registryId, hintFile, resourceByDeclaredId, problems);
					validateRuleIds(libraryName, hintFile, problems);
				}
			} catch (HintParseException e) {
				problems.add(new HintFileValidationProblem(libraryName,
						"Parse error: " + e.getMessage())); //$NON-NLS-1$
			} catch (IOException e) {
				problems.add(new HintFileValidationProblem(libraryName,
						"I/O error: " + e.getMessage())); //$NON-NLS-1$
			}
		}

		validateIncludes(parsedByResource, resourceByDeclaredId.keySet(), problems);
		return Collections.unmodifiableList(problems);
	}

	private static String toLibraryId(String libraryName) {
		return libraryName.replace(SANDBOX_HINT_EXTENSION, ""); //$NON-NLS-1$
	}

	private static void validateHintFileId(String libraryName, String registryId, HintFile hintFile,
			Map<String, String> resourceByDeclaredId, List<HintFileValidationProblem> problems) {
		String declaredId = hintFile.getId();
		String effectiveId = declaredId == null || declaredId.isBlank() ? registryId : declaredId;
		String previousResource = resourceByDeclaredId.putIfAbsent(effectiveId, libraryName);
		if (previousResource != null) {
			problems.add(new HintFileValidationProblem(libraryName,
					"Duplicate hint file ID '" + effectiveId //$NON-NLS-1$
							+ "' also used by " + previousResource)); //$NON-NLS-1$
		}
	}

	private static void validateRuleIds(String libraryName, HintFile hintFile,
			List<HintFileValidationProblem> problems) {
		Set<String> ruleIds = new HashSet<>();
		for (TransformationRule rule : hintFile.getRules()) {
			String ruleId = rule.getRuleId();
			if (ruleId != null && !ruleId.isBlank() && !ruleIds.add(ruleId)) {
				problems.add(new HintFileValidationProblem(libraryName,
						"Duplicate rule ID '" + ruleId + "'")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	private static void validateIncludes(Map<String, HintFile> parsedByResource, Set<String> declaredIds,
			List<HintFileValidationProblem> problems) {
		Set<String> availableIds = new HashSet<>(declaredIds);
		for (String libraryName : parsedByResource.keySet()) {
			availableIds.add(toLibraryId(libraryName));
		}
		for (Map.Entry<String, HintFile> entry : parsedByResource.entrySet()) {
			for (String includeId : entry.getValue().getIncludes()) {
				if (!availableIds.contains(includeId)) {
					problems.add(new HintFileValidationProblem(entry.getKey(),
							"Unresolved include: " + includeId)); //$NON-NLS-1$
				}
			}
		}
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
		String id = INFERRED_PREFIX + sourceCommit;
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
			if (entry.getKey().startsWith(INFERRED_PREFIX)) {
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
			String newId = hintFileId.replace(INFERRED_PREFIX, "manual:"); //$NON-NLS-1$
			hintFile.setId(newId);
			hintFiles.put(newId, hintFile);
			hintFilesByDeclaredId.put(newId, hintFile);
		}
	}

	/**
	 * Registers inferred rules from a list of {@link CommitEvaluation} results.
	 *
	 * <p>Each evaluation with a non-null, non-blank {@code dslRule} and a valid
	 * DSL parse result is converted into a {@link HintFile} and registered with
	 * the tag {@code "ai-inferred"}. Evaluations without valid rules are
	 * silently skipped.</p>
	 *
	 * @param evaluations the list of commit evaluations from AI inference
	 * @param source      a label identifying the source of these evaluations
	 *                    (e.g., repository URL or branch name)
	 * @return list of IDs of successfully registered hint files
	 * @since 1.3.2
	 */
	public List<String> registerInferredRules(List<CommitEvaluation> evaluations, String source) {
		List<String> registered = new ArrayList<>();
		if (evaluations == null) {
			return registered;
		}
		for (CommitEvaluation eval : evaluations) {
			if (eval == null || !eval.relevant()) {
				continue;
			}
			String dslRule = eval.dslRule();
			if (dslRule == null || dslRule.isBlank()) {
				continue;
			}
			try {
				HintFile hintFile = parser.parse(dslRule);
				String normalizedSource = (source != null && !source.isBlank()) ? source : "unknown"; //$NON-NLS-1$
				String commitId = eval.commitHash();
				if (commitId == null || commitId.isBlank()) {
					commitId = normalizedSource;
				}
				String id = INFERRED_PREFIX + commitId;
				hintFile.setId(id);
				hintFile.setTags(List.of("ai-inferred", normalizedSource, commitId)); //$NON-NLS-1$
				hintFile.setSeverity(Severity.INFO);
				if (hintFile.getDescription() == null) {
					hintFile.setDescription(eval.summary());
				}
				hintFiles.put(id, hintFile);
				hintFilesByDeclaredId.put(id, hintFile);
				registered.add(id);
			} catch (HintParseException e) {
				LOGGER.log(Level.WARNING,
						"Failed to parse DSL rule from evaluation: " + eval.commitHash(), e); //$NON-NLS-1$
			} catch (RuntimeException e) {
				LOGGER.log(Level.WARNING,
						"Unexpected error while parsing DSL rule from evaluation: " + eval.commitHash(), e); //$NON-NLS-1$
			}
		}
		return registered;
	}

	// ------------------------------------------------------------
	// Persistence
	// ------------------------------------------------------------

	/**
	 * Saves all AI-inferred hint files to a {@code .hints/} directory.
	 *
	 * <p>Each inferred hint file is serialized to the {@code .sandbox-hint}
	 * DSL format and written to
	 * {@code <directory>/.hints/ai-inferred-<sanitized-id>.sandbox-hint}.</p>
	 *
	 * <p>The {@code .hints/} subdirectory is created if it does not exist.
	 * Existing files with the same name are overwritten.</p>
	 *
	 * @param baseDirectory the project root directory
	 * @return list of paths that were written
	 * @throws IOException if a write error occurs
	 * @since 1.3.2
	 */
	public List<Path> saveInferredHintFiles(Path baseDirectory) throws IOException {
		List<Path> written = new ArrayList<>();
		Path hintsDir = baseDirectory.resolve(HINTS_DIRECTORY);
		List<HintFile> inferred = getInferredHintFiles();
		if (inferred.isEmpty()) {
			return written;
		}
		Files.createDirectories(hintsDir);
		HintFileSerializer serializer = new HintFileSerializer();
		for (HintFile hf : inferred) {
			String hfId = hf.getId();
			// Strip the "inferred:" prefix to avoid doubled prefix on reload
			if (hfId != null && hfId.startsWith(INFERRED_PREFIX)) {
				hfId = hfId.substring(INFERRED_PREFIX.length());
			}
			String safeName = sanitizeFileName(hfId);
			Path target = hintsDir.resolve(AI_INFERRED_FILE_PREFIX + safeName + SANDBOX_HINT_EXTENSION);
			String content = serializer.serialize(hf);
			Files.writeString(target, content, StandardCharsets.UTF_8);
			written.add(target);
		}
		return written;
	}

	/**
	 * Loads all {@code ai-inferred-*.sandbox-hint} files from the
	 * {@code .hints/} directory under the given base directory.
	 *
	 * <p>Each loaded file is registered with the {@code "inferred:"} prefix
	 * so it appears in {@link #getInferredHintFiles()}.</p>
	 *
	 * @param baseDirectory the project root directory
	 * @return list of IDs of successfully loaded hint files
	 * @since 1.3.2
	 */
	public List<String> loadInferredHintFiles(Path baseDirectory) {
		List<String> loaded = new ArrayList<>();
		Path hintsDir = baseDirectory.resolve(HINTS_DIRECTORY);
		if (!Files.isDirectory(hintsDir)) {
			return loaded;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(hintsDir,
				AI_INFERRED_FILE_PREFIX + "*" + SANDBOX_HINT_EXTENSION)) { //$NON-NLS-1$
			for (Path file : stream) {
				try {
					String content = Files.readString(file, StandardCharsets.UTF_8);
					HintFile hintFile = parser.parse(content);
					String declaredId = hintFile.getId();
					if (declaredId == null) {
						Path fileNamePath = file.getFileName();
						if (fileNamePath == null) {
							continue;
						}
						String fileName = fileNamePath.toString();
						String baseName = fileName
								.replace(AI_INFERRED_FILE_PREFIX, "") //$NON-NLS-1$
								.replace(SANDBOX_HINT_EXTENSION, ""); //$NON-NLS-1$
						declaredId = INFERRED_PREFIX + baseName;
						hintFile.setId(declaredId);
					}
					hintFiles.put(declaredId, hintFile);
					indexByDeclaredId(hintFile);
					loaded.add(declaredId);
				} catch (HintParseException | IOException | RuntimeException e) {
					LOGGER.log(Level.WARNING,
							"Failed to load inferred hint file: " + file, e); //$NON-NLS-1$
				}
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING,
					"Failed to scan .hints directory: " + hintsDir, e); //$NON-NLS-1$
		}
		return loaded;
	}

	// ------------------------------------------------------------
	// Internal helpers
	// ------------------------------------------------------------

	/**
	 * Sanitizes a hint file ID for use as a file name.
	 * Replaces characters that are unsafe in file names.
	 */
	private static String sanitizeFileName(String id) {
		if (id == null) {
			return "unknown"; //$NON-NLS-1$
		}
		return id.replaceAll("[^a-zA-Z0-9._-]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
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
