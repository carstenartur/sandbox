/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.ClasspathComputer;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.ResourceSnapshot;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.SourceSnapshot;
import org.sandbox.jdt.internal.common.MigrationSupportProvider;
import org.sandbox.jdt.internal.common.PdeManifestImportUpdater;

/** Temporarily exposes a real active target provider while JDT resolves candidate registrations. */
final class MixedSupportImport implements AutoCloseable {
	private final ResourceSnapshot descriptor;
	private final byte[] staged;
	private final IProgressMonitor monitor;

	private MixedSupportImport(ResourceSnapshot descriptor, byte[] staged, IProgressMonitor monitor) {
		this.descriptor= descriptor;
		this.staged= staged;
		this.monitor= monitor;
	}

	static MixedSupportImport stage(IJavaProject project, List<ResourceSnapshot> resources, List<SourceSnapshot> sources,
			Map<String, String> options, IProgressMonitor monitor) throws IOException, CoreException {
		if (resources.isEmpty() || !"META-INF/MANIFEST.MF".equals(resources.getFirst().relativePath()) //$NON-NLS-1$
				|| !Boolean.parseBoolean(options.get("cleanup.junitcleanup")) //$NON-NLS-1$
				|| !(Boolean.parseBoolean(options.get("cleanup.junitcleanup_4_ruleexternalresource")) //$NON-NLS-1$
						|| Boolean.parseBoolean(options.get("cleanup.junitcleanup_4_externalresource"))) //$NON-NLS-1$
				|| !project.getProject().hasNature("org.eclipse.pde.PluginNature") //$NON-NLS-1$
				|| Platform.getBundle("org.eclipse.pde.core") == null) return null; //$NON-NLS-1$
		// Projects without an apparent class rule cannot require the mixed strategy.
		// The JUnit planner subsequently proves both annotations and their common source hierarchy.
		boolean possible= false;
		for (var source : sources) {
			if (source.unit().getSource().contains("ClassRule")) { possible= true; break; } //$NON-NLS-1$
		}
		if (!possible || !Target.hasProvider()) return null;
		if (MigrationSupportProvider.available(project)
				&& project.findType("org.junit.jupiter.api.TestInstance") != null //$NON-NLS-1$
				&& project.findType("org.junit.jupiter.api.extension.RegisterExtension") != null //$NON-NLS-1$
				&& project.findType("org.junit.jupiter.api.parallel.Isolated") != null //$NON-NLS-1$
				&& project.findType("org.junit.jupiter.api.function.ThrowingConsumer") != null) return null; //$NON-NLS-1$
		var descriptor= resources.getFirst();
		byte[] staged= descriptor.before();
		for (String name : List.of(MigrationSupportProvider.PACKAGE, "org.junit.jupiter.api", //$NON-NLS-1$
				"org.junit.jupiter.api.extension", "org.junit.jupiter.api.parallel", "org.junit.jupiter.api.function")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			staged= PdeManifestImportUpdater.addImport(staged, name);
		}
		if (Arrays.equals(staged, descriptor.before())) return null;
		var transaction= new MixedSupportImport(descriptor, staged, monitor);
		transaction.write(descriptor.before(), staged);
		try {
			Target.awaitClasspath(monitor);
			return transaction;
		} catch (RuntimeException e) {
			try { transaction.close(); } catch (CoreException restore) { e.addSuppressed(restore); }
			throw e;
		}
	}

	private void write(byte[] expected, byte[] replacement) throws CoreException {
		var file= descriptor.file();
		var rules= file.getWorkspace().getRuleFactory();
		var rule= MultiRule.combine(rules.modifyRule(file), rules.refreshRule(file));
		file.getWorkspace().run(pm -> {
			try {
				if (!Arrays.equals(Files.readAllBytes(descriptor.path()), expected)) {
					throw new IOException("The PDE manifest changed during migration planning: " + descriptor.path()); //$NON-NLS-1$
				}
			} catch (IOException changed) {
				throw new CoreException(Status.error(changed.getMessage(), changed));
			}
			try {
				file.setContents(new ByteArrayInputStream(replacement), IResource.NONE, null);
			} catch (CoreException failure) {
				try { recover(expected, replacement, failure); }
				catch (IOException | CoreException recovery) { failure.addSuppressed(recovery); }
				throw failure;
			}
		}, rule, IWorkspace.AVOID_UPDATE, null);
	}

	private void recover(byte[] expected, byte[] replacement, CoreException failure) throws IOException, CoreException {
		var file= descriptor.file();
		file.refreshLocal(IResource.DEPTH_ZERO, null);
		byte[] current= Files.readAllBytes(descriptor.path());
		// A failed stream write can leave the old bytes or a prefix of its output.
		// An out-of-sync rejection precedes the write, so it cannot own such a prefix.
		boolean wrotePrefix= failure.getStatus().getCode() != IResourceStatus.OUT_OF_SYNC_LOCAL
				&& current.length <= replacement.length
				&& Arrays.equals(current, 0, current.length, replacement, 0, current.length);
		if (!Arrays.equals(current, descriptor.before()) && (Arrays.equals(current, expected) || wrotePrefix)) {
			file.setContents(new ByteArrayInputStream(descriptor.before()), IResource.NONE, null);
		}
	}

	@Override public void close() throws CoreException {
		write(staged, descriptor.before());
		Target.awaitClasspath(monitor);
	}

	/** Keep PDE optional for non-PDE installations of the application. */
	private static final class Target {
		static boolean hasProvider() {
			var model= PluginRegistry.findModel(MigrationSupportProvider.BUNDLE);
			return model != null && model.isEnabled() && model.getInstallLocation() != null
					&& MigrationSupportProvider.compatible(Path.of(model.getInstallLocation()));
		}

		static void awaitClasspath(IProgressMonitor monitor) {
			try {
				Job.getJobManager().join(ClasspathComputer.class, monitor);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new OperationCanceledException();
			}
		}
	}
}
