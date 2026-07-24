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
package org.sandbox.jdt.core.cleanupapp;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.internal.core.util.Util;

/**
 * Expands command-line files and directories into one deterministic, canonical
 * Java source selection without modifying workspace or file contents.
 */
final class CleanupInputCollector {

	private static final Set<String> TEST_SEGMENTS= Set.of(
			"test", "tests", "testfixtures", "integrationtest", "integration-test"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

	/** One input that could not be inspected safely. */
	record Diagnostic(String input, String message) {
		Diagnostic {
			input= input == null ? "" : input; //$NON-NLS-1$
			message= message == null ? "" : message; //$NON-NLS-1$
		}
	}

	/** Stable result of the discovery phase. */
	record Result(List<File> files, List<Diagnostic> diagnostics) {
		Result {
			files= List.copyOf(files);
			diagnostics= List.copyOf(diagnostics);
		}
	}

	Result collect(Collection<File> inputs, CodeCleanupApplication.CleanupScope scope) {
		if (scope == null) {
			throw new NullPointerException("scope"); //$NON-NLS-1$
		}
		Map<String, File> filesByCanonicalPath= new LinkedHashMap<>();
		List<Diagnostic> diagnostics= new ArrayList<>();
		if (inputs == null) {
			return new Result(List.of(), List.of(new Diagnostic("", "Input collection is null"))); //$NON-NLS-1$ //$NON-NLS-2$
		}
		for (File input : inputs) {
			if (input == null) {
				diagnostics.add(new Diagnostic("", "Input entry is null")); //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}
			Path root;
			try {
				root= input.toPath().toRealPath();
			} catch (IOException exception) {
				diagnostics.add(new Diagnostic(input.getPath(), exception.getMessage()));
				continue;
			}
			if (Files.isDirectory(root)) {
				collectDirectory(root, scope, filesByCanonicalPath, diagnostics);
			} else {
				addCandidate(root, scope, filesByCanonicalPath, diagnostics);
			}
		}
		List<File> files= new ArrayList<>(filesByCanonicalPath.values());
		files.sort(Comparator.comparing(file -> stablePath(file.toPath())));
		diagnostics.sort(Comparator.comparing(Diagnostic::input).thenComparing(Diagnostic::message));
		return new Result(files, diagnostics);
	}

	private static void collectDirectory(Path root, CodeCleanupApplication.CleanupScope scope,
			Map<String, File> filesByCanonicalPath, List<Diagnostic> diagnostics) {
		try (Stream<Path> paths= Files.walk(root)) {
			paths.filter(Files::isRegularFile)
					.sorted(Comparator.comparing(CleanupInputCollector::stablePath))
					.forEach(path -> addCandidate(path, scope, filesByCanonicalPath, diagnostics));
		} catch (IOException | UncheckedIOException exception) {
			diagnostics.add(new Diagnostic(stablePath(root), exception.getMessage()));
		}
	}

	private static void addCandidate(Path path, CodeCleanupApplication.CleanupScope scope,
			Map<String, File> filesByCanonicalPath, List<Diagnostic> diagnostics) {
		if (!Util.isJavaLikeFileName(path.toString()) || !includes(scope, path)) {
			return;
		}
		try {
			Path canonical= path.toRealPath();
			filesByCanonicalPath.putIfAbsent(stablePath(canonical), canonical.toFile());
		} catch (IOException exception) {
			diagnostics.add(new Diagnostic(stablePath(path), exception.getMessage()));
		}
	}

	private static boolean includes(CodeCleanupApplication.CleanupScope scope, Path path) {
		boolean testSource= isTestPath(path);
		return switch (scope) {
			case BOTH -> true;
			case MAIN -> !testSource;
			case TEST -> testSource;
		};
	}

	private static String stablePath(Path path) {
		return path.toAbsolutePath().normalize().toString().replace(File.separatorChar, '/');
	}

	static boolean isTestPath(Path path) {
		Path normalized= path.normalize();
		int lastSourceSegment= -1;
		for (int index= 0; index < normalized.getNameCount(); index++) {
			String value= normalized.getName(index).toString().toLowerCase(Locale.ROOT);
			if ("src".equals(value)) { //$NON-NLS-1$
				lastSourceSegment= index;
			}
		}
		if (lastSourceSegment >= 0) {
			if (lastSourceSegment + 1 >= normalized.getNameCount()) {
				return false;
			}
			String sourceSet= normalized.getName(lastSourceSegment + 1).toString().toLowerCase(Locale.ROOT);
			return TEST_SEGMENTS.contains(sourceSet);
		}
		for (Path segment : normalized) {
			if (TEST_SEGMENTS.contains(segment.toString().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}
}
