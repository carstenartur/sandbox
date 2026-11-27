/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jface.text.Document;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.FieldCallEvidence;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

/** Qualification of the ordinary Java analysis on an unmodified, commit-pinned scalar program. */
@EnabledIfSystemProperty(named = "sandbox.math.bc.root.upstream", matches = "true")
class BouncyCastleGuardedRootTest {
    private static final String REMOTE = "https://github.com/bcgit/bc-java.git";
    private static final String REF = "refs/heads/main", COMMIT = "6560e91d0a164b8ab58266f141ee6daeec21a340";
    private static final String TARGET = "org/bouncycastle/crypto/bls/BLS12_381G2HashToCurve.java";
    @TempDir Path temporary;

    @Test @Timeout(240) void ordinaryApplySpecializesTheCallAndPreservesDirectRootsAndPublicVectors() throws Exception {
        var mirror = System.getProperty("sandbox.math.bc.root.repository");
        var remote = URI.create(mirror == null ? REMOTE : mirror);
        if (mirror != null) try (var repo = Git.open(Path.of(remote).toFile())) {
            assertEquals(REMOTE, repo.getRepository().getConfig().getString("remote", "origin", "url"));
        }
        try (var checkout = mirror == null
                ? PinnedGitRepository.cloneAtCommit(temporary.resolve("checkout"), remote, REF, COMMIT)
                : PinnedGitRepository.cloneAt(temporary.resolve("checkout"), remote, REF, COMMIT)) {
            assertEquals(COMMIT, checkout.headCommit());
            Path sources = checkout.directory().resolve("core/src/main/java");
            String original = Files.readString(sources.resolve(TARGET));
            var originalAst = parse(original, sources);
            var analysis = analyze(originalAst, original, sources);
            var region = analysis.evidence().stream().filter(r -> r.fieldRoot() != null).findFirst()
                    .orElseThrow(() -> new AssertionError(analysis.diagnostics().toString()));
            var proof = region.fieldRoot();
            var program = proof.certificate().program();
            assertEquals(1L, program.prefix().stream().filter(s -> s.op().name().equals("POWER")).count());
            assertTrue(List.of(program.prefix(), program.equal().steps(), program.unequal().steps()).stream()
                    .flatMap(List::stream).noneMatch(s -> s.op().name().equals("INVERSE")));
            assertTrue(program.equal().normalizeRealCharacter() || program.unequal().normalizeRealCharacter(),
                    "The exact-pair program still needs the raw-root sign correction");
            var call = proof.callSite();
            assertNotNull(call, "Ordinary Apply must qualify the actual BC call site");
            for (var coordinate : call.inputs().subList(0, 2)) {
                assertTrue(coordinate.exactClass());
                assertTrue(coordinate.nonnegative());
                assertFalse(coordinate.belowModulus(), "A magnitude constructor does not prove the field bound");
            }
            assertFalse(call.inputs().get(2).exactClass(), "A BigInteger return type alone is insufficient");
            assertFalse(call.normWitnessKey().isEmpty(), "The true norm branch has a bound source witness");
            var document = new Document(original);
            var undo = analysis.newEdit().apply(document);
            String generated = document.get();
            undo.apply(document);
            assertEquals(original, document.get());
            var generatedAst = parse(generated, sources);
            assertFalse(analyze(generatedAst, generated, sources).changed());
            Path output = Path.of(System.getProperty("sandbox.math.bc.root.output", temporary.resolve("products").toString()));
            assertEquals(rootMethodSource(originalAst, original, proof.methodKey()),
                    rootMethodSource(generatedAst, generated, proof.methodKey()),
                    "A call-site specialization must leave the original private helper byte-for-byte unchanged");
            assertCallRouting(generatedAst, proof.methodKey(), call);
            assertEquals(run(output.resolve("original"), original, sources, null),
                    run(output.resolve("generated"), generated, sources, call.helperName()));
            var receipts = analysis.sourceDependencyReceipts().stream().map(r -> r.identity()).toList();
            for (var file : List.of("Fp2Element.java", "Fp.java", "BLS12_381G1.java", "BigIntegers.java", "Pack.java", "Nat.java", "Mod.java", "Arrays.java"))
                assertTrue(receipts.stream().anyMatch(path -> path.endsWith(file)), () -> receipts.toString());
            for (String dependency : List.of("org/bouncycastle/crypto/bls/Fp.java", "org/bouncycastle/util/Pack.java")) {
                Path helper = sources.resolve(dependency);
                String unchanged = Files.readString(helper);
                Files.writeString(helper, unchanged + "\n");
                assertThrows(IllegalArgumentException.class, analysis::newEdit, dependency);
                Files.writeString(helper, unchanged);
                assertDoesNotThrow(analysis::newEdit);
            }
            if (System.getProperty("sandbox.math.bc.root.output") != null) {
                var receipt = new java.util.Properties();
                receipt.setProperty("upstreamCommit", COMMIT);
                for (String variant : List.of("original", "generated")) {
                    var directory = output.resolve(variant);
                    receipt.setProperty(variant + "SourceSha256", CorpusFiles.sha256(directory.resolve("BLS12_381G2HashToCurve.java")));
                    receipt.setProperty(variant + "ClassesSha256", GuardedRootBenchmark.classTreeSha256(directory.resolve("classes")));
                }
                receipt.setProperty("candidates", Integer.toString(proof.candidates()));
                receipt.setProperty("searchWork", Long.toString(proof.searchWork()));
                receipt.setProperty("prefixSteps", Integer.toString(program.prefix().size()));
                receipt.setProperty("equalSteps", Integer.toString(program.equal().steps().size()));
                receipt.setProperty("unequalSteps", Integer.toString(program.unequal().steps().size()));
                try (var writer = Files.newBufferedWriter(output.resolve("root-qualification.properties"))) {
                    receipt.store(writer, "Exact-pair verification on upstream source");
                }
            }
            System.out.printf("FIELD_ROOT source=%s candidates=%d work=%d prefix=%d equal=%d unequal=%d%n",
                    COMMIT, proof.candidates(), proof.searchWork(),
                    program.prefix().size(), program.equal().steps().size(), program.unequal().steps().size());
        }
    }

    private static CompilationUnit parse(String source, Path sources) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setEnvironment(new String[0], new String[]{sources.toString()}, null, true);
        parser.setUnitName(TARGET);
        parser.setCompilerOptions(StrictCompilerOptions.allWarnings(17));
        parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), () -> Arrays.toString(ast.getProblems()));
        return ast;
    }

    private static MathematicalAnalysis.Analysis analyze(CompilationUnit ast, String source, Path sources) {
        var options = MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.CLEANUP, "true",
                MathCleanUpOptions.WORK_BUDGET, "100000000", MathCleanUpOptions.MAX_STATES, "20000",
                MathCleanUpOptions.TIMEOUT_SECONDS, "60"), 17);
        return MathematicalAnalysis.analyze(ast, source, options, null, -1, 0,
                new MathematicalAnalysis.SourceEnvironment(TARGET, List.of(), List.of(sources.toString()), List.of(),
                        StrictCompilerOptions.allWarnings(17)));
    }

    private static String rootMethodSource(CompilationUnit ast, String source, String rootKey) {
        var method = assertInstanceOf(MethodDeclaration.class, ast.findDeclaringNode(rootKey));
        return source.substring(method.getStartPosition(), method.getStartPosition() + method.getLength());
    }

    private static void assertCallRouting(CompilationUnit ast, String rootKey, FieldCallEvidence call) {
        var caller = assertInstanceOf(MethodDeclaration.class, ast.findDeclaringNode(call.callerKey()));
        var members = new ArrayList<MethodInvocation>();
        caller.accept(new ASTVisitor() {
            @Override public boolean visit(MethodInvocation invocation) {
                var binding = invocation.resolveMethodBinding();
                if (binding != null && binding.getDeclaringClass().isEqualTo(caller.resolveBinding().getDeclaringClass())) {
                    members.add(invocation);
                }
                return true;
            }
        });
        var candidates = members.stream().filter(i -> i.resolveMethodBinding().getName().equals(call.helperName())).toList();
        assertEquals(1, candidates.size(), "The proven caller must invoke its candidate");
        var bindings = new ArrayList<String>();
        for (Object argument : candidates.getFirst().arguments()) {
            var name = assertInstanceOf(SimpleName.class, argument);
            bindings.add(name.resolveBinding().getKey());
        }
        assertEquals(call.arguments(), bindings.subList(0, 3));
        assertEquals(call.normWitnessKey(), bindings.get(3));
        assertEquals(1L, members.stream().filter(i -> i.resolveMethodBinding().getKey().equals(rootKey)).count(),
                "The bound original helper is still the caller's direct fallback");
    }

    private static Object run(Path dir, String source, Path sources, String candidate) throws Exception {
        var classes = Files.createDirectories(dir.resolve("classes"));
        var target = Files.writeString(dir.resolve("BLS12_381G2HashToCurve.java"), source);
        var probe = Files.writeString(dir.resolve("RootProbe.java"), PROBE);
        var javac = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "--release", "17", "-proc:none", "-sourcepath", sources.toString(),
                "-d", classes.toString(), target.toString(), probe.toString()));
        assertEquals(0, javac.run(null, null, null, "--release", "17", "-proc:none", "-Xlint:all", "-Werror", "-sourcepath", "",
                "-cp", classes.toString(), "-d", classes.toString(), target.toString(), probe.toString()));
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var probeClass = loader.loadClass("org.bouncycastle.crypto.bls.RootProbe");
            if (candidate != null)
                probeClass.getMethod("verifyCandidate", String.class).invoke(null, candidate);
            return probeClass.getMethod("run").invoke(null);
        }
    }

    private static final String PROBE = """
            package org.bouncycastle.crypto.bls;
            import java.lang.reflect.Modifier;
            import java.math.BigInteger;
            import java.nio.charset.StandardCharsets;
            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.List;
            import java.util.Random;
            import org.bouncycastle.util.encoders.Hex;
            public class RootProbe {
                public static Object run() {
                    var out = new ArrayList<String>();
                    var inputs = new Fp2Element[]{Fp2Element.ZERO, Fp2Element.ONE, Fp2Element.of(4, 0),
                            Fp2Element.of(-4, 0), Fp2Element.of(7, 13), Fp2Element.of(0, 2), Fp2Element.of(3, 4),
                            Fp2Element.of(1, 1), Fp2Element.of(-2, 1)};
                    for (int i = 0; i < inputs.length; i++) {
                        addRoot(out, inputs[i], i < 7);
                    }
                    expectFailure(out, NullPointerException.class,
                            () -> BLS12_381G2HashToCurve.sqrtOrSqrtZ(null, new Fp2Element[1]));
                    expectFailure(out, NullPointerException.class,
                            () -> BLS12_381G2HashToCurve.sqrtOrSqrtZ(inputs[7], null));
                    expectFailure(out, ArrayIndexOutOfBoundsException.class,
                            () -> BLS12_381G2HashToCurve.sqrtOrSqrtZ(inputs[4], new Fp2Element[0]));
                    var random = new Random(541331);
                    for (int i = 0; i < 128; i++) {
                        BigInteger a = new BigInteger(381, random).mod(Fp2Element.P);
                        BigInteger b = new BigInteger(381, random).mod(Fp2Element.P);
                        addRoot(out, Fp2Element.of(a, b), null);
                    }
                    var h = new BLS12_381G2HashToCurve("QUUX-V01-CS02-with-BLS12381G2_XMD:SHA-256_SSWU_RO_".getBytes(StandardCharsets.US_ASCII));
                    for (var input : inputs) {
                        out.add(encode(h.mapToCurveAndIso3(input)));
                    }
                    expectFailure(out, NullPointerException.class, () -> h.mapToCurveAndIso3(null));
                    var ikm = new byte[32];
                    random.nextBytes(ikm);
                    var secret = BLS12_381BasicScheme.keyGenEncoded(ikm, new byte[0]);
                    var publicKey = BLS12_381BasicScheme.skToPk(secret);
                    for (String message : new String[]{"", "abc", "abcdef0123456789"}) {
                        byte[] bytes = message.getBytes(StandardCharsets.US_ASCII);
                        var point = h.hashToCurve(bytes);
                        out.add(encode(point));
                        if (message.isEmpty() && !point.x().c0().equals(new BigInteger(
                                "0141ebfbdca40eb85b87142e130ab689c673cf60f1a3e98d69335266f30d9b8d4ac44c1038e9dcdd5393faf5c41fb78a", 16)))
                            throw new AssertionError("RFC 9380 J.10.1");
                        var signature = BLS12_381BasicScheme.sign(secret, bytes);
                        if (!BLS12_381BasicScheme.verify(publicKey, bytes, signature))
                            throw new AssertionError("BLS signature did not verify");
                        out.add(encode(signature));
                    }
                    return out;
                }

                private static void addRoot(List<String> out, Fp2Element input, Boolean expectedSquare) {
                    var root = new Fp2Element[1];
                    boolean square = BLS12_381G2HashToCurve.sqrtOrSqrtZ(input, root);
                    if (expectedSquare != null && square != expectedSquare.booleanValue())
                        throw new AssertionError("Unexpected norm branch for " + input);
                    var squareTarget = square ? input : Fp2Element.of(-2, -1).mul(input);
                    if (!root[0].square().equals(squareTarget))
                        throw new AssertionError("Incorrect square root for " + input);
                    out.add(square + ":" + root[0].c0() + ":" + root[0].c1());
                }

                private static void expectFailure(List<String> out, Class<? extends RuntimeException> expected,
                        Runnable operation) {
                    try {
                        operation.run();
                    } catch (RuntimeException failure) {
                        if (failure.getClass() != expected)
                            throw failure;
                        out.add(failure.getClass().getName());
                        return;
                    }
                    throw new AssertionError("Expected " + expected.getName());
                }

                private static String encode(BLS12_381G2Point point) {
                    String compressed = Hex.toHexString(BLS12_381Serialization.compressG2(point));
                    if (point.isInfinity())
                        return compressed;
                    return point.x().c0() + ":" + point.x().c1() + ":" + point.y().c0()
                            + ":" + point.y().c1() + ":" + compressed;
                }

                public static void verifyCandidate(String name) throws ReflectiveOperationException {
                    var owner = BLS12_381G2HashToCurve.class;
                    var candidate = owner.getDeclaredMethod(name, BigInteger.class, BigInteger.class,
                            BigInteger.class, boolean.class);
                    if (!Modifier.isPrivate(candidate.getModifiers()) || !Modifier.isStatic(candidate.getModifiers()))
                        throw new AssertionError("The proven caller owns the candidate");
                    var original = owner.getDeclaredMethod("sqrtOfSquare", BigInteger.class, BigInteger.class,
                            BigInteger.class);
                    candidate.setAccessible(true);
                    original.setAccessible(true);

                    var p = Fp2Element.P;
                    var exp = p.add(BigInteger.ONE).shiftRight(2);
                    var one = BigInteger.ONE;
                    var two = BigInteger.TWO;
                    var zero = BigInteger.ZERO;
                    var a = BigInteger.valueOf(3);
                    var b = BigInteger.valueOf(4);
                    var trueNormRoot = BigInteger.valueOf(25).modPow(exp, p);
                    // The uncorrected inverse-root rotation gives (2, 1) for this input.
                    var expectedTrue = Fp2Element.of(-2, -1);
                    if (!expectedTrue.equals(original.invoke(null, a, b, trueNormRoot))
                            || !expectedTrue.equals(candidate.invoke(null, a, b, trueNormRoot, true)))
                        throw new AssertionError("The certified nonreal candidate must preserve the signed root");

                    var zw = Fp2Element.of(-2, -1).mul(Fp2Element.of(1, 1));
                    var falseNormRoot = p.subtract(BigInteger.valueOf(5)).modPow(exp, p)
                            .multiply(two.modPow(exp, p)).mod(p);
                    var expectedFalse = original.invoke(null, zw.c0(), zw.c1(), falseNormRoot);
                    if (!expectedFalse.equals(candidate.invoke(null, zw.c0(), zw.c1(), falseNormRoot, false)))
                        throw new AssertionError("The false norm branch must pass the retained check for valid inputs");

                    var subclass = new BigInteger(trueNormRoot.toByteArray()) {
                        private static final long serialVersionUID = 1L;
                        @Override public int signum() {
                            throw new AssertionError("The field guard must check the class before virtual operations");
                        }
                    };
                    for (Object[] input : new Object[][]{
                            {p.add(a), b, trueNormRoot, true},
                            {a, p.add(b), trueNormRoot, true},
                            {a, b, one, false},
                            {a, b, null, false},
                            {a, b, subclass, false},
                            {one, zero, one, true}}) {
                        if (candidate.invoke(null, input) != null)
                            throw new AssertionError("A remaining guard failed to reject " + Arrays.toString(input));
                    }
                }
            }
            """;
}
