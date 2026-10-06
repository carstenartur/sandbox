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
        checkExpression("Byte.MIN_VALUE", -128, Set.of(NumericKind.BYTE, NumericKind.INT));
        checkExpression("Byte.MAX_VALUE", 127, Set.of(NumericKind.BYTE, NumericKind.INT));
        checkExpression("Short.MIN_VALUE", -32768, Set.of(NumericKind.SHORT, NumericKind.INT));
        checkExpression("Short.MAX_VALUE", 32767, Set.of(NumericKind.SHORT, NumericKind.INT));
    }

    private void check(String literal, int expectedCodeUnit) throws Exception {
        checkExpression("(int)" + literal, expectedCodeUnit, Set.of(NumericKind.INT, NumericKind.CHAR));
    }

    private void checkExpression(String literal, int expectedCodeUnit, Set<NumericKind> kinds) throws Exception {
        String source = "public class Calculation { public static int compute(int x) { int r=x+"
                + literal + "+0; return r; }}";
        var options = JavaComputationExtractorTest.options(kinds, SafetyProfile.PRESERVE_JAVA);
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, new NullProgressMonitor(), -1, 0);
        assertTrue(analysis.changed(), literal + ": " + analysis.diagnostics());
        Document document = new Document(source);
        analysis.newEdit().apply(document);
        Path output = Files.createTempDirectory(temporary, "char-");
        Path file = output.resolve("Calculation.java");
        Files.writeString(file, document.get());
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "8", "-Xlint:-options", "-d", output.toString(), file.toString()), document.get());
        try (var loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, null)) {
            var method = loader.loadClass("Calculation").getMethod("compute", int.class);
            for (int input : new int[] { 0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE })
                assertEquals(input + expectedCodeUnit, method.invoke(null, input), literal);
        }
    }
}
