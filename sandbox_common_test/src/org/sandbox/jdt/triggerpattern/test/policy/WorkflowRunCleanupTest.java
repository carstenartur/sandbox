/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Executes the real cleanup script; only GitHub transport, clock and sleep are substituted. */
class WorkflowRunCleanupTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 5, 8})
    void recentCompletedRunsCountTowardsTheOverallMinimum(int recent) throws Exception {
        JsonObject result = execute(config(8, recent));
        succeeded(result);
        int protectedOld = Math.max(0, 5 - recent);
        assertEquals(range(1, 9 - protectedOld), ints(result, "deleted"));
        assertEquals(protectedOld, result.getAsJsonObject("outputs").get("protected").getAsInt());
        assertEquals(1, requests(result, "recent").size());
        JsonObject query = requests(result, "recent").getFirst();
        assertEquals("completed", query.get("status").getAsString());
        assertEquals("2026-09-07T12:00:00Z..2026-10-07T12:00:00Z", query.get("created").getAsString());
        assertEquals(1, query.get("per_page").getAsInt());
    }

    @Test void minimumIsIndependentPerWorkflowEvenWithIdenticalNames() throws Exception {
        JsonObject config = config(8, 2);
        JsonArray extra = new JsonArray();
        for (int i = 101; i <= 104; i++) extra.add(record(i, 20, "2026-08-01T00:00:00Z", "completed"));
        for (int i = 201; i <= 202; i++) extra.add(record(i, 30, "2026-08-02T00:00:00Z", "completed"));
        for (int i = 301; i <= 302; i++) extra.add(record(i, 20, "2026-10-01T00:00:00Z", "completed"));
        config.add("extra", extra);
        JsonObject result = execute(config);
        succeeded(result);
        // Workflow 20 keeps 102..104 plus 301..302; workflow 30 keeps both old runs.
        assertEquals(List.of(101, 1, 2, 3, 4, 5), ints(result, "deleted"));
        assertEquals(3, requests(result, "recent").size());
    }

    @Test void runningQueuedAndFutureRunsCannotReplaceCompletedEvidence() throws Exception {
        JsonObject config = config(8, 0);
        JsonArray extra = new JsonArray();
        extra.add(record(10001, 10, "2026-10-01T00:00:00Z", "in_progress"));
        extra.add(record(10002, 10, "2026-10-01T00:00:00Z", "queued"));
        extra.add(record(10003, 10, "2026-08-01T00:00:00Z", "in_progress"));
        extra.add(record(10004, 10, "2026-10-08T00:00:00Z", "completed"));
        config.add("extra", extra);
        JsonObject result = execute(config);
        succeeded(result);
        assertEquals(List.of(1, 2, 3), ints(result, "deleted"));
    }

    @Test void cutoffIsStrictAndCompletedFailuresAlsoCount() throws Exception {
        JsonObject config = config(5, 0);
        JsonArray extra = new JsonArray();
        JsonObject boundary = record(10001, 10, "2026-09-07T12:00:00Z", "completed");
        boundary.addProperty("conclusion", "failure");
        extra.add(boundary);
        config.add("extra", extra);
        JsonObject result = execute(config);
        succeeded(result);
        assertEquals(List.of(1), ints(result, "deleted"));
    }

    @Test void zeroMinimumAndEmptyRepositoriesNeedNoRecentQueries() throws Exception {
        JsonObject config = config(8, 0);
        config.addProperty("keep", 0);
        JsonObject result = execute(config);
        succeeded(result);
        assertEquals(range(1, 9), ints(result, "deleted"));
        assertTrue(requests(result, "recent").isEmpty());
        result = execute(config(0, 0));
        succeeded(result);
        assertTrue(ints(result, "deleted").isEmpty());
        assertTrue(requests(result, "recent").isEmpty());
    }

    @Test void dryRunUsesTheSameSelectionWithoutDeleting() throws Exception {
        JsonObject config = config(8, 5);
        config.addProperty("dry", true);
        JsonObject result = execute(config);
        succeeded(result);
        assertTrue(ints(result, "deleted").isEmpty());
        assertEquals(8, result.getAsJsonObject("outputs").get("selected").getAsInt());
        assertEquals(8, result.getAsJsonObject("outputs").get("remaining").getAsInt());
    }

    @Test void currentRunAndRateReserveRemainProtected() throws Exception {
        JsonObject config = config(8, 5);
        config.addProperty("current", 1);
        config.addProperty("rate", 351);
        JsonObject result = execute(config);
        succeeded(result);
        assertEquals(List.of(2), ints(result, "deleted"));
        assertEquals(6, result.getAsJsonObject("outputs").get("remaining").getAsInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"probe", "page", "recent", "rate"})
    void transientReadFailuresRetryTheExactRequest(String kind) throws Exception {
        JsonObject config = config(500, 5);
        config.add("failure", failure(kind, 502, 1));
        JsonObject result = execute(config);
        succeeded(result);
        assertEquals(500, ints(result, "deleted").size());
        assertEquals(List.of(1000), ints(result, "delays"));
        List<JsonObject> retried = requests(result, kind).stream()
                .filter(q -> !kind.equals("page") || q.get("page").getAsInt() == 5).toList();
        assertEquals(2, retried.size());
        assertEquals(retried.get(0), retried.get(1), "Retry the same page, never advance after failure");
        assertEquals(4, requests(result, "page").stream().filter(q -> q.get("page").getAsInt() < 5).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"probe", "page", "recent", "rate"})
    void exhaustedReadRetriesAbortBeforeAnyDeletion(String kind) throws Exception {
        JsonObject config = config(500, 5);
        config.add("failure", failure(kind, 502, 99));
        JsonObject result = execute(config);
        assertTrue(result.get("error").getAsString().contains("injected 502"));
        assertEquals(List.of(1000, 2000, 4000), ints(result, "delays"));
        assertTrue(ints(result, "deleted").isEmpty());
        assertFalse(result.getAsJsonObject("outputs").has("remaining"), "An incomplete scan is not zero backlog");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 422})
    void permanentReadErrorsAreNotRetriedOrTreatedAsEmpty(int status) throws Exception {
        JsonObject config = config(500, 5);
        config.add("failure", failure("page", status, 1));
        JsonObject result = execute(config);
        assertTrue(result.get("error").getAsString().contains("injected " + status));
        assertTrue(ints(result, "delays").isEmpty());
        assertTrue(ints(result, "deleted").isEmpty());
    }

    @Test void retryAfterIsRespected() throws Exception {
        JsonObject config = config(8, 5);
        JsonObject fault = failure("recent", 429, 1);
        fault.add("headers", JsonParser.parseString("{\"retry-after\":\"3\"}"));
        config.add("failure", fault);
        JsonObject result = execute(config);
        succeeded(result);
        assertEquals(List.of(3000), ints(result, "delays"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"truncated", "duplicate", "wrongStatus", "wrongRange", "invalidCount", "invalidRecentCount", "invalidRate"})
    void inconsistentReadResponsesFailClosed(String corruption) throws Exception {
        JsonObject config = config(500, 5);
        config.addProperty("corruption", corruption);
        JsonObject result = execute(config);
        assertFalse(result.get("error").getAsString().isEmpty(), corruption);
        assertTrue(ints(result, "deleted").isEmpty(), corruption);
    }

    @Test void timeWindowsSplitBeforeGithubsFilteredResultLimit() throws Exception {
        JsonObject result = execute(config(1100, 5));
        succeeded(result);
        assertEquals(range(1, 1101), ints(result, "deleted"));
        assertTrue(requests(result, "probe").size() > 1);
        assertTrue(requests(result, "page").stream().allMatch(q -> q.get("page").getAsInt() <= 10));
    }

    @Test void deletionRetriesAndAlreadyGoneSemanticsArePreserved() throws Exception {
        for (int status : new int[] {502, 404}) {
            JsonObject config = config(1, 5);
            config.add("failure", failure("delete", status, 1));
            JsonObject result = execute(config);
            succeeded(result);
            assertEquals(status == 502 ? List.of(1) : List.of(), ints(result, "deleted"));
            assertEquals(status == 502 ? List.of(1000) : List.of(), ints(result, "delays"));
            assertEquals(0, result.getAsJsonObject("outputs").get("remaining").getAsInt());
        }
    }

    @Test void existingNodeRegressionSuiteStillPasses() throws Exception {
        Path root = root();
        String output = node(root, "--test", "--test-reporter=tap",
                root.resolve(".github/scripts/cleanup-workflow-runs.test.cjs").toString());
        assertTrue(output.contains("# pass 7"), output);
        assertTrue(output.contains("# fail 0"), output);
    }

    @Test void workflowPreservesExplicitZeroAndMainOnlyDeletion() throws Exception {
        String workflow = Files.readString(root().resolve(".github/workflows/cleanup-workflow-runs.yml"), StandardCharsets.UTF_8);
        assertTrue(workflow.contains("KEEP_MINIMUM_RUNS: ${{ inputs.keep_minimum_runs }}"),
                "The script supplies the missing-input default; zero must not become five");
        assertTrue(workflow.contains("if: github.event_name != 'pull_request' && github.ref == 'refs/heads/main'"));
        assertTrue(workflow.contains("cancel-in-progress: false"));
        assertTrue(workflow.contains("needs: verify"));
    }

    @Test void workflowReportsAnUnfinishedScanAsUnknown() throws Exception {
        String workflow = Files.readString(root().resolve(".github/workflows/cleanup-workflow-runs.yml"), StandardCharsets.UTF_8);
        assertTrue(workflow.contains("steps.cleanup.outputs.old || 'unknown'"));
        assertTrue(workflow.contains("steps.cleanup.outputs.protected || 'unknown'"));
        assertTrue(workflow.contains("steps.cleanup.outputs.selected || 'unknown'"));
        assertTrue(workflow.contains("steps.cleanup.outputs.remaining || 'unknown'"));
    }

    private JsonObject execute(JsonObject config) throws Exception {
        Path root = root();
        Path input = Files.createTempFile(temporary, "input-", ".json");
        Files.writeString(input, config.toString(), StandardCharsets.UTF_8);
        return JsonParser.parseString(node(root, "-e", DRIVER,
                root.resolve(".github/scripts/cleanup-workflow-runs.cjs").toString(), input.toString())).getAsJsonObject();
    }

    private String node(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("node"));
        command.addAll(List.of(arguments));
        Path output = Files.createTempFile(temporary, "node-", ".log");
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Node cleanup probe timed out");
            String text = Files.readString(output, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), text);
            return text;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static Path root() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve(".github/scripts/cleanup-workflow-runs.cjs"))) return path;
        }
        throw new AssertionError("Cannot locate cleanup script");
    }

    private static JsonObject config(int old, int recent) {
        JsonObject value = new JsonObject();
        value.addProperty("old", old);
        value.addProperty("recent", recent);
        return value;
    }
    private static JsonObject failure(String kind, int status, int times) {
        JsonObject value = new JsonObject();
        value.addProperty("kind", kind);
        value.addProperty("status", status);
        value.addProperty("times", times);
        if (kind.equals("page")) value.addProperty("page", 5);
        return value;
    }
    private static JsonObject record(int id, int workflow, String date, String status) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("workflow_id", workflow);
        value.addProperty("name", "shared-name");
        value.addProperty("created_at", date);
        value.addProperty("status", status);
        value.addProperty("conclusion", "success");
        return value;
    }
    private static List<Integer> range(int from, int to) { return IntStream.range(from, to).boxed().toList(); }
    private static List<Integer> ints(JsonObject result, String key) {
        List<Integer> values = new ArrayList<>();
        result.getAsJsonArray(key).forEach(value -> values.add(value.getAsInt()));
        return values;
    }
    private static List<JsonObject> requests(JsonObject result, String kind) {
        List<JsonObject> values = new ArrayList<>();
        result.getAsJsonArray("requests").forEach(value -> {
            JsonObject object = value.getAsJsonObject();
            if (kind.equals(object.get("kind").getAsString())) values.add(object);
        });
        return values;
    }
    private static void succeeded(JsonObject result) { assertEquals("", result.get("error").getAsString(), result.toString()); }

    // This adapter contains no semantic assertions and never makes a network request.
    // JUnit inspects requests, outcomes and delays produced by the actual production module.
    private static final String DRIVER = """
        'use strict';
        const cleanup = require(process.argv[1]);
        const config = JSON.parse(require('node:fs').readFileSync(process.argv[2], 'utf8'));
        const seen = { requests: [], deleted: [], delays: [], outputs: {}, error: '' };
        const nativeTimeout = global.setTimeout;
        global.setTimeout = (callback, delay) => {
          seen.delays.push(delay);
          return nativeTimeout(callback, 0);
        };
        Date.now = () => Date.parse('2026-10-07T12:00:00Z');
        const data = [];
        function record(id, date) {
          return { id, workflow_id: 10, name: 'shared-name', created_at: date,
            status: 'completed', conclusion: 'success' };
        }
        for (let id = 1; id <= config.old; id++)
          data.push(record(id, new Date(Date.parse('2026-08-01T00:00:00Z') + id * 1000).toISOString()));
        for (let id = 1; id <= config.recent; id++)
          data.push(record(10000 + id, '2026-10-01T00:00:00Z'));
        data.push(...(config.extra || []));
        async function request(kind, params, response) {
          seen.requests.push({ kind, ...params });
          const fault = config.failure;
          if (fault && fault.kind === kind && (!fault.page || fault.page === params.page) && fault.times-- > 0) {
            throw Object.assign(new Error('injected ' + fault.status),
              {status: fault.status, response: {headers: fault.headers || {}}});
          }
          return response();
        }
        function matching(params) {
          const [from, to] = params.created.split('..').map(Date.parse);
          return data.filter(run => run.status === params.status
            && Date.parse(run.created_at) >= from && Date.parse(run.created_at) <= to
            && (params.workflow_id === undefined || params.workflow_id === run.workflow_id))
            .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || b.id - a.id);
        }
        const actions = {
          listWorkflowRunsForRepo: params => request(params.per_page === 1 ? 'probe' : 'page', params, () => {
            const rows = matching(params);
            const start = ((params.page || 1) - 1) * params.per_page;
            let selected = rows.slice(start, start + params.per_page).map(row => ({...row}));
            if (params.page === 2 && config.corruption === 'truncated') selected = [];
            if (params.page === 2 && config.corruption === 'duplicate') selected = rows.slice(0, params.per_page);
            if (params.page === 2 && config.corruption === 'wrongStatus') selected[0].status = 'in_progress';
            if (params.page === 2 && config.corruption === 'wrongRange') selected[0].created_at = '2026-10-01T00:00:00Z';
            return {data: {total_count: config.corruption === 'invalidCount' ? 'unknown' : rows.length, workflow_runs: selected}};
          }),
          listWorkflowRuns: params => request('recent', params, () => ({data: {
            total_count: config.corruption === 'invalidRecentCount' ? 'unknown' : matching(params).length,
            workflow_runs: matching(params).slice(0, params.per_page)
          }})),
          deleteWorkflowRun: params => request('delete', params, () => { seen.deleted.push(params.run_id); return {}; })
        };
        const github = {rest: {actions, rateLimit: {get: () => request('rate', {}, () => ({data: {
          resources: {core: {remaining: config.corruption === 'invalidRate' ? 'unknown' : config.rate ?? 5000}}
        }}))}}, paginate: {iterator: async function* (method, params) {
          for (let page = 1; ; page++) {
            const response = await method({...params, page});
            yield response;
            if (response.data.workflow_runs.length < params.per_page) return;
          }
        }}};
        const summary = {addHeading() {return this;}, addTable() {return this;}, async write() {}};
        const core = {info() {}, warning() {}, error() {}, summary,
          setOutput(key, value) {seen.outputs[key] = value;}, setFailed(message) {seen.error = message;}};
        cleanup.run({github, core, context: {repo: {owner: 'fixture', repo: 'no-network'}, runId: config.current || 999999},
          env: {RETAIN_DAYS: '30', KEEP_MINIMUM_RUNS: String(config.keep ?? 5), DRY_RUN: String(config.dry ?? false),
            PARALLELISM: '1', MAX_DELETIONS: '4000'}})
          .catch(error => {seen.error = error.message;})
          .finally(() => console.log(JSON.stringify(seen)));
        """;
}
