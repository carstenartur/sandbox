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
package org.sandbox.mining.config;

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
 */
public class MiningConfig {

	private List<String> hints = Collections.emptyList();
	private List<RepoEntry> repositories = Collections.emptyList();
	private int maxFilesPerRepo = 5000;
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
	@SuppressWarnings("unchecked")
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
	@SuppressWarnings("unchecked")
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

		// Parse hints
		Object hintsObj = mining.get("hints");
		if (hintsObj instanceof List<?> hintsList) {
			config.hints = hintsList.stream().map(Object::toString).toList();
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

		// Parse settings
		Object settingsObj = mining.get("settings");
		if (settingsObj instanceof Map<?, ?> settings) {
			Object maxFiles = settings.get("max-files-per-repo");
			if (maxFiles instanceof Number n) {
				config.maxFilesPerRepo = n.intValue();
			}
			Object timeout = settings.get("timeout-per-repo-minutes");
			if (timeout instanceof Number n) {
				config.timeoutPerRepoMinutes = n.intValue();
			}
		}

		return config;
	}

	public List<String> getHints() {
		return hints;
	}

	public void setHints(List<String> hints) {
		this.hints = hints != null ? hints : Collections.emptyList();
	}

	public List<RepoEntry> getRepositories() {
		return repositories;
	}

	public void setRepositories(List<RepoEntry> repositories) {
		this.repositories = repositories != null ? repositories : Collections.emptyList();
	}

	public int getMaxFilesPerRepo() {
		return maxFilesPerRepo;
	}

	public void setMaxFilesPerRepo(int maxFilesPerRepo) {
		this.maxFilesPerRepo = maxFilesPerRepo;
	}

	public int getTimeoutPerRepoMinutes() {
		return timeoutPerRepoMinutes;
	}

	public void setTimeoutPerRepoMinutes(int timeoutPerRepoMinutes) {
		this.timeoutPerRepoMinutes = timeoutPerRepoMinutes;
	}
}
