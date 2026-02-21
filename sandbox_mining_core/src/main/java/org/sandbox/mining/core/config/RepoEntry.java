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

import java.util.Collections;
import java.util.List;

/**
 * POJO representing a single repository entry in the mining configuration.
 */
public class RepoEntry {

	private String url;
	private String branch = "main";
	private List<String> paths = Collections.emptyList();

	public RepoEntry() {
	}

	public RepoEntry(String url, String branch, List<String> paths) {
		this.url = url;
		this.branch = branch;
		this.paths = paths != null ? paths : Collections.emptyList();
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getBranch() {
		return branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	public List<String> getPaths() {
		return paths;
	}

	public void setPaths(List<String> paths) {
		this.paths = paths != null ? paths : Collections.emptyList();
	}
}
