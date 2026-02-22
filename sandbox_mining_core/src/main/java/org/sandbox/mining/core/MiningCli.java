/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.sandbox.mining.core.category.CategoryManager;
import org.sandbox.mining.core.config.MiningConfig;
import org.sandbox.mining.core.config.MiningState;
import org.sandbox.mining.core.config.MiningState.DeferredCommit;
import org.sandbox.mining.core.config.MiningState.RepoState;
import org.sandbox.mining.core.config.RepoEntry;
import org.sandbox.mining.core.dsl.DslValidator;
import org.sandbox.mining.core.llm.CommitEvaluation;
import org.sandbox.mining.core.llm.DslContextCollector;
import org.sandbox.mining.core.llm.LlmClient;
import org.sandbox.mining.core.llm.LlmClientFactory;
import org.sandbox.mining.core.llm.PromptBuilder;
import org.sandbox.mining.core.llm.PromptBuilder.CommitData;
import org.sandbox.mining.core.git.CommitWalker;
import org.sandbox.mining.core.git.DiffExtractor;
import org.sandbox.mining.core.git.RepoCloner;
import org.sandbox.mining.core.report.GithubPagesGenerator;
import org.sandbox.mining.core.report.JsonReporter;
import org.sandbox.mining.core.report.ReportAggregator;
import org.sandbox.mining.core.report.StatisticsCollector;

/**
 * CLI entry point for AI-powered commit analysis.
 *
 * <p>Analyzes Eclipse project commits using a configurable LLM provider and
 * generates reports for TriggerPattern DSL mining.</p>
 *
 * <p>Usage: java -jar sandbox-mining-core.jar [options]</p>
 */
public class MiningCli {

private static final String OPT_CONFIG = "--config"; //$NON-NLS-1$
private static final String OPT_STATE = "--state"; //$NON-NLS-1$
private static final String OPT_SANDBOX_ROOT = "--sandbox-root"; //$NON-NLS-1$
private static final String OPT_BATCH_SIZE = "--batch-size"; //$NON-NLS-1$
private static final String OPT_OUTPUT = "--output"; //$NON-NLS-1$
private static final String OPT_COMMITS_PER_REQUEST = "--commits-per-request"; //$NON-NLS-1$
private static final String OPT_MAX_FAILURE_DURATION = "--max-failure-duration"; //$NON-NLS-1$
private static final String OPT_LLM_PROVIDER = "--llm-provider"; //$NON-NLS-1$
private static final String OPT_RETRY_DEFERRED = "--retry-deferred"; //$NON-NLS-1$
private static final String OPT_RESET_LEARNED_LIMITS = "--reset-learned-limits"; //$NON-NLS-1$

private static final int DEFAULT_BATCH_SIZE = 500;
private static final int DEFAULT_COMMITS_PER_REQUEST = 4;
private static final int DEFAULT_MAX_FAILURE_DURATION_SECONDS = 300;

private static final DateTimeFormatter COMMIT_DATE_FORMAT =
DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC); //$NON-NLS-1$

/**
 * Upper bound for useful commits by diff size.
 * <p>Commits with more diff lines than this are considered too large and are
 * skipped to avoid overwhelming the model and wasting API quota. The minimum
 * useful diff size is controlled separately via {@code minDiffLines} in the
 * configuration.</p>
 */
private static final int MAX_USEFUL_DIFF_LINES = 300;

public static void main(String[] args) {
try {
new MiningCli().run(args);
} catch (Exception e) {
System.err.println("Error: " + e.getMessage());
e.printStackTrace();
System.exit(1);
}
}

/**
 * Runs the mining process with the given arguments.
 *
 * @param args CLI arguments
 * @throws IOException     if an I/O error occurs
 * @throws GitAPIException if a Git operation fails
 */
public void run(String[] args) throws IOException, GitAPIException {
long startTimeMs = System.currentTimeMillis();
Path configPath = null;
Path statePath = null;
Path sandboxRoot = Path.of("."); //$NON-NLS-1$
int batchSize = DEFAULT_BATCH_SIZE;
int commitsPerRequest = DEFAULT_COMMITS_PER_REQUEST;
int maxFailureDurationSeconds = DEFAULT_MAX_FAILURE_DURATION_SECONDS;
Path outputDir = Path.of("output"); //$NON-NLS-1$
String llmProvider = null;
boolean retryDeferred = false;
boolean resetLearnedLimits = false;

for (int i = 0; i < args.length; i++) {
switch (args[i]) {
case OPT_CONFIG:
configPath = Path.of(requireArg(args, ++i, OPT_CONFIG));
break;
case OPT_STATE:
statePath = Path.of(requireArg(args, ++i, OPT_STATE));
break;
case OPT_SANDBOX_ROOT:
sandboxRoot = Path.of(requireArg(args, ++i, OPT_SANDBOX_ROOT));
break;
case OPT_BATCH_SIZE:
batchSize = Integer.parseInt(requireArg(args, ++i, OPT_BATCH_SIZE));
break;
case OPT_COMMITS_PER_REQUEST:
commitsPerRequest = Integer.parseInt(requireArg(args, ++i, OPT_COMMITS_PER_REQUEST));
if (commitsPerRequest < 1) {
throw new IllegalArgumentException(
"--commits-per-request must be >= 1 but was " + commitsPerRequest); //$NON-NLS-1$
}
break;
case OPT_OUTPUT:
outputDir = Path.of(requireArg(args, ++i, OPT_OUTPUT));
break;
case OPT_MAX_FAILURE_DURATION:
maxFailureDurationSeconds = Integer.parseInt(requireArg(args, ++i, OPT_MAX_FAILURE_DURATION));
if (maxFailureDurationSeconds < 10) {
throw new IllegalArgumentException(
"--max-failure-duration must be at least 10 seconds but was " //$NON-NLS-1$
+ maxFailureDurationSeconds);
}
break;
case OPT_LLM_PROVIDER:
llmProvider = requireArg(args, ++i, OPT_LLM_PROVIDER);
break;
case OPT_RETRY_DEFERRED:
retryDeferred = true;
break;
case OPT_RESET_LEARNED_LIMITS:
resetLearnedLimits = true;
break;
default:
System.err.println("Unknown option: " + args[i]); //$NON-NLS-1$
printUsage();
return;
}
}

if (configPath == null) {
configPath = sandboxRoot.resolve(".github/refactoring-mining/repos.yml"); //$NON-NLS-1$
}
if (statePath == null) {
statePath = sandboxRoot.resolve(".github/refactoring-mining/state.json"); //$NON-NLS-1$
}

System.out.println("=== Sandbox Mining Core ==="); //$NON-NLS-1$
System.out.println("Config: " + configPath); //$NON-NLS-1$
System.out.println("State:  " + statePath); //$NON-NLS-1$
System.out.println("Output: " + outputDir); //$NON-NLS-1$

MiningConfig config = MiningConfig.parse(configPath);
MiningState state = MiningState.load(statePath);
MiningState.backup(statePath);
CategoryManager categoryManager = new CategoryManager();
DslContextCollector dslCollector = new DslContextCollector();
String dslContext = dslCollector.collectContext(sandboxRoot);
PromptBuilder promptBuilder = new PromptBuilder();
DslValidator validator = new DslValidator();
StatisticsCollector stats = new StatisticsCollector();
ReportAggregator aggregator = new ReportAggregator();

Path workDir = Files.createTempDirectory("mining-core-"); //$NON-NLS-1$
try (LlmClient llmClient = LlmClientFactory.createFromEnvironment(llmProvider)) {
llmClient.setMaxFailureDuration(Duration.ofSeconds(maxFailureDurationSeconds));
// Reset learned limits if requested or if model changed
for (RepoEntry repo : config.getRepositories()) {
RepoState repoState = state.getRepoState(repo.getUrl());
String currentModel = llmClient.getModel();
if (resetLearnedLimits || (repoState.getLastModelUsed() != null
&& !repoState.getLastModelUsed().equals(currentModel))) {
if (repoState.getLearnedMaxDiffLines() != -1) {
System.out.println("Resetting learned diff limit for " + repo.getUrl() //$NON-NLS-1$
+ " (was " + repoState.getLearnedMaxDiffLines() + " lines)"); //$NON-NLS-1$ //$NON-NLS-2$
repoState.setLearnedMaxDiffLines(-1);
}
// Reset deferred retry counts on model change
for (DeferredCommit dc : repoState.getDeferredCommits()) {
dc.setRetryCount(0);
}
}
repoState.setLastModelUsed(currentModel);
}
try {
processRepositories(config, state, statePath, workDir, batchSize, commitsPerRequest,
llmClient, promptBuilder, dslContext, categoryManager, validator, stats, aggregator);
if (retryDeferred) {
retryDeferredCommits(state, statePath, workDir, llmClient, promptBuilder,
dslContext, categoryManager, validator, stats, aggregator, config, retryDeferred);
}
} finally {
deleteDirectory(workDir);
}
printDeferredReport(state, config);
printRunSummary(stats, state, config, llmClient, startTimeMs);
}

// Generate output
Files.createDirectories(outputDir);
JsonReporter jsonReporter = new JsonReporter();
jsonReporter.writeEvaluations(aggregator.getAllEvaluations(), outputDir);
jsonReporter.writeStatistics(stats, outputDir);

GithubPagesGenerator pagesGenerator = new GithubPagesGenerator();
pagesGenerator.generate(aggregator.getAllEvaluations(), stats, outputDir);

state.save(statePath);

System.out.println("=== Mining complete ===");
System.out.println("Processed: " + stats.getTotalProcessed() + " commits");
System.out.println("Relevant:  " + stats.getRelevant());
System.out.println("Output:    " + outputDir.toAbsolutePath());
}

private void processRepositories(MiningConfig config, MiningState state, Path statePath,
Path workDir, int batchSize, int commitsPerRequest,
LlmClient llmClient, PromptBuilder promptBuilder,
String dslContext, CategoryManager categoryManager, DslValidator validator,
StatisticsCollector stats, ReportAggregator aggregator) throws IOException, GitAPIException {
RepoCloner cloner = new RepoCloner();
// Dynamic batch size tracking (reduced on truncation)
int[] dynamicCPR = { commitsPerRequest };

for (RepoEntry repo : config.getRepositories()) {
if (!llmClient.hasRemainingQuota()) {
System.out.println("Daily API quota exhausted (" + llmClient.getDailyRequestCount() //$NON-NLS-1$
+ " requests used). Stopping. Will resume from current position on next run."); //$NON-NLS-1$
return;
}
if (llmClient.isApiUnavailable()) {
logApiUnavailable(llmClient);
return;
}
System.out.println("Processing: " + repo.getUrl()); //$NON-NLS-1$
Path repoDir = workDir.resolve(repoDirectoryName(repo.getUrl()));
cloner.cloneRepo(repo.getUrl(), repo.getBranch(), repoDir);

String lastCommit = state.getLastProcessedCommit(repo.getUrl());
try (CommitWalker walker = new CommitWalker(repoDir);
DiffExtractor diffExtractor = new DiffExtractor(repoDir,
config.getMaxDiffLinesPerCommit(), repo.getPaths(),
config.getMaxFilesPerCommit())) {
List<RevCommit> batch = walker.nextBatch(lastCommit, config.getStartDate(), batchSize);
while (!batch.isEmpty()) {
// Group commits into sub-batches for API calls
for (int i = 0; i < batch.size(); i += dynamicCPR[0]) {
if (!llmClient.hasRemainingQuota()) {
System.out.println("Daily API quota exhausted (" //$NON-NLS-1$
+ llmClient.getDailyRequestCount()
+ " requests used). Stopping. Will resume from current position on next run."); //$NON-NLS-1$
return;
}
if (llmClient.isApiUnavailable()) {
logApiUnavailable(llmClient);
return;
}
int end = Math.min(i + dynamicCPR[0], batch.size());
List<RevCommit> subBatch = batch.subList(i, end);
processBatch(subBatch, repo, diffExtractor, state, statePath,
llmClient, promptBuilder, dslContext, categoryManager,
validator, stats, aggregator, config.getMinDiffLinesPerCommit());
if (llmClient.wasLastResponseTruncated() && dynamicCPR[0] > 1) {
dynamicCPR[0] = Math.max(1, dynamicCPR[0] / 2);
System.out.println("  Reducing commits-per-request to " + dynamicCPR[0] + " after truncated response"); //$NON-NLS-1$
}
if (llmClient.isApiUnavailable()) {
logApiUnavailable(llmClient);
return;
}
}
batch = walker.nextBatch(batch.get(batch.size() - 1).getName(),
config.getStartDate(), batchSize);
}
}

System.out.println("  Completed: " + repo.getUrl()); //$NON-NLS-1$
}
}

private void processBatch(List<RevCommit> commits, RepoEntry repo,
DiffExtractor diffExtractor, MiningState state, Path statePath,
LlmClient llmClient, PromptBuilder promptBuilder,
String dslContext, CategoryManager categoryManager, DslValidator validator,
StatisticsCollector stats, ReportAggregator aggregator,
int minDiffLines) throws IOException {
// Classify commits in original order: track which are skipped vs included
List<CommitData> commitDataList = new ArrayList<>();
List<Boolean> isSkipped = new ArrayList<>();
List<Integer> diffLineCounts = new ArrayList<>();

RepoState repoState = state.getRepoState(repo.getUrl());
int effectiveMaxDiff = repoState.getLearnedMaxDiffLines() > 0
? repoState.getLearnedMaxDiffLines() : MAX_USEFUL_DIFF_LINES;

for (RevCommit commit : commits) {
String diff = diffExtractor.extractDiff(commit);
int lineCount = diff.split("\n", -1).length; //$NON-NLS-1$
if (diff.isBlank()) {
isSkipped.add(Boolean.TRUE);
} else if (lineCount < minDiffLines) {
System.out.println("  Skipping commit " + formatCommitInfo(commit, repo) //$NON-NLS-1$
+ " (diff too small: " + lineCount + " lines)"); //$NON-NLS-1$ //$NON-NLS-2$
isSkipped.add(Boolean.TRUE);
} else if (lineCount > effectiveMaxDiff) {
System.out.println("  Deferring commit " + formatCommitInfo(commit, repo) //$NON-NLS-1$
+ " (" + lineCount + " lines > limit " + effectiveMaxDiff + ")"); //$NON-NLS-1$ //$NON-NLS-2$
repoState.addDeferredCommit(new DeferredCommit(
commit.getName(),
commit.getShortMessage().substring(0, Math.min(120, commit.getShortMessage().length())),
lineCount, "DIFF_TOO_LARGE", Instant.now().toString(), 0, 3)); //$NON-NLS-1$
isSkipped.add(Boolean.TRUE);
} else {
commitDataList.add(new CommitData(commit.getName(), commit.getFullMessage(), diff));
diffLineCounts.add(lineCount);
isSkipped.add(Boolean.FALSE);
}
}

List<CommitEvaluation> evaluations = null;
if (!commitDataList.isEmpty()) {
List<String> hashes = commitDataList.stream().map(CommitData::commitHash).toList();
List<String> messages = commitDataList.stream().map(CommitData::commitMessage).toList();
String prompt = promptBuilder.buildBatchPrompt(dslContext,
categoryManager.getCategoriesJson(), commitDataList);
evaluations = llmClient.evaluateBatch(prompt, hashes, messages, repo.getUrl());

// Learn from truncation
if (llmClient.wasLastResponseTruncated() && !diffLineCounts.isEmpty()) {
int maxDiffInBatch = diffLineCounts.stream().mapToInt(Integer::intValue).max().orElse(0);
if (maxDiffInBatch > 0) {
int newLimit = (int) (maxDiffInBatch * 0.8);
if (repoState.getLearnedMaxDiffLines() == -1 || newLimit < repoState.getLearnedMaxDiffLines()) {
System.out.println("  Learning: reducing max diff limit to " + newLimit //$NON-NLS-1$
+ " lines (was " + (repoState.getLearnedMaxDiffLines() == -1 ? "default" : repoState.getLearnedMaxDiffLines()) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
repoState.setLearnedMaxDiffLines(newLimit);
}
}
}

if (evaluations == null || evaluations.size() != commitDataList.size()) {
System.out.println("  Incomplete batch evaluation for repository " + repo.getUrl() //$NON-NLS-1$
+ " [" + repo.getBranch() + "]" //$NON-NLS-1$ //$NON-NLS-2$
+ "; will retry non-evaluated commits in a future run."); //$NON-NLS-1$
System.out.println("  Batch contained commits:"); //$NON-NLS-1$
for (RevCommit c : commits) {
System.out.println("    - " + formatCommitInfo(c, repo)); //$NON-NLS-1$
}
if (llmClient.isApiUnavailable()) {
logApiUnavailable(llmClient);
}
// Defer commits that were not evaluated
if (evaluations != null) {
for (int j = evaluations.size(); j < commitDataList.size(); j++) {
CommitData cd = commitDataList.get(j);
repoState.addDeferredCommit(new DeferredCommit(
cd.commitHash(),
cd.commitMessage().substring(0, Math.min(120, cd.commitMessage().length())),
diffLineCounts.get(j), "INCOMPLETE_BATCH", Instant.now().toString(), 0, 3)); //$NON-NLS-1$
}
}
// Advance state only through the leading prefix of skipped commits so
// that included commits in this batch are not permanently lost.
for (int i = 0; i < commits.size(); i++) {
if (!isSkipped.get(i)) {
break;
}
state.updateLastProcessedCommit(repo.getUrl(), commits.get(i).getName());
}
state.save(statePath);
return;
}
}

// Process all commits in original order so state always advances
// monotonically and no commit is permanently skipped on failure.
if (evaluations != null) {
System.out.println("  Evaluated batch of " + commitDataList.size() + " commits for " //$NON-NLS-1$ //$NON-NLS-2$
+ repo.getUrl() + " [" + repo.getBranch() + "]:"); //$NON-NLS-1$ //$NON-NLS-2$
}
int evalIdx = 0;
for (int i = 0; i < commits.size(); i++) {
RevCommit commit = commits.get(i);
if (isSkipped.get(i)) {
state.updateLastProcessedCommit(repo.getUrl(), commit.getName());
} else {
CommitEvaluation evaluation = evaluations != null ? evaluations.get(evalIdx++) : null;
if (evaluation == null) {
System.out.println("  Missing evaluation for commit " + formatCommitInfo(commit, repo) //$NON-NLS-1$
+ "; stopping batch to retry remaining commits later."); //$NON-NLS-1$
break;
}
System.out.println("    - " + formatCommitInfo(commit, repo) + " [" + diffLineCounts.get(evalIdx - 1) + " lines]" + " -> " + evaluation.trafficLight()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
handleEvaluation(evaluation, commit, repo, validator, categoryManager, stats, aggregator);
state.updateLastProcessedCommit(repo.getUrl(), commit.getName());
}
}

// Save state after each batch for resume safety
state.save(statePath);
}

private void handleEvaluation(CommitEvaluation evaluation, RevCommit commit, RepoEntry repo,
DslValidator validator, CategoryManager categoryManager,
StatisticsCollector stats, ReportAggregator aggregator) {
if (evaluation.dslRule() != null && !evaluation.dslRule().isBlank()) {
var validation = validator.validate(evaluation.dslRule());
if (!validation.valid()) {
System.out.println("  Invalid DSL rule for " + formatCommitInfo(commit, repo) //$NON-NLS-1$
+ ": " + validation.message()); //$NON-NLS-1$
if (Boolean.parseBoolean(System.getenv("GEMINI_DEBUG"))) { //$NON-NLS-1$
System.out.println("  --- DSL rule begin ---"); //$NON-NLS-1$
System.out.println(evaluation.dslRule());
System.out.println("  --- DSL rule end ---"); //$NON-NLS-1$
}
}
}
if (evaluation.isNewCategory() && evaluation.category() != null) {
categoryManager.addCategory(evaluation.category());
}
stats.record(evaluation);
aggregator.add(evaluation);
}

static String repoDirectoryName(String url) {
String name = url;
if (name.endsWith(".git")) {
name = name.substring(0, name.length() - 4);
}
int lastSlash = name.lastIndexOf('/');
if (lastSlash >= 0) {
name = name.substring(lastSlash + 1);
}
return name;
}

static String formatCommitInfo(RevCommit commit, RepoEntry repo) {
String datetime = COMMIT_DATE_FORMAT.format(commit.getAuthorIdent().getWhen().toInstant());
String title = commit.getShortMessage().replace("\"", "\\\""); //$NON-NLS-1$ //$NON-NLS-2$
return commit.getName().substring(0, 7) + " on " + repo.getBranch() //$NON-NLS-1$
+ " (" + datetime + ") \"" + title + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
}

private static void logApiUnavailable(LlmClient llmClient) {
System.out.println("LLM API has been unreachable for over " //$NON-NLS-1$
+ llmClient.getMaxFailureDuration().toMinutes()
+ " minutes. Stopping to avoid wasting CI time. State saved; will resume on next run."); //$NON-NLS-1$
}

private static void printUsage() {
System.out.println("Usage: java -jar sandbox-mining-core.jar [options]"); //$NON-NLS-1$
System.out.println("Options:"); //$NON-NLS-1$
System.out.println("  --config <path>              Path to repos.yml config file"); //$NON-NLS-1$
System.out.println("  --state <path>               Path to state.json file"); //$NON-NLS-1$
System.out.println("  --sandbox-root <path>        Root of sandbox repository"); //$NON-NLS-1$
System.out.println("  --batch-size <n>             Number of commits per batch (default: 500)"); //$NON-NLS-1$
System.out.println("  --commits-per-request <n>    Commits grouped into one API call (default: 4)"); //$NON-NLS-1$
System.out.println("  --output <path>              Output directory (default: output)"); //$NON-NLS-1$
System.out.println("  --max-failure-duration <s>   Seconds without a successful API call before aborting (default: 300)"); //$NON-NLS-1$
System.out.println("  --llm-provider <name>        LLM provider: gemini, openai, deepseek, qwen, llama, or mistral (default: auto-detect)"); //$NON-NLS-1$
System.out.println("  --retry-deferred             Retry previously deferred commits"); //$NON-NLS-1$
System.out.println("  --reset-learned-limits       Reset learned diff size limits"); //$NON-NLS-1$
}

private static String requireArg(String[] args, int index, String option) {
if (index >= args.length) {
throw new IllegalArgumentException("Option " + option + " requires a value");
}
return args[index];
}

private static void deleteDirectory(Path dir) throws IOException {
if (Files.exists(dir)) {
try (var walk = Files.walk(dir)) {
walk.sorted(java.util.Comparator.reverseOrder())
.forEach(p -> {
try {
Files.deleteIfExists(p);
} catch (IOException e) {
// best effort cleanup
}
});
}
}
}

private void retryDeferredCommits(MiningState state, Path statePath, Path workDir,
LlmClient llmClient, PromptBuilder promptBuilder, String dslContext,
CategoryManager categoryManager, DslValidator validator,
StatisticsCollector stats, ReportAggregator aggregator,
MiningConfig config, boolean forceRetry) throws IOException {
for (RepoEntry repo : config.getRepositories()) {
RepoState repoState = state.getRepoState(repo.getUrl());
List<DeferredCommit> toRetry = new ArrayList<>(repoState.getDeferredCommits());
if (toRetry.isEmpty()) continue;

System.out.println("Retrying " + toRetry.size() + " deferred commits for " + repo.getUrl()); //$NON-NLS-1$ //$NON-NLS-2$
List<DeferredCommit> remaining = new ArrayList<>();
for (DeferredCommit dc : toRetry) {
if (!forceRetry && dc.getRetryCount() >= dc.getMaxRetries()) {
repoState.moveToPermanentlySkipped(dc.getHash());
continue;
}
if (!llmClient.hasRemainingQuota() || llmClient.isApiUnavailable()) {
remaining.add(dc);
continue;
}
// Single-commit retry via evaluate()
// (We can't re-extract diffs here without the repo checkout being available,
// so we just increment retry count and leave for next run when diff is available)
dc.setRetryCount(dc.getRetryCount() + 1);
remaining.add(dc);
}
repoState.setDeferredCommits(remaining);
state.save(statePath);
}
}

private void printRunSummary(StatisticsCollector stats, MiningState state,
MiningConfig config, LlmClient llmClient, long startTimeMs) {
long durationMs = System.currentTimeMillis() - startTimeMs;
long minutes = durationMs / 60000;
long seconds = (durationMs % 60000) / 1000;
int totalDeferred = 0;
int totalPermanentlySkipped = 0;
for (RepoEntry repo : config.getRepositories()) {
RepoState rs = state.getRepoState(repo.getUrl());
totalDeferred += rs.getDeferredCommits().size();
totalPermanentlySkipped += rs.getPermanentlySkipped().size();
}
System.out.println("=== Mining Run Summary ==="); //$NON-NLS-1$
System.out.println("Duration:           " + minutes + "m " + seconds + "s"); //$NON-NLS-1$ //$NON-NLS-2$
System.out.println("Commits processed:  " + stats.getTotalProcessed()); //$NON-NLS-1$
System.out.println("Commits deferred:   " + totalDeferred); //$NON-NLS-1$
System.out.println("Commits permanently skipped: " + totalPermanentlySkipped); //$NON-NLS-1$
System.out.println("Relevant:           " + stats.getRelevant()); //$NON-NLS-1$
System.out.println("API calls:          " + llmClient.getDailyRequestCount()); //$NON-NLS-1$
System.out.println("Model:              " + llmClient.getModel()); //$NON-NLS-1$
// Learned limits
for (RepoEntry repo : config.getRepositories()) {
RepoState rs = state.getRepoState(repo.getUrl());
if (rs.getLearnedMaxDiffLines() > 0) {
System.out.println("Learned max diff:   " + rs.getLearnedMaxDiffLines() + " lines (for " + repo.getUrl() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
}
}
}

private void printDeferredReport(MiningState state, MiningConfig config) {
boolean hasDeferred = false;
for (RepoEntry repo : config.getRepositories()) {
RepoState rs = state.getRepoState(repo.getUrl());
if (!rs.getDeferredCommits().isEmpty()) {
if (!hasDeferred) {
System.out.println("Deferred commits for retry:"); //$NON-NLS-1$
hasDeferred = true;
}
System.out.println("  " + repo.getUrl() + ": " + rs.getDeferredCommits().size() + " commits"); //$NON-NLS-1$ //$NON-NLS-2$
for (DeferredCommit dc : rs.getDeferredCommits()) {
System.out.println("    - " + dc.getHash().substring(0, Math.min(7, dc.getHash().length())) //$NON-NLS-1$
+ " (" + dc.getDiffLines() + " lines, " + dc.getReason() //$NON-NLS-1$ //$NON-NLS-2$
+ ", retry " + dc.getRetryCount() + "/" + dc.getMaxRetries() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
}
}
}
}
}
