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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.mining.core.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sandbox.mining.core.config.KnownRulesStore;
import org.sandbox.mining.core.config.KnownRulesStore.KnownRule;
import org.sandbox.mining.core.config.KnownRulesStore.RuleStatus;

/**
 * Generates reports from rules that require DSL enhancements.
 *
 * <p>Scans the {@link KnownRulesStore} for rules with status
 * {@link RuleStatus#NEEDS_DSL_EXTENSION} and groups them by the
 * DSL limitation they hit (inferred from category and summary).</p>
 *
 * <p>The generated report can be used to create GitHub issues
 * automatically from the mining workflow.</p>
 *
 * @since 1.3.3
 */
public class DslEnhancementReporter {

	/** A group of rules that share the same DSL limitation. */
	public static class DslLimitationGroup {
		private final String limitation;
		private final List<KnownRule> rules;

		DslLimitationGroup(String limitation, List<KnownRule> rules) {
			this.limitation = limitation;
			this.rules = List.copyOf(rules);
		}

		public String getLimitation() { return limitation; }
		public List<KnownRule> getRules() { return rules; }
		public int getCount() { return rules.size(); }
	}

	/**
	 * Scans the known rules store and groups rules that need DSL extensions
	 * by the DSL limitation they hit.
	 *
	 * @param store the known rules store to scan
	 * @return list of limitation groups, sorted by count (most impactful first)
	 */
	public List<DslLimitationGroup> groupByLimitation(KnownRulesStore store) {
		Map<String, List<KnownRule>> groups = new LinkedHashMap<>();
		for (KnownRule rule : store.getRules()) {
			if (rule.getStatus() != RuleStatus.NEEDS_DSL_EXTENSION) {
				continue;
			}
			String limitation = inferLimitation(rule);
			groups.computeIfAbsent(limitation, k -> new ArrayList<>()).add(rule);
		}
		return groups.entrySet().stream()
				.map(e -> new DslLimitationGroup(e.getKey(), e.getValue()))
				.sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
				.toList();
	}

	/**
	 * Generates a structured markdown report of all DSL enhancement needs.
	 *
	 * @param store the known rules store to scan
	 * @return markdown report string, or empty string if no rules need DSL extensions
	 */
	public String generateReport(KnownRulesStore store) {
		List<DslLimitationGroup> groups = groupByLimitation(store);
		if (groups.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		StringBuilder sb = new StringBuilder();
		sb.append("# DSL Enhancement Needs\n\n"); //$NON-NLS-1$
		sb.append("Found ").append(groups.stream().mapToInt(DslLimitationGroup::getCount).sum()); //$NON-NLS-1$
		sb.append(" rules across ").append(groups.size()).append(" limitation categories.\n\n"); //$NON-NLS-1$ //$NON-NLS-2$

		for (DslLimitationGroup group : groups) {
			sb.append("## ").append(group.getLimitation()); //$NON-NLS-1$
			sb.append(" (").append(group.getCount()).append(" rules)\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
			for (KnownRule rule : group.getRules()) {
				sb.append("- **").append(safe(rule.getSummary())).append("**"); //$NON-NLS-1$ //$NON-NLS-2$
				sb.append(" (category: ").append(safe(rule.getCategory())); //$NON-NLS-1$
				if (rule.getSourceCommit() != null) {
					sb.append(", commit: `").append(rule.getSourceCommit().substring(0, //$NON-NLS-1$
							Math.min(7, rule.getSourceCommit().length()))).append('`');
				}
				sb.append(")\n"); //$NON-NLS-1$
				if (rule.getDslRule() != null && !rule.getDslRule().isBlank()) {
					sb.append("  ```\n  ").append(rule.getDslRule().replace("\n", "\n  ")).append("\n  ```\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	/**
	 * Generates a list of issue descriptors, one per DSL limitation category.
	 * Each descriptor contains a title and body suitable for creating a GitHub issue.
	 *
	 * @param store the known rules store to scan
	 * @return list of issue descriptors
	 */
	public List<IssueDescriptor> generateIssueDescriptors(KnownRulesStore store) {
		List<DslLimitationGroup> groups = groupByLimitation(store);
		List<IssueDescriptor> issues = new ArrayList<>();
		for (DslLimitationGroup group : groups) {
			String title = "\uD83D\uDD27 DSL Enhancement: " + group.getLimitation() //$NON-NLS-1$
					+ " (" + group.getCount() + " rules blocked)"; //$NON-NLS-1$ //$NON-NLS-2$
			StringBuilder body = new StringBuilder();
			body.append("## DSL Limitation\n\n"); //$NON-NLS-1$
			body.append("**").append(group.getLimitation()).append("**\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
			body.append("## Blocked Rules\n\n"); //$NON-NLS-1$
			body.append("| # | Summary | Category | Commit |\n"); //$NON-NLS-1$
			body.append("|---|---------|----------|--------|\n"); //$NON-NLS-1$
			int idx = 0;
			for (KnownRule rule : group.getRules()) {
				idx++;
				String shortCommit = rule.getSourceCommit() != null
						? rule.getSourceCommit().substring(0, Math.min(7, rule.getSourceCommit().length()))
						: "?"; //$NON-NLS-1$
				body.append("| ").append(idx).append(" | ").append(safe(rule.getSummary())); //$NON-NLS-1$ //$NON-NLS-2$
				body.append(" | ").append(safe(rule.getCategory())); //$NON-NLS-1$
				body.append(" | `").append(shortCommit).append("` |\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			body.append("\n## Priority\n\n"); //$NON-NLS-1$
			body.append("This limitation blocks **").append(group.getCount()); //$NON-NLS-1$
			body.append("** discovered transformation rules from being implemented.\n"); //$NON-NLS-1$
			body.append("\n_Auto-generated by mining workflow._\n"); //$NON-NLS-1$
			issues.add(new IssueDescriptor(title, body.toString()));
		}
		return issues;
	}

	/**
	 * Descriptor for a GitHub issue to be created.
	 */
	public record IssueDescriptor(String title, String body) {
	}

	/**
	 * Infers the DSL limitation category from a rule's summary and category.
	 */
	static String inferLimitation(KnownRule rule) {
		String summary = rule.getSummary() != null ? rule.getSummary().toLowerCase() : ""; //$NON-NLS-1$
		String dslRule = rule.getDslRule() != null ? rule.getDslRule().toLowerCase() : ""; //$NON-NLS-1$

		if (summary.contains("bitwise") || dslRule.contains(" | ") || dslRule.contains("&")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return "Bitwise operators in patterns/replacements"; //$NON-NLS-1$
		}
		if (summary.contains("try-with") || summary.contains("autocloseable") //$NON-NLS-1$ //$NON-NLS-2$
				|| summary.contains("resource wrapping")) { //$NON-NLS-1$
			return "Statement insertion / wrapping (try-with-resources, guard clauses)"; //$NON-NLS-1$
		}
		if (summary.contains("generic") || summary.contains("type parameter") //$NON-NLS-1$ //$NON-NLS-2$
				|| dslRule.contains("<$t>")) { //$NON-NLS-1$
			return "Type-parameterized matching (generics)"; //$NON-NLS-1$
		}
		if (summary.contains("arity") || summary.contains("vararg") //$NON-NLS-1$ //$NON-NLS-2$
				|| summary.contains("argument reorder")) { //$NON-NLS-1$
			return "Complex expression composition with arity changes"; //$NON-NLS-1$
		}
		if (summary.contains("control flow") || summary.contains("if-else") //$NON-NLS-1$ //$NON-NLS-2$
				|| summary.contains("multi-statement")) { //$NON-NLS-1$
			return "Control flow / multi-statement patterns"; //$NON-NLS-1$
		}
		return "Other DSL limitations"; //$NON-NLS-1$
	}

	private static String safe(String s) {
		return s != null ? s : "?"; //$NON-NLS-1$
	}
}
