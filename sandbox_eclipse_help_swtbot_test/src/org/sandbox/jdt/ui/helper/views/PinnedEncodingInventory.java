/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.sandbox.jdt.triggerpattern.git.UnifiedDiffFormatter;

/** Exact report contracts shared by the pinned encoding preview and headless checks. */
final class PinnedEncodingInventory {
    static final String READER_SOURCE = "ui/org/eclipse/jdt/internal/ui/preferences/formatter/FormatterProfileStore.java"; //$NON-NLS-1$
    private static final String READER_BEFORE = "FileReader reader= new FileReader(file)"; //$NON-NLS-1$

    private PinnedEncodingInventory() {
    }

    static void assertControls(Path evidence) throws Exception {
        var controls = report(evidence.resolve("controls.json"), 2, 0); //$NON-NLS-1$
        assertTrue(controls.getAsJsonArray("changedFiles").isEmpty()); //$NON-NLS-1$
        assertEquals(0, Files.size(evidence.resolve("controls.patch"))); //$NON-NLS-1$
    }

    static void assertReader(Path evidence, Path source, byte[] originalBytes, boolean preferUtf8) throws Exception {
        var reader = report(evidence.resolve("reader.json"), 1, 1); //$NON-NLS-1$
        var changed = reader.getAsJsonArray("changedFiles"); //$NON-NLS-1$
        assertEquals(1, changed.size());
        assertEquals(source.toRealPath(), Path.of(changed.get(0).getAsString()).toRealPath());
        String original = new String(originalBytes, UTF_8);
        assertTrue(original.contains(READER_BEFORE));
        assertEquals(original.indexOf(READER_BEFORE), original.lastIndexOf(READER_BEFORE));
        String charset = preferUtf8 ? "StandardCharsets.UTF_8" : "Charset.defaultCharset()"; //$NON-NLS-1$ //$NON-NLS-2$
        String addedImport = "import java.nio.charset." + (preferUtf8 ? "StandardCharsets" : "Charset") + ";\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        String importBefore = "import java.io.IOException;\n"; //$NON-NLS-1$
        assertTrue(original.contains(importBefore));
        String expected = original.replace(importBefore, importBefore + addedImport)
                .replace(READER_BEFORE, "FileReader reader= new FileReader(file, " + charset + ")"); //$NON-NLS-1$ //$NON-NLS-2$
        assertArrayEquals(UnifiedDiffFormatter.format(READER_SOURCE, originalBytes, expected.getBytes(UTF_8)),
                Files.readAllBytes(evidence.resolve("reader.patch"))); //$NON-NLS-1$
    }

    private static JsonObject report(Path path, int processed, int changed) throws Exception {
        var report = JsonParser.parseString(Files.readString(path, UTF_8)).getAsJsonObject();
        assertEquals("sandbox-cleanup", report.get("tool").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("check", report.get("mode").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(processed, report.get("filesProcessed").getAsInt()); //$NON-NLS-1$
        assertEquals(changed, report.get("filesChanged").getAsInt()); //$NON-NLS-1$
        assertEquals(0, report.get("errorCount").getAsInt()); //$NON-NLS-1$
        assertTrue(report.getAsJsonArray("errors").isEmpty()); //$NON-NLS-1$
        return report;
    }
}
