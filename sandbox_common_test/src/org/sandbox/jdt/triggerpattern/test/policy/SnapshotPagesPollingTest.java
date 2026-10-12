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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Executes the workflow's actual inline script; only transport, clock and timers are substituted. */
class SnapshotPagesPollingTest {
    private static final String WORKFLOW = ".github/workflows/deploy-snapshot.yml";
    private static final String SHA = "1".repeat(40);
    @TempDir Path temporary;

    @Test void selectedDeploymentCanSucceedAfterTwelveMinutes() throws Exception {
        JsonObject result = execute("delayed_success");
        assertEquals("", result.get("error").getAsString(), result.toString());
        assertTrue(result.get("completedNormally").getAsBoolean());
        assertEquals(720_000, result.get("elapsed").getAsLong());
        assertEquals(73, result.getAsJsonArray("requests").size());
        assertEquals(1, successMessages(result).size());
        assertTrue(successMessages(result).get(0).startsWith("GitHub Pages deployed " + SHA + ":"));
    }

    @Test void anotherCommitCannotSatisfyTheSelectedDeployment() throws Exception {
        timedOut(execute("wrong_sha"));
    }

    @Test void terminalFailureStopsWithoutAnotherPoll() throws Exception {
        JsonObject result = execute("terminal_failure");
        assertTrue(result.get("error").getAsString().contains("GitHub Pages did not deploy " + SHA + ": failure"));
        assertEquals(0, result.get("elapsed").getAsLong());
        assertEquals(1, result.getAsJsonArray("requests").size());
        assertEquals(0, result.getAsJsonArray("delays").size());
        assertFalse(result.get("completedNormally").getAsBoolean());
        assertTrue(successMessages(result).isEmpty());
    }

    @Test void permanentlyQueuedDeploymentExpiresAfterThirtyMinutes() throws Exception {
        timedOut(execute("forever_queued"));
    }

    @Test void blockedRequestReceivesAndPropagatesTheThirtySecondAbortSignal() throws Exception {
        JsonObject result = execute("http_abort");
        assertEquals("TimeoutError", result.get("errorName").getAsString(), result.toString());
        assertTrue(result.get("signalAborted").getAsBoolean());
        assertEquals(1, result.getAsJsonArray("abortTimeouts").size());
        assertEquals(30_000, result.getAsJsonArray("abortTimeouts").get(0).getAsInt());
        assertEquals(1, result.getAsJsonArray("requests").size());
        assertTrue(result.getAsJsonArray("requests").get(0).getAsJsonObject().get("signalPresent").getAsBoolean());
        assertEquals(0, result.getAsJsonArray("delays").size());
        assertFalse(result.get("completedNormally").getAsBoolean());
        assertTrue(successMessages(result).isEmpty());
    }

    private static void timedOut(JsonObject result) {
        assertEquals("Timed out waiting for GitHub Pages to deploy " + SHA + ".", result.get("error").getAsString());
        assertEquals(1_800_000, result.get("elapsed").getAsLong());
        assertEquals(180, result.getAsJsonArray("requests").size());
        assertFalse(result.get("completedNormally").getAsBoolean());
        assertTrue(successMessages(result).isEmpty());
    }

    private static List<String> successMessages(JsonObject result) {
        List<String> messages = new ArrayList<>();
        result.getAsJsonArray("messages").forEach(value -> {
            if (value.getAsString().startsWith("GitHub Pages deployed ")) messages.add(value.getAsString());
        });
        return messages;
    }

    private JsonObject execute(String scenario) throws Exception {
        Path root = root();
        Path script = Files.createTempFile(temporary, "pages-poller-", ".js");
        Files.writeString(script, inlineScript(root), StandardCharsets.UTF_8);
        Path output = Files.createTempFile(temporary, "node-", ".log");
        Process process = new ProcessBuilder("node", "-e", DRIVER, script.toString(), scenario, SHA)
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Node Pages probe timed out");
            String text = Files.readString(output, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), text);
            JsonObject result = JsonParser.parseString(text).getAsJsonObject();
            assertFalse(result.getAsJsonArray("requests").isEmpty(), text);
            result.getAsJsonArray("requests").forEach(value -> {
                JsonObject request = value.getAsJsonObject();
                assertEquals("gh-pages", request.get("branch").getAsString());
                assertEquals(SHA, request.get("head_sha").getAsString());
                assertEquals("dynamic", request.get("event").getAsString());
                assertEquals(100, request.get("per_page").getAsInt());
            });
            return result;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String inlineScript(Path root) throws Exception {
        List<String> lines = Files.readAllLines(root.resolve(WORKFLOW), StandardCharsets.UTF_8);
        String step = "      - name: Wait for the selected GitHub Pages deployment";
        assertEquals(1, lines.stream().filter(step::equals).count(), "Expected exactly one Pages wait step");
        int start = lines.indexOf(step) + 1;
        while (start < lines.size() && !lines.get(start).startsWith("      - ")
                && !lines.get(start).equals("          script: |")) start++;
        assertTrue(start < lines.size() && lines.get(start).equals("          script: |"), "Missing inline script");
        StringBuilder script = new StringBuilder();
        for (int index = start + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.isBlank() && !line.startsWith("            ")) break;
            script.append(line.isBlank() ? "" : line.substring(12)).append('\n');
        }
        assertFalse(script.toString().isBlank(), "Empty inline script");
        return script.toString();
    }

    private static Path root() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve(WORKFLOW))) return path;
        }
        throw new AssertionError("Cannot locate snapshot workflow");
    }

    // This transport adapter has no semantic assertions and makes no network requests.
    // The abort case records the production timeout argument and shortens only the native timer.
    private static final String DRIVER = """
        'use strict';
        const source = require('node:fs').readFileSync(process.argv[1], 'utf8');
        const scenario = process.argv[2], sha = process.argv[3];
        const seen = { scenario, requests: [], delays: [], messages: [], abortTimeouts: [], elapsed: 0,
          completedNormally: false, signalAborted: false, error: '', errorName: '' };
        const actions = {
          async listWorkflowRunsForRepo(options) {
            const { request, ...query } = options;
            const signal = request?.signal;
            seen.requests.push({ ...query, signalPresent: Boolean(signal) });
            if (seen.requests.length > 500) throw new Error('Mock API request budget exhausted');
            if (scenario === 'http_abort') {
              if (!signal) throw new Error('No request abort signal');
              const keepAlive = setTimeout(() => {}, 1000);
              try {
                return await new Promise((resolve, reject) => {
                  const abort = () => { seen.signalAborted = signal.aborted; reject(signal.reason); };
                  if (signal.aborted) abort();
                  else signal.addEventListener('abort', abort, { once: true });
                });
              } finally { clearTimeout(keepAlive); }
            }
            const run = { head_sha: sha, name: 'pages build and deployment', status: 'queued',
              conclusion: null, html_url: 'https://example.invalid/actions/runs/1' };
            if (scenario === 'delayed_success' && seen.elapsed >= 720000) {
              run.status = 'completed'; run.conclusion = 'success';
            } else if (scenario === 'wrong_sha') {
              run.head_sha = '2'.repeat(40); run.status = 'completed'; run.conclusion = 'success';
            } else if (scenario === 'terminal_failure') {
              run.status = 'completed'; run.conclusion = 'failure';
            }
            return { data: { workflow_runs: [run] } };
          }
        };
        const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
        const poll = new AsyncFunction('github', 'context', 'core', 'process', 'Date', 'setTimeout',
          'AbortSignal', source);
        (async () => {
          try {
            await poll({ rest: { actions } }, { repo: { owner: 'example', repo: 'sandbox' } },
              { info: message => seen.messages.push(message) }, { env: { GH_PAGES_SHA: sha } },
              { now: () => seen.elapsed }, (callback, delay) => {
                seen.delays.push(delay); seen.elapsed += delay; queueMicrotask(callback);
              }, { timeout: milliseconds => {
                seen.abortTimeouts.push(milliseconds);
                return AbortSignal.timeout(scenario === 'http_abort' ? 1 : milliseconds);
              } });
            seen.completedNormally = true;
          } catch (error) { seen.error = error.message; seen.errorName = error.name; }
          process.stdout.write(JSON.stringify(seen));
        })().catch(error => { console.error(error); process.exitCode = 1; });
        """;
}
