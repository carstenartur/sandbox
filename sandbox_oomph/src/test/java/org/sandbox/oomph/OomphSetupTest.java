/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.oomph;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class OomphSetupTest {
    private final Path module = Path.of("").toAbsolutePath();
    private final Path root = module.getParent();

    @Test
    void catalogIdentityAndRuntimeFeatureCoverage() throws Exception {
        var project = xml(module.resolve("sandboxproject.setup"));
        assertEquals("sandbox", project.getDocumentElement().getAttribute("name"));
        assertEquals("main", ((Element) project.getElementsByTagName("stream").item(0)).getAttribute("name"));
        assertTrue(project.getElementsByTagName("setupTask").getLength() > 0);
        assertFalse(Files.readString(module.resolve("sandboxproject.setup")).contains("dialog_settings.xml"),
                "Setup must not replace user-owned workbench state");
        var product = xml(root.resolve("sandbox_product/sandbox.product"));
        var launch = xml(root.resolve("sandbox_product/sandbox.product.launch"));
        Set<String> expected = new TreeSet<>();
        var features = product.getElementsByTagName("feature");
        for (int i = 0; i < features.getLength(); i++) {
            expected.add(((Element) features.item(i)).getAttribute("id"));
        }
        Set<String> actual = new TreeSet<>();
        var sets = launch.getElementsByTagName("setAttribute");
        for (int i = 0; i < sets.getLength(); i++) {
            var set = (Element) sets.item(i);
            if (set.getAttribute("key").equals("selected_features")) {
                var entries = set.getElementsByTagName("setEntry");
                for (int j = 0; j < entries.getLength(); j++) {
                    actual.add(((Element) entries.item(j)).getAttribute("value").split(":")[0]);
                }
            }
        }
        assertEquals(expected, actual, "Every product feature must be available in the development launch");
        var configuration = xml(module.resolve("sandbox-installer.setup"));
        assertEquals(1, configuration.getElementsByTagName("installation").getLength());
        assertEquals(1, configuration.getElementsByTagName("workspace").getLength());
        assertEquals(0, configuration.getElementsByTagName("setupTask").getLength());
    }

    @Test
    @EnabledIfSystemProperty(named = "oomph.integration", matches = "true")
    void officialCatalogFreshWorkspaceAndManualUpdate() throws Exception {
        Path run = Files.createDirectories(module.resolve("target/oomph-runtime"));
        assertFalse(Files.exists(run.resolve("workspace/.metadata")),
                "Acceptance testing requires a fresh workspace; run Maven clean verify");
        Path eclipse = run.resolve("eclipse");
        if (!Files.isRegularFile(eclipse.resolve("eclipse"))) {
            Path archive = run.resolve("sdk.tar.gz");
            download("https://download.eclipse.org/eclipse/downloads/drops4/R-4.40-202606010713/"
                    + "eclipse-SDK-4.40-linux-gtk-x86_64.tar.gz", archive);
            Path checksums = run.resolve("sdk-checksums.txt");
            download("https://download.eclipse.org/eclipse/downloads/drops4/R-4.40-202606010713/eclipse-4.40-checksums",
                    checksums);
            String expected = Files.readAllLines(checksums).stream()
                    .filter(line -> line.endsWith("eclipse-SDK-4.40-linux-gtk-x86_64.tar.gz"))
                    .findFirst().orElseThrow().split("\\s+")[0];
            var digest = java.security.MessageDigest.getInstance("SHA-512");
            try (var in = new java.security.DigestInputStream(Files.newInputStream(archive), digest)) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            }
            assertEquals(expected.toLowerCase(java.util.Locale.ROOT), java.util.HexFormat.of().formatHex(digest.digest()),
                    "SDK archive must match the published Eclipse checksum");
            process(run.resolve("extract.log"), run, List.of("tar", "--no-same-owner", "-xzf", archive.toString()));
        }
        List<String> units = new ArrayList<>(List.of("org.eclipse.oomph.setup.sdk.feature.group",
                "org.eclipse.oomph.setup.maven.feature.group"));
        var project = xml(module.resolve("sandboxproject.setup"));
        var requirements = project.getElementsByTagName("requirement");
        for (int i = 0; i < requirements.getLength(); i++) {
            units.add(((Element) requirements.item(i)).getAttribute("name"));
        }
        List<String> repositories = new ArrayList<>(List.of("https://download.eclipse.org/oomph/updates/release/latest/"));
        var locations = project.getElementsByTagName("repository");
        for (int i = 0; i < locations.getLength(); i++) {
            repositories.add(((Element) locations.item(i)).getAttribute("url"));
        }
        List<String> director = eclipseCommand(eclipse);
        director.addAll(List.of("-application", "org.eclipse.equinox.p2.director", "-repository",
                String.join(",", repositories), "-installIU", String.join(",", units), "-nosplash"));
        process(run.resolve("provision.log"), run, director);
        installProbe(eclipse, run);
        Path workspace = run.resolve("workspace");
        for (String phase : List.of("fresh", "update")) {
            Properties result = new Properties();
            for (int attempt = 0; attempt < 3; attempt++) {
                List<String> command = eclipseCommand(eclipse);
                command.addAll(List.of("-clean", "-nosplash", "-application", "org.sandbox.oomph.probe.run",
                        "-data", workspace.toString(), "-consoleLog", "-vmargs", "-Xmx4g",
                        "-Doomph.setup.skip=true", "-Doomph.setup.questionnaire.skip=true",
                        "-Dsandbox.oomph.root=" + root, "-Dsandbox.oomph.phase=" + phase,
                        "-Dsandbox.oomph.attempt=" + attempt,
                        "-Dsandbox.oomph.ref=" + System.getProperty("sandbox.oomph.ref", "main"),
                        "-Dsandbox.oomph.commit=" + System.getProperty("sandbox.oomph.commit", ""),
                        "-Dsandbox.oomph.repository=" + System.getProperty("sandbox.oomph.repository",
                                "https://github.com/carstenartur/sandbox.git")));
                process(run.resolve(phase + "-" + attempt + ".log"), run, command);
                result.clear();
                try (var in = Files.newInputStream(run.resolve(phase + ".properties"))) {
                    result.load(in);
                }
                if ("passed".equals(result.getProperty("result"))) {
                    break;
                }
                assertEquals("restart", result.getProperty("result"), result.toString());
            }
            assertEquals("passed", result.getProperty("result"), "Setup must finish after requested IDE restarts: " + result);
            assertTrue(Integer.parseInt(result.getProperty("projects")) >= 70, result.toString());
            assertEquals("target platform for sandbox", result.getProperty("target"));
        }
    }

    private void installProbe(Path eclipse, Path run) throws Exception {
        Path classes = Files.createDirectories(run.resolve("probe-classes"));
        String classpath;
        try (var files = Files.list(eclipse.resolve("plugins"))) {
            classpath = files.filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().contains(".source_"))
                    .map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-sourcepath", "", "-classpath", classpath, "-d", classes.toString(),
                module.resolve("src/test/resources/probe/SetupProbe.java").toString()));
        Manifest manifest = new Manifest();
        Attributes a = manifest.getMainAttributes();
        a.putValue("Manifest-Version", "1.0");
        a.putValue("Bundle-ManifestVersion", "2");
        a.putValue("Bundle-SymbolicName", "org.sandbox.oomph.probe;singleton:=true");
        a.putValue("Bundle-Version", "1.0.0");
        a.putValue("Bundle-RequiredExecutionEnvironment", "JavaSE-21");
        a.putValue("Require-Bundle", String.join(",", List.of("org.eclipse.core.runtime", "org.eclipse.core.resources",
                "org.eclipse.ui", "org.eclipse.equinox.app", "org.eclipse.equinox.p2.metadata",
                "org.eclipse.equinox.p2.core", "org.eclipse.equinox.p2.director.app", "org.eclipse.oomph.p2.core", "bcpg",
                "org.eclipse.emf.common", "org.eclipse.emf.ecore",
                "org.eclipse.oomph.base", "org.eclipse.oomph.util", "org.eclipse.oomph.ui", "org.eclipse.oomph.setup",
                "org.eclipse.oomph.setup.core", "org.eclipse.oomph.setup.git", "org.eclipse.oomph.setup.pde",
                "org.eclipse.oomph.setup.maven", "org.eclipse.oomph.setup.workingsets", "org.eclipse.jdt.core",
                "org.eclipse.jdt.launching", "org.eclipse.pde.core", "org.eclipse.pde.launching",
                "org.eclipse.debug.core", "org.eclipse.jgit")));
        Path dropins = Files.createDirectories(eclipse.resolve("dropins"));
        try (var jar = new JarOutputStream(Files.newOutputStream(dropins.resolve("org.sandbox.oomph.probe.jar")), manifest)) {
            jar.putNextEntry(new JarEntry("plugin.xml"));
            jar.write(("<plugin><extension point=\"org.eclipse.core.runtime.applications\" id=\"run\">"
                    + "<application><run class=\"org.sandbox.oomph.probe.SetupProbe\"/></application>"
                    + "</extension></plugin>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
                    Files.copy(file, jar);
                    jar.closeEntry();
                }
            }
        }
    }

    private static List<String> eclipseCommand(Path eclipse) {
        return new ArrayList<>(List.of(eclipse.resolve("eclipse").toString(), "--launcher.suppressErrors", "-vm",
                Path.of(System.getProperty("java.home"), "bin/java").toString()));
    }

    private static void process(Path log, Path directory, List<String> command) throws Exception {
        System.out.println("Executing " + command);
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            if (!process.waitFor(35, TimeUnit.MINUTES)) {
                dumpThreads(process, log);
                fail("Timed out: " + log + "\n" + tail(log));
            }
            assertEquals(0, process.exitValue(), () -> "Process failed: " + log + "\n" + tail(log));
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    private static void dumpThreads(Process process, Path log) throws Exception {
        for (var child : process.descendants().toList()) {
            if (!child.info().command().map(c -> Path.of(c).getFileName().toString().equals("java")).orElse(false)) {
                continue;
            }
            Path output = log.resolveSibling(log.getFileName() + "-" + child.pid() + "-threads.log");
            Process diagnostic = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/jcmd").toString(),
                    Long.toString(child.pid()), "Thread.print", "-l")
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                diagnostic.waitFor(10, TimeUnit.SECONDS);
            } finally {
                if (diagnostic.isAlive()) {
                    diagnostic.destroyForcibly();
                }
            }
        }
    }

    private static String tail(Path log) {
        try {
            var lines = Files.readAllLines(log);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 100), lines.size()));
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static void download(String url, Path destination) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30)).build()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).build(),
                    HttpResponse.BodyHandlers.ofFile(destination));
            assertEquals(200, response.statusCode(), url);
        }
    }

    private static Document xml(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(path.toFile());
    }
}
