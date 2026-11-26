package org.sandbox.oomph.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.oomph.setup.Installation;
import org.eclipse.oomph.setup.Workspace;
import org.eclipse.oomph.setup.internal.core.SetupContext;
import org.eclipse.oomph.setup.internal.core.util.SetupCoreUtil;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.internal.core.PluginModelManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.osgi.framework.FrameworkUtil;

/** Observer/driver loaded as early-startup extension of the ordinary EPP product. */
public final class PublicWorkbenchProbe implements IStartup {
    private final Path evidence = Path.of(System.getProperty("sandbox.public.evidence"));
    private final String phase = System.getProperty("sandbox.public.phase");
    private final String expected = System.getProperty("sandbox.public.commit");
    private final Display display = PlatformUI.getWorkbench().getDisplay();

    @Override public void earlyStartup() {
        Thread driver = new Thread(this::run, "public-epp-acceptance");
        driver.setDaemon(true);
        driver.start();
    }

    private void run() {
        try {
            require("org.eclipse.epp.package.java.product".equals(Platform.getProduct().getId()),
                    "Unexpected running product " + Platform.getProduct());
            require(Runtime.version().feature() == 25, "Expected Java 25: " + Runtime.version());
            Path runningJdk = Path.of(System.getProperty("java.home")).toRealPath();
            require(runningJdk.equals(Path.of(System.getProperty("sandbox.public.jdk")).toRealPath())
                    && Files.isExecutable(runningJdk.resolve("bin/javac")), "The Java IDE is not running the selected full JDK: " + runningJdk);
            var root = ResourcesPlugin.getWorkspace().getRoot();
            boolean fresh = "fresh".equals(phase);
            var sdkLaunch = root.getProject("sandbox_target").getFile("Regelsuche-SDK.launch");
            if (fresh || sdkLaunch.exists()) {
                // Show the normal asynchronous startup progress so its own success receipt can be observed.
                // A public main with the SDK bootstrap also schedules STARTUP on restart.
                String startup = fresh ? "startup" : "restart-startup";
                awaitUi(() -> {
                    var log = key(StyledText.class, "log");
                    if (log == null) return false;
                    checkOomphStatus(log, startup);
                    Button dismiss = key(Button.class, "dismiss");
                    if (dismiss != null && dismiss.getSelection()) { dismiss.setSelection(false); press(dismiss); }
                    Button finish = key(Button.class, "finish");
                    if (finish == null || !finish.isEnabled()) return false;
                    String transcript = log.getText();
                    if (fresh) require(transcript.contains("Sandbox Maven projects"), "Missing Maven import in native STARTUP: " + transcript);
                    if (sdkLaunch.exists()) {
                        Path privateRepo = Path.of(System.getProperty("user.home"), ".m2", "repository");
                        require(transcript.contains("Verified qualified Regelsuche jars and descriptors in " + privateRepo),
                                "Missing qualified SDK in the isolated Maven repository: " + transcript);
                    }
                    Files.writeString(evidence.resolve(startup + "-tasks.log"), transcript);
                    screenshot(startup + "-complete");
                    press(finish);
                    return true;
                }, "successful public STARTUP tasks");
            }
            await(() -> root.getProject("sandbox_target").isOpen(), "initial target project");
            // Both imports previously went missing during an interrupted public STARTUP.
            await(() -> root.getProject("sandbox_oomph").isOpen() && root.getProject("sandbox_distribution_verify").isOpen(),
                    "fresh Oomph Maven imports");
            var launcher=Path.of(System.getProperty("eclipse.launcher")).toRealPath();
            var expectedLauncher=Path.of(System.getProperty("sandbox.public.launcher")).toRealPath();
            require(launcher.equals(expectedLauncher),"Wrong installed Eclipse executable: "+launcher);
            var models = SetupCoreUtil.createResourceSet();
            // Demand-load the actual BOOTSTRAP files. Empty in-memory resources cannot prove the chosen stream.
            var workspaceModel = (Workspace) models.getResource(SetupContext.WORKSPACE_SETUP_URI, true).getContents().getFirst();
            var installationModel = (Installation) models.getResource(SetupContext.INSTALLATION_SETUP_URI, true).getContents().getFirst();
            var chosen=workspaceModel.getStreams();
            String publicEntry="https://raw.githubusercontent.com/carstenartur/sandbox/main/sandbox_oomph/sandboxproject.setup";
            require(chosen.size()==1 && !chosen.getFirst().eIsProxy() && "main".equals(chosen.getFirst().getName())
                    && publicEntry.equals(chosen.getFirst().eResource().getURI().toString()),
                    "Unexpected stream in installed workspace: "+chosen);
            var pv=installationModel.getProductVersion();
            require(pv!=null && !pv.eIsProxy() && pv.getLabel().contains("2026-09"),"Wrong installed EPP product model: "+pv);
            Files.writeString(evidence.resolve(phase+"-identity.txt"),launcher+"\n"+pv.eResource().getURI()+" "+pv.getLabel()
                    +"\n"+chosen.getFirst().eResource().getURI()+" "+chosen.getFirst().getName()+"\n");
            if ("restart".equals(phase)) {
                command("org.eclipse.oomph.setup.editor.perform");
                awaitUi(() -> {
                    Button all=key(Button.class, "showAllTasks");
                    if (all==null) return false;
                    if (!all.getSelection()) { all.setSelection(true); press(all); }
                    for (Tree tree: visible(Tree.class)) {
                        if ((tree.getStyle() & SWT.CHECK)==0) continue;
                        TreeItem task=treeItem(tree,tree.getItems(),"Sandbox Maven projects");
                        if (task==null) continue;
                        if (!task.getChecked()) {
                            task.setChecked(true);
                            Event e=new Event(); e.item=task; e.detail=SWT.CHECK; tree.notifyListeners(SWT.Selection,e);
                        }
                        Button confirm=key(Button.class,"finish");
                        if (confirm==null || !confirm.isEnabled()) return false;
                        screenshot("manual-selected");
                        press(confirm); return true;
                    }
                    return false;
                }, "manual Sandbox Maven projects task");
                awaitUi(() -> {
                    var log=key(StyledText.class,"log");
                    if(log==null) return false;
                    checkOomphStatus(log, "manual");
                    var dismiss=key(Button.class,"dismiss");
                    if (dismiss!=null && dismiss.getSelection()) { dismiss.setSelection(false); press(dismiss); }
                    Button finish=key(Button.class,"finish");
                    if(finish==null || !finish.isEnabled()) return false;
                    if(!log.getText().contains("Sandbox Maven projects")) throw new AssertionError("Missing manual task: "+log.getText());
                    Files.writeString(evidence.resolve("manual.log"),log.getText());
                    screenshot("manual-complete"); press(finish); return true;
                }, "completed MANUAL operation");
                java.util.concurrent.CountDownLatch updated=new java.util.concurrent.CountDownLatch(1);
                try (var update = new MavenUpdateWitness(updated)) {
                    String updateCommand = "org.eclipse.m2e.core.ui.command.updateProject";
                    awaitUi(() -> {
                        var activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                        if (activeWindow == null || activeWindow.getActivePage() == null) return false;
                        var page = activeWindow.getActivePage();
                        var explorer = page.showView(IPageLayout.ID_PROJECT_EXPLORER);
                        page.activate(explorer);
                        explorer.setFocus();
                        var realCommand = activeWindow.getService(org.eclipse.ui.commands.ICommandService.class)
                                .getCommand(updateCommand);
                        return page.getActivePart() == explorer && realCommand.isEnabled();
                    }, "enabled workspace Maven Update Project command", 2);
                    command(updateCommand);
                    awaitUi(() -> {
                        Shell active=display.getActiveShell();
                        if(active==null || !active.getText().equals("Update Maven Project")) return false;
                        List<Button> buttons=new ArrayList<>(); collect(active,Button.class,buttons);
                        Button select=buttons.stream().filter(b -> b.getText().replace("&","").equals("Select All")).findFirst().orElse(null);
                        Button ok=buttons.stream().filter(b -> b.getText().equals("OK")).findFirst().orElse(null);
                        if(select==null || ok==null) return false;
                        press(select);
                        verifyUpdateProjectSelection(active);
                        screenshot("maven-all-projects");
                        press(ok);
                        return true;
                    }, "normal Maven > Update Project dialog", 3);
                    require(updated.await(55, TimeUnit.MINUTES), "m2e UpdateMavenProjectJob did not complete");
                    require(update.result != null && update.result.isOK(), "m2e update failed: "+update.result);
                }
            } else {
                require("fresh".equals(phase),"Unknown phase: "+phase);
            }
            settle();
            var targetReference=FrameworkUtil.getBundle(ITargetPlatformService.class).getBundleContext();
            var targetService=targetReference.getServiceReference(ITargetPlatformService.class);
            require(targetService!=null,"PDE target service missing");
            try {
                var target=targetReference.getService(targetService).getWorkspaceTargetDefinition();
                require("target platform for sandbox".equals(target.getName()) && target.isResolved() && target.getStatus().isOK(),
                        "Unresolved or wrong target: "+target.getName()+" "+target.getStatus());
            } finally { targetReference.ungetService(targetService); }
            var ee=JavaRuntime.getExecutionEnvironmentsManager().getEnvironment("JavaSE-25");
            require(ee!=null && ee.getCompatibleVMs().length>0,"No workspace JavaSE-25 VM");
            var host=root.getProject("sandbox_extra_search");
            var fragment=root.getProject("sandbox_extra_search_test");
            require(host.isOpen() && fragment.isOpen() && fragment.hasNature(JavaCore.NATURE_ID)
                    && fragment.hasNature("org.eclipse.pde.PluginNature"),"Git search fragment/host import broken");
            var type=JavaCore.create(fragment).findType("org.sandbox.jdt.internal.ui.search.gitindex.GitSearchView");
            require(type!=null && host.equals(type.getJavaProject().getProject()),"GitSearchView is not resolved through the workspace host");
            Path clone=host.getLocation().toFile().toPath();
            require(expected.equals(git(clone,"rev-parse","HEAD")), "Public clone did not resolve expected main "+expected);
            require("https://github.com/carstenartur/sandbox.git".equals(git(clone,"remote","get-url","origin")),"Unexpected public Git origin");
            settle();
            verifyInventory(Path.of(git(clone, "rev-parse", "--show-toplevel")));
            var errors = new ArrayList<String>();
            for (var marker: root.findMarkers(IMarker.PROBLEM,true,IResource.DEPTH_INFINITE)) {
                if(marker.getAttribute(IMarker.SEVERITY,0)==IMarker.SEVERITY_ERROR)
                    errors.add(marker.getResource().getFullPath()+" "+marker.getAttribute(IMarker.MESSAGE,""));
            }
            Files.write(evidence.resolve(phase+"-errors.txt"),errors);
            display.syncExec(() -> {
                try {
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(IPageLayout.ID_PROBLEM_VIEW);
                    screenshot(phase+"-problems");
                } catch(Exception exception) { throw new IllegalStateException(exception); }
            });
            require(errors.isEmpty(), "Workspace Errors: "+errors);
            Files.writeString(evidence.resolve(phase+".ok"),expected+" "+type.getPath()+" "+root.getProjects().length+" projects\n");
            display.asyncExec(() -> PlatformUI.getWorkbench().close());
        } catch(Throwable failure) {
            try (var out = new java.io.PrintWriter(Files.newBufferedWriter(evidence.resolve(phase+".failed")))) { failure.printStackTrace(out); }
            catch(Exception ignored) { failure.printStackTrace(); }
            Runtime.getRuntime().halt(71);
        }
    }

    private void verifyUpdateProjectSelection(Shell dialog) throws Exception {
        Set<String> expectedProjects = new TreeSet<>();
        for (var project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (project.isAccessible() && project.hasNature("org.eclipse.m2e.core.maven2Nature")) {
                expectedProjects.add(project.getName());
            }
        }
        List<Tree> trees = new ArrayList<>();
        collect(dialog, Tree.class, trees);
        Tree tree = trees.stream().filter(widget -> (widget.getStyle() & SWT.CHECK) != 0).findFirst().orElseThrow();
        Set<String> checked = new TreeSet<>();
        var pending = new java.util.ArrayDeque<TreeItem>(List.of(tree.getItems()));
        while (!pending.isEmpty()) {
            TreeItem node = pending.removeFirst();
            if (node.getData() instanceof org.eclipse.core.resources.IProject project && node.getChecked()) {
                checked.add(project.getName());
            }
            pending.addAll(List.of(node.getItems()));
        }
        Files.write(evidence.resolve("maven-update-expected-projects.txt"), expectedProjects);
        Files.write(evidence.resolve("maven-update-checked-projects.txt"), checked);
        require(!checked.isEmpty() && checked.equals(expectedProjects), "Update Maven Project selection differs from the workspace");
    }

    private void checkOomphStatus(StyledText log, String trigger) throws Exception {
        if (log.getShell().getData("org.eclipse.oomph.setup.status") instanceof IStatus status
                && status.matches(IStatus.ERROR | IStatus.CANCEL)) {
            Files.writeString(evidence.resolve(trigger + "-tasks.log"), log.getText());
            throw new IllegalStateException("Oomph " + trigger + " failed: " + status);
        }
    }

    private void verifyInventory(Path clone) throws Exception {
        var workspace = ResourcesPlugin.getWorkspace();
        String descriptions = git(clone, "ls-files", "--", ".project", ":(glob)**/.project");
        List<Path> committedDescriptions = descriptions.lines().map(Path::of).toList();
        Set<String> expectedProjects = PublicProjectInventory.expectedProjects(clone, committedDescriptions, path -> {
            String xml = git(clone, "show", "HEAD:" + path);
            try (var input = new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                return workspace.loadProjectDescription(input).getName();
            }
        });
        Set<String> actual = new TreeSet<>();
        for (var project : workspace.getRoot().getProjects()) {
            actual.add(project.getName() + (project.isOpen() ? "" : " [closed]"));
        }
        Files.write(evidence.resolve(phase + "-expected-projects.txt"), expectedProjects);
        Files.write(evidence.resolve(phase + "-projects.txt"), actual);
        Set<String> missing = new TreeSet<>(expectedProjects);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(expectedProjects);
        require(missing.isEmpty() && extra.isEmpty(), "Workspace project inventory: missing=" + missing + " extra=" + extra);
    }

    private static String git(Path clone,String... args) throws Exception {
        var command = new ArrayList<>(List.of("git","-C",clone.toString())); command.addAll(List.of(args));
        var child=new ProcessBuilder(command).redirectErrorStream(true).start();
        require(child.waitFor(60,TimeUnit.SECONDS) && child.exitValue()==0,"Git failed: "+command);
        return new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8).strip();
    }
    private void command(String id) {
        display.asyncExec(() -> {
            try { PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(IHandlerService.class).executeCommand(id,null); }
            catch(Exception e) { throw new IllegalStateException(id,e); }
        });
    }
    private static void settle() throws InterruptedException {
        var jobs=Job.getJobManager();
        var families=List.of(PluginModelManager.class,ResourcesPlugin.FAMILY_MANUAL_BUILD,ResourcesPlugin.FAMILY_AUTO_BUILD);
        do { for(Object family:families) jobs.join(family,new NullProgressMonitor()); }
        while(Arrays.stream(jobs.find(null)).anyMatch(job -> families.stream().anyMatch(job::belongsTo)));
    }
    static final class MavenUpdateWitness extends org.eclipse.core.runtime.jobs.JobChangeAdapter implements AutoCloseable {
        final java.util.concurrent.CountDownLatch done;
        volatile IStatus result;
        MavenUpdateWitness(java.util.concurrent.CountDownLatch done) { this.done=done; Job.getJobManager().addJobChangeListener(this); }
        @Override public void done(org.eclipse.core.runtime.jobs.IJobChangeEvent e) {
            if(e.getJob().getClass().getName().equals("org.eclipse.m2e.core.ui.internal.UpdateMavenProjectJob")) {
                result=e.getResult(); done.countDown();
            }
        }
        @Override public void close() { Job.getJobManager().removeJobChangeListener(this); }
    }
    private static void await(java.util.concurrent.Callable<Boolean> test,String name) throws Exception {
        await(test, name, 50);
    }
    private static void await(java.util.concurrent.Callable<Boolean> test, String name, int minutes) throws Exception {
        long end=System.nanoTime()+TimeUnit.MINUTES.toNanos(minutes);
        do { if(test.call()) return; Thread.sleep(500); } while(System.nanoTime()<end);
        throw new AssertionError("Timed out waiting for "+name);
    }
    private void awaitUi(java.util.concurrent.Callable<Boolean> test,String name) throws Exception {
        awaitUi(test, name, 50);
    }
    private void awaitUi(java.util.concurrent.Callable<Boolean> test, String name, int minutes) throws Exception {
        await(() -> {
            var result=new java.util.concurrent.FutureTask<Boolean>(test);
            display.syncExec(result); return result.get();
        },name,minutes);
    }
    private static TreeItem treeItem(Tree tree,TreeItem[] items,String text) {
        for(TreeItem item:items) {
            if(item.getText().contains(text)) return item;
            if(!item.getExpanded()) { item.setExpanded(true); Event e=new Event(); e.item=item; tree.notifyListeners(SWT.Expand,e); }
            TreeItem sub=treeItem(tree,item.getItems(),text);
            if(sub!=null) return sub;
        }
        return null;
    }
    private static <T extends Control> void collect(Control c,Class<T> t,List<T> list) {
        if(c.isDisposed()||!c.isVisible()) return;
        if(t.isInstance(c)) list.add(t.cast(c));
        if(c instanceof Composite parent) for(Control child: parent.getChildren()) collect(child,t,list);
    }
    private <T extends Control> List<T> visible(Class<T> t) {
        List<T> list=new ArrayList<>(); for(Shell s:display.getShells()) collect(s,t,list); return list;
    }
    private <T extends Control> T key(Class<T> t,String k) {
        return visible(t).stream().filter(w -> k.equals(w.getData("org.eclipse.swtbot.widget.key"))).findFirst().orElse(null);
    }
    private static void press(Button b) { b.notifyListeners(SWT.Selection,new Event()); }
    private void screenshot(String id) {
        Shell shell=display.getActiveShell();
        if(shell==null) shell=Arrays.stream(display.getShells()).filter(Shell::isVisible).findFirst().orElseThrow();
        var area=shell.getClientArea(); Image image=new Image(display,area.width,area.height);
        try { GC gc=new GC(shell); try { gc.copyArea(image,0,0); } finally { gc.dispose(); }
            ImageLoader loader=new ImageLoader(); loader.data=new ImageData[]{image.getImageData()}; loader.save(evidence.resolve(id+".png").toString(),SWT.IMAGE_PNG);
        } finally { image.dispose(); }
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
