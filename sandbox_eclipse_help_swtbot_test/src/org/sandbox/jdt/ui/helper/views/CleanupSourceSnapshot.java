/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Byte-exact source inventory; additions and deletions are changes, too. */
final class CleanupSourceSnapshot {

	private final Map<String, byte[]> files;

	CleanupSourceSnapshot(Map<String, byte[]> sourceFiles) {
		Map<String, byte[]> copy= new TreeMap<>();
		sourceFiles.forEach((path, bytes) -> copy.put(Objects.requireNonNull(path),
				Objects.requireNonNull(bytes).clone()));
		files= Collections.unmodifiableMap(copy);
	}

	Set<String> paths() {
		return files.keySet();
	}

	Set<String> changedPaths(CleanupSourceSnapshot after) {
		Set<String> changed= new TreeSet<>(files.keySet());
		changed.addAll(after.files.keySet());
		changed.removeIf(path -> Arrays.equals(files.get(path), after.files.get(path)));
		return Collections.unmodifiableSet(changed);
	}
}
