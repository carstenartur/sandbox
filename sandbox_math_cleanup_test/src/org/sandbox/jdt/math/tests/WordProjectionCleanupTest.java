/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** The qualified SDK's new generic relation must reach the existing source-edit path. */
class WordProjectionCleanupTest {
    @TempDir Path temporary;

    @Test void wordReconstructionIsIndependentlyVerifiedAppliedCompiledAndUndone() throws Exception {
        String source = "public class Calculation { public static long run(long value) { "
                + "long high = value >>> 32; long low = (long)(int)value & 0xffffffffL; return (high << 32) | low; } }";
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
        var analysis = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, new NullProgressMonitor(), -1, 0);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().stream().allMatch(e -> e.cost().estimatedRuntimeImprovement()));
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document);
        assertEquals(source, document.get());
        assertFalse(generated.contains(">>>"), generated); assertFalse(generated.contains("<<"), generated);
        assertFalse(generated.contains("0xffffffff"), generated);
        try (var original = compile("before", source); var replacement = compile("after", generated)) {
            var before = original.loadClass("Calculation").getMethod("run", long.class);
            var after = replacement.loadClass("Calculation").getMethod("run", long.class);
            for (long value : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 0x12345678ffffffffL}) {
                assertEquals(value, before.invoke(null, value)); assertEquals(value, after.invoke(null, value));
            }
            var random = new SplittableRandom(1114);
            for (int i = 0; i < 128; i++) {
                long value = random.nextLong(); assertEquals(before.invoke(null, value), after.invoke(null, value));
            }
        }
    }

    private URLClassLoader compile(String name, String source) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(name)), file = directory.resolve("Calculation.java");
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-d", directory.toString(), file.toString()));
        return new URLClassLoader(new URL[]{directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
}
