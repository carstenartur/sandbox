/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.cleanup;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.ClasspathComputer;
import org.eclipse.pde.internal.core.PDEClasspathContainer;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
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
            verifyCallerDiagnostics();
            receipt.setProperty("diagnostic.scope.regression", "PASS");
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
            assertEquals(31, configs.size(), "Keep the complete explicit cleanup profile matrix");
            receipt.setProperty("profiles", Integer.toString(configs.size()));
            receipt.setProperty("profile.names", String.join(",", configs.stream()
                    .map(path -> path.getFileName().toString().replace(".properties", "")).toList()));
            var workspace = ResourcesPlugin.getWorkspace();
            var description = workspace.getDescription();
            description.setAutoBuilding(false);
            workspace.setDescription(description);
            ensureJre();
            List<IJavaProject> projects = importProjects(checkout, args[3]);
            int expectedProjects = switch (args[3]) { case "core" -> 17; case "ui" -> 18; case "swt" -> 12;
                default -> throw new IllegalArgumentException(args[3]); };
            assertEquals(expectedProjects, projects.size(), "Pinned upstream project inventory changed");
            receipt.setProperty("projects", Integer.toString(projects.size()));
            receipt.setProperty("project.names", String.join(",", projects.stream().map(IJavaProject::getElementName).sorted().toList()));
            JavaCore.initializeAfterLoad(monitor);
            var dependencies = projectDependencies(projects);
            Files.write(output.resolve("diagnostic-project-edges.tsv"), dependencies.entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream().map(required -> required.getElementName()
                            + "\t" + entry.getKey().getElementName())).sorted().toList(), StandardCharsets.UTF_8);
            receipt.setProperty("diagnostic.scope", "target-and-transitive-source-callers");
            long sourceCount = 0, executions = 0, changedFiles = 0;
            for (IJavaProject project : projects) {
                ICompilationUnit[] units = units(project);
                if (units.length == 0) {
                    failures.add(project.getElementName() + ": imported Java project has no source compilation units");
                    continue;
                }
                sourceCount += units.length;
                Path projectOutput = Files.createDirectories(output.resolve(project.getElementName()));
                List<IJavaProject> scope = diagnosticProjects(project, dependencies);
                List<ProjectState> original = capture(scope, project, projectOutput);
                ICompilationUnit[] scopeUnits = units(scope);
                System.out.println("BASELINE " + project.getElementName() + " units=" + units.length
                        + " diagnostic.projects=" + scope.size() + " diagnostic.units=" + scopeUnits.length);
                var diagnostics = CompilerDiagnosticAssertions.capture(scopeUnits);
                Files.writeString(projectOutput.resolve("baseline-diagnostics.txt"),
                        "scope.projects=" + scope.size() + "\nscope.units=" + scopeUnits.length
                        + "\nerrors=" + diagnostics.baselineErrors() + "\nwarnings=" + diagnostics.baselineWarnings() + "\n");
                Files.write(projectOutput.resolve("baseline-errors.txt"), diagnostics.baselineErrorDetails(), StandardCharsets.UTF_8);
                if (diagnostics.baselineErrors() != 0) {
                    failures.add(project.getElementName() + ": target/caller original sources have " + diagnostics.baselineErrors()
                            + " compiler errors; resolve the upstream classpath before qualifying cleanups");
                    continue;
                }
                for (Path config : configs) {
                    String name = config.getFileName().toString().replace(".properties", "");
                    Path evidence = Files.createDirectories(projectOutput.resolve(name));
                    System.out.println("APPLY " + project.getElementName() + " " + name);
                    executions++;
                    Throwable profileFailure = null;
                    boolean restorationFailed = false;
                    try {
                        project.getProject().deleteMarkers(null, true, IResource.DEPTH_INFINITE);
                        var app = new ProjectWideCodeCleanupApplication();
                        Object result = app.start(new ArgumentsContext(context, new String[] {
                                "--project", project.getElementName(), "--config", config.toString(),
                                "--mode", "apply", "--report", evidence.resolve("apply.json").toString(),
                                "--patch", evidence.resolve("changes.patch").toString() }));
                        assertStableLocations(original);
                        List<Path> changed = changed(original);
                        changedFiles += changed.size();
                        Files.write(evidence.resolve("changed-files.txt"), changed.stream().map(Path::toString).toList(), StandardCharsets.UTF_8);
                        for (Path file : changed) {
                            Path relative = checkout.relativize(file);
                            assertFalse(relative.startsWith(".."), "Generated evidence must remain inside the pinned checkout: " + file);
                            if (Files.exists(file)) assertTrue(file.toRealPath().startsWith(checkout), "Unexpected external evidence target: " + file);
                            Path saved = evidence.resolve("generated").resolve(relative);
                            Files.createDirectories(saved.getParent());
                            if (Files.exists(file)) Files.copy(file, saved);
                        }
                        Files.write(evidence.resolve("markers.tsv"), markers(project.getProject()), StandardCharsets.UTF_8);
                        assertEquals(0, result, "Application failed; see apply.json");
                        assertCompilationEnvironment(original);
                        // An unchanged source set has exactly the captured diagnostics. Marker-only
                        // inspections still run and retain their actual marker inventory above.
                        if (!changed.isEmpty()) diagnostics.assertNoNewDiagnostics(units(scope));
                    } catch (Exception | AssertionError problem) {
                        profileFailure = problem;
                    } finally {
                        try {
                            boolean changedState = !changed(original).isEmpty();
                            restore(original);
                            assertTrue(changed(original).isEmpty(), "Every profile must restore target/caller bytes and source inventory");
                            assertCompilationEnvironment(original, true);
                            if (changedState) diagnostics.assertRestored();
                        } catch (Exception | AssertionError problem) {
                            restorationFailed = true;
                            if (profileFailure == null) profileFailure = problem;
                            else profileFailure.addSuppressed(problem);
                        }
                    }
                    if (profileFailure == null) Files.writeString(evidence.resolve("result.txt"), "PASS\n", StandardCharsets.UTF_8);
                    else {
                        String failure = project.getElementName() + " / " + name + ": " + profileFailure;
                        failures.add(failure);
                        var trace = new java.io.StringWriter();
                        profileFailure.printStackTrace(new java.io.PrintWriter(trace));
                        Files.writeString(evidence.resolve("result.txt"), "FAIL\n" + failure + "\n" + trace, StandardCharsets.UTF_8);
                        profileFailure.printStackTrace(System.err);
                    }
                    receipt.setProperty("executions", Long.toString(executions));
                    receipt.setProperty("source.units", Long.toString(sourceCount));
                    receipt.setProperty("changed.files.total", Long.toString(changedFiles));
                    receipt.setProperty("failures", Integer.toString(failures.size()));
                    saveReceipt();
                    if (restorationFailed) throw new AssertionError("Cannot continue with an unrestored corpus project", profileFailure);
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

    private static Map<IJavaProject, List<IJavaProject>> projectDependencies(List<IJavaProject> projects) throws Exception {
        awaitClasspath();
        var byName = new TreeMap<String, IJavaProject>();
        for (IJavaProject project : projects) byName.put(project.getElementName(), project);
        var result = new LinkedHashMap<IJavaProject, List<IJavaProject>>();
        for (IJavaProject project : projects) {
            assertTrue(project.getProject().isAccessible(), "Inaccessible diagnostic project: " + project.getElementName());
            var required = new ArrayList<IJavaProject>();
            // PDE containers resolve source dependencies here; raw .classpath entries are insufficient.
            for (IClasspathEntry entry : project.getResolvedClasspath(false)) {
                if (entry.getEntryKind() != IClasspathEntry.CPE_PROJECT) continue;
                IJavaProject dependency = byName.get(entry.getPath().segment(0));
                assertNotNull(dependency, "Unqualified source-project dependency of " + project.getElementName() + ": " + entry.getPath());
                required.add(dependency);
            }
            result.put(project, List.copyOf(required));
        }
        return result;
    }

    private static List<IJavaProject> diagnosticProjects(IJavaProject target, Map<IJavaProject, List<IJavaProject>> dependencies) {
        var result = new LinkedHashSet<IJavaProject>();
        result.add(target);
        boolean expanded;
        do {
            expanded = false;
            for (var entry : dependencies.entrySet())
                if (entry.getValue().stream().anyMatch(result::contains)) expanded |= result.add(entry.getKey());
        } while (expanded);
        return result.stream().sorted(Comparator.comparing(IJavaProject::getElementName)).toList();
    }

    private static ICompilationUnit[] units(List<IJavaProject> projects) throws Exception {
        var result = new ArrayList<ICompilationUnit>();
        for (IJavaProject project : projects) result.addAll(Arrays.asList(units(project)));
        return result.toArray(ICompilationUnit[]::new);
    }

    private void verifyCallerDiagnostics() throws Exception {
        var workspace = ResourcesPlugin.getWorkspace();
        var description = workspace.getDescription();
        description.setAutoBuilding(false);
        workspace.setDescription(description);
        ensureJre();
        IJavaProject api = fixtureProject("api", JavaCore.VERSION_17);
        IJavaProject caller = fixtureProject("caller", JavaCore.VERSION_21, api);
        caller.setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.ERROR);
        IJavaProject leaf = fixtureProject("leaf", JavaCore.VERSION_21, caller);
        IJavaProject unrelated = fixtureProject("unrelated", JavaCore.VERSION_17);
        List<IJavaProject> projects = List.of(api, caller, leaf, unrelated);
        var source = api.getPackageFragmentRoot(api.getProject().getFolder("src"))
                .createPackageFragment("api", true, monitor).createCompilationUnit("Api.java",
                        "package api; public class Api { public static int value() { return 1; } public void café() {} }", true, monitor);
        caller.getPackageFragmentRoot(caller.getProject().getFolder("src"))
                .createPackageFragment("caller", true, monitor).createCompilationUnit("Caller.java",
                        "package caller; public class Caller { public int value() { return api.Api.value(); } }", true, monitor);
        var leafPackage = leaf.getPackageFragmentRoot(leaf.getProject().getFolder("src"))
                .createPackageFragment("leaf", true, monitor);
        leafPackage.createCompilationUnit("Leaf.java",
                "package leaf; public class Leaf { public int value() { return new caller.Caller().value(); } }", true, monitor);
        unrelated.getPackageFragmentRoot(unrelated.getProject().getFolder("src"))
                .createPackageFragment("unrelated", true, monitor).createCompilationUnit("Broken.java",
                        "package unrelated; class Broken { Missing value; }", true, monitor);
        leaf.getProject().getFolder("fixtures").create(true, true, monitor);
        Path fixture = leaf.getProject().getFile("fixtures/Kept.java").getLocation().toFile().toPath();
        Files.writeString(fixture, "class Kept {}", StandardCharsets.UTF_8);
        leaf.getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
        JavaCore.initializeAfterLoad(monitor);
        List<IJavaProject> scope = diagnosticProjects(api, projectDependencies(projects));
        List<ProjectState> original = capture(scope, api, Files.createDirectories(output.resolve("caller-regression")));
        var diagnostics = CompilerDiagnosticAssertions.capture(units(scope));
        assertEquals(0, diagnostics.baselineErrors(), diagnostics.baselineErrorDetails().toString());
        source.getBuffer().setContents("package api; public class Api { public static String value() { return null; } }");
        source.save(monitor, true);
        assertEquals(0, CompilerDiagnosticAssertions.capture(units(api)).baselineErrors(), "The API owner still compiles");
        AssertionError failure = assertThrows(AssertionError.class, () -> diagnostics.assertNoNewDiagnostics(units(scope)),
                "A changed API must be checked in its dependent caller project");
        assertTrue(failure.getMessage().contains("Caller.java"), failure.getMessage());
        restore(original);
        diagnostics.assertRestored();
        assertEquals(List.of(api, caller, leaf), scope, "Use reverse transitive dependencies, excluding unrelated projects");
        assertTrue(CompilerDiagnosticAssertions.capture(units(unrelated)).baselineErrors() > 0);
        leafPackage.createCompilationUnit("Added.java", "package leaf; class Added { Missing value; }", true, monitor);
        assertThrows(AssertionError.class, () -> diagnostics.assertNoNewDiagnostics(units(scope)), "Check newly created caller CUs");
        assertTrue(CompilerDiagnosticAssertions.capture(units(scope)).baselineErrors() > 0, "Caller errors must invalidate a baseline");
        caller.setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.IGNORE);
        assertThrows(AssertionError.class, () -> assertCompilationEnvironment(original), "Never lower caller diagnostics");
        restore(original);
        assertTrue(changed(original).isEmpty(), "Restore caller metadata and remove newly created CUs");
        assertCompilationEnvironment(original);
        diagnostics.assertRestored();
        assertEquals(JavaCore.VERSION_17, api.getOption(JavaCore.COMPILER_SOURCE, true));
        assertEquals(JavaCore.VERSION_21, caller.getOption(JavaCore.COMPILER_SOURCE, true));
        verifyEnvironmentGuards(original, scope, diagnostics, api, leaf);
        assertAll("Observe settled PDE classpaths", () -> verifyQueuedClasspath(api, unrelated), this::verifyPdeImports);
        for (IJavaProject project : projects) project.getProject().delete(true, true, monitor);
        verifyLinkedSourceProtection();
    }

    private void verifyQueuedClasspath(IJavaProject consumer, IJavaProject provider) throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var original = consumer.getRawClasspath();
        Job update = new Job("Controlled PDE classpath update") {
            @Override public boolean belongsTo(Object family) { return ClasspathComputer.class.equals(family); }
            @Override protected IStatus run(IProgressMonitor progress) {
                started.countDown();
                try {
                    if (!release.await(20, TimeUnit.SECONDS)) return Status.error("Observation did not release the update");
                    var updated = new ArrayList<>(List.of(original));
                    updated.add(JavaCore.newProjectEntry(provider.getPath()));
                    consumer.setRawClasspath(updated.toArray(IClasspathEntry[]::new), progress);
                    return Status.OK_STATUS;
                } catch (Exception failure) {
                    return Status.error("Cannot publish the controlled classpath", failure);
                }
            }
        };
        var observing = new CountDownLatch(1);
        var observed = new FutureTask<>(() -> {
            observing.countDown();
            return projectDependencies(List.of(consumer, provider)).get(consumer);
        });
        update.schedule();
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS));
            Thread.ofVirtual().start(observed);
            assertTrue(observing.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> observed.get(500, TimeUnit.MILLISECONDS),
                    "Dependency observation must wait for the pending classpath update");
            release.countDown();
            assertEquals(List.of(provider), observed.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            update.join();
            consumer.setRawClasspath(original, monitor);
        }
        assertTrue(update.getResult().isOK(), update.getResult()::toString);
    }

    private void verifyPdeImports() throws Exception {
        IJavaProject java = fixtureProject("pde", JavaCore.VERSION_21);
        IProject project = java.getProject();
        try {
            project.getFolder("META-INF").create(true, true, monitor);
            var file = project.getFile("META-INF/MANIFEST.MF");
            // The same two Jupiter API imports and version range as the R4_41 UI refactoring bundle.
            String manifest = """
                    Manifest-Version: 1.0
                    Bundle-ManifestVersion: 2
                    Bundle-SymbolicName: scope.pde
                    Bundle-Version: 1.0.0
                    Bundle-RequiredExecutionEnvironment: JavaSE-21
                    Require-Bundle: org.junit;bundle-version="[4.0.0,5.0.0)"
                    Import-Package: org.junit.jupiter.api;version="[5.14.0,6.0.0)",
                     org.junit.jupiter.api.extension;version="[5.14.0,6.0.0)"

                    """;
            file.create(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)), false, monitor);
            project.getFile("build.properties").create(new ByteArrayInputStream(
                    "source.. = src/\noutput.. = bin/\nbin.includes = .,META-INF/\n".getBytes(StandardCharsets.UTF_8)), false, monitor);
            var description = project.getDescription();
            description.setNatureIds(new String[] {JavaCore.NATURE_ID, "org.eclipse.pde.PluginNature"});
            project.setDescription(description, monitor);
            var entries = new ArrayList<>(List.of(java.getRawClasspath()));
            entries.add(JavaCore.newContainerEntry(org.eclipse.core.runtime.Path.fromPortableString("org.eclipse.pde.core.requiredPlugins")));
            java.setRawClasspath(entries.toArray(IClasspathEntry[]::new), monitor);
            var unit = java.getPackageFragmentRoot(project.getFolder("src")).createPackageFragment("consumer", false, monitor)
                    .createCompilationUnit("Sample.java", "package consumer; public class Sample { @org.junit.jupiter.api.Test public void test() {} }", false, monitor);
            var scope = List.of(java);
            var original = capture(scope, java, Files.createDirectories(output.resolve("pde-regression")));
            var diagnostics = CompilerDiagnosticAssertions.capture(units(scope));
            assertEquals(0, diagnostics.baselineErrors(), diagnostics.baselineErrorDetails().toString());
            String updated = manifest.stripTrailing() + ",\n org.junit.jupiter.api.parallel;version=\"[5.14.0,6.0.0)\","
                    + "\n org.junit.jupiter.api.function;version=\"[5.14.0,6.0.0)\"\n\n";
            file.setContents(new ByteArrayInputStream(updated.getBytes(StandardCharsets.UTF_8)), false, false, monitor);
            unit.getBuffer().setContents("package consumer; @org.junit.jupiter.api.parallel.Isolated public class Sample {"
                    + " @org.junit.jupiter.api.Test public void test() { org.junit.jupiter.api.function.ThrowingConsumer<String> f = String::trim;"
                    + " java.util.Objects.requireNonNull(f, toString()); } }");
            unit.save(monitor, true);
            assertCompilationEnvironment(original);
            var state = original.getFirst();
            var before = state.resolvedClasspath();
            var after = List.of(java.getResolvedClasspath(false));
            var access = newImportAccess(state);
            var api = new Dependency(IClasspathEntry.CPE_LIBRARY, java.findType("org.junit.jupiter.api.Test")
                    .getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT).getPath());
            assertEquals(Map.of(api, Set.of(IPath.fromPortableString("org/junit/jupiter/api/parallel/*"),
                    IPath.fromPortableString("org/junit/jupiter/api/function/*"))), access, "Use the actual selected exporter");
            var oldEntry = before.stream().filter(entry -> Dependency.of(entry).equals(api)).findFirst().orElseThrow();
            var newEntry = after.stream().filter(entry -> Dependency.of(entry).equals(api)).findFirst().orElseThrow();
            assertEquals(oldEntry.getAccessRules().length + 2, newEntry.getAccessRules().length,
                    "The real PDE container must expose both imported packages on the original API entry");
            verifyInvalidAccessChanges(oldEntry, newEntry, access.get(api));
            var other = before.stream().filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY
                    && entry.getPath().lastSegment().startsWith("org.junit_")).findFirst().orElseThrow();
            var wrongRules = new ArrayList<>(List.of(other.getAccessRules()));
            wrongRules.addFirst(JavaCore.newAccessRule(access.get(api).iterator().next(), IAccessRule.K_ACCESSIBLE));
            assertThrows(AssertionError.class, () -> assertAdditiveDependencies(List.of(other),
                    List.of(withRules(other, wrongRules.toArray(IAccessRule[]::new))), access, java.getElementName()),
                    "An actual import may change access only on its selected exporter");
            diagnostics.assertNoNewDiagnostics(units(scope));
            restore(original);
            assertCompilationEnvironment(original, true);
            assertTrue(newImportAccess(state).isEmpty(), "Restore the original declared packages");
            assertEquals(oldEntry, List.of(java.getResolvedClasspath(false)).stream()
                    .filter(entry -> Dependency.of(entry).equals(api)).findFirst().orElseThrow(), "Restore the original package access");
            diagnostics.assertRestored();
        } finally {
            project.delete(true, true, monitor);
        }
    }

    private static void verifyInvalidAccessChanges(IClasspathEntry original, IClasspathEntry actual, Set<IPath> allowed) {
        var rules = List.of(actual.getAccessRules());
        var originalRules = List.of(original.getAccessRules());
        var missing = new ArrayList<>(rules);
        missing.remove(originalRules.getFirst());
        var changed = new ArrayList<>(rules);
        changed.set(changed.indexOf(originalRules.getLast()), JavaCore.newAccessRule(originalRules.getLast().getPattern(), IAccessRule.K_ACCESSIBLE));
        var reordered = new ArrayList<>(rules);
        Collections.swap(reordered, 0, 1);
        for (var invalid : List.of(missing, changed, reordered))
            assertFalse(containsOnlyImportAdditions(original, withRules(actual, invalid.toArray(IAccessRule[]::new)), allowed),
                    "Preserve every existing access rule in place");
        var added = rules.stream().filter(rule -> allowed.contains(rule.getPattern())).findFirst().orElseThrow();
        for (var forbidden : List.of(added, JavaCore.newAccessRule(IPath.fromPortableString("unbacked/package/*"), IAccessRule.K_ACCESSIBLE),
                JavaCore.newAccessRule(added.getPattern().removeLastSegments(1).append("**"), IAccessRule.K_ACCESSIBLE))) {
            var invalid = new ArrayList<>(rules);
            invalid.addFirst(forbidden);
            assertFalse(containsOnlyImportAdditions(original, withRules(actual, invalid.toArray(IAccessRule[]::new)), allowed),
                    "Reject duplicate, unbacked or wildcard access");
        }
        for (int kind : List.of(IAccessRule.K_DISCOURAGED, IAccessRule.K_ACCESSIBLE | IAccessRule.IGNORE_IF_BETTER)) {
            var invalid = new ArrayList<>(rules);
            invalid.set(invalid.indexOf(added), JavaCore.newAccessRule(added.getPattern(), kind));
            assertFalse(containsOnlyImportAdditions(original, withRules(actual, invalid.toArray(IAccessRule[]::new)), allowed),
                    "Only an exact accessible rule may be added");
        }
    }

    private void verifyLinkedSourceProtection() throws Exception {
        IJavaProject project = fixtureProject("linked", JavaCore.VERSION_17);
        var source = project.getProject().getFolder("src");
        source.delete(true, monitor);
        Path before = Files.createDirectories(output.resolve("linked-regression/before"));
        Path other = Files.createDirectories(output.resolve("linked-regression/other"));
        Files.writeString(before.resolve("Before.java"), "class Before {}", StandardCharsets.UTF_8);
        Files.writeString(other.resolve("Keep.java"), "class Keep {}", StandardCharsets.UTF_8);
        source.createLink(before.toUri(), IResource.NONE, monitor);
        List<ProjectState> original = capture(List.of(project), project, output.resolve("linked-regression"));
        try {
            source.createLink(other.toUri(), IResource.REPLACE, monitor);
            assertAll("Unexpected link retargeting must preserve both physical source trees",
                    () -> assertThrows(AssertionError.class, () -> assertCompilationEnvironment(original)),
                    () -> assertThrows(AssertionError.class, () -> restore(original)),
                    () -> assertEquals("class Keep {}", Files.readString(other.resolve("Keep.java"), StandardCharsets.UTF_8)),
                    () -> assertEquals("class Before {}", Files.readString(before.resolve("Before.java"), StandardCharsets.UTF_8)));
        } finally {
            source.createLink(before.toUri(), IResource.REPLACE, monitor);
            project.getProject().delete(true, true, monitor);
        }
    }

    private void verifyEnvironmentGuards(List<ProjectState> original, List<IJavaProject> scope,
            CompilerDiagnosticAssertions.Snapshot diagnostics, IJavaProject api, IJavaProject leaf) throws Exception {
        IClasspathEntry[] classpath = leaf.getRawClasspath();
        assertAll("Preserve the original compilation environment",
                () -> {
                    try {
                        ICompilationUnit source = units(leaf)[0];
                        source.getBuffer().setContents("package leaf; public class Leaf { Missing value; }");
                        source.save(monitor, true);
                        IClasspathEntry[] excluded = classpath.clone();
                        excluded[0] = JavaCore.newSourceEntry(classpath[0].getPath(),
                                new org.eclipse.core.runtime.IPath[] {org.eclipse.core.runtime.Path.fromPortableString("leaf/Leaf.java")});
                        leaf.setRawClasspath(excluded, monitor);
                        assertEquals(0, units(leaf).length, "The real source entry excludes the broken caller");
                        assertDoesNotThrow(() -> diagnostics.assertNoNewDiagnostics(units(scope)),
                                "Re-enumerating only the reduced scope cannot see the broken caller");
                        assertThrows(AssertionError.class, () -> assertCompilationEnvironment(original),
                                "Removing a caller from the diagnostic scope must fail qualification");
                    } finally {
                        restore(original);
                        assertCompilationEnvironment(original, true);
                    }
                },
                () -> {
                    try {
                        IClasspathEntry[] weakened = classpath.clone();
                        weakened[0] = JavaCore.newSourceEntry(classpath[0].getPath(), null, null, null,
                                new IClasspathAttribute[] {JavaCore.newClasspathAttribute(IClasspathAttribute.IGNORE_OPTIONAL_PROBLEMS, "true")});
                        leaf.setRawClasspath(weakened, monitor);
                        assertThrows(AssertionError.class, () -> assertCompilationEnvironment(original),
                                "Source-entry attributes must not disable optional compiler diagnostics");
                    } finally {
                        restore(original);
                        assertCompilationEnvironment(original, true);
                    }
                },
                () -> {
                    Path created = leaf.getProject().getFile("src/leaf/OnDisk.java").getLocation().toFile().toPath();
                    try {
                        Files.writeString(created, "package leaf; class OnDisk { Missing value; }", StandardCharsets.UTF_8);
                        assertFalse(changed(original).isEmpty(), "Refresh before deciding that Apply changed nothing");
                        assertThrows(AssertionError.class, () -> diagnostics.assertNoNewDiagnostics(units(scope)),
                                "Check new Java files written without workspace resource deltas");
                    } finally {
                        restore(original);
                    }
                    assertFalse(Files.exists(created), "Restore the original source inventory after a disk-only addition");
                },
                () -> {
                    Path fixture = leaf.getProject().getFile("fixtures/Kept.java").getLocation().toFile().toPath();
                    var expanded = new ArrayList<>(List.of(classpath));
                    expanded.add(JavaCore.newSourceEntry(leaf.getProject().getFolder("fixtures").getFullPath()));
                    try {
                        leaf.setRawClasspath(expanded.toArray(IClasspathEntry[]::new), monitor);
                        assertThrows(AssertionError.class, () -> assertCompilationEnvironment(original));
                    } finally {
                        restore(original);
                    }
                    assertTrue(Files.isRegularFile(fixture), "A pre-existing Java fixture is not a newly generated compilation unit");
                    assertEquals("class Kept {}", Files.readString(fixture, StandardCharsets.UTF_8));
                    assertCompilationEnvironment(original, true);
                },
                () -> {
                    try {
                        api.getProject().setDefaultCharset(StandardCharsets.ISO_8859_1.name(), monitor);
                        assertAll("Detect and restore metadata-only encoding changes on non-ASCII Java",
                                () -> assertFalse(changed(original).isEmpty(), "Encoding metadata must be tracked"),
                                () -> assertThrows(AssertionError.class, () -> assertCompilationEnvironment(original)),
                                () -> {
                                    restore(original);
                                    assertEquals(StandardCharsets.UTF_8.name(), api.getProject().getDefaultCharset());
                                });
                    } finally {
                        api.getProject().setDefaultCharset(StandardCharsets.UTF_8.name(), monitor);
                        restore(original);
                    }
                });
        assertTrue(changed(original).isEmpty());
        diagnostics.assertRestored();
    }

    private IJavaProject fixtureProject(String name, String version, IJavaProject... required) throws Exception {
        IProject resource = ResourcesPlugin.getWorkspace().getRoot().getProject("scope." + name);
        resource.create(monitor);
        resource.open(monitor);
        resource.setDefaultCharset(StandardCharsets.UTF_8.name(), monitor);
        var description = resource.getDescription();
        description.setNatureIds(new String[] {JavaCore.NATURE_ID});
        resource.setDescription(description, monitor);
        resource.getFolder("src").create(true, true, monitor);
        resource.getFolder("bin").create(true, true, monitor);
        IJavaProject project = JavaCore.create(resource);
        var classpath = new ArrayList<IClasspathEntry>();
        classpath.add(JavaCore.newSourceEntry(resource.getFolder("src").getFullPath()));
        classpath.add(JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath()));
        for (IJavaProject dependency : required) classpath.add(JavaCore.newProjectEntry(dependency.getPath(), true));
        project.setRawClasspath(classpath.toArray(IClasspathEntry[]::new), resource.getFolder("bin").getFullPath(), monitor);
        var options = project.getOptions(false);
        JavaCore.setComplianceOptions(version, options);
        options.put(JavaCore.COMPILER_RELEASE, JavaCore.ENABLED);
        project.setOptions(options);
        return project;
    }

    private record ProjectState(IJavaProject project, Map<Path, byte[]> files,
            Map<String, String> ownOptions, Map<String, String> effectiveOptions,
            List<IClasspathEntry> rawClasspath, List<IClasspathEntry> resolvedClasspath, IPath outputLocation,
            List<ICompilationUnit> units, Map<IResource, String> explicitCharsets, Map<IFile, String> effectiveCharsets,
            Map<Path, Path> physicalLocations, List<Path> sourceRoots) { }

    private static List<ProjectState> capture(List<IJavaProject> scope, IJavaProject target, Path output) throws Exception {
        awaitClasspath();
        var result = new ArrayList<ProjectState>();
        var inventory = new ArrayList<String>();
        inventory.add("project\trole\tunits");
        for (IJavaProject project : scope) {
            ICompilationUnit[] units = units(project);
            Path evidence = project.equals(target) ? output
                    : Files.createDirectories(output.resolve("callers").resolve(project.getElementName()));
            Files.write(evidence.resolve("sources.txt"), Arrays.stream(units)
                    .map(u -> u.getPath().toPortableString()).toList(), StandardCharsets.UTF_8);
            Files.write(evidence.resolve("classpath.txt"), Arrays.stream(project.getResolvedClasspath(false))
                    .map(Object::toString).toList(), StandardCharsets.UTF_8);
            Files.write(evidence.resolve("raw-classpath.txt"), Arrays.stream(project.getRawClasspath())
                    .map(Object::toString).toList(), StandardCharsets.UTF_8);
            writeProperties(evidence.resolve("compiler-options.properties"), project.getOptions(true));
            inventory.add(project.getElementName() + "\t" + (project.equals(target) ? "target" : "caller") + "\t" + units.length);
            Map<Path, byte[]> files = snapshot(project, units);
            Map<Path, Path> locations = new LinkedHashMap<>();
            for (var entry : files.entrySet()) if (entry.getValue() != null) locations.put(entry.getKey(), entry.getKey().toRealPath());
            var roots = new ArrayList<Path>();
            for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) if (root.getKind() == IPackageFragmentRoot.K_SOURCE)
                roots.add(root.getResource().getLocation().toFile().toPath().toRealPath());
            result.add(new ProjectState(project, files,
                    Map.copyOf(project.getOptions(false)), Map.copyOf(project.getOptions(true)),
                    List.of(project.getRawClasspath()), List.of(project.getResolvedClasspath(false)), project.getOutputLocation(),
                    List.of(units), explicitCharsets(project, units), effectiveCharsets(units), Map.copyOf(locations), List.copyOf(roots)));
        }
        Files.write(output.resolve("diagnostic-scope.tsv"), inventory, StandardCharsets.UTF_8);
        return result;
    }

    private static void assertCompilationEnvironment(List<ProjectState> scope) throws Exception {
        assertCompilationEnvironment(scope, false);
    }

    private static void assertCompilationEnvironment(List<ProjectState> scope, boolean restored) throws Exception {
        assertStableLocations(scope);
        refreshAndAwaitClasspath(scope);
        boolean dependencyMetadataChanged = false;
        for (ProjectState state : scope) {
            for (String name : List.of("META-INF/MANIFEST.MF", "pom.xml")) {
                Path path = state.project().getProject().getFile(name).getLocation().toFile().toPath().toAbsolutePath().normalize();
                if (!Arrays.equals(state.files().get(path), Files.exists(path) ? Files.readAllBytes(path) : null))
                    dependencyMetadataChanged = true;
            }
        }
        for (ProjectState state : scope) {
            IJavaProject project = state.project();
            assertEquals(state.ownOptions(), state.project().getOptions(false), "Project options changed: " + state.project().getElementName());
            assertEquals(state.effectiveOptions(), state.project().getOptions(true), "Effective options changed: " + state.project().getElementName());
            assertEquals(state.rawClasspath(), List.of(project.getRawClasspath()), "Raw classpath changed: " + project.getElementName());
            assertEquals(state.outputLocation(), project.getOutputLocation(), "Output location changed: " + project.getElementName());
            List<IClasspathEntry> resolved = List.of(project.getResolvedClasspath(false));
            if (restored || !dependencyMetadataChanged) {
                assertEquals(state.resolvedClasspath(), resolved, "Resolved classpath changed: " + project.getElementName());
            } else {
                assertAdditiveDependencies(state.resolvedClasspath(), resolved, newImportAccess(state), project.getElementName());
            }
            List<ICompilationUnit> current = List.of(units(project));
            if (restored) assertEquals(state.units(), current, "Restore the complete source inventory");
            else assertTrue(new LinkedHashSet<>(current).containsAll(state.units()), "Original compilation units disappeared: " + project.getElementName());
            for (var entry : state.explicitCharsets().entrySet())
                assertEquals(entry.getValue(), explicitCharset(entry.getKey()), "Explicit charset changed: " + entry.getKey().getFullPath());
            assertEquals(state.effectiveCharsets(), effectiveCharsets(state.units().toArray(ICompilationUnit[]::new)),
                    "Effective source encodings changed: " + project.getElementName());
        }
    }

    private record Dependency(int kind, IPath path) {
        static Dependency of(IClasspathEntry entry) { return new Dependency(entry.getEntryKind(), entry.getPath()); }
    }

    private static Set<String> imports(byte[] manifest) throws Exception {
        var result = new LinkedHashSet<String>();
        if (manifest != null) {
            var attributes = new Manifest(new ByteArrayInputStream(manifest)).getMainAttributes();
            var clauses = ManifestElement.parseHeader(Constants.IMPORT_PACKAGE, attributes.getValue(Constants.IMPORT_PACKAGE));
            if (clauses != null) for (var clause : clauses) result.addAll(List.of(clause.getValueComponents()));
        }
        return result;
    }

    private static Map<Dependency, Set<IPath>> newImportAccess(ProjectState state) throws Exception {
        var project = state.project().getProject();
        var path = project.getFile("META-INF/MANIFEST.MF").getLocation().toFile().toPath().toAbsolutePath().normalize();
        if (!Files.exists(path)) return Map.of();
        byte[] before = state.files().get(path), after = Files.readAllBytes(path);
        if (Arrays.equals(before, after)) return Map.of();
        Set<String> added = imports(after);
        added.removeAll(imports(before));
        if (added.isEmpty()) return Map.of();
        var model = PluginRegistry.findModel(project);
        if (model == null) return Map.of();
        var bundle = model.getBundleDescription();
        if (bundle == null || !bundle.isResolved()) return Map.of();
        var allowed = new LinkedHashMap<Dependency, Set<IPath>>();
        for (var imported : bundle.getResolvedImports()) {
            if (!added.contains(imported.getName())) continue;
            var provider = PluginRegistry.findModel((org.osgi.resource.Resource) imported.getExporter());
            if (provider == null) continue;
            var pattern = IPath.fromPortableString(imported.getName().replace('.', '/') + "/*");
            if (provider.getUnderlyingResource() != null) {
                var key = new Dependency(IClasspathEntry.CPE_PROJECT, provider.getUnderlyingResource().getProject().getFullPath());
                allowed.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(pattern);
            } else {
                for (var entry : PDEClasspathContainer.getExternalEntries(provider))
                    allowed.computeIfAbsent(Dependency.of(entry), ignored -> new LinkedHashSet<>()).add(pattern);
            }
        }
        return allowed;
    }

    private static void assertAdditiveDependencies(List<IClasspathEntry> baseline, List<IClasspathEntry> resolved,
            Map<Dependency, Set<IPath>> allowed, String project) {
        var existing = new LinkedHashSet<Dependency>();
        for (var entry : baseline) existing.add(Dependency.of(entry));
        var additions = new LinkedHashSet<Dependency>();
        int retained = 0;
        for (var entry : resolved) {
            var key = Dependency.of(entry);
            if (!existing.contains(key)) {
                assertTrue(additions.add(key), "New dependency duplicated: " + project + " " + entry);
                assertTrue(key.kind() == IClasspathEntry.CPE_LIBRARY || key.kind() == IClasspathEntry.CPE_PROJECT,
                        "Only additive binary/project dependencies may follow a descriptor update: " + entry);
                continue;
            }
            assertTrue(retained < baseline.size(), "Existing dependency duplicated: " + project + " " + entry);
            var prior = baseline.get(retained++);
            if (!prior.equals(entry)) {
                assertEquals(Dependency.of(prior), key, "Resolved dependency order changed: " + project);
                assertTrue(containsOnlyImportAdditions(prior, entry, allowed.getOrDefault(key, Set.of())),
                        "Resolved dependency changed beyond newly wired package imports: " + project + " " + entry);
            }
        }
        assertEquals(baseline.size(), retained, "Resolved dependency disappeared: " + project);
    }

    private static boolean containsOnlyImportAdditions(IClasspathEntry prior, IClasspathEntry entry, Set<IPath> allowed) {
        if (allowed.isEmpty() || (entry.getEntryKind() != IClasspathEntry.CPE_PROJECT && entry.getEntryKind() != IClasspathEntry.CPE_LIBRARY))
            return false;
        var oldRules = prior.getAccessRules();
        int retained = 0;
        var remaining = new LinkedHashSet<>(allowed);
        for (var rule : oldRules) remaining.remove(rule.getPattern());
        for (var rule : entry.getAccessRules()) {
            if (retained < oldRules.length && oldRules[retained].equals(rule)) retained++;
            else if (rule.getKind() != IAccessRule.K_ACCESSIBLE || rule.ignoreIfBetter() || !remaining.remove(rule.getPattern())) return false;
        }
        return retained == oldRules.length && prior.equals(withRules(entry, oldRules))
                && java.util.Objects.equals(prior.getReferencingEntry(), entry.getReferencingEntry());
    }

    private static IClasspathEntry withRules(IClasspathEntry entry, IAccessRule[] rules) {
        if (entry.getEntryKind() == IClasspathEntry.CPE_PROJECT)
            return JavaCore.newProjectEntry(entry.getPath(), rules, entry.combineAccessRules(), entry.getExtraAttributes(), entry.isExported());
        return JavaCore.newLibraryEntry(entry.getPath(), entry.getSourceAttachmentPath(), entry.getSourceAttachmentRootPath(),
                rules, entry.getExtraAttributes(), entry.isExported());
    }

    private static void assertStableLocations(List<ProjectState> scope) throws Exception {
        for (ProjectState state : scope) {
            Path descriptor = state.project().getProject().getFile(".project").getLocation().toFile().toPath().toAbsolutePath().normalize();
            assertArrayEquals(state.files().get(descriptor), Files.readAllBytes(descriptor),
                    "Unexpected project description or linked-resource change; preserve checkout and abort: " + descriptor);
            for (var entry : state.physicalLocations().entrySet()) {
                if (Files.exists(entry.getKey())) assertEquals(entry.getValue(), entry.getKey().toRealPath(),
                        "Tracked physical location changed: " + entry.getKey());
                else assertEquals(entry.getValue().getParent(), entry.getKey().getParent().toRealPath(),
                        "Parent location of a removed file changed: " + entry.getKey());
            }
        }
    }

    private static String explicitCharset(IResource resource) throws Exception {
        return resource instanceof IFile file ? file.getCharset(false) : ((IContainer) resource).getDefaultCharset(false);
    }

    private static Map<IResource, String> explicitCharsets(IJavaProject project, ICompilationUnit[] units) throws Exception {
        var resources = new LinkedHashSet<IResource>();
        resources.add(project.getProject());
        for (ICompilationUnit unit : units) {
            for (IResource resource = unit.getResource(); resource != null && resource.getType() != IResource.ROOT; resource = resource.getParent())
                resources.add(resource);
        }
        Map<IResource, String> result = new LinkedHashMap<>();
        for (IResource resource : resources) result.put(resource, explicitCharset(resource));
        return Collections.unmodifiableMap(result); // A null value means that the resource inherits its encoding.
    }

    private static Map<IFile, String> effectiveCharsets(ICompilationUnit[] units) throws Exception {
        Map<IFile, String> result = new LinkedHashMap<>();
        for (ICompilationUnit unit : units) {
            IFile file = (IFile) unit.getResource();
            result.put(file, file.getCharset());
        }
        return Map.copyOf(result);
    }

    private void restore(List<ProjectState> scope) throws Exception {
        // Do not delete or overwrite files when project links or physical targets changed unexpectedly.
        assertStableLocations(scope);
        for (ProjectState state : scope) {
            if (!state.ownOptions().equals(state.project().getOptions(false))) state.project().setOptions(state.ownOptions());
            // Restore roots before deleting additions: an added root may expose pre-existing compiler fixtures.
            if (!state.rawClasspath().equals(List.of(state.project().getRawClasspath()))
                    || !state.outputLocation().equals(state.project().getOutputLocation()))
                state.project().setRawClasspath(state.rawClasspath().toArray(IClasspathEntry[]::new), state.outputLocation(), monitor);
            for (var entry : state.explicitCharsets().entrySet()) {
                if (!entry.getKey().exists()) continue;
                if (java.util.Objects.equals(entry.getValue(), explicitCharset(entry.getKey()))) continue;
                if (entry.getKey() instanceof IFile file) file.setCharset(entry.getValue(), monitor);
                else ((IContainer) entry.getKey()).setDefaultCharset(entry.getValue(), monitor);
            }
            restore(state);
        }
        awaitClasspath();
    }

    private static void awaitClasspath() throws InterruptedException {
        try {
            Job.getJobManager().join(ClasspathComputer.class, new NullProgressMonitor());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    private static void refreshAndAwaitClasspath(List<ProjectState> scope) throws Exception {
        for (ProjectState state : scope) state.project().getProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
        awaitClasspath();
    }

    private static List<Path> changed(List<ProjectState> scope) throws Exception {
        refreshAndAwaitClasspath(scope);
        var result = new ArrayList<Path>();
        for (ProjectState state : scope) {
            result.addAll(changed(state.project(), state.files()));
        }
        return result.stream().distinct().sorted().toList();
    }

    private static Map<Path, byte[]> snapshot(IJavaProject project, ICompilationUnit[] units) throws Exception {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        for (Path path : trackedPaths(project, units)) result.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
        return result;
    }

    private static java.util.Set<Path> trackedPaths(IJavaProject project, ICompilationUnit[] units) throws Exception {
        var result = new LinkedHashSet<Path>();
        for (ICompilationUnit unit : units) {
            IFile file = (IFile) unit.getResource();
            Path location = file.getLocation().toFile().toPath().toAbsolutePath().normalize();
            result.add(location);
        }
        for (String name : List.of(".classpath", ".project", "pom.xml", "build.properties", "META-INF/MANIFEST.MF")) {
            IFile file = project.getProject().getFile(name);
            if (file.exists()) {
                Path location = file.getLocation().toFile().toPath().toAbsolutePath().normalize();
                result.add(location);
            } else if (file.getLocation() != null) {
                result.add(file.getLocation().toFile().toPath().toAbsolutePath().normalize());
            }
        }
        Path settings = project.getProject().getFolder(".settings").getLocation().toFile().toPath();
        if (Files.isDirectory(settings)) {
            try (var files = Files.walk(settings)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) result.add(file.toAbsolutePath().normalize());
            }
        }
        return result;
    }

    private void restore(ProjectState state) throws Exception {
        IJavaProject project = state.project();
        Map<Path, byte[]> snapshot = state.files();
        project.getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
        var additions = new ArrayList<Path>();
        Path projectRoot = project.getProject().getLocation().toFile().toPath().toRealPath();
        for (Path path : trackedPaths(project, units(project))) if (snapshot.get(path) == null && Files.exists(path)) {
            Path physicalParent = path.getParent().toRealPath();
            assertTrue(physicalParent.startsWith(projectRoot) || state.sourceRoots().stream().anyMatch(physicalParent::startsWith),
                    "Refuse to delete an unexpected external source target: " + path);
            additions.add(path);
        }
        for (Path path : additions) Files.deleteIfExists(path);
        for (var entry : snapshot.entrySet()) {
            byte[] current = Files.exists(entry.getKey()) ? Files.readAllBytes(entry.getKey()) : null;
            if (Arrays.equals(entry.getValue(), current)) continue;
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
        Map<Path, byte[]> current = snapshot(project, units(project));
        for (var entry : snapshot.entrySet()) {
            if (!Arrays.equals(entry.getValue(), current.get(entry.getKey()))) result.add(entry.getKey());
        }
        for (Path location : current.keySet()) {
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
