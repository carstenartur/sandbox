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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;
import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/** Converts high-level {@code =>! action(...)} alternatives into parser sentinels. */
final class HintStructuredActionPreprocessor {

	record Result(String parserSource,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel) {
		Result {
			actionsBySentinel= Map.copyOf(actionsBySentinel);
		}
	}

	private HintStructuredActionPreprocessor() {
	}

	static Result preprocess(String source, RewriteActionCatalog catalog) throws HintParseException {
		String normalized= source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String[] lines= normalized.split("\n", -1); //$NON-NLS-1$
		List<String> output= new ArrayList<>(lines.length);
		Map<String, List<StructuredRewriteAction>> actions= new LinkedHashMap<>();
		boolean inBlockComment= false;
		boolean inEmbeddedJava= false;
		int actionIndex= 0;

		for (int lineIndex= 0; lineIndex < lines.length; lineIndex++) {
			String line= lines[lineIndex];
			String trimmed= line.stripLeading();
			if (inEmbeddedJava) {
				output.add(line);
				if (trimmed.contains("?>")) { //$NON-NLS-1$
					inEmbeddedJava= false;
				}
				continue;
			}
			if (inBlockComment) {
				output.add(line);
				if (trimmed.contains("*/")) { //$NON-NLS-1$
					inBlockComment= false;
				}
				continue;
			}
			if (trimmed.startsWith("<?") && !trimmed.contains("?>")) { //$NON-NLS-1$ //$NON-NLS-2$
				inEmbeddedJava= true;
				output.add(line);
				continue;
			}
			if (trimmed.startsWith("/*") && !trimmed.contains("*/")) { //$NON-NLS-1$ //$NON-NLS-2$
				inBlockComment= true;
				output.add(line);
				continue;
			}
			if (!trimmed.startsWith("=>!")) { //$NON-NLS-1$
				output.add(line);
				continue;
			}

			int firstLine= lineIndex + 1;
			int indentationLength= line.length() - trimmed.length();
			String indentation= line.substring(0, indentationLength);
			StringBuilder alternative= new StringBuilder(trimmed.substring(3).trim());
			int lastLine= lineIndex;
			while (lastLine + 1 < lines.length) {
				String nextTrimmed= lines[lastLine + 1].stripLeading();
				if (nextTrimmed.startsWith("=>") || ";;".equals(nextTrimmed)) { //$NON-NLS-1$ //$NON-NLS-2$
					break;
				}
				lastLine++;
				alternative.append('\n').append(nextTrimmed);
			}

			ActionAndGuard split= splitGuard(alternative.toString());
			List<StructuredRewriteAction> parsed= StructuredRewriteActionParser.parse(
					split.actionText().trim(), firstLine, catalog);
			String sentinel;
			do {
				sentinel= "__sandbox_structured_action_" + actionIndex++ + "__"; //$NON-NLS-1$ //$NON-NLS-2$
			} while (normalized.contains(sentinel));
			actions.put(sentinel, parsed);
			String guardSuffix= split.guardText() == null ? "" : " :: " + split.guardText().trim(); //$NON-NLS-1$ //$NON-NLS-2$
			output.add(indentation + "=> " + sentinel + guardSuffix); //$NON-NLS-1$
			for (int consumed= lineIndex + 1; consumed <= lastLine; consumed++) {
				output.add(""); //$NON-NLS-1$
			}
			lineIndex= lastLine;
		}
		return new Result(String.join("\n", output), actions); //$NON-NLS-1$
	}

	private static ActionAndGuard splitGuard(String text) {
		int depth= 0;
		boolean inQuote= false;
		boolean escaped= false;
		for (int index= 0; index < text.length() - 1; index++) {
			char current= text.charAt(index);
			if (inQuote) {
				if (escaped) {
					escaped= false;
				} else if (current == '\\') {
					escaped= true;
				} else if (current == '"') {
					inQuote= false;
				}
				continue;
			}
			if (current == '"') {
				inQuote= true;
			} else if (current == '(') {
				depth++;
			} else if (current == ')') {
				depth--;
			} else if (depth == 0 && current == ':' && text.charAt(index + 1) == ':') {
				return new ActionAndGuard(text.substring(0, index), text.substring(index + 2));
			}
		}
		return new ActionAndGuard(text, null);
	}

	private record ActionAndGuard(String actionText, String guardText) {
	}
}
