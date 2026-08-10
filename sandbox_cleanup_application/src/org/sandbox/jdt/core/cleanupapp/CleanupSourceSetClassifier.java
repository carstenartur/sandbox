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

import java.nio.file.Files;
import java.nio.file.Path;

/** Classifies Java files by conventional main/test source-set roots. */
final class CleanupSourceSetClassifier {

	private enum SourceSet {
		MAIN, TEST, UNKNOWN
	}

	private final Path requestedRoot;
	private final Path projectRoot;
	private final SourceSet rootSourceSet;

	private CleanupSourceSetClassifier(Path requestedRoot) {
		this.requestedRoot= requestedRoot.toAbsolutePath().normalize();
		this.projectRoot= findProjectRoot(this.requestedRoot);
		this.rootSourceSet= sourceSetContaining(this.requestedRoot);
	}

	static CleanupSourceSetClassifier create(Path requestedRoot) {
		return new CleanupSourceSetClassifier(requestedRoot);
	}

	boolean isTestSource(Path path) {
		Path normalizedPath= path.toAbsolutePath().normalize();
		if (projectRoot != null && normalizedPath.startsWith(projectRoot)) {
			SourceSet projectSourceSet= sourceSetWithin(projectRoot.relativize(normalizedPath), true);
			return projectSourceSet == SourceSet.TEST;
		}
		if (rootSourceSet != SourceSet.UNKNOWN) {
			return rootSourceSet == SourceSet.TEST;
		}
		if (isTestSegment(requestedRoot.getFileName())) {
			return true;
		}
		if (normalizedPath.startsWith(requestedRoot)) {
			boolean rootIsSourceContainer= "src".equals(fileName(requestedRoot)); //$NON-NLS-1$
			return sourceSetWithin(requestedRoot.relativize(normalizedPath), rootIsSourceContainer)
					== SourceSet.TEST;
		}
		return false;
	}

	private static Path findProjectRoot(Path start) {
		for (Path current= start; current != null; current= current.getParent()) {
			if (Files.isRegularFile(current.resolve(".project"))) { //$NON-NLS-1$
				return current;
			}
		}
		return null;
	}

	private static SourceSet sourceSetContaining(Path path) {
		for (int index= path.getNameCount() - 2; index >= 0; index--) {
			if (!"src".equals(path.getName(index).toString())) { //$NON-NLS-1$
				continue;
			}
			SourceSet sourceSet= sourceSet(path.getName(index + 1).toString());
			if (sourceSet != SourceSet.UNKNOWN) {
				return sourceSet;
			}
		}
		return SourceSet.UNKNOWN;
	}

	private static SourceSet sourceSetWithin(Path path, boolean leadingTestDirectory) {
		if (leadingTestDirectory && path.getNameCount() > 0 && isTestSegment(path.getName(0))) {
			return SourceSet.TEST;
		}
		for (int index= 0; index + 1 < path.getNameCount(); index++) {
			if (!"src".equals(path.getName(index).toString())) { //$NON-NLS-1$
				continue;
			}
			SourceSet sourceSet= sourceSet(path.getName(index + 1).toString());
			if (sourceSet != SourceSet.UNKNOWN) {
				return sourceSet;
			}
		}
		return SourceSet.UNKNOWN;
	}

	private static SourceSet sourceSet(String name) {
		if ("main".equals(name)) { //$NON-NLS-1$
			return SourceSet.MAIN;
		}
		return isTestSegment(name) ? SourceSet.TEST : SourceSet.UNKNOWN;
	}

	private static boolean isTestSegment(Path segment) {
		return segment != null && isTestSegment(segment.toString());
	}

	private static boolean isTestSegment(String name) {
		return "test".equals(name) || "tests".equals(name); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String fileName(Path path) {
		Path fileName= path.getFileName();
		return fileName == null ? "" : fileName.toString(); //$NON-NLS-1$
	}
}
