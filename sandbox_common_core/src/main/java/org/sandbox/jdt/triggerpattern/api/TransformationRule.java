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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a transformation rule consisting of a source pattern, an optional guard,
 * and a list of rewrite alternatives.
 * 
 * <p>A rule matches source code using its {@link #sourcePattern()} and optional
 * {@link #sourceGuard()}. When a match is found, the rewrite alternatives are
 * evaluated in order. The first alternative whose guard evaluates to {@code true}
 * (or an unconditional "otherwise" alternative) is applied.</p>
 * 
 * <p>If no alternatives are provided, the rule acts as a hint-only rule
 * (warning/inspection without rewrite).</p>
 * 
 * <p>Optionally, a rule may have import directives ({@link ImportDirective}) that
 * specify imports to add or remove when the rule is applied. Import directives
 * are automatically inferred from fully qualified names (FQNs) in the source
 * and replacement patterns.</p>
 * 
 * <p>Example DSL syntax (FQN-based):</p>
 * <pre>
 * new java.io.FileReader($path) :: sourceVersionGE(11)
 * =&gt; new java.io.FileReader($path, java.nio.charset.StandardCharsets.UTF_8) :: sourceVersionGE(11)
 * =&gt; new java.io.FileReader($path, java.nio.charset.Charset.defaultCharset()) :: otherwise
 * ;;
 * </pre>
 * 
 * @since 1.3.2
 */
public final class TransformationRule {
	
	private final String ruleId;
	private final String description;
	private final Pattern sourcePattern;
	private final GuardExpression sourceGuard;
	private final List<RewriteAlternative> alternatives;
	private final ImportDirective importDirective;
	private final Severity severity;
	
	/**
	 * Creates a rule with only a source pattern and alternatives, all other fields null.
	 * Pass an empty list for hint-only rules that flag matches without rewriting.
	 * 
	 * @param sourcePattern the pattern to match
	 * @param alternatives list of rewrite alternatives (empty for hint-only rules)
	 * @return a new transformation rule
	 */
	public static TransformationRule of(Pattern sourcePattern, List<RewriteAlternative> alternatives) {
		return new TransformationRule(null, null, sourcePattern, null, alternatives, null, null);
	}
	
	/**
	 * Creates a new transformation rule.
	 * 
	 * @param ruleId optional unique rule ID for usage tracking (may be {@code null})
	 * @param description optional description (may be {@code null})
	 * @param sourcePattern the pattern to match
	 * @param sourceGuard optional guard on the source pattern (may be {@code null})
	 * @param alternatives list of rewrite alternatives (empty for hint-only rules)
	 * @param importDirective optional import directives (may be {@code null})
	 * @param severity optional per-rule severity level (may be {@code null} to inherit from hint file)
	 */
	public TransformationRule(String ruleId, String description, Pattern sourcePattern,
			GuardExpression sourceGuard, List<RewriteAlternative> alternatives,
			ImportDirective importDirective, Severity severity) {
		this.ruleId = ruleId;
		this.description = description;
		this.sourcePattern = Objects.requireNonNull(sourcePattern, "Source pattern cannot be null"); //$NON-NLS-1$
		this.sourceGuard = sourceGuard;
		this.alternatives = alternatives != null
				? Collections.unmodifiableList(alternatives)
				: Collections.emptyList();
		this.importDirective = importDirective;
		this.severity = severity;
	}
	
	/**
	 * Returns the unique rule ID for usage tracking.
	 * 
	 * <p>If not set, the rule has no explicit ID. Callers should fall back to
	 * a generated ID (e.g., hint file ID + rule index).</p>
	 * 
	 * @return the rule ID, or {@code null} if not set
	 * @since 1.4.1
	 */
	public String getRuleId() {
		return ruleId;
	}
	
	/**
	 * Returns the rule description.
	 * 
	 * @return the description, or {@code null} if not set
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * Returns the source pattern.
	 * 
	 * @return the source pattern
	 */
	public Pattern sourcePattern() {
		return sourcePattern;
	}
	
	/**
	 * Returns the source guard.
	 * 
	 * @return the source guard, or {@code null} if unconditional
	 */
	public GuardExpression sourceGuard() {
		return sourceGuard;
	}
	
	/**
	 * Returns the list of rewrite alternatives.
	 * 
	 * @return unmodifiable list of alternatives (empty for hint-only rules)
	 */
	public List<RewriteAlternative> alternatives() {
		return alternatives;
	}
	
	/**
	 * Returns {@code true} if this is a hint-only rule (no rewrite alternatives).
	 * 
	 * @return {@code true} if no alternatives are defined
	 */
	public boolean isHintOnly() {
		return alternatives.isEmpty();
	}
	
	/**
	 * Returns the import directive for this rule.
	 * 
	 * @return the import directive, or {@code null} if no imports are specified
	 * @since 1.3.2
	 */
	public ImportDirective getImportDirective() {
		return importDirective;
	}
	
	/**
	 * Returns {@code true} if this rule has import directives.
	 * 
	 * @return {@code true} if imports are specified
	 * @since 1.3.2
	 */
	public boolean hasImportDirective() {
		return importDirective != null && !importDirective.isEmpty();
	}
	
	/**
	 * Returns the per-rule severity level.
	 * 
	 * <p>If not set, the severity should be inherited from the containing
	 * {@link HintFile}.</p>
	 * 
	 * @return the severity, or {@code null} if not set (inherit from hint file)
	 * @since 1.4.0
	 */
	public Severity getSeverity() {
		return severity;
	}
	
	/**
	 * Finds the first matching rewrite alternative for the given context.
	 * 
	 * <p>Alternatives are evaluated in order. The first one whose guard evaluates
	 * to {@code true}, or an unconditional "otherwise" alternative, is returned.</p>
	 * 
	 * @param ctx the guard context for evaluation
	 * @return an {@link Optional} containing the matching alternative, or empty if none matches
	 */
	public Optional<RewriteAlternative> findMatchingAlternative(GuardContext ctx) {
		for (RewriteAlternative alt : alternatives) {
			if (alt.isOtherwise()) {
				return Optional.of(alt);
			}
			if (alt.condition() != null && alt.condition().evaluate(ctx)) {
				return Optional.of(alt);
			}
		}
		return Optional.empty();
	}
}
