/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.SafetyProfile;

class CharacterLiteralRoundTripTest {
    @TempDir Path temporary;

    @Test
    void nonAsciiCharacterSurvivesIndependentReExtractionAndJava8Compilation() throws Exception {
        check("'\\uffff'", 65535);
    }

    @Test
    void escapedCharacterCodeUnitsKeepTheirJavaValue() throws Exception {
        for (var example : Map.ofEntries(Map.entry("'\\0'", 0), Map.entry("'\\n'", 10),
                Map.entry("'\\r'", 13), Map.entry("'\\t'", 9), Map.entry("'\\b'", 8),
                Map.entry("'\\f'", 12), Map.entry("'\\''", 39), Map.entry("'\\\\'", 92),
                Map.entry("'\\ud800'", 55296), Map.entry("'\\u2028'", 8232)).entrySet()) {
            check(example.getKey(), example.getValue());
        }
    }

    @Test
    void byteAndShortConstantPromotionsSurviveIndependentReExtraction() throws Exception {
        checkBound("byte", "Byte.MIN_VALUE", -128, Set.of(NumericKind.BYTE, NumericKind.INT));
        checkBound("byte", "Byte.MAX_VALUE", 127, Set.of(NumericKind.BYTE, NumericKind.INT));
        checkBound("short", "Short.MIN_VALUE", -32768, Set.of(NumericKind.SHORT, NumericKind.INT));
        checkBound("short", "Short.MAX_VALUE", 32767, Set.of(NumericKind.SHORT, NumericKind.INT));
    }

    @Test
    voidExplicitConstantCastsAndQualifiedNamesAreNotRewritten() throws Exception {
        for (String expression : new String[] {"(int)'\\uffff'", "Byte.MIN_VALUE", "Short.MAX_VALUE"}) {
            String source = "public class Calculation { public static int compute(int x) { return x+"
                    + expression + "+0; }}";
            var options = JavaComputationExtractorTest.options(Set.of(NumericKind.INT, NumericKind.CHAR,
                    NumericKind.BYTE, NumericKind.SHORT), SafetyProfile.PRESERVE_JAVA);
            var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options,
                    new NullProgressMonitor(), -1, 0);
            assertFalse(analysis.changed(), expression + ": " + analysis.diagnostics());
            assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("CONSTANT_EXPRESSION_PRESERVED")));
            Document document = new Document(source);
            analysis.newEdit().apply(document);
            assertEquals(source, document.get());
        }
    }

    private void check(String literal, int expectedCodeUnit) throws Exception {
        // The Java promotion still inserts char-to-int in the typed plan. Do not
        // request rewriting a deliberately spelled constant cast in source.
        checkExpression("", literal, expectedCodeUnit, Set.of(NumericKind.INT, NumericKind.CHAR), null);
    }

    private void checkBound(String type, String constant, int value, Set<NumericKind> kinds) throws Exception {
        String declaration = type + " bound=" + constant + ";";
        checkExpression(declaration, "bound", value, kinds, declaration);
    }

    private void checkExpression(String declarations, String literal, int expectedCodeUnit,
            Set<NumericKind> kinds, String preserved) throws Exception {
        String source = "public class Calculation { public static int compute(int x) { " + declarations
                + "int r=x+" + literal + "+0; return r; }}";
        var options = JavaComputationExtractorTest.options(kinds, SafetyProfile.PRESERVE_JAVA);
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, new NullProgressMonitor(), -1, 0);
        assertTrue(analysis.changed(), literal + ": " + analysis.diagnostics());
        assertFalse(analysis.evidence().isEmpty(), "The generated code must pass independent re-extraction");
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        if (preserved != null) assertTrue(generated.contains(preserved), generated);
        undo.apply(document);
        assertEquals(source, document.get());
        Path output = Files.createTempDirectory(temporary, "char-");
        Path file = output.resolve("Calculation.java");
        Files.writeString(file, generated);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "8", "-Xlint:-options", "-d", output.toString(), file.toString()), generated);
        try (var loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, null)) {
            var method = loader.loadClass("Calculation").getMethod("compute", int.class);
            for (int input : new int[] { 0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE })
                assertEquals(input + expectedCodeUnit, method.invoke(null, input), literal);
        }
    }
}
