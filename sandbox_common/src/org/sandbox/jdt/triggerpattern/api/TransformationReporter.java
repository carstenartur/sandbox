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

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;

/**
 * Generates CSV and JSON reports from {@link TransformationResult} lists.
 *
 * <p>This reporter works directly with the results produced by
 * {@link BatchTransformationProcessor#process} and writes reports to a
 * caller-supplied {@link Writer}, making it easy to write to files, streams,
 * or string buffers.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * List&lt;TransformationResult&gt; results = processor.process(cu);
 * TransformationReporter reporter = new TransformationReporter();
 * try (Writer w = new FileWriter("report.csv")) {
 *     reporter.generateCsvReport(results, w);
 * }
 * </pre>
 *
 * @since 1.3.6
 */
public final class TransformationReporter {

	/**
	 * Writes a CSV report of the given transformation results to the writer.
	 *
	 * <p>The CSV contains a header row followed by one data row per result.
	 * Columns: {@code line, offset, length, matched, replacement, description,
	 * severity, pattern}.</p>
	 *
	 * @param results the transformation results to report
	 * @param writer the writer to write the CSV to
	 * @throws IOException if an I/O error occurs
	 */
	public void generateCsvReport(List<TransformationResult> results, Writer writer) throws IOException {
		writer.write("line,offset,length,matched,replacement,description,severity,pattern\n"); //$NON-NLS-1$

		for (TransformationResult result : results) {
			writer.write(String.valueOf(result.lineNumber()));
			writer.write(',');
			writer.write(String.valueOf(result.match().getOffset()));
			writer.write(',');
			writer.write(String.valueOf(result.match().getLength()));
			writer.write(',');
			writer.write(escapeCsv(result.matchedText()));
			writer.write(',');
			writer.write(result.replacement() != null ? escapeCsv(result.replacement()) : ""); //$NON-NLS-1$
			writer.write(',');
			writer.write(result.description() != null ? escapeCsv(result.description()) : ""); //$NON-NLS-1$
			writer.write(',');
			writer.write(escapeCsv(getSeverity(result)));
			writer.write(',');
			writer.write(escapeCsv(result.rule().sourcePattern().getValue()));
			writer.write('\n');
		}
	}

	/**
	 * Writes a JSON report of the given transformation results to the writer.
	 *
	 * <p>The JSON output is an array of objects, one per result, with fields:
	 * {@code line, offset, length, matched, replacement, description, severity,
	 * pattern}.</p>
	 *
	 * @param results the transformation results to report
	 * @param writer the writer to write the JSON to
	 * @throws IOException if an I/O error occurs
	 */
	public void generateJsonReport(List<TransformationResult> results, Writer writer) throws IOException {
		writer.write("[\n"); //$NON-NLS-1$

		for (int i = 0; i < results.size(); i++) {
			TransformationResult result = results.get(i);
			writer.write("  {\n"); //$NON-NLS-1$
			writer.write("    \"line\": "); //$NON-NLS-1$
			writer.write(String.valueOf(result.lineNumber()));
			writer.write(",\n"); //$NON-NLS-1$
			writer.write("    \"offset\": "); //$NON-NLS-1$
			writer.write(String.valueOf(result.match().getOffset()));
			writer.write(",\n"); //$NON-NLS-1$
			writer.write("    \"length\": "); //$NON-NLS-1$
			writer.write(String.valueOf(result.match().getLength()));
			writer.write(",\n"); //$NON-NLS-1$
			writer.write("    \"matched\": "); //$NON-NLS-1$
			writer.write(escapeJson(result.matchedText()));
			writer.write(",\n"); //$NON-NLS-1$
			writer.write("    \"replacement\": "); //$NON-NLS-1$
			writer.write(result.replacement() != null
					? escapeJson(result.replacement())
					: "null"); //$NON-NLS-1$
			writer.write(",\n"); //$NON-NLS-1$
			writer.write("    \"description\": "); //$NON-NLS-1$
			writer.write(result.description() != null
					? escapeJson(result.description())
					: "null"); //$NON-NLS-1$
			writer.write(",\n"); //$NON-NLS-1$
			writer.write("    \"severity\": "); //$NON-NLS-1$
			writer.write(escapeJson(getSeverity(result)));
			writer.write(",\n"); //$NON-NLS-1$
			writer.write("    \"pattern\": "); //$NON-NLS-1$
			writer.write(escapeJson(result.rule().sourcePattern().getValue()));
			writer.write('\n');
			writer.write("  }"); //$NON-NLS-1$
			if (i < results.size() - 1) {
				writer.write(',');
			}
			writer.write('\n');
		}

		writer.write("]"); //$NON-NLS-1$
	}

	/**
	 * Determines the severity string for a transformation result.
	 */
	private String getSeverity(TransformationResult result) {
		if (result.rule() != null && result.rule().sourcePattern() != null) {
			return result.hasReplacement() ? "warning" : "info"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "info"; //$NON-NLS-1$
	}

	/**
	 * Escapes a string for JSON output.
	 */
	static String escapeJson(String value) {
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
	static String escapeCsv(String value) {
		if (value == null) {
			return ""; //$NON-NLS-1$
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return "\"" + value.replace("\"", "\"\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
		return value;
	}
}
