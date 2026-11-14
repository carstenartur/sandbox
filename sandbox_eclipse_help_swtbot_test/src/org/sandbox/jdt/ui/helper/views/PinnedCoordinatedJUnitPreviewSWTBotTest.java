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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.jupiter.api.Test;

/** The patched JDT preview must implement the strict upstream candidate's closed three-file contract. */
public class PinnedCoordinatedJUnitPreviewSWTBotTest {
    private static final String PROJECT = "org.eclipse.jdt.ui.tests";
    private static final String OWNER = "ui/org/eclipse/jdt/ui/tests/core/rules/JUnitWorkspaceTestSetup.java";
    private static final String CONSUMER = "ui/org/eclipse/jdt/junit/tests/TestRunSessionSerializationTests";
    private static final String NAME = "org.eclipse.jdt.ui.tests.core.rules.JUnitWorkspaceTestSetup";
    private static final String CANDIDATE = "external-resource:" + NAME;
    private static final Set<String> FILES = Set.of(OWNER, CONSUMER + "3.java", CONSUMER + "4.java");

    @Test
    public void selectAndApplyTheStrictWorkspaceSetupCandidate() throws Exception {
        var scenario = new CleanupScreenshotScenario("pinned-junit-workspace-setup", PreviewContract.ATOMIC_CANDIDATE,
                PROJECT, FILES, FILES);
        var sandbox = SandboxCheckout.locate("sandbox.repository.root");
        Path corpus = Path.of(System.getenv("SANDBOX_JUNIT_CORPUS_EVIDENCE"));
        Path evidence = Path.of(System.getenv("SANDBOX_STATUS_EVIDENCE"), "junit-atomic");
        Files.createDirectories(evidence);
        JsonObject strict = json(corpus.resolve("provenance.json"));
        assertEquals("strict", strict.get("mode").getAsString());
        assertEquals("PASS", strict.get("result").getAsString());
        var upstreamTests = new TreeMap<String, List<String>>();
        for (var expected : Map.of("TestRunSessionSerializationTests3", 7, "TestRunSessionSerializationTests4", 5).entrySet()) {
            String name = "org.eclipse.jdt.junit.tests." + expected.getKey();
            var baseline = PinnedJUnitPreviewSWTBotTest.passedSelectedTests(corpus.resolve("baseline"), name);
            assertEquals(expected.getValue().intValue(), baseline.size(), name);
            assertEquals(baseline, PinnedJUnitPreviewSWTBotTest.passedSelectedTests(corpus.resolve("migrated"), name));
            upstreamTests.put(name, baseline);
        }
        var pins = PinnedFilePreviewDriver.properties(sandbox.resolve("qa/upstream-jdt/pins.env"));
        String pin = pins.getProperty("PIN_JDT_UI_COMMIT");
        assertEquals(pin, strict.getAsJsonObject("jdtUi").get("commit").getAsString());
        Path profilePath = sandbox.resolve("qa/upstream-jdt/junit4-to-jupiter.properties");
        JsonObject artifacts = strict.getAsJsonObject("artifacts");
        assertEquals(artifacts.get("cleanupProfileSha256").getAsString(), digest(Files.readAllBytes(profilePath)));
        Path report = corpus.resolve("cleanup-check-report.json");
        assertEquals(artifacts.get("checkReportSha256").getAsString(), digest(Files.readAllBytes(report)));
        var plan = json(report).getAsJsonArray("planningDiagnostics").asList().stream().map(x -> x.getAsJsonObject())
                .flatMap(x -> x.getAsJsonArray("candidates").asList().stream()).map(x -> x.getAsJsonObject())
                .filter(c -> c.get("candidateId").getAsString().equals(CANDIDATE)).toList();
        assertEquals(1, plan.size());
        var candidate = plan.getFirst();
        assertEquals("TRANSFORMED", candidate.get("outcome").getAsString());
        var project = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT);
        assertTrue(project.isAccessible());
        var data = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath().toRealPath();
        assertEquals(Path.of(System.getenv("SANDBOX_STATUS_WORKSPACE")).toRealPath(), data);
        assertEquals(pins, PinnedFilePreviewDriver.properties(data.resolve(".sandbox-jdt-migration-qa-pins.env")));
        PinnedFilePreviewDriver.resolvePinnedTarget();
        var checkout = Objects.requireNonNull(project.getLocation().toFile().toPath().toRealPath().getParent(),
                "Missing repository for the pinned JDT project");
        var before = new TreeMap<String, byte[]>();
        var diagnostics = new TreeMap<String, List<String>>();
        var external = new TreeSet<String>();
        try (Git git = Git.open(checkout.toFile()); RevWalk walk = new RevWalk(git.getRepository())) {
            var repo = git.getRepository();
            assertEquals(pins.getProperty("PIN_JDT_UI_REPOSITORY"), repo.getConfig().getString("remote", "origin", "url"));
            assertEquals(pin, repo.resolve("HEAD^{commit}").name());
            assertEquals(pin, repo.resolve("refs/tags/" + pins.getProperty("PIN_JDT_UI_REF") + "^{commit}").name());
            for (String path : FILES) {
                var file = project.getFile(path);
                var unit = JavaCore.createCompilationUnitFrom(file);
                var handle = unit.getPrimary().getHandleIdentifier().getBytes(StandardCharsets.UTF_8);
                String id = "cu-" + digest(handle).substring(0, 12);
                external.add(id);
                if (OWNER.equals(path)) {
                    assertEquals(candidate.get("ownerCompilationUnit").getAsString(), id);
                }
                try (var tree = TreeWalk.forPath(repo, PROJECT + "/" + path, walk.parseCommit(repo.resolve("HEAD")).getTree())) {
                    assertNotNull(tree, path);
                    byte[] original = repo.open(tree.getObjectId(0)).getBytes();
                    assertArrayEquals(original, Files.readAllBytes(file.getLocation().toFile().toPath()), path);
                    before.put(path, original);
                }
                var problems = PinnedFilePreviewDriver.compilerDiagnostics(unit);
                assertTrue(problems.stream().noneMatch(problem -> problem.startsWith("error ")),
                        () -> path + " baseline compiler errors: " + problems);
                diagnostics.put(path, problems);
            }
        }
        assertEquals(external, new TreeSet<>(candidate.getAsJsonArray("relatedCompilationUnits").asList().stream()
                .map(x -> x.getAsString()).toList()));
        String fullPatch = Files.readString(corpus.resolve("migration.patch"));
        assertEquals(artifacts.get("migrationPatchSha256").getAsString(), digest(fullPatch.getBytes(StandardCharsets.UTF_8)));
        Map<String, byte[]> expected = testedMigration(before, fullPatch);
        var bot = new SWTWorkbenchBot();
        var driver = new CleanupWizardDriver(bot);
        PinnedFilePreviewDriver.selectProfile(PinnedFilePreviewDriver.properties(profilePath), scenario.id());
        Path image = evidence.resolve("junit-workspace-setup-pinned.png");
        var applied = new TreeMap<String, String>();
        CleanupWorkbenchDriver.run(scenario, PreviewContract.ATOMIC_CANDIDATE, () -> {
            var wizard = driver.openCleanUpWizard(PinnedFilePreviewDriver.selectPinnedFile(bot, project.getFile(OWNER)));
            var custom = wizard.bot().radio("Use configured profiles");
            if (!custom.isSelected()) {
                custom.click();
            }
            var preview = driver.openCleanUpPreview(wizard, NAME);
            var row = CleanupWizardDriver.findTreeItemContaining(preview.tree(), NAME);
            assertNotNull(row);
            assertEquals(0, row.getItems().length, "The atomic candidate cannot have file-level checkboxes");
            assertTrue(row.isChecked());
            row.uncheck();
            assertFalse(row.isChecked());
            row.check();
            for (var sibling : preview.tree().getAllItems()) {
                if (!sibling.widget.equals(row.widget) && sibling.isChecked()) {
                    sibling.uncheck();
                }
            }
            row.select();
            waitForFiles(bot, preview.shell());
            var table = preview.shell().bot().table();
            for (int index = 0; index < table.rowCount(); index++) {
                String file = table.cell(index, 0);
                table.select(index);
                String expectedOld = OWNER.equals(file) ? "extends ExternalResource" : "@Rule";
                String expectedNew = OWNER.equals(file) ? "BeforeEachCallback" : "@RegisterExtension";
                waitForDiff(bot, preview.shell(), expectedOld, expectedNew);
            }
            int selected = table.indexOf(OWNER);
            assertTrue(selected >= 0);
            table.select(selected);
            waitForDiff(bot, preview.shell(), "extends ExternalResource", "BeforeEachCallback");
            PinnedFilePreviewDriver.capture(preview.shell(), bot, image);
            CleanupWizardDriver.clickButtonAsync(preview.shell(), "Finish");
            driver.waitForShellToClose(preview.shell(), "Clean Up wizard");
            for (String path : FILES) {
                var file = project.getFile(path);
                byte[] bytes = Files.readAllBytes(file.getLocation().toFile().toPath());
                assertArrayEquals(expected.get(path), bytes, path);
                var added = new ArrayList<>(PinnedFilePreviewDriver.compilerDiagnostics(JavaCore.createCompilationUnitFrom(file)));
                diagnostics.get(path).forEach(added::remove);
                assertTrue(added.isEmpty(), () -> file + ": " + added);
                applied.put(path, digest(bytes));
            }
        });
        for (String path : FILES) {
            var file = project.getFile(path);
            assertArrayEquals(before.get(path), Files.readAllBytes(file.getLocation().toFile().toPath()), path);
            assertEquals(diagnostics.get(path), PinnedFilePreviewDriver.compilerDiagnostics(JavaCore.createCompilationUnitFrom(file)));
        }
        var provenance = new JsonObject();
        provenance.addProperty("scenario", scenario.id());
        provenance.addProperty("previewContract", scenario.previewContract().name());
        provenance.addProperty("repository", pins.getProperty("PIN_JDT_UI_REPOSITORY"));
        provenance.addProperty("ref", pins.getProperty("PIN_JDT_UI_REF"));
        provenance.addProperty("commit", pin);
        provenance.addProperty("project", PROJECT);
        provenance.addProperty("candidateId", CANDIDATE);
        provenance.add("upstreamTests", new com.google.gson.Gson().toJsonTree(upstreamTests));
        provenance.addProperty("cleanupProfile", "junit4-to-jupiter.properties");
        provenance.addProperty("profileSha256", artifacts.get("cleanupProfileSha256").getAsString());
        provenance.addProperty("strictMigrationPatchSha256", artifacts.get("migrationPatchSha256").getAsString());
        provenance.addProperty("sourcePath", OWNER);
        provenance.add("appliedSha256", new com.google.gson.Gson().toJsonTree(applied));
        provenance.addProperty("screenshotSha256", digest(Files.readAllBytes(image)));
        Files.writeString(evidence.resolve("junit-workspace-setup-pinned.provenance.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(provenance) + "\n");
    }

    /** Apply only the audited file patches in an isolated repository, preserving upstream's exact diff format. */
    private static Map<String, byte[]> testedMigration(Map<String, byte[]> originals, String patch) throws Exception {
        var result = new TreeMap<String, byte[]>();
        Path temporary = Files.createTempDirectory("pinned-junit-expected-");
        try (Git reference = Git.init().setDirectory(temporary.toFile()).call()) {
            for (var original : originals.entrySet()) {
                String relative = PROJECT + "/" + original.getKey();
                Path destination = temporary.resolve(relative);
                Files.createDirectories(Objects.requireNonNull(destination.getParent()));
                Files.write(destination, original.getValue());
                String header = "diff --git a/" + relative + " b/" + relative + "\n";
                int begin = patch.indexOf(header);
                assertTrue(begin >= 0, () -> "Missing strict migration for " + relative);
                int next = patch.indexOf("\ndiff --git ", begin);
                String filePatch = patch.substring(begin, next < 0 ? patch.length() : next + 1);
                try (var input = new java.io.ByteArrayInputStream(filePatch.getBytes(StandardCharsets.UTF_8))) {
                    var changed = reference.apply().setPatch(input).call().getUpdatedFiles();
                    assertEquals(List.of(destination.toFile()), changed, relative);
                }
                result.put(original.getKey(), Files.readAllBytes(destination));
            }
        } finally {
            try (var generated = Files.walk(temporary)) {
                for (Path entry : generated.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(entry);
                }
            }
        }
        return result;
    }

    private static void waitForFiles(SWTWorkbenchBot bot, SWTBotShell shell) {
        bot.waitUntil(new DefaultCondition() {
            @Override public boolean test() {
                var files = shell.bot().table();
                var actual = new TreeSet<String>();
                for (int index = 0; index < files.rowCount(); index++) {
                    actual.add(files.cell(index, 0));
                }
                return FILES.equals(actual);
            }
            @Override public String getFailureMessage() {
                return "The native candidate does not report its three strict corpus files";
            }
        });
    }

    private static void waitForDiff(SWTWorkbenchBot bot, SWTBotShell shell, String oldText, String newText) {
        bot.waitUntil(new DefaultCondition() {
            @Override public boolean test() {
                return UIThreadRunnable.syncExec(shell.display,
                        (Result<Boolean>) () ->
                                PinnedFilePreviewDriver.showVisibleDiff(shell.widget, oldText, newText));
            }
            @Override public String getFailureMessage() {
                return "Candidate diff does not show " + oldText + " and " + newText;
            }
        });
    }

    private static JsonObject json(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
