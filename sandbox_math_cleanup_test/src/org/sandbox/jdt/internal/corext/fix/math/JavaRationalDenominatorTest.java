/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.ToolProvider;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class JavaRationalDenominatorTest {
    @TempDir Path temporary;

    private static final String SOURCE = """
        import java.math.BigInteger;
        class RationalMap {
            private static final BigInteger[] X = { BigInteger.valueOf(45), BigInteger.valueOf(33) };
            private static final BigInteger[] Y = { BigInteger.valueOf(86), BigInteger.valueOf(34), BigInteger.valueOf(100) };
            private static final BigInteger[] N = { BigInteger.ONE, BigInteger.TWO };
            private final BigInteger modulus;
            RationalMap(BigInteger modulus) { this.modulus = modulus; }
            BigInteger[] map(BigInteger value, BigInteger weight) {
                BigInteger nx = horner(N, N.length - 1, value);
                BigInteger dx = horner(X, X.length - 1, value).add(power(value, X.length)).mod(this.modulus);
                BigInteger ny = horner(N, N.length - 1, value);
                BigInteger dy = horner(Y, Y.length - 1, value).add(power(value, Y.length)).mod(this.modulus);
                BigInteger rx = nx.multiply(dx.modInverse(this.modulus)).mod(this.modulus);
                BigInteger ry = weight.multiply(ny).mod(this.modulus).multiply(dy.modInverse(this.modulus)).mod(this.modulus);
                return new BigInteger[] { rx, ry };
            }
            private BigInteger horner(BigInteger[] coefficients, int top, BigInteger value) {
                BigInteger total = coefficients[top];
                for (int i = top - 1; i >= 0; --i) {
                    total = total.multiply(value).add(coefficients[i]).mod(this.modulus);
                }
                return total;
            }
            private BigInteger power(BigInteger base, int exponent) {
                return base.modPow(BigInteger.valueOf(exponent), this.modulus);
            }
        }
        """;

    @Test void discoversTheDenominatorAndKeepsTheOriginalForOtherModuli() throws Exception {
        String generated = apply(SOURCE, true);
        assertTrue(generated.contains("mathDenominatorInverse"), generated);
        assertTrue(generated.contains("dx.modInverse(this.modulus)"), generated);
        assertTrue(generated.contains("dy.modInverse(this.modulus)"), generated);
        var compiler = StrictCompilerOptions.allWarnings(17);
        for (String source : List.of(SOURCE, generated)) {
            var problems = parse(source, compiler).getProblems();
            assertEquals(1, problems.length, Arrays.toString(problems));
            assertEquals(IProblem.MissingNonNullByDefaultAnnotationOnType, problems[0].getID());
            assertTrue(problems[0].isWarning());
        }
        assertEquals(execute(SOURCE, "map"), execute(generated, "map"));
        assertEquals(generated, apply(generated, true));
    }

    @Test void discoversTheDenominatorWithImplicitThisInHelpers() throws Exception {
        String original = SOURCE.replace("add(coefficients[i]).mod(this.modulus)", "add(coefficients[i]).mod(modulus)")
            .replace("modPow(BigInteger.valueOf(exponent), this.modulus)", "modPow(BigInteger.valueOf(exponent), modulus)");
        String generated = apply(original, true);
        assertTrue(generated.contains("mathDenominatorInverse"), generated);
        assertEquals(execute(original, "map"), execute(generated, "map"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "modPow(BigInteger.valueOf(exponent), ", "dx.modInverse(", "dy.modInverse(" })
    void preservesNullReceiverWhenReadingTheSameFieldOnAnotherInstance(String access) throws Exception {
        String original = SOURCE.replace("private final BigInteger modulus;",
                "private static final RationalMap PEER = null; private final BigInteger modulus;")
            .replace(access + "this.modulus)", access + "RationalMap.PEER.modulus)");
        String generated = apply(original, true);
        // Modulus 101, input 1, and weight 2 take the fast path for a valid mapping.
        assertEquals(NullPointerException.class.getName(), execute(original, "map").get(1));
        assertEquals(NullPointerException.class.getName(), execute(generated, "map").get(1));
        assertFalse(generated.contains("mathDenominatorInverse"), generated);
    }

    @Test void keepsEarlyClassInitializationFailure() throws Exception {
        String duringInitialization = SOURCE.replace("private static final BigInteger[] N = { BigInteger.ONE, BigInteger.TWO };", "")
            .replace("private static final BigInteger[] X", """
                private static final BigInteger[] N = { BigInteger.ONE, BigInteger.TWO };
                static { new RationalMap(BigInteger.valueOf(101)).map(BigInteger.ONE, BigInteger.ONE); }
                private static final BigInteger[] X""");
        String generated = apply(duringInitialization, true);
        assertTrue(generated.contains("mathDenominatorInverse"), generated);
        assertEquals(execute(duringInitialization, "map"), execute(generated, "map"));
    }

    @Test void combinesTwoVerifiedMethodsInOneOwner() throws Exception {
        int helper = SOURCE.indexOf("private BigInteger horner");
        String second = SOURCE.substring(SOURCE.indexOf("BigInteger[] map"), helper)
            .replace("BigInteger[] map(", "BigInteger[] mapAgain(");
        String twoMethods = SOURCE.substring(0, helper) + second + SOURCE.substring(helper);
        String generated = apply(twoMethods, true);
        assertTrue(generated.contains("mathDenominatorCoefficients_"), generated);
        assertEquals(execute(twoMethods, "mapAgain"), execute(generated, "mapAgain"));
    }

    private List<String> execute(String source, String name) throws Exception {
        Path folder = Files.createTempDirectory(temporary, "rationals");
        Path javaSource = Files.writeString(folder.resolve("RationalMap.java"), source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-d", folder.toString(), javaSource.toString()));
        try (var loader = new URLClassLoader(new URL[] { folder.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
            Class<?> type;
            try {
                type = Class.forName("RationalMap", true, loader);
            } catch (ExceptionInInitializerError failure) {
                return List.of("initialization:" + failure.getCause().getClass().getName());
            }
            var constructor = type.getDeclaredConstructor(BigInteger.class);
            constructor.setAccessible(true);
            var method = type.getDeclaredMethod(name, BigInteger.class, BigInteger.class);
            method.setAccessible(true);
            var outcomes = new ArrayList<String>();
            for (int p : new int[] { 101, 103, 100, 0, -101 }) {
                Object receiver = constructor.newInstance(BigInteger.valueOf(p));
                for (BigInteger input : new BigInteger[] { BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(34),
                        BigInteger.valueOf(-67), BigInteger.ONE.shiftLeft(128), new AlternateInteger(), null }) {
                    try {
                        outcomes.add(Arrays.toString((BigInteger[])method.invoke(receiver, input, BigInteger.TWO)));
                    } catch (InvocationTargetException failure) {
                        var cause = failure.getCause();
                        outcomes.add(cause.getClass().getName() + (cause instanceof ArithmeticException ? cause.getMessage() : ""));
                    }
                }
            }
            return List.copyOf(outcomes);
        }
    }

    private static final class AlternateInteger extends BigInteger {
        private static final long serialVersionUID = 1L;
        AlternateInteger() { super("5"); }
        @Override public BigInteger modPow(BigInteger exponent, BigInteger modulus) {
            return super.modPow(exponent, modulus).add(ONE).mod(modulus);
        }
    }

    @Test void rejectsAChangedTableOrAnUnauditedTableUse() throws Exception {
        String modified = SOURCE.replace("valueOf(86)", "valueOf(87)");
        assertFalse(apply(modified, true).contains("mathDenominatorInverse"));
        String escaping = SOURCE.replace("private final BigInteger modulus;",
            "static BigInteger[] table() { return X; } private final BigInteger modulus;");
        assertFalse(apply(escaping, true).contains("mathDenominatorInverse"));
        assertFalse(apply(SOURCE, false).contains("mathDenominatorInverse"));
    }

    private static CompilationUnit parse(String source, Map<String, String> compiler) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setUnitName("RationalMap.java");
        parser.setEnvironment(new String[0], new String[0], new String[0], true);
        parser.setResolveBindings(true);
        parser.setCompilerOptions(compiler);
        return (CompilationUnit)parser.createAST(null);
    }

    private static String apply(String source, boolean optIn) throws Exception {
        var compiler = StrictCompilerOptions.allWarnings(17);
        var ast = parse(source, compiler);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(IProblem::isError), () -> Arrays.toString(ast.getProblems()));
        var base = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
            OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 12_000_000, 20_000, false, 17, List.of(), optIn);
        var profile = new HashMap<>(base.toMap());
        profile.put(MathCleanUpOptions.TIMEOUT_SECONDS, "20");
        var options = MathCleanUpOptions.parse(profile, 17);
        var environment = new MathematicalAnalysis.SourceEnvironment("RationalMap.java", List.of(), List.of(), List.of(), compiler);
        var analysis = MathematicalAnalysis.analyze(ast, source, options, null, -1, 0, environment);
        var edit = analysis.newEdit();
        var provenance = GeneratedSourceDiagnostics.edits(edit);
        var document = new Document(source);
        edit.apply(document);
        String generated = document.get();
        GeneratedSourceDiagnostics.verify(ast, source, parse(generated, compiler), generated, provenance, null);
        return generated;
    }
}
