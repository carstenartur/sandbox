package org.sandbox.distribution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Cross-platform Maven distribution verification.
 *
 * <p>This verifier intentionally uses only Java APIs. It is executed as the
 * final module of the {@code distribution} Maven profile after the product and
 * update-site modules have completed.</p>
 */
public final class DistributionVerifier {
    private static final Pattern RELEASE_REPOSITORY =
            Pattern.compile("/releases/(\\d{4}-\\d{2})/?$");
    private static final Pattern ORBIT_REPOSITORY =
            Pattern.compile("/orbit-aggregation/(\\d{4}-\\d{2})/?$");
    private static final Pattern HEX_64 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern HEX_32 = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Set<String> BOUNCY_CASTLE_IDS =
            Set.of("bcutil", "bcprov", "bcpkix", "bcpg");

    private final Path root;
    private final Path evidence;
    private final Platform platform;

    private DistributionVerifier(Path root) throws VerificationException {
        this.root = root.toAbsolutePath().normalize();
        this.evidence = this.root.resolve("target/distribution-verification");
        this.platform = Platform.current();
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        new DistributionVerifier(root).verify();
    }

    private void verify() throws Exception {
        Files.createDirectories(evidence);

        Model model = verifyModel();
        RepositoryEvidence repository = verifyRepository(model.publishedFeatures());
        ProductEvidence product = verifyProduct(model.publishedFeatures());

        verifyInstalledRoots(product, "materialized-product.log");
        verifyDefaultIdeStart(product);
        FreshInstallation fresh = verifyFreshInstallation(model, product);
        verifyCleanupApplication(fresh);

        writeEvidence(model, repository, product);
        System.out.printf(
                "Sandbox distribution verified for %s: %d features, %d p2 artifacts, %d product bundles.%n",
                platform, model.publishedFeatures().size(), repository.artifactFilesChecked(),
                product.pluginCount());
    }

    private Model verifyModel() throws Exception {
        Document pom = parseXml(root.resolve("pom.xml"));
        Document target = parseXml(root.resolve("sandbox_target/eclipse.target"));
        Document product = parseXml(root.resolve("sandbox_product/sandbox.product"));
        Document category = parseXml(root.resolve("sandbox_updatesite/category.xml"));
        Document productPom = parseXml(root.resolve("sandbox_product/pom.xml"));
        Document updateSitePom = parseXml(root.resolve("sandbox_updatesite/pom.xml"));

        List<String> pomRepositories = childTexts(pom, "repository", "url").stream()
                .filter(url -> "p2".equals(repositoryLayout(pom, url)))
                .toList();
        List<String> targetRepositories = attributeValues(target, "repository", "location");
        List<String> productRepositories = directChildAttributeValues(
                product.getDocumentElement(), "repositories", "repository", "location");

        String pomRelease = singleRepositoryVersion(pomRepositories, RELEASE_REPOSITORY, "Maven Eclipse");
        String targetRelease = singleRepositoryVersion(targetRepositories, RELEASE_REPOSITORY, "target Eclipse");
        String productRelease = singleRepositoryVersion(productRepositories, RELEASE_REPOSITORY, "product Eclipse");

        String oomph = Files.readString(root.resolve("sandbox_oomph/sandbox.setup"));
        Matcher oomphMatcher = Pattern.compile(
                "name=\"eclipse\\.target\\.version\".*?defaultValue=\"(\\d{4}-\\d{2})\"",
                Pattern.DOTALL).matcher(oomph);
        require(oomphMatcher.find(), "Missing eclipse.target.version in Oomph setup");
        String oomphRelease = oomphMatcher.group(1);

        Map<String, String> releases = Map.of(
                "pom.xml", pomRelease,
                "sandbox_target/eclipse.target", targetRelease,
                "sandbox_product/sandbox.product", productRelease,
                "sandbox_oomph/sandbox.setup", oomphRelease);
        require(new HashSet<>(releases.values()).size() == 1,
                "Eclipse release repositories are inconsistent: " + releases);

        String pomOrbit = singleRepositoryVersion(pomRepositories, ORBIT_REPOSITORY, "Maven Orbit");
        String targetOrbit = singleRepositoryVersion(targetRepositories, ORBIT_REPOSITORY, "target Orbit");
        require(pomOrbit.equals(targetOrbit),
                "Orbit repositories are inconsistent: pom.xml=" + pomOrbit + ", target=" + targetOrbit);

        String mavenBc = requiredFirstText(pom, "bouncycastle.version");
        String osgiBc = normalizeOsgiVersion(mavenBc);
        Map<String, String> targetBc = new LinkedHashMap<>();
        for (Element unit : elements(target, "unit")) {
            String id = unit.getAttribute("id");
            if (BOUNCY_CASTLE_IDS.contains(id)) {
                targetBc.put(id, unit.getAttribute("version"));
            }
        }
        require(targetBc.keySet().equals(BOUNCY_CASTLE_IDS),
                "Target Bouncy Castle units are incomplete: " + targetBc);
        require(new HashSet<>(targetBc.values()).equals(Set.of(osgiBc)),
                "Bouncy Castle Maven version " + mavenBc + " does not match target units " + targetBc);

        Map<String, String> pomBc = new LinkedHashMap<>();
        for (Element requirement : elements(pom, "requirement")) {
            String id = directChildText(requirement, "id").orElse("");
            if (BOUNCY_CASTLE_IDS.contains(id)) {
                pomBc.put(id, directChildText(requirement, "versionRange")
                        .orElseThrow(() -> new VerificationException("Missing Bouncy Castle versionRange for " + id)));
            }
        }
        require(pomBc.equals(targetBc),
                "Bouncy Castle Maven extra requirements do not match target units: "
                        + "expected " + targetBc + ", found " + pomBc);

        Element productElement = product.getDocumentElement();
        require("org.eclipse.ui.ide.workbench".equals(productElement.getAttribute("application")),
                "Standalone product must use org.eclipse.ui.ide.workbench, found "
                        + productElement.getAttribute("application"));
        require("true".equals(productElement.getAttribute("includeLaunchers")),
                "Standalone product must include native launchers");

        Set<String> productFeatures = directChildAttributeValues(
                productElement, "features", "feature", "id").stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> requiredBase = Set.of(
                "org.eclipse.platform",
                "org.eclipse.jdt",
                "org.eclipse.pde",
                "org.eclipse.equinox.p2.user.ui",
                "org.eclipse.egit",
                "org.eclipse.jgit");
        require(productFeatures.containsAll(requiredBase),
                "Standalone product is missing base features: " + difference(requiredBase, productFeatures));

        Set<String> publishedFeatures = directChildAttributeValues(
                category.getDocumentElement(), null, "feature", "id").stream()
                .filter(id -> id.startsWith("sandbox_"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        require(!publishedFeatures.isEmpty(), "Update site declares no Sandbox features");

        Set<String> productSandbox = productFeatures.stream()
                .filter(id -> id.startsWith("sandbox_"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        require(productSandbox.equals(publishedFeatures),
                "Product and update-site Sandbox features differ: product-only="
                        + difference(productSandbox, publishedFeatures)
                        + ", update-site-only=" + difference(publishedFeatures, productSandbox));

        verifyDeliveryDependencies(productPom, publishedFeatures, "sandbox_product/pom.xml");
        verifyDeliveryDependencies(updateSitePom, publishedFeatures, "sandbox_updatesite/pom.xml");

        Set<String> distributionModules = profileModules(pom, "distribution");
        Set<String> requiredModules = Set.of(
                "sandbox_product", "sandbox_updatesite", "sandbox_distribution_verify");
        require(distributionModules.equals(requiredModules),
                "The distribution profile must contain product, update site and verifier: "
                        + distributionModules);

        return new Model(
                pomRelease,
                pomOrbit,
                mavenBc,
                List.copyOf(publishedFeatures),
                targetRepositories);
    }

    private String repositoryLayout(Document pom, String url) {
        for (Element repository : elements(pom, "repository")) {
            String candidate = directChildText(repository, "url").orElse("");
            if (candidate.equals(url)) {
                return directChildText(repository, "layout").orElse("");
            }
        }
        return "";
    }

    private void verifyDeliveryDependencies(
            Document pom, Set<String> publishedFeatures, String label) throws VerificationException {
        Set<String> dependencies = directChildren(pom.getDocumentElement(), "dependencies", "dependency").stream()
                .map(element -> directChildText(element, "artifactId").orElse(""))
                .filter(id -> id.startsWith("sandbox_") && id.endsWith("_feature"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        require(dependencies.equals(publishedFeatures),
                label + " does not declare the complete feature graph: missing="
                        + difference(publishedFeatures, dependencies)
                        + ", extra=" + difference(dependencies, publishedFeatures));
    }

    private RepositoryEvidence verifyRepository(List<String> publishedFeatures) throws Exception {
        Path repository = root.resolve("sandbox_updatesite/target/repository");
        require(Files.isDirectory(repository), "Built p2 repository does not exist: " + repository);

        Document content = repositoryXml(repository, "content");
        Document artifacts = repositoryXml(repository, "artifacts");

        Map<String, String> units = new HashMap<>();
        for (Element unit : elements(content, "unit")) {
            String id = unit.getAttribute("id");
            if (!id.isBlank()) {
                units.put(id, unit.getAttribute("version"));
            }
        }
        for (String feature : publishedFeatures) {
            require(units.containsKey(feature + ".feature.group"),
                    "Published feature IU missing: " + feature + ".feature.group");
        }

        Set<String> keys = new HashSet<>();
        Map<String, ArtifactEvidence> featureArtifacts = new HashMap<>();
        int filesChecked = 0;
        int checksumsChecked = 0;
        for (Element artifact : elements(artifacts, "artifact")) {
            String classifier = artifact.getAttribute("classifier");
            String id = artifact.getAttribute("id");
            String version = artifact.getAttribute("version");
            String key = classifier + ":" + id + ":" + version;
            require(keys.add(key), "Duplicate p2 artifact key: " + key);

            Path file = artifactPath(repository, classifier, id, version);
            if (file == null) {
                continue;
            }
            filesChecked++;
            require(Files.isRegularFile(file), "p2 metadata references missing file: " + file);

            Map<String, String> properties = properties(artifact);
            String size = Optional.ofNullable(properties.get("download.size"))
                    .orElse(properties.get("artifact.size"));
            if (size != null && size.chars().allMatch(Character::isDigit)) {
                require(Files.size(file) == Long.parseLong(size),
                        "Artifact size mismatch for " + file.getFileName());
            }

            String sha256 = digest(file, "SHA-256");
            for (Map.Entry<String, String> property : properties.entrySet()) {
                String name = property.getKey().toLowerCase(Locale.ROOT);
                String value = property.getValue();
                if (HEX_64.matcher(value).matches()
                        && (name.contains("sha-256") || name.contains("sha256"))) {
                    checksumsChecked++;
                    require(sha256.equalsIgnoreCase(value),
                            "SHA-256 mismatch for " + file.getFileName());
                } else if (HEX_32.matcher(value).matches() && name.contains("md5")) {
                    checksumsChecked++;
                    require(digest(file, "MD5").equalsIgnoreCase(value),
                            "MD5 mismatch for " + file.getFileName());
                }
            }

            if ("org.eclipse.update.feature".equals(classifier) && publishedFeatures.contains(id)) {
                featureArtifacts.put(id + ":" + version,
                        new ArtifactEvidence(id, version, Files.size(file), sha256));
            }
        }
        require(filesChecked > 0, "No p2 bundle or feature artifacts were checked");

        for (String feature : publishedFeatures) {
            String version = units.get(feature + ".feature.group");
            require(featureArtifacts.containsKey(feature + ":" + version),
                    "Feature artifact missing for exact IU version: " + feature + "/" + version);
        }

        return new RepositoryEvidence(units.size(), keys.size(), filesChecked, checksumsChecked);
    }

    private ProductEvidence verifyProduct(List<String> publishedFeatures) throws Exception {
        Path productRoot = findProductRoot();
        Path launcher = findNativeLauncher(productRoot);

        List<Path> plugins;
        try (Stream<Path> stream = Files.list(productRoot.resolve("plugins"))) {
            plugins = stream.filter(this::isBundle).sorted().toList();
        }
        require(!plugins.isEmpty(), "Materialized product contains no plug-ins: " + productRoot);

        Map<String, List<String>> singletonVersions = new HashMap<>();
        for (Path plugin : plugins) {
            Manifest manifest = readManifest(plugin);
            if (manifest == null) {
                continue;
            }
            String declaration = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
            if (declaration == null || declaration.isBlank()) {
                continue;
            }
            String symbolicName = declaration.split(";", 2)[0].trim();
            if (declaration.replace(" ", "").contains("singleton:=true")) {
                String version = Optional.ofNullable(
                        manifest.getMainAttributes().getValue("Bundle-Version")).orElse("");
                singletonVersions.computeIfAbsent(symbolicName, unused -> new ArrayList<>())
                        .add(version + "@" + plugin.getFileName());
            }
        }
        Map<String, List<String>> duplicates = singletonVersions.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        require(duplicates.isEmpty(), "Duplicate singleton bundles in product: " + duplicates);

        Path featuresDirectory = productRoot.resolve("features");
        for (String feature : publishedFeatures) {
            try (Stream<Path> stream = Files.list(featuresDirectory)) {
                long count = stream.filter(path -> path.getFileName().toString().startsWith(feature + "_"))
                        .count();
                require(count == 1, "Expected exactly one installed " + feature + " feature, found " + count);
            }
        }

        Properties config = loadProperties(productRoot.resolve("configuration/config.ini"));
        require("org.eclipse.ui.ide.workbench".equals(config.getProperty("eclipse.application")),
                "Materialized product default application is not the Eclipse IDE workbench");

        return new ProductEvidence(productRoot, launcher, plugins.size(), singletonVersions.size());
    }

    private void verifyInstalledRoots(ProductEvidence product, String logName) throws Exception {
        Path workspace = evidence.resolve("materialized-product-data");
        ProcessResult result = run(
                List.of(
                        product.launcher().toString(),
                        "-nosplash",
                        "-consoleLog",
                        "-application", "org.eclipse.equinox.p2.director",
                        "-listInstalledRoots",
                        "-data", workspace.toString()),
                product.root(),
                evidence.resolve(logName),
                Duration.ofMinutes(5));
        require(result.exitCode() == 0, "Materialized product p2 director failed; see " + logName);
        String output = Files.readString(evidence.resolve(logName));
        require(output.contains("org.eclipse.") || output.contains("sandbox_"),
                "Materialized product did not list installed roots");
    }

    private void verifyDefaultIdeStart(ProductEvidence product) throws Exception {
        Path log = evidence.resolve("ide-workbench.log");
        ProcessResult result = run(
                List.of(
                        product.launcher().toString(),
                        "-nosplash",
                        "-consoleLog",
                        "-data", evidence.resolve("ide-workspace").toString()),
                product.root(),
                log,
                Duration.ofSeconds(30));
        require(result.timedOut(),
                "Default Eclipse IDE exited before the 30-second smoke-test window; see " + log);
        String output = Files.readString(log);
        require(!containsFatalApplicationError(output),
                "Default Eclipse IDE reported an application error; see " + log);
    }

    private FreshInstallation verifyFreshInstallation(Model model, ProductEvidence product)
            throws Exception {
        Path fresh = evidence.resolve("fresh-install");
        deleteRecursively(fresh);
        Files.createDirectories(fresh);

        List<String> roots = new ArrayList<>();
        roots.add("org.eclipse.sdk.ide");
        roots.add("org.eclipse.equinox.p2.extras.feature.feature.group");
        roots.add("org.eclipse.equinox.executable.feature.group");
        roots.addAll(model.publishedFeatures().stream()
                .map(feature -> feature + ".feature.group").toList());

        List<String> repositories = new ArrayList<>();
        repositories.add(root.resolve("sandbox_updatesite/target/repository").toUri().toString());
        repositories.addAll(model.targetRepositories());

        Path installLog = evidence.resolve("fresh-install.log");
        ProcessResult install = run(
                List.of(
                        product.launcher().toString(),
                        "-nosplash",
                        "-consoleLog",
                        "-application", "org.eclipse.equinox.p2.director",
                        "-repository", String.join(",", repositories),
                        "-installIU", String.join(",", roots),
                        "-destination", fresh.toString(),
                        "-bundlepool", fresh.toString(),
                        "-profile", "SandboxDistributionSmoke",
                        "-profileProperties", "org.eclipse.update.install.features=true",
                        "-p2.os", platform.osgiOs(),
                        "-p2.ws", platform.osgiWs(),
                        "-p2.arch", platform.osgiArch(),
                        "-roaming",
                        "-vmargs",
                        "-Declipse.p2.mirrors=false"),
                product.root(),
                installLog,
                Duration.ofMinutes(15));
        require(install.exitCode() == 0, "Fresh p2 installation failed; see " + installLog);

        Path freshLauncher = findNativeLauncher(fresh);
        FreshInstallation installation = new FreshInstallation(fresh, freshLauncher);

        ProcessResult rootsResult = run(
                List.of(
                        freshLauncher.toString(),
                        "-nosplash",
                        "-consoleLog",
                        "-application", "org.eclipse.equinox.p2.director",
                        "-listInstalledRoots",
                        "-data", evidence.resolve("fresh-data").toString()),
                fresh,
                evidence.resolve("fresh-product.log"),
                Duration.ofMinutes(5));
        require(rootsResult.exitCode() == 0, "Fresh installation did not start p2 director");
        String output = Files.readString(evidence.resolve("fresh-product.log"));
        for (String feature : model.publishedFeatures()) {
            require(output.contains(feature),
                    "Fresh installation does not report published feature root " + feature);
        }
        return installation;
    }

    private void verifyCleanupApplication(FreshInstallation fresh) throws Exception {
        Path workspace = evidence.resolve("cleanup-workspace");
        Path project = workspace.resolve("SmokeProject");
        Path source = project.resolve("src/smoke/Smoke.java");
        Path config = evidence.resolve("distribution-smoke.properties");
        Path report = evidence.resolve("cleanup-report.json");

        deleteRecursively(workspace);
        Files.createDirectories(source.getParent());
        Files.createDirectories(project.resolve("bin"));

        Files.writeString(project.resolve(".project"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <projectDescription>
                  <name>SmokeProject</name>
                  <comment></comment>
                  <projects></projects>
                  <buildSpec>
                    <buildCommand>
                      <name>org.eclipse.jdt.core.javabuilder</name>
                      <arguments></arguments>
                    </buildCommand>
                  </buildSpec>
                  <natures>
                    <nature>org.eclipse.jdt.core.javanature</nature>
                  </natures>
                </projectDescription>
                """);
        Files.writeString(project.resolve(".classpath"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <classpath>
                  <classpathentry kind="src" path="src"/>
                  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
                  <classpathentry kind="output" path="bin"/>
                </classpath>
                """);
        Files.writeString(config, """
                cleanup.format_source_code=true
                cleanup.format_source_code_changes_only=false
                """);
        String before = "package smoke;\npublic class Smoke{public String value(){return \"smoke\";}}\n";
        Files.writeString(source, before);

        Path log = evidence.resolve("cleanup-application.log");
        ProcessResult cleanup = run(
                List.of(
                        fresh.launcher().toString(),
                        "-nosplash",
                        "-consoleLog",
                        "-application", "org.sandbox.jdt.core.JavaCleanup",
                        "-data", workspace.toString(),
                        "--import-project", project.toString(),
                        "--mode", "apply",
                        "--report", report.toString(),
                        "--config", config.toString(),
                        source.toString()),
                fresh.root(),
                log,
                Duration.ofMinutes(5));
        require(cleanup.exitCode() == 0, "Cleanup application failed; see " + log);
        require(Files.isRegularFile(report), "Cleanup application did not create its JSON report");
        String reportText = Files.readString(report);
        require(reportText.matches("(?s).*\\\"filesProcessed\\\"\\s*:\\s*1.*"),
                "Cleanup report does not record one processed file");
        require(reportText.matches("(?s).*\\\"filesChanged\\\"\\s*:\\s*1.*"),
                "Cleanup report does not record one changed file");

        String after = Files.readString(source);
        require(!after.equals(before), "Cleanup reported a change but source remained byte-identical");
        require(!after.contains("public class Smoke{") && !after.contains("value(){"),
                "Cleanup did not format compact declarations");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "A full JDK is required to compile the transformed source");
        Path compiled = evidence.resolve("compiled");
        Files.createDirectories(compiled);
        int compilerStatus = compiler.run(null, null, null,
                "-d", compiled.toString(), source.toString());
        require(compilerStatus == 0, "Transformed source does not compile with the current JDK");
    }

    private void writeEvidence(
            Model model, RepositoryEvidence repository, ProductEvidence product) throws IOException {
        String json = """
                {
                  "schemaVersion": 2,
                  "result": "PASS",
                  "platform": "%s",
                  "eclipseRelease": "%s",
                  "orbitRelease": "%s",
                  "bouncyCastleVersion": "%s",
                  "publishedFeatureCount": %d,
                  "p2MetadataUnits": %d,
                  "p2ArtifactKeys": %d,
                  "p2ArtifactFilesChecked": %d,
                  "p2ChecksumsChecked": %d,
                  "productPluginCount": %d,
                  "productSingletonBundleCount": %d,
                  "productRoot": "%s"
                }
                """.formatted(
                jsonEscape(platform.toString()),
                jsonEscape(model.eclipseRelease()),
                jsonEscape(model.orbitRelease()),
                jsonEscape(model.bouncyCastleVersion()),
                model.publishedFeatures().size(),
                repository.metadataUnits(),
                repository.artifactKeys(),
                repository.artifactFilesChecked(),
                repository.checksumsChecked(),
                product.pluginCount(),
                product.singletonBundleCount(),
                jsonEscape(product.root().toString()));
        Files.writeString(evidence.resolve("verification.json"), json);

        String markdown = """
                # Sandbox distribution verification

                - Result: **PASS**
                - Platform: **%s**
                - Eclipse release: **%s**
                - Published Sandbox features: **%d**
                - p2 metadata units: **%d**
                - p2 artifact files checked: **%d**
                - Product plug-ins: **%d**
                - Default standalone IDE workbench launch: **PASS**
                - Fresh p2 installation and startup: **PASS**
                - Cleanup application transformation and Java compilation: **PASS**
                - Product root: `%s`
                """.formatted(
                platform,
                model.eclipseRelease(),
                model.publishedFeatures().size(),
                repository.metadataUnits(),
                repository.artifactFilesChecked(),
                product.pluginCount(),
                product.root());
        Files.writeString(evidence.resolve("verification.md"), markdown);
    }

    private Path findProductRoot() throws Exception {
        Path products = root.resolve("sandbox_product/target/products");
        require(Files.isDirectory(products), "Materialized products directory is missing: " + products);
        List<Path> candidates;
        try (Stream<Path> stream = Files.walk(products)) {
            candidates = stream
                    .filter(path -> path.getFileName().toString().startsWith("org.eclipse.equinox.launcher_"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .map(path -> path.getParent().getParent())
                    .filter(path -> Files.isRegularFile(path.resolve("configuration/config.ini")))
                    .distinct()
                    .sorted()
                    .toList();
        }
        List<Path> matching = candidates.stream()
                .filter(platform::matchesProductPath)
                .toList();
        if (matching.size() == 1) {
            return matching.get(0);
        }
        require(candidates.size() == 1,
                "Expected one materialized product for " + platform + ", candidates=" + candidates);
        return candidates.get(0);
    }

    private Path findNativeLauncher(Path productRoot) throws VerificationException {
        List<Path> candidates = switch (platform.osgiOs()) {
            case "win32" -> List.of(productRoot.resolve("eclipse.exe"));
            case "macosx" -> List.of(
                    productRoot.resolve("../MacOS/eclipse").normalize(),
                    productRoot.resolve("Eclipse.app/Contents/MacOS/eclipse"),
                    productRoot.resolve("eclipse"));
            default -> List.of(productRoot.resolve("eclipse"));
        };
        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new VerificationException(
                        "Native Eclipse launcher not found below " + productRoot
                                + "; tried " + candidates));
    }

    private boolean isBundle(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")
                || Files.isDirectory(path) && Files.isRegularFile(path.resolve("META-INF/MANIFEST.MF"));
    }

    private Manifest readManifest(Path bundle) {
        try {
            if (Files.isDirectory(bundle)) {
                try (InputStream stream = Files.newInputStream(bundle.resolve("META-INF/MANIFEST.MF"))) {
                    return new Manifest(stream);
                }
            }
            try (JarFile jar = new JarFile(bundle.toFile())) {
                return jar.getManifest();
            }
        } catch (IOException error) {
            return null;
        }
    }

    private ProcessResult run(
            List<String> command,
            Path workingDirectory,
            Path log,
            Duration timeout) throws Exception {
        Files.createDirectories(log.getParent());
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        Process process = builder.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
            return new ProcessResult(-1, true);
        }
        return new ProcessResult(process.exitValue(), false);
    }

    private Document repositoryXml(Path repository, String stem) throws Exception {
        Path jar = repository.resolve(stem + ".jar");
        if (Files.isRegularFile(jar)) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry(stem + ".xml");
                require(entry != null, jar + " does not contain " + stem + ".xml");
                try (InputStream stream = zip.getInputStream(entry)) {
                    return parseXml(stream);
                }
            }
        }
        Path xml = repository.resolve(stem + ".xml");
        require(Files.isRegularFile(xml),
                "Repository is missing both " + stem + ".jar and " + stem + ".xml");
        return parseXml(xml);
    }

    private Path artifactPath(Path repository, String classifier, String id, String version) {
        return switch (classifier) {
            case "osgi.bundle" -> repository.resolve("plugins/" + id + "_" + version + ".jar");
            case "org.eclipse.update.feature" ->
                    repository.resolve("features/" + id + "_" + version + ".jar");
            default -> null;
        };
    }

    private Map<String, String> properties(Element artifact) {
        Map<String, String> result = new HashMap<>();
        for (Element property : descendants(artifact, "property")) {
            String name = property.getAttribute("name");
            if (!name.isBlank()) {
                result.put(name, property.getAttribute("value"));
            }
        }
        return result;
    }

    private String digest(Path path, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int read; (read = stream.read(buffer)) >= 0;) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private Document parseXml(Path path) throws Exception {
        try (InputStream stream = Files.newInputStream(path)) {
            return parseXml(stream);
        }
    }

    private Document parseXml(InputStream stream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(stream);
    }

    private List<Element> elements(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>(nodes.getLength());
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private List<Element> descendants(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>(nodes.getLength());
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element child) {
                result.add(child);
            }
        }
        return result;
    }

    private List<Element> directChildren(Element parent, String containerName, String childName) {
        Element container = directChild(parent, containerName).orElse(null);
        if (container == null) {
            return List.of();
        }
        List<Element> result = new ArrayList<>();
        for (Node node = container.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName(element).equals(childName)) {
                result.add(element);
            }
        }
        return result;
    }

    private Optional<Element> directChild(Element parent, String localName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName(element).equals(localName)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private Optional<String> directChildText(Element parent, String localName) {
        return directChild(parent, localName)
                .map(Element::getTextContent)
                .map(String::strip)
                .filter(text -> !text.isEmpty());
    }

    private String requiredFirstText(Document document, String localName) throws VerificationException {
        return elements(document, localName).stream()
                .map(Element::getTextContent)
                .map(String::strip)
                .filter(text -> !text.isEmpty())
                .findFirst()
                .orElseThrow(() -> new VerificationException("Missing " + localName));
    }

    private List<String> childTexts(Document document, String parentName, String childName) {
        List<String> result = new ArrayList<>();
        for (Element parent : elements(document, parentName)) {
            directChildText(parent, childName).ifPresent(result::add);
        }
        return result;
    }

    private List<String> attributeValues(Document document, String elementName, String attribute) {
        return elements(document, elementName).stream()
                .map(element -> element.getAttribute(attribute))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> directChildAttributeValues(
            Element rootElement, String containerName, String childName, String attribute) {
        List<Element> children;
        if (containerName == null) {
            children = new ArrayList<>();
            for (Node node = rootElement.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element && localName(element).equals(childName)) {
                    children.add(element);
                }
            }
        } else {
            children = directChildren(rootElement, containerName, childName);
        }
        return children.stream()
                .map(element -> element.getAttribute(attribute))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Set<String> profileModules(Document pom, String profileId) throws VerificationException {
        for (Element profile : elements(pom, "profile")) {
            if (profileId.equals(directChildText(profile, "id").orElse(""))) {
                Element modules = directChild(profile, "modules").orElse(null);
                if (modules == null) {
                    return Set.of();
                }
                Set<String> result = new LinkedHashSet<>();
                for (Node node = modules.getFirstChild(); node != null; node = node.getNextSibling()) {
                    if (node instanceof Element element && localName(element).equals("module")) {
                        result.add(element.getTextContent().strip());
                    }
                }
                return result;
            }
        }
        throw new VerificationException("Missing Maven profile " + profileId);
    }

    private String singleRepositoryVersion(
            List<String> repositories, Pattern pattern, String label) throws VerificationException {
        List<String> matches = repositories.stream()
                .map(pattern::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .toList();
        require(matches.size() == 1,
                "Expected exactly one " + label + " repository, found " + repositories);
        return matches.get(0);
    }

    private String normalizeOsgiVersion(String version) throws VerificationException {
        if (version.matches("\\d+\\.\\d+")) {
            return version + ".0";
        }
        require(version.matches("\\d+\\.\\d+\\.\\d+"),
                "Unsupported Bouncy Castle version syntax: " + version);
        return version;
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static boolean containsFatalApplicationError(String text) {
        return text.contains("No application id has been found")
                || text.matches("(?s).*Application \".*\" could not be found.*")
                || text.contains("Unhandled event loop exception");
    }

    private static <T> Set<T> difference(Set<T> left, Set<T> right) {
        Set<T> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static void require(boolean condition, String message) throws VerificationException {
        if (!condition) {
            throw new VerificationException(message);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path candidate : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Model(
            String eclipseRelease,
            String orbitRelease,
            String bouncyCastleVersion,
            List<String> publishedFeatures,
            List<String> targetRepositories) {
    }

    private record RepositoryEvidence(
            int metadataUnits,
            int artifactKeys,
            int artifactFilesChecked,
            int checksumsChecked) {
    }

    private record ArtifactEvidence(
            String id,
            String version,
            long size,
            String sha256) {
    }

    private record ProductEvidence(
            Path root,
            Path launcher,
            int pluginCount,
            int singletonBundleCount) {
    }

    private record FreshInstallation(
            Path root,
            Path launcher) {
    }

    private record ProcessResult(int exitCode, boolean timedOut) {
    }

    private record Platform(String osgiOs, String osgiWs, String osgiArch) {
        static Platform current() throws VerificationException {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String archName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            String arch = switch (archName) {
                case "amd64", "x86_64" -> "x86_64";
                case "aarch64", "arm64" -> "aarch64";
                default -> throw new VerificationException("Unsupported architecture: " + archName);
            };
            if (osName.contains("win")) {
                return new Platform("win32", "win32", arch);
            }
            if (osName.contains("mac")) {
                return new Platform("macosx", "cocoa", arch);
            }
            if (osName.contains("linux")) {
                return new Platform("linux", "gtk", arch);
            }
            throw new VerificationException("Unsupported operating system: " + osName);
        }

        boolean matchesProductPath(Path path) {
            String normalized = path.toString().replace('\\', '/');
            return normalized.contains("/" + osgiOs + "/")
                    && normalized.contains("/" + osgiWs + "/")
                    && normalized.endsWith("/" + osgiArch);
        }

        @Override
        public String toString() {
            return osgiOs + "/" + osgiWs + "/" + osgiArch;
        }
    }

    private static final class VerificationException extends Exception {
        private static final long serialVersionUID = 1L;

        VerificationException(String message) {
            super(message);
        }
    }
}
