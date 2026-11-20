/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.cleanup;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** Executes the shipped application and shared strict compiler assertions on real projects. */
public final class CorpusApplication implements IApplication {
    private final NullProgressMonitor monitor = new NullProgressMonitor();
    private final List<String> failures = new ArrayList<>();
    private final Properties receipt = new Properties();
    private Path output;

    @Override public Object start(IApplicationContext context) throws Exception {
        String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        assertEquals(4, args.length, "checkout, profiles, output, family are required");
        Path checkout = Path.of(args[0]).toRealPath(), profiles = Path.of(args[1]).toRealPath();
        output = Files.createDirectories(Path.of(args[2]));
        receipt.setProperty("family", args[3]);
        receipt.setProperty("status", "RUNNING");
        saveReceipt();
        Display display = null;
        try {
            Map<String, String> installed = new TreeMap<>();
            for (var element : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps")) {
                if (element.getName().equals("cleanUp") && element.getContributor().getName().startsWith("sandbox_"))
                    assertNull(installed.put(element.getAttribute("id"), element.getAttribute("class")), "Duplicate cleanup registration");
            }
            assertEquals(readProperties(profiles.resolve("registrations.properties")), installed,
                    "The actual product must contain every source registration, with the exact implementation");
            receipt.setProperty("registrations", Integer.toString(installed.size()));
            var combinedOptions = new MapCleanUpOptions(readProperties(profiles.resolve("all-java-preserve.properties")));
            var activated = new ArrayList<String>();
            for (var element : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps")) {
                if (!element.getName().equals("cleanUp") || !element.getContributor().getName().startsWith("sandbox_")) continue;
                var cleanup = (ICleanUp) element.createExecutableExtension("class");
                cleanup.setOptions(combinedOptions);
                String[] steps = cleanup.getStepDescriptions();
                assertTrue(steps.length > 0, "Combined profile does not activate " + element.getAttribute("id"));
                activated.add(element.getAttribute("id") + "\t" + String.join(" | ", steps));
            }
            Files.write(output.resolve("activated-cleanups.tsv"), activated, StandardCharsets.UTF_8);
            if (args[3].equals("inventory")) {
                receipt.setProperty("status", "PASS");
                return IApplication.EXIT_OK;
            }
            display = new Display();
            List<Path> configs;
            try (var files = Files.list(profiles)) {
                configs = files.filter(p -> p.toString().endsWith(".properties")
                        && !p.getFileName().toString().equals("registrations.properties")).sorted().toList();
            }
            assertFalse(configs.isEmpty(), "No cleanup profiles");
            receipt.setProperty("profiles", Integer.toString(configs.size()));
            var workspace = ResourcesPlugin.getWorkspace();
            var description = workspace.getDescription();
            description.setAutoBuilding(false);
            workspace.setDescription(description);
            ensureJre();
            List<IJavaProject> projects = importProjects(checkout, args[3]);
            assertFalse(projects.isEmpty(), "No upstream Java projects imported");
            JavaCore.initializeAfterLoad(monitor);
            long sourceCount = 0, executions = 0, changedFiles = 0;
            for (IJavaProject project : projects) {
                ICompilationUnit[] units = units(project);
                if (units.length == 0) {
                    failures.add(project.getElementName() + ": imported Java project has no source compilation units");
                    continue;
                }
                sourceCount += units.length;
                Path projectOutput = Files.createDirectories(output.resolve(project.getElementName()));
                Files.write(projectOutput.resolve("sources.txt"), Arrays.stream(units)
                        .map(u -> u.getPath().toPortableString()).sorted().toList(), StandardCharsets.UTF_8);
                Files.write(projectOutput.resolve("classpath.txt"), Arrays.stream(project.getResolvedClasspath(false))
                        .map(Object::toString).toList(), StandardCharsets.UTF_8);
                writeProperties(projectOutput.resolve("compiler-options.properties"), project.getOptions(true));
                Map<Path, byte[]> original = snapshot(project, units);
                System.out.println("BASELINE " + project.getElementName() + " units=" + units.length);
                var diagnostics = CompilerDiagnosticAssertions.capture(units);
                Files.writeString(projectOutput.resolve("baseline-diagnostics.txt"),
                        "errors=" + diagnostics.baselineErrors() + "\nwarnings=" + diagnostics.baselineWarnings() + "\n");
                Files.write(projectOutput.resolve("baseline-errors.txt"), diagnostics.baselineErrorDetails(), StandardCharsets.UTF_8);
                if (diagnostics.baselineErrors() != 0) {
                    failures.add(project.getElementName() + ": original sources have " + diagnostics.baselineErrors()
                            + " compiler errors; resolve the upstream classpath before qualifying cleanups");
                    continue;
                }
                for (Path config : configs) {
                    String name = config.getFileName().toString().replace(".properties", "");
                    Path evidence = Files.createDirectories(projectOutput.resolve(name));
                    System.out.println("APPLY " + project.getElementName() + " " + name);
                    executions++;
                    boolean changedSource = false;
                    try {
                        project.getProject().deleteMarkers(null, true, IResource.DEPTH_INFINITE);
                        var app = new ProjectWideCodeCleanupApplication();
                        Object result = app.start(new ArgumentsContext(context, new String[] {
                                "--project", project.getElementName(), "--config", config.toString(),
                                "--mode", "apply", "--report", evidence.resolve("apply.json").toString(),
                                "--patch", evidence.resolve("changes.patch").toString() }));
                        List<Path> changed = changed(project, original);
                        changedSource = !changed.isEmpty();
                        changedFiles += changed.size();
                        Files.write(evidence.resolve("changed-files.txt"), changed.stream().map(Path::toString).toList(), StandardCharsets.UTF_8);
                        for (Path file : changed) {
                            Path relative = checkout.relativize(file);
                            Path saved = evidence.resolve("generated").resolve(relative);
                            Files.createDirectories(saved.getParent());
                            if (Files.exists(file)) Files.copy(file, saved);
                        }
                        Files.write(evidence.resolve("markers.tsv"), markers(project.getProject()), StandardCharsets.UTF_8);
                        assertEquals(0, result, "Application failed; see apply.json");
                        // An unchanged source set has exactly the captured diagnostics. Marker-only
                        // inspections still run and retain their actual marker inventory above.
                        if (!changed.isEmpty()) diagnostics.assertNoNewDiagnostics(units(project));
                        Files.writeString(evidence.resolve("result.txt"), "PASS\n", StandardCharsets.UTF_8);
                    } catch (Exception | AssertionError problem) {
                        String failure = project.getElementName() + " / " + name + ": " + problem;
                        failures.add(failure);
                        Files.writeString(evidence.resolve("result.txt"), "FAIL\n" + failure + "\n", StandardCharsets.UTF_8);
                        problem.printStackTrace(System.err);
                    } finally {
                        restore(project, original);
                        assertTrue(changed(project, original).isEmpty(), "Every profile must start with identical original bytes and source inventory");
                        if (changedSource) diagnostics.assertRestored();
                    }
                    receipt.setProperty("executions", Long.toString(executions));
                    receipt.setProperty("source.units", Long.toString(sourceCount));
                    receipt.setProperty("changed.files.total", Long.toString(changedFiles));
                    receipt.setProperty("failures", Integer.toString(failures.size()));
                    saveReceipt();
                }
            }
            assertTrue(sourceCount > 0, "No source compilation units were executed");
            receipt.setProperty("projects", Integer.toString(projects.size()));
            receipt.setProperty("status", failures.isEmpty() ? "PASS" : "FAIL");
        } catch (Exception | AssertionError problem) {
            failures.add(problem.toString());
            receipt.setProperty("status", "FAIL");
            problem.printStackTrace(System.err);
        } finally {
            if (display != null && !display.isDisposed()) display.dispose();
            receipt.setProperty("failures", Integer.toString(failures.size()));
            Files.write(output.resolve("failures.txt"), failures, StandardCharsets.UTF_8);
            saveReceipt();
        }
        return failures.isEmpty() ? IApplication.EXIT_OK : Integer.valueOf(1);
    }

    private List<IJavaProject> importProjects(Path checkout, String family) throws Exception {
        List<Path> directories = new ArrayList<>();
        if (family.equals("swt")) {
            for (String group : List.of("bundles", "examples", "tests")) {
                try (var paths = Files.list(checkout.resolve(group))) {
                    directories.addAll(paths.filter(Files::isDirectory).toList());
                }
            }
            directories.add(checkout.resolve("binaries/org.eclipse.swt.gtk.linux.x86_64"));
        } else {
            try (var paths = Files.list(checkout)) { directories.addAll(paths.filter(Files::isDirectory).toList()); }
        }
        var projects = new ArrayList<IJavaProject>();
        var excluded = new ArrayList<String>();
        var selectedClasspaths = new ArrayList<String>();
        var workspace = ResourcesPlugin.getWorkspace();
        for (Path directory : directories.stream().sorted().toList()) {
            Path projectFile = directory.resolve(".project");
            if (!Files.isRegularFile(projectFile)) continue;
            String name = directory.getFileName().toString();
            if (family.equals("swt") && (name.endsWith(".win32") || name.endsWith(".cocoa"))) {
                excluded.add(checkout.relativize(directory) + "\tRequires its native target platform; this run uses Linux GTK");
                continue;
            }
            if (family.equals("swt") && name.equals("org.eclipse.swt.opengl.examples")
                    && !Files.exists(directory.resolve("META-INF/MANIFEST.MF"))) {
                excluded.add(checkout.relativize(directory)
                        + "\tLegacy example outside the upstream reactor; requires org.eclipse.opengl 0.5.0, absent from the pinned platform");
                continue;
            }
            Path gtkClasspath = directory.resolve(".classpath_gtk");
            if (family.equals("swt") && !Files.exists(directory.resolve(".classpath")) && Files.isRegularFile(gtkClasspath)) {
                Files.copy(gtkClasspath, directory.resolve(".classpath"));
                selectedClasspaths.add(checkout.relativize(gtkClasspath) + "\t" + checkout.relativize(directory.resolve(".classpath")));
            }
            var description = workspace.loadProjectDescription(org.eclipse.core.runtime.Path.fromOSString(projectFile.toString()));
            if (!Arrays.asList(description.getNatureIds()).contains(JavaCore.NATURE_ID)) continue;
            IProject project = workspace.getRoot().getProject(description.getName());
            assertFalse(project.exists(), "Duplicate project identity: " + description.getName());
            description.setLocation(org.eclipse.core.runtime.Path.fromOSString(directory.toString()));
            project.create(description, monitor);
            project.open(monitor);
            project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            if (!project.getFile(".classpath").exists()) {
                excluded.add(checkout.relativize(directory) + "\tNo selected .classpath; no source scope inferred");
                continue;
            }
            projects.add(JavaCore.create(project));
        }
        Files.write(output.resolve("excluded-projects.tsv"), excluded, StandardCharsets.UTF_8);
        Files.write(output.resolve("selected-classpaths.tsv"), selectedClasspaths, StandardCharsets.UTF_8);
        return projects;
    }

    private static void ensureJre() throws Exception {
        if (JavaRuntime.getDefaultVMInstall() != null) return;
        var type = JavaRuntime.getVMInstallTypes()[0];
        var vm = type.createVMInstall("sandbox-upstream-qa");
        vm.setName("Sandbox upstream QA Java");
        vm.setInstallLocation(Path.of(System.getProperty("java.home")).toFile());
        JavaRuntime.setDefaultVMInstall(vm, new NullProgressMonitor());
    }

    private static ICompilationUnit[] units(IJavaProject project) throws Exception {
        var result = new ArrayList<ICompilationUnit>();
        for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
            if (root.getKind() != IPackageFragmentRoot.K_SOURCE) continue;
            for (var child : root.getChildren()) if (child instanceof IPackageFragment fragment)
                result.addAll(Arrays.asList(fragment.getCompilationUnits()));
        }
        result.sort(Comparator.comparing(u -> u.getPath().toPortableString()));
        return result.toArray(ICompilationUnit[]::new);
    }

    private static Map<Path, byte[]> snapshot(IJavaProject project, ICompilationUnit[] units) throws Exception {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        for (ICompilationUnit unit : units) {
            IFile file = (IFile) unit.getResource();
            Path location = file.getLocation().toFile().toPath().toRealPath();
            result.put(location, Files.readAllBytes(location));
        }
        for (String name : List.of(".classpath", ".project", "pom.xml", "build.properties", "META-INF/MANIFEST.MF")) {
            IFile file = project.getProject().getFile(name);
            if (file.exists()) {
                Path location = file.getLocation().toFile().toPath().toRealPath();
                result.put(location, Files.readAllBytes(location));
            } else if (file.getLocation() != null) {
                result.put(file.getLocation().toFile().toPath().toAbsolutePath().normalize(), null);
            }
        }
        return result;
    }

    private void restore(IJavaProject project, Map<Path, byte[]> snapshot) throws Exception {
        for (ICompilationUnit unit : units(project)) {
            IFile file = (IFile) unit.getResource();
            Path location = file.getLocation().toFile().toPath().toRealPath();
            if (!snapshot.containsKey(location)) file.delete(true, monitor);
        }
        for (var entry : snapshot.entrySet()) {
            if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
            else {
                Files.createDirectories(entry.getKey().getParent());
                Files.write(entry.getKey(), entry.getValue());
            }
        }
        project.getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
    }

    private static List<Path> changed(IJavaProject project, Map<Path, byte[]> snapshot) throws Exception {
        var result = new ArrayList<Path>();
        for (var entry : snapshot.entrySet()) {
            byte[] current = Files.exists(entry.getKey()) ? Files.readAllBytes(entry.getKey()) : null;
            if (!Arrays.equals(entry.getValue(), current)) result.add(entry.getKey());
        }
        for (ICompilationUnit unit : units(project)) {
            Path location = unit.getResource().getLocation().toFile().toPath().toRealPath();
            if (!snapshot.containsKey(location)) result.add(location);
        }
        return result;
    }

    private static List<String> markers(IProject project) throws Exception {
        var result = new ArrayList<String>();
        for (IMarker marker : project.findMarkers(null, true, IResource.DEPTH_INFINITE))
            result.add(marker.getResource().getProjectRelativePath() + "\t" + marker.getType() + "\t"
                    + marker.getAttribute(IMarker.SEVERITY, -1) + "\t" + marker.getAttribute(IMarker.LINE_NUMBER, -1)
                    + "\t" + marker.getAttribute(IMarker.MESSAGE, "").replace('\n', ' '));
        result.sort(String::compareTo);
        return result;
    }

    private static Map<String, String> readProperties(Path path) throws Exception {
        var values = new Properties();
        try (var reader = Files.newBufferedReader(path)) { values.load(reader); }
        Map<String, String> result = new TreeMap<>();
        for (String key : values.stringPropertyNames()) result.put(key, values.getProperty(key));
        return result;
    }

    private static void writeProperties(Path path, Map<String, String> values) throws Exception {
        Properties data = new Properties(); data.putAll(values);
        try (var writer = Files.newBufferedWriter(path)) { data.store(writer, "Observed upstream project options"); }
    }

    private void saveReceipt() throws Exception {
        try (var writer = Files.newBufferedWriter(output.resolve("receipt.properties"))) {
            receipt.store(writer, "Actual Eclipse cleanup corpus execution");
        }
    }

    private record ArgumentsContext(IApplicationContext delegate, String[] arguments) implements IApplicationContext {
        @Override public Map<String, Object> getArguments() { return Map.of(APPLICATION_ARGS, arguments.clone()); }
        @Override public void applicationRunning() { delegate.applicationRunning(); }
        @Override public String getBrandingApplication() { return delegate.getBrandingApplication(); }
        @Override public Bundle getBrandingBundle() { return delegate.getBrandingBundle(); }
        @Override public String getBrandingDescription() { return delegate.getBrandingDescription(); }
        @Override public String getBrandingId() { return delegate.getBrandingId(); }
        @Override public String getBrandingName() { return delegate.getBrandingName(); }
        @Override public String getBrandingProperty(String key) { return delegate.getBrandingProperty(key); }
        @Override public void setResult(Object result, IApplication application) { delegate.setResult(result, application); }
    }

    @Override public void stop() { }
}
