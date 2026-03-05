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
package org.sandbox.mining.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

/**
 * Persistent store for known mining rules ({@code known-rules.json}).
 *
 * <p>Tracks all rules ever discovered and validated across mining runs.
 * GREEN+VALID evaluations are registered after each run so the LLM can
 * be informed about already-known rules and avoid proposing duplicates.</p>
 *
 * @since 1.3.2
 */
public class KnownRulesStore {

	/** Status of a known rule. */
	public enum RuleStatus {
		/** Discovered by mining but not yet implemented as a .sandbox-hint file. */
		DISCOVERED,
		/** Implemented as a .sandbox-hint rule. */
		IMPLEMENTED,
		/** Manually rejected by a reviewer. */
		REJECTED,
		/** Requires a DSL language extension before it can be implemented. */
		NEEDS_DSL_EXTENSION
	}

	/**
	 * A single known rule entry.
	 */
	public static class KnownRule {
		private String id;
		private String category;
		private String dslRule;
		private String summary;
		private String discoveredAt;
		private int discoveredInRun;
		private String sourceCommit;
		private RuleStatus status;
		private String hintFile;

		/** No-arg constructor for Gson. */
		public KnownRule() {
		}

		public KnownRule(String id, String category, String dslRule, String summary,
				String discoveredAt, int discoveredInRun, String sourceCommit,
				RuleStatus status, String hintFile) {
			this.id = id;
			this.category = category;
			this.dslRule = dslRule;
			this.summary = summary;
			this.discoveredAt = discoveredAt;
			this.discoveredInRun = discoveredInRun;
			this.sourceCommit = sourceCommit;
			this.status = status;
			this.hintFile = hintFile;
		}

		public String getId() { return id; }
		public void setId(String id) { this.id = id; }
		public String getCategory() { return category; }
		public void setCategory(String category) { this.category = category; }
		public String getDslRule() { return dslRule; }
		public void setDslRule(String dslRule) { this.dslRule = dslRule; }
		public String getSummary() { return summary; }
		public void setSummary(String summary) { this.summary = summary; }
		public String getDiscoveredAt() { return discoveredAt; }
		public void setDiscoveredAt(String discoveredAt) { this.discoveredAt = discoveredAt; }
		public int getDiscoveredInRun() { return discoveredInRun; }
		public void setDiscoveredInRun(int discoveredInRun) { this.discoveredInRun = discoveredInRun; }
		public String getSourceCommit() { return sourceCommit; }
		public void setSourceCommit(String sourceCommit) { this.sourceCommit = sourceCommit; }
		public RuleStatus getStatus() { return status; }
		public void setStatus(RuleStatus status) { this.status = status; }
		public String getHintFile() { return hintFile; }
		public void setHintFile(String hintFile) { this.hintFile = hintFile; }
	}

	/**
	 * Root JSON structure for known-rules.json.
	 */
	static class KnownRulesData {
		int version = 1;
		List<KnownRule> rules = new ArrayList<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final KnownRulesData data;

	/** Creates a new empty store. */
	public KnownRulesStore() {
		this.data = new KnownRulesData();
	}

	private KnownRulesStore(KnownRulesData data) {
		this.data = data;
	}

	/**
	 * Loads the store from a JSON file, or returns a new empty store if the file
	 * does not exist.
	 *
	 * @param path path to known-rules.json
	 * @return the loaded store
	 * @throws IOException if the file cannot be read
	 */
	public static KnownRulesStore load(Path path) throws IOException {
		if (!Files.exists(path)) {
			return new KnownRulesStore();
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			KnownRulesData loaded = GSON.fromJson(json, KnownRulesData.class);
			return new KnownRulesStore(loaded != null ? loaded : new KnownRulesData());
		} catch (Exception e) {
			System.err.println("Warning: could not load known-rules.json: " + e.getMessage()); //$NON-NLS-1$
			return new KnownRulesStore();
		}
	}

	/**
	 * Saves the store to a JSON file atomically.
	 *
	 * @param path path to known-rules.json
	 * @throws IOException if the file cannot be written
	 */
	public void save(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path fileName = path.getFileName();
		if (fileName == null) {
			throw new IOException("Cannot save known rules to a root path"); //$NON-NLS-1$
		}
		Path tmpFile = path.resolveSibling(fileName + ".tmp"); //$NON-NLS-1$
		String json = GSON.toJson(data);
		Files.writeString(tmpFile, json, StandardCharsets.UTF_8);
		Files.move(tmpFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/**
	 * Returns all known rules.
	 *
	 * @return unmodifiable list of known rules
	 */
	public List<KnownRule> getRules() {
		return List.copyOf(data.rules);
	}

	/**
	 * Returns the number of known rules.
	 *
	 * @return count
	 */
	public int size() {
		return data.rules.size();
	}

	/**
	 * Checks whether a rule with the given commit hash already exists.
	 *
	 * @param commitHash the commit hash
	 * @return true if a rule from this commit is already known
	 */
	public boolean containsCommit(String commitHash) {
		return data.rules.stream().anyMatch(r -> commitHash.equals(r.sourceCommit));
	}

	/**
	 * Registers GREEN+VALID evaluations from a mining run as known rules.
	 * Evaluations whose commit hash is already present are skipped.
	 *
	 * @param evaluations all evaluations from the current run
	 * @param runNumber   the mining run number
	 * @return the number of newly registered rules
	 */
	public int registerFromEvaluations(List<CommitEvaluation> evaluations, int runNumber) {
		int added = 0;
		for (CommitEvaluation eval : evaluations) {
			if (!isGreenAndValid(eval)) {
				continue;
			}
			if (containsCommit(eval.commitHash())) {
				continue;
			}
			String ruleId = buildRuleId(eval);
			KnownRule rule = new KnownRule(
					ruleId,
					eval.category(),
					eval.dslRule(),
					eval.summary(),
					LocalDate.now().toString(),
					runNumber,
					eval.commitHash(),
					RuleStatus.DISCOVERED,
					eval.targetHintFile());
			data.rules.add(rule);
			added++;
		}
		return added;
	}

	/**
	 * Formats the known rules as a concise text summary suitable for LLM context.
	 *
	 * @return summary string, or empty string if no rules are known
	 */
	public String formatForPrompt() {
		if (data.rules.isEmpty()) {
			return ""; //$NON-NLS-1$
		}
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for (int i = 0; i < data.rules.size(); i++) {
			KnownRule r = data.rules.get(i);
			if (i > 0) {
				sb.append(',');
			}
			sb.append("\n  {\"id\":\"").append(escape(r.id)) //$NON-NLS-1$
			  .append("\",\"category\":\"").append(escape(r.category)) //$NON-NLS-1$
			  .append("\",\"summary\":\"").append(escape(r.summary)) //$NON-NLS-1$
			  .append("\",\"dslRule\":\"").append(escape(r.dslRule)) //$NON-NLS-1$
			  .append("\"}"); //$NON-NLS-1$
		}
		sb.append("\n]"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Returns a map of commit hashes to their rule IDs for quick lookup.
	 *
	 * @return map of commitHash → ruleId
	 */
	public Map<String, String> getCommitHashIndex() {
		Map<String, String> index = new LinkedHashMap<>();
		for (KnownRule rule : data.rules) {
			if (rule.sourceCommit != null) {
				index.put(rule.sourceCommit, rule.id);
			}
		}
		return index;
	}

	private static boolean isGreenAndValid(CommitEvaluation eval) {
		if (eval == null || !eval.relevant()) {
			return false;
		}
		if (eval.trafficLight() != CommitEvaluation.TrafficLight.GREEN) {
			return false;
		}
		if (eval.dslRule() == null || eval.dslRule().isBlank()) {
			return false;
		}
		return "VALID".equals(eval.dslValidationResult()); //$NON-NLS-1$
	}

	private static String buildRuleId(CommitEvaluation eval) {
		String category = eval.category() != null ? eval.category().toLowerCase().replace(' ', '-') : "unknown"; //$NON-NLS-1$
		String summary = eval.summary() != null
				? eval.summary().toLowerCase().replaceAll("[^a-z0-9]+", "-") //$NON-NLS-1$ //$NON-NLS-2$
				: "rule"; //$NON-NLS-1$
		if (summary.length() > 40) {
			summary = summary.substring(0, 40);
		}
		if (summary.endsWith("-")) { //$NON-NLS-1$
			summary = summary.substring(0, summary.length() - 1);
		}
		return category + "-" + summary; //$NON-NLS-1$
	}

	private static String escape(String s) {
		if (s == null) {
			return ""; //$NON-NLS-1$
		}
		return s.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\"", "\\\"") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\n", "\\n") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\r", "\\r") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\t", "\\t"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
