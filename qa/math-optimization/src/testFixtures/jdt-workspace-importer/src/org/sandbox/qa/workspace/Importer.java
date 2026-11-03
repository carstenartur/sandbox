package org.sandbox.qa.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.JavaCore;

/** Temporary QA bootstrap: imports actual projects; contains no cleanup or test logic. */
public final class Importer implements IApplication {
    @Override public Object start(IApplicationContext context) throws Exception {
        String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        if (args == null || args.length == 0) throw new IllegalArgumentException("Supply existing project directories");
        var monitor = new NullProgressMonitor();
        var workspace = ResourcesPlugin.getWorkspace();
        for (String arg : args) {
            Path directory = Path.of(arg).toRealPath();
            Path file = directory.resolve(".project");
            if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Missing .project: " + file);
            IProjectDescription description = workspace.loadProjectDescription(
                    org.eclipse.core.runtime.Path.fromOSString(file.toString()));
            IProject project = workspace.getRoot().getProject(description.getName());
            if (!project.exists()) {
                description.setLocation(org.eclipse.core.runtime.Path.fromOSString(directory.toString()));
                project.create(description, monitor);
            } else if (!directory.equals(project.getLocation().toFile().toPath().toRealPath())) {
                throw new IllegalStateException("Project name already maps to another location: " + project.getName());
            }
            if (!project.isOpen()) project.open(monitor);
            project.refreshLocal(org.eclipse.core.resources.IResource.DEPTH_INFINITE, monitor);
            System.out.println("IMPORTED " + project.getName() + " " + directory);
        }
        JavaCore.initializeAfterLoad(monitor);
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_REFRESH, monitor);
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
        for (IProject project : workspace.getRoot().getProjects()) {
            if (project.isOpen() && project.hasNature(JavaCore.NATURE_ID)) {
                var javaProject = JavaCore.create(project);
                var entries = javaProject.getResolvedClasspath(false);
                System.out.println("CLASSPATH " + project.getName() + " " + entries.length);
                for (var entry : entries) System.out.println("  " + entry);
            }
        }
        var status = workspace.save(true, monitor);
        if (!status.isOK()) throw new org.eclipse.core.runtime.CoreException(status);
        System.out.println("WORKSPACE_SAVED");
        return IApplication.EXIT_OK;
    }
    @Override public void stop() { }
}
