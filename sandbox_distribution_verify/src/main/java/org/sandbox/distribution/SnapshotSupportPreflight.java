/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.sandbox.distribution.AggregateInstallationEvidence.require;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Validates the checked-out Pages site and the staged candidate before Pages mutation. */
public final class SnapshotSupportPreflight {
    static final String RECEIPT = "support-receipt.json";
    private static final String METADATA_PREFIX = "sandbox_updatesite/target/repository/";
    private static final Set<String> SIMPLE_METADATA = Set.of("content.jar", "artifacts.jar", "content.xml", "artifacts.xml",
            "content.xml.xz", "artifacts.xml.xz", "p2.index");

    record Receipt(String sourceRef, String sourceSHA, long firstParentHeight, String sourceCiRunId,
            String bsn, String version, String sha256, String componentVersion, String componentSha256,
            String aggregateVersion, String aggregateSha256, String bindingVersion, String bindingSha256) {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), prior = Path.of(args[1]), next = Path.of(args[2]), evidence = Path.of(args[3]);
        String expected = System.getenv("VALIDATED_SHA");
        var nextSource = SnapshotSource.selected(root, expected);
        var nextManifest = verifiedManifest(next, root.resolve("build-manifest.json"));
        var priorManifest = verifiedManifest(prior, prior.resolve("build-manifest.json"));
        require("refs/heads/main".equals(nextManifest.get("ref").getAsString())
                && "refs/heads/main".equals(priorManifest.get("ref").getAsString()), "Unexpected snapshot source ref");
        var candidate = SupportArtifacts.read(next, false);
        var previous = SupportArtifacts.read(prior, true);
        require(expected.equals(nextManifest.get("commit_sha").getAsString()), "Candidate manifest differs from selected source " + expected);
        Receipt receipt = receipt(nextManifest, nextSource, candidate);
        require(receipt.sourceCiRunId().equals(System.getenv("SOURCE_JAVA_CI_RUN_ID")), "Candidate manifest CI run differs from selected run");
        String oldSource = priorManifest.get("commit_sha").getAsString();
        var previousSource = SnapshotSource.revision(root, oldSource);
        SnapshotSource.requireLineage(root, previousSource, nextSource);
        if (previous.support() != null) {
            verifyReceipt(prior, receipt(priorManifest, previousSource, previous));
        } else {
            require(!Files.exists(prior.resolve(RECEIPT)), "Support receipt without prior support IU");
        }
        System.out.println("Checking snapshot update from " + oldSource + " to " + expected);
        compare(expected.equals(oldSource), previous, candidate);
        verifyBuild(evidence, candidate);
        String json = new Gson().toJson(receipt) + "\n";
        Files.writeString(evidence.resolve(RECEIPT), json);
        Files.writeString(next.resolve(RECEIPT), json);
        System.out.print(json);
    }

    static void verifyBuild(Path evidence, SupportArtifacts candidate) throws Exception {
        var installed = new Properties();
        try (var in = Files.newInputStream(evidence.resolve("aggregate/verification.properties"))) { installed.load(in); }
        require("PASS".equals(installed.getProperty("status")), "Missing installed distribution proof");
        require(candidate.support().version().toString().equals(installed.getProperty("support.version")), "Verified support version differs from candidate");
        for (var location : List.of("support.maven", "support.p2", "fresh.support", "product.support", "support-after.support")) {
            require(candidate.support().sha256().equals(installed.getProperty(location + ".sha256")), "Candidate differs from verified " + location);
        }
        var binding = candidate.binding();
        Path bindingFile = Path.of(SupportArtifacts.BINDING + '_' + binding.version() + ".jar");
        for (String stage : List.of("product", "fresh", "support-after", "updated", "upgraded", "migrated")) {
            var observed = json(evidence.resolve("aggregate/" + stage + "-support.json"));
            require(binding.version().toString().equals(observed.get("fragmentVersion").getAsString())
                    && bindingFile.equals(Path.of(observed.get("fragmentPath").getAsString()).getFileName())
                    && binding.sha256().equals(observed.get("fragmentSha256").getAsString()), "Candidate fragment differs from verified " + stage);
        }
        var build = json(evidence.resolve("verification.json"));
        require("PASS".equals(build.get("result").getAsString()), "Missing passing distribution verification");
        var features = build.getAsJsonObject("repository").getAsJsonArray("publishedFeatures");
        requireVerifiedFeature(features, SupportArtifacts.COMPONENT, candidate.component());
        requireVerifiedFeature(features, SupportArtifacts.AGGREGATE, candidate.aggregate());
    }

    private static void requireVerifiedFeature(JsonArray features, String id, SupportArtifacts.Artifact staged) throws Exception {
        var matches = features.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(feature -> id.equals(feature.get("id").getAsString())).toList();
        require(matches.size() == 1, "Expected one verified feature " + id);
        var feature = matches.getFirst();
        require(staged.version().toString().equals(feature.get("version").getAsString())
                && staged.sha256().equals(feature.get("artifactSha256").getAsString()), "Candidate differs from verified feature " + id);
    }

    static void verifyReceipt(Path repository, Receipt actual) throws Exception {
        var stored = new Gson().fromJson(Files.readString(repository.resolve(RECEIPT)), Receipt.class);
        require(actual.equals(stored), "Published support receipt differs: expected " + actual + ", found " + stored);
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    static JsonObject verifiedManifest(Path repository, Path original) throws Exception {
        Path staged = repository.resolve("build-manifest.json");
        require(Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS)
                && Files.mismatch(original, staged) == -1, "Staged manifest differs from the original build manifest at " + original);
        var manifest = json(original);
        var entries = manifest.getAsJsonObject("artifacts").getAsJsonObject("update_site").getAsJsonArray("metadata");
        Set<String> recorded = new HashSet<>();
        for (JsonElement item : entries) {
            var entry = item.getAsJsonObject();
            String path = entry.get("path").getAsString();
            require(path.startsWith(METADATA_PREFIX), "Unexpected repository metadata path " + path);
            String name = path.substring(METADATA_PREFIX.length());
            require(SIMPLE_METADATA.contains(name) && recorded.add(name), "Unknown or duplicate repository metadata " + path);
            Path file = repository.resolve(name);
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && Long.toString(Files.size(file)).equals(entry.get("size_bytes").getAsString())
                    && AggregateInstallationVerifier.digest(file).equals(entry.get("sha256").getAsString()), "Repository metadata differs at " + file);
        }
        try (var files = Files.list(repository)) {
            var actual = files.map(AggregateInstallationEvidence::fileName)
                    .filter(name -> SIMPLE_METADATA.contains(name) || name.matches("(composite(Content|Artifacts)|content|artifacts)\\.(jar|xml|xml\\.xz|xz)"))
                    .collect(Collectors.toSet());
            require(recorded.equals(actual), "Repository metadata inventory differs at " + repository + ": " + recorded + " versus " + actual);
        }
        for (String stem : List.of("content", "artifacts")) {
            require(recorded.contains(stem + ".jar") || recorded.contains(stem + ".xml"), "Missing repository metadata for " + stem);
        }
        return manifest;
    }

    static Receipt receipt(JsonObject manifest, SnapshotSource.Revision source, SupportArtifacts site) throws Exception {
        String sha = manifest.get("commit_sha").getAsString();
        require(source.sha().equals(sha), "Unexpected source " + sha);
        long height = source.height();
        require(SnapshotSource.qualifier(height, sha).equals(site.support().version().getQualifier()), "Wrong support qualifier for " + sha + ": " + site.support().version());
        String run = manifest.getAsJsonObject("provenance_workflow").get("source_java_ci_run_id").getAsString();
        require(run.matches("[1-9][0-9]*"), "Missing source CI run for " + sha);
        return new Receipt(manifest.get("ref").getAsString(), sha, height, run, SupportArtifacts.BSN, site.support().version().toString(),
                site.support().sha256(), site.component().version().toString(), site.component().sha256(), site.aggregate().version().toString(), site.aggregate().sha256(),
                site.binding().version().toString(), site.binding().sha256());
    }

    static void compare(boolean retry, SupportArtifacts previous, SupportArtifacts candidate) throws Exception {
        var old = previous.support();
        var next = candidate.support();
        require(next != null, "Missing candidate support");
        if (old == null) {
            require(!retry, "Initial support introduction requires a newer source");
        } else if (retry) {
            require(old.version().equals(next.version()) && old.sha256().equals(next.sha256()), "Same-source support mismatch: " + old + " versus " + next);
        } else {
            require(next.version().compareTo(old.version()) > 0, "Support version did not advance: " + old + " versus " + next);
        }
        compareFeature(retry, previous.component(), candidate.component());
        compareFeature(retry, previous.aggregate(), candidate.aggregate());
        // A shared binding at an existing identity must remain byte-for-byte immutable.
        if (old != null) compareFeature(true, previous.binding(), candidate.binding());
    }

    private static void compareFeature(boolean allowIdentical, SupportArtifacts.Artifact old, SupportArtifacts.Artifact next) throws Exception {
        int order = next.version().compareTo(old.version());
        require(order > 0 || (allowIdentical && order == 0 && next.sha256().equals(old.sha256())), "Feature collision or regression: " + old + " versus " + next);
    }

    private SnapshotSupportPreflight() {}
}
