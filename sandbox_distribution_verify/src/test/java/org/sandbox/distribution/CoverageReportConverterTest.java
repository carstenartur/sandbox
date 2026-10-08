/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoverageReportConverterTest {
    @TempDir Path root;
    Path input;
    Path output;

    @BeforeEach
    void fixture() throws Exception {
        for (String module : List.of("first", "second_module")) {
            Path directory = Files.createDirectories(root.resolve(module + "/src/example"));
            Files.writeString(directory.resolve("Demo.java"), "package example; class Demo {}\n");
            Files.writeString(root.resolve(module + "/pom.xml"), "<project><artifactId>"
                    + module.replace('_', '-') + "</artifactId></project>");
        }
        input = root.resolve("jacoco.xml");
        output = root.resolve("export/cobertura.xml");
        Path manifest = root.resolve("target/github-coverage/sources.list");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "first/src/example/Demo.java\u0000second_module/src/example/Demo.java\u0000");
        Files.writeString(input, """
                <?xml version="1.0"?><!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="aggregate">
                  <group name="first"><package name="example"><sourcefile name="Demo.java">
                    <line nr="1" ci="5" mi="0"/><line nr="2" ci="0" mi="8"/>
                  </sourcefile></package></group>
                  <group name="second-module"><package name="example"><sourcefile name="Demo.java">
                    <line nr="1" ci="0" mi="5"/><line nr="2" ci="3" mi="8"/>
                  </sourcefile></package></group>
                  <counter type="LINE" covered="2" missed="2"/>
                </report>
                """);
    }

    @Test
    void preservesModuleIdentityAndEveryMeasuredLine() throws Exception {
        CoverageReportConverter.convert(root, input, output);
        org.w3c.dom.Element report;
        try (var stream = Files.newInputStream(output)) {
            report = AggregateInstallationEvidence.xml(stream);
        }
        assertEquals("2", report.getAttribute("lines-covered"));
        assertEquals("4", report.getAttribute("lines-valid"));
        var classes = report.getElementsByTagName("class");
        assertEquals(2, classes.getLength());
        var first = (org.w3c.dom.Element) classes.item(0);
        var second = (org.w3c.dom.Element) classes.item(1);
        assertEquals("first/src/example/Demo.java", first.getAttribute("filename"));
        assertEquals("second_module/src/example/Demo.java", second.getAttribute("filename"));
        assertEquals("1", ((org.w3c.dom.Element) first.getElementsByTagName("line").item(0)).getAttribute("hits"));
        assertEquals("0", ((org.w3c.dom.Element) second.getElementsByTagName("line").item(0)).getAttribute("hits"));
        assertEquals("1", ((org.w3c.dom.Element) second.getElementsByTagName("line").item(1)).getAttribute("hits"));
    }

    @Test
    void rejectsMissingOrAmbiguousSourcesWithoutPublishing() throws Exception {
        Path source = root.resolve("first/src/example/Demo.java");
        Path duplicate = root.resolve("first/another/example/Demo.java");
        Files.createDirectories(duplicate.getParent());
        Files.copy(source, duplicate);
        Files.writeString(root.resolve("target/github-coverage/sources.list"),
                "first/src/example/Demo.java\u0000first/another/example/Demo.java\u0000second_module/src/example/Demo.java\u0000");
        assertThrows(Exception.class, () -> CoverageReportConverter.convert(root, input, output));
        assertFalse(Files.exists(output));
        Files.delete(duplicate);
        Files.delete(source);
        assertThrows(Exception.class, () -> CoverageReportConverter.convert(root, input, output));
        assertFalse(Files.exists(output));
    }

    @Test
    void rejectsLostOrDuplicatedLineData() throws Exception {
        Files.writeString(input, Files.readString(input).replace("covered=\"2\"", "covered=\"3\""));
        assertThrows(Exception.class, () -> CoverageReportConverter.convert(root, input, output));
        assertFalse(Files.exists(output));
    }

    @Test
    void ignoresUntrackedGeneratedCopies() throws Exception {
        Path generated = root.resolve("first/target/generated/example/Demo.java");
        Files.createDirectories(generated.getParent());
        Files.copy(root.resolve("first/src/example/Demo.java"), generated);
        CoverageReportConverter.convert(root, input, output);
    }

    @ParameterizedTest
    @CsvSource({"62,100,false", "63,100,true", "64,100,true", "6299,10000,false",
            "1,1,true", "0,1,false", "0,0,false"})
    void commandEnforcesMinimumLineCoverageWithoutRounding(int covered, int total, boolean accepted) throws Exception {
        var lines = new StringBuilder();
        for (int line = 1; line <= total; line++) {
            lines.append("<line nr=\"").append(line).append("\" ci=\"")
                    .append(line <= covered ? 1 : 0).append("\"/>");
        }
        Path report = root.resolve("sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "<report><group name=\"first\"><package name=\"example\">"
                + "<sourcefile name=\"Demo.java\">" + lines + "</sourcefile></package></group>"
                + "<counter type=\"LINE\" covered=\"" + covered + "\" missed=\"" + (total - covered)
                + "\"/></report>");
        if (accepted) {
            assertDoesNotThrow(() -> CoverageReportConverter.main(new String[] {root.toString()}));
        } else {
            assertThrows(IOException.class, () -> CoverageReportConverter.main(new String[] {root.toString()}));
        }
    }
}
