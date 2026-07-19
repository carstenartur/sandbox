/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.mining.core.candidate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileSerializer;

/**
 * Builds canonical fingerprints for individual parsed transformation rules and
 * indexes the curated bundled rule library. File metadata, comments, and rule
 * order do not affect an individual rule fingerprint.
 */
public final class RuleFingerprintIndex {

	private RuleFingerprintIndex() {
	}

	/** Returns the canonical fingerprint of the single rule in candidate DSL. */
	public static String fingerprintDsl(String dslRule) throws IOException {
		try {
			HintFile parsed = new HintFileParser().parse(dslRule);
			if (parsed.getRules().size() != 1) {
				throw new IOException("Candidate DSL must contain exactly one rule"); //$NON-NLS-1$
			}
			return fingerprintRule(parsed.getRules().get(0));
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("Could not fingerprint candidate DSL: " + e.getMessage(), e); //$NON-NLS-1$
		}
	}

	/** Returns a stable fingerprint for one parsed transformation rule. */
	public static String fingerprintRule(TransformationRule rule) {
		HintFile singleRuleFile = new HintFile();
		singleRuleFile.addRule(rule);
		String canonical = new HintFileSerializer().serialize(singleRuleFile)
				.replace("\r\n", "\n") //$NON-NLS-1$ //$NON-NLS-2$
				.replace('\r', '\n')
				.strip();
		return "sha256:" + sha256(canonical); //$NON-NLS-1$
	}

	/**
	 * Loads all curated {@code .sandbox-hint} files below a directory.
	 *
	 * @return fingerprint to human-readable file/rule reference
	 */
	public static Map<String, String> loadCurated(Path hintDirectory) throws IOException {
		if (hintDirectory == null || !Files.isDirectory(hintDirectory)) {
			throw new IOException("Curated hint directory does not exist: " + hintDirectory); //$NON-NLS-1$
		}
		List<Path> files;
		try (Stream<Path> paths = Files.walk(hintDirectory)) {
			files = paths.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".sandbox-hint")) //$NON-NLS-1$
					.sorted()
					.toList();
		}

		Map<String, String> index = new LinkedHashMap<>();
		HintFileParser parser = new HintFileParser();
		for (Path file : files) {
			HintFile hintFile;
			try {
				hintFile = parser.parse(Files.readString(file, StandardCharsets.UTF_8));
			} catch (Exception e) {
				throw new IOException("Could not parse curated hint file " + file + ": " //$NON-NLS-1$ //$NON-NLS-2$
						+ e.getMessage(), e);
			}
			for (int i = 0; i < hintFile.getRules().size(); i++) {
				TransformationRule rule = hintFile.getRules().get(i);
				String ruleName = rule.getRuleId() == null || rule.getRuleId().isBlank()
						? "rule " + (i + 1) : rule.getRuleId(); //$NON-NLS-1$
				String relativeFile = hintDirectory.relativize(file).toString()
						.replace(File.separatorChar, '/');
				String reference = relativeFile + "#" + ruleName; //$NON-NLS-1$
				index.putIfAbsent(fingerprintRule(rule), reference);
			}
		}
		return Map.copyOf(index);
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(Character.forDigit((b >>> 4) & 0x0f, 16));
				hex.append(Character.forDigit(b & 0x0f, 16));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e); //$NON-NLS-1$
		}
	}
}
