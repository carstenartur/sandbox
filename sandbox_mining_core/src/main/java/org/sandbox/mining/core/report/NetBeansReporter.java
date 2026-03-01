/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;

/**
 * Formats evaluations in NetBeans/compiler-warning format.
 *
 * <p>One line per evaluation:
 * {@code REPO/COMMIT:1: SEVERITY: [TRAFFIC_LIGHT/CATEGORY] SUMMARY — scores}</p>
 *
 * <p>This format is compatible with IDE error parsers and can be piped
 * through grep, sort, or other Unix tools.</p>
 */
public class NetBeansReporter {

	private static final String EVALUATIONS_TXT = "evaluations.txt"; //$NON-NLS-1$

	/**
	 * Formats a list of evaluations in NetBeans format.
	 *
	 * @param evaluations the evaluations to format
	 * @return formatted string with one line per evaluation
	 */
	public String format(List<CommitEvaluation> evaluations) {
		StringBuilder sb = new StringBuilder();
		for (CommitEvaluation eval : evaluations) {
			sb.append(formatLine(eval)).append("\n"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	/**
	 * Writes the formatted evaluations to a file.
	 *
	 * @param evaluations the evaluations to write
	 * @param outputDir   the output directory
	 * @throws IOException if file writing fails
	 */
	public void write(List<CommitEvaluation> evaluations, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		String content = format(evaluations);
		Files.writeString(outputDir.resolve(EVALUATIONS_TXT), content, StandardCharsets.UTF_8);
	}

	/**
	 * Prints the formatted evaluations to the given print stream.
	 *
	 * @param evaluations the evaluations to print
	 * @param out         the output stream (typically System.out)
	 */
	public void printToStream(List<CommitEvaluation> evaluations, PrintStream out) {
		for (CommitEvaluation eval : evaluations) {
			out.println(formatLine(eval));
		}
	}

	/**
	 * Formats a single evaluation as a NetBeans-style line.
	 *
	 * @param eval the evaluation
	 * @return formatted line
	 */
	String formatLine(CommitEvaluation eval) {
		String repo = eval.repoUrl() != null ? repoShortName(eval.repoUrl()) : "unknown"; //$NON-NLS-1$
		String hash = eval.commitHash() != null
				? eval.commitHash().substring(0, Math.min(7, eval.commitHash().length()))
				: "???????"; //$NON-NLS-1$
		String severity = severityFromTrafficLight(eval.trafficLight());
		String category = eval.category() != null ? eval.category() : "uncategorized"; //$NON-NLS-1$
		String summary = eval.summary() != null ? eval.summary() : ""; //$NON-NLS-1$
		String scores = "R=" + eval.reusability() + "/I=" + eval.codeImprovement() //$NON-NLS-1$ //$NON-NLS-2$
				+ "/E=" + eval.implementationEffort(); //$NON-NLS-1$

		return repo + "/" + hash + ":1: " + severity + ": [" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				+ eval.trafficLight() + "/" + category + "] " //$NON-NLS-1$ //$NON-NLS-2$
				+ summary + " \u2014 " + scores; //$NON-NLS-1$
	}

	static String severityFromTrafficLight(TrafficLight light) {
		if (light == null) {
			return "info"; //$NON-NLS-1$
		}
		return switch (light) {
			case GREEN -> "warning"; //$NON-NLS-1$
			case YELLOW -> "info"; //$NON-NLS-1$
			case RED -> "info"; //$NON-NLS-1$
			case NOT_APPLICABLE -> "info"; //$NON-NLS-1$
		};
	}

	static String repoShortName(String url) {
		String name = url;
		if (name.endsWith(".git")) { //$NON-NLS-1$
			name = name.substring(0, name.length() - 4);
		}
		int lastSlash = name.lastIndexOf('/');
		if (lastSlash >= 0) {
			name = name.substring(lastSlash + 1);
		}
		return name;
	}
}
