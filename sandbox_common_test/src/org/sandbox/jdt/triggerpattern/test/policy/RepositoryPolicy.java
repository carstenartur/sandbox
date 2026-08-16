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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * JGit-backed repository policy used by the ordinary Maven/JUnit test suite.
 *
 * <p>The policy validates the tracked tree, compares the current head with a
 * merge base, rejects newly introduced Python automation, and reports textual
 * change size without invoking command-line Git or a second test runner.</p>
 *
 * @since 1.3.4
 */
public final class RepositoryPolicy implements AutoCloseable {

	private static final Path POLICY_DIRECTORY = Path.of(".github", "repository-policy"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final Pattern SETUP_PYTHON = Pattern.compile(
			"^(?:-\\s*)?uses:\\s*actions/setup-python@", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$
	private static final Pattern PYTHON_COMMAND = Pattern.compile(
			"(^|[\\s;&|`$()=])python(?:3(?:\\.\\d+)?)?(?=\\s|$)"); //$NON-NLS-1$

	private final Repository repository;
	private final Configuration configuration;

	private RepositoryPolicy(Repository repository, Configuration configuration) {
		this.repository = repository;
		this.configuration = configuration;
	}

	/**
	 * Opens the enclosing Git checkout and loads its repository policy.
	 *
	 * @return repository policy
	 * @throws IOException when the checkout or policy cannot be read
	 */
	public static RepositoryPolicy openFromWorkingDirectory() throws IOException {
		Path root = findRepositoryRoot(Path.of(System.getProperty("user.dir"))); //$NON-NLS-1$
		FileRepositoryBuilder builder = new FileRepositoryBuilder()
				.setWorkTree(root.toFile())
				.findGitDir(root.toFile());
		if (builder.getGitDir() == null) {
			throw new IOException("Repository policy requires a Git checkout rooted at " + root); //$NON-NLS-1$
		}
		return new RepositoryPolicy(builder.build(), Configuration.load(root));
	}

	/**
	 * Evaluates the tracked tree and the current review slice.
	 *
	 * @return policy report
	 * @throws IOException when Git objects or policy files cannot be read
	 */
	public PolicyReport evaluate() throws IOException {
		ObjectId head = requireCommit(repository, Constants.HEAD);
		ObjectId base = resolveBase(head);
		return evaluate(repository, base, head, configuration, documentedException());
	}

	@Override
	public void close() {
		repository.close();
	}

	static PolicyReport evaluate(Repository repository, ObjectId base, ObjectId head,
			Configuration configuration, Optional<String> documentedException) throws IOException {
		List<String> violations = new ArrayList<>();
		TreeSnapshot snapshot = snapshot(repository, head);
		compareAllowlist("Python automation", configuration.pythonFiles(), snapshot.pythonFiles(), violations); //$NON-NLS-1$
		compareAllowlist("Python workflow", configuration.pythonWorkflows(), snapshot.pythonWorkflows(), violations); //$NON-NLS-1$

		Map<String, Integer> changedLinesByFile = new LinkedHashMap<>();
		int changedFiles = 0;
		int changedLines = 0;
		if (!base.equals(head)) {
			ObjectId mergeBase = mergeBase(repository, base, head);
			DiffResult diff = inspectDiff(repository, mergeBase, head);
			changedFiles = diff.changedFiles();
			changedLines = diff.changedLines();
			changedLinesByFile.putAll(diff.changedLinesByFile());
			violations.addAll(diff.violations());

			if (changedLines > configuration.maxChangedLines()) {
				boolean hardStop = changedLines > configuration.hardStopChangedLines();
				if (documentedException.filter(text -> text.strip().length()
						>= configuration.minimumExceptionCharacters()).isEmpty()) {
					String limitDescription = hardStop
							? "hard stop of " + configuration.hardStopChangedLines() //$NON-NLS-1$
							: "review target of " + configuration.maxChangedLines(); //$NON-NLS-1$
					violations.add("Change slice contains " + changedLines + " changed text lines in " //$NON-NLS-1$ //$NON-NLS-2$
							+ changedFiles + " files, exceeding the " + limitDescription //$NON-NLS-1$
							+ ". Split the PR or document a substantive '## " //$NON-NLS-1$
							+ configuration.exceptionHeading() + "' section."); //$NON-NLS-1$
				}
			}
		}
		return new PolicyReport(changedFiles, changedLines, changedLinesByFile, violations);
	}

	private Optional<String> documentedException() throws IOException {
		String direct = firstNonBlank(System.getProperty("repository.policy.exception"), //$NON-NLS-1$
				System.getenv("REPOSITORY_POLICY_EXCEPTION")); //$NON-NLS-1$
		if (direct != null) {
			return Optional.of(direct);
		}

		String eventPath = System.getenv("GITHUB_EVENT_PATH"); //$NON-NLS-1$
		if (eventPath == null || eventPath.isBlank() || !Files.isRegularFile(Path.of(eventPath))) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(Path.of(eventPath), StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				return Optional.empty();
			}
			JsonObject rootObject = parsed.getAsJsonObject();
			JsonElement pullRequest = rootObject.get("pull_request"); //$NON-NLS-1$
			if (pullRequest == null || !pullRequest.isJsonObject()) {
				return Optional.empty();
			}
			JsonElement body = pullRequest.getAsJsonObject().get("body"); //$NON-NLS-1$
			if (body == null || body.isJsonNull()) {
				return Optional.empty();
			}
			return extractException(body.getAsString(), configuration.exceptionHeading(),
					configuration.minimumExceptionCharacters());
		}
	}

	static Optional<String> extractException(String pullRequestBody, String heading, int minimumCharacters) {
		if (pullRequestBody == null) {
			return Optional.empty();
		}
		Pattern section = Pattern.compile("(?ms)^##\\s+" + Pattern.quote(heading) //$NON-NLS-1$
				+ "\\s*$\\R(.*?)(?=^##\\s+|\\z)"); //$NON-NLS-1$
		Matcher matcher = section.matcher(pullRequestBody);
		if (!matcher.find()) {
			return Optional.empty();
		}
		String explanation = matcher.group(1).strip();
		return explanation.length() >= minimumCharacters ? Optional.of(explanation) : Optional.empty();
	}

	private ObjectId resolveBase(ObjectId head) throws IOException {
		List<String> candidates = new ArrayList<>();
		String configured = firstNonBlank(System.getProperty("repository.policy.base"), //$NON-NLS-1$
				System.getenv("REPOSITORY_POLICY_BASE")); //$NON-NLS-1$
		if (configured != null) {
			candidates.add(configured);
		}

		String githubBaseRef = System.getenv("GITHUB_BASE_REF"); //$NON-NLS-1$
		if (githubBaseRef != null && !githubBaseRef.isBlank()) {
			candidates.add(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + githubBaseRef); //$NON-NLS-1$
			candidates.add(Constants.R_HEADS + githubBaseRef);
			candidates.add(githubBaseRef);
		}
		String githubBefore = System.getenv("GITHUB_EVENT_BEFORE"); //$NON-NLS-1$
		if (githubBefore != null && githubBefore.matches("[0-9a-fA-F]{40}") //$NON-NLS-1$
				&& !githubBefore.matches("0{40}")) { //$NON-NLS-1$
			candidates.add(githubBefore);
		}
		candidates.add(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/main"); //$NON-NLS-1$
		candidates.add(Constants.HEAD + "~1"); //$NON-NLS-1$

		for (String candidate : candidates) {
			ObjectId resolved = resolveCommit(repository, candidate);
			if (resolved != null && !resolved.equals(head)) {
				return resolved;
			}
		}
		throw new IOException("Cannot resolve a repository-policy base commit. Fetch origin/main or set " //$NON-NLS-1$
				+ "-Drepository.policy.base=<ref> or REPOSITORY_POLICY_BASE=<ref>."); //$NON-NLS-1$
	}

	private static DiffResult inspectDiff(Repository repository, ObjectId base, ObjectId head)
			throws IOException {
		List<String> violations = new ArrayList<>();
		Map<String, Integer> changedLinesByFile = new LinkedHashMap<>();
		try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
			formatter.setRepository(repository);
			formatter.setDetectRenames(true);
			List<DiffEntry> entries = formatter.scan(treeParser(repository, base), treeParser(repository, head));
			for (DiffEntry entry : entries) {
				String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
						? entry.getOldPath()
						: entry.getNewPath();
				FileHeader fileHeader = formatter.toFileHeader(entry);
				int fileLines = fileHeader.toEditList().stream()
						.mapToInt(edit -> edit.getEndA() - edit.getBeginA()
								+ edit.getEndB() - edit.getBeginB())
						.sum();
				changedLinesByFile.put(path, fileLines);

				if (entry.getChangeType() != DiffEntry.ChangeType.DELETE
						&& entry.getNewPath().endsWith(".py") //$NON-NLS-1$
						&& !pathExists(repository, base, entry.getNewPath())) {
					violations.add("New Python automation file is forbidden: " + entry.getNewPath()); //$NON-NLS-1$
				}

				if (entry.getChangeType() != DiffEntry.ChangeType.DELETE
						&& isWorkflow(entry.getNewPath())) {
					String baseContent = workflowBaseContent(repository, base, entry);
					String headContent = readPath(repository, head, entry.getNewPath());
					List<PythonInvocation> additions = newPythonInvocations(baseContent, headContent);
					if (!additions.isEmpty()) {
						Set<Integer> lines = new TreeSet<>();
						for (PythonInvocation addition : additions) {
							lines.add(Integer.valueOf(addition.lineNumber()));
						}
						violations.add("New Python workflow invocation is forbidden in " + entry.getNewPath() //$NON-NLS-1$
								+ " at line(s) " + lines); //$NON-NLS-1$
					}
				}
			}
			int changedLines = changedLinesByFile.values().stream().mapToInt(Integer::intValue).sum();
			return new DiffResult(entries.size(), changedLines, changedLinesByFile, violations);
		}
	}

	private static String workflowBaseContent(Repository repository, ObjectId base, DiffEntry entry)
			throws IOException {
		String oldPath = entry.getOldPath();
		if (!DiffEntry.DEV_NULL.equals(oldPath) && pathExists(repository, base, oldPath)) {
			return readPath(repository, base, oldPath);
		}
		return ""; //$NON-NLS-1$
	}

	private static List<PythonInvocation> newPythonInvocations(String baseContent, String headContent) {
		Map<String, Integer> baseCounts = new LinkedHashMap<>();
		for (PythonInvocation invocation : pythonInvocations(baseContent)) {
			baseCounts.merge(invocation.signature(), Integer.valueOf(1), Integer::sum);
		}

		List<PythonInvocation> additions = new ArrayList<>();
		for (PythonInvocation invocation : pythonInvocations(headContent)) {
			int remaining = baseCounts.getOrDefault(invocation.signature(), Integer.valueOf(0)).intValue();
			if (remaining > 0) {
				if (remaining == 1) {
					baseCounts.remove(invocation.signature());
				} else {
					baseCounts.put(invocation.signature(), Integer.valueOf(remaining - 1));
				}
			} else {
				additions.add(invocation);
			}
		}
		return additions;
	}

	private static TreeSnapshot snapshot(Repository repository, ObjectId commitId) throws IOException {
		Set<String> pythonFiles = new TreeSet<>();
		Set<String> pythonWorkflows = new TreeSet<>();
		RevCommit commit = parseCommit(repository, commitId);
		try (TreeWalk walk = new TreeWalk(repository)) {
			walk.addTree(commit.getTree());
			walk.setRecursive(true);
			while (walk.next()) {
				String path = walk.getPathString();
				if (path.endsWith(".py")) { //$NON-NLS-1$
					pythonFiles.add(path);
				}
				if (isWorkflow(path)) {
					ObjectLoader loader = repository.open(walk.getObjectId(0));
					String contents = new String(loader.getBytes(), StandardCharsets.UTF_8);
					if (!pythonInvocations(contents).isEmpty()) {
						pythonWorkflows.add(path);
					}
				}
			}
		}
		return new TreeSnapshot(pythonFiles, pythonWorkflows);
	}

	private static List<PythonInvocation> pythonInvocations(String contents) {
		List<PythonInvocation> result = new ArrayList<>();
		String[] lines = contents.split("\\r?\\n", -1); //$NON-NLS-1$
		int runBlockIndent = -1;
		for (int index = 0; index < lines.length; index++) {
			String raw = lines[index];
			String trimmed = raw.stripLeading();
			int indent = raw.length() - trimmed.length();
			if (runBlockIndent >= 0 && !trimmed.isBlank() && indent <= runBlockIndent) {
				runBlockIndent = -1;
			}

			String directive = trimmed.startsWith("- ") ? trimmed.substring(2).stripLeading() : trimmed; //$NON-NLS-1$
			if (SETUP_PYTHON.matcher(directive).find()) {
				result.add(new PythonInvocation(index + 1, "uses:actions/setup-python")); //$NON-NLS-1$
			}
			if (directive.startsWith("run:")) { //$NON-NLS-1$
				String command = directive.substring("run:".length()).strip(); //$NON-NLS-1$
				if (command.startsWith("|") || command.startsWith(">")) { //$NON-NLS-1$ //$NON-NLS-2$
					runBlockIndent = indent;
				} else if (invokesPython(command)) {
					result.add(new PythonInvocation(index + 1, normalizeInvocation(command)));
				}
			} else if (runBlockIndent >= 0 && indent > runBlockIndent
					&& !trimmed.startsWith("#") && invokesPython(trimmed)) { //$NON-NLS-1$
				result.add(new PythonInvocation(index + 1, normalizeInvocation(trimmed)));
			}
		}
		return result;
	}

	private static String normalizeInvocation(String invocation) {
		return invocation.strip().replaceAll("\\s+", " "); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean invokesPython(String command) {
		return PYTHON_COMMAND.matcher(command).find();
	}

	private static boolean isWorkflow(String path) {
		return path.startsWith(".github/workflows/") //$NON-NLS-1$
				&& (path.endsWith(".yml") || path.endsWith(".yaml")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void compareAllowlist(String label, Set<String> allowed, Set<String> actual,
			List<String> violations) {
		Set<String> unexpected = new TreeSet<>(actual);
		unexpected.removeAll(allowed);
		for (String path : unexpected) {
			violations.add(label + " is not allowlisted: " + path); //$NON-NLS-1$
		}
		Set<String> stale = new TreeSet<>(allowed);
		stale.removeAll(actual);
		for (String path : stale) {
			violations.add(label + " allowlist entry is stale and must be removed: " + path); //$NON-NLS-1$
		}
	}

	private static Path findRepositoryRoot(Path start) throws IOException {
		Path current = start.toAbsolutePath().normalize();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.exists(current.resolve(".git")) //$NON-NLS-1$
					&& Files.isRegularFile(current.resolve(POLICY_DIRECTORY).resolve("policy.properties"))) { //$NON-NLS-1$
				return current;
			}
			current = current.getParent();
		}
		throw new IOException("Cannot locate repository root from " + start); //$NON-NLS-1$
	}

	private static ObjectId mergeBase(Repository repository, ObjectId base, ObjectId head)
			throws IOException {
		try (RevWalk walk = new RevWalk(repository)) {
			RevCommit baseCommit = walk.parseCommit(base);
			RevCommit headCommit = walk.parseCommit(head);
			walk.setRevFilter(RevFilter.MERGE_BASE);
			walk.markStart(baseCommit);
			walk.markStart(headCommit);
			RevCommit common = walk.next();
			if (common == null) {
				throw new IOException("Configured base and HEAD have no merge base"); //$NON-NLS-1$
			}
			return common.getId();
		}
	}

	private static AbstractTreeIterator treeParser(Repository repository, ObjectId commitId)
			throws IOException {
		RevCommit commit = parseCommit(repository, commitId);
		try (ObjectReader reader = repository.newObjectReader()) {
			CanonicalTreeParser parser = new CanonicalTreeParser();
			parser.reset(reader, commit.getTree().getId());
			return parser;
		}
	}

	private static RevCommit parseCommit(Repository repository, ObjectId commitId) throws IOException {
		try (RevWalk walk = new RevWalk(repository)) {
			return walk.parseCommit(commitId);
		}
	}

	private static boolean pathExists(Repository repository, ObjectId commitId, String path)
			throws IOException {
		RevCommit commit = parseCommit(repository, commitId);
		try (TreeWalk walk = TreeWalk.forPath(repository, path, commit.getTree())) {
			return walk != null;
		}
	}

	private static String readPath(Repository repository, ObjectId commitId, String path)
			throws IOException {
		RevCommit commit = parseCommit(repository, commitId);
		try (TreeWalk walk = TreeWalk.forPath(repository, path, commit.getTree())) {
			if (walk == null) {
				throw new IOException("Path is unavailable in commit " + commitId.name() + ": " + path); //$NON-NLS-1$ //$NON-NLS-2$
			}
			ObjectLoader loader = repository.open(walk.getObjectId(0));
			return new String(loader.getBytes(), StandardCharsets.UTF_8);
		}
	}

	private static ObjectId requireCommit(Repository repository, String ref) throws IOException {
		ObjectId resolved = resolveCommit(repository, ref);
		if (resolved == null) {
			throw new IOException("Cannot resolve commit: " + ref); //$NON-NLS-1$
		}
		return resolved;
	}

	private static ObjectId resolveCommit(Repository repository, String ref) throws IOException {
		String expression = ref.endsWith("^{commit}") ? ref : ref + "^{commit}"; //$NON-NLS-1$ //$NON-NLS-2$
		return repository.resolve(expression);
	}

	private static String firstNonBlank(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	static record Configuration(int maxChangedLines, int hardStopChangedLines,
			int minimumExceptionCharacters, String exceptionHeading, Set<String> pythonFiles,
			Set<String> pythonWorkflows) {

		Configuration {
			pythonFiles = Set.copyOf(pythonFiles);
			pythonWorkflows = Set.copyOf(pythonWorkflows);
		}

		static Configuration load(Path root) throws IOException {
			Path policyDirectory = root.resolve(POLICY_DIRECTORY);
			Properties properties = new Properties();
			try (Reader reader = Files.newBufferedReader(policyDirectory.resolve("policy.properties"), //$NON-NLS-1$
					StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
			return new Configuration(
					Integer.parseInt(properties.getProperty("max.changed.lines")), //$NON-NLS-1$
					Integer.parseInt(properties.getProperty("hard.stop.changed.lines")), //$NON-NLS-1$
					Integer.parseInt(properties.getProperty("minimum.exception.characters")), //$NON-NLS-1$
					properties.getProperty("exception.heading"), //$NON-NLS-1$
					readAllowlist(policyDirectory.resolve("python-files.allowlist")), //$NON-NLS-1$
					readAllowlist(policyDirectory.resolve("python-workflows.allowlist"))); //$NON-NLS-1$
		}

		private static Set<String> readAllowlist(Path path) throws IOException {
			Set<String> result = new TreeSet<>();
			for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				String candidate = line.strip();
				if (!candidate.isEmpty() && !candidate.startsWith("#")) { //$NON-NLS-1$
					result.add(candidate);
				}
			}
			return result;
		}
	}

	/**
	 * Immutable policy result with an actionable per-file line report.
	 *
	 * @param changedFiles       number of changed files
	 * @param changedLines       textual additions plus deletions
	 * @param changedLinesByFile textual additions plus deletions per file
	 * @param violations         policy violations
	 */
	public static record PolicyReport(int changedFiles, int changedLines,
			Map<String, Integer> changedLinesByFile, List<String> violations) {

		public PolicyReport {
			changedLinesByFile = Map.copyOf(changedLinesByFile);
			violations = List.copyOf(violations);
		}

		/**
		 * Returns whether the review slice satisfies the policy.
		 *
		 * @return {@code true} when no violation exists
		 */
		public boolean isCompliant() {
			return violations.isEmpty();
		}

		/**
		 * Formats violations and the largest file totals for JUnit/CI output.
		 *
		 * @return actionable report
		 */
		public String format() {
			StringBuilder result = new StringBuilder("Repository policy report: ") //$NON-NLS-1$
					.append(changedLines).append(" changed text lines in ") //$NON-NLS-1$
					.append(changedFiles).append(" files.\n"); //$NON-NLS-1$
			if (!violations.isEmpty()) {
				result.append("Violations:\n"); //$NON-NLS-1$
				for (String violation : violations) {
					result.append(" - ").append(violation).append('\n'); //$NON-NLS-1$
				}
			}
			if (!changedLinesByFile.isEmpty()) {
				result.append("Largest textual changes:\n"); //$NON-NLS-1$
				Comparator<Map.Entry<String, Integer>> byLargestChange = Comparator
						.<Map.Entry<String, Integer>>comparingInt(entry -> entry.getValue().intValue())
						.reversed()
						.thenComparing(Map.Entry::getKey);
				changedLinesByFile.entrySet().stream()
						.sorted(byLargestChange)
						.limit(20)
						.forEach(entry -> result.append(" - ").append(entry.getValue()) //$NON-NLS-1$
								.append(" lines: ").append(entry.getKey()).append('\n')); //$NON-NLS-1$
			}
			return result.toString();
		}
	}

	private static record PythonInvocation(int lineNumber, String signature) {
	}

	private static record TreeSnapshot(Set<String> pythonFiles, Set<String> pythonWorkflows) {
	}

	private static record DiffResult(int changedFiles, int changedLines,
			Map<String, Integer> changedLinesByFile, List<String> violations) {
	}
}
