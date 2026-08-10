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
package org.sandbox.jdt.internal.css.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.sandbox.jdt.internal.css.CSSCleanupPlugin;
import org.sandbox.jdt.internal.css.preferences.CSSPreferenceConstants;

/** Runs Prettier to format CSS files. */
public class PrettierRunner {

	/** Check if Prettier is available via npx. */
	public static boolean isPrettierAvailable() {
		if (!NodeExecutor.isNpxAvailable()) {
			return false;
		}
		try {
			NodeExecutor.ExecutionResult result = NodeExecutor.executeNpx("prettier", "--version"); //$NON-NLS-1$ //$NON-NLS-2$
			return result.isSuccess();
		} catch (Exception e) {
			return false;
		}
	}

	/** Format a CSS file using Prettier and the configured JSON options. */
	public static String format(IFile file) throws IOException, InterruptedException {
		String optionsJson = CSSCleanupPlugin.getDefault().getPreferenceStore()
				.getString(CSSPreferenceConstants.PRETTIER_OPTIONS);
		return format(file, optionsJson);
	}

	static String format(IFile file, String optionsJson) throws IOException, InterruptedException {
		if (!NodeExecutor.isNpxAvailable()) {
			throw new IllegalStateException("npx is not available. Please install Node.js and npm."); //$NON-NLS-1$
		}

		Path sourcePath = localPath(file);
		String normalizedOptions = normalizeAndValidateOptions(optionsJson);
		String originalContent = Files.readString(sourcePath, StandardCharsets.UTF_8);
		Path configFile = null;
		try {
			if (!"{}".equals(normalizedOptions)) { //$NON-NLS-1$
				configFile = createTemporaryConfig(sourcePath, normalizedOptions);
			}
			List<String> args = buildArguments(sourcePath.toString(), configFile != null ? configFile.toString() : null);
			NodeExecutor.ExecutionResult result = NodeExecutor.executeNpxWithInput(
					originalContent, args.toArray(String[]::new));
			if (!result.isSuccess()) {
				throw new IOException(toolFailure("Prettier", result)); //$NON-NLS-1$
			}
			if (result.stdout.isBlank()) {
				throw new IOException("Prettier returned no formatted CSS output"); //$NON-NLS-1$
			}
			return result.stdout;
		} finally {
			if (configFile != null) {
				Files.deleteIfExists(configFile);
			}
		}
	}

	/** Validates that the preference is a JSON object and canonicalizes blank/empty input. */
	public static String normalizeAndValidateOptions(String optionsJson) {
		String normalized = optionsJson == null || optionsJson.isBlank() ? "{}" : optionsJson.trim(); //$NON-NLS-1$
		Map<String, Object> parsed = SimpleJsonParser.parseObject(normalized);
		return parsed.isEmpty() ? "{}" : normalized; //$NON-NLS-1$
	}

	static List<String> buildArguments(String filePath, String configPath) {
		List<String> args = new ArrayList<>(List.of(
				"prettier", "--stdin-filepath", filePath)); //$NON-NLS-1$ //$NON-NLS-2$
		if (configPath != null && !configPath.isBlank()) {
			args.add("--config"); //$NON-NLS-1$
			args.add(configPath);
		}
		return List.copyOf(args);
	}

	private static Path createTemporaryConfig(Path sourcePath, String optionsJson) throws IOException {
		Path parent = sourcePath.getParent();
		Path configFile = parent != null
				? Files.createTempFile(parent, ".sandbox-prettier-", ".json") //$NON-NLS-1$ //$NON-NLS-2$
				: Files.createTempFile("sandbox-prettier-", ".json"); //$NON-NLS-1$ //$NON-NLS-2$
		Files.writeString(configFile, optionsJson, StandardCharsets.UTF_8);
		return configFile;
	}

	private static Path localPath(IFile file) throws IOException {
		if (file.getLocation() == null) {
			throw new IOException("CSS file has no local filesystem location: " + file.getFullPath()); //$NON-NLS-1$
		}
		return file.getLocation().toFile().toPath();
	}

	private static String toolFailure(String tool, NodeExecutor.ExecutionResult result) {
		String detail = !result.stderr.isBlank() ? result.stderr.trim() : result.stdout.trim();
		return detail.isBlank()
				? tool + " failed with exit code " + result.exitCode //$NON-NLS-1$
				: tool + " failed with exit code " + result.exitCode + ": " + detail; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
