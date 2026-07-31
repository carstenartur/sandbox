/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.Map;

import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Normalizes multiline explicit type headers for the line-oriented compatibility
 * parser. The original line count is retained so later diagnostics keep useful
 * source positions.
 */
final class HintTypeDeclarationSourcePreprocessor {

	private HintTypeDeclarationSourcePreprocessor() {
	}

	static String preprocess(String source, Map<String, PatternKind> kindsByRuleId)
			throws HintParseException {
		if (source == null || source.isEmpty() || kindsByRuleId.isEmpty()) {
			return source == null ? "" : source; //$NON-NLS-1$
		}
		String normalized= source.replace("\r\n", "\n").replace('\r', '\n'); //$NON-NLS-1$ //$NON-NLS-2$
		String[] lines= normalized.split("\n", -1); //$NON-NLS-1$
		StringBuilder output= new StringBuilder(normalized.length());
		String ruleId= null;
		boolean sourceSeen= false;

		for (int index= 0; index < lines.length; index++) {
			String line= lines[index];
			String trimmed= line.stripLeading();
			if (trimmed.startsWith("@id:")) { //$NON-NLS-1$
				ruleId= trimmed.substring(4).trim();
				sourceSeen= false;
			}

			if (!sourceSeen && kindsByRuleId.get(ruleId) == PatternKind.TYPE_DECLARATION
					&& isSourceStart(trimmed)) {
				int lastHeaderLine= index;
				StringBuilder header= new StringBuilder(trimmed);
				while (lastHeaderLine + 1 < lines.length) {
					String next= lines[lastHeaderLine + 1].stripLeading();
					if (next.startsWith("=>") || next.startsWith("::") || ";;".equals(next)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						break;
					}
					lastHeaderLine++;
					if (!next.isBlank()) {
						header.append(' ').append(next);
					}
				}
				int indentationLength= line.length() - trimmed.length();
				output.append(line, 0, indentationLength).append(header);
				for (int consumed= index; consumed < lastHeaderLine; consumed++) {
					output.append('\n');
				}
				index= lastHeaderLine;
				sourceSeen= true;
			} else {
				output.append(line);
			}

			if (";;".equals(trimmed)) { //$NON-NLS-1$
				ruleId= null;
				sourceSeen= false;
			}
			if (index + 1 < lines.length) {
				output.append('\n');
			}
		}
		return output.toString();
	}

	private static boolean isSourceStart(String trimmed) throws HintParseException {
		if (trimmed.isBlank() || trimmed.startsWith("@severity:") //$NON-NLS-1$
				|| trimmed.startsWith("@id:")) { //$NON-NLS-1$
			return false;
		}
		if (trimmed.startsWith("=>") || trimmed.startsWith("::") || ";;".equals(trimmed)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new HintParseException("Explicit type rule has no source declaration", 0); //$NON-NLS-1$
		}
		return !(trimmed.startsWith("\"") && trimmed.endsWith("\":")); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
