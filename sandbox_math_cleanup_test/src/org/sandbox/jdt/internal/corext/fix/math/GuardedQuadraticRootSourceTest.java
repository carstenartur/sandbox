/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;

class GuardedQuadraticRootSourceTest {
    static final String ORIGINAL = """
        import java.math.BigInteger;
        class Ocean {
            static final BigInteger Q = BigInteger.valueOf(19);
            static final BigInteger Slice = BigInteger.TWO.modInverse(Q);
            static final BigInteger Grade = Q.add(BigInteger.ONE).shiftRight(2);
            static long pack(BigInteger left, BigInteger right) {
                return left.longValueExact()*1000L + right.longValueExact();
            }
            static long recover(BigInteger amber, BigInteger cobalt, BigInteger grey) {
                BigInteger field = Q;
                if (cobalt.signum() == 0) {
                    return pack(amber, cobalt);
                }
                BigInteger seed = amber.add(grey).multiply(Slice).mod(field);
                BigInteger u = seed.modPow(Grade, field);
                if (!u.multiply(u).mod(field).equals(seed)) {
                    seed = amber.subtract(grey).multiply(Slice).mod(field);
                    u = seed.modPow(Grade, field);
                }
                BigInteger v = cobalt.multiply(u.shiftLeft(1).modInverse(field)).mod(field);
                return pack(u, v);
            }
        }
        """;

    static MathematicalAnalysis.Analysis analyze(String source, long budget) {
        var compiler = JavaCore.getOptions();
        JavaCore.setComplianceOptions("17", compiler);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setUnitName("Ocean.java");
        parser.setCompilerOptions(compiler);
        parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), () -> Arrays.toString(ast.getProblems()));
        var options = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                MathCleanUpOptions.WORK_BUDGET, Long.toString(budget), MathCleanUpOptions.MAX_STATES, "20000",
                MathCleanUpOptions.TIMEOUT_SECONDS, "30"), 17);
        return MathematicalAnalysis.analyze(ast, source, options, null, source.indexOf("seed ="), 1,
                new MathematicalAnalysis.SourceEnvironment("Ocean.java", List.of(), List.of(), List.of(), compiler));
    }

    @Test void discoversTheFieldProgramFromRenamedJava() throws Exception {
        var result = analyze(ORIGINAL, 100_000_000);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_QUADRATIC_ROOT")),
                () -> result.diagnostics().toString());
        var document = new Document(ORIGINAL);
        result.newEdit().apply(document);
        String generated = document.get();
        assertTrue(generated.contains(ORIGINAL.substring(ORIGINAL.indexOf("BigInteger seed"))));
        assertFalse(analyze(generated, 100_000_000).diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_QUADRATIC_ROOT")));
    }

    @Test void rejectsMutatedArithmeticAndObservableCoordinates() {
        for (String mutated : List.of(ORIGINAL.replace("shiftLeft(1)", "shiftLeft(2)"),
                ORIGINAL.replace("!u.multiply", "u.multiply"), ORIGINAL.replace("subtract(grey)", "add(grey)"),
                ORIGINAL.replace("shiftRight(2)", "shiftRight(1)"), ORIGINAL.replace("TWO.modInverse(Q)", "ONE.modInverse(Q)"),
                ORIGINAL.replace("return left.longValueExact()", "if (left == right) throw new Error(); return left.longValueExact()"),
                ORIGINAL.replace("seed = amber.subtract", "System.nanoTime(); seed = amber.subtract"))) {
            assertFalse(analyze(mutated, 100_000_000).diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_QUADRATIC_ROOT")));
        }
        assertFalse(analyze(ORIGINAL, 25).diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_QUADRATIC_ROOT")));
    }
}
