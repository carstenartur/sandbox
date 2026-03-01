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
package org.sandbox.mining.core.comparison;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.sandbox.jdt.triggerpattern.internal.DslValidator;

/**
 * Applies validated DSL rules discovered during gap analysis
 * to {@code .sandbox-hint} files.
 */
public class HintFileUpdater {

	private final DslValidator validator;

	/**
	 * Creates a HintFileUpdater with the given validator.
	 *
	 * @param validator the DSL validator
	 */
	public HintFileUpdater(DslValidator validator) {
		this.validator = validator;
	}

	/**
	 * Validates and writes DSL rules from gap entries to hint files.
	 *
	 * @param gaps       gap entries that may contain reference DSL rules
	 * @param outputDir  directory where hint files will be written
	 * @return list of paths to newly created hint files
	 * @throws IOException if file writing fails
	 */
	public List<Path> applyGaps(List<GapEntry> gaps, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		List<Path> created = new ArrayList<>();

		for (GapEntry gap : gaps) {
			if (gap.category() != GapCategory.MISSING_DSL_RULE
					&& gap.category() != GapCategory.INVALID_DSL_RULE) {
				continue;
			}
			String rule = gap.referenceValue();
			if (rule == null || rule.isBlank()) {
				continue;
			}
			var result = validator.validate(rule);
			if (!result.valid()) {
				continue;
			}
			String fileName = sanitizeFileName(gap.commitHash()) + ".sandbox-hint"; //$NON-NLS-1$
			Path hintFile = outputDir.resolve(fileName);
			Files.writeString(hintFile, rule, StandardCharsets.UTF_8);
			created.add(hintFile);
		}

		return created;
	}

	static String sanitizeFileName(String commitHash) {
		if (commitHash == null || commitHash.isBlank()) {
			return "unknown"; //$NON-NLS-1$
		}
		return commitHash.substring(0, Math.min(7, commitHash.length()))
				.replaceAll("[^a-zA-Z0-9_-]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
