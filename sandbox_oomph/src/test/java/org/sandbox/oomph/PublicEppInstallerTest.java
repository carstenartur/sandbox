package org.sandbox.oomph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Launches the public binary and its resulting ordinary EPP product on an isolated native desktop. */
class PublicEppInstallerTest {
    private static final String FILE="/oomph/drops/release/1.42.0/products/eclipse-inst-linux64.tar.gz";
    private static final String PROJECT_SETUP =
            "https://raw.githubusercontent.com/carstenartur/sandbox/%s/sandbox_oomph/sandboxproject.setup";
    private final Path run=Path.of("target/public-epp").toAbsolutePath();
    private final Path home=run.resolve("user-home");
    private final Path sources=Path.of("src/test/resources/probe").toAbsolutePath();
    private final Path jdk=Path.of(System.getProperty("java.home"));

    @Test
    @EnabledIfSystemProperty(named="oomph.public.epp",matches="true")
    void publicJavaPackageMainFreshAndRestart() throws Exception {
        assertEquals(25,Runtime.version().feature());
        assertNotNull(System.getenv("DISPLAY"),"Start with xvfb-run or a desktop display");
        String expected=Objects.requireNonNull(System.getProperty("oomph.public.commit"),"Supply full intended public main SHA");
        assertTrue(expected.matches("[a-f0-9]{40}"));
        assertFalse(Files.exists(run),"Use a fresh run directory to prevent accepting stale receipts or installs");
        Files.createDirectories(home);
        Path archive=run.resolve("installer.tar.gz");
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(45)).followRedirects(HttpClient.Redirect.NORMAL).build();
        var response=http.send(HttpRequest.newBuilder(URI.create("https://download.eclipse.org"+FILE))
                .timeout(Duration.ofMinutes(6)).build(),HttpResponse.BodyHandlers.ofFile(archive));
        assertEquals(200,response.statusCode());
        assertEquals(56_002_664L, Files.size(archive), "Unexpected public Installer 1.42 archive size");
        assertEquals("5d4010f80d327e67476d5d3e3d3c4d84959c6075798651f091747f6301c71783",
                digest(archive, "SHA-256"), "Archive differs from the measured 1.42.0 standalone installer");
        // The release archive is pinned by its previously measured size and SHA-256.
        // Retain a SHA-512 of the bytes used in this run as additional provenance.
        Files.writeString(run.resolve("archive.sha512"), digest(archive, "SHA-512") + "  " + FILE + "\n");
        exec("unpack",List.of("tar","-xf",archive.toString(),"-C",run.toString()),3);
        Path installer=only(run,"eclipse-inst",true);
        Files.writeString(run.resolve("expected-main.txt"), expected + "\n");
        // The public catalog reads raw main, while the workbench clones the Git branch.
        // After a push those endpoints can temporarily expose different revisions.
        URI pinnedSetup = URI.create(PROJECT_SETUP.formatted(expected));
        URI publicSetup = URI.create(PROJECT_SETUP.formatted("main"));
        byte[] setup = awaitPublishedSetup(http, pinnedSetup, publicSetup, 21, Duration.ofSeconds(15));
        String setupSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(setup));
        Files.writeString(run.resolve("public-project-setup.txt"), publicSetup + "\n" + pinnedSetup + "\n" + setupSha256 + "\n");
        Path agentClasses=compile("agent",List.of(),sources.resolve("PublicInstallerAgent.java"));
        Path uiClasses=compile("installer-ui",List.of(installer.getParent()),sources.resolve("PublicInstallerUi.java"));
        Path agent=jar(run.resolve("public-agent.jar"),agentClasses,Map.of("Premain-Class","PublicInstallerAgent"),null);
        Path ui=jar(run.resolve("public-ui.jar"),uiClasses,Map.of(),null);
        exec("installer",List.of(installer.toString(),"-nosplash","-vm",jdk.resolve("bin/java").toString(),"--launcher.appendVmargs",
                "-vmargs","-Duser.home="+home,"-Duser.language=en","-Duser.country=US","-Doomph.setup.installer.mode=advanced",
                "-javaagent:"+agent+"="+ui+";"+run+";installer"),90);
        assertTrue(Files.isRegularFile(run.resolve("installer-complete")),"Installer exited without verified BOOTSTRAP");
        Path eclipse=only(home,"eclipse",true);
        Path workspace=only(home,"workspace.setup",false).getParent().getParent().getParent().getParent();
        Path inventorySource = Path.of("src/test/java/org/sandbox/oomph/probe/PublicProjectInventory.java").toAbsolutePath();
        Path workbenchClasses=compile("workbench",List.of(home),sources.resolve("PublicWorkbenchProbe.java"),inventorySource);
        Path dropin=Files.createDirectories(eclipse.getParent().resolve("dropins")).resolve("sandbox.public.acceptance.jar");
        jar(dropin,workbenchClasses,Map.of("Bundle-ManifestVersion","2","Bundle-SymbolicName","sandbox.public.acceptance;singleton:=true",
                "Bundle-Version","1.0.0","Bundle-RequiredExecutionEnvironment","JavaSE-25",
                "Import-Package","javax.xml,javax.xml.parsers,org.w3c.dom,org.xml.sax","Require-Bundle",
                "org.eclipse.core.runtime,org.eclipse.core.resources,org.eclipse.ui,org.eclipse.ui.ide,org.eclipse.ui.workbench,org.eclipse.jface,org.eclipse.swt,org.eclipse.jdt.core,org.eclipse.jdt.launching,org.eclipse.pde.core,org.eclipse.osgi,org.eclipse.oomph.setup.core,org.eclipse.oomph.setup,org.eclipse.emf.ecore,org.eclipse.emf.common"),
                "<plugin><extension point=\"org.eclipse.ui.startup\"><startup class=\"org.sandbox.oomph.probe.PublicWorkbenchProbe\"/></extension></plugin>");
        for(String phase:List.of("fresh","restart")) {
            assertFalse(Files.exists(run.resolve(phase+".ok")));
            exec(phase,List.of(eclipse.toString(),"-nosplash","-consoleLog","-data",workspace.toString(),
                    "--launcher.appendVmargs","-vmargs","-Duser.home="+home,
                    "-Duser.language=en","-Duser.country=US","-Doomph.setup.show.initial.progress=true","-Dsandbox.public.evidence="+run,"-Dsandbox.public.commit="+expected,
                    "-Dsandbox.public.phase="+phase,"-Dsandbox.public.launcher="+eclipse,"-Dsandbox.public.jdk="+jdk.toRealPath()),115);
            assertTrue(Files.isRegularFile(run.resolve(phase+".ok")),()->"Native EPP exited without receipt: "+phase);
            assertTrue(Files.readString(run.resolve(phase+".ok")).startsWith(expected+" "));
            assertEquals(0,Files.size(run.resolve(phase+"-errors.txt")),"Problems view contains errors");
            Path wlog=workspace.resolve(".metadata/.log");
            if(Files.isRegularFile(wlog)) Files.copy(wlog,run.resolve(phase+"-workbench.log"));
            Path olog=eclipse.getParent().resolve("configuration/org.eclipse.oomph.setup/setup.log");
            if(Files.isRegularFile(olog)) Files.copy(olog,run.resolve(phase+"-oomph.log"));
        }
    }

    static byte[] awaitPublishedSetup(HttpClient http, URI pinned, URI published,
            int maxAttempts, Duration retryDelay) throws Exception {
        byte[] expected = readSetup(http, pinned);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            byte[] observed = readSetup(http, published);
            if (Arrays.equals(expected, observed)) {
                return observed;
            }
            if (attempt < maxAttempts) {
                Thread.sleep(retryDelay);
            }
        }
        return fail("Public setup " + published + " did not match " + pinned + " after " + maxAttempts + " attempts");
    }

    private static byte[] readSetup(HttpClient http, URI uri) throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), () -> "Could not read the setup at " + uri);
        return response.body();
    }

    private static String digest(Path file, String algorithm) throws Exception {
        var md=MessageDigest.getInstance(algorithm);
        try(var stream=new java.security.DigestInputStream(Files.newInputStream(file),md)) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(md.digest());
    }
    private static Path only(Path root,String filename,boolean executable) throws Exception {
        try (var files=Files.walk(root)) {
            var paths=files.filter(f->Files.isRegularFile(f)&&f.getFileName().toString().equals(filename)&&(!executable||Files.isExecutable(f))).toList();
            assertEquals(1,paths.size(),()->"Expected unique "+filename+" in "+root+": "+paths);
            return paths.getFirst();
        }
    }
    private Path compile(String name,List<Path> roots,Path... sourceFiles) throws Exception {
        Path dst=Files.createDirectories(run.resolve(name+"-classes"));
        Set<String> jars=new TreeSet<>();
        for(Path root:roots) try(var files=Files.walk(root)) {
            files.filter(f->f.getFileName().toString().endsWith(".jar")&&f.getParent().getFileName().toString().equals("plugins"))
                    .filter(f->!f.getFileName().toString().contains(".source_")).forEach(f->jars.add(f.toString()));
        }
        var arguments = new ArrayList<>(List.of("--release", "25", "-sourcepath", "",
                "-classpath", String.join(File.pathSeparator,jars), "-d", dst.toString()));
        for (Path source : sourceFiles) {
            arguments.add(source.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        return dst;
    }
    private static Path jar(Path destination,Path classes,Map<String,String> attrs,String plugin) throws Exception {
        Manifest manifest=new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        attrs.forEach(manifest.getMainAttributes()::putValue);
        try(var out=new JarOutputStream(Files.newOutputStream(destination),manifest);var files=Files.walk(classes)) {
            for(Path f:files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(f).toString().replace(File.separatorChar,'/')));
                Files.copy(f,out); out.closeEntry();
            }
            if(plugin!=null) {
                out.putNextEntry(new JarEntry("plugin.xml"));out.write(plugin.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
            }
        }
        return destination;
    }
    private String diagnostics(String phase) {
        StringBuilder result = new StringBuilder();
        for (String name : List.of(phase + "-stage.txt", phase + ".failed", phase + ".log")) {
            Path file = run.resolve(name);
            if (!Files.isRegularFile(file)) continue;
            try (var lines = Files.lines(file)) {
                var selected = lines.filter(line -> name.endsWith(".txt") || name.endsWith(".failed")
                        || line.contains("PUBLIC_INSTALLER") || line.contains("Exception")
                        || line.contains("Caused by:") || line.contains("Timed out")).toList();
                result.append(name).append('\n');
                for (String line : selected.subList(Math.max(0, selected.size() - 16), selected.size())) {
                    result.append(line).append('\n');
                }
            } catch (java.io.IOException failure) {
                result.append(name).append(": ").append(failure.getMessage()).append('\n');
            }
        }
        return result.toString();
    }
    private void exec(String phase,List<String> arguments,int minutes) throws Exception {
        var process=new ProcessBuilder(arguments).directory(run.toFile()).redirectErrorStream(true).redirectOutput(run.resolve(phase+".log").toFile());
        process.environment().put("HOME",home.toString());
        process.environment().put("XDG_CONFIG_HOME",home.resolve("config").toString());
        process.environment().put("XDG_CACHE_HOME",home.resolve("cache").toString());
        System.out.println("Starting public Eclipse phase " + phase);
        var child = process.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(minutes);
            while (child.isAlive() && System.nanoTime() < deadline) {
                if (Files.isRegularFile(run.resolve(phase + ".failed"))) {
                    fail("The native " + phase + " driver reported a failure\n" + diagnostics(phase));
                }
                child.waitFor(2, TimeUnit.SECONDS);
            }
            if (child.isAlive()) {
                for(ProcessHandle h:child.descendants().toList()) if(h.info().command().orElse("").endsWith("/java")) {
                    var dump=new ProcessBuilder(jdk.resolve("bin/jcmd").toString(),String.valueOf(h.pid()),"Thread.print","-l")
                        .redirectOutput(run.resolve(phase+"-"+h.pid()+"-threads.log").toFile()).start();
                    if(!dump.waitFor(15,TimeUnit.SECONDS)) dump.destroyForcibly();
                }
                fail(phase + " timed out\n" + diagnostics(phase));
            }
            assertEquals(0, child.exitValue(), () -> phase + " exited with " + child.exitValue() + "\n" + diagnostics(phase));
        } finally {
            if(child.isAlive()) { child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); }
        }
    }
}
