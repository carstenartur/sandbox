/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import de.regelsuche.ast.iteration.TableXorReduction;
import de.regelsuche.ast.iteration.TableXorReduction.Axis;
import de.regelsuche.ast.iteration.TableXorReduction.Storage;
import de.regelsuche.sdk.optimization.iteration.TableXorReductionSdk;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Observe each registered implementation against its own compiled Apply result. */
final class ReedSolomonRegionSemantics {
    private static final int OFFSET = 3;
    private static final int COUNT = 13;

    private enum Shape { DISJOINT, EMPTY, REPEATED_OUTPUT, INPUT_OUTPUT, COEFFICIENT_OUTPUT, SHORT_INPUT, NULL_INPUT }

    private record Buffers(byte[][] matrix, byte[][] inputs, byte[][] outputs) {
        static Buffers create(Shape shape) {
            var random = new Random(503846);
            var buffers = new Buffers(new byte[2][22], new byte[4][22], new byte[2][22]);
            for (byte[][] rows : new byte[][][]{buffers.matrix, buffers.inputs, buffers.outputs}) {
                for (byte[] row : rows) random.nextBytes(row);
            }
            // Ensure a single known table mutation is observed once in the first output element.
            buffers.matrix[0][0] = 1;
            buffers.inputs[0][OFFSET] = 1;
            buffers.inputs[0][OFFSET + 1] = OFFSET;
            for (int i = 1; i < buffers.inputs.length; i++) buffers.matrix[0][i] = (byte) (200 + i);
            switch (shape) {
                case REPEATED_OUTPUT -> buffers.outputs[1] = buffers.outputs[0];
                case INPUT_OUTPUT -> buffers.outputs[0] = buffers.inputs[2];
                case COEFFICIENT_OUTPUT -> buffers.outputs[0] = buffers.matrix[1];
                case SHORT_INPUT -> buffers.inputs[3] = Arrays.copyOf(buffers.inputs[3], 7);
                case NULL_INPUT -> buffers.inputs[3] = null;
                default -> { }
            }
            return buffers;
        }

        void assertMatches(Buffers actual, String where) {
            assertTrue(Arrays.deepEquals(matrix, actual.matrix), where + " coefficient rows");
            assertTrue(Arrays.deepEquals(inputs, actual.inputs), where + " input rows");
            assertTrue(Arrays.deepEquals(outputs, actual.outputs), where + " output rows");
        }
    }

    static void compare(Object original, Object generated) throws ReflectiveOperationException {
        String loop = original.getClass().getName();
        assertEquals(loop, generated.getClass().getName());
        for (Shape shape : Shape.values()) {
            String where = loop + " " + shape;
            int count = shape == Shape.EMPTY ? 0 : COUNT;
            var before = Buffers.create(shape);
            var after = Buffers.create(shape);
            Class<?> expectedError = switch (shape) {
                case SHORT_INPUT -> ArrayIndexOutOfBoundsException.class;
                case NULL_INPUT -> NullPointerException.class;
                default -> null;
            };
            assertEquals(expectedError, invoke(original, before, count), where + " original exception");
            assertEquals(expectedError, invoke(generated, after, count), where + " Apply exception");
            before.assertMatches(after, where);
            if (shape == Shape.SHORT_INPUT) {
                assertFalse(Arrays.deepEquals(Buffers.create(shape).outputs, before.outputs),
                        where + " must exercise writes preceding an exception");
            }
            if (shape == Shape.DISJOINT || shape == Shape.EMPTY) {
                var independent = Buffers.create(shape);
                for (int o = 0; o < independent.outputs.length; o++) {
                    for (int pos = OFFSET; pos < OFFSET + count; pos++) {
                        int sum = 0;
                        for (int i = 0; i < independent.inputs.length; i++) {
                            sum ^= product(independent.matrix[o][i] & 255, independent.inputs[i][pos] & 255);
                        }
                        independent.outputs[o][pos] = (byte) sum;
                    }
                }
                independent.assertMatches(before, where + " polynomial oracle");
            }
        }
        // Some upstream orders initialize from input 0 before looping over the remaining inputs.
        for (int inputCount : new int[]{Integer.MIN_VALUE, 0, 1, 3, 5}) {
            var before = Buffers.create(Shape.DISJOINT);
            var after = Buffers.create(Shape.DISJOINT);
            String where = loop + " inputCount=" + inputCount;
            Class<?> expectedError = inputCount > before.inputs.length ? ArrayIndexOutOfBoundsException.class : null;
            assertEquals(expectedError, invoke(original, before, inputCount, COUNT), where);
            assertEquals(expectedError, invoke(generated, after, inputCount, COUNT), where);
            before.assertMatches(after, where);
        }
        compareTableRowAlias(original, generated);
    }

    private static void compareTableRowAlias(Object original, Object generated) throws ReflectiveOperationException {
        var originalTable = multiplicationTable(original);
        var generatedTable = multiplicationTable(generated);
        byte[] savedOriginal = originalTable[1].clone();
        byte[] savedGenerated = generatedTable[1].clone();
        try {
            var before = Buffers.create(Shape.DISJOINT);
            var after = Buffers.create(Shape.DISJOINT);
            before.outputs[0] = originalTable[1];
            after.outputs[0] = generatedTable[1];
            assertNull(invoke(original, before, COUNT));
            assertNull(invoke(generated, after, COUNT));
            before.assertMatches(after, original.getClass().getName() + " table/output alias");
        } finally {
            System.arraycopy(savedOriginal, 0, originalTable[1], 0, savedOriginal.length);
            System.arraycopy(savedGenerated, 0, generatedTable[1], 0, savedGenerated.length);
        }
    }

    static Optional<TableXorReduction> readReduction(JsonNode file, Path path) throws IOException {
        var regions = file.required("byteTableAccumulations");
        var expectedOrder = switch (path.getFileName().toString()) {
            case "ByteOutputInputTableCodingLoop.java" -> List.of(Axis.POSITION, Axis.OUTPUT, Axis.INPUT);
            case "OutputByteInputTableCodingLoop.java" -> List.of(Axis.OUTPUT, Axis.POSITION, Axis.INPUT);
            default -> List.<Axis>of();
        };
        assertEquals(expectedOrder.isEmpty() ? 0 : 1, regions.size(), path.toString());
        if (expectedOrder.isEmpty()) return Optional.empty();
        var region = regions.get(0);
        var order = new ArrayList<Axis>();
        region.required("loopOrder").forEach(axis -> order.add(Axis.valueOf(axis.asText())));
        assertEquals(expectedOrder, order);
        var storage = Storage.valueOf(region.required("storage").asText());
        assertEquals(Storage.ZERO_XOR_REDUCE_THEN_STORE, storage);
        assertEquals("MAY_ALIAS", region.required("aliasing").asText());
        assertEquals("UNVERIFIED_TABLE", region.required("fieldClassification").asText());
        assertFalse(region.required("editAuthorized").asBoolean());
        assertEquals(CorpusFiles.sha256(path), region.required("sourceSha256").asText());
        var source = Files.readString(path);
        int start = region.required("sourceStart").asInt();
        int end = start + region.required("sourceLength").asInt();
        String body = source.substring(start, end);
        assertTrue(start > source.indexOf("void codeSomeShards") && body.contains("value ^=")
                && body.contains("= (byte) value"), path.toString());
        var loops = region.required("loops");
        assertEquals(3, loops.size());
        int parentStart = start, parentEnd = end;
        for (var loop : loops) {
            int loopStart = loop.required("sourceStart").asInt();
            int loopEnd = loopStart + loop.required("sourceLength").asInt();
            assertTrue(loopStart >= parentStart && loopEnd <= parentEnd && source.startsWith("for (", loopStart));
            parentStart = loopStart;
            parentEnd = loopEnd;
        }
        var roles = Set.of("OUTPUT", "COEFFICIENT", "INPUT", "TABLE");
        for (String role : roles) assertFalse(region.required("arrayBindings").required(role).asText().isBlank());
        var accesses = region.required("accessSchedule");
        assertEquals(8, accesses.size());
        var readRoles = new HashSet<String>();
        for (var access : accesses) {
            var role = access.required("arrayRole").asText();
            assertTrue(roles.contains(role));
            int accessStart = access.required("sourceStart").asInt();
            int accessLength = access.required("sourceLength").asInt();
            assertTrue(accessStart >= start && accessLength > 0 && accessStart + accessLength <= end);
            int depth = access.required("loopDepth").asInt();
            assertTrue(depth >= 1 && depth <= order.size());
            if ("ARRAY_READ".equals(access.required("kind").asText())) readRoles.add(role);
        }
        assertTrue(readRoles.containsAll(Set.of("COEFFICIENT", "INPUT", "TABLE")));
        var store = accesses.get(accesses.size() - 1);
        assertEquals("ARRAY_WRITE", store.required("kind").asText());
        assertEquals("BYTE", store.required("element").asText());
        assertEquals("OUTPUT", store.required("arrayRole").asText());
        return Optional.of(TableXorReductionSdk.describe(order, storage));
    }

    static void checkTypedReduction(Object original, Object generated, TableXorReduction reduction)
            throws ReflectiveOperationException {
        var originalTable = multiplicationTable(original);
        var generatedTable = multiplicationTable(generated);
        for (var table : new byte[][][]{originalTable, generatedTable}) {
            var evidence = TableXorReductionSdk.verifyBinaryField(table).orElseThrow();
            assertEquals(0x11d, evidence.modulus());
            assertEquals(65_536, evidence.productsChecked());
        }
        var canonical = compareDisjointLookup(original, generated, reduction, originalTable);
        originalTable[1][1] ^= 1;
        generatedTable[1][1] ^= 1;
        try {
            assertTrue(TableXorReductionSdk.verifyBinaryField(originalTable).isEmpty());
            assertTrue(TableXorReductionSdk.verifyBinaryField(generatedTable).isEmpty());
            var mutated = compareDisjointLookup(original, generated, reduction, originalTable);
            assertEquals(1, canonical.outputs[0][OFFSET] ^ mutated.outputs[0][OFFSET]);
        } finally {
            originalTable[1][1] ^= 1;
            generatedTable[1][1] ^= 1;
        }
    }

    private static Buffers compareDisjointLookup(Object original, Object generated, TableXorReduction reduction,
            byte[][] table) throws ReflectiveOperationException {
        var expected = Buffers.create(Shape.DISJOINT);
        byte[][] window = TableXorReductionSdk.evaluate(reduction, table, expected.matrix, expected.inputs,
                expected.inputs.length, expected.outputs.length, OFFSET, COUNT);
        assertEquals(expected.outputs.length, window.length);
        for (int o = 0; o < expected.outputs.length; o++) {
            assertEquals(COUNT, window[o].length);
            System.arraycopy(window[o], 0, expected.outputs[o], OFFSET, COUNT);
        }
        for (Object loop : List.of(original, generated)) {
            var actual = Buffers.create(Shape.DISJOINT);
            assertNull(invoke(loop, actual, COUNT));
            expected.assertMatches(actual, loop.getClass().getName() + " table oracle");
        }
        return expected;
    }

    private static byte[][] multiplicationTable(Object loop) throws ReflectiveOperationException {
        return (byte[][]) loop.getClass().getClassLoader().loadClass("com.backblaze.erasure.Galois")
                .getField("MULTIPLICATION_TABLE").get(null);
    }

    /** Polynomial multiplication over GF(2) with reduction modulo x^8 + x^4 + x^3 + x^2 + 1. */
    private static int product(int left, int right) {
        int result = 0;
        while (right != 0) {
            if ((right & 1) != 0) result ^= left;
            right >>>= 1;
            left <<= 1;
            if ((left & 256) != 0) left ^= 0x11d;
        }
        return result;
    }

    private static Class<?> invoke(Object loop, Buffers buffers, int count) throws ReflectiveOperationException {
        return invoke(loop, buffers, buffers.inputs.length, count);
    }

    private static Class<?> invoke(Object loop, Buffers buffers, int inputCount, int count)
            throws ReflectiveOperationException {
        var method = loop.getClass().getMethod("codeSomeShards", byte[][].class, byte[][].class, int.class,
                byte[][].class, int.class, int.class, int.class);
        try {
            method.invoke(loop, buffers.matrix, buffers.inputs, inputCount, buffers.outputs,
                    buffers.outputs.length, OFFSET, count);
            return null;
        } catch (InvocationTargetException failure) {
            return failure.getCause().getClass();
        }
    }

    private ReedSolomonRegionSemantics() { }
}
