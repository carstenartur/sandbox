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
 * when a rewrite alternative is applied. They also support automatic import
 * detection from fully qualified type references in replacement patterns.</p>
 * 
 * <h2>DSL Syntax</h2>
 * <pre>
 * =&gt; replacement_pattern
 *    addImport java.util.Objects
 *    addImport java.nio.charset.StandardCharsets
 *    removeImport java.io.UnsupportedEncodingException
 *    addStaticImport java.util.Objects.requireNonNull
 *    replaceStaticImport org.junit.Assert org.junit.jupiter.api.Assertions
 * </pre>
 * 
 * <h2>Auto-detection</h2>
 * <p>When a replacement pattern contains a fully qualified name like
 * {@code java.util.Objects.equals($x, $y)}, the import for {@code java.util.Objects}
 * is automatically detected.</p>
 * 
 * @since 1.3.2
 */
public final class ImportDirective {
	
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
		// Pattern: qualified name like com.example.ClassName or java.util.Objects
		// Must start with lowercase (package), contain dots, and end with an uppercase class name
		java.util.regex.Pattern fqnPattern = java.util.regex.Pattern.compile(
				"\\b([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*(\\.[A-Z][A-Za-z0-9_]*))\\b"); //$NON-NLS-1$
		Matcher matcher = fqnPattern.matcher(replacementPattern);
		
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
