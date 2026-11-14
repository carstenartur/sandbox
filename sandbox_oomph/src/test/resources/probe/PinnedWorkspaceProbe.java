/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.oomph.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.p2.metadata.ILicense;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.oomph.internal.setup.SetupPrompter;
import org.eclipse.oomph.setup.Configuration;
import org.eclipse.oomph.setup.SetupFactory;
import org.eclipse.oomph.setup.SetupTask;
import org.eclipse.oomph.setup.SetupTaskContext;
import org.eclipse.oomph.setup.Trigger;
import org.eclipse.oomph.setup.VariableTask;
import org.eclipse.oomph.setup.git.GitCloneTask;
import org.eclipse.oomph.setup.internal.core.SetupContext;
import org.eclipse.oomph.setup.internal.core.SetupTaskPerformer;
import org.eclipse.oomph.setup.internal.core.util.SetupCoreUtil;
import org.eclipse.oomph.setup.log.ProgressLog;
import org.eclipse.oomph.setup.projects.ProjectsBuildTask;
import org.eclipse.oomph.ui.UICallback;
import org.eclipse.oomph.ui.UIUtil;
import org.eclipse.oomph.util.Confirmer;
import org.eclipse.oomph.util.OS;
import org.eclipse.oomph.util.UserCallback;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.core.target.LoadTargetDefinitionJob;
import org.eclipse.pde.internal.core.ClasspathComputer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.osgi.framework.FrameworkUtil;

/** Applies the checked-in R4_40 Oomph configuration to a fresh retained workspace. */
public class PinnedWorkspaceProbe implements IApplication {
    private volatile Throwable failure;

    @Override
    public Object start(IApplicationContext application) {
        Display display = PlatformUI.createDisplay();
        try {
            PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor() {
                @Override public String getInitialWindowPerspectiveId() { return "org.eclipse.jdt.ui.JavaPerspective"; }

                @Override public void postStartup() {
                    display.asyncExec(() -> Job.create("Provision pinned JDT workspace", monitor -> {
                        try {
                            provision(monitor);
                        } catch (Throwable error) {
                            failure = error;
                            error.printStackTrace();
                        } finally {
                            display.asyncExec(() -> PlatformUI.getWorkbench().close());
                        }
                    }).schedule());
                }
            });
        } finally {
            display.dispose();
        }
        return failure == null ? EXIT_OK : Integer.valueOf(1);
    }

    @Override public void stop() { }

    private static void provision(IProgressMonitor monitor) throws Exception {
        Path checkout = Path.of(System.getProperty("sandbox.oomph.root")).toRealPath();
        Path run = Path.of(System.getProperty("sandbox.oomph.run")).toRealPath();
        var workspace = ResourcesPlugin.getWorkspace();
        var description = workspace.getDescription();
        description.setAutoBuilding(false);
        workspace.setDescription(description);
        var resources = SetupCoreUtil.createResourceSet();
        var configuration = (Configuration) resources.getResource(URI.createFileURI(checkout.resolve(
                "sandbox_oomph/jdt-migration-qa.configuration.setup").toString()), true).getContents().get(0);
        var installation = configuration.getInstallation();
        var modelWorkspace = configuration.getWorkspace();
        var stream = modelWorkspace.getStreams().get(0);
        if (installation.getProductVersion().eIsProxy() || !"4.40".equals(installation.getProductVersion().getName())
                || stream.eIsProxy() || !"r4_40".equals(stream.getName())) {
            throw new IllegalStateException("The pinned configuration must resolve SDK 4.40 and the R4_40 stream");
        }
        var user = SetupFactory.eINSTANCE.createUser();
        var context = SetupContext.create(installation, modelWorkspace, user);
        Map<String, String> values = new HashMap<>();
        values.put("jre.location-21", System.getProperty("sandbox.oomph.jdk21"));
        values.put("jre.location-25", System.getProperty("java.home"));
        values.put("installation.location", run.toString());
        values.put("installation.relativeProductFolder", "eclipse");
        values.put("workspace.location", run.resolve("workspace").toString());
        values.put("oomph.update.url", "https://download.eclipse.org/releases/2026-06/");
        values.put("github.user.id", "anonymous");
        values.put("github.author.name", "Sandbox QA");
        values.put("github.author.email", "sandbox-qa@example.invalid");
        values.put("sandbox.qa.sandbox.ref", "main"); // The existing candidate checkout supplies this clone.
        values.put("github.clone.sandbox.qa.location", checkout.toString());
        for (String component : List.of("core", "ui", "debug", "core.binaries")) {
            values.put("github.clone.jdt." + component + ".qa.location", run.resolve("eclipse.jdt." + component).toString());
        }
        values.forEach((key, value) -> {
            var task = SetupFactory.eINSTANCE.createVariableTask();
            task.setName(key);
            task.setValue(value);
            user.getSetupTasks().add(task);
        });
        var modelFiles = Map.<String, EObject>of("eclipse/configuration/org.eclipse.oomph.setup/installation.setup", installation,
                "workspace/.metadata/.plugins/org.eclipse.oomph.setup/workspace.setup", modelWorkspace, "user.setup", user);
        for (var entry : modelFiles.entrySet()) {
            Path file = run.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            var resource = resources.createResource(URI.createFileURI(file.toString()));
            resource.getContents().add(entry.getValue());
            resource.save(Map.of());
        }
        var callback = new UICallback(UIUtil.getShell(), "Pinned workspace setup") {
            @Override public void information(boolean async, String message) { System.out.println(message); }
            @Override public boolean question(String message) { throw new IllegalStateException(message); }
        };
        var performer = SetupTaskPerformer.create(resources.getURIConverter(), new SetupPrompter() {
            @Override public OS getOS() { return OS.INSTANCE; }
            @Override public String getVMPath() { return Path.of(System.getProperty("java.home"), "bin/java").toString(); }
            @Override public UserCallback getUserCallback() { return callback; }
            @Override public String getValue(VariableTask variable) { return null; }
            @Override public boolean promptVariables(List<? extends SetupTaskContext> performers) {
                for (var item : performers) {
                    for (var variable : ((SetupTaskPerformer) item).getUnresolvedVariables()) {
                        String value = values.getOrDefault(variable.getName(), variable.getDefaultValue());
                        if (value == null) {
                            throw new IllegalStateException("Missing Oomph variable " + variable.getName());
                        }
                        variable.setValue(value);
                    }
                }
                return true;
            }
        }, Trigger.STARTUP, context, false);
        performer.put(ILicense.class, Confirmer.ACCEPT);
        performer.setProgress(new ProgressLog() {
            @Override public boolean isCanceled() { return monitor.isCanceled(); }
            @Override public void log(String line) { System.out.println(line); }
            @Override public void log(String line, Severity severity) { log(severity + " " + line); }
            @Override public void log(String line, boolean filter) { log(line); }
            @Override public void log(String line, boolean filter, Severity severity) { log(line, severity); }
            @Override public void log(IStatus status) { log(status.toString()); }
            @Override public void log(Throwable error) { error.printStackTrace(); }
            @Override public void task(SetupTask task) { log("Oomph task: " + task); }
            @Override public void setTerminating() { }
        });
        // The CI checkout is already the Sandbox revision under test. Oomph owns all four JDT clones.
        // The two consumers need imported source and a resolved target, not a full JDT workspace build.
        performer.initNeededSetupTasks(monitor).removeIf(task -> task instanceof ProjectsBuildTask
                || task instanceof GitCloneTask && "github.clone.sandbox.qa".equals(task.getID()));
        performer.perform(monitor);
        if (!performer.hasSuccessfullyPerformed()) {
            throw new IllegalStateException("Oomph did not finish provisioning the pinned workspace");
        }
        if (!performer.getRestartReasons().isEmpty()) {
            Files.writeString(run.resolve("pinned-workspace.restart"), performer.getRestartReasons().toString());
            return;
        }
        var root = workspace.getRoot();
        var jobs = Job.getJobManager();
        var bundleContext = FrameworkUtil.getBundle(ITargetPlatformService.class).getBundleContext();
        var reference = bundleContext.getServiceReference(ITargetPlatformService.class);
        try {
            var target = bundleContext.getService(reference).getWorkspaceTargetDefinition();
            if (!"Sandbox upstream JDT migration QA target".equals(target.getName())) {
                throw new IllegalStateException("Unexpected active target " + target.getName());
            }
            var load = new LoadTargetDefinitionJob(target);
            load.schedule();
            load.join();
            if (!load.getResult().isOK() || target.getBundles() == null || target.getBundles().length == 0) {
                throw new IllegalStateException("Could not activate the pinned target: " + load.getResult());
            }
            jobs.join(ClasspathComputer.class, monitor);
        } finally {
            bundleContext.ungetService(reference);
        }
        jobs.beginRule(root, monitor);
        try {
            var project = root.getProject("org.eclipse.jdt.ui");
            if (!project.exists()) {
                throw new IllegalStateException("Oomph did not import org.eclipse.jdt.ui");
            }
            if (!project.isOpen()) {
                project.open(monitor);
            }
            project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            var source = project.getFile("ui/org/eclipse/jdt/internal/ui/text/java/ProposalSorterHandle.java");
            if (!source.isAccessible() || !Files.isSameFile(project.getLocation().toFile().toPath(),
                    run.resolve("eclipse.jdt.ui/org.eclipse.jdt.ui"))) {
                throw new IllegalStateException("Missing retained JDT UI source at " + source.getFullPath());
            }
            var javaProject = JavaCore.create(project);
            var status = javaProject.findType("org.eclipse.core.runtime.Status");
            if (status == null || !status.isBinary() || javaProject.findType("java.lang.String") == null) {
                throw new IllegalStateException("The imported JDT project must resolve the target Status and Java system library");
            }
            workspace.save(true, monitor);
        } finally {
            jobs.endRule(root);
        }
    }
}
