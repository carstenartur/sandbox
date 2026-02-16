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
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Represents import directives for a rewrite alternative.
 * 
 * <p>Import directives specify which imports should be added or removed
 * when a rewrite alternative is applied. Import information is automatically
 * inferred from fully qualified names (FQNs) in source and replacement patterns.</p>
 * 
 * <h2>FQN-based inference</h2>
 * <p>The preferred way to specify imports is by using FQNs directly in the
 * source and replacement patterns. The engine automatically infers:</p>
 * <ul>
 *   <li>{@code addImport}: FQN types that appear in the replacement pattern</li>
 *   <li>{@code removeImport}: FQN types that appear in the source but not in the replacement</li>
 *   <li>{@code replaceStaticImport}: When both source and replacement contain
 *       {@code pkg.Type.method()} patterns with different types</li>
 * </ul>
 * 
 * <h2>Example (FQN-based)</h2>
 * <pre>
 * org.junit.Assert.assertEquals($expected, $actual)
 * =&gt; org.junit.jupiter.api.Assertions.assertEquals($expected, $actual)
 * ;;
 * </pre>
 * <p>This automatically infers:</p>
 * <ul>
 *   <li>{@code addImport org.junit.jupiter.api.Assertions}</li>
 *   <li>{@code removeImport org.junit.Assert}</li>
 *   <li>{@code replaceStaticImport org.junit.Assert org.junit.jupiter.api.Assertions}</li>
 * </ul>
 * 
 * @since 1.3.2
 */
public final class ImportDirective {
	
	/**
	 * Compiled regex for detecting fully qualified type names in patterns.
	 * Matches patterns like {@code java.util.Objects} or {@code org.junit.Assert}.
	 */
	private static final java.util.regex.Pattern FQN_PATTERN = java.util.regex.Pattern.compile(
			"\\b([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*(\\.[A-Z][A-Za-z0-9_]*))\\b"); //$NON-NLS-1$

	/**
	 * Compiled regex for detecting FQN.method patterns (e.g., {@code org.junit.Assert.assertEquals}).
	 * 
	 * <p>Capture groups:</p>
	 * <ul>
	 *   <li>Group 1: full match including method (e.g., {@code org.junit.Assert.assertEquals})</li>
	 *   <li>Group 2: type part only (e.g., {@code org.junit.Assert})</li>
	 *   <li>The method name is the last segment after the type part's last dot</li>
	 * </ul>
	 */
	private static final java.util.regex.Pattern FQN_METHOD_PATTERN = java.util.regex.Pattern.compile(
			"\\b(([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*(\\.[A-Z][A-Za-z0-9_]*))\\.[a-zA-Z][A-Za-z0-9_]*)\\b"); //$NON-NLS-1$

	private final List<String> addImports;
	private final List<String> removeImports;
	private final List<String> addStaticImports;
	private final List<String> removeStaticImports;
	private final Map<String, String> replaceStaticImports;
	
	/**
	 * Creates an empty import directive.
	 */
	public ImportDirective() {
		this.addImports = new ArrayList<>();
		this.removeImports = new ArrayList<>();
		this.addStaticImports = new ArrayList<>();
		this.removeStaticImports = new ArrayList<>();
		this.replaceStaticImports = new LinkedHashMap<>();
	}
	
	/**
	 * Creates an import directive with the given imports.
	 * 
	 * @param addImports imports to add
	 * @param removeImports imports to remove
	 * @param addStaticImports static imports to add
	 * @param removeStaticImports static imports to remove
	 */
	public ImportDirective(List<String> addImports, List<String> removeImports,
			List<String> addStaticImports, List<String> removeStaticImports) {
		this.addImports = addImports != null ? new ArrayList<>(addImports) : new ArrayList<>();
		this.removeImports = removeImports != null ? new ArrayList<>(removeImports) : new ArrayList<>();
		this.addStaticImports = addStaticImports != null ? new ArrayList<>(addStaticImports) : new ArrayList<>();
		this.removeStaticImports = removeStaticImports != null ? new ArrayList<>(removeStaticImports) : new ArrayList<>();
		this.replaceStaticImports = new LinkedHashMap<>();
	}
	
	/**
	 * Adds a regular import.
	 * 
	 * @param qualifiedName the fully qualified type name (e.g., {@code "java.util.Objects"})
	 */
	public void addImport(String qualifiedName) {
		addImports.add(qualifiedName);
	}
	
	/**
	 * Adds a regular import to be removed.
	 * 
	 * @param qualifiedName the fully qualified type name to remove
	 */
	public void removeImport(String qualifiedName) {
		removeImports.add(qualifiedName);
	}
	
	/**
	 * Adds a static import.
	 * 
	 * @param qualifiedName the fully qualified static member name
	 *        (e.g., {@code "java.util.Objects.requireNonNull"})
	 */
	public void addStaticImport(String qualifiedName) {
		addStaticImports.add(qualifiedName);
	}
	
	/**
	 * Adds a static import to be removed.
	 * 
	 * @param qualifiedName the fully qualified static member name to remove
	 */
	public void removeStaticImport(String qualifiedName) {
		removeStaticImports.add(qualifiedName);
	}
	
	/**
	 * Adds a static import replacement mapping.
	 * 
	 * <p>This replaces all static imports from the old type with static imports
	 * from the new type. Works for both wildcard imports
	 * ({@code import static org.junit.Assert.*}) and specific member imports
	 * ({@code import static org.junit.Assert.assertEquals}).</p>
	 * 
	 * <p>DSL syntax: {@code replaceStaticImport org.junit.Assert org.junit.jupiter.api.Assertions}</p>
	 * 
	 * @param oldType the fully qualified old type name
	 * @param newType the fully qualified new type name
	 */
	public void replaceStaticImport(String oldType, String newType) {
		replaceStaticImports.put(oldType, newType);
	}
	
	/**
	 * Returns the list of imports to add.
	 * 
	 * @return unmodifiable list of imports to add
	 */
	public List<String> getAddImports() {
		return Collections.unmodifiableList(addImports);
	}
	
	/**
	 * Returns the list of imports to remove.
	 * 
	 * @return unmodifiable list of imports to remove
	 */
	public List<String> getRemoveImports() {
		return Collections.unmodifiableList(removeImports);
	}
	
	/**
	 * Returns the list of static imports to add.
	 * 
	 * @return unmodifiable list of static imports to add
	 */
	public List<String> getAddStaticImports() {
		return Collections.unmodifiableList(addStaticImports);
	}
	
	/**
	 * Returns the list of static imports to remove.
	 * 
	 * @return unmodifiable list of static imports to remove
	 */
	public List<String> getRemoveStaticImports() {
		return Collections.unmodifiableList(removeStaticImports);
	}
	
	/**
	 * Returns the static import replacement mappings.
	 * 
	 * <p>Each entry maps the old fully qualified type name to the new one.
	 * For example: {@code org.junit.Assert → org.junit.jupiter.api.Assertions}.</p>
	 * 
	 * @return unmodifiable map of old type → new type
	 */
	public Map<String, String> getReplaceStaticImports() {
		return Collections.unmodifiableMap(replaceStaticImports);
	}
	
	/**
	 * Returns {@code true} if there are no import directives.
	 * 
	 * @return {@code true} if empty
	 */
	public boolean isEmpty() {
		return addImports.isEmpty() && removeImports.isEmpty()
				&& addStaticImports.isEmpty() && removeStaticImports.isEmpty()
				&& replaceStaticImports.isEmpty();
	}
	
	/**
	 * Detects imports from fully qualified type references in a replacement pattern.
	 * 
	 * <p>Recognizes patterns like {@code java.util.Objects.equals($x, $y)} and extracts
	 * {@code java.util.Objects} as an import to add. Only recognizes standard Java
	 * package patterns (starting with a lowercase segment followed by more segments).</p>
	 * 
	 * @param replacementPattern the replacement pattern text
	 * @return an {@link ImportDirective} with detected imports
	 */
	public static ImportDirective detectFromPattern(String replacementPattern) {
		ImportDirective directive = new ImportDirective();
		if (replacementPattern == null || replacementPattern.isEmpty()) {
			return directive;
		}
		
		Set<String> detected = new HashSet<>();
		Matcher matcher = FQN_PATTERN.matcher(replacementPattern);
		
		while (matcher.find()) {
			String fqn = matcher.group(1);
			// Skip placeholders
			if (fqn.contains("$")) { //$NON-NLS-1$
				continue;
			}
			// Only add if it looks like a real qualified name (has at least package.Class)
			int lastDot = fqn.lastIndexOf('.');
			if (lastDot > 0) {
				String className = fqn.substring(lastDot + 1);
				if (Character.isUpperCase(className.charAt(0))) {
					detected.add(fqn);
				}
			}
		}
		
		for (String fqn : detected) {
			directive.addImport(fqn);
		}
		
		return directive;
	}
	
	/**
	 * Detects types that appear as FQNs in the source pattern but not in any replacement pattern.
	 * These are candidates for {@code removeImport} directives.
	 * 
	 * <p>Only works when the source pattern contains fully qualified names (e.g.,
	 * {@code java.io.FileReader}). Short-form type names cannot be resolved without
	 * additional context.</p>
	 * 
	 * @param sourcePattern the source pattern text
	 * @param alternatives the list of replacement alternatives
	 * @return an {@link ImportDirective} with detected removeImport candidates
	 */
	public static ImportDirective detectRemovedTypes(String sourcePattern, List<RewriteAlternative> alternatives) {
		ImportDirective directive = new ImportDirective();
		if (sourcePattern == null || sourcePattern.isEmpty() || alternatives == null || alternatives.isEmpty()) {
			return directive;
		}
		
		Set<String> sourceFqns = extractFqns(sourcePattern);
		if (sourceFqns.isEmpty()) {
			return directive;
		}
		
		// Collect all FQNs from all replacement patterns
		Set<String> replacementFqns = new HashSet<>();
		for (RewriteAlternative alt : alternatives) {
			replacementFqns.addAll(extractFqns(alt.replacementPattern()));
		}
		
		// Types in source but not in any replacement are candidates for removeImport
		for (String fqn : sourceFqns) {
			if (!replacementFqns.contains(fqn)) {
				directive.removeImport(fqn);
			}
		}
		
		return directive;
	}
	
	/**
	 * Infers all import directives from FQN patterns in source and replacement.
	 * 
	 * <p>This is the primary method for the FQN-based import inference.
	 * It extracts FQN types from both patterns and automatically derives:</p>
	 * <ul>
	 *   <li>{@code addImport}: FQN types in replacement that are not in source</li>
	 *   <li>{@code removeImport}: FQN types in source that are not in replacement</li>
	 *   <li>{@code replaceStaticImport}: When source and replacement have FQN.method()
	 *       patterns with the same method name but different types</li>
	 * </ul>
	 * 
	 * @param sourcePattern the source pattern text
	 * @param replacementPatterns the replacement pattern texts
	 * @return an {@link ImportDirective} with all inferred import directives
	 */
	public static ImportDirective inferFromFqnPatterns(String sourcePattern, List<String> replacementPatterns) {
		ImportDirective directive = new ImportDirective();
		if (replacementPatterns == null || replacementPatterns.isEmpty()) {
			return directive;
		}
		
		Set<String> sourceFqns = extractFqns(sourcePattern);
		Map<String, String> sourceFqnMethods = extractFqnMethods(sourcePattern);
		
		Set<String> replacementFqns = new HashSet<>();
		Map<String, String> replacementFqnMethods = new LinkedHashMap<>();
		for (String replacement : replacementPatterns) {
			replacementFqns.addAll(extractFqns(replacement));
			replacementFqnMethods.putAll(extractFqnMethods(replacement));
		}
		
		// addImport: FQN types in replacement (regardless of source)
		for (String fqn : replacementFqns) {
			directive.addImport(fqn);
		}
		
		// removeImport: FQN types in source but not in replacement
		for (String fqn : sourceFqns) {
			if (!replacementFqns.contains(fqn)) {
				directive.removeImport(fqn);
			}
		}
		
		// replaceStaticImport: Detect when source and replacement have FQN.method()
		// patterns with the same method name but different types
		// e.g., org.junit.Assert.assertEquals → org.junit.jupiter.api.Assertions.assertEquals
		for (Map.Entry<String, String> sourceEntry : sourceFqnMethods.entrySet()) {
			String sourceMethod = sourceEntry.getKey(); // e.g., "assertEquals"
			String sourceType = sourceEntry.getValue(); // e.g., "org.junit.Assert"
			for (Map.Entry<String, String> replEntry : replacementFqnMethods.entrySet()) {
				String replMethod = replEntry.getKey();
				String replType = replEntry.getValue();
				if (sourceMethod.equals(replMethod) && !sourceType.equals(replType)) {
					directive.replaceStaticImport(sourceType, replType);
				}
			}
		}
		
		return directive;
	}
	
	/**
	 * Extracts FQN.method patterns from a pattern string.
	 * 
	 * <p>Returns a map of method name to FQN type. For example,
	 * for {@code "org.junit.Assert.assertEquals($a, $b)"} returns
	 * {@code {"assertEquals" -> "org.junit.Assert"}}.</p>
	 * 
	 * @param pattern the pattern string to analyze
	 * @return map of method name to FQN type
	 */
	private static Map<String, String> extractFqnMethods(String pattern) {
		Map<String, String> result = new LinkedHashMap<>();
		if (pattern == null || pattern.isEmpty()) {
			return result;
		}
		Matcher matcher = FQN_METHOD_PATTERN.matcher(pattern);
		while (matcher.find()) {
			String fullMatch = matcher.group(1); // e.g., "org.junit.Assert.assertEquals"
			if (fullMatch.contains("$")) { //$NON-NLS-1$
				continue;
			}
			String typePart = matcher.group(2); // e.g., "org.junit.Assert"
			int lastDot = fullMatch.lastIndexOf('.');
			String methodPart = fullMatch.substring(lastDot + 1); // e.g., "assertEquals"
			// Only include if the type part is a real FQN (last segment starts with uppercase)
			int typeLastDot = typePart.lastIndexOf('.');
			if (typeLastDot > 0 && Character.isUpperCase(typePart.substring(typeLastDot + 1).charAt(0))) {
				result.put(methodPart, typePart);
			}
		}
		return result;
	}
	/**
	 * Extracts fully qualified type names from a pattern string.
	 * 
	 * <p>Recognizes patterns like {@code java.util.Objects} or
	 * {@code java.nio.charset.StandardCharsets} — names that start with
	 * lowercase package segments and end with an uppercase class name.</p>
	 * 
	 * @param pattern the pattern string to analyze
	 * @return set of fully qualified type names found in the pattern
	 */
	private static Set<String> extractFqns(String pattern) {
		Set<String> fqns = new HashSet<>();
		if (pattern == null || pattern.isEmpty()) {
			return fqns;
		}
		java.util.regex.Matcher matcher = FQN_PATTERN.matcher(pattern);
		while (matcher.find()) {
			String fqn = matcher.group(1);
			if (fqn.contains("$")) { //$NON-NLS-1$
				continue;
			}
			int lastDot = fqn.lastIndexOf('.');
			if (lastDot > 0 && Character.isUpperCase(fqn.substring(lastDot + 1).charAt(0))) {
				fqns.add(fqn);
			}
		}
		return fqns;
	}
	
	/**
	 * Merges another directive into this one.
	 * 
	 * @param other the directive to merge
	 */
	public void merge(ImportDirective other) {
		if (other == null) {
			return;
		}
		addImports.addAll(other.addImports);
		removeImports.addAll(other.removeImports);
		addStaticImports.addAll(other.addStaticImports);
		removeStaticImports.addAll(other.removeStaticImports);
		replaceStaticImports.putAll(other.replaceStaticImports);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ImportDirective["); //$NON-NLS-1$
		if (!addImports.isEmpty()) {
			sb.append("add=").append(addImports); //$NON-NLS-1$
		}
		if (!removeImports.isEmpty()) {
			sb.append(", remove=").append(removeImports); //$NON-NLS-1$
		}
		if (!addStaticImports.isEmpty()) {
			sb.append(", addStatic=").append(addStaticImports); //$NON-NLS-1$
		}
		if (!removeStaticImports.isEmpty()) {
			sb.append(", removeStatic=").append(removeStaticImports); //$NON-NLS-1$
		}
		if (!replaceStaticImports.isEmpty()) {
			sb.append(", replaceStatic=").append(replaceStaticImports); //$NON-NLS-1$
		}
		sb.append(']');
		return sb.toString();
	}
}
