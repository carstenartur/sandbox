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
package org.sandbox.jdt.triggerpattern.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks a directory tree and collects all {@code .sandbox-hint} files,
 * returning their content formatted as context for an LLM prompt.
 */
public class DslContextCollector {

	private static final String HINT_FILE_EXTENSION = ".sandbox-hint";

	/**
	 * Collects DSL context from all hint files under the given root directory.
	 *
	 * @param sandboxRoot the root directory to search
	 * @return formatted string containing all hint file contents
	 * @throws IOException if an I/O error occurs
	 */
	public String collectContext(Path sandboxRoot) throws IOException {
		if (!Files.isDirectory(sandboxRoot)) {
			return "";
		}

		List<Path> hintFiles = findHintFiles(sandboxRoot);
		if (hintFiles.isEmpty()) {
			return "(no existing DSL rules found)";
		}

		StringBuilder sb = new StringBuilder();
		for (Path hintFile : hintFiles) {
			sb.append("### ");
			sb.append(sandboxRoot.relativize(hintFile));
			sb.append("\n```\n");
			sb.append(Files.readString(hintFile, StandardCharsets.UTF_8));
			sb.append("\n```\n\n");
		}
		return sb.toString();
	}

	/**
	 * Finds all .sandbox-hint files under the given directory.
	 *
	 * @param root the root directory to search
	 * @return list of hint file paths
	 * @throws IOException if an I/O error occurs
	 */
	List<Path> findHintFiles(Path root) throws IOException {
		List<Path> result = new ArrayList<>();
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				Path fileName = file.getFileName();
				if (fileName != null && fileName.toString().endsWith(HINT_FILE_EXTENSION)) {
					result.add(file);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
		return result;
	}
}
