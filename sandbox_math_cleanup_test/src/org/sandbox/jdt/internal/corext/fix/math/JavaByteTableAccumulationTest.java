/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.model.UlrExpression;

import de.regelsuche.ast.iteration.TableXorReduction.Axis;
import de.regelsuche.ast.iteration.TableXorReduction.Storage;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class JavaByteTableAccumulationTest {
    private static final String SOURCE = """
        class Sample {
          static byte[][] mutable;
          void apply(byte[][] c, byte[][] x, byte[][] y, int m, int n, int from, int size) {
            byte[][] cells = mutable;
            for (int j = 0; j < m; j++) {
              byte[] out = y[j];
              byte[] co = c[j];
              for (int q = from; q < from + size; ++q) {
                int sum = 0;
                for (int k = 0; k < n; ++k) {
                  byte[] data = x[k];
                  byte[] row = cells[co[k] & 255];
                  sum ^= row[data[q] & 0xff];
                }
                out[q] = (byte) sum;
              }
            }
          }
        }
        """;
    private static final MathCleanUpOptions OPTIONS = new MathCleanUpOptions(true,
            EnumSet.of(NumericKind.BYTE, NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
            OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());

    @Test void normalAnalysisReturnsTypedFullRegionAndExactAccessOrder() {
        var ast = JavaArrayStoreTest.parse(SOURCE);
        var result = MathematicalAnalysis.analyze(ast, SOURCE, OPTIONS, new NullProgressMonitor(), -1, 0);
        assertEquals(1, result.byteTableAccumulations().size(), result.diagnostics().toString());
        var region = result.byteTableAccumulations().getFirst();
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("BYTE_TABLE_XOR_REDUCTION")
                && d.offset() == region.sourceStart() && d.length() == region.sourceLength()));
        assertFalse(region.editAuthorized());
        assertEquals("UNVERIFIED_TABLE", region.fieldClassification());
        assertEquals(JavaByteTableAccumulation.Aliasing.MAY_ALIAS, region.aliasing());
        assertEquals(JavaByteTableAccumulation.Element.BYTE_ROW, region.accessSchedule().getFirst().element());
        assertEquals(JavaByteTableAccumulation.Element.BYTE, region.accessSchedule().getLast().element());
        assertEquals(result.sourceDigest(), region.sourceSha256());
        assertEquals(SOURCE.indexOf("for (int j"), region.sourceStart());
        assertTrue(SOURCE.substring(region.sourceStart(), region.sourceStart() + region.sourceLength()).endsWith("\n    }"));
        assertEquals(List.of(Axis.OUTPUT, Axis.POSITION, Axis.INPUT), region.loopOrder());
        assertEquals(Storage.ZERO_XOR_REDUCE_THEN_STORE, region.storage());
        assertEquals(4, region.arrayBindings().size());
        var end = assertInstanceOf(UlrExpression.Binary.class, region.loops().get(1).endExclusive());
        assertEquals(UlrExpression.Overflow.WRAP, end.overflow());
        assertEquals(List.of("y[j]", "c[j]", "x[k]", "co[k]", "cells[co[k] & 255]", "data[q]", "row[data[q] & 0xff]", "out[q]"),
                region.accessSchedule().stream().map(e -> SOURCE.substring(e.sourceStart(), e.sourceStart() + e.sourceLength())).toList());
        assertEquals(List.of(1, 1, 3, 3, 3, 3, 3, 2), region.accessSchedule().stream().map(JavaByteTableAccumulation.Access::loopDepth).toList());
        assertEquals(1, region.accessSchedule().stream().filter(e -> e.kind() == JavaByteTableAccumulation.AccessKind.ARRAY_WRITE).count());
    }

    @Test void independentlyReplaysGuardTailIndexBindingsAndEveryOriginalRead() {
        var before = JavaArrayStoreTest.parse(SOURCE);
        var result = MathematicalAnalysis.analyze(before, SOURCE, OPTIONS, new NullProgressMonitor(), -1, 0);
        assertTrue(result.evidence().stream().noneMatch(r -> r.orderedInputPrefix() != null));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("ORDERED_INPUT_PREFIX_AVAILABLE")));
        var explicit = new java.util.HashMap<>(OPTIONS.toMap());
        explicit.put(MathCleanUpOptions.ORDERED_INPUT_PREFIX, "true");
        var requested = MathematicalAnalysis.analyze(before, SOURCE, MathCleanUpOptions.parse(explicit, 17), null, -1, 0);
        var receipt = requested.evidence().stream().filter(r -> r.orderedInputPrefix() != null).findFirst().orElseThrow();
        String emitted = receipt.replacement();
        for (var change : List.of(
                List.of("if (n >= 4)", "if (n >= 3)"),
                List.of("n >= 4 ? 4 : 0", "n >= 4 ? 3 : 0"),
                List.of("n >= 4 ? 4 : 0", "m >= 4 ? 4 : 0"),
                List.of("x[1]", "x[2]"),
                List.of("co[2]", "co[3]"),
                List.of("data[q]", "data[j]"),
                List.of("sum ^=", "sum |="))) {
            String changed = emitted.replace(change.getFirst(), change.getLast());
            assertNotEquals(emitted, changed);
            String source = SOURCE.substring(0, receipt.offset()) + changed + SOURCE.substring(receipt.offset() + receipt.length());
            var after = JavaArrayStoreTest.parse(source);
            var work = new JavaValueSourceSummary.Work(100_000, OPTIONS, () -> false);
            assertThrows(IllegalArgumentException.class, () -> JavaOrderedInputPrefix.replay(before, after,
                    receipt.orderedInputPrefix(), receipt.offset(), changed.length(), work), change.toString());
        }
    }

    @Test void annotatedBodyAndNonlocalBoundKeepTheirOriginalSource() {
        for (var source : List.of(SOURCE.replace("byte[] data =", "/* required source attribution */ byte[] data ="),
                SOURCE.replace("k < n", "k < n + 1"))) {
            var explicit = new java.util.HashMap<>(OPTIONS.toMap());
            explicit.put(MathCleanUpOptions.ORDERED_INPUT_PREFIX, "true");
            var analysis = MathematicalAnalysis.analyze(JavaArrayStoreTest.parse(source), source, MathCleanUpOptions.parse(explicit, 17), null, -1, 0);
            assertTrue(analysis.evidence().stream().noneMatch(r -> r.orderedInputPrefix() != null));
        }
    }

    @Test void staleSelectionsProtectionAndNumericKindsGateRecognition() {
        var ast = JavaArrayStoreTest.parse(SOURCE);
        var stale = MathematicalAnalysis.analyze(ast, SOURCE.replace("0xff", "0xfe"), OPTIONS, null, -1, 0);
        assertTrue(stale.byteTableAccumulations().isEmpty());
        int target = SOURCE.indexOf("sum ^=");
        assertTrue(MathematicalAnalysis.analyze(ast, SOURCE, OPTIONS, null, -1, 0, null,
                List.of(new SourceRange(target, 1))).byteTableAccumulations().isEmpty());
        assertTrue(MathematicalAnalysis.analyze(ast, SOURCE, OPTIONS, null, SOURCE.indexOf("void apply"), 1).byteTableAccumulations().isEmpty());
        assertTrue(MathematicalAnalysis.analyze(ast, SOURCE, JavaArrayStoreTest.options(), null, -1, 0).byteTableAccumulations().isEmpty());
    }

    @Test void relatedSyntaxCannotHideDifferentIndexingValuesOrObservableEffects() {
        for (var change : List.of(
                List.of("sum ^=", "sum +="), List.of("sum = 0", "sum = 1"), List.of("k = 0", "k = 1"),
                List.of("x[k]", "x[j]"), List.of("co[k]", "co[q]"), List.of("data[q]", "data[k]"),
                List.of("0xff", "0x7f"), List.of("cells[co", "mutable[co"),
                List.of("byte[] data = x[k];", "byte[] extra = c[k]; byte[] data = x[k];"),
                List.of("int sum = 0;", "int sum = 0; x[0] = out;"),
                List.of("k < n", "k <= n"), List.of("k < n", "k < n--"),
                List.of("out[q] = (byte) sum", "out[q] ^= (byte) sum"),
                List.of("out[q] = (byte) sum", "out[q] = (byte) (sum + 1)"))) {
            String source = SOURCE.replace(change.get(0), change.get(1));
            assertNotEquals(SOURCE, source);
            var ast = JavaArrayStoreTest.parse(source);
            var candidates = new JavaByteTableAccumulation.Candidates(OPTIONS);
            ast.accept(new ASTVisitor() {
                @Override public boolean visit(VariableDeclarationFragment declaration) {
                    candidates.consider(declaration);
                    return true;
                }
            });
            var work = new JavaValueSourceSummary.Work(OPTIONS.workBudget(), OPTIONS, () -> false);
            assertTrue(candidates.find(ast, MathematicalAnalysis.digest(source), -1, 0, List.of(), work).isEmpty(), change.toString());
        }
    }
}
