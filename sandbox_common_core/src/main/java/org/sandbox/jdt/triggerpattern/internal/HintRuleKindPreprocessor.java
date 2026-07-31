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

import java.util.LinkedHashMap;
import java.util.Map;

import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/** Removes explicit {@code @kind:} metadata before compatibility parsing. */
final class HintRuleKindPreprocessor {

	record Result(String parserSource, Map<String, PatternKind> kindsByRuleId) {
		Result {
			kindsByRuleId= Map.copyOf(kindsByRuleId);
		}
	}

	private HintRuleKindPreprocessor() {
	}

	static Result preprocess(String source) throws HintParseException {
		String normalized= source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String[] lines= normalized.split("\n", -1); //$NON-NLS-1$
		StringBuilder output= new StringBuilder(normalized.length());
		Map<String, PatternKind> kinds= new LinkedHashMap<>();
		String ruleId= null;
		PatternKind ruleKind= null;
		int kindLine= -1;
		boolean inBlockComment= false;
		boolean inEmbeddedJava= false;

		for (int index= 0; index < lines.length; index++) {
			String line= lines[index];
			String trimmed= line.stripLeading();
			boolean metadataVisible= true;
			if (inEmbeddedJava) {
				metadataVisible= false;
				if (trimmed.contains("?>")) { //$NON-NLS-1$
					inEmbeddedJava= false;
				}
			} else if (inBlockComment) {
				metadataVisible= false;
				if (trimmed.contains("*/")) { //$NON-NLS-1$
					inBlockComment= false;
				}
			} else if (trimmed.startsWith("<?")) { //$NON-NLS-1$
				metadataVisible= false;
				inEmbeddedJava= !trimmed.contains("?>"); //$NON-NLS-1$
			} else if (trimmed.startsWith("/*")) { //$NON-NLS-1$
				metadataVisible= false;
				inBlockComment= !trimmed.contains("*/"); //$NON-NLS-1$
			} else if (trimmed.startsWith("//")) { //$NON-NLS-1$
				metadataVisible= false;
			}

			boolean stripLine= false;
			if (metadataVisible && trimmed.startsWith("@id:")) { //$NON-NLS-1$
				String parsedId= trimmed.substring(4).trim();
				if (parsedId.isBlank()) {
					throw new HintParseException("Per-rule id must not be blank", index + 1); //$NON-NLS-1$
				}
				if (ruleId != null && !ruleId.equals(parsedId)) {
					throw new HintParseException("Rule contains multiple @id values", index + 1); //$NON-NLS-1$
				}
				ruleId= parsedId;
			} else if (metadataVisible && trimmed.startsWith("@kind:")) { //$NON-NLS-1$
				if (ruleKind != null) {
					throw new HintParseException("Rule contains multiple @kind values", index + 1); //$NON-NLS-1$
				}
				String value= trimmed.substring(6).trim();
				try {
					ruleKind= PatternKind.valueOf(value);
				} catch (IllegalArgumentException exception) {
					throw new HintParseException("Unknown pattern kind " + value, index + 1); //$NON-NLS-1$
				}
				kindLine= index + 1;
				stripLine= true;
			}

			if (metadataVisible && ";;".equals(trimmed)) { //$NON-NLS-1$
				if (ruleKind != null) {
					if (ruleId == null) {
						throw new HintParseException("@kind requires an explicit @id in the same rule", //$NON-NLS-1$
								kindLine);
					}
					PatternKind previous= kinds.putIfAbsent(ruleId, ruleKind);
					if (previous != null && previous != ruleKind) {
						throw new HintParseException("Conflicting @kind values for rule " + ruleId, kindLine); //$NON-NLS-1$
					}
				}
				ruleId= null;
				ruleKind= null;
				kindLine= -1;
			}

			if (!stripLine) {
				output.append(line);
			}
			if (index + 1 < lines.length) {
				output.append('\n');
			}
		}
		if (ruleKind != null) {
			throw new HintParseException("Rule with @kind is missing ';;' terminator", kindLine); //$NON-NLS-1$
		}
		return new Result(output.toString(), kinds);
	}
}
