/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXParseException;

/** Negative provenance/target checks: incomplete evidence never authorizes a patch. */
class LtkRuntimeMetadataTest {
    private static final String VERSION = "3.16.0.v20260912-1900";
    @TempDir
    Path directory;

    @Test
    void addsOneExactlyVersionedLocalPatchWithoutRemovingExistingTargetUnits() throws Exception {
        Path target = target("2026-09");
        LtkRuntimePatch.addToTarget(target, directory.resolve("repository"), VERSION);
        var xml = LtkRuntimePatch.xml(Files.readAllBytes(target));
        assertEquals("1.0.0.v20260912-1900", LtkRuntimePatch.value(xml,
                "//unit[@id='sandbox_patched_ltk_feature.feature.group']/@version"));
        assertEquals("1", LtkRuntimePatch.value(xml, "count(//unit[@id='existing'])"));
        assertEquals("1", LtkRuntimePatch.value(xml, "count(//unit[@id='sandbox_patched_jdt_ui_feature.feature.group'])"));
    }

    @Test
    void rejectsADifferentTargetRelease() throws Exception {
        Path target = target("2026-06");
        String original = Files.readString(target);
        assertThrows(IOException.class, () -> LtkRuntimePatch.addToTarget(target, directory, VERSION));
        assertEquals(original, Files.readString(target));
    }

    @Test
    void rejectsDuplicatePatchInsertionWithoutChangingTarget() throws Exception {
        Path target = target("2026-09");
        LtkRuntimePatch.addToTarget(target, directory, VERSION);
        String original = Files.readString(target);
        assertThrows(IOException.class, () -> LtkRuntimePatch.addToTarget(target, directory, VERSION));
        assertEquals(original, Files.readString(target));
    }

    @Test
    void rejectsXmlExternalEntities() {
        String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///unreadable'>]><x>&e;</x>";
        assertThrows(SAXParseException.class, () -> LtkRuntimePatch.xml(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsAnEmptyTestReportDirectory() {
        assertThrows(IOException.class, () -> LtkRuntimePatch.verifyTests(directory));
    }

    @Test
    void countsDistinctNewRegressionMethods() throws Exception {
        Files.writeString(directory.resolve("TEST-AllTests.xml"), report(""));
        assertEquals(13, LtkRuntimePatch.verifyTests(directory).get("distinctNewRegressions"));
        assertEquals(13, LtkRuntimePatch.verifyTests(directory).get("testCases"));
    }

    @Test
    void rejectsFailureEvenWhenAllNewMethodsArePresent() throws Exception {
        Files.writeString(directory.resolve("TEST-AllTests.xml"), report("<failure message='regression'/>"));
        assertThrows(IOException.class, () -> LtkRuntimePatch.verifyTests(directory));
    }

    @Test
    void rejectsSkippedRegressionMethods() throws Exception {
        Files.writeString(directory.resolve("TEST-AllTests.xml"), report("<skipped/>"));
        assertThrows(IOException.class, () -> LtkRuntimePatch.verifyTests(directory));
    }

    @Test
    void rejectsErrorsAndDoesNotEquateThemWithNoFailures() throws Exception {
        Files.writeString(directory.resolve("TEST-AllTests.xml"), report("<error message='launch failed'/>"));
        assertThrows(IOException.class, () -> LtkRuntimePatch.verifyTests(directory));
    }

    @Test
    void doesNotCountDuplicateMethodsAsCoverage() throws Exception {
        Files.writeString(directory.resolve("TEST-AllTests.xml"), report("").replace("method12", "method11"));
        assertThrows(IOException.class, () -> LtkRuntimePatch.verifyTests(directory));
    }

    private Path target(String release) throws IOException {
        Path target = directory.resolve("eclipse.target");
        Files.writeString(target, """
                <target name="test"><locations>
                  <location type="InstallableUnit">
                    <repository location="https://download.eclipse.org/releases/%s/"/>
                    <unit id="existing" version="0.0.0"/>
                  </location>
                  <location type="InstallableUnit">
                    <repository location="file:/jdt-patch"/>
                    <unit id="sandbox_patched_jdt_ui_feature.feature.group" version="0.0.0"/>
                  </location>
                </locations></target>
                """.formatted(release));
        return target;
    }

    private static String report(String problem) {
        StringBuilder result = new StringBuilder("<testsuite>");
        for (int i = 0; i < 13; i++) {
            result.append("<testcase classname='org.eclipse.ltk.core.refactoring.tests.CompositeChangeTest' name='method")
                    .append(i).append("'>").append(i == 0 ? problem : "").append("</testcase>");
        }
        return result.append("</testsuite>").toString();
    }
}
