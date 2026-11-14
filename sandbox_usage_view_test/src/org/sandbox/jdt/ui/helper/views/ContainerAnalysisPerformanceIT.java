/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms of
 * the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;
import org.sandbox.jdt.ui.helper.views.ContainerAnalysisService.ContainerAnalysisRow;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Opt-in, native workspace measurement of the same service invoked by the view's analysis job. */
class ContainerAnalysisPerformanceIT {

	private static final String MODULE= "org.eclipse.jdt.core.manipulation"; //$NON-NLS-1$
	private static final String COMMIT= "c922f757b27b7e2b6215db383cec5f8aafd13227"; //$NON-NLS-1$
	private static final List<String> SOURCE_ROOTS= List.of("common", "core extension", "proposals", "refactoring"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	@Test
	void reportOnPinnedJdtProject() throws Exception {
		String repository= System.getenv("SANDBOX_CONTAINER_JDT_UI"); //$NON-NLS-1$
		assertNotNull(repository, "Set SANDBOX_CONTAINER_JDT_UI to a local JDT UI clone containing R4_40"); //$NON-NLS-1$
		String name= "ContainerAnalysisPerformance"; //$NON-NLS-1$
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		assertFalse(workspace.getRoot().getProject(name).exists());
		IWorkspaceDescription original= workspace.getDescription();
		IWorkspaceDescription measurement= workspace.getDescription();
		measurement.setAutoBuilding(false);
		workspace.setDescription(measurement);
		try {
			runReport(repository, name);
		} finally {
			workspace.setDescription(original);
		}
	}

	private static void runReport(String repository, String name) throws Exception {
		IJavaProject project= AbstractEclipseJava.createJavaProject(name, "bin"); //$NON-NLS-1$
		try {
			List<String> paths= importSources(new File(repository), project);
			assertEquals(944, paths.size());
			configureClasspath(project);
			List<ICompilationUnit> units= paths.stream()
					.map(path -> JavaCore.createCompilationUnitFrom(project.getProject().getFile(path)))
					.sorted(Comparator.comparing(ICompilationUnit::getHandleIdentifier)).toList();
			assertNotNull(project.findType("java.lang.String")); //$NON-NLS-1$
			assertNotNull(project.findType("org.eclipse.jdt.core.dom.ASTParser")); //$NON-NLS-1$
			assertNotNull(project.findType("org.eclipse.core.runtime.IProgressMonitor")); //$NON-NLS-1$
			System.out.printf(Locale.ROOT, "Container Analysis: JDT %s, Java %s %s, max heap %d MiB%n", //$NON-NLS-1$
					JavaCore.getPlugin().getBundle().getVersion(), System.getProperty("java.vendor"), //$NON-NLS-1$
					Runtime.version(), Runtime.getRuntime().maxMemory() / (1024 * 1024));
			var rows= measure(units);
			assertFalse(rows.isEmpty());
			MessageDigest digest= MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
			for (var row : rows) {
				digest.update(row.toString().getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			System.out.println("Container Analysis result SHA-256: " + HexFormat.of().formatHex(digest.digest())); //$NON-NLS-1$
		} finally {
			project.getProject().delete(true, true, null);
		}
	}

	private static List<String> importSources(File location, IJavaProject project) throws Exception {
		var resource= project.getProject();
		Path directory= java.util.Objects.requireNonNull(resource.getLocation(), "Workspace project location is unavailable").toFile().toPath(); //$NON-NLS-1$
		List<String> paths= new ArrayList<>();
		long bytes= 0;
		try (Git git= Git.open(location);
				var walk= new RevWalk(git.getRepository());
				var files= new TreeWalk(git.getRepository())) {
			String origin= git.getRepository().getConfig().getString("remote", "origin", "url"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertNotNull(origin);
			assertEquals("https://github.com/eclipse-jdt/eclipse.jdt.ui", origin.replaceFirst("\\.git$", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			var commit= walk.parseCommit(git.getRepository().resolve("refs/tags/R4_40^{commit}")); //$NON-NLS-1$
			assertEquals(COMMIT, commit.name());
			files.addTree(commit.getTree());
			files.setRecursive(true);
			files.setFilter(PathFilter.create(MODULE));
			while (files.next()) {
				String path= files.getPathString().substring(MODULE.length() + 1);
				if (path.endsWith(".java")) { //$NON-NLS-1$
					byte[] source= git.getRepository().open(files.getObjectId(0)).getBytes(Charset.defaultCharset());
					Path destination= directory.resolve(path);
					Path parent= java.util.Objects.requireNonNull(destination.getParent(),
							"Imported Java source requires a parent directory: " + path); //$NON-NLS-1$
					Files.createDirectories(parent);
					Files.write(destination, source);
					bytes+= source.length;
					paths.add(path);
				}
			}
		}
		assertEquals(10_398_833L, bytes);
		resource.refreshLocal(IResource.DEPTH_INFINITE, null);
		resource.setDefaultCharset(StandardCharsets.UTF_8.name(), null);
		return paths;
	}

	private static void configureClasspath(IJavaProject project) throws Exception {
		List<IClasspathEntry> entries= new ArrayList<>();
		for (String root : SOURCE_ROOTS) {
			entries.add(JavaCore.newSourceEntry(project.getProject().getFolder(root).getFullPath()));
		}
		entries.add(JavaCore.newContainerEntry(IPath.fromPortableString("org.eclipse.jdt.launching.JRE_CONTAINER"))); //$NON-NLS-1$
		// Mirror the installed manipulation bundle's dependency closure, excluding its own binary.
		Bundle module= Platform.getBundle(MODULE);
		var visited= new LinkedHashSet<Bundle>();
		var pending= new ArrayDeque<Bundle>();
		pending.add(module);
		while (!pending.isEmpty()) {
			Bundle bundle= pending.removeFirst();
			if (visited.add(bundle)) {
				if (bundle != module) {
					File library= FileLocator.getBundleFileLocation(bundle).orElseThrow();
					entries.add(JavaCore.newLibraryEntry(IPath.fromOSString(library.getAbsolutePath()), null, null));
				}
				bundle.adapt(BundleWiring.class).getRequiredWires(null)
						.forEach(wire -> pending.add(wire.getProviderWiring().getBundle()));
			}
		}
		project.setRawClasspath(entries.toArray(IClasspathEntry[]::new), null);
		var options= project.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		project.setOptions(options);
	}

	private static List<ContainerAnalysisRow> measure(List<ICompilationUnit> units) {
		var service= new ContainerAnalysisService();
		var monitor= new NullProgressMonitor();
		List<ContainerAnalysisRow> rows= new ArrayList<>();
		long start= System.nanoTime();
		for (ICompilationUnit unit : units) {
			rows.addAll(service.analyze(unit, monitor));
		}
		double seconds= (System.nanoTime() - start) / 1_000_000_000.0;
		System.out.printf(Locale.ROOT, "Container Analysis: %d units, %d rows, %.3f seconds, %.2f units/s%n", //$NON-NLS-1$
				units.size(), rows.size(), seconds, units.size() / seconds);
		return rows;
	}
}
