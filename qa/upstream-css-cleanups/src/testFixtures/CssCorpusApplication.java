/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.css.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.sandbox.qa.css.CssDiagnosticIdentity;

/** Runs the installed CSS implementations on copies of every pinned CSS source. */
public final class CssCorpusApplication implements IApplication {
    private static final String GTK_BINDING_SOURCE = "bundles/org.eclipse.swt/Eclipse SWT PI/gtk/org/eclipse/swt/internal/gtk/swt_functional_gtk_3_20.css";

    @Override
    public Object start(IApplicationContext context) throws Exception {
        String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        Path checkout = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String family = args[2];
        Path fixtures = Path.of(args[3]);
        Files.createDirectories(output);
        var receipt = new Properties();
        receipt.setProperty("family", family);
        receipt.setProperty("status", "FAIL");
        var failures = new ArrayList<String>();
        int changed = 0;
        int fixed = 0;
        int beforeIssues = 0;
        int afterIssues = 0;
        int rejected = 0;
        int files = 0;
        try {
            assertTrue(NodeExecutor.isNpxAvailable(), "Pinned Node/Prettier/Stylelint tools are required");
            receipt.setProperty("prettier", NodeExecutor.executeNpx("prettier", "--version").stdout.trim());
            receipt.setProperty("stylelint", NodeExecutor.executeNpx("stylelint", "--version").stdout.trim());
            List<Path> sources;
            try (var paths = Files.walk(checkout)) {
                sources = paths.filter(Files::isRegularFile).filter(CssCorpusApplication::isCss).sorted().toList();
            }
            assertFalse(sources.isEmpty(), "No CSS corpus sources in " + checkout);
            var project = ResourcesPlugin.getWorkspace().getRoot().getProject("css-corpus-" + family);
            project.create(null);
            project.open(null);
            Path config = fixtures.resolve("stylelint.config.json");
            Files.copy(config, output.resolve("stylelint.config.json"));
            try (var report = new PrintWriter(Files.newBufferedWriter(output.resolve("results.tsv")))) {
                report.println("file\tstatus\tformattedChanged\tfixChanged\tbaselineIssues\tformattedIssues\tfixedIssues");
                for (Path source : sources) {
                    Path relative = checkout.relativize(source);
                    String original = Files.readString(source, StandardCharsets.UTF_8);
                    Path evidence = output.resolve("files").resolve(relative);
                    Files.createDirectories(evidence);
                    Files.writeString(evidence.resolve("original.css"), original, StandardCharsets.UTF_8);
                    IFile file = project.getFile("sample-" + files + "." + extension(source));
                    file.create(bytes(original), true, null);
                    files++;
                    try {
                        var baseline = StylelintRunner.validate(file, config.toString());
                        diagnostics(evidence.resolve("baseline-diagnostics.tsv"), baseline);
                        beforeIssues += baseline.getIssues().size();
                        assertValidConfiguration(baseline);
                        if (family.equals("swt") && relative.toString().replace('\\', '/').equals(GTK_BINDING_SOURCE)) {
                            assertEquals(1, baseline.getIssues().size(), "GTK rejection diagnostic changed");
                            assertEquals("CssSyntaxError", baseline.getIssues().get(0).rule);
                            IOException rejection = assertThrows(IOException.class, () -> PrettierRunner.format(file, "{\"tabWidth\":2}"));
                            assertTrue(rejection.getMessage().contains("CssSyntaxError"), "Unexpected formatter failure");
                            Files.writeString(evidence.resolve("prettier-rejection.txt"), rejection.toString());
                            String retained = StylelintRunner.fix(file, config.toString());
                            Files.writeString(evidence.resolve("stylelint-fixed.css"), retained, StandardCharsets.UTF_8);
                            assertEquals(original, retained, "Unsupported GTK syntax must remain byte-identical");
                            assertEquals(original, StylelintRunner.fix(file, config.toString()));
                            var retainedResult = StylelintRunner.validate(file, config.toString());
                            diagnostics(evidence.resolve("retained-diagnostics.tsv"), retainedResult);
                            assertEquals(counts(baseline), counts(retainedResult), "GTK syntax diagnostic was suppressed");
                            assertEquals(original, Files.readString(file.getLocation().toFile().toPath()), "Rejected source was mutated");
                            assertEquals(original, Files.readString(source), "Upstream source was modified");
                            rejected++;
                            report.println(relative + "\tREJECTED_UNSUPPORTED_SYNTAX\tfalse\tfalse\t1\tn/a\t1");
                            continue;
                        }
                        String originalStructure = structure(fixtures, evidence.resolve("original.css"), evidence.resolve("original.structure.json"));
                        String formatted = PrettierRunner.format(file, "{\"tabWidth\":2}");
                        assertEquals(original, Files.readString(file.getLocation().toFile().toPath()), "Formatter mutated its source");
                        Files.writeString(evidence.resolve("formatted.css"), formatted, StandardCharsets.UTF_8);
                        assertEquals(originalStructure, structure(fixtures, evidence.resolve("formatted.css"), evidence.resolve("formatted.structure.json")), "Prettier changed parsed CSS structure");
                        file.setContents(bytes(formatted), IResource.FORCE, null);
                        assertEquals(formatted, PrettierRunner.format(file, "{\"tabWidth\":2}"), "Prettier is not idempotent");
                        var formattedResult = StylelintRunner.validate(file, config.toString());
                        diagnostics(evidence.resolve("formatted-diagnostics.tsv"), formattedResult);
                        afterIssues += formattedResult.getIssues().size();
                        assertNoNewDiagnostics(baseline, formattedResult);

                        file.setContents(bytes(original), IResource.FORCE, null);
                        String repaired = StylelintRunner.fix(file, config.toString());
                        assertEquals(original, Files.readString(file.getLocation().toFile().toPath()), "Stylelint mutated its source");
                        Files.writeString(evidence.resolve("stylelint-fixed.css"), repaired, StandardCharsets.UTF_8);
                        assertEquals(originalStructure, structure(fixtures, evidence.resolve("stylelint-fixed.css"), evidence.resolve("fixed.structure.json")), "Stylelint changed parsed CSS structure");
                        file.setContents(bytes(repaired), IResource.FORCE, null);
                        assertEquals(repaired, StylelintRunner.fix(file, config.toString()), "Stylelint fix is not idempotent");
                        var fixedResult = StylelintRunner.validate(file, config.toString());
                        diagnostics(evidence.resolve("fixed-diagnostics.tsv"), fixedResult);
                        assertNoNewDiagnostics(baseline, fixedResult);
                        assertEquals(original, Files.readString(source), "Upstream source was modified");
                        boolean formattingChanged = !original.equals(formatted);
                        boolean fixesChanged = !original.equals(repaired);
                        if (formattingChanged) changed++;
                        if (fixesChanged) fixed++;
                        report.println(relative + "\tPASS\t" + formattingChanged + "\t" + fixesChanged + "\t"
                                + baseline.getIssues().size() + "\t" + formattedResult.getIssues().size() + "\t" + fixedResult.getIssues().size());
                    } catch (Exception | AssertionError failure) {
                        failures.add(relative + ": " + failure);
                        try (var log = new PrintWriter(Files.newBufferedWriter(evidence.resolve("failure.txt")))) { failure.printStackTrace(log); }
                        report.println(relative + "\tFAIL");
                    } finally {
                        file.setContents(bytes(original), IResource.FORCE, null);
                    }
                }
            }
            receipt.setProperty("files", Integer.toString(files));
            receipt.setProperty("prettierChanged", Integer.toString(changed));
            receipt.setProperty("stylelintChanged", Integer.toString(fixed));
            receipt.setProperty("baselineIssues", Integer.toString(beforeIssues));
            receipt.setProperty("formattedIssues", Integer.toString(afterIssues));
            receipt.setProperty("failedFiles", Integer.toString(failures.size()));
            receipt.setProperty("rejectedFiles", Integer.toString(rejected));
            receipt.setProperty("formattedFiles", Integer.toString(files - rejected - failures.size()));
            assertTrue(failures.isEmpty(), String.join("\n", failures));
            ResourcesPlugin.getWorkspace().save(true, null);
            receipt.setProperty("status", rejected == 0 ? "PASS" : "PASS_WITH_REJECTIONS");
            return EXIT_OK;
        } finally {
            try (var writer = Files.newBufferedWriter(output.resolve("receipt.properties"))) {
                receipt.store(writer, "Actual Sandbox CSS cleanup corpus run");
            }
            System.out.println(receipt);
        }
    }

    private static boolean isCss(Path path) {
        return List.of("css", "scss", "less").contains(extension(path));
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertValidConfiguration(CSSValidationResult result) {
        assertTrue(result.getIssues().stream().noneMatch(issue -> "invalid-option".equals(issue.rule)),
                "The pinned Stylelint configuration is invalid");
    }

    private static void assertNoNewDiagnostics(CSSValidationResult before, CSSValidationResult after) {
        Map<CssDiagnosticIdentity, Integer> baseline = counts(before);
        counts(after).forEach((diagnostic, count) -> assertTrue(count <= baseline.getOrDefault(diagnostic, 0),
                "New or additional CSS diagnostic: " + diagnostic));
    }

    private static Map<CssDiagnosticIdentity, Integer> counts(CSSValidationResult result) {
        Map<CssDiagnosticIdentity, Integer> counts = new HashMap<>();
        for (var issue : result.getIssues()) counts.merge(CssDiagnosticIdentity.of(issue.severity, issue.rule, issue.message), 1, Integer::sum);
        return counts;
    }

    private static void diagnostics(Path destination, CSSValidationResult result) throws Exception {
        try (var writer = new PrintWriter(Files.newBufferedWriter(destination))) {
            writer.println("line\tcolumn\tseverity\trule\tmessage");
            for (var issue : result.getIssues()) writer.println(issue.line + "\t" + issue.column + "\t" + issue.severity + "\t" + issue.rule + "\t" + issue.message.replace('\n', ' '));
        }
    }

    private static String structure(Path fixtures, Path source, Path destination) throws Exception {
        String node = Path.of(System.getProperty("sandbox.css.node.installDirectory"), "node", "node").toString();
        Process process = new ProcessBuilder(node, fixtures.resolve("css-structure.cjs").toString(),
                System.getProperty("sandbox.css.node.modulesDirectory"), source.toString())
                .redirectOutput(destination.toFile()).redirectError(destination.resolveSibling(destination.getFileName() + ".stderr").toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "CSS parser timed out");
            assertEquals(0, process.exitValue(), "CSS parser failed: " + source);
            return Files.readString(destination);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Override public void stop() { }
}
