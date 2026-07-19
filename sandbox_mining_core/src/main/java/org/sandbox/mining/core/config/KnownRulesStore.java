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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Read-compatible archive for the legacy {@code known-rules.json} format.
 *
 * <p>This store is no longer an approval registry, duplicate index, or LLM
 * prompt source. New proposals are represented exclusively by versioned
 * {@code MiningCandidate} JSON. The class remains temporarily so historic
 * reports can be loaded and archived without data loss.</p>
 *
 * @deprecated use staged candidates and their rule fingerprints
 */
@Deprecated(forRemoval = true)
public class KnownRulesStore {

	/** Legacy status retained for JSON compatibility. */
	public enum RuleStatus {
		DISCOVERED,
		IMPLEMENTED,
		REJECTED,
		NEEDS_DSL_EXTENSION
	}

	/** Legacy known-rule record retained for archival reads. */
	public static class KnownRule {
		private String id;
		private String category;
		private String dslRule;
		private String summary;
		private String discoveredAt;
		private int discoveredInRun;
		private String sourceCommit;
		private String sourceRepo;
		private String candidateId;
		private RuleStatus status;
		private String hintFile;

		public KnownRule() {
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
		public String getSourceRepo() { return sourceRepo; }
		public void setSourceRepo(String sourceRepo) { this.sourceRepo = sourceRepo; }
		public String getCandidateId() { return candidateId; }
		public void setCandidateId(String candidateId) { this.candidateId = candidateId; }
		public RuleStatus getStatus() { return status; }
		public void setStatus(RuleStatus status) { this.status = status; }
		public String getHintFile() { return hintFile; }
		public void setHintFile(String hintFile) { this.hintFile = hintFile; }
	}

	static class KnownRulesData {
		int version = 1;
		List<KnownRule> rules = new ArrayList<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final KnownRulesData data;

	public KnownRulesStore() {
		this(new KnownRulesData());
	}

	private KnownRulesStore(KnownRulesData data) {
		this.data = data;
		if (this.data.rules == null) {
			this.data.rules = new ArrayList<>();
		}
	}

	/** Loads historic data, returning an empty archive for absent/invalid files. */
	public static KnownRulesStore load(Path path) throws IOException {
		if (!Files.exists(path)) {
			return new KnownRulesStore();
		}
		try {
			KnownRulesData loaded = GSON.fromJson(
					Files.readString(path, StandardCharsets.UTF_8), KnownRulesData.class);
			return new KnownRulesStore(loaded == null ? new KnownRulesData() : loaded);
		} catch (RuntimeException e) {
			System.err.println("Warning: could not load legacy known-rules.json: " //$NON-NLS-1$
					+ e.getMessage());
			return new KnownRulesStore();
		}
	}

	/** Writes the archive without interpreting or promoting its contents. */
	public void save(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path fileName = path.getFileName();
		if (fileName == null) {
			throw new IOException("Cannot save legacy known rules to a root path"); //$NON-NLS-1$
		}
		Path temporary = path.resolveSibling(fileName + ".tmp"); //$NON-NLS-1$
		Files.writeString(temporary, GSON.toJson(data) + System.lineSeparator(),
				StandardCharsets.UTF_8);
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public List<KnownRule> getRules() {
		return List.copyOf(data.rules);
	}

	public int size() {
		return data.rules.size();
	}

	public boolean containsCommit(String commitHash) {
		return data.rules.stream().anyMatch(rule -> commitHash != null
				&& commitHash.equals(rule.sourceCommit));
	}

	/**
	 * New evaluations are deliberately not copied into this legacy store.
	 *
	 * @return always {@code 0}
	 */
	public int registerFromEvaluations(List<CommitEvaluation> evaluations, int runNumber) {
		return 0;
	}

	/**
	 * Legacy rule data is deliberately excluded from LLM prompts.
	 *
	 * @return always the empty string
	 */
	public String formatForPrompt() {
		return ""; //$NON-NLS-1$
	}

	/** Returns a read-only index for archival tooling only. */
	public Map<String, String> getCommitHashIndex() {
		Map<String, String> index = new LinkedHashMap<>();
		for (KnownRule rule : data.rules) {
			if (rule.sourceCommit != null) {
				index.put(rule.sourceCommit, rule.id);
			}
		}
		return Map.copyOf(index);
	}
}
