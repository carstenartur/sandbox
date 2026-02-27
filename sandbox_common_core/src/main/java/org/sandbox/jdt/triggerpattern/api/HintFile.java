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
import java.util.List;
import java.util.Locale;

/**
 * Represents a parsed {@code .sandbox-hint} file containing transformation rules.
 * 
 * <p>A hint file contains metadata and a list of {@link TransformationRule} objects.
 * Each rule defines a source pattern, optional guard, and rewrite alternatives.</p>
 * 
 * <p>Example {@code .sandbox-hint} file:</p>
 * <pre>
 * &lt;!id: encoding.utf8&gt;
 * &lt;!description: Replace String encoding literals with StandardCharsets&gt;
 * &lt;!severity: warning&gt;
 * &lt;!minJavaVersion: 7&gt;
 *
 * new String($bytes, "UTF-8")
 * =&gt; new String($bytes, java.nio.charset.StandardCharsets.UTF_8)
 * ;;
 * </pre>
 * 
 * @since 1.3.2
 */
public final class HintFile {
	
	private String id;
	private String description;
	private Severity severity;
	private int minJavaVersion;
	private List<String> tags;
	private final List<TransformationRule> rules;
	private final List<String> includes;
	private boolean caseInsensitive;
	private List<String> suppressWarnings;
	
	/**
	 * Creates a new empty hint file.
	 */
	public HintFile() {
		this.tags = new ArrayList<>();
		this.rules = new ArrayList<>();
		this.includes = new ArrayList<>();
		this.severity = Severity.INFO;
		this.suppressWarnings = new ArrayList<>();
	}
	
	/**
	 * Returns the hint file ID.
	 * 
	 * @return the ID, or {@code null} if not set
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * Sets the hint file ID.
	 * 
	 * @param id the ID
	 */
	public void setId(String id) {
		this.id = id;
	}
	
	/**
	 * Returns the hint file description.
	 * 
	 * @return the description, or {@code null} if not set
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * Sets the hint file description.
	 * 
	 * @param description the description
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * Returns the severity level.
	 * 
	 * @return the severity enum value
	 */
	public Severity getSeverity() {
		return severity;
	}
	
	/**
	 * Returns the severity level as a lowercase string.
	 * 
	 * @return the severity name in lowercase (e.g. {@code "info"}, {@code "warning"})
	 */
	public String getSeverityAsString() {
		return severity.name().toLowerCase();
	}
	
	/**
	 * Sets the severity level from a string value.
	 * Parsing is case-insensitive; unrecognized values fall back to {@link Severity#INFO}.
	 * 
	 * @param severity the severity string ({@code "info"}, {@code "warning"}, {@code "error"}, or {@code "hint"})
	 */
	public void setSeverity(String severity) {
		if (severity == null) {
			this.severity = Severity.INFO;
			return;
		}
		try {
			this.severity = Severity.valueOf(severity.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			this.severity = Severity.INFO;
		}
	}
	
	/**
	 * Sets the severity level.
	 * 
	 * @param severity the severity enum value
	 */
	public void setSeverity(Severity severity) {
		this.severity = severity;
	}
	
	/**
	 * Returns the minimum Java version required for this hint file.
	 * 
	 * @return the minimum Java version, or 0 if not set
	 */
	public int getMinJavaVersion() {
		return minJavaVersion;
	}
	
	/**
	 * Sets the minimum Java version.
	 * 
	 * @param minJavaVersion the minimum Java version
	 */
	public void setMinJavaVersion(int minJavaVersion) {
		this.minJavaVersion = minJavaVersion;
	}
	
	/**
	 * Returns the tags for this hint file.
	 * 
	 * @return unmodifiable list of tags
	 */
	public List<String> getTags() {
		return Collections.unmodifiableList(tags);
	}
	
	/**
	 * Sets the tags.
	 * 
	 * @param tags the tags
	 */
	public void setTags(List<String> tags) {
		this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
	}
	
	/**
	 * Returns the transformation rules.
	 * 
	 * @return unmodifiable list of rules
	 */
	public List<TransformationRule> getRules() {
		return Collections.unmodifiableList(rules);
	}
	
	/**
	 * Adds a transformation rule.
	 * 
	 * @param rule the rule to add
	 */
	public void addRule(TransformationRule rule) {
		rules.add(rule);
	}
	
	/**
	 * Returns the list of included hint file IDs for pattern composition.
	 * 
	 * <p>When a hint file includes another hint file by ID, all rules from
	 * the included file are also applied when this hint file is processed.</p>
	 * 
	 * @return unmodifiable list of included hint file IDs
	 * @since 1.3.4
	 */
	public List<String> getIncludes() {
		return Collections.unmodifiableList(includes);
	}
	
	/**
	 * Adds an include reference to another hint file by ID.
	 * 
	 * @param hintFileId the ID of the hint file to include
	 * @since 1.3.4
	 */
	public void addInclude(String hintFileId) {
		if (hintFileId != null && !hintFileId.isBlank()) {
			includes.add(hintFileId.trim());
		}
	}

	/**
	 * Returns whether string literal matching should be case-insensitive.
	 *
	 * <p>When enabled, pattern strings like {@code "UTF-8"} will also match
	 * {@code "utf-8"}, {@code "Utf-8"}, etc.</p>
	 *
	 * @return {@code true} if case-insensitive matching is enabled
	 * @since 1.3.8
	 */
	public boolean isCaseInsensitive() {
		return caseInsensitive;
	}

	/**
	 * Sets whether string literal matching should be case-insensitive.
	 *
	 * @param caseInsensitive {@code true} to enable case-insensitive matching
	 * @since 1.3.8
	 */
	public void setCaseInsensitive(boolean caseInsensitive) {
		this.caseInsensitive = caseInsensitive;
	}

	/**
	 * Returns the suppress warnings keys for this hint file.
	 *
	 * <p>When a key is listed here, hints from this file will not be reported
	 * for code that has a {@code @SuppressWarnings} annotation containing
	 * that key.</p>
	 *
	 * @return unmodifiable list of suppress warnings keys
	 * @since 1.4.0
	 */
	public List<String> getSuppressWarnings() {
		return Collections.unmodifiableList(suppressWarnings);
	}

	/**
	 * Sets the suppress warnings keys.
	 *
	 * @param suppressWarnings the suppress warnings keys
	 * @since 1.4.0
	 */
	public void setSuppressWarnings(List<String> suppressWarnings) {
		this.suppressWarnings = suppressWarnings != null ? new ArrayList<>(suppressWarnings) : new ArrayList<>();
	}

	/**
	 * Adds a suppress warnings key.
	 *
	 * @param key the suppress warnings key
	 * @since 1.4.0
	 */
	public void addSuppressWarnings(String key) {
		if (key != null && !key.isBlank()) {
			this.suppressWarnings.add(key.trim());
		}
	}
}
