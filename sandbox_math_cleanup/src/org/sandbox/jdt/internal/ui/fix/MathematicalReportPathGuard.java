/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Preflight for report paths. Reports belong outside projects and workspace
 * metadata, including linked locations. This is not filesystem race isolation.
 */
final class MathematicalReportPathGuard {
	private MathematicalReportPathGuard() {
	}

	static void requireSafe(Path destination, List<Path> protectedPaths, List<Path> protectedRoots) throws IOException {
		Path report = destination.toAbsolutePath().normalize();
		if (report.getFileName() == null || Files.isDirectory(report)) {
			throw new IllegalArgumentException("The report destination must name a file: " + report); //$NON-NLS-1$
		}
		Path resolvedReport = resolveExistingAncestor(report);
		for (Path protectedRoot : protectedRoots) {
			Path root = protectedRoot.toAbsolutePath().normalize();
			if (report.startsWith(root) || resolvedReport.startsWith(resolveExistingAncestor(root))) {
				throw protectedDestination(report);
			}
		}
		for (Path protectedPath : protectedPaths) {
			Path path = protectedPath.toAbsolutePath().normalize();
			if (report.equals(path) || resolvedReport.equals(resolveExistingAncestor(path))
					|| Files.exists(report) && Files.exists(path) && Files.isSameFile(report, path)) {
				throw protectedDestination(report);
			}
		}
	}

	private static IllegalArgumentException protectedDestination(Path report) {
		return new IllegalArgumentException(
				"The report must be outside project resources and workspace metadata, and must not overwrite configuration: " + report); //$NON-NLS-1$
	}

	/**
	 * Resolve symbolic parents even when the output file/directories do not exist.
	 * Only a genuinely absent path permits walking upwards. Unreadable paths,
	 * dangling links and symbolic-link loops fail closed.
	 */
	private static Path resolveExistingAncestor(Path path) throws IOException {
		ArrayDeque<Path> missing = new ArrayDeque<>();
		Path ancestor = path;
		while (ancestor != null) {
			try {
				Files.readAttributes(ancestor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			} catch (NoSuchFileException absent) {
				Path name = ancestor.getFileName();
				if (name == null) {
					throw absent;
				}
				missing.addFirst(name);
				ancestor = ancestor.getParent();
				continue;
			}
			// Keep toRealPath outside the catch: a dangling symlink is not absence.
			Path resolved = ancestor.toRealPath();
			for (Path name : missing) {
				resolved = resolved.resolve(name);
			}
			return resolved;
		}
		throw new IOException("Cannot resolve report-path ancestry: " + path); //$NON-NLS-1$
	}
}
