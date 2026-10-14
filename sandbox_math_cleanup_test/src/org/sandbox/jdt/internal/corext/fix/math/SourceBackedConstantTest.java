/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class SourceBackedConstantTest {
    @TempDir Path directory;
    private static final String SOURCE = """
        package sample;
        import java.math.BigInteger;
        public class Sample {
            private static final BigInteger P = Bridge.P;
            public static BigInteger calculate(byte digit) {
                byte[] magnitude = new byte[48];
                magnitude[47] = digit;
                BigInteger x = new BigInteger(1, magnitude);
                if (x.compareTo(P) >= 0) { throw new IllegalArgumentException("range"); }
                BigInteger result = x.multiply(x).mod(P).multiply(x).mod(P)
                    .add(BigInteger.valueOf(4)).mod(P);
                return result;
            }
        }
        """;
    private void sources(String constant) throws Exception {
        Files.createDirectories(directory.resolve("sample"));
        Files.writeString(directory.resolve("sample/Sample.java"), SOURCE);
        Files.writeString(directory.resolve("sample/Bridge.java"),
            "package sample; import java.math.BigInteger; public class Bridge { public static final BigInteger P = Constants.Q; }");
        Files.writeString(directory.resolve("sample/Constants.java"),
            "package sample; import java.math.BigInteger; public class Constants { public static final BigInteger Q = " + constant + "; }");
    }
    @Test void emitsMinimalModularRewriteFromThreeRealSourceUnits() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        var result = analyze(SOURCE);
        assertTrue(result.changed(), result.diagnostics().toString());
        String changed = result.replacements().getFirst().replacement();
        assertFalse(changed.contains("getClass()"), changed);
        assertFalse(changed.contains("bitLength()"), changed);
        assertFalse(changed.contains("if ("), changed);
        assertTrue(changed.contains("BigInteger.valueOf(4)"), changed);
        assertEquals(1, changed.split("\\.mod\\(P\\)", -1).length - 1, changed);
        var document = new Document(SOURCE);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        assertTrue(document.get().contains("x.compareTo(P)"));
        undo.apply(document); assertEquals(SOURCE, document.get());
    }
    @Test void changedDependencyInvalidatesAnAlreadyPreparedPatch() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        var result = analyze(SOURCE);
        assertTrue(result.changed(), result.diagnostics().toString());
        assertTrue(result.matches(SOURCE, Map.of()));
        Files.writeString(directory.resolve("sample/Constants.java"),
            "package sample; import java.math.BigInteger; public class Constants { public static final BigInteger Q = BigInteger.ZERO; }");
        assertFalse(result.matches(SOURCE, Map.of()), "External source changes must invalidate source-proved assumptions");
        assertThrows(IllegalArgumentException.class, result::newEdit);
    }
    @Test void incompleteDefinitionDoesNotPermitAnUnguardedRewrite() throws Exception {
        sources("make()");
        Files.writeString(directory.resolve("sample/Constants.java"),
            "package sample; import java.math.BigInteger; public class Constants { public static final BigInteger Q = make(); private static BigInteger make() { return BigInteger.TEN; } }");
        var result = analyze(SOURCE);
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")));
    }
    @Test void nullableDuringClassInitializationNeedsAnOriginalNonnullUse() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        String unguarded = SOURCE.replace("if (x.compareTo(P) >= 0) { throw new IllegalArgumentException(\"range\"); }", "");
        var result = analyze(unguarded);
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")));
    }
    @Test void retainsTheOriginalMultilineExpressionAndFactory() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        String source = SOURCE.replace("BigInteger calculate", "byte[] calculate")
            .replace("return result;", "return result.toByteArray();");
        var result = analyze(source);
        assertTrue(result.changed(), result.diagnostics().toString());
        assertEquals("BigInteger result = x.multiply(x).multiply(x)\n            .add(BigInteger.valueOf(4)).mod(P);",
            result.replacements().getFirst().replacement());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"BigInteger.ZERO", "new BigInteger(\"-1\")", "new BigInteger(\"bad\")", "null"})
    void rejectsNonpositiveInvalidAndNullableDefinitions(String constant) throws Exception {
        sources(constant);
        var result = analyze(SOURCE);
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")));
    }
    @Test void cyclicAliasesDoNotEstablishAValue() throws Exception {
        sources("Bridge.P");
        var result = analyze(SOURCE);
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")));
    }
    @Test void mutableAliasTargetsDoNotEstablishAValue() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        Path path=directory.resolve("sample/Constants.java");
        Files.writeString(path,Files.readString(path).replace("static final", "static"));
        var result=analyze(SOURCE);
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")));
    }
    @Test void fieldLosingFinalAfterCallerParsingCannotProveAConstant() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        String source = SOURCE.replace("Bridge.P", "Constants.Q");
        var roots = List.of(directory.toString());
        var ast = parse(source, roots);
        Path definition = directory.resolve("sample/Constants.java");
        Files.writeString(definition, Files.readString(definition).replace("static final", "static"));
        var result = analyze(ast, source, roots);
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")),
            "The fresh declaration is mutable even though the caller's old binding was final: " + result.replacements());
    }
    @Test void aShortCircuitRhsDoesNotProveNonnull() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        var result=analyze(SOURCE.replace("if (x.compareTo(P)", "if (digit > 0 && x.compareTo(P)"));
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")));
    }
    @Test void caughtNullDereferenceDoesNotProveNonnull() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        var result=analyze(SOURCE.replace("if (x.compareTo(P) >= 0) { throw new IllegalArgumentException(\"range\"); }",
            "try { P.signum(); } catch (NullPointerException absent) { }"));
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")));
    }
    @Test void methodNamedCompareToOnAnUnprovedReceiverDoesNotProveNonnull() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        String source=SOURCE.replace("calculate(byte digit)", "calculate(byte digit, BigInteger supplied)")
            .replace("BigInteger x = new BigInteger(1, magnitude);", "BigInteger x = supplied;");
        var result=analyze(source);
        assertTrue(!result.changed() || result.replacements().stream().allMatch(r -> r.replacement().contains("getClass()")));
    }
    @Test void removedDependencyInvalidatesThePreparedEdit() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        var result=analyze(SOURCE);assertTrue(result.changed());
        Files.delete(directory.resolve("sample/Constants.java"));
        assertFalse(result.matches(SOURCE,Map.of()));
        assertThrows(IllegalArgumentException.class,result::newEdit);
    }
    @Test void dependenciesAreBoundToTheirRealSources() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        var result=analyze(SOURCE);assertTrue(result.changed());
        var receipts=result.sourceDependencies().receipts();
        assertEquals(2,receipts.size());
        for(var receipt:receipts) assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(Path.of(receipt.identity())))),receipt.sha256());
    }
    @Test void newlyShadowingSourceInvalidatesThePreparedEdit() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        Path extra=Files.createDirectory(directory.resolve("other-root"));
        var result=analyze(SOURCE, List.of(extra.toString(), directory.toString()));
        assertTrue(result.changed(),result.diagnostics().toString());
        assertFalse(result.replacements().getFirst().replacement().contains("getClass()"));
        Files.createDirectories(extra.resolve("sample"));
        Files.copy(directory.resolve("sample/Constants.java"),extra.resolve("sample/Constants.java"));
        assertFalse(result.matches(SOURCE,Map.of()), "A newly competing definition changes source resolution even if its bytes match");
        assertThrows(IllegalArgumentException.class,result::newEdit);
    }
    @Test void aCommentInsideAnEliminatedExpressionIsNeverSilentlyDeleted() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        String source=SOURCE.replace("x.multiply(x).mod(P).multiply(x).mod(P)\n            .add(BigInteger.valueOf(4)).mod(P)",
            "x.add(/* input coordinate */ BigInteger.ZERO)");
        var result=analyze(source);
        assertTrue(!result.changed() || result.replacements().getFirst().replacement().contains("input coordinate"),
            "Rendering may decline a rewrite, but cannot remove its source comment: " + result.replacements());
    }
    @Test void retainedFactoryCommentsAndNewlinesRemainByteExact() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        String source=SOURCE.replace("BigInteger.valueOf(4)", "BigInteger.valueOf(/* curve coefficient */ 4)");
        var result=analyze(source);
        assertTrue(!result.changed() || result.replacements().getFirst().replacement().contains("BigInteger.valueOf(/* curve coefficient */ 4)"),
            "The source comment policy may decline this region, never strip its original comment");
    }
    @Test void deserializationCannotTurnSourceDependentEvidenceIntoATrustedEdit() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        var result=analyze(SOURCE);
        assertTrue(result.changed());
        // Isolate the source-context boundary from polymorphic expression JSON handling.
        var portable=new MathematicalAnalysis.Analysis(result.replacements(), List.of(), result.sourceDigest(),
            result.compilerOptions(), List.of(), result.documentation(), result.sourceDependencies());
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        String json=mapper.writeValueAsString(portable);
        var decoded=mapper.readValue(json,MathematicalAnalysis.Analysis.class);
        assertFalse(decoded.matches(SOURCE,Map.of()), "Stored receipts are not live source validation");
        assertThrows(IllegalArgumentException.class,decoded::newEdit);
    }
    @Test void historicalIndependentAnalysisStillRoundTripsWithoutSourceContext() throws Exception {
        var result=new MathematicalAnalysis.Analysis(List.of(),List.of(),"digest",Map.of());
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var decoded=mapper.readValue(mapper.writeValueAsString(result),MathematicalAnalysis.Analysis.class);
        assertEquals(result,decoded);
        assertDoesNotThrow(decoded::newEdit);
    }
    @Test void retainedSourceCheckpointDoesNotKeepTheParsingSessionOrItsAstsAlive() throws Exception {
        sources("new BigInteger(\"1fffffff\", 16)");
        var result=analyze(SOURCE);assertTrue(result.changed());
        var dependencies=JavaSourceContext.Checkpoint.class.getDeclaredField("dependencies");dependencies.setAccessible(true);
        for(Object dependency:(List<?>)dependencies.get(result.sourceDependencies())) {
            var read=dependency.getClass().getDeclaredField("read");read.setAccessible(true);Object reader=read.get(dependency);
            for(var capture:reader.getClass().getDeclaredFields()) {
                capture.setAccessible(true);Object captured=capture.get(reader);
                assertFalse(captured instanceof JavaSourceContext || captured instanceof org.eclipse.jdt.core.dom.ASTNode,
                    "A retained validation callback must not retain the request AST context");
            }
        }
    }
    private MathematicalAnalysis.Analysis analyze(String source) {
        return analyze(source,List.of(directory.toString()));
    }
    private MathematicalAnalysis.Analysis analyze(String source,List<String> roots) {
        return analyze(parse(source, roots), source, roots);
    }
    private CompilationUnit parse(String source, List<String> roots) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray()); parser.setUnitName("sample/Sample.java");
        parser.setEnvironment(new String[0], roots.toArray(String[]::new), java.util.Collections.nCopies(roots.size(),"UTF-8").toArray(String[]::new), true);
        parser.setResolveBindings(true);
        var compiler = new HashMap<String,String>(); JavaCore.setComplianceOptions("17", compiler); parser.setCompilerOptions(compiler);
        var ast = (CompilationUnit)parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
    private MathematicalAnalysis.Analysis analyze(CompilationUnit ast, String source, List<String> roots) {
        var options = new MathCleanUpOptions(true, EnumSet.of(NumericKind.BIG_INTEGER, NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
            OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of(), true);
        var config = new HashMap<>(options.toMap()); config.put(MathCleanUpOptions.EXPLANATIONS, "NONE");
        options = MathCleanUpOptions.parse(config, 17);
        int start = source.indexOf("BigInteger result =");
        return MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), start, source.indexOf(";", start) + 1 - start,
            new MathematicalAnalysis.SourceEnvironment("sample/Sample.java", List.of(), roots, java.util.Collections.nCopies(roots.size(),"UTF-8")));
    }
}
