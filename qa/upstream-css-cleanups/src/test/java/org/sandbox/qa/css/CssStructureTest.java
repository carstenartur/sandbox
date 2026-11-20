/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CssStructureTest {
    @TempDir Path directory;
    private int sequence;

    @Test
    void formattingAndQuoteChoicePreserveTheFingerprint() throws Exception {
        assertEquals(structure("/* license text */ .a>.b { color:#AABBCC; content:'a b'; margin:1px 2px; }"),
                structure("/* license   text */\n.a > .b {\n color: #aabbcc;\n content: \"a b\";\n margin: 1px   2px;\n}\n"));
    }

    @Test
    void selectorsValuesStringsAndCascadeOrderRemainSignificant() throws Exception {
        assertNotEquals(structure(".a .b { color: red }"), structure(".a.b { color: red }"));
        assertNotEquals(structure(".a { color: red }"), structure(".a { color: blue }"));
        assertNotEquals(structure(".a { content: 'a b' }"), structure(".a { content: 'ab' }"));
        assertNotEquals(structure(".a { color: red; color: blue }"), structure(".a { color: blue; color: red }"));
        assertNotEquals(structure(".a { margin: 1px 2px }"), structure(".a { margin: 1px2px }"));
    }

    @Test
    void atRulesAndImportantFlagsRemainSignificant() throws Exception {
        assertNotEquals(structure("@media screen { .a { color: red } }"), structure("@media print { .a { color: red } }"));
        assertNotEquals(structure(".a { color: red !important }"), structure(".a { color: red }"));
    }

    @Test
    void fragmentIdentifiersAndCustomTokensRemainCaseSensitive() throws Exception {
        assertNotEquals(structure(".a { background: url(#AABBCC) }"), structure(".a { background: url(#aabbcc) }"));
        assertNotEquals(structure(".a { --token: #AABBCC }"), structure(".a { --token: #aabbcc }"));
    }

    private String structure(String css) throws Exception {
        Path file = directory.resolve("sample-" + sequence++ + ".css");
        Files.writeString(file, css);
        Path result = directory.resolve(file.getFileName() + ".json");
        Path error = directory.resolve(file.getFileName() + ".stderr");
        String node = Path.of(System.getProperty("sandbox.css.node.installDirectory"), "node", "node").toString();
        Path adapter = Path.of(System.getProperty("sandbox.css.repo"), "qa/upstream-css-cleanups/src/testFixtures/css-structure.cjs");
        Process process = new ProcessBuilder(node, adapter.toString(), System.getProperty("sandbox.css.node.modulesDirectory"), file.toString())
                .redirectOutput(result.toFile()).redirectError(error.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(error));
            return Files.readString(result);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
