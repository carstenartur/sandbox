/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import static org.sandbox.distribution.AggregateInstallationEvidence.children;
import static org.sandbox.distribution.AggregateInstallationEvidence.require;
import static org.sandbox.distribution.AggregateInstallationEvidence.xml;
import static org.sandbox.distribution.DistributionVerifier.elements;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.osgi.framework.Version;
import org.w3c.dom.Element;

/** Inventory of the exact payload and the two feature archives that deliver it. */
record SupportArtifacts(Artifact support, Artifact component, Artifact aggregate, Artifact binding) {
    static final String BSN = "org.sandbox.junit.migration.support";
    static final String BINDING = "sandbox_junit_cleanup_jdt5";
    static final String COMPONENT = "sandbox_junit_cleanup_feature";
    static final String AGGREGATE = "sandbox_feature";
    static final String GROUP = ".feature.group";
    static final String IU = "org.eclipse.equinox.p2.iu";

    record Artifact(Version version, Path path, String sha256) {
        static Artifact read(String version, Path path) throws Exception {
            return new Artifact(new Version(version), path.toRealPath(), AggregateInstallationVerifier.digest(path));
        }

        void requireSameBytes(Path other) throws Exception {
            String actual = AggregateInstallationVerifier.digest(other);
            require(sha256.equals(actual), "Artifact mismatch " + path + " " + sha256 + " versus " + other + " " + actual);
        }
    }

    static SupportArtifacts read(Path repository, boolean allowAbsent) throws Exception {
        var units = elements(DistributionVerifier.repositoryXml(repository, "content"), "unit");
        var keys = elements(DistributionVerifier.repositoryXml(repository, "artifacts"), "artifact");
        var supportUnits = named(units, "id", BSN);
        var supportKeys = named(keys, "id", BSN);
        var componentUnit = one(units, "id", COMPONENT + GROUP);
        var aggregateUnit = one(units, "id", AGGREGATE + GROUP);
        Artifact component = artifact(repository, componentUnit, COMPONENT, keys, true);
        Artifact aggregate = artifact(repository, aggregateUnit, AGGREGATE, keys, true);
        requireMember(aggregateUnit, aggregate, COMPONENT, component.version());
        List<Path> payloadFiles;
        try (var files = Files.list(repository.resolve("plugins"))) {
            payloadFiles = files.filter(p -> {
                String name = AggregateInstallationEvidence.fileName(p);
                return (name.startsWith(BSN + "_") || name.startsWith(BINDING + "_")) && name.endsWith(".jar");
            }).toList();
        }
        if (allowAbsent && supportUnits.isEmpty()) {
            require(supportKeys.isEmpty() && payloadFiles.isEmpty(), "Orphan prior support artifact in " + repository);
            require(named(units, "id", BINDING).isEmpty() && named(keys, "id", BINDING).isEmpty(), "Orphan prior JDT 5 fragment");
            requireMember(componentUnit, component, BSN, null);
            requireMember(componentUnit, component, BINDING, null);
            return new SupportArtifacts(null, component, aggregate, null);
        }
        var unit = one(units, "id", BSN);
        Artifact bundle = artifact(repository, unit, BSN, keys, false);
        checkKey(one(nested(unit, "artifacts", "artifact"), "id", BSN), "osgi.bundle", bundle.version().toString());
        requireMember(componentUnit, component, BSN, bundle.version());
        requireBundle(bundle.path(), bundle.version());
        Element fragment = one(units, "id", BINDING);
        var binding = artifact(repository, fragment, BINDING, keys, false);
        checkKey(one(nested(fragment, "artifacts", "artifact"), "id", BINDING), "osgi.bundle", binding.version().toString());
        requireMember(componentUnit, component, BINDING, binding.version());
        requireBundle(binding.path(), BINDING, binding.version());
        require(payloadFiles.size() == 2, "Ambiguous support or binding files " + payloadFiles);
        return new SupportArtifacts(bundle, component, aggregate, binding);
    }

    static void requireBundle(Path path, Version version) throws Exception {
        requireBundle(path, BSN, version);
    }

    private static void requireBundle(Path path, String id, Version version) throws Exception {
        try (var jar = new JarFile(path.toFile())) {
            var manifest = jar.getManifest();
            require(manifest != null, "Missing support manifest at " + path);
            var attributes = manifest.getMainAttributes();
            String name = attributes.getValue("Bundle-SymbolicName"), actual = attributes.getValue("Bundle-Version");
            require(name != null && id.equals(name.split(";")[0]) && version.toString().equals(actual),
                    "Expected " + id + '/' + version + " at " + path + ", found " + name + '/' + actual);
            if (BINDING.equals(id)) {
                String host = attributes.getValue("Fragment-Host");
                require(host != null && BSN.equals(host.split(";")[0].strip()), "Incorrect JDT 5 host at " + path);
            }
        }
    }

    private static Artifact artifact(Path repo, Element unit, String id, List<Element> keys, boolean feature) throws Exception {
        String version = unit.getAttribute("version");
        checkKey(one(keys, "id", id), feature ? "org.eclipse.update.feature" : "osgi.bundle", version);
        Path directory = repo.toRealPath().resolve(feature ? "features" : "plugins");
        Path path = directory.resolve(id + '_' + version + ".jar");
        require(path.equals(path.toRealPath()), "Artifact is outside its repository location: " + path);
        return Artifact.read(version, path);
    }

    private static void checkKey(Element key, String classifier, String version) throws Exception {
        require(classifier.equals(key.getAttribute("classifier")) && version.equals(key.getAttribute("version")), "Wrong artifact key " + key.getAttribute("id"));
    }

    private static void requireMember(Element group, Artifact feature, String member, Version version) throws Exception {
        boolean includes = COMPONENT.equals(member);
        String id = includes ? member + GROUP : member;
        var dependencies = named(nested(group, "requires", "required"), "name", id);
        try (var jar = new JarFile(feature.path().toFile()); var in = jar.getInputStream(jar.getJarEntry("feature.xml"))) {
            Element definition = xml(in);
            require((definition.getAttribute("id") + GROUP).equals(group.getAttribute("id"))
                    && feature.version().toString().equals(definition.getAttribute("version")), "Feature identity differs at " + feature.path());
            var members = named(children(definition, includes ? "includes" : "plugin"), "id", member);
            if (version == null) {
                require(members.isEmpty() && dependencies.isEmpty(), "Prior feature references missing support");
                return;
            }
            require(members.size() == 1 && version.toString().equals(members.getFirst().getAttribute("version"))
                    && !"true".equals(members.getFirst().getAttribute("optional")), "Wrong feature member " + id);
            for (String selector : List.of("os", "ws", "arch", "nl", "filter")) {
                require(members.getFirst().getAttribute(selector).isBlank(), "Conditional feature member " + id + ": " + selector);
            }
            require(dependencies.size() == 1, "Ambiguous or missing feature dependency " + id);
            Element dep = dependencies.getFirst();
            require(children(dep, "filter").isEmpty() && dep.getAttribute("filter").isBlank(), "Conditional p2 dependency " + id);
            require(IU.equals(dep.getAttribute("namespace")) && ("[" + version + "," + version + "]").equals(dep.getAttribute("range"))
                    && !"true".equals(dep.getAttribute("optional")) && !"0".equals(dep.getAttribute("min")), "Expected exact mandatory dependency " + id + '/' + version);
        }
    }

    static Path installed(String id, Artifact expected, Path home, Path reported, AggregateInstallationEvidence.Profile profile) throws Exception {
        require(Set.of(expected.version().toString()).equals(profile.units().get(id)) && !profile.roots().contains(id),
                "Unexpected installed support versions or root " + profile.units().get(id));
        Path installed = reported.toRealPath();
        require(!profile.id().isBlank() && !profile.bundlePool().isBlank(), "Missing p2 profile identity or bundle pool");
        Path pool = Path.of(profile.bundlePool()).toRealPath();
        require(pool.equals(home.toRealPath()), "Expected a private bundle pool in " + home + ", found " + pool);
        Path plugins = pool.resolve("plugins");
        require(installed.equals(plugins.resolve(id + '_' + expected.version() + ".jar")),
                "Support file outside selected installation: " + installed + ", expected in " + plugins);
        requireBundle(installed, id, expected.version());
        expected.requireSameBytes(installed);
        return installed;
    }

    private static List<Element> nested(Element parent, String container, String child) {
        return children(parent, container).stream().flatMap(group -> children(group, child).stream()).toList();
    }

    private static List<Element> named(List<Element> list, String attr, String value) {
        return list.stream().filter(e -> value.equals(e.getAttribute(attr))).toList();
    }

    private static Element one(List<Element> list, String attr, String value) throws Exception {
        var matches = named(list, attr, value);
        require(matches.size() == 1, "Expected exactly one " + value + ", found " + matches.size());
        return matches.getFirst();
    }
}
