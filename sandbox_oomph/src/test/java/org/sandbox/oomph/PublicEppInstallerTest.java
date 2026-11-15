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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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
        var published=http.send(HttpRequest.newBuilder(URI.create("https://www.eclipse.org/downloads/sums.php?file="+FILE+"&type=sha512"))
                .timeout(Duration.ofMinutes(2)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,published.statusCode());
        var match=java.util.regex.Pattern.compile("(?i)(?<![a-f0-9])[a-f0-9]{128}(?![a-f0-9])").matcher(published.body());
        assertTrue(match.find(),"Eclipse publisher's SHA-512 was not returned");
        String actual=digest(archive, "SHA-512");
        assertEquals(match.group().toLowerCase(Locale.ROOT),actual);
        Files.writeString(run.resolve("archive.sha512"),actual+"  "+FILE+"\n");
        exec("unpack",List.of("tar","-xf",archive.toString(),"-C",run.toString()),3);
        Path installer=only(run,"eclipse-inst",true);
        Files.writeString(run.resolve("expected-main.txt"), expected + "\n");
        Path agentClasses=compile("agent",List.of(),"PublicInstallerAgent.java");
        Path uiClasses=compile("installer-ui",List.of(installer.getParent()),"PublicInstallerUi.java");
        Path agent=jar(run.resolve("public-agent.jar"),agentClasses,Map.of("Premain-Class","PublicInstallerAgent"),null);
        Path ui=jar(run.resolve("public-ui.jar"),uiClasses,Map.of(),null);
        exec("installer",List.of(installer.toString(),"-nosplash","-vm",jdk.resolve("bin/java").toString(),"--launcher.appendVmargs",
                "-vmargs","-Duser.home="+home,"-Duser.language=en","-Duser.country=US","-Doomph.setup.installer.mode=advanced",
                "-javaagent:"+agent+"="+ui+";"+run+";installer"),90);
        assertTrue(Files.isRegularFile(run.resolve("installer-complete")),"Installer exited without verified BOOTSTRAP");
        Path eclipse=only(home,"eclipse",true);
        Path workspace=only(home,"workspace.setup",false).getParent().getParent().getParent().getParent();
        Path workbenchClasses=compile("workbench",List.of(home),"PublicWorkbenchProbe.java");
        Path dropin=Files.createDirectories(eclipse.getParent().resolve("dropins")).resolve("sandbox.public.acceptance.jar");
        jar(dropin,workbenchClasses,Map.of("Bundle-ManifestVersion","2","Bundle-SymbolicName","sandbox.public.acceptance;singleton:=true",
                "Bundle-Version","1.0.0","Bundle-RequiredExecutionEnvironment","JavaSE-25","Require-Bundle",
                "org.eclipse.core.runtime,org.eclipse.core.resources,org.eclipse.ui,org.eclipse.ui.ide,org.eclipse.ui.workbench,org.eclipse.jface,org.eclipse.swt,org.eclipse.jdt.core,org.eclipse.jdt.launching,org.eclipse.pde.core,org.eclipse.osgi,org.eclipse.oomph.setup.core,org.eclipse.oomph.setup,org.eclipse.emf.ecore,org.eclipse.emf.common"),
                "<plugin><extension point=\"org.eclipse.ui.startup\"><startup class=\"org.sandbox.oomph.probe.PublicWorkbenchProbe\"/></extension></plugin>");
        for(String phase:List.of("fresh","restart")) {
            assertFalse(Files.exists(run.resolve(phase+".ok")));
            exec(phase,List.of(eclipse.toString(),"-nosplash","-consoleLog","-data",workspace.toString(),
                    "-vm",jdk.resolve("bin/java").toString(),"--launcher.appendVmargs","-vmargs","-Duser.home="+home,
                    "-Duser.language=en","-Duser.country=US","-Doomph.setup.show.initial.progress=true","-Dsandbox.public.evidence="+run,"-Dsandbox.public.commit="+expected,
                    "-Dsandbox.public.phase="+phase,"-Dsandbox.public.launcher="+eclipse),115);
            assertTrue(Files.isRegularFile(run.resolve(phase+".ok")),()->"Native EPP exited without receipt: "+phase);
            assertTrue(Files.readString(run.resolve(phase+".ok")).startsWith(expected+" "));
            assertEquals(0,Files.size(run.resolve(phase+"-errors.txt")),"Problems view contains errors");
            Path wlog=workspace.resolve(".metadata/.log");
            if(Files.isRegularFile(wlog)) Files.copy(wlog,run.resolve(phase+"-workbench.log"));
            Path olog=eclipse.getParent().resolve("configuration/org.eclipse.oomph.setup/setup.log");
            if(Files.isRegularFile(olog)) Files.copy(olog,run.resolve(phase+"-oomph.log"));
        }
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
    private Path compile(String name,List<Path> roots,String source) throws Exception {
        Path dst=Files.createDirectories(run.resolve(name+"-classes"));
        Set<String> jars=new TreeSet<>();
        for(Path root:roots) try(var files=Files.walk(root)) {
            files.filter(f->f.getFileName().toString().endsWith(".jar")&&f.getParent().getFileName().toString().equals("plugins"))
                    .filter(f->!f.getFileName().toString().contains(".source_")).forEach(f->jars.add(f.toString()));
        }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","25","-sourcepath","",
                "-classpath",String.join(File.pathSeparator,jars),"-d",dst.toString(),sources.resolve(source).toString()));
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
    private void exec(String phase,List<String> arguments,int minutes) throws Exception {
        var process=new ProcessBuilder(arguments).directory(run.toFile()).redirectErrorStream(true).redirectOutput(run.resolve(phase+".log").toFile());
        process.environment().put("HOME",home.toString());
        process.environment().put("XDG_CONFIG_HOME",home.resolve("config").toString());
        process.environment().put("XDG_CACHE_HOME",home.resolve("cache").toString());
        var child=process.start();
        try {
            if(!child.waitFor(minutes,TimeUnit.MINUTES)) {
                for(ProcessHandle h:child.descendants().toList()) if(h.info().command().orElse("").endsWith("/java")) {
                    var dump=new ProcessBuilder(jdk.resolve("bin/jcmd").toString(),String.valueOf(h.pid()),"Thread.print","-l")
                        .redirectOutput(run.resolve(phase+"-"+h.pid()+"-threads.log").toFile()).start();
                    if(!dump.waitFor(15,TimeUnit.SECONDS)) dump.destroyForcibly();
                }
                fail(phase+" timed out; retain target/public-epp");
            }
            assertEquals(0,child.exitValue(),()->phase+" exited with "+child.exitValue()+"; retain target/public-epp");
        } finally {
            if(child.isAlive()) { child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); }
        }
    }
}