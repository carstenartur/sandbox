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
	private String severity;
	private int minJavaVersion;
	private List<String> tags;
	private final List<TransformationRule> rules;
	
	/**
	 * Creates a new empty hint file.
	 */
	public HintFile() {
		this.tags = new ArrayList<>();
		this.rules = new ArrayList<>();
		this.severity = "info"; //$NON-NLS-1$
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
	 * @return the severity ({@code "info"}, {@code "warning"}, or {@code "error"})
	 */
	public String getSeverity() {
		return severity;
	}
	
	/**
	 * Sets the severity level.
	 * 
	 * @param severity the severity ({@code "info"}, {@code "warning"}, or {@code "error"})
	 */
	public void setSeverity(String severity) {
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
}
