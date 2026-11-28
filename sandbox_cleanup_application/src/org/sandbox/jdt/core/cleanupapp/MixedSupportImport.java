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
import java.util.Comparator;
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
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.ClasspathComputer;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.ResourceSnapshot;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.SourceSnapshot;
import org.sandbox.jdt.internal.common.MigrationSupportProvider;
import org.sandbox.jdt.internal.common.PdeManifestImportUpdater;
import org.sandbox.jdt.internal.common.PdeManifestImportUpdater.Exporter;

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
		// A previously adapted family can receive a new rule even after its last ClassRule is gone.
		boolean possible= false;
		for (var source : sources) {
			if (source.unit().getSource().contains("ClassRule")) { possible= true; break; } //$NON-NLS-1$
		}
		if (!possible && !MigrationSupportProvider.available(project)) return null;
		var exporters= Target.exporters(project);
		if (exporters == null || exporters.support() == null) return null;
		var descriptor= resources.getFirst();
		byte[] staged= descriptor.before();
		for (String name : JupiterDependencies.MIXED_PACKAGES) staged= addImport(staged, name, exporters);
		var transaction= new MixedSupportImport(descriptor, staged, monitor);
		if (Arrays.equals(staged, descriptor.before())) return transaction;
		transaction.write(descriptor.before(), staged);
		try {
			Target.awaitClasspath(monitor);
			return transaction;
		} catch (RuntimeException e) {
			try { transaction.close(); } catch (CoreException restore) { e.addSuppressed(restore); }
			throw e;
		}
	}

	private record Api(Exporter exporter, List<String> packages) {}
	private record Exporters(Api api, Exporter support) {}

	record Bindings(byte[] manifest, Api api) {
		byte[] addImport(byte[] before, String name) throws IOException {
			boolean jupiterApi= isApi(name);
			var updated= PdeManifestImportUpdater.addImport(before, name, jupiterApi && api != null ? api.exporter() : null);
			if (updated != before && jupiterApi && (api == null || !api.packages().contains(name))) {
				throw new IOException("The selected Jupiter API does not export " + name); //$NON-NLS-1$
			}
			return updated;
		}
	}

	private static boolean isApi(String name) {
		return name.equals("org.junit.jupiter.api") || name.startsWith("org.junit.jupiter.api."); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static byte[] addImport(byte[] before, String name, Exporters exporters) {
		Exporter selected= null;
		if (name.equals(MigrationSupportProvider.PACKAGE)) selected= exporters.support();
		else if (isApi(name) && exporters.api() != null) selected= exporters.api().exporter();
		return PdeManifestImportUpdater.addImport(before, name, selected);
	}

	Bindings bindings(IJavaProject project) throws JavaModelException {
		return new Bindings(staged, Target.api(project));
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
		if (Arrays.equals(staged, descriptor.before())) return;
		write(staged, descriptor.before());
		Target.awaitClasspath(monitor);
	}

	/** Keep PDE optional for non-PDE installations of the application. */
	private static final class Target {
		static Exporters exporters(IJavaProject java) throws JavaModelException {
			var model= PluginRegistry.findModel(java.getProject());
			var entry= PluginRegistry.findEntry(MigrationSupportProvider.BUNDLE);
			if (model == null || model.getBundleDescription() == null || entry == null) return null;
			var existing= MigrationSupportProvider.binaryLocation(java, MigrationSupportProvider.FACTORY);
			var providers= Arrays.stream(entry.getActiveModels()).filter(target -> target.isEnabled() && target.getInstallLocation() != null
					&& MigrationSupportProvider.compatible(Path.of(target.getInstallLocation()))).toList();
			var support= providers.stream().filter(target -> Path.of(target.getInstallLocation()).equals(existing)).findFirst()
					.or(() -> providers.stream().max(Comparator.comparing(target -> target.getBundleDescription().getVersion())));
			return new Exporters(api(java), support.map(target -> new Exporter(MigrationSupportProvider.BUNDLE,
					target.getBundleDescription().getVersion().toString())).orElse(null));
		}

		static Api api(IJavaProject java) throws JavaModelException {
			for (String type : List.of("org.junit.jupiter.api.Test", "org.junit.jupiter.api.extension.Extension")) { //$NON-NLS-1$ //$NON-NLS-2$
				var binary= MigrationSupportProvider.binaryLocation(java, type);
				if (binary == null) continue;
				String pkg= type.substring(0, type.lastIndexOf('.'));
				for (var target : PluginRegistry.getActiveModels()) {
					if (target.getInstallLocation() == null || !binary.equals(Path.of(target.getInstallLocation()))) continue;
					var supplier= target.getBundleDescription();
					if (supplier != null && Arrays.stream(supplier.getExportPackages()).anyMatch(cap -> pkg.equals(cap.getName()))) {
						return new Api(new Exporter(supplier.getSymbolicName(), supplier.getVersion().toString()),
								Arrays.stream(supplier.getExportPackages()).map(cap -> cap.getName()).toList());
					}
				}
			}
			return null;
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
