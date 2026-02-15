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
 * specify imports to add or remove when the rule is applied.</p>
 * 
 * <p>Example DSL syntax:</p>
 * <pre>
 * new FileReader($path) :: sourceVersionGE(11)
 * =&gt; new FileReader($path, StandardCharsets.UTF_8) :: sourceVersionGE(11)
 * =&gt; new FileReader($path, Charset.defaultCharset()) :: otherwise
 *    addImport java.nio.charset.StandardCharsets
 * ;;
 * </pre>
 * 
 * @since 1.3.2
 */
public final class TransformationRule {
	
	private final String description;
	private final Pattern sourcePattern;
	private final GuardExpression sourceGuard;
	private final List<RewriteAlternative> alternatives;
	private final ImportDirective importDirective;
	
	/**
	 * Creates a new transformation rule.
	 * 
	 * @param description optional description (may be {@code null})
	 * @param sourcePattern the pattern to match
	 * @param sourceGuard optional guard on the source pattern (may be {@code null})
	 * @param alternatives list of rewrite alternatives (empty for hint-only rules)
	 */
	public TransformationRule(String description, Pattern sourcePattern,
			GuardExpression sourceGuard, List<RewriteAlternative> alternatives) {
		this(description, sourcePattern, sourceGuard, alternatives, null);
	}
	
	/**
	 * Creates a new transformation rule with import directives.
	 * 
	 * @param description optional description (may be {@code null})
	 * @param sourcePattern the pattern to match
	 * @param sourceGuard optional guard on the source pattern (may be {@code null})
	 * @param alternatives list of rewrite alternatives (empty for hint-only rules)
	 * @param importDirective optional import directives (may be {@code null})
	 */
	public TransformationRule(String description, Pattern sourcePattern,
			GuardExpression sourceGuard, List<RewriteAlternative> alternatives,
			ImportDirective importDirective) {
		this.description = description;
		this.sourcePattern = Objects.requireNonNull(sourcePattern, "Source pattern cannot be null"); //$NON-NLS-1$
		this.sourceGuard = sourceGuard;
		this.alternatives = alternatives != null
				? Collections.unmodifiableList(alternatives)
				: Collections.emptyList();
		this.importDirective = importDirective;
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
	 * Finds the first matching rewrite alternative for the given context.
	 * 
	 * <p>Alternatives are evaluated in order. The first one whose guard evaluates
	 * to {@code true}, or an unconditional "otherwise" alternative, is returned.</p>
	 * 
	 * @param ctx the guard context for evaluation
	 * @return the matching alternative, or {@code null} if no alternative matches
	 */
	public RewriteAlternative findMatchingAlternative(GuardContext ctx) {
		for (RewriteAlternative alt : alternatives) {
			if (alt.isOtherwise()) {
				return alt;
			}
			if (alt.condition() != null && alt.condition().evaluate(ctx)) {
				return alt;
			}
		}
		return null;
	}
}
