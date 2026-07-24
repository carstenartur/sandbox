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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable byte-level snapshot of every file participating in one project
 * cleanup transaction.
 */
final class ProjectSnapshot {

	/** One immutable original file image. */
	record Entry(Path path, byte[] content, String sha256) {
		Entry {
			path= Objects.requireNonNull(path, "path"); //$NON-NLS-1$
			content= content.clone();
			sha256= Objects.requireNonNull(sha256, "sha256"); //$NON-NLS-1$
		}

		@Override
		public byte[] content() {
			return content.clone();
		}
	}

	/** Verification result for one path after an operation. */
	record Verification(Path path, boolean restored, String expectedSha256, String actualSha256, String message) {
		Verification {
			message= message == null ? "" : message; //$NON-NLS-1$
		}
	}

	/** Aggregated restore failure after every snapshot entry was attempted. */
	static final class RestoreException extends IOException {
		private static final long serialVersionUID= 1L;
		private final List<Verification> failures;

		RestoreException(List<Verification> failures) {
			super("Could not restore or verify " + failures.size() + " project snapshot file(s)"); //$NON-NLS-1$ //$NON-NLS-2$
			this.failures= List.copyOf(failures);
		}

		List<Verification> failures() {
			return failures;
		}
	}

	private final List<Entry> entries;

	private ProjectSnapshot(List<Entry> entries) {
		this.entries= List.copyOf(entries);
	}

	static ProjectSnapshot capture(Collection<File> files) throws IOException {
		if (files == null) {
			throw new NullPointerException("files"); //$NON-NLS-1$
		}
		Map<String, Path> paths= new LinkedHashMap<>();
		for (File file : files) {
			if (file == null) {
				throw new IOException("Project snapshot contains a null file entry"); //$NON-NLS-1$
			}
			Path path= file.toPath().toRealPath();
			if (!Files.isRegularFile(path)) {
				throw new IOException("Project snapshot input is not a regular file: " + stablePath(path)); //$NON-NLS-1$
			}
			paths.putIfAbsent(stablePath(path), path);
		}
		List<Entry> entries= new ArrayList<>();
		for (Map.Entry<String, Path> item : paths.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			byte[] content= Files.readAllBytes(item.getValue());
			entries.add(new Entry(item.getValue(), content, sha256(content)));
		}
		return new ProjectSnapshot(entries);
	}

	List<Entry> entries() {
		return entries;
	}

	/** Returns deterministic verification results without changing any file. */
	List<Verification> verify() {
		List<Verification> results= new ArrayList<>();
		for (Entry entry : entries) {
			try {
				byte[] current= Files.readAllBytes(entry.path());
				String actual= sha256(current);
				boolean restored= MessageDigest.isEqual(entry.content, current);
				results.add(new Verification(entry.path(), restored, entry.sha256(), actual,
						restored ? "" : "File content differs from the captured snapshot")); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (IOException exception) {
				results.add(new Verification(entry.path(), false, entry.sha256(), "", //$NON-NLS-1$
						exception.getMessage()));
			}
		}
		return List.copyOf(results);
	}

	/**
	 * Restores every file, even when an earlier write fails, and then verifies every
	 * path. A single aggregated exception is thrown only after all entries have been
	 * attempted.
	 */
	List<Verification> restoreAndVerify() throws RestoreException {
		Map<Path, String> writeFailures= new LinkedHashMap<>();
		for (Entry entry : entries) {
			try {
				Files.write(entry.path(), entry.content,
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			} catch (IOException exception) {
				writeFailures.put(entry.path(), exception.getMessage());
			}
		}

		List<Verification> results= new ArrayList<>();
		for (Verification verification : verify()) {
			String writeFailure= writeFailures.get(verification.path());
			if (writeFailure == null) {
				results.add(verification);
			} else {
				String message= verification.message().isEmpty()
						? "Restore write failed: " + writeFailure //$NON-NLS-1$
						: "Restore write failed: " + writeFailure + "; verification: " + verification.message(); //$NON-NLS-1$ //$NON-NLS-2$
				results.add(new Verification(verification.path(), false, verification.expectedSha256(),
						verification.actualSha256(), message));
			}
		}
		List<Verification> failures= results.stream().filter(result -> !result.restored()).toList();
		if (!failures.isEmpty()) {
			throw new RestoreException(failures);
		}
		return List.copyOf(results);
	}

	private static String sha256(byte[] content) {
		try {
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception); //$NON-NLS-1$
		}
	}

	private static String stablePath(Path path) {
		return path.toAbsolutePath().normalize().toString().replace(File.separatorChar, '/');
	}
}
