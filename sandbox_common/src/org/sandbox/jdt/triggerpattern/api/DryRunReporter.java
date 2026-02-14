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
import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Performs dry-run analysis by finding all matches for a set of transformation
 * rules without modifying any code.
 *
 * <p>Generates a report of all matches found, including the file, line number,
 * matched code, and suggested replacement. This is useful for:</p>
 * <ul>
 *   <li>Previewing changes before applying them</li>
 *   <li>Generating CSV/JSON reports of code improvement opportunities</li>
 *   <li>Integration with Eclipse Problem View as markers</li>
 * </ul>
 *
 * <h2>Usage example</h2>
 * <pre>
 * DryRunReporter reporter = new DryRunReporter();
 * List&lt;ReportEntry&gt; entries = reporter.analyze(cu, hintFile.getRules());
 * String json = reporter.toJson(entries);
 * </pre>
 *
 * @since 1.3.3
 */
public final class DryRunReporter {

	private final TriggerPatternEngine engine;

	/**
	 * Creates a new dry-run reporter using the default engine.
	 */
	public DryRunReporter() {
		this.engine = new TriggerPatternEngine();
	}

	/**
	 * Creates a new dry-run reporter using the given engine.
	 *
	 * @param engine the trigger pattern engine to use for matching
	 */
	public DryRunReporter(TriggerPatternEngine engine) {
		this.engine = engine;
	}

	/**
	 * Analyzes a compilation unit against a list of transformation rules.
	 *
	 * @param cu the compilation unit to analyze
	 * @param rules the transformation rules to apply
	 * @return list of report entries for all matches found
	 */
	public List<ReportEntry> analyze(CompilationUnit cu, List<TransformationRule> rules) {
		return analyze(cu, rules, null);
	}

	/**
	 * Analyzes a compilation unit against a list of transformation rules
	 * with compiler options for guard evaluation.
	 *
	 * @param cu the compilation unit to analyze
	 * @param rules the transformation rules to apply
	 * @param compilerOptions compiler options for source version guards (may be {@code null})
	 * @return list of report entries for all matches found
	 */
	public List<ReportEntry> analyze(CompilationUnit cu, List<TransformationRule> rules,
			Map<String, String> compilerOptions) {
		if (cu == null || rules == null || rules.isEmpty()) {
			return Collections.emptyList();
		}

		List<ReportEntry> entries = new ArrayList<>();

		for (TransformationRule rule : rules) {
			Pattern sourcePattern = rule.sourcePattern();
			List<Match> matches = engine.findMatches(cu, sourcePattern);

			for (Match match : matches) {
				int lineNumber = cu.getLineNumber(match.getOffset());
				String matchedCode = match.getMatchedNode().toString().trim();

				// Determine replacement if available
				String suggestedReplacement = null;
				if (!rule.isHintOnly() && !rule.alternatives().isEmpty()) {
					// Try to find the matching alternative using guards
					if (compilerOptions != null) {
						GuardContext guardCtx = GuardContext.fromMatch(match, cu, compilerOptions);
						RewriteAlternative alt = rule.findMatchingAlternative(guardCtx);
						if (alt != null) {
							suggestedReplacement = substitutePlaceholders(
									alt.replacementPattern(), match.getBindings());
						}
					} else {
						// Without compiler options, use the first alternative
						suggestedReplacement = substitutePlaceholders(
								rule.alternatives().get(0).replacementPattern(),
								match.getBindings());
					}
				}

				String severity = "info"; //$NON-NLS-1$
				String description = rule.getDescription();

				entries.add(new ReportEntry(
						lineNumber,
						match.getOffset(),
						match.getLength(),
						matchedCode,
						suggestedReplacement,
						description,
						severity,
						sourcePattern.getValue(),
						rule.hasImportDirective() ? rule.getImportDirective() : null));
			}
		}

		return entries;
	}

	/**
	 * Performs simple placeholder substitution for generating replacement previews.
	 * Replaces {@code $name} placeholders with the text of their bound AST nodes.
	 */
	private String substitutePlaceholders(String pattern, Map<String, Object> bindings) {
		String result = pattern;
		// Sort by key length descending to handle $args$ before $a
		List<Map.Entry<String, Object>> sorted = new ArrayList<>(bindings.entrySet());
		sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

		for (Map.Entry<String, Object> entry : sorted) {
			String placeholder = entry.getKey();
			Object value = entry.getValue();
			if (value instanceof org.eclipse.jdt.core.dom.ASTNode) {
				result = result.replace(placeholder, value.toString().trim());
			} else if (value instanceof List<?> list) {
				// Variadic placeholder: join with ", "
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < list.size(); i++) {
					if (i > 0) {
						sb.append(", "); //$NON-NLS-1$
					}
					sb.append(list.get(i).toString().trim());
				}
				result = result.replace(placeholder, sb.toString());
			}
		}
		return result;
	}

	/**
	 * Converts a list of report entries to JSON format.
	 *
	 * @param entries the report entries
	 * @return JSON string representation
	 */
	public String toJson(List<ReportEntry> entries) {
		StringBuilder sb = new StringBuilder();
		sb.append("[\n"); //$NON-NLS-1$

		for (int i = 0; i < entries.size(); i++) {
			ReportEntry entry = entries.get(i);
			sb.append("  {\n"); //$NON-NLS-1$
			sb.append("    \"line\": ").append(entry.lineNumber()).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"offset\": ").append(entry.offset()).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"length\": ").append(entry.length()).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"matched\": ").append(escapeJson(entry.matchedCode())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"replacement\": ") //$NON-NLS-1$
					.append(entry.suggestedReplacement() != null
							? escapeJson(entry.suggestedReplacement())
							: "null") //$NON-NLS-1$
					.append(",\n"); //$NON-NLS-1$
			sb.append("    \"description\": ") //$NON-NLS-1$
					.append(entry.description() != null
							? escapeJson(entry.description())
							: "null") //$NON-NLS-1$
					.append(",\n"); //$NON-NLS-1$
			sb.append("    \"severity\": ").append(escapeJson(entry.severity())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"pattern\": ").append(escapeJson(entry.sourcePattern())).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("  }"); //$NON-NLS-1$
			if (i < entries.size() - 1) {
				sb.append(',');
			}
			sb.append('\n');
		}

		sb.append("]"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Converts a list of report entries to CSV format.
	 *
	 * @param entries the report entries
	 * @return CSV string with header row
	 */
	public String toCsv(List<ReportEntry> entries) {
		StringBuilder sb = new StringBuilder();
		sb.append("line,offset,length,matched,replacement,description,severity,pattern\n"); //$NON-NLS-1$

		for (ReportEntry entry : entries) {
			sb.append(entry.lineNumber()).append(',');
			sb.append(entry.offset()).append(',');
			sb.append(entry.length()).append(',');
			sb.append(escapeCsv(entry.matchedCode())).append(',');
			sb.append(entry.suggestedReplacement() != null
					? escapeCsv(entry.suggestedReplacement()) : "").append(','); //$NON-NLS-1$
			sb.append(entry.description() != null
					? escapeCsv(entry.description()) : "").append(','); //$NON-NLS-1$
			sb.append(escapeCsv(entry.severity())).append(',');
			sb.append(escapeCsv(entry.sourcePattern()));
			sb.append('\n');
		}

		return sb.toString();
	}

	/**
	 * Escapes a string for JSON output.
	 */
	private static String escapeJson(String value) {
		if (value == null) {
			return "null"; //$NON-NLS-1$
		}
		StringBuilder sb = new StringBuilder("\""); //$NON-NLS-1$
		for (char c : value.toCharArray()) {
			switch (c) {
			case '"':
				sb.append("\\\""); //$NON-NLS-1$
				break;
			case '\\':
				sb.append("\\\\"); //$NON-NLS-1$
				break;
			case '\n':
				sb.append("\\n"); //$NON-NLS-1$
				break;
			case '\r':
				sb.append("\\r"); //$NON-NLS-1$
				break;
			case '\t':
				sb.append("\\t"); //$NON-NLS-1$
				break;
			default:
				sb.append(c);
				break;
			}
		}
		sb.append('"');
		return sb.toString();
	}

	/**
	 * Escapes a string for CSV output.
	 */
	private static String escapeCsv(String value) {
		if (value == null) {
			return ""; //$NON-NLS-1$
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return "\"" + value.replace("\"", "\"\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
		return value;
	}

	/**
	 * Represents a single entry in a dry-run report.
	 *
	 * @param lineNumber the line number in the source file (1-based)
	 * @param offset the character offset of the match
	 * @param length the character length of the match
	 * @param matchedCode the matched source code text
	 * @param suggestedReplacement the suggested replacement (null for hint-only)
	 * @param description the rule description (may be null)
	 * @param severity the severity level (info, warning, error)
	 * @param sourcePattern the source pattern that matched
	 * @param importDirective the import directives if any (may be null)
	 * @since 1.3.3
	 */
	public record ReportEntry(
			int lineNumber,
			int offset,
			int length,
			String matchedCode,
			String suggestedReplacement,
			String description,
			String severity,
			String sourcePattern,
			ImportDirective importDirective) {

		/**
		 * Returns {@code true} if this entry has a suggested replacement.
		 *
		 * @return {@code true} if not hint-only
		 */
		public boolean hasReplacement() {
			return suggestedReplacement != null;
		}
	}
}
