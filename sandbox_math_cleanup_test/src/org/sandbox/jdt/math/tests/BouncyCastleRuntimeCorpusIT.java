/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Opt-in external-corpus qualification. Run through qa/math-optimization/runtime-bouncy.sh. */
class BouncyCastleRuntimeCorpusIT {
    private static final String REVISION = "c314b9cdffa3958a0eff5344f8fdcdb0181ed830";
    private static final String PACKAGE = "org.bouncycastle.crypto.digests.";
    private static final String SOURCE_DIRECTORY = "core/src/main/java/org/bouncycastle/crypto/digests/";
    private static Path repository;
    private static Path baseline;
    private static Path output;
    private static final MathCleanUpOptions OPTIONS = new MathCleanUpOptions(true,
            Set.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
            OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 8, List.of());

    @BeforeAll static void requirePinnedUnmodifiedCorpusAndRealDependencies() throws Exception {
        repository = directory("MATH_BC_SOURCE");
        baseline = directory("MATH_BC_CLASSES");
        output = directory("MATH_BC_OUTPUT");
        assertEquals(REVISION, command(repository, 0, "git", "rev-parse", "HEAD").strip());
        assertEquals("", command(repository, 0, "git", "status", "--porcelain").strip());
        assertTrue(Files.isRegularFile(baseline.resolve("org/bouncycastle/crypto/digests/GeneralDigest.class")));
        assertTrue(Files.isRegularFile(baseline.resolve("org/bouncycastle/crypto/test/SM3DigestTest.class")));
    }

    @TestFactory Stream<DynamicTest> unchangedProductionFiles() {
        return Stream.of(
                new Case("MD4Digest", Set.of("F", "G")),
                new Case("RIPEMD160Digest", Set.of("f2", "f4")),
                new Case("SM3Digest", Set.of("FF1", "GG1")),
                new Case("SHA256Digest", Set.of()))
                .map(example -> DynamicTest.dynamicTest(example.name(), () -> qualify(example)));
    }

    private static void qualify(Case example) throws Exception {
        String relative = SOURCE_DIRECTORY + example.name() + ".java";
        Path originalFile = repository.resolve(relative);
        String original = Files.readString(originalFile);
        String sourceHash = hash(original);
        CompilationUnit originalAst = parse(original, example.name());
        Path caseOutput = Files.createDirectories(output.resolve(example.name()));
        var analysis = analyze(originalAst, original, example.name());
        Files.writeString(caseOutput.resolve("diagnostics.txt"), analysis.diagnostics().toString());
        Files.writeString(caseOutput.resolve("independent-proof-and-cost.txt"), analysis.evidence().toString());
        assertEquals(example.methods().size(), analysis.evidence().size(), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().stream().allMatch(e -> e.cost().estimatedRuntimeImprovement()));

        Document document = new Document(original);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(original, document.get(), "Byte-exact Undo");
        assertEquals(example.methods(), changedMethods(originalAst, analysis));
        for (Object value : originalAst.getCommentList()) {
            ASTNode comment = (ASTNode) value;
            assertTrue(generated.contains(original.substring(comment.getStartPosition(),
                    comment.getStartPosition() + comment.getLength())), "Original comment was lost");
        }
        assertFalse(analyze(parse(generated, example.name()), generated, example.name()).changed(), "Idempotence");
        assertFalse(generated.contains("_math"), "Do not hide a more verbose generated result in the demo");
        write(caseOutput.resolve("before").resolve(relative), original);
        Path after = write(caseOutput.resolve("after").resolve(relative), generated);
        Path classes = Files.createDirectories(caseOutput.resolve("classes"));
        var compilerOutput = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, compilerOutput, compilerOutput,
                "--release", "8", "-classpath", baseline.toString(), "-d", classes.toString(), after.toString()),
                compilerOutput.toString(StandardCharsets.UTF_8));

        int comparisons;
        try (var originalLoader = new URLClassLoader(new URL[] {baseline.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
                var generatedLoader = new URLClassLoader(new URL[] {classes.toUri().toURL(), baseline.toUri().toURL()},
                        ClassLoader.getPlatformClassLoader())) {
            runOriginalUpstreamTests(originalLoader, example.name());
            runOriginalUpstreamTests(generatedLoader, example.name());
            comparisons = compareCompleteDigests(originalLoader, generatedLoader, example.name());
        }
        String patch = command(caseOutput, example.methods().isEmpty() ? 0 : 1, "git", "diff", "--no-index",
                "--no-ext-diff", "--no-color", "--src-prefix=a/", "--dst-prefix=b/",
                "before/" + relative, "after/" + relative);
        patch = patch.lines().map(line -> line.startsWith("diff --git ") || line.startsWith("--- ") || line.startsWith("+++ ")
                ? line.replace("a/before/", "a/").replace("b/after/", "b/") : line)
                .collect(java.util.stream.Collectors.joining("\n", "", patch.isEmpty() ? "" : "\n"));
        Path patchFile = write(caseOutput.resolve(example.name() + ".patch"), patch);
        if (!patch.isEmpty()) command(repository, 0, "git", "apply", "--check", patchFile.toString());
        assertEquals(sourceHash, hash(Files.readString(originalFile)), "Qualification must not modify upstream source");
        assertEquals("", command(repository, 0, "git", "status", "--porcelain").strip());
        write(caseOutput.resolve("qualification.properties"),
                "status=PASS\nsource.repository=bcgit/bc-java\nsource.revision=" + REVISION
                + "\nsource.path=" + relative + "\nsource.sha256=" + sourceHash
                + "\ngenerated.sha256=" + hash(generated) + "\npatch.sha256=" + hash(patch)
                + "\nchanged.methods=" + String.join(",", example.methods().stream().sorted().toList())
                + "\nverified.regions=" + analysis.evidence().size()
                + "\ncompile.release=8\nupstream.tests.original=PASS\nupstream.tests.generated=PASS"
                + "\ndifferential.full.digest.comparisons=" + comparisons
                + "\nundo=BYTE_EXACT\nidempotence=PASS\nupstream.patch.apply.check=PASS"
                + "\nmeasured.speedup=NOT_CLAIMED\nconstant.time.qualification=NOT_CLAIMED\n");
        System.out.println("BC_RUNTIME_PASS " + example.name() + " regions=" + analysis.evidence().size()
                + " fullDigestComparisons=" + comparisons + " sha256=" + sourceHash);
    }

    private static Set<String> changedMethods(CompilationUnit ast, MathematicalAnalysis.Analysis analysis) {
        var names = new java.util.HashSet<String>();
        ast.accept(new ASTVisitor() {
            @Override public boolean visit(MethodDeclaration method) {
                for (var change : analysis.evidence()) {
                    if (change.offset() >= method.getStartPosition()
                            && change.offset() + change.length() <= method.getStartPosition() + method.getLength())
                        names.add(method.getName().getIdentifier());
                }
                return false;
            }
        });
        return names;
    }

    private static MathematicalAnalysis.Analysis analyze(CompilationUnit ast, String source, String name) {
        return MathematicalAnalysis.analyze(ast, source, OPTIONS, new NullProgressMonitor(), -1, 0,
                new MathematicalAnalysis.SourceEnvironment(name + ".java", List.of(baseline.toString()), List.of(), List.of()));
    }

    private static CompilationUnit parse(String source, String name) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(name + ".java");
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(false);
        parser.setEnvironment(new String[] {baseline.toString()}, new String[0], null, true);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        parser.setCompilerOptions(options);
        CompilationUnit ast = (CompilationUnit) parser.createAST(null);
        assertTrue(Arrays.stream(ast.getProblems()).noneMatch(p -> p.isError()), Arrays.toString(ast.getProblems()));
        return ast;
    }

    private static void runOriginalUpstreamTests(ClassLoader loader, String digest) throws Exception {
        Object test = loader.loadClass("org.bouncycastle.crypto.test." + digest + "Test").getConstructor().newInstance();
        Object result = test.getClass().getMethod("perform").invoke(test);
        Class<?> resultType = loader.loadClass("org.bouncycastle.util.test.TestResult");
        assertEquals(Boolean.TRUE, resultType.getMethod("isSuccessful").invoke(result), result.toString());
    }

    private static int compareCompleteDigests(ClassLoader before, ClassLoader after, String name) throws Exception {
        var original = new DigestAccess(before.loadClass(PACKAGE + name));
        var generated = new DigestAccess(after.loadClass(PACKAGE + name));
        SplittableRandom random = new SplittableRandom(1657);
        int comparisons = 0;
        for (int length : new int[] {0, 1, 3, 55, 56, 63, 64, 65, 127, 128, 129, 1024, 8192}) {
            for (int pattern = 0; pattern < 4; pattern++) {
                byte[] message = new byte[length];
                for (int i = 0; i < length; i++) message[i] = switch (pattern) {
                    case 0 -> 0; case 1 -> (byte) 0xff; case 2 -> (byte) i; default -> (byte) random.nextInt();
                };
                for (int split : new int[] {0, length / 2, length}) {
                    assertArrayEquals(original.hash(message, split), generated.hash(message, split), name + " length=" + length);
                    comparisons++;
                }
            }
        }
        return comparisons;
    }

    private record DigestAccess(Class<?> type, Method update, Method finish, Method size) {
        DigestAccess(Class<?> type) throws NoSuchMethodException {
            this(type, type.getMethod("update", byte[].class, int.class, int.class),
                    type.getMethod("doFinal", byte[].class, int.class), type.getMethod("getDigestSize"));
        }
        byte[] hash(byte[] message, int split) throws ReflectiveOperationException {
            Object digest = type.getConstructor().newInstance();
            update.invoke(digest, message, 0, split);
            update.invoke(digest, message, split, message.length - split);
            byte[] result = new byte[(Integer) size.invoke(digest)];
            assertEquals(result.length, finish.invoke(digest, result, 0));
            return result;
        }
    }

    private static Path directory(String variable) throws Exception {
        String value = System.getenv(variable);
        assertNotNull(value, variable + " must identify the pinned external qualification inputs");
        Path path = Path.of(value).toRealPath();
        assertTrue(Files.isDirectory(path), variable);
        return path;
    }

    private static Path write(Path path, String value) throws Exception {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, value);
        return path;
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String command(Path directory, int expectedExit, String... command) throws Exception {
        Path log = Files.createTempFile(output, "command-", ".log");
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Command timed out: " + Arrays.toString(command));
        }
        String text = Files.readString(log);
        assertEquals(expectedExit, process.exitValue(), text);
        Files.delete(log);
        return text;
    }

    private record Case(String name, Set<String> methods) { }
}
