package org.sandbox.jdt.core.cleanupapp;

/*-
 * #%L
 * Sandbox cleanup application
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class CodeCleanupApplication implements IApplication {
	private static final File[] FILES = new File[0];

	private static final String ARG_CONFIG = "-config"; //$NON-NLS-1$

	private static final String ARG_HELP = "-help"; //$NON-NLS-1$

	private static final String ARG_QUIET = "-quiet"; //$NON-NLS-1$

	private static final String ARG_VERBOSE = "-verbose"; //$NON-NLS-1$

	private static final String ARG_MODE = "--mode"; //$NON-NLS-1$

	private static final String ARG_SOURCE = "--source"; //$NON-NLS-1$

	private static final String ARG_SCOPE = "--scope"; //$NON-NLS-1$

	private static final String ARG_PATCH = "--patch"; //$NON-NLS-1$

	private static final String ARG_REPORT = "--report"; //$NON-NLS-1$

	/** Exit code: success, no changes needed (check) or applied (apply). */
	static final int EXIT_OK = 0;

	/** Exit code: error (parsing, IO, config invalid, etc.). */
	static final int EXIT_ERROR = 1;

	/** Exit code: changes detected/needed (check/diff mode). */
	static final int EXIT_CHANGES = 2;

	/**
	 * Execution mode for the cleanup application.
	 */
	enum CleanupMode {
		/** Apply changes to files (default, backwards compatible). */
		APPLY,
		/** Check for changes without modifying files; exit 2 if changes needed. */
		CHECK,
		/** Generate unified diff output without modifying files. */
		DIFF
	}

	/**
	 * Scope filter for source files.
	 */
	enum CleanupScope {
		MAIN, TEST, BOTH
	}

	private String configName;

	private Map<String, String> options = null;

	private static final String PDE_LAUNCH = "-pdelaunch"; //$NON-NLS-1$

	private boolean quiet = false;

	private boolean verbose = false;

	CleanupMode cleanupMode = CleanupMode.APPLY;

	CleanupScope cleanupScope = CleanupScope.BOTH;

	String patchFile = null;

	String reportFile = null;

	private static final int INITIALSIZE = 1;

	private static final int DEFAULT_PARSE_MODE = 0;

	private static final int CONFIG_PARSE_MODE = 1;

	private static final int MODE_PARSE_MODE = 2;

	private static final int SOURCE_PARSE_MODE = 3;

	private static final int SCOPE_PARSE_MODE = 4;

	private static final int PATCH_PARSE_MODE = 5;

	private static final int REPORT_PARSE_MODE = 6;

	private final List<String> changedFiles = new ArrayList<>();

	private final StringBuilder patchContent = new StringBuilder();

	private int filesProcessed = 0;


	/**
	 * Clean up the given Java source file. In CHECK/DIFF modes, detects changes
	 * without writing them to disk.
	 */
	private void cleanFile(final File file) {
		try {
			if (this.verbose) {
				System.out.println(Messages.bind(Messages.CommandLineCleaning, file.getAbsolutePath()));
			}

			IPath filePath = Path.fromOSString(file.getAbsolutePath());
			IFile iFile = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(filePath);

			if (iFile == null || !iFile.exists()) {
				if (!this.quiet) {
					System.err.println(Messages.bind(Messages.FileOutsideWorkspace, file.getAbsolutePath()));
				}
				return;
			}

			ICompilationUnit cu = JavaCore.createCompilationUnitFrom(iFile);
			if (cu == null) {
				if (!this.quiet) {
					System.err.println(Messages.bind(Messages.CleanupProblem, file.getAbsolutePath()));
				}
				return;
			}

			this.filesProcessed++;

			// Snapshot original content for change detection in CHECK/DIFF modes
			byte[] originalContent = null;
			if (this.cleanupMode == CleanupMode.CHECK || this.cleanupMode == CleanupMode.DIFF) {
				originalContent = Files.readAllBytes(file.toPath());
			}

			CleanUpRefactoring refactoring = new CleanUpRefactoring();
			refactoring.addCompilationUnit(cu);

			ICleanUp[] cleanUps = JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps();
			if (this.options != null && cleanUps.length > 0) {
				CleanUpOptions cleanUpOptions = new CleanUpOptions();
				for (Map.Entry<String, String> entry : this.options.entrySet()) {
					cleanUpOptions.setOption(entry.getKey(), entry.getValue());
				}
				for (ICleanUp cleanUp : cleanUps) {
					cleanUp.setOptions(cleanUpOptions);
					refactoring.addCleanUp(cleanUp);
				}
			} else {
				refactoring.setUseOptionsFromProfile(true);
				for (ICleanUp cleanUp : cleanUps) {
					refactoring.addCleanUp(cleanUp);
				}
			}

			RefactoringStatus status = refactoring.checkAllConditions(new NullProgressMonitor());
			if (status.hasFatalError()) {
				if (!this.quiet) {
					System.err.println(Messages.bind(Messages.CleanupFatalError, file.getAbsolutePath(), status.getMessageMatchingSeverity(RefactoringStatus.FATAL)));
				}
				return;
			}

			Change change = refactoring.createChange(new NullProgressMonitor());
			if (change != null) {
				if (this.cleanupMode == CleanupMode.CHECK || this.cleanupMode == CleanupMode.DIFF) {
					// Perform the change, then compare and restore
					change.perform(new NullProgressMonitor());
					cu.save(new NullProgressMonitor(), true);
					iFile.refreshLocal(1, new NullProgressMonitor());

					byte[] newContent = Files.readAllBytes(file.toPath());
					boolean changed = !MessageDigest.isEqual(
							computeHash(originalContent), computeHash(newContent));

					if (changed) {
						this.changedFiles.add(file.getAbsolutePath());

						String origStr = new String(originalContent, StandardCharsets.UTF_8);
						String newStr = new String(newContent, StandardCharsets.UTF_8);

						if (this.cleanupMode == CleanupMode.DIFF && !this.quiet) {
							printUnifiedDiff(file.getAbsolutePath(), origStr, newStr);
						}

						// Capture diff for patch file
						if (this.patchFile != null) {
							appendUnifiedDiff(file.getAbsolutePath(), origStr, newStr);
						}
					}

					// Restore original content (dry-run)
					Files.write(file.toPath(), originalContent);
					iFile.refreshLocal(1, new NullProgressMonitor());
				} else {
					// APPLY mode – snapshot before, apply, compare
					byte[] beforeContent = Files.readAllBytes(file.toPath());
					change.perform(new NullProgressMonitor());
					cu.save(new NullProgressMonitor(), true);
					iFile.refreshLocal(1, new NullProgressMonitor());

					byte[] afterContent = Files.readAllBytes(file.toPath());
					if (!MessageDigest.isEqual(computeHash(beforeContent), computeHash(afterContent))) {
						this.changedFiles.add(file.getAbsolutePath());
					}
				}
			}

		} catch (CoreException e) {
			final String errorMessage = Messages.bind(Messages.CaughtException, "CoreException", e.getLocalizedMessage()); //$NON-NLS-1$
			Util.log(e, errorMessage);
			System.err.println(Messages.bind(Messages.ExceptionSkip, errorMessage));
		} catch (Exception e) {
			final String errorMessage = Messages.bind(Messages.CaughtException, e.getClass().getSimpleName(), e.getLocalizedMessage());
			Util.log(e, errorMessage);
			System.err.println(Messages.bind(Messages.ExceptionSkip, errorMessage));
		}
	}

	File[] processCommandLine(final String[] argsArray) {

		int index = 0;
		final int argCount = argsArray.length;

		int parseMode = DEFAULT_PARSE_MODE;

		int fileCounter = 0;

		File[] filesToCleanup = new File[INITIALSIZE];

		loop: while (index < argCount) {
			final String currentArg = argsArray[index++];

			switch (parseMode) {
				default:
					break;
				case DEFAULT_PARSE_MODE:
					if (PDE_LAUNCH.equals(currentArg)) {
						continue loop;
					}
					if (ARG_HELP.equals(currentArg) || "--help".equals(currentArg)) { //$NON-NLS-1$
						displayHelp();
						return FILES;
					}
					if (ARG_VERBOSE.equals(currentArg) || "--verbose".equals(currentArg)) { //$NON-NLS-1$
						this.verbose = true;
						continue loop;
					}
					if (ARG_QUIET.equals(currentArg) || "--quiet".equals(currentArg)) { //$NON-NLS-1$
						this.quiet = true;
						continue loop;
					}
					if (ARG_CONFIG.equals(currentArg) || "--config".equals(currentArg)) { //$NON-NLS-1$
						parseMode = CONFIG_PARSE_MODE;
						continue loop;
					}
					if (ARG_MODE.equals(currentArg)) {
						parseMode = MODE_PARSE_MODE;
						continue loop;
					}
					if (ARG_SOURCE.equals(currentArg)) {
						parseMode = SOURCE_PARSE_MODE;
						continue loop;
					}
					if (ARG_SCOPE.equals(currentArg)) {
						parseMode = SCOPE_PARSE_MODE;
						continue loop;
					}
					if (ARG_PATCH.equals(currentArg)) {
						parseMode = PATCH_PARSE_MODE;
						continue loop;
					}
					if (ARG_REPORT.equals(currentArg)) {
						parseMode = REPORT_PARSE_MODE;
						continue loop;
					}
					// the current arg should be a file or a directory name
					final File file = new File(currentArg);
					if (file.exists()) {
						if (filesToCleanup.length == fileCounter) {
							System.arraycopy(filesToCleanup, 0, filesToCleanup = new File[fileCounter * 2], 0, fileCounter);
						}
						filesToCleanup[fileCounter++] = file;
					} else {
						String canonicalPath;
						try {
							canonicalPath = file.getCanonicalPath();
						} catch (IOException e2) {
							canonicalPath = file.getAbsolutePath();
						}
						final String errorMsg = file.isAbsolute() ?
								Messages.bind(Messages.CommandLineErrorFile, canonicalPath) :
								Messages.bind(Messages.CommandLineErrorFileTryFullPath, canonicalPath);
						displayHelp(errorMsg);
						return FILES;
					}
					break;
				case CONFIG_PARSE_MODE:
					this.configName = currentArg;
					this.options = readConfig(currentArg);
					if (this.options == null) {
						displayHelp(Messages.bind(Messages.CommandLineErrorConfig, currentArg));
						return FILES;
					}
					parseMode = DEFAULT_PARSE_MODE;
					continue loop;
				case MODE_PARSE_MODE:
					try {
						this.cleanupMode = CleanupMode.valueOf(currentArg.toUpperCase());
					} catch (@SuppressWarnings("unused") IllegalArgumentException e) {
						displayHelp(Messages.bind(Messages.CommandLineErrorInvalidMode, currentArg));
						return FILES;
					}
					parseMode = DEFAULT_PARSE_MODE;
					continue loop;
				case SOURCE_PARSE_MODE: {
					final File sourceDir = new File(currentArg);
					if (sourceDir.exists()) {
						if (filesToCleanup.length == fileCounter) {
							System.arraycopy(filesToCleanup, 0, filesToCleanup = new File[fileCounter * 2], 0, fileCounter);
						}
						filesToCleanup[fileCounter++] = sourceDir;
					} else {
						displayHelp(Messages.bind(Messages.CommandLineErrorFile, currentArg));
						return FILES;
					}
					parseMode = DEFAULT_PARSE_MODE;
					continue loop;
				}
				case SCOPE_PARSE_MODE:
					try {
						this.cleanupScope = CleanupScope.valueOf(currentArg.toUpperCase());
					} catch (@SuppressWarnings("unused") IllegalArgumentException e) {
						displayHelp(Messages.bind(Messages.CommandLineErrorInvalidScope, currentArg));
						return FILES;
					}
					parseMode = DEFAULT_PARSE_MODE;
					continue loop;
				case PATCH_PARSE_MODE:
					this.patchFile = currentArg;
					parseMode = DEFAULT_PARSE_MODE;
					continue loop;
				case REPORT_PARSE_MODE:
					this.reportFile = currentArg;
					parseMode = DEFAULT_PARSE_MODE;
					continue loop;
			}
		}

		if (parseMode == CONFIG_PARSE_MODE || this.options == null) {
			displayHelp(Messages.bind(Messages.CommandLineErrorNoConfigFile));
			return null;
		}
		if (this.quiet && this.verbose) {
			displayHelp(
				Messages.bind(
					Messages.CommandLineErrorQuietVerbose,
					new String[] { ARG_QUIET, ARG_VERBOSE }
				));
			return null;
		}
		if (fileCounter == 0) {
			displayHelp(Messages.bind(Messages.CommandLineErrorFileDir));
			return null;
		}
		if (filesToCleanup.length != fileCounter) {
			System.arraycopy(filesToCleanup, 0, filesToCleanup = new File[fileCounter], 0, fileCounter);
		}
		return filesToCleanup;
	}

	/**
	 * Return a Java Properties file representing the options that are in the
	 * specified configuration file.
	 */
	private static Map<String, String> readConfig(final String filename) {
		final File configFile = new File(filename);
		try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(configFile))) {
			final Properties formatterOptions = new Properties();
			formatterOptions.load(stream);
			Map<String, String> optionsMap = new HashMap<>();
			for (String key : formatterOptions.stringPropertyNames()) {
				optionsMap.put(key, formatterOptions.getProperty(key));
			}
			return optionsMap;
		} catch (IOException e) {
			String canonicalPath = null;
			try {
				canonicalPath = configFile.getCanonicalPath();
			} catch (IOException e2) {
				canonicalPath = configFile.getAbsolutePath();
			}
			final String errorMessage;
			if (!configFile.exists() && !configFile.isAbsolute()) {
				errorMessage = Messages.bind(Messages.ConfigFileNotFoundErrorTryFullPath, new Object[] {
					canonicalPath,
					System.getProperty("user.dir") //$NON-NLS-1$
				});

			} else {
				errorMessage = Messages.bind(Messages.ConfigFileReadingError, canonicalPath);
			}
			Util.log(e, errorMessage);
			System.err.println(errorMessage);
		}
		return null;
	}

	/**
	 * Runs the Java code cleanup application
	 */
	@Override
	public Object start(final IApplicationContext context) throws Exception {
		Instant startTime = Instant.now();
		final File[] filesToCleanup = processCommandLine((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));

		if (filesToCleanup == null) {
			return IApplication.EXIT_OK;
		}

		if (!this.quiet) {
			if (this.configName != null) {
				System.out.println(Messages.bind(Messages.CommandLineConfigFile, this.configName));
			}
			System.out.println(Messages.bind(Messages.CommandLineStart));
			if (this.cleanupMode != CleanupMode.APPLY) {
				System.out.println(Messages.bind(Messages.CommandLineMode, this.cleanupMode.name().toLowerCase()));
			}
		}

		// clean up the list of files and/or directories
		for (final File file : filesToCleanup) {
			if (file.isDirectory()) {
				cleanDirTree(file);
			} else if (Util.isJavaLikeFileName(file.getPath())) {
				cleanFile(file);
			}
		}

		Instant endTime = Instant.now();

		// Write patch file if requested
		if (this.patchFile != null && !this.changedFiles.isEmpty()) {
			writePatchFile(filesToCleanup);
		}

		// Write JSON report if requested
		if (this.reportFile != null) {
			writeJsonReport(startTime, endTime);
		}

		if (!this.quiet) {
			System.out.println(Messages.bind(Messages.CommandLineDone));
			if (!this.changedFiles.isEmpty()) {
				System.out.println(Messages.bind(Messages.CommandLineChangedFiles,
						String.valueOf(this.changedFiles.size())));
			}
		}

		// Determine exit code based on mode
		if (this.cleanupMode == CleanupMode.CHECK || this.cleanupMode == CleanupMode.DIFF) {
			return this.changedFiles.isEmpty() ? Integer.valueOf(EXIT_OK) : Integer.valueOf(EXIT_CHANGES);
		}
		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {
		// do nothing
	}

	/**
	 * Display the command line usage message.
	 */
	private static void displayHelp() {
		System.out.println(Messages.bind(Messages.CommandLineUsage));
	}

	private static void displayHelp(final String message) {
		System.err.println(message);
		System.out.println();
		displayHelp();
	}

	/**
	 * Recursively clean up the Java source code that is contained in the
	 * directory rooted at dir.
	 */
	private void cleanDirTree(final File dir) {

		final File[] files = dir.listFiles();
		if (files == null) {
			return;
		}

		for (final File file : files) {
			if (file.isDirectory()) {
				if (shouldProcessDirectory(file)) {
					cleanDirTree(file);
				}
			} else if (Util.isJavaLikeFileName(file.getPath())) {
				cleanFile(file);
			}
		}
	}

	/**
	 * Check if a directory should be processed based on the scope setting.
	 */
	private boolean shouldProcessDirectory(final File dir) {
		if (this.cleanupScope == CleanupScope.BOTH) {
			return true;
		}
		String name = dir.getName();
		if (this.cleanupScope == CleanupScope.MAIN) {
			return !"test".equals(name) && !"tests".equals(name); //$NON-NLS-1$ //$NON-NLS-2$
		}
		// TEST scope: only process test directories and their parents
		return "test".equals(name) || "tests".equals(name); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Compute SHA-256 hash for content comparison.
	 */
	private static byte[] computeHash(byte[] content) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(content); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is always available in Java
			throw new AssertionError(e);
		}
	}

	/**
	 * Print a simple unified diff between original and new content.
	 */
	private static void printUnifiedDiff(String filePath, String original, String modified) {
		System.out.println("--- a/" + filePath); //$NON-NLS-1$
		System.out.println("+++ b/" + filePath); //$NON-NLS-1$
		String[] origLines = original.split("\n", -1); //$NON-NLS-1$
		String[] newLines = modified.split("\n", -1); //$NON-NLS-1$
		// Simple line-by-line diff (hunk-based)
		int maxLen = Math.max(origLines.length, newLines.length);
		int hunkStart = -1;
		List<String> hunkLines = new ArrayList<>();
		for (int i = 0; i < maxLen; i++) {
			String origLine = i < origLines.length ? origLines[i] : ""; //$NON-NLS-1$
			String newLine = i < newLines.length ? newLines[i] : ""; //$NON-NLS-1$
			if (!origLine.equals(newLine)) {
				if (hunkStart == -1) {
					hunkStart = i + 1;
				}
				if (i < origLines.length) {
					hunkLines.add("-" + origLine); //$NON-NLS-1$
				}
				if (i < newLines.length) {
					hunkLines.add("+" + newLine); //$NON-NLS-1$
				}
			} else {
				if (!hunkLines.isEmpty()) {
					System.out.println("@@ -" + hunkStart + " @@"); //$NON-NLS-1$ //$NON-NLS-2$
					hunkLines.forEach(System.out::println);
					hunkLines.clear();
					hunkStart = -1;
				}
			}
		}
		if (!hunkLines.isEmpty()) {
			System.out.println("@@ -" + hunkStart + " @@"); //$NON-NLS-1$ //$NON-NLS-2$
			hunkLines.forEach(System.out::println);
		}
	}

	/**
	 * Append unified diff content to the patchContent buffer.
	 */
	private void appendUnifiedDiff(String filePath, String original, String modified) {
		this.patchContent.append("--- a/").append(filePath).append('\n'); //$NON-NLS-1$
		this.patchContent.append("+++ b/").append(filePath).append('\n'); //$NON-NLS-1$
		String[] origLines = original.split("\n", -1); //$NON-NLS-1$
		String[] newLines = modified.split("\n", -1); //$NON-NLS-1$
		int maxLen = Math.max(origLines.length, newLines.length);
		int hunkStart = -1;
		List<String> hunkLines = new ArrayList<>();
		for (int i = 0; i < maxLen; i++) {
			String origLine = i < origLines.length ? origLines[i] : ""; //$NON-NLS-1$
			String newLine = i < newLines.length ? newLines[i] : ""; //$NON-NLS-1$
			if (!origLine.equals(newLine)) {
				if (hunkStart == -1) {
					hunkStart = i + 1;
				}
				if (i < origLines.length) {
					hunkLines.add("-" + origLine); //$NON-NLS-1$
				}
				if (i < newLines.length) {
					hunkLines.add("+" + newLine); //$NON-NLS-1$
				}
			} else {
				if (!hunkLines.isEmpty()) {
					this.patchContent.append("@@ -").append(hunkStart).append(" @@\n"); //$NON-NLS-1$ //$NON-NLS-2$
					for (String line : hunkLines) {
						this.patchContent.append(line).append('\n');
					}
					hunkLines.clear();
					hunkStart = -1;
				}
			}
		}
		if (!hunkLines.isEmpty()) {
			this.patchContent.append("@@ -").append(hunkStart).append(" @@\n"); //$NON-NLS-1$ //$NON-NLS-2$
			for (String line : hunkLines) {
				this.patchContent.append(line).append('\n');
			}
		}
	}

	/**
	 * Write unified diff patch file for all changed files.
	 */
	private void writePatchFile(final File[] sourceRoots) {
		try (PrintWriter writer = new PrintWriter(
				new OutputStreamWriter(Files.newOutputStream(new File(this.patchFile).toPath()),
						StandardCharsets.UTF_8))) {
			writer.print(this.patchContent.toString());
			if (this.verbose) {
				System.out.println(Messages.bind(Messages.CommandLinePatchWritten, this.patchFile));
			}
		} catch (IOException e) {
			System.err.println(Messages.bind(Messages.CommandLinePatchError, this.patchFile));
		}
	}

	/**
	 * Write a JSON report file with cleanup results.
	 */
	private void writeJsonReport(Instant startTime, Instant endTime) {
		try (PrintWriter writer = new PrintWriter(
				new OutputStreamWriter(Files.newOutputStream(new File(this.reportFile).toPath()),
						StandardCharsets.UTF_8))) {
			Map<String, Object> report = new LinkedHashMap<>();
			report.put("tool", "sandbox-cleanup"); //$NON-NLS-1$ //$NON-NLS-2$
			report.put("version", getToolVersion()); //$NON-NLS-1$
			report.put("mode", this.cleanupMode.name().toLowerCase()); //$NON-NLS-1$
			report.put("scope", this.cleanupScope.name().toLowerCase()); //$NON-NLS-1$
			report.put("startTime", startTime.toString()); //$NON-NLS-1$
			report.put("endTime", endTime.toString()); //$NON-NLS-1$
			report.put("durationMs", endTime.toEpochMilli() - startTime.toEpochMilli()); //$NON-NLS-1$
			report.put("filesProcessed", this.filesProcessed); //$NON-NLS-1$
			report.put("filesChanged", this.changedFiles.size()); //$NON-NLS-1$
			report.put("changedFiles", this.changedFiles); //$NON-NLS-1$

			// Write JSON manually to avoid adding dependencies
			writeJsonObject(writer, report, 0);
			writer.println();

			if (this.verbose) {
				System.out.println(Messages.bind(Messages.CommandLineReportWritten, this.reportFile));
			}
		} catch (IOException e) {
			System.err.println(Messages.bind(Messages.CommandLineReportError, this.reportFile));
		}
	}

	/**
	 * Simple JSON writer without external dependencies.
	 */
	@SuppressWarnings("unchecked")
	private static void writeJsonObject(PrintWriter writer, Map<String, Object> map, int indent) {
		String pad = "  ".repeat(indent); //$NON-NLS-1$
		String innerPad = "  ".repeat(indent + 1); //$NON-NLS-1$
		writer.println("{"); //$NON-NLS-1$
		int i = 0;
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			writer.print(innerPad + "\"" + escapeJson(entry.getKey()) + "\": "); //$NON-NLS-1$ //$NON-NLS-2$
			Object val = entry.getValue();
			if (val instanceof String s) {
				writer.print("\"" + escapeJson(s) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			} else if (val instanceof Number || val instanceof Boolean) {
				writer.print(val);
			} else if (val instanceof List<?> list) {
				writeJsonArray(writer, list, indent + 1);
			} else if (val instanceof Map<?, ?> m) {
				writeJsonObject(writer, (Map<String, Object>) m, indent + 1);
			} else {
				writer.print("null"); //$NON-NLS-1$
			}
			if (i++ < map.size() - 1) {
				writer.print(","); //$NON-NLS-1$
			}
			writer.println();
		}
		writer.print(pad + "}"); //$NON-NLS-1$
	}

	private static void writeJsonArray(PrintWriter writer, List<?> list, int indent) {
		if (list.isEmpty()) {
			writer.print("[]"); //$NON-NLS-1$
			return;
		}
		String innerPad = "  ".repeat(indent + 1); //$NON-NLS-1$
		String pad = "  ".repeat(indent); //$NON-NLS-1$
		writer.println("["); //$NON-NLS-1$
		for (int i = 0; i < list.size(); i++) {
			Object item = list.get(i);
			if (item instanceof String s) {
				writer.print(innerPad + "\"" + escapeJson(s) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				writer.print(innerPad + item);
			}
			if (i < list.size() - 1) {
				writer.print(","); //$NON-NLS-1$
			}
			writer.println();
		}
		writer.print(pad + "]"); //$NON-NLS-1$
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\"", "\\\"") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\n", "\\n") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\r", "\\r") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\t", "\\t"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String getToolVersion() {
		// Read version from bundle or fallback
		return System.getProperty("sandbox.cleanup.version", "1.2.6-SNAPSHOT"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
