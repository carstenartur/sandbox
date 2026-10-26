/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class JavaSourceGuardRefinementTest {
    private static final String TARGET = "return x.modPow(BigInteger.TWO, p);";
    private static final String SOURCE = """
        import java.math.BigInteger;
        public class Sample {
          public static BigInteger f(BigInteger x, BigInteger p) {
            if (x != null && x.getClass() == BigInteger.class && x.bitLength() < 4096
                && p != null && p.getClass() == BigInteger.class && p.bitLength() < 4096 && p.signum() > 0) {
              return x.modPow(BigInteger.TWO, p);
            }
            return x.modPow(BigInteger.TWO, p);
          }
        }
        """;
    @TempDir Path temporary;

    @Test void removesOnlyReceiverGuardsEstablishedOutsideTheCompleteRegion() {
        var extraction = new JavaComputationExtractor().extractRuntime(parse(SOURCE), SOURCE, options(true));
        var selected = extraction.regions().stream().filter(r -> r.start() == SOURCE.indexOf(TARGET)).findFirst().orElseThrow();
        assertTrue(selected.receiverGuards().isEmpty(), selected.receiverGuards().toString());
        assertTrue(selected.assumptions().stream().anyMatch(a -> a.provenance().contains("dominating source guard")));
        var outside = extraction.regions().stream().filter(r -> r.start() == SOURCE.lastIndexOf(TARGET)).findFirst().orElseThrow();
        assertFalse(outside.receiverGuards().isEmpty(), "No source facts apply after the if statement");
    }
    @Test void unrelatedProofMetadataIsRetainedWithoutAnInputName() {
        var ast = parse(SOURCE);
        var selected = new JavaComputationExtractor().extractRuntime(ast, SOURCE, options(true)).regions().stream()
                .filter(r -> r.start() == SOURCE.indexOf(TARGET)).findFirst().orElseThrow();
        var unrelated = new de.regelsuche.sdk.optimization.SemanticAssumption(
                de.regelsuche.sdk.optimization.SemanticAssumption.Kind.NO_NAN_PAYLOAD_OBSERVATION,
                "region@source", "", "unrelated observation metadata");
        var assumptions = new java.util.HashSet<>(selected.assumptions()); assumptions.add(unrelated);
        var guarded = new JavaComputationRegion(selected.plan(), selected.trace(), selected.outputs(), selected.inputNames(),
                assumptions, selected.reservedNames(), java.util.Set.of("x", "p"), selected.start(), selected.length(),
                selected.internalBindings(), selected.arrayReads());
        var refined = JavaSourceGuardRefinement.refine(guarded, ast);
        assertTrue(refined.receiverGuards().isEmpty());
        assertTrue(refined.assumptions().contains(unrelated));
    }
    @Test void aMissingModulusSignProofKeepsItsFullGuard() {
        String source = SOURCE.replace(" && p.signum() > 0", "");
        var selected = new JavaComputationExtractor().extractRuntime(parse(source), source, options(true)).regions().stream()
                .filter(r -> r.start() == source.indexOf(TARGET)).findFirst().orElseThrow();
        assertEquals(java.util.Set.of("p"), selected.receiverGuards());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void optimizedOutputCompilesPreservesFallbackBehaviorAndSupportsExactUndo(boolean aliases) throws Exception {
        String source = aliases ? SOURCE.replace(TARGET, "return base.modPow(BigInteger.TWO, modulus);")
                .replace("if (x !=", "BigInteger base = x; BigInteger modulus = p; if (x !=") : SOURCE;
        String target = aliases ? "return base.modPow(BigInteger.TWO, modulus);" : TARGET;
        var analysis = MathematicalAnalysis.analyze(parse(source), source, options(true), new NullProgressMonitor(), source.indexOf(target), target.length());
        assertEquals(1, analysis.replacements().size(), analysis.diagnostics().toString());
        String replacement = analysis.replacements().getFirst().replacement();
        assertTrue(replacement.contains(".multiply("), replacement);
        assertFalse(replacement.contains("getClass()"), replacement);
        assertFalse(replacement.contains("if ("), replacement);
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get(); parse(generated);
        undo.apply(document); assertEquals(source, document.get());
        assertEquals(1, generated.split(java.util.regex.Pattern.quote(target), -1).length - 1,
                "The original calculation outside the dominating branch remains untouched");
        try (var original = compile(source, "original"); var optimized = compile(generated, "optimized")) {
            var before = original.loadClass("Sample").getMethod("f", BigInteger.class, BigInteger.class);
            var after = optimized.loadClass("Sample").getMethod("f", BigInteger.class, BigInteger.class);
            BigInteger[] values = {null, BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(-3), BigInteger.valueOf(27), BigInteger.ONE.shiftLeft(5000)};
            BigInteger[] moduli = {null, BigInteger.ZERO, BigInteger.valueOf(-1), BigInteger.valueOf(17)};
            for (var value : values) for (var modulus : moduli)
                assertEquals(outcome(before, value, modulus), outcome(after, value, modulus));
            BigInteger subclass = new BigInteger("3") {
                private static final long serialVersionUID = 1L;
                @Override public BigInteger modPow(BigInteger exponent, BigInteger modulus) { return BigInteger.valueOf(12345); }
            };
            assertEquals(BigInteger.valueOf(12345), outcome(after, subclass, BigInteger.valueOf(17)));
        }
    }
    @Test void strictModeDoesNotAcquireAnImplicitIdentityChangePermission() {
        var analysis = MathematicalAnalysis.analyze(parse(SOURCE), SOURCE, options(false), new NullProgressMonitor(), SOURCE.indexOf(TARGET), TARGET.length());
        assertFalse(analysis.changed(), "Source facts do not activate mathematical mode");
    }
    private static Object outcome(java.lang.reflect.Method method, BigInteger value, BigInteger modulus) throws Exception {
        try { return method.invoke(null, value, modulus); }
        catch (InvocationTargetException failure) {
            var cause = failure.getCause(); return cause.getClass().getName() + ":" + cause.getMessage();
        }
    }
    private URLClassLoader compile(String source, String name) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(name)), file = directory.resolve("Sample.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler, "Run under the required JDK");
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-d", directory.toString(), file.toString()));
        return new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, getClass().getClassLoader());
    }
    private static MathCleanUpOptions options(boolean mathematical) {
        return new MathCleanUpOptions(true, EnumSet.of(NumericKind.BIG_INTEGER, NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of(), mathematical);
    }
    private static CompilationUnit parse(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", options); parser.setCompilerOptions(options);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
}
