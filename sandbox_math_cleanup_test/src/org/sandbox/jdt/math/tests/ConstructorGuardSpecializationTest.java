/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

@SuppressWarnings("nls")
class ConstructorGuardSpecializationTest {
    private static final String SOURCE = """
            import java.math.BigInteger;

            public class Calculation extends EarlyCheck {
                private static final Calculation EARLY = new Calculation(new BigInteger("137"));
                private static final BigInteger STANDARD = new BigInteger("137");
                private final BigInteger width;

                public Calculation() { this(new BigInteger("137")); }
                Calculation(BigInteger width) { this.width = width; }
                Calculation(int width) { this.width = BigInteger.valueOf(width); }

                @Override boolean accepts(int count) {
                    if (width != null && width.getClass() == BigInteger.class && width.equals(STANDARD)
                            && positive(count)) {
                        return true;
                    }
                    return false;
                }

                private boolean positive(int count) { return count > 0; }

                public static String probe() {
                    var standard = new Calculation();
                    return EARLY.accepts(1) + ":" + standard.before + ":" + standard.accepts(2)
                            + ":" + standard.accepts(0) + ":" + new Calculation(137).accepts(1)
                            + ":" + new Calculation(138).accepts(1)
                            + ":" + new Calculation(null).accepts(1)
                            + ":" + new Calculation(new CustomWidth()).accepts(1);
                }

                private static final class CustomWidth extends BigInteger {
                    private static final long serialVersionUID = 1L;
                    CustomWidth() { super("137"); }
                    @Override public boolean equals(Object ignored) { throw new AssertionError(); }
                }
            }

            abstract class EarlyCheck {
                final boolean before = accepts(1);
                abstract boolean accepts(int count);
            }
            """;

    @TempDir Path temp;

    @Test
    void cachesOnlyTheBoundPrefixAndKeepsEarlyObservations() throws Exception {
        var result = analyze(SOURCE, true, OptimizationGoal.LOWER_ESTIMATED_RUNTIME);
        assertTrue(result.changed(), result.diagnostics().toString());
        assertEquals(1, result.replacements().size());
        assertEquals(1, result.evidence().size());
        assertTrue(result.evidence().getFirst().sourcePlan().changes().size() >= 4);
        var document = new Document(SOURCE);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        assertTrue(generated.contains("private final boolean "));
        assertTrue(generated.contains("? this.width != null && Calculation.STANDARD != null"));
        assertTrue(generated.contains("&& positive(count)"));
        assertEquals("true:false:true:false:true:false:false:false", probe(SOURCE));
        assertEquals(probe(SOURCE), probe(generated));
        assertFalse(analyze(generated, true, OptimizationGoal.LOWER_ESTIMATED_RUNTIME).changed());
        undo.apply(document);
        assertEquals(SOURCE, document.get());
    }

    @Test
    void keepsUnprovedGuardsAndConstructorPaths() {
        assertFalse(analyze(SOURCE, false, OptimizationGoal.LOWER_ESTIMATED_RUNTIME).changed());
        assertFalse(analyze(SOURCE, true, OptimizationGoal.READABILITY).changed());
        assertFalse(analyze(SOURCE.replace("width.getClass() == BigInteger.class && ", ""),
                true, OptimizationGoal.LOWER_ESTIMATED_RUNTIME).changed());
        assertFalse(analyze(SOURCE.replace("width.equals(STANDARD)", "width.equals(selectStandard())")
                .replace("private boolean positive", "private BigInteger selectStandard() { return STANDARD; }\n    private boolean positive"),
                true, OptimizationGoal.LOWER_ESTIMATED_RUNTIME).changed());
        assertFalse(analyze(SOURCE.replace("this.width = BigInteger.valueOf(width);",
                "if (width > 0) this.width = BigInteger.valueOf(width); else this.width = null;"),
                true, OptimizationGoal.LOWER_ESTIMATED_RUNTIME).changed());
    }

    private static org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Analysis analyze(
            String source, boolean optIn, OptimizationGoal goal) {
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
                goal, 1_000_000, 20_000, false, 17, List.of(), optIn);
        return MathTestSupport.analyze(MathTestSupport.parse(source), source, options, null, -1, 0);
    }

    private String probe(String source) throws Exception {
        Path directory = Files.createTempDirectory(temp, "constructor-guard-");
        Path input = directory.resolve("Calculation.java");
        Files.writeString(input, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17",
                "-classpath", directory.toString(), "-d", directory.toString(), input.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null)) {
            return (String) loader.loadClass("Calculation").getMethod("probe").invoke(null);
        }
    }
}
