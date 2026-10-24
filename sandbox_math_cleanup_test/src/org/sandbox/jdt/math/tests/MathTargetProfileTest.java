/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.tools.ToolProvider;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Target source/API compatibility, executed on the Java-25 host, not eighteen different JVMs. */
@SuppressWarnings("nls")
class MathTargetProfileTest {
    @TempDir Path temporary;

    @Test void parserHonorsTargetSyntaxInsteadOfAlwaysUsingJava17() {
        String source = "public class Calculation { record Pair(int value) {} }";
        assertThrows(AssertionError.class, () -> MathTestSupport.parse(source, 8));
        assertDoesNotThrow(() -> MathTestSupport.parse(source, 17));
    }

    @ParameterizedTest(name = "Java {0}: all numeric kinds and safety profiles")
    @ValueSource(ints = {8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25})
    void provesEmitsAndExecutesEveryAdvertisedTarget(int target) throws Exception {
        List<Row> rows = new ArrayList<>();
        for (NumericKind kind : NumericKind.values()) {
            for (SafetyProfile safety : SafetyProfile.values()) {
                String source = source(kind, safety);
                var options = options(kind, safety, target);
                var analysis = analyze(source, options);
                boolean rejected = kind.floatingPoint() && target < 17;
                if (rejected) {
                    assertFalse(analysis.changed(), source);
                    assertTrue(analysis.evidence().isEmpty(), source);
                    assertTrue(analysis.diagnostics().stream().anyMatch(d ->
                            d.message().contains("PRE_JAVA_17_FLOATING_POINT")), analysis.diagnostics().toString());
                } else {
                    assertTrue(analysis.changed(), kind + "/" + safety + ": " + analysis.diagnostics());
                    assertFalse(analysis.evidence().isEmpty(), source);
                }
                Document document = new Document(source);
                var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
                String generated = document.get();
                undo.apply(document);
                assertEquals(source, document.get(), "Byte-exact undo");
                rows.add(new Row(kind, safety, source, generated, rejected));
            }
        }
        // Two compiler invocations per target, not one invocation per value or Cartesian cell.
        try (var original = compile(rows, false, target); var generated = compile(rows, true, target)) {
            for (Row row : rows) {
                String name = packageName(row.kind(), row.safety()) + ".Calculation";
                Method before = original.loadClass(name).getMethod("compute", parameter(row.kind()));
                Method after = generated.loadClass(name).getMethod("compute", parameter(row.kind()));
                for (Input input : inputs(row.kind(), row.safety())) {
                    assertEquals(input.original(), before.invoke(null, input.value()), name + " original");
                    String expectedError = row.rejected() ? null : input.checkedError();
                    if (row.safety() == SafetyProfile.CHECKED_THROW && expectedError != null) {
                        Throwable cause = assertThrows(InvocationTargetException.class,
                                () -> after.invoke(null, input.value()), name).getCause();
                        assertEquals(expectedError, assertInstanceOf(ArithmeticException.class, cause).getMessage(), name);
                    } else {
                        assertEquals(input.original(), after.invoke(null, input.value()), name + " generated");
                    }
                }
            }
        }
    }

    private URLClassLoader compile(List<Row> rows, boolean generated, int target) throws Exception {
        Path directory = Files.createDirectories(temporary.resolve(target + (generated ? "-after" : "-before")));
        List<String> arguments = new ArrayList<>(List.of("--release", Integer.toString(target),
                "-classpath", directory.toString(), "-d", directory.toString()));
        for (Row row : rows) {
            Path file = directory.resolve(packageName(row.kind(), row.safety()).replace('.', '/')).resolve("Calculation.java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, generated ? row.generated() : row.original());
            arguments.add(file.toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Qualification requires a JDK");
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)), "target=" + target);
        return new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null);
    }

    private static MathematicalAnalysis.Analysis analyze(String source, MathCleanUpOptions options) {
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source, options.targetJava()), source,
                options, new NullProgressMonitor(), -1, 0);
    }

    private static MathCleanUpOptions options(NumericKind kind, SafetyProfile safety, int target) {
        Set<NumericKind> kinds = switch (kind) {
            case BYTE, SHORT, CHAR -> Set.of(kind, NumericKind.INT);
            default -> Set.of(kind);
        };
        return new MathCleanUpOptions(true, kinds, safety, OptimizationGoal.READABILITY, 100_000, 2_000,
                safety == SafetyProfile.CHECKED_THROW, target, List.of());
    }

    private static String packageName(NumericKind kind, SafetyProfile safety) {
        return "matrix." + kind.name().toLowerCase(Locale.ROOT) + "_" + safety.name().toLowerCase(Locale.ROOT);
    }

    private static String source(NumericKind kind, SafetyProfile safety) {
        String type = kind.name().toLowerCase(Locale.ROOT);
        String method = switch (kind) {
            case BYTE, SHORT, CHAR -> "public static " + type + " compute(int x) { " + type
                    + " result=(" + type + ")((x+1)-1); return result; }";
            case INT, LONG -> {
                String one = kind == NumericKind.LONG ? "1L" : "1";
                String two = kind == NumericKind.LONG ? "2L" : "2";
                String expression = safety == SafetyProfile.PRESERVE_JAVA
                        ? "(x+" + one + ")-" + one : "(x*" + two + ")/" + two;
                yield "public static " + type + " compute(" + type + " x) { " + type
                        + " result=" + expression + "; return result; }";
            }
            case BIG_INTEGER -> "public static int compute(int x) { "
                    + "java.math.BigInteger input=java.math.BigInteger.valueOf(x); "
                    + "java.math.BigInteger result=input.add(java.math.BigInteger.ONE).subtract(java.math.BigInteger.ONE); "
                    + "return result.intValue(); }";
            case FLOAT, DOUBLE -> {
                String one = kind == NumericKind.FLOAT ? "1.0F" : "1.0D";
                String expression = safety == SafetyProfile.PRESERVE_JAVA ? "x*" + one : "(x+" + one + ")-x";
                String observer = kind == NumericKind.FLOAT ? "Float.floatToIntBits" : "Double.doubleToLongBits";
                yield "public static " + (kind == NumericKind.FLOAT ? "int" : "long") + " compute("
                        + type + " x) { " + type + " result=" + expression + "; return " + observer + "(result); }";
            }
        };
        return "package " + packageName(kind, safety) + "; public class Calculation { " + method + " }";
    }

    private static Class<?> parameter(NumericKind kind) {
        return switch (kind) {
            case LONG -> long.class;
            case FLOAT -> float.class;
            case DOUBLE -> double.class;
            default -> int.class;
        };
    }

    private static List<Input> inputs(NumericKind kind, SafetyProfile safety) {
        if (kind.floatingPoint()) return floatingInputs(kind, safety);
        List<Input> result = new ArrayList<>();
        if (kind == NumericKind.INT || kind == NumericKind.LONG) {
            long min = kind == NumericKind.INT ? Integer.MIN_VALUE : Long.MIN_VALUE;
            long max = kind == NumericKind.INT ? Integer.MAX_VALUE : Long.MAX_VALUE;
            long[] values = {0, 1, -1, min, max, min / 2, max / 2, min / 2 - 1, max / 2 + 1};
            long[] divisionResults = {0, 1, -1, 0, -1, min / 2, max / 2, max / 2, min / 2};
            for (int i = 0; i < values.length; i++) {
                long value = values[i];
                long expected = safety == SafetyProfile.PRESERVE_JAVA ? value : divisionResults[i];
                result.add(new Input(integralValue(kind, value), integralValue(kind, expected),
                        value < min / 2 || value > max / 2 ? "MATH_OVERFLOW" : null));
            }
            return result;
        }
        for (int value : new int[] {0, 1, -1, -129, -128, 127, 128, -32769, -32768, 32767,
                32768, 65535, 65536, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            Object expected = switch (kind) {
                case BYTE -> (byte) value;
                case SHORT -> (short) value;
                case CHAR -> (char) value;
                default -> value;
            };
            String error = null;
            if (kind != NumericKind.BIG_INTEGER) {
                int min = kind == NumericKind.BYTE ? Byte.MIN_VALUE : kind == NumericKind.SHORT ? Short.MIN_VALUE : 0;
                int max = kind == NumericKind.BYTE ? Byte.MAX_VALUE : kind == NumericKind.SHORT ? Short.MAX_VALUE : Character.MAX_VALUE;
                error = value == Integer.MAX_VALUE ? "MATH_OVERFLOW"
                        : value < min || value > max ? "MATH_NARROWING" : null;
            }
            result.add(new Input(value, expected, error));
        }
        return result;
    }

    private static Object integralValue(NumericKind kind, long value) {
        if (kind == NumericKind.INT) return (int) value;
        return value;
    }

    private static List<Input> floatingInputs(NumericKind kind, SafetyProfile safety) {
        List<Input> result = new ArrayList<>();
        Number[] values = kind == NumericKind.FLOAT
                ? new Number[] {0F, -0F, 1F, -1F, Float.MIN_VALUE, Float.MIN_NORMAL, 16_777_216F,
                        Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.intBitsToFloat(0x7fc00042)}
                : new Number[] {0D, -0D, 1D, -1D, Double.MIN_VALUE, Double.MIN_NORMAL, 1e16D,
                        Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.longBitsToDouble(0x7ff8000000000042L)};
        for (int i = 0; i < values.length; i++) {
            Number value = values[i];
            double expected = safety == SafetyProfile.PRESERVE_JAVA ? value.doubleValue() : i < 6 ? 1D : i < 8 ? 0D : Double.NaN;
            Object bits;
            if (kind == NumericKind.FLOAT) bits = Float.floatToIntBits((float) expected);
            else bits = Double.doubleToLongBits(expected);
            result.add(new Input(value, bits, i < 6 ? null : i < 8 ? "MATH_NUMERIC_MISMATCH" : "MATH_NON_FINITE"));
        }
        return result;
    }

    private record Input(Object value, Object original, String checkedError) {}
    private record Row(NumericKind kind, SafetyProfile safety, String original, String generated, boolean rejected) {}
}
