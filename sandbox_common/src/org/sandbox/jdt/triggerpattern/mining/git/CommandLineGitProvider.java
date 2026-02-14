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
package org.sandbox.jdt.triggerpattern.mining.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunk;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;

/**
 * {@link GitHistoryProvider} implementation that uses the {@code git}
 * command-line tool.
 *
 * <p>This provider does not require JGit or EGit and works in any environment
 * where the {@code git} executable is available on the system PATH.</p>
 *
 * <p>The provider only returns diffs for Java source files
 * ({@code *.java}).</p>
 *
 * @since 1.2.6
 */
public class CommandLineGitProvider implements GitHistoryProvider {

	private static final String FIELD_SEP = "\u001f"; //$NON-NLS-1$
	private static final String RECORD_SEP = "\u001e"; //$NON-NLS-1$

	@Override
	public List<CommitInfo> getHistory(Path repositoryPath, int maxCommits) {
		// Format: hash<FS>short<FS>subject<FS>author<FS>unix-timestamp<FS>filecount<RS>
		String format = "%H" + FIELD_SEP + "%h" + FIELD_SEP + "%s" + FIELD_SEP //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ "%an" + FIELD_SEP + "%ct" + RECORD_SEP; //$NON-NLS-1$ //$NON-NLS-2$

		String output = runGit(repositoryPath,
				"log", //$NON-NLS-1$
				"--format=" + format, //$NON-NLS-1$
				"--shortstat", //$NON-NLS-1$
				"-n", String.valueOf(maxCommits)); //$NON-NLS-1$

		return parseHistory(output);
	}

	@Override
	public List<FileDiff> getDiffs(Path repositoryPath, String commitId) {
		// Get list of changed Java files
		String nameOutput = runGit(repositoryPath,
				"diff-tree", "--no-commit-id", "-r", "--name-only", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"--diff-filter=M", commitId); //$NON-NLS-1$

		List<FileDiff> diffs = new ArrayList<>();

		for (String filePath : nameOutput.split("\\n")) { //$NON-NLS-1$
			String trimmed = filePath.trim();
			if (trimmed.isEmpty() || !trimmed.endsWith(".java")) { //$NON-NLS-1$
				continue;
			}

			String contentBefore = getFileContent(repositoryPath, commitId + "~1", trimmed); //$NON-NLS-1$
			String contentAfter = getFileContent(repositoryPath, commitId, trimmed);

			if (contentBefore == null || contentAfter == null) {
				continue;
			}

			List<DiffHunk> hunks = extractHunks(repositoryPath, commitId, trimmed);
			diffs.add(new FileDiff(trimmed, contentBefore, contentAfter, hunks));
		}

		return diffs;
	}

	@Override
	public String getFileContent(Path repositoryPath, String commitId, String filePath) {
		try {
			return runGit(repositoryPath,
					"show", commitId + ":" + filePath); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (GitProviderException e) {
			return null;
		}
	}

	// ---- internal helpers ----

	/**
	 * Parses the output of {@code git log} into a list of commit info objects.
	 * <p>Visible for testing.</p>
	 *
	 * @param output the raw git log output
	 * @return list of parsed commit info objects
	 */
	public List<CommitInfo> parseHistory(String output) {
		List<CommitInfo> commits = new ArrayList<>();
		if (output == null || output.isBlank()) {
			return commits;
		}

		String[] records = output.split(RECORD_SEP);
		for (String record : records) {
			String trimmed = record.strip();
			if (trimmed.isEmpty()) {
				continue;
			}

			// The record may contain stat lines after the main fields
			String[] lines = trimmed.split("\\n"); //$NON-NLS-1$
			String headerLine = lines[0].strip();
			if (headerLine.isEmpty()) {
				continue;
			}

			String[] fields = headerLine.split(FIELD_SEP);
			if (fields.length < 5) {
				continue;
			}

			String id = fields[0].strip();
			String shortId = fields[1].strip();
			String message = fields[2].strip();
			String author = fields[3].strip();

			long epoch;
			try {
				epoch = Long.parseLong(fields[4].strip());
			} catch (NumberFormatException e) {
				continue;
			}
			LocalDateTime timestamp = LocalDateTime.ofInstant(
					Instant.ofEpochSecond(epoch), ZoneId.systemDefault());

			// Parse file count from shortstat line (e.g. " 3 files changed, ...")
			int fileCount = parseFileCount(lines);

			commits.add(new CommitInfo(id, shortId, message, author, timestamp, fileCount));
		}
		return commits;
	}

	private int parseFileCount(String[] lines) {
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i].strip();
			if (line.contains("file")) { //$NON-NLS-1$
				// Pattern: " 3 files changed" or " 1 file changed"
				String[] parts = line.split("\\s+"); //$NON-NLS-1$
				for (int j = 0; j < parts.length - 1; j++) {
					if (parts[j + 1].startsWith("file")) { //$NON-NLS-1$
						try {
							return Integer.parseInt(parts[j]);
						} catch (NumberFormatException e) {
							return 0;
						}
					}
				}
			}
		}
		return 0;
	}

	private List<DiffHunk> extractHunks(Path repositoryPath, String commitId, String filePath) {
		String diffOutput = runGit(repositoryPath,
				"diff", commitId + "~1", commitId, //$NON-NLS-1$ //$NON-NLS-2$
				"--", filePath); //$NON-NLS-1$

		return parseHunks(diffOutput);
	}

	/**
	 * Parses unified diff output into a list of diff hunks.
	 * <p>Visible for testing.</p>
	 *
	 * @param diffOutput the raw unified diff output
	 * @return list of parsed diff hunks
	 */
	public List<DiffHunk> parseHunks(String diffOutput) {
		List<DiffHunk> hunks = new ArrayList<>();
		if (diffOutput == null || diffOutput.isEmpty()) {
			return hunks;
		}

		String[] lines = diffOutput.split("\\n"); //$NON-NLS-1$
		int i = 0;

		while (i < lines.length) {
			if (lines[i].startsWith("@@")) { //$NON-NLS-1$
				DiffHunk hunk = parseOneHunk(lines, i);
				if (hunk != null) {
					hunks.add(hunk);
				}
				// Advance past this hunk
				i++;
				while (i < lines.length && !lines[i].startsWith("@@")) { //$NON-NLS-1$
					i++;
				}
			} else {
				i++;
			}
		}
		return hunks;
	}

	private DiffHunk parseOneHunk(String[] lines, int hunkHeaderIndex) {
		// Parse @@ -startLine,count +startLine,count @@
		String header = lines[hunkHeaderIndex];
		int atEnd = header.indexOf("@@", 2); //$NON-NLS-1$
		if (atEnd < 0) {
			return null;
		}
		String range = header.substring(3, atEnd).strip();
		String[] parts = range.split("\\s+"); //$NON-NLS-1$
		if (parts.length < 2) {
			return null;
		}

		int[] beforeRange = parseRange(parts[0].substring(1)); // strip leading '-'
		int[] afterRange = parseRange(parts[1].substring(1));  // strip leading '+'

		StringBuilder beforeText = new StringBuilder();
		StringBuilder afterText = new StringBuilder();

		for (int i = hunkHeaderIndex + 1; i < lines.length; i++) {
			if (lines[i].startsWith("@@") || lines[i].startsWith("diff ")) { //$NON-NLS-1$ //$NON-NLS-2$
				break;
			}
			if (lines[i].startsWith("-")) { //$NON-NLS-1$
				beforeText.append(lines[i].substring(1)).append('\n');
			} else if (lines[i].startsWith("+")) { //$NON-NLS-1$
				afterText.append(lines[i].substring(1)).append('\n');
			} else if (lines[i].startsWith(" ")) { //$NON-NLS-1$
				beforeText.append(lines[i].substring(1)).append('\n');
				afterText.append(lines[i].substring(1)).append('\n');
			}
		}

		return new DiffHunk(
				beforeRange[0], beforeRange[1],
				afterRange[0], afterRange[1],
				beforeText.toString(), afterText.toString());
	}

	private int[] parseRange(String rangeStr) {
		String[] parts = rangeStr.split(","); //$NON-NLS-1$
		int start;
		int count;
		try {
			start = Integer.parseInt(parts[0]);
			count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
		} catch (NumberFormatException e) {
			start = 1;
			count = 0;
		}
		return new int[] { start, count };
	}

	private String runGit(Path workingDir, String... args) {
		List<String> command = new ArrayList<>();
		command.add("git"); //$NON-NLS-1$
		for (String arg : args) {
			command.add(arg);
		}

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(workingDir.toFile());
		pb.redirectErrorStream(false);

		try {
			Process process = pb.start();
			String stdout;
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				stdout = readFully(reader);
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				String stderr;
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
					stderr = readFully(reader);
				}
				throw new GitProviderException(
						"git command failed with exit code " + exitCode + ": " + stderr); //$NON-NLS-1$ //$NON-NLS-2$
			}

			return stdout;
		} catch (IOException e) {
			throw new GitProviderException("Failed to execute git command", e); //$NON-NLS-1$
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GitProviderException("Git command interrupted", e); //$NON-NLS-1$
		}
	}

	private String readFully(BufferedReader reader) throws IOException {
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(line);
		}
		return sb.toString();
	}
}
