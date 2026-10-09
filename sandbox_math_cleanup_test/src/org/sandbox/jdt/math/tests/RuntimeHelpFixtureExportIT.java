/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Run only after BouncyCastleRuntimeCorpusIT. Never fabricate before/after source for screenshots. */
class RuntimeHelpFixtureExportIT {
    private static final String REVISION = "c314b9cdffa3958a0eff5344f8fdcdb0181ed830";
    private static final String PACKAGE = "org.bouncycastle.crypto.digests";
    private static final String SOURCE_DIRECTORY = "core/src/main/java/org/bouncycastle/crypto/digests/";

    @Test void exportedMethodPreviewsMatchTheQualifiedWholeFileChanges() throws Exception {
        Path repository = Path.of(required("MATH_BC_SOURCE")).toRealPath();
        Path qualified = Path.of(required("MATH_BC_OUTPUT")).toRealPath();
        Path fixtures = qualified.resolve("help-fixtures");
        assertFalse(Files.exists(fixtures), "Never overwrite an earlier fixture export");
        Files.createDirectory(fixtures);
        for (Example example : List.of(
                new Example("01-bouncy-castle-md4-choice", "MD4Digest", Set.of("F")),
                new Example("02-bouncy-castle-md4-majority", "MD4Digest", Set.of("G")),
                new Example("03-bouncy-castle-ripemd-choices", "RIPEMD160Digest", Set.of("f2", "f4")),
                new Example("04-bouncy-castle-sm3-functions", "SM3Digest", Set.of("FF1", "GG1")))) {
            String path = SOURCE_DIRECTORY + example.type() + ".java";
            Path result = qualified.resolve(example.type());
            Properties qualification = new Properties();
            try (var reader = Files.newBufferedReader(result.resolve("qualification.properties"), StandardCharsets.UTF_8)) {
                qualification.load(reader);
            }
            assertEquals("PASS", qualification.getProperty("status"));
            assertEquals(REVISION, qualification.getProperty("source.revision"));
            assertEquals(path, qualification.getProperty("source.path"));
            String original = Files.readString(repository.resolve(path));
            String generated = Files.readString(result.resolve("after").resolve(path));
            assertEquals(qualification.getProperty("source.sha256"), hash(original));
            assertEquals(original, Files.readString(result.resolve("before").resolve(path)));
            assertEquals(qualification.getProperty("generated.sha256"), hash(generated));
            Fragment before = fragment(original, example);
            Fragment after = fragment(generated, example);
            String wrapper = "package " + PACKAGE + ";\n\nclass " + example.type() + "\n{\n";
            String beforeSource = wrapper + before.source() + "\n}\n";
            String afterSource = wrapper + after.source() + "\n}\n";
            assertNotEquals(beforeSource, afterSource);
            // The native preview uses an otherwise empty class shell around exact,
            // contiguous upstream methods. Check that this does not change the result.
            var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG),
                    SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME,
                    1_000_000L, 20_000, false, 17, List.of());
            var analysis = MathematicalAnalysis.analyze(parse(beforeSource, example.type()), beforeSource,
                    options, new NullProgressMonitor(), -1, 0);
            assertTrue(analysis.changed(), analysis.diagnostics().toString());
            Document document = new Document(beforeSource);
            analysis.newEdit().apply(document);
            assertEquals(afterSource, document.get(), "Excerpt and complete production-file results must agree");
            Path directory = Files.createDirectory(fixtures.resolve(example.id()));
            write(directory.resolve("before.java.txt"), beforeSource);
            write(directory.resolve("after.java.txt"), afterSource);
            Map<String, String> metadata = new TreeMap<>();
            metadata.put("id", example.id());
            metadata.put("fileName", example.type() + ".java");
            metadata.put("packageName", PACKAGE);
            metadata.put("kinds", "INT,LONG");
            metadata.put("goal", "LOWER_ESTIMATED_RUNTIME");
            metadata.put("beforeFragment", firstReturn(before.source()));
            metadata.put("afterFragment", firstReturn(after.source()));
            metadata.put("sourceRepository", "bcgit/bc-java");
            metadata.put("sourceCommit", REVISION);
            metadata.put("sourcePath", path);
            metadata.put("sourceLines", before.lines());
            metadata.put("sourceMethods", String.join(",", example.methods().stream().sorted().toList()));
            metadata.put("excerptSha256", hash(beforeSource));
            metadata.put("sourceMethodSpanSha256", hash(before.source()));
            metadata.put("sourceFileSha256", hash(original));
            metadata.put("qualifiedGeneratedFileSha256", hash(generated));
            metadata.put("qualifiedSourcePath", example.type() + "/qualification.properties");
            metadata.put("sourceKind", "Unchanged contiguous production methods in an empty class shell; complete upstream files are qualified separately");
            metadata.put("runtimeVariables", "true");
            metadata.put("measuredSpeedup", "not claimed");
            StringBuilder properties = new StringBuilder();
            metadata.forEach((key, value) -> properties.append(key).append('=').append(escape(value)).append('\n'));
            write(directory.resolve("example.properties"), properties.toString());
        }
        System.out.println("RUNTIME_HELP_FIXTURES_PASS count=4 fullProductionSource=" + REVISION);
    }

    private static Fragment fragment(String source, Example example) {
        CompilationUnit ast = parse(source, example.type());
        List<MethodDeclaration> methods = new ArrayList<>();
        ast.accept(new ASTVisitor() {
            @Override public boolean visit(MethodDeclaration method) {
                if (example.methods().contains(method.getName().getIdentifier())) methods.add(method);
                return false;
            }
        });
        assertEquals(example.methods().size(), methods.size());
        int start = methods.stream().mapToInt(MethodDeclaration::getStartPosition).min().orElseThrow();
        int end = methods.stream().mapToInt(m -> m.getStartPosition() + m.getLength()).max().orElseThrow();
        start = source.lastIndexOf('\n', start - 1) + 1;
        return new Fragment(source.substring(start, end), ast.getLineNumber(start) + "-" + ast.getLineNumber(end - 1));
    }

    private static CompilationUnit parse(String source, String name) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(name + ".java");
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        // Whole production files may need the external baseline to resolve imports.
        String baseline = System.getenv("MATH_BC_CLASSES");
        if (baseline != null) parser.setEnvironment(new String[] {baseline}, new String[0], null, true);
        CompilationUnit ast = (CompilationUnit) parser.createAST(null);
        assertTrue(Arrays.stream(ast.getProblems()).noneMatch(p -> p.isError()), Arrays.toString(ast.getProblems()));
        return ast;
    }

    private static String firstReturn(String source) {
        return source.lines().map(String::strip).filter(line -> line.startsWith("return ")).findFirst().orElseThrow();
    }
    private static String required(String variable) {
        String value = System.getenv(variable);
        assertNotNull(value, variable + " must come from the preceding production qualification");
        return value;
    }
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static void write(Path path, String value) throws Exception {
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private record Example(String id, String type, Set<String> methods) { }
    private record Fragment(String source, String lines) { }
}
