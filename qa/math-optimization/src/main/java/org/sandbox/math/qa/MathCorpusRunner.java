/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.SourceEnvironment;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Analysis only: never applies a proposed edit to upstream source or executes it. */
public final class MathCorpusRunner {
    public static final String REFERENCE_PATH = "core/src/main/java/org/bouncycastle/crypto/hash2curve/impl/GenericSqrtRatioCalculator.java";
    public static final String LICENSE_BLOB = "a2253d7c69443c24ad6c5af81dfa2678cae72cd9";
    public static final Pin ORIGINAL = new Pin("math-original", "ab16374d37c7e18c4090eb8838ebbd72a92593f2", "acf5666f51eb82688878bde8c447a58b6bdd73d1");
    public static final Pin CANDIDATE = new Pin("math-candidate", "3d56837c3fe6c4a30fc7639b806958649e143210", "693f65df02ea381a25339a2cb9f40106e5b3d35e");
    private static final String REMOTE = "https://github.com/bcgit/bc-java.git";
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final MathCleanUpOptions OPTIONS = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER),
            SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 100_000L, 2_000, false, 8, List.of());

    private MathCorpusRunner() { }
    public record Pin(String ref, String commit, String sourceBlob) { }
    public record Report(long javaFiles, long filesWithErrors, long bigIntegerCalls, long extractedRegions,
            long supportedRegions, long candidateRegions, long rejectedRegions, long appliedChanges, long elapsedNanos) { }

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("references")) {
            archiveReferences(prepareMirror(Path.of(args[1])), Path.of(args[2]));
            return;
        }
        if (args.length != 2) throw new IllegalArgumentException("<cache-directory> <report-directory> OR references <cache-directory> <snapshot-directory>");
        Path cache = Files.createDirectories(Path.of(args[0]).toAbsolutePath());
        Path report = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(report)) throw new IOException("Refusing to overwrite a corpus report: " + report);
        Files.createDirectories(report);
        Properties implementation = implementationReceipt();
        writeProperties(report.resolve("implementation.properties"), implementation);
        writeProperties(report.resolve("sandbox-source.properties"), repositoryReceipt());
        Path mirror = prepareMirror(cache);
        for (Pin pin : List.of(ORIGINAL, CANDIDATE)) {
            try (var checkout = PinnedGitRepository.cloneAt(cache.resolve(pin.ref()), mirror.toUri(), pin.ref(), pin.commit())) {
                verifyPinnedBlobs(checkout.directory(), pin);
                Path output = report.resolve(pin == ORIGINAL ? "original" : "candidate-reference");
                Report result = pin == ORIGINAL ? analyzeTree(checkout.directory(), output) : analyzeReference(checkout.directory(), output);
                Files.writeString(output.resolve("commit.txt"), pin.commit() + "\n");
                System.out.println(pin.ref() + ": " + result);
            }
        }
        requireUnchangedImplementation(implementation);
        Files.writeString(report.resolve("COMPLETED"), "Runtime class closure and SDK unchanged during analysis.\n");
    }

    /** A bare cache gives the existing fixture advertised, verified refs without relying on moving branches. */
    public static Path prepareMirror(Path cache) throws Exception {
        configureProxy();
        Files.createDirectories(cache);
        Path mirror = cache.resolve("bc-java.git").toAbsolutePath();
        try (Git git = Files.exists(mirror) ? Git.open(mirror.toFile()) : Git.init().setBare(true).setDirectory(mirror.toFile()).call()) {
            for (Pin pin : List.of(ORIGINAL, CANDIDATE)) {
                String ref = Constants.R_HEADS + pin.ref();
                ObjectId expected = ObjectId.fromString(pin.commit());
                if (!expected.equals(git.getRepository().resolve(ref + "^{commit}"))) {
                    git.fetch().setRemote(REMOTE).setDepth(1).setTagOpt(TagOpt.NO_TAGS)
                            .setRefSpecs(new RefSpec("+" + pin.commit() + ":" + ref)).call();
                    try (var walk = new RevWalk(git.getRepository())) {
                        if (!walk.parseCommit(expected).getId().equals(expected)) throw new IOException("Wrong fetched object: " + pin);
                    }
                    RefUpdate update = git.getRepository().updateRef(ref);
                    update.setNewObjectId(expected);
                    update.setForceUpdate(true);
                    var result = update.update();
                    if (!Set.of(RefUpdate.Result.NEW, RefUpdate.Result.NO_CHANGE, RefUpdate.Result.FORCED,
                            RefUpdate.Result.FAST_FORWARD).contains(result)) throw new IOException("Cannot pin " + ref + ": " + result);
                }
                if (!expected.equals(git.getRepository().resolve(ref + "^{commit}"))) throw new IOException("Pinned ref drift: " + ref);
            }
        }
        return mirror;
    }

    public static void verifyPinnedBlobs(Path root, Pin pin) throws Exception {
        try (Git git = Git.open(root.toFile()); var formatter = new ObjectInserter.Formatter()) {
            if (!pin.commit().equals(git.getRepository().resolve("HEAD^{commit}").name())) throw new IOException("Wrong checkout commit");
            String source = formatter.idFor(Constants.OBJ_BLOB, Files.readAllBytes(root.resolve(REFERENCE_PATH))).name();
            String license = formatter.idFor(Constants.OBJ_BLOB, Files.readAllBytes(root.resolve("LICENSE.html"))).name();
            if (!pin.sourceBlob().equals(source) || !LICENSE_BLOB.equals(license)) throw new IOException("Pinned source/license blob mismatch: " + pin);
        }
    }

    private static void archiveReferences(Path mirror, Path output) throws Exception {
        if (Files.exists(output)) throw new IOException("Refusing to overwrite upstream snapshots: " + output);
        Files.createDirectories(output);
        Properties receipt = new Properties();
        receipt.setProperty("remote", REMOTE);
        receipt.setProperty("reference.path", REFERENCE_PATH);
        receipt.setProperty("reference.status", "KNOWN_DEVELOPMENT_REFERENCE_NOT_UNSEEN_TRANSFER");
        receipt.setProperty("license.blob", LICENSE_BLOB);
        for (Pin pin : List.of(ORIGINAL, CANDIDATE)) {
            Path checkoutPath = Files.createTempDirectory("math-upstream-parent-");
            try (var checkout = PinnedGitRepository.cloneAt(checkoutPath.resolve("tree"), mirror.toUri(), pin.ref(), pin.commit())) {
                verifyPinnedBlobs(checkout.directory(), pin);
                String label = pin == ORIGINAL ? "original" : "candidate";
                Path target = Files.createDirectories(output.resolve(label)).resolve("GenericSqrtRatioCalculator.java");
                Files.copy(checkout.directory().resolve(REFERENCE_PATH), target);
                receipt.setProperty(label + ".commit", pin.commit());
                receipt.setProperty(label + ".blob", pin.sourceBlob());
                receipt.setProperty(label + ".sha256", sha256(Files.readAllBytes(target)));
                if (pin == ORIGINAL) Files.copy(checkout.directory().resolve("LICENSE.html"), output.resolve("LICENSE.html"));
            } finally { Files.delete(checkoutPath); }
        }
        try (Git git = Git.open(mirror.toFile()); var bytes = new ByteArrayOutputStream(); var formatter = new DiffFormatter(bytes)) {
            formatter.setRepository(git.getRepository());
            formatter.setPathFilter(PathFilter.create(REFERENCE_PATH));
            formatter.format(ObjectId.fromString(ORIGINAL.commit()), ObjectId.fromString(CANDIDATE.commit()));
            formatter.flush();
            Files.write(output.resolve("reference.patch"), bytes.toByteArray());
        }
        receipt.setProperty("license.sha256", sha256(Files.readAllBytes(output.resolve("LICENSE.html"))));
        receipt.setProperty("patch.sha256", sha256(Files.readAllBytes(output.resolve("reference.patch"))));
        writeProperties(output.resolve("provenance.properties"), receipt);
    }

    public static Report analyzeTree(Path root, Path output) throws Exception {
        List<Path> all = javaFiles(root);
        return analyze(root, all, all, output);
    }

    public static Report analyzeReference(Path root, Path output) throws Exception {
        return analyze(root, javaFiles(root), List.of(root.resolve(REFERENCE_PATH)), output);
    }

    private static Report analyze(Path root, List<Path> environmentFiles, List<Path> selected, Path output) throws Exception {
        long started = System.nanoTime();
        Files.createDirectories(output);
        List<String> roots = sourceRoots(environmentFiles);
        long errors = 0, calls = 0, regions = 0, supported = 0, candidates = 0;
        Map<String, Long> diagnosticCounts = new TreeMap<>();
        Map<Path, String> inputs = new TreeMap<>();
        try (var rows = Files.newBufferedWriter(output.resolve("files.tsv"))) {
            rows.write("path\tsource_sha256\tcompiler_errors\tbig_integer_calls\textracted_regions\tsupported_regions\tcandidate_regions\trejected_regions\tclassification\tdiagnostics\n");
            for (Path file : selected) {
                byte[] before = Files.readAllBytes(file);
                inputs.put(file, sha256(before));
                String source = new String(before, StandardCharsets.UTF_8);
                SourceEnvironment environment = new SourceEnvironment(unitName(file, source), List.of(), roots, List.of());
                CompilationUnit ast = parse(source, environment);
                int compilerErrors = (int) Arrays.stream(ast.getProblems()).filter(problem -> problem.isError()).count();
                long callCount = bigIntegerCalls(ast);
                long extracted = 0, accepted = 0, proposed = 0;
                List<String> diagnostics = new ArrayList<>();
                if (compilerErrors > 0) {
                    errors++;
                    diagnostics.add("COMPILATION_ERRORS=" + compilerErrors);
                    diagnosticCounts.merge("COMPILATION_ERRORS", (long) compilerErrors, Long::sum);
                    Arrays.stream(ast.getProblems()).filter(problem -> problem.isError())
                            .forEach(problem -> diagnostics.add("JDT:" + problem.getSourceLineNumber() + ":" + problem.getMessage()));
                } else {
                    var extraction = new JavaComputationExtractor().extract(ast, source, OPTIONS);
                    extracted = extraction.regions().size();
                    var analysis = MathematicalAnalysis.analyze(ast, source, OPTIONS, new NullProgressMonitor(), -1, 0, environment);
                    proposed = analysis.replacements().size();
                    long unchangedSupported = analysis.diagnostics().stream().filter(d -> d.code().equals("NOIMPROVEMENT")).count();
                    accepted = Math.min(extracted, proposed + unchangedSupported);
                    for (var diagnostic : analysis.diagnostics()) {
                        diagnostics.add(diagnostic.code() + "@" + diagnostic.offset() + ":" + diagnostic.message());
                        diagnosticCounts.merge(diagnostic.code(), 1L, Long::sum);
                    }
                }
                if (!Arrays.equals(before, Files.readAllBytes(file))) throw new IOException("Source changed during analysis: " + file);
                String relative = root.relativize(file).toString().replace('\\', '/');
                rows.write(tsv(relative) + '\t' + inputs.get(file) + '\t' + compilerErrors + '\t' + callCount + '\t'
                        + extracted + '\t' + accepted + '\t' + proposed + '\t' + (extracted - accepted) + '\t'
                        + (relative.equals(REFERENCE_PATH) ? "INITIALIZATION_REQUIRES_PARAMETER_REVIEW" : "UNCLASSIFIED_NO_APPLY")
                        + '\t' + tsv(String.join(" | ", diagnostics)) + '\n');
                calls += callCount; regions += extracted; supported += accepted; candidates += proposed;
            }
        }
        for (var input : inputs.entrySet()) if (!input.getValue().equals(sha256(Files.readAllBytes(input.getKey()))))
            throw new IOException("Source changed after analysis: " + input.getKey());
        try (var rows = Files.newBufferedWriter(output.resolve("diagnostics.tsv"))) {
            rows.write("diagnostic_code\toccurrences\n");
            for (var diagnostic : diagnosticCounts.entrySet()) rows.write(diagnostic.getKey() + '\t' + diagnostic.getValue() + '\n');
        }
        Report report = new Report(selected.size(), errors, calls, regions, supported, candidates, regions - supported, 0, System.nanoTime() - started);
        Properties summary = new Properties();
        summary.setProperty("schema", "math-corpus/v2-reconstructed");
        summary.setProperty("createdAt", Instant.now().toString());
        summary.setProperty("mode", "ANALYSIS_ONLY");
        summary.setProperty("safetyProfile", "PRESERVE_JAVA");
        summary.setProperty("numericKinds", "BIG_INTEGER");
        summary.setProperty("goal", "LOWER_ESTIMATED_RUNTIME");
        summary.setProperty("sourceCompliance", "8");
        summary.setProperty("bootClasses", "Runner JDK; not a --release 8 API check or the upstream Gradle build");
        summary.setProperty("javaRuntime", System.getProperty("java.runtime.version"));
        summary.setProperty("workBudgetPerFile", Long.toString(OPTIONS.workBudget()));
        summary.setProperty("maxStates", Integer.toString(OPTIONS.maxStates()));
        summary.setProperty("environmentJavaFiles", Integer.toString(environmentFiles.size()));
        summary.setProperty("sourceRoots", String.join(";", roots));
        summary.setProperty("javaFiles", Long.toString(report.javaFiles()));
        summary.setProperty("filesWithErrors", Long.toString(report.filesWithErrors()));
        summary.setProperty("bigIntegerCalls", Long.toString(report.bigIntegerCalls()));
        summary.setProperty("extractedRegions", Long.toString(report.extractedRegions()));
        summary.setProperty("supportedRegions", Long.toString(report.supportedRegions()));
        summary.setProperty("candidateRegions", Long.toString(report.candidateRegions()));
        summary.setProperty("rejectedRegions", Long.toString(report.rejectedRegions()));
        summary.setProperty("appliedChanges", "0");
        summary.setProperty("measuredRuntimeImprovements", "0");
        summary.setProperty("elapsedNanos", Long.toString(report.elapsedNanos()));
        summary.setProperty("inputBytesUnchanged", "true");
        writeProperties(output.resolve("summary.properties"), summary);
        return report;
    }

    private static CompilationUnit parse(String source, SourceEnvironment environment) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(environment.unitName());
        parser.setEnvironment(environment.classpath().toArray(String[]::new), environment.sourcepath().toArray(String[]::new), null, true);
        parser.setResolveBindings(true);
        Map<String, String> options = new HashMap<>();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        parser.setCompilerOptions(options);
        return (CompilationUnit) parser.createAST(null);
    }

    private static long bigIntegerCalls(CompilationUnit ast) {
        long[] count = {0};
        ast.accept(new ASTVisitor() {
            @Override public boolean visit(MethodInvocation invocation) {
                var binding = invocation.resolveMethodBinding();
                if (binding != null && !binding.isRecovered() && binding.getDeclaringClass() != null
                        && binding.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")) count[0]++;
                return true;
            }
        });
        return count[0];
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<String> sourceRoots(List<Path> files) throws IOException {
        Set<String> roots = new TreeSet<>();
        for (Path file : files) {
            Path root = file.toAbsolutePath().getParent();
            var matcher = PACKAGE.matcher(Files.readString(file));
            if (matcher.find()) {
                String[] parts = matcher.group(1).split("\\.");
                for (int i = parts.length - 1; i >= 0 && root != null; i--) {
                    if (!root.getFileName().toString().equals(parts[i])) { root = null; break; }
                    root = root.getParent();
                }
            }
            if (root != null) roots.add(root.toString());
        }
        return List.copyOf(roots);
    }

    private static String unitName(Path file, String source) {
        var matcher = PACKAGE.matcher(source);
        return (matcher.find() ? matcher.group(1).replace('.', '/') + '/' : "") + file.getFileName();
    }

    /** Every class in the core and QA packages, including anonymous/nested helper classes. */
    public static Properties implementationReceipt() throws Exception {
        Properties receipt = new Properties();
        receipt.setProperty("schema", "math-implementation/v2-reconstructed");
        receipt.setProperty("javaRuntime", System.getProperty("java.runtime.version"));
        classClosure(receipt, MathematicalAnalysis.class, "org/sandbox/jdt/internal/corext/fix/math/", "core");
        classClosure(receipt, MathCorpusRunner.class, "org/sandbox/math/qa/", "qa");
        Path sdk = Path.of(ComputationOptimizer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isRegularFile(sdk)) throw new IllegalStateException("Qualification requires a frozen SDK jar, not development class directories: " + sdk);
        receipt.setProperty("sdk.codeSource", sdk.toString());
        receipt.setProperty("sdk.codeSourceKind", "frozen-jar");
        receipt.setProperty("sdk.sha256", sha256(Files.readAllBytes(sdk)));
        try (JarFile jar = new JarFile(sdk.toFile())) {
            var provenance = jar.getJarEntry("META-INF/regelsuche/optimization-provenance.json");
            if (provenance != null) try (var in = jar.getInputStream(provenance)) {
                receipt.setProperty("sdk.embeddedProvenance", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return receipt;
    }

    public static void requireUnchangedImplementation(Properties before) throws Exception {
        if (!before.equals(implementationReceipt())) throw new IllegalStateException("Runtime implementation/SDK changed during qualification");
    }

    private static void classClosure(Properties receipt, Class<?> anchor, String prefix, String label) throws Exception {
        Path origin = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        Map<String, byte[]> classes = new TreeMap<>();
        if (Files.isDirectory(origin)) {
            try (var paths = Files.walk(origin.resolve(prefix))) {
                for (Path file : paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".class")).toList())
                    classes.put(origin.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        } else try (JarFile jar = new JarFile(origin.toFile())) {
            for (var entry : jar.stream().filter(entry -> entry.getName().startsWith(prefix) && entry.getName().endsWith(".class")).toList())
                try (var in = jar.getInputStream(entry)) { classes.put(entry.getName(), in.readAllBytes()); }
        }
        if (classes.isEmpty()) throw new IllegalStateException("Empty runtime class closure for " + label);
        receipt.setProperty(label + ".codeSource", origin.toString());
        receipt.setProperty(label + ".classCount", Integer.toString(classes.size()));
        StringBuilder aggregate = new StringBuilder();
        for (var entry : classes.entrySet()) {
            String hash = sha256(entry.getValue());
            receipt.setProperty(label + ".class." + entry.getKey(), hash);
            aggregate.append(entry.getKey()).append('\t').append(hash).append('\n');
        }
        receipt.setProperty(label + ".closureSha256", sha256(aggregate.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /** Exact HEAD plus working-state hashes; dirty builds are recorded as dirty, never described as a clean pin. */
    public static Properties repositoryReceipt() throws Exception {
        Properties receipt = new Properties();
        Path root = Path.of(System.getProperty("math.qa.sandboxRoot", ".")).toAbsolutePath();
        try (var repository = new FileRepositoryBuilder().findGitDir(root.toFile()).build(); var git = new Git(repository);
                var reader = repository.newObjectReader(); var walk = new RevWalk(repository); var bytes = new ByteArrayOutputStream();
                var formatter = new DiffFormatter(bytes)) {
            ObjectId head = repository.resolve("HEAD^{commit}");
            if (head == null) throw new IOException("No Sandbox HEAD found at " + root);
            receipt.setProperty("head", head.name());
            receipt.setProperty("workTree", repository.getWorkTree().getAbsolutePath());
            var status = git.status().call();
            receipt.setProperty("dirty", Boolean.toString(!status.isClean()));
            var old = new CanonicalTreeParser();
            old.reset(reader, walk.parseCommit(head).getTree());
            formatter.setRepository(repository);
            formatter.format(old, new FileTreeIterator(repository));
            formatter.flush();
            receipt.setProperty("headToWorkingTreeDiffSha256", sha256(bytes.toByteArray()));
            Set<String> changed = new TreeSet<>();
            for (Set<String> group : List.of(status.getAdded(), status.getChanged(), status.getRemoved(), status.getMissing(),
                    status.getModified(), status.getConflicting(), status.getUntracked())) changed.addAll(group);
            for (String name : changed) {
                Path file = repository.getWorkTree().toPath().resolve(name);
                receipt.setProperty("changed." + name, Files.isRegularFile(file) ? sha256(Files.readAllBytes(file)) : "DELETED_OR_NONREGULAR");
            }
        }
        return receipt;
    }

    public static void writeProperties(Path file, Properties values) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (var writer = Files.newBufferedWriter(file)) { values.store(writer, "Fresh qualification receipt; not recovered historical output"); }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String tsv(String value) { return value.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n"); }

    private static void configureProxy() {
        if (System.getProperty("https.proxyHost") != null) return;
        String configured = System.getenv("HTTPS_PROXY");
        if (configured != null && !configured.isBlank()) {
            URI proxy = URI.create(configured);
            System.setProperty("https.proxyHost", proxy.getHost());
            System.setProperty("https.proxyPort", Integer.toString(proxy.getPort() < 0 ? 80 : proxy.getPort()));
        }
    }
}
