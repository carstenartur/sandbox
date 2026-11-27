/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.bootstrap;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.util.FileUtils;

/** Provision the unpublished SDK for the Maven repository used by the Eclipse workbench. */
public final class SdkBootstrap {
    private static final String SDK = "regelsuche-optimization-sdk";
    private static final String PARENT = "regelsuche-parent";
    private static final String UPSTREAM = "https://github.com/carstenartur/Regelsuche";
    private static final Set<String> JARS = Set.of(SDK, "regelsuche-core", "regelsuche-egraph",
            "regelsuche-search", "regelsuche-validation", "regelsuche-math-algorithms");

    private SdkBootstrap() {}

    record Receipt(String revision, String tree, String timestamp, String compiler, String version,
            String sdkHash, Map<String, String> jars, Map<String, String> poms) {
        static Receipt read(Path path) throws IOException {
            var json = new ObjectMapper().readTree(path.toFile());
            String revision = json.path("sourceRevision").asText();
            String tree = json.path("sourceTree").asText();
            String version = json.path("version").asText();
            String timestamp = json.path("buildOutputTimestamp").asText();
            String sdkHash = json.path("jarSha256").asText();
            Instant.parse(timestamp);
            if (!UPSTREAM.equals(json.path("sourceRepository").asText()) || !revision.matches("[a-f0-9]{40}")
                    || !tree.matches("[a-f0-9]{40}") || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+-SNAPSHOT")
                    || !("io.github.carstenartur:" + SDK + ":" + version).equals(json.path("artifact").asText())) {
                throw new IOException("Invalid Regelsuche source qualification: " + path);
            }
            var jars = new LinkedHashMap<String, String>();
            for (var entry : json.path("artifacts")) {
                String id = entry.path("artifactId").asText();
                if (jars.put(id, entry.path("sha256").asText()) != null) throw new IOException("Duplicate jar: " + id);
            }
            var poms = new LinkedHashMap<String, String>();
            json.path("pomSha256").properties().forEach(entry -> poms.put(entry.getKey(), entry.getValue().asText()));
            var expectedPoms = new HashSet<>(JARS);
            expectedPoms.add(PARENT);
            if (!JARS.equals(jars.keySet()) || !expectedPoms.equals(poms.keySet()) || !sdkHash.equals(jars.get(SDK))
                    || !jars.values().stream().allMatch(hash -> hash.matches("[0-9a-f]{64}"))
                    || !poms.values().stream().allMatch(hash -> hash.matches("[0-9a-f]{64}"))) {
                throw new IOException("Incomplete or inconsistent Regelsuche artifact qualification: " + path);
            }
            return new Receipt(revision, tree, timestamp, json.path("javaCompiler").asText(), version,
                    sdkHash, jars, poms);
        }

        String baseName(String id) {
            return id + "-" + version;
        }

        Map<String, String> files() {
            var result = new LinkedHashMap<String, String>();
            poms.forEach((id, hash) -> {
                String base = "io/github/carstenartur/" + id + "/" + version + "/" + baseName(id);
                result.put(base + ".pom", hash);
                if (jars.containsKey(id)) result.put(base + ".jar", jars.get(id));
            });
            return result;
        }
    }

    @FunctionalInterface
    interface Acquisition {
        void run() throws Exception;
    }

    static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static void verify(Path path, String hash) throws IOException {
        if (!hash.equals(sha256(path))) throw new IOException("Qualified SHA-256 differs for " + path);
    }

    static void ensure(Path repo, Map<String, String> contents, Acquisition acquire) throws Exception {
        boolean missing = false;
        for (var entry : contents.entrySet()) {
            Path path = repo.resolve(entry.getKey());
            if (Files.notExists(path)) missing = true;
            else verify(path, entry.getValue());
        }
        if (missing) {
            acquire.run();
            for (var entry : contents.entrySet()) verify(repo.resolve(entry.getKey()), entry.getValue());
        }
        System.out.println("Verified qualified Regelsuche jars and descriptors in " + repo);
    }

    static void checkout(Path directory, String remote, String revision, String tree) throws Exception {
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            var repository = git.getRepository();
            var config = repository.getConfig();
            config.setBoolean("core", null, "autocrlf", false);
            config.setString("core", null, "eol", "lf");
            config.save();
            git.fetch().setRemote(remote).setRefSpecs(new RefSpec(revision))
                    .setTagOpt(TagOpt.NO_TAGS).setDepth(1).setTimeout(90).call();
            try (RevWalk walk = new RevWalk(repository)) {
                var commit = walk.parseCommit(ObjectId.fromString(revision));
                if (!tree.equals(commit.getTree().name())) throw new IOException("Fetched tree differs from qualification");
            }
            git.checkout().setName(revision).call();
            if (!revision.equals(repository.resolve(Constants.HEAD).name())) throw new IOException("Detached HEAD differs");
        }
    }

    record Maven(Path sandbox, Path repo, Path settings, Path globalSettings) {
        void run(Path cwd, List<String> goals, Map<String, String> properties, boolean reactor) throws Exception {
            var props = new Properties();
            props.putAll(properties);
            props.setProperty("user.home", System.getProperty("user.home"));
            var request = new DefaultInvocationRequest();
            var wrapper = System.getProperty("os.name").startsWith("Windows") ? "mvnw.cmd" : "mvnw";
            // m2e uses the non-path identifier EMBEDDED as maven.home. The absolute
            // wrapper selects its own distribution, so anchor Invoker to its checkout.
            request.setMavenHome(sandbox.toFile()).setMavenExecutable(sandbox.resolve(wrapper).toFile())
                    .setBaseDirectory(cwd.toFile())
                    .setJavaHome(Path.of(System.getProperty("java.home")).toFile()).setLocalRepositoryDirectory(repo.toFile())
                    .setBatchMode(true).setNoTransferProgress(true).addArgs(goals).setProperties(props)
                    .addShellEnvironment("MAVEN_USER_HOME", Path.of(System.getProperty("user.home"), ".m2").toString());
            request.setTimeoutInSeconds(900);
            if (Files.isRegularFile(settings)) request.setUserSettingsFile(settings.toFile());
            if (Files.isRegularFile(globalSettings)) request.setGlobalSettingsFile(globalSettings.toFile());
            if (reactor) request.setProjects(List.of(SDK)).setAlsoMake(true);
            var result = new DefaultInvoker().execute(request);
            if (result.getExitCode() != 0 || result.getExecutionException() != null) {
                throw new IOException("Regelsuche " + goals + " failed with exit " + result.getExitCode(), result.getExecutionException());
            }
        }
    }

    static void buildAndInstall(Maven maven, Receipt receipt, Path source) throws Exception {
        if (!("Temurin " + Runtime.version().toString().replaceFirst("-LTS$", "")).equals(receipt.compiler())
                || !"Eclipse Adoptium".equals(System.getProperty("java.vendor"))) {
            throw new IOException("Building the qualified SDK requires " + receipt.compiler() + ", current JVM is "
                    + System.getProperty("java.vendor") + " " + Runtime.version());
        }
        for (var entry : receipt.poms().entrySet()) verify(pom(source, entry.getKey()), entry.getValue());
        maven.run(source, List.of("package"), Map.of("skipTests", "true",
                "project.build.outputTimestamp", receipt.timestamp()), true);
        for (var entry : receipt.jars().entrySet()) verify(jar(source, receipt, entry.getKey()), entry.getValue());
        // Only hand artifacts to Maven's installer after every descriptor and binary has matched.
        for (String id : receipt.poms().keySet()) {
            Path pom = pom(source, id);
            Path artifact = PARENT.equals(id) ? pom : jar(source, receipt, id);
            maven.run(maven.sandbox().resolve("qa/regelsuche-sdk-bootstrap"),
                    List.of("org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file"),
                    Map.of("pomFile", pom.toString(), "file", artifact.toString(), "generatePom", "false"), false);
        }
    }

    private static Path pom(Path source, String id) {
        return (PARENT.equals(id) ? source : source.resolve(id)).resolve("pom.xml");
    }

    private static Path jar(Path source, Receipt receipt, String id) {
        return source.resolve(id).resolve("target").resolve(receipt.baseName(id) + ".jar");
    }

    static void removeCheckout(Path source) throws IOException {
        // JGit retries transient pack handles and clears the Windows read-only bit before deleting.
        FileUtils.delete(source.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
    }

    public static void main(String[] args) throws Exception {
        Path sandbox = Path.of(args[0]).toRealPath();
        String override = System.getProperty("bootstrap.workbenchRepository", "");
        Path repo = Files.createDirectories(Path.of(override.isBlank() ? args[1] : override)).toRealPath();
        Receipt receipt = Receipt.read(sandbox.resolve("sandbox_math_cleanup/lib/qualification.json"));
        var maven = new Maven(sandbox, repo, Path.of(args[2]), Path.of(args[3]));
        ensure(repo, receipt.files(), () -> {
            var target = Files.createDirectories(sandbox.resolve("target"));
            var source = Files.createTempDirectory(target, "qualified-sdk-");
            try {
                checkout(source, UPSTREAM, receipt.revision(), receipt.tree());
                buildAndInstall(maven, receipt, source);
            } finally {
                Git.shutdown();
                removeCheckout(source);
            }
        });
    }
}
