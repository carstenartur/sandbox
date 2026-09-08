/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.oomph.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.oomph.internal.setup.SetupPrompter;
import org.eclipse.oomph.setup.Configuration;
import org.eclipse.oomph.setup.ProductVersion;
import org.eclipse.oomph.setup.Project;
import org.eclipse.oomph.setup.ProjectCatalog;
import org.eclipse.oomph.setup.SetupFactory;
import org.eclipse.oomph.setup.SetupTaskContext;
import org.eclipse.oomph.setup.Trigger;
import org.eclipse.oomph.setup.VariableTask;
import org.eclipse.oomph.setup.git.GitCloneTask;
import org.eclipse.oomph.setup.internal.core.SetupContext;
import org.eclipse.oomph.setup.internal.core.SetupTaskPerformer;
import org.eclipse.oomph.setup.internal.core.util.SetupCoreUtil;
import org.eclipse.oomph.util.OS;
import org.eclipse.oomph.util.UserCallback;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.internal.launching.launcher.BundleLauncherHelper;
import org.eclipse.pde.internal.launching.launcher.LaunchValidationOperation;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.osgi.framework.FrameworkUtil;

/** Executed in a real SDK with Oomph, m2e, PDE and a running workbench. */
public class SetupProbe implements IApplication {
    private static final String ENTRY = "https://raw.githubusercontent.com/carstenartur/sandbox/main/"
            + "sandbox_oomph/sandboxproject.setup";
    private volatile Throwable failure;

    @Override
    public Object start(IApplicationContext applicationContext) {
        Display display = PlatformUI.createDisplay();
        try {
            PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor() {
                @Override
                public String getInitialWindowPerspectiveId() {
                    return "org.eclipse.jdt.ui.JavaPerspective";
                }

                @Override
                public void postStartup() {
                    new Job("Verify Sandbox Oomph setup") {
                        @Override
                        protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                            try {
                                verify();
                            } catch (Throwable t) {
                                failure = t;
                                t.printStackTrace();
                            } finally {
                                display.asyncExec(() -> PlatformUI.getWorkbench().close());
                            }
                            return Status.OK_STATUS;
                        }
                    }.schedule();
                }
            });
        } finally {
            display.dispose();
        }
        return failure == null ? EXIT_OK : Integer.valueOf(1);
    }

    private void verify() throws Exception {
        Path root = Path.of(System.getProperty("sandbox.oomph.root"));
        Path run = root.resolve("sandbox_oomph/target/oomph-runtime");
        Path clone = run.resolve("checkout");
        boolean update = "update".equals(System.getProperty("sandbox.oomph.phase"));
        var workspace = ResourcesPlugin.getWorkspace();
        var monitor = new NullProgressMonitor() {
            @Override public void subTask(String name) {
                if (name != null && !name.isBlank()) {
                    System.out.println("Oomph: " + name);
                }
            }
            @Override public void setTaskName(String name) { subTask(name); }
        };
        ResourceSet rs = SetupCoreUtil.createResourceSet();
        System.out.println("Loading and validating the official catalog and candidate models");
        rs.getURIConverter().getURIMap().put(URI.createURI(ENTRY),
                URI.createFileURI(root.resolve("sandbox_oomph/sandboxproject.setup").toString()));
        ProjectCatalog catalog = (ProjectCatalog) rs.getEObject(URI.createURI(
                "index:/org.eclipse.setup#//@projectCatalogs[name='com.github']"), true);
        require(catalog != null && !catalog.eIsProxy(), "Official Github Projects catalog is unavailable");
        Project project = null;
        // Resolve only Sandbox; the catalog contains unrelated external repositories.
        var entries = (org.eclipse.emf.ecore.util.InternalEList<Project>) catalog.getProjects();
        for (int i = 0; i < entries.size(); i++) {
            Project candidate = entries.basicGet(i);
            URI proxy = ((InternalEObject) candidate).eProxyURI();
            if (proxy != null && ENTRY.equals(proxy.trimFragment().toString())) {
                project = catalog.getProjects().get(i);
                break;
            }
        }
        require(project != null && !project.eIsProxy(), "Official catalog entry no longer resolves");
        validate(project.eResource());
        for (String file : List.of("sandbox.setup", "sandbox-installer.setup",
                "jdt-migration-qa.setup", "jdt-migration-qa.configuration.setup")) {
            validate(rs.getResource(URI.createFileURI(root.resolve("sandbox_oomph/" + file).toString()), true));
        }
        ProductVersion sdk = (ProductVersion) rs.getEObject(URI.createURI("index:/org.eclipse.setup"
                + "#//@productCatalogs[name='org.eclipse.applications']/@products[name='eclipse.platform.sdk']"
                + "/@versions[name='4.40']"), true);
        require(sdk != null && !sdk.eIsProxy(), "Official SDK 4.40 product is unavailable");
        var stream = project.getStreams().stream().filter(s -> "main".equals(s.getName())).findFirst().orElseThrow();
        // Exercise the candidate branch through the real GitCloneTask; public setup still tracks main.
        var contents = project.eAllContents();
        while (contents.hasNext()) {
            if (contents.next() instanceof GitCloneTask git) {
                require("https://github.com/carstenartur/sandbox.git".equals(git.getRemoteURI()), "Public clone URL changed");
                git.setCheckoutBranch(System.getProperty("sandbox.oomph.ref"));
                git.setRemoteURI(System.getProperty("sandbox.oomph.repository", git.getRemoteURI()));
            }
        }
        SetupContext context = SetupContext.create(sdk, stream);
        Map<String, String> values = Map.ofEntries(
                Map.entry("git.clone.sandbox.location", clone.toString()),
                Map.entry("jre.location-21", System.getProperty("java.home")),
                Map.entry("installation.location", run.toString()),
                Map.entry("installation.relativeProductFolder", "eclipse"),
                Map.entry("workspace.location", workspace.getRoot().getLocation().toOSString()),
                Map.entry("oomph.update.url", "https://download.eclipse.org/oomph/updates/release/latest/"),
                Map.entry("github.user.id", "anonymous"),
                Map.entry("github.author.name", "Oomph verification"),
                Map.entry("github.author.email", "oomph-verification@example.invalid"));
        for (var value : values.entrySet()) {
            VariableTask task = SetupFactory.eINSTANCE.createVariableTask();
            task.setName(value.getKey());
            task.setValue(value.getValue());
            context.getUser().getSetupTasks().add(task);
        }
        rs.createResource(URI.createFileURI(run.resolve("installation.setup").toString()))
                .getContents().add(context.getInstallation());
        rs.createResource(URI.createFileURI(run.resolve("workspace.setup").toString()))
                .getContents().add(context.getWorkspace());
        rs.createResource(URI.createFileURI(run.resolve("user.setup").toString()))
                .getContents().add(context.getUser());
        var sentinel = workspace.getRoot().getProject("UserOwnedProject");
        if (update) {
            require(sentinel.exists(), "User project was not persisted across restart");
            var missing = workspace.getRoot().getProject("sandbox_distribution_verify");
            require(missing.exists(), "Fresh setup did not import distribution verification");
            missing.delete(false, true, monitor);
        } else {
            sentinel.create(monitor);
            sentinel.open(monitor);
            Files.writeString(Path.of(sentinel.getLocation().toOSString(), "keep.txt"), "user content");
        }
        var performer = SetupTaskPerformer.create(rs.getURIConverter(), new SetupPrompter() {
            @Override public OS getOS() { return OS.INSTANCE; }
            @Override public String getVMPath() { return Path.of(System.getProperty("java.home"), "bin/java").toString(); }
            @Override public UserCallback getUserCallback() { return null; }
            @Override public String getValue(VariableTask variable) { return values.get(variable.getName()); }
            @Override public boolean promptVariables(List<? extends SetupTaskContext> performers) {
                var unresolved = performers.stream()
                        .flatMap(p -> ((SetupTaskPerformer) p).getUnresolvedVariables().stream()).toList();
                require(unresolved.isEmpty(), "Unexpected setup questions: " + unresolved);
                return true;
            }
        }, update ? Trigger.MANUAL : Trigger.STARTUP, context, false);
        require(performer != null, "Setup was cancelled");
        System.out.println("Executing Oomph " + (update ? "MANUAL" : "STARTUP") + " tasks");
        performer.perform(monitor);
        System.out.println("Checking imported projects, target and workspace build markers");
        require(performer.hasSuccessfullyPerformed(), "Setup did not complete");
        workspace.save(true, monitor);
        require(Files.readString(Path.of(sentinel.getLocation().toOSString(), "keep.txt")).equals("user content"),
                "Setup changed user-owned project content");
        for (String name : List.of("sandbox_common_core", "sandbox-functional-converter-core", "sandbox_common",
                "sandbox_functional_converter", "sandbox_functional_converter_test", "sandbox_int_to_enum",
                "sandbox_int_to_enum_help", "sandbox_distribution_verify", "sandbox_oomph", "sandbox_target")) {
            require(workspace.getRoot().getProject(name).isOpen(), "Missing imported project: " + name);
        }
        require(Arrays.stream(workspace.getRoot().getProjects()).noneMatch(p -> p.getLocation() != null
                && p.getLocation().toOSString().startsWith(clone.resolve(".github").toString())),
                "CI fixture must not be imported into the contributor workspace");
        var ee = JavaRuntime.getExecutionEnvironmentsManager().getEnvironment("JavaSE-21");
        require(ee != null && ee.getCompatibleVMs().length > 0, "JavaSE-21 is not configured");
        var bundleContext = FrameworkUtil.getBundle(ITargetPlatformService.class).getBundleContext();
        var reference = bundleContext.getServiceReference(ITargetPlatformService.class);
        require(reference != null, "PDE target service is unavailable");
        var service = bundleContext.getService(reference);
        var target = service.getWorkspaceTargetDefinition();
        require("target platform for sandbox".equals(target.getName()), "Wrong active target: " + target.getName());
        require(target.isResolved() && target.getStatus().isOK(), "Unresolved target: " + target.getStatus());
        bundleContext.ungetService(reference);
        var errors = Arrays.stream(workspace.getRoot().findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE))
                .filter(m -> m.getAttribute(IMarker.SEVERITY, 0) == IMarker.SEVERITY_ERROR)
                .map(m -> m.getResource().getFullPath() + ": " + m.getAttribute(IMarker.MESSAGE, ""))
                .collect(Collectors.toCollection(TreeSet::new));
        require(errors.isEmpty(), "Workspace build errors:\n" + String.join("\n", errors));
        var launchFile = workspace.getRoot().getProject("sandbox_product").getFile("sandbox.product.launch");
        var launch = DebugPlugin.getDefault().getLaunchManager().getLaunchConfiguration(launchFile);
        var launchModels = BundleLauncherHelper.getMergedBundleMap(launch, false).keySet();
        require(launchModels.stream().anyMatch(m -> "sandbox_int_to_enum".equals(m.getPluginBase().getId())
                && m.getUnderlyingResource() != null), "Development launch must include workspace cleanup plug-ins");
        var validation = new LaunchValidationOperation(launch, launchModels);
        validation.run(monitor);
        require(!validation.isEmpty() && !validation.hasErrors(), "Invalid development launch: "
                + validation.getInput().entrySet().stream()
                        .map(e -> e.getKey() + ": " + Arrays.toString(e.getValue())).collect(Collectors.joining("\n")));
        try (var repository = new FileRepositoryBuilder().setGitDir(clone.resolve(".git").toFile()).build()) {
            require(System.getProperty("sandbox.oomph.repository", "https://github.com/carstenartur/sandbox.git").equals(repository.getConfig().getString("remote", "origin", "url")),
                    "Unexpected cloned repository");
            String expectedCommit = System.getProperty("sandbox.oomph.commit", "");
            require(expectedCommit.isEmpty() || expectedCommit.equals(repository.resolve("HEAD").name()),
                    "The clone must contain the exact candidate commit: " + expectedCommit);
        }
        var workingSets = PlatformUI.getWorkbench().getWorkingSetManager();
        require(workingSets.getWorkingSet("Sandbox Core") != null, "Missing dynamic working sets");
        Properties result = new Properties();
        result.setProperty("result", "passed");
        result.setProperty("projects", Integer.toString(workspace.getRoot().getProjects().length));
        result.setProperty("target", target.getName());
        try (var out = Files.newOutputStream(run.resolve(update ? "update.properties" : "fresh.properties"))) {
            result.store(out, "Real Oomph workspace verification");
        }
        System.out.println("OOMPH VERIFIED: " + result);
    }

    private static void validate(Resource resource) {
        require(resource.getErrors().isEmpty(), resource.getURI() + ": " + resource.getErrors());
        for (EObject object : resource.getContents()) {
            Diagnostic diagnostic = Diagnostician.INSTANCE.validate(object);
            require(diagnostic.getSeverity() < Diagnostic.ERROR, resource.getURI() + ": " + diagnostic);
            if (object instanceof Configuration config) {
                require(config.getInstallation() != null && config.getWorkspace() != null,
                        "Configuration must contain both installation and workspace");
                require(!config.getInstallation().getProductVersion().eIsProxy(), "Unresolved product reference");
                require(!config.getWorkspace().getStreams().get(0).eIsProxy(), "Unresolved project stream");
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @Override
    public void stop() {
        if (PlatformUI.isWorkbenchRunning()) {
            PlatformUI.getWorkbench().getDisplay().asyncExec(() -> PlatformUI.getWorkbench().close());
        }
    }
}
