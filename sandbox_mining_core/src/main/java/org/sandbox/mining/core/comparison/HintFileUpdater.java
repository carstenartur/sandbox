/****************************************************************************
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
 *****************************************************************************/
package org.sandbox.mining.core.comparison;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sandbox.jdt.triggerpattern.internal.DslValidator;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

/**
 * Applies validated DSL rules discovered during gap analysis
 * to {@code .sandbox-hint} files.
 *
 * <p>Rules are <b>appended</b> to existing hint files rather than overwriting
 * them. Duplicate detection prevents the same rule from being added twice.</p>
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
	 * If the target file already exists, rules are appended (not overwritten).
	 * Duplicate rules are detected and skipped.
	 *
	 * @param gaps       gap entries that may contain reference DSL rules
	 * @param outputDir  directory where hint files will be written
	 * @return list of paths to hint files that were created or updated
	 * @throws IOException if file writing fails
	 */
	public List<Path> applyGaps(List<GapEntry> gaps, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		List<Path> created = new ArrayList<>();

		for (GapEntry gap : gaps) {
			if (gap.category() != GapCategory.MISSING_DSL_RULE
				&& gap.category() != GapCategory.INVALID_DSL_RULE
				&& gap.category() != GapCategory.DSL_SYNTAX
				&& gap.category() != GapCategory.GUARD_WISSEN) {
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
			if (appendRuleIfNotDuplicate(hintFile, rule)) {
				created.add(hintFile);
			}
		}

		return created;
	}

	/**
	 * Writes {@code .sandbox-hint} files for evaluations that are GREEN with a
	 * VALID DSL rule. If the target file already exists, rules are appended
	 * (not overwritten). Duplicate rules are detected and skipped.
	 *
	 * @param evaluations all commit evaluations from the mining run
	 * @param outputDir   directory where hint files will be written
	 * @return list of paths to hint files that were created or updated
	 * @throws IOException if file writing fails
	 */
	public List<Path> writeHintFiles(List<CommitEvaluation> evaluations, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		List<Path> created = new ArrayList<>();

		for (CommitEvaluation eval : evaluations) {
			if (eval.trafficLight() != CommitEvaluation.TrafficLight.GREEN) {
				continue;
			}
			if (!"VALID".equals(eval.dslValidationResult())) { //$NON-NLS-1$
				continue;
			}
			String rule = eval.dslRule();
			if (rule == null || rule.isBlank()) {
				continue;
			}
			// Use targetHintFile if available, otherwise generate from commit hash
			String fileName;
			if (eval.targetHintFile() != null && !eval.targetHintFile().isBlank()) {
				fileName = eval.targetHintFile();
				if (!fileName.endsWith(".sandbox-hint")) { //$NON-NLS-1$
					fileName = fileName + ".sandbox-hint"; //$NON-NLS-1$
				}
			} else {
				fileName = sanitizeFileName(eval.commitHash()) + ".sandbox-hint"; //$NON-NLS-1$
			}
			// Sanitize to prevent path traversal (targetHintFile may come from LLM output)
			Path baseName = Path.of(fileName).getFileName();
			fileName = baseName != null ? baseName.toString() : sanitizeFileName(eval.commitHash()) + ".sandbox-hint"; //$NON-NLS-1$
			if (fileName.isEmpty() || "..".equals(fileName)) { //$NON-NLS-1$
				fileName = sanitizeFileName(eval.commitHash()) + ".sandbox-hint"; //$NON-NLS-1$
			}
			Path hintFile = outputDir.resolve(fileName);
			if (appendRuleIfNotDuplicate(hintFile, rule)) {
				created.add(hintFile);
			}
		}

		return created;
	}

	/**
	 * Appends a rule to the given hint file if it does not already contain
	 * an equivalent rule. If the file does not exist, it is created.
	 *
	 * @param hintFile the target hint file
	 * @param newRule  the new rule text to append
	 * @return {@code true} if the rule was written (file created or updated),
	 *         {@code false} if the rule was a duplicate and skipped
	 * @throws IOException if file I/O fails
	 */
	static boolean appendRuleIfNotDuplicate(Path hintFile, String newRule) throws IOException {
		if (!Files.exists(hintFile)) {
			// New file — write directly
			Files.writeString(hintFile, newRule, StandardCharsets.UTF_8);
			return true;
		}

		// Read existing content
		String existingContent = Files.readString(hintFile, StandardCharsets.UTF_8);

		// Extract normalized signatures from existing rules
		Set<String> existingSignatures = extractRuleSignatures(existingContent);

		// Extract normalized signatures from the new rule
		Set<String> newSignatures = extractRuleSignatures(newRule);

		// Check if ALL new rule signatures already exist
		if (!newSignatures.isEmpty() && existingSignatures.containsAll(newSignatures)) {
			return false; // duplicate — skip
		}

		// Append the new rule, separated by double newline
		String separator = existingContent.endsWith("\n") ? "\n" : "\n\n"; //$NON-NLS-1$ //$NON-NLS-2$
		Files.writeString(hintFile, existingContent + separator + newRule, StandardCharsets.UTF_8);
		return true;
	}

	/**
	 * Extracts normalized rule signatures from hint file content.
	 * A signature is the source pattern + replacement pattern with comments,
	 * metadata directives, and whitespace stripped out.
	 *
	 * @param content the hint file content
	 * @return set of normalized rule signatures
	 */
	static Set<String> extractRuleSignatures(String content) {
		Set<String> signatures = new HashSet<>();
		if (content == null || content.isBlank()) {
			return signatures;
		}

		// Split on rule terminators
		String[] ruleBlocks = content.split(";;"); //$NON-NLS-1$
		for (String block : ruleBlocks) {
			String normalized = normalizeRule(block);
			if (!normalized.isEmpty()) {
				signatures.add(normalized);
			}
		}
		return signatures;
	}

	/**
	 * Normalizes a single rule block by stripping comments, metadata
	 * directives, and excess whitespace. This produces a canonical form
	 * that can be used for duplicate comparison.
	 *
	 * @param ruleBlock a single rule block (without the {@code ;;} terminator)
	 * @return the normalized rule text
	 */
	static String normalizeRule(String ruleBlock) {
		if (ruleBlock == null) {
			return ""; //$NON-NLS-1$
		}
		StringBuilder sb = new StringBuilder();
		for (String line : ruleBlock.split("\n")) { //$NON-NLS-1$
			String trimmed = line.trim();
			// Skip empty lines
			if (trimmed.isEmpty()) {
				continue;
			}
			// Skip line comments
			if (trimmed.startsWith("//")) { //$NON-NLS-1$
				continue;
			}
			// Skip metadata directives like <!id: ...> or <!description: ...>
			if (trimmed.startsWith("<!")) { //$NON-NLS-1$
				continue;
			}
			// Skip block comments (simplified: skip lines starting with /* or *)
			if (trimmed.startsWith("/*") || trimmed.startsWith("*")) { //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(trimmed);
		}
		return sb.toString();
	}

	public static String sanitizeFileName(String commitHash) {
		if (commitHash == null || commitHash.isBlank()) {
			return "unknown"; //$NON-NLS-1$
		}
		return commitHash.substring(0, Math.min(7, commitHash.length()))
			.replaceAll("[^a-zA-Z0-9_-]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}