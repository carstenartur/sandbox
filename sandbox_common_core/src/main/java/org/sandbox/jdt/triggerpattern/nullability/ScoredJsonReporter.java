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
package org.sandbox.jdt.triggerpattern.nullability;

import java.util.List;

/**
 * Generates a JSON report from scored match entries.
 *
 * <p>The output includes the full score metadata for each entry,
 * following the schema described in the mining report specification.</p>
 *
 * @since 1.2.6
 */
public class ScoredJsonReporter {

	/**
	 * Generates a JSON report from scored entries.
	 *
	 * @param entries the scored match entries
	 * @return the JSON content
	 */
	public String generate(List<ScoredMatchEntry> entries) {
		StringBuilder sb = new StringBuilder();
		sb.append("[\n"); //$NON-NLS-1$

		for (int i = 0; i < entries.size(); i++) {
			ScoredMatchEntry entry = entries.get(i);
			MatchScore score = entry.score();

			sb.append("  {\n"); //$NON-NLS-1$
			sb.append("    \"repository\": ").append(jsonString(entry.repository())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"rule\": ").append(jsonString(entry.rule())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"file\": ").append(jsonString(entry.file())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"line\": ").append(entry.line()).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("    \"matchedCode\": ").append(jsonString(entry.matchedCode())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			if (entry.suggestedReplacement() != null) {
				sb.append("    \"suggestedReplacement\": ").append(jsonString(entry.suggestedReplacement())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			sb.append("    \"score\": {\n"); //$NON-NLS-1$
			sb.append("      \"trivialChange\": ").append(score.trivialChange()).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("      \"nullStatus\": ").append(jsonString(score.nullStatus().name())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("      \"severity\": ").append(jsonString(score.severity().name())).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("      \"reason\": ").append(jsonString(score.reason())); //$NON-NLS-1$

			if (!score.evidence().isEmpty()) {
				sb.append(",\n      \"evidence\": [\n"); //$NON-NLS-1$
				for (int j = 0; j < score.evidence().size(); j++) {
					sb.append("        ").append(jsonString(score.evidence().get(j))); //$NON-NLS-1$
					if (j < score.evidence().size() - 1) {
						sb.append(',');
					}
					sb.append('\n');
				}
				sb.append("      ]"); //$NON-NLS-1$
			}

			sb.append("\n    }\n"); //$NON-NLS-1$
			sb.append("  }"); //$NON-NLS-1$
			if (i < entries.size() - 1) {
				sb.append(',');
			}
			sb.append('\n');
		}

		sb.append("]\n"); //$NON-NLS-1$
		return sb.toString();
	}

	private static String jsonString(String value) {
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
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c)); //$NON-NLS-1$
				} else {
					sb.append(c);
				}
				break;
			}
		}
		sb.append('"');
		return sb.toString();
	}
}
