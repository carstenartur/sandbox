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
package org.sandbox.mining.gemini.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Parses and holds the mining configuration from a repos.yml file.
 *
 * <p>Expected YAML structure:</p>
 * <pre>
 * mining:
 *   start-date: "2024-01-01"
 *   batch-size: 50
 *   max-diff-lines-per-commit: 500
 *   timeout-per-repo-minutes: 10
 *   repositories:
 *     - url: https://github.com/example/repo.git
 *       branch: main
 *       paths:
 *         - src/main/java
 * </pre>
 */
public class MiningConfig {

	private List<RepoEntry> repositories = Collections.emptyList();
	private String startDate;
	private int batchSize = 50;
	private int maxDiffLinesPerCommit = 500;
	private int timeoutPerRepoMinutes = 10;

	public MiningConfig() {
	}

	/**
	 * Parses a repos.yml configuration file.
	 *
	 * @param configPath path to the YAML configuration file
	 * @return parsed MiningConfig
	 * @throws IOException if the file cannot be read
	 */
	public static MiningConfig parse(Path configPath) throws IOException {
		Yaml yaml = new Yaml();
		try (InputStream in = Files.newInputStream(configPath)) {
			Map<String, Object> root = yaml.load(in);
			return fromMap(root);
		}
	}

	/**
	 * Parses a repos.yml configuration from an input stream.
	 *
	 * @param in input stream containing YAML content
	 * @return parsed MiningConfig
	 */
	public static MiningConfig parse(InputStream in) {
		Yaml yaml = new Yaml();
		Map<String, Object> root = yaml.load(in);
		return fromMap(root);
	}

	@SuppressWarnings("unchecked")
	private static MiningConfig fromMap(Map<String, Object> root) {
		MiningConfig config = new MiningConfig();
		if (root == null) {
			return config;
		}

		Map<String, Object> mining = (Map<String, Object>) root.get("mining");
		if (mining == null) {
			return config;
		}

		// Parse start-date
		Object startDateObj = mining.get("start-date");
		if (startDateObj != null) {
			config.startDate = startDateObj.toString();
		}

		// Parse batch-size
		Object batchSizeObj = mining.get("batch-size");
		if (batchSizeObj instanceof Number n) {
			config.batchSize = n.intValue();
		}

		// Parse max-diff-lines-per-commit
		Object maxDiffObj = mining.get("max-diff-lines-per-commit");
		if (maxDiffObj instanceof Number n) {
			config.maxDiffLinesPerCommit = n.intValue();
		}

		// Parse timeout
		Object timeoutObj = mining.get("timeout-per-repo-minutes");
		if (timeoutObj instanceof Number n) {
			config.timeoutPerRepoMinutes = n.intValue();
		}

		// Parse repositories
		Object reposObj = mining.get("repositories");
		if (reposObj instanceof List<?> reposList) {
			config.repositories = reposList.stream().map(obj -> {
				if (obj instanceof Map<?, ?> map) {
					RepoEntry entry = new RepoEntry();
					entry.setUrl((String) map.get("url"));
					Object branchObj = map.get("branch");
					if (branchObj != null) {
						entry.setBranch(branchObj.toString());
					}
					Object pathsObj = map.get("paths");
					if (pathsObj instanceof List<?> pathsList) {
						entry.setPaths(pathsList.stream().map(Object::toString).toList());
					}
					return entry;
				}
				return null;
			}).filter(e -> e != null).toList();
		}

		return config;
	}

	public List<RepoEntry> getRepositories() {
		return repositories;
	}

	public void setRepositories(List<RepoEntry> repositories) {
		this.repositories = repositories != null ? repositories : Collections.emptyList();
	}

	public String getStartDate() {
		return startDate;
	}

	public void setStartDate(String startDate) {
		this.startDate = startDate;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public int getMaxDiffLinesPerCommit() {
		return maxDiffLinesPerCommit;
	}

	public void setMaxDiffLinesPerCommit(int maxDiffLinesPerCommit) {
		this.maxDiffLinesPerCommit = maxDiffLinesPerCommit;
	}

	public int getTimeoutPerRepoMinutes() {
		return timeoutPerRepoMinutes;
	}

	public void setTimeoutPerRepoMinutes(int timeoutPerRepoMinutes) {
		this.timeoutPerRepoMinutes = timeoutPerRepoMinutes;
	}
}
