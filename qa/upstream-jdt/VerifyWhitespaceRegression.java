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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares Git whitespace diagnostics for changed files before and after a
 * migration.
 *
 * <p>The verifier deliberately uses {@code git diff --check} for both trees, so
 * repository-specific whitespace attributes remain authoritative. Diagnostics
 * are grouped by path, rule and offending whitespace rather than line number or
 * complete source line. A semantic rewrite may therefore retain a pre-existing
 * mixed indentation, but it fails when it adds or worsens such formatting.</p>
 */
public final class VerifyWhitespaceRegression {

	private static final Pattern DIAGNOSTIC=
			Pattern.compile("^(.*):(\\d+): (.+?)(?:\\.)?$"); //$NON-NLS-1$

	private record Arguments(Path repository, String baseline, Path paths, Path output) {
	}

	private record CommandResult(int status, String output) {
	}

	private record ViolationKey(String path, String rule, String whitespace) implements Comparable<ViolationKey> {
		@Override
		public int compareTo(ViolationKey other) {
			int result= path.compareTo(other.path);
			if (result != 0) {
				return result;
			}
			result= rule.compareTo(other.rule);
			return result != 0 ? result : whitespace.compareTo(other.whitespace);
		}
	}

	private record Regression(ViolationKey key, int baselineCount, int migratedCount) {
		int addedCount() {
			return migratedCount - baselineCount;
		}
	}

	private VerifyWhitespaceRegression() {
		// Not instantiable.
	}

	public static void main(String[] values) throws Exception {
		Arguments arguments= parseArguments(values);
		List<String> paths= Files.readAllLines(arguments.paths(), StandardCharsets.UTF_8).stream()
				.map(String::strip)
				.filter(path -> !path.isEmpty())
				.distinct()
				.sorted()
				.toList();
		if (paths.isEmpty()) {
			throw new IllegalArgumentException("Changed-path list is empty."); //$NON-NLS-1$
		}

		String emptyTree= emptyTree(arguments.repository());
		String migratedTree= migratedTree(arguments.repository(), arguments.baseline(), paths);
		Map<ViolationKey, Integer> baseline= diagnostics(arguments.repository(), emptyTree,
				arguments.baseline(), paths);
		Map<ViolationKey, Integer> migrated= diagnostics(arguments.repository(), emptyTree, migratedTree, paths);

		List<Regression> regressions= new ArrayList<>();
		for (Map.Entry<ViolationKey, Integer> entry : migrated.entrySet()) {
			int before= baseline.getOrDefault(entry.getKey(), Integer.valueOf(0)).intValue();
			if (entry.getValue().intValue() > before) {
				regressions.add(new Regression(entry.getKey(), before, entry.getValue().intValue()));
			}
		}
		regressions.sort(Comparator.comparing(Regression::key));

		writeReport(arguments.output(), baseline, migrated, regressions);
		if (!regressions.isEmpty()) {
			for (Regression regression : regressions) {
				System.err.printf("%s: added %d %s violation(s) with whitespace %s%n", //$NON-NLS-1$
						regression.key().path(), Integer.valueOf(regression.addedCount()),
						regression.key().rule(), visible(regression.key().whitespace()));
			}
			System.exit(1);
		}
		System.out.printf("No new Git whitespace violations across %d changed path(s).%n", //$NON-NLS-1$
				Integer.valueOf(paths.size()));
	}

	private static Arguments parseArguments(String[] values) throws IOException, InterruptedException {
		Map<String, String> options= new LinkedHashMap<>();
		for (int index= 0; index < values.length; index += 2) {
			if (index + 1 >= values.length || !values[index].startsWith("--")) { //$NON-NLS-1$
				throw new IllegalArgumentException("Expected --option value pairs."); //$NON-NLS-1$
			}
			options.put(values[index], values[index + 1]);
		}
		Path repository= Path.of(required(options, "--repository")).toAbsolutePath().normalize(); //$NON-NLS-1$
		String baseline= required(options, "--baseline"); //$NON-NLS-1$
		Path paths= Path.of(required(options, "--paths")).toAbsolutePath().normalize(); //$NON-NLS-1$
		Path output= Path.of(required(options, "--output")).toAbsolutePath().normalize(); //$NON-NLS-1$
		if (!Files.isDirectory(repository)) {
			throw new IllegalArgumentException("Repository directory does not exist: " + repository); //$NON-NLS-1$
		}
		if (!Files.isRegularFile(paths)) {
			throw new IllegalArgumentException("Changed-path list does not exist: " + paths); //$NON-NLS-1$
		}
		CommandResult repositoryCheck= run(repository, Map.of(), null,
				"git", "rev-parse", "--is-inside-work-tree"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if (repositoryCheck.status() != 0 || !"true".equals(repositoryCheck.output().strip())) { //$NON-NLS-1$
			throw new IllegalArgumentException("Not a Git work tree: " + repository); //$NON-NLS-1$
		}
		return new Arguments(repository, baseline, paths, output);
	}

	private static String required(Map<String, String> options, String name) {
		String value= options.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required."); //$NON-NLS-1$
		}
		return value;
	}

	private static String emptyTree(Path repository) throws IOException, InterruptedException {
		CommandResult result= run(repository, Map.of(), new byte[0],
				"git", "hash-object", "-t", "tree", "--stdin"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		requireSuccess(result, "Cannot create empty Git tree"); //$NON-NLS-1$
		return result.output().strip();
	}

	private static String migratedTree(Path repository, String baseline, List<String> paths)
			throws IOException, InterruptedException {
		Path index= Files.createTempFile("sandbox-whitespace-", ".index"); //$NON-NLS-1$ //$NON-NLS-2$
		Files.deleteIfExists(index);
		Map<String, String> environment= Map.of("GIT_INDEX_FILE", index.toString()); //$NON-NLS-1$
		try {
			CommandResult readTree= run(repository, environment, null, "git", "read-tree", baseline); //$NON-NLS-1$ //$NON-NLS-2$
			requireSuccess(readTree, "Cannot initialize temporary Git index"); //$NON-NLS-1$
			List<String> add= new ArrayList<>();
			add.add("git"); //$NON-NLS-1$
			add.add("add"); //$NON-NLS-1$
			add.add("-A"); //$NON-NLS-1$
			add.add("--"); //$NON-NLS-1$
			add.addAll(paths);
			CommandResult stage= run(repository, environment, null, add.toArray(String[]::new));
			requireSuccess(stage, "Cannot stage migrated files in temporary Git index"); //$NON-NLS-1$
			CommandResult writeTree= run(repository, environment, null, "git", "write-tree"); //$NON-NLS-1$ //$NON-NLS-2$
			requireSuccess(writeTree, "Cannot write migrated Git tree"); //$NON-NLS-1$
			return writeTree.output().strip();
		} finally {
			Files.deleteIfExists(index);
		}
	}

	private static Map<ViolationKey, Integer> diagnostics(Path repository, String from, String to,
			List<String> paths) throws IOException, InterruptedException {
		List<String> command= new ArrayList<>();
		command.add("git"); //$NON-NLS-1$
		command.add("diff"); //$NON-NLS-1$
		command.add("--check"); //$NON-NLS-1$
		command.add(from);
		command.add(to);
		command.add("--"); //$NON-NLS-1$
		command.addAll(paths);
		CommandResult result= run(repository, Map.of(), null, command.toArray(String[]::new));
		if (result.status() != 0 && result.status() != 2) {
			throw new IOException("git diff --check failed with exit code " + result.status() + ": " //$NON-NLS-1$ //$NON-NLS-2$
					+ result.output());
		}
		return parseDiagnostics(result.output());
	}

	private static Map<ViolationKey, Integer> parseDiagnostics(String output) {
		Map<ViolationKey, Integer> result= new TreeMap<>();
		String[] lines= output.split("\\R", -1); //$NON-NLS-1$
		for (int index= 0; index < lines.length; index++) {
			Matcher matcher= DIAGNOSTIC.matcher(lines[index]);
			if (!matcher.matches()) {
				continue;
			}
			String path= matcher.group(1).replace('\\', '/');
			String rule= matcher.group(3);
			String source= index + 1 < lines.length && lines[index + 1].startsWith("+") //$NON-NLS-1$
					? lines[++index].substring(1)
					: ""; //$NON-NLS-1$
			String whitespace= offendingWhitespace(rule, source);
			result.merge(new ViolationKey(path, rule, whitespace), Integer.valueOf(1), Integer::sum);
		}
		return result;
	}

	private static String offendingWhitespace(String rule, String source) {
		if (rule.contains("space before tab in indent")) { //$NON-NLS-1$
			int end= 0;
			while (end < source.length() && (source.charAt(end) == ' ' || source.charAt(end) == '\t')) {
				end++;
			}
			return source.substring(0, end);
		}
		if (rule.contains("trailing whitespace")) { //$NON-NLS-1$
			int start= source.length();
			while (start > 0 && (source.charAt(start - 1) == ' ' || source.charAt(start - 1) == '\t')) {
				start--;
			}
			return source.substring(start);
		}
		return source;
	}

	private static CommandResult run(Path directory, Map<String, String> environment, byte[] input,
			String... command) throws IOException, InterruptedException {
		ProcessBuilder builder= new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
		builder.environment().putAll(environment);
		Process process= builder.start();
		if (input != null) {
			process.getOutputStream().write(input);
		}
		process.getOutputStream().close();
		ByteArrayOutputStream output= new ByteArrayOutputStream();
		process.getInputStream().transferTo(output);
		int status= process.waitFor();
		return new CommandResult(status, output.toString(StandardCharsets.UTF_8));
	}

	private static void requireSuccess(CommandResult result, String message) throws IOException {
		if (result.status() != 0) {
			throw new IOException(message + " (exit " + result.status() + "): " + result.output()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static void writeReport(Path path, Map<ViolationKey, Integer> baseline,
			Map<ViolationKey, Integer> migrated, List<Regression> regressions) throws IOException {
		Path parent= path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		StringBuilder json= new StringBuilder(1024);
		json.append("{\n"); //$NON-NLS-1$
		json.append("  \"result\": \"").append(regressions.isEmpty() ? "PASS" : "FAIL").append("\",\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		json.append("  \"baselineViolationCount\": ").append(total(baseline)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
		json.append("  \"migratedViolationCount\": ").append(total(migrated)).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
		json.append("  \"newViolationCount\": ").append(regressions.stream().mapToInt(Regression::addedCount).sum()) //$NON-NLS-1$
				.append(",\n"); //$NON-NLS-1$
		json.append("  \"regressions\": ["); //$NON-NLS-1$
		for (int index= 0; index < regressions.size(); index++) {
			Regression regression= regressions.get(index);
			if (index > 0) {
				json.append(',');
			}
			json.append("\n    {\n"); //$NON-NLS-1$
			property(json, "path", regression.key().path(), 3).append(",\n"); //$NON-NLS-1$
			property(json, "rule", regression.key().rule(), 3).append(",\n"); //$NON-NLS-1$
			property(json, "whitespace", visible(regression.key().whitespace()), 3).append(",\n"); //$NON-NLS-1$
			json.append("      \"baselineCount\": ").append(regression.baselineCount()).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			json.append("      \"migratedCount\": ").append(regression.migratedCount()).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			json.append("      \"addedCount\": ").append(regression.addedCount()).append("\n    }"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (!regressions.isEmpty()) {
			json.append('\n').append("  "); //$NON-NLS-1$
		}
		json.append("]\n}\n"); //$NON-NLS-1$
		Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
	}

	private static int total(Map<ViolationKey, Integer> counts) {
		return counts.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static StringBuilder property(StringBuilder json, String name, String value, int indent) {
		return json.append("  ".repeat(indent)).append('"').append(escape(name)).append("\": \"") //$NON-NLS-1$ //$NON-NLS-2$
				.append(escape(value)).append('"');
	}

	private static String visible(String whitespace) {
		return whitespace.replace(" ", "·").replace("\t", "→"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	}
}
