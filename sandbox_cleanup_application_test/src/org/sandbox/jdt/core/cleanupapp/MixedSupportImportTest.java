/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.pde.core.target.ITargetLocation;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.core.target.LoadTargetDefinitionJob;
import org.eclipse.pde.internal.core.ClasspathComputer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.FrameworkUtil;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.ResourceSnapshot;
import org.sandbox.jdt.internal.common.MigrationSupportProvider;

class MixedSupportImportTest {
	@TempDir Path temporary;
	private IProject project;
	private static final String MANIFEST= """
			Manifest-Version: 1.0
			Bundle-ManifestVersion: 2
			Bundle-SymbolicName: mixed.resources.transaction
			Bundle-Version: 1.0.0
			Bundle-RequiredExecutionEnvironment: JavaSE-21
			Require-Bundle: org.junit
			Import-Package: org.junit.jupiter.api;version="[5,6)"

			""";

	@AfterEach void cleanupProject() throws Exception {
		if (project != null && project.exists()) project.delete(true, true, null);
	}

	@Test void checksAppliesAndRestoresOnlyIntroducedSupport() throws Exception {
		// Tycho references jars outside eclipse_home/plugins. Build the consumer target from
		// the same installed artifacts that an Eclipse product offers in its plugins directory.
		Path directory= Files.createDirectory(temporary.resolve("target"));
		var context= FrameworkUtil.getBundle(ITargetPlatformService.class).getBundleContext();
		var names= List.of("org.eclipse.osgi", "org.junit", "org.hamcrest", "org.apiguardian.api", "org.opentest4j",
				"junit-jupiter-api", "junit-platform-commons", "junit-platform-engine", "junit-platform-launcher",
				MigrationSupportProvider.BUNDLE);
		for (var bundle : context.getBundles()) {
			String name= bundle.getSymbolicName();
			if (names.contains(name) && !(name.startsWith("junit-") && bundle.getVersion().getMajor() >= 6)) {
				Path jar= FileLocator.getBundleFileLocation(bundle).orElseThrow().toPath();
				Files.copy(jar, directory.resolve(name + "_" + bundle.getVersion() + ".jar"));
			}
		}
		var reference= context.getServiceReference(ITargetPlatformService.class);
		try {
			var service= context.getService(reference);
			var previousTarget= service.getWorkspaceTargetDefinition();
			var fixtureTarget= service.newTarget();
			fixtureTarget.setName("Mixed rule consumer");
			fixtureTarget.setTargetLocations(new ITargetLocation[] { service.newDirectoryLocation(directory.toString()) });
			service.saveTargetDefinition(fixtureTarget);
			try {
				load(fixtureTarget);
				checkMetadataTransaction();
			} finally {
				try { load(previousTarget); }
				finally { service.deleteTarget(fixtureTarget.getHandle()); }
			}
		} finally {
			context.ungetService(reference);
		}
	}

	private static void load(ITargetDefinition target) throws Exception {
		var load= new LoadTargetDefinitionJob(target);
		load.schedule();
		load.join();
		assertTrue(load.getResult().isOK(), () -> load.getResult().toString());
		Job.getJobManager().join(ClasspathComputer.class, new NullProgressMonitor());
	}

	private void checkMetadataTransaction() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot().getProject("MixedSupportImport" + System.nanoTime());
		project.create(null);
		project.open(null);
		project.setDefaultCharset(StandardCharsets.UTF_8.name(), null);
		for (String folder : List.of("META-INF", "src", "src/mixed")) project.getFolder(folder).create(true, true, null);
		var manifest= file("META-INF/MANIFEST.MF", MANIFEST);
		file("build.properties", "source.. = src/\noutput.. = bin/\nbin.includes = .,META-INF/\n");
		var fixture= file("src/mixed/Resource.java", """
				package mixed;
				public class Resource extends org.junit.rules.ExternalResource {
					@Override public void before() {}
					@Override public void after() {}
				}
				""");
		String instanceSource= """
				package mixed;
				public class InstanceTest {
					@org.junit.Rule public Resource value = new Resource();
					@org.junit.Test public void works() {}
				}
				""";
		var instance= file("src/mixed/InstanceTest.java", instanceSource);
		var shared= file("src/mixed/ClassTest.java", instanceSource.replace("InstanceTest", "ClassTest")
				.replace("@org.junit.Rule public", "@org.junit.ClassRule public static"));
		var description= project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID, "org.eclipse.pde.PluginNature" });
		project.setDescription(description, null);
		var java= JavaCore.create(project);
		java.setRawClasspath(new IClasspathEntry[] {
				JavaCore.newSourceEntry(project.getFolder("src").getFullPath()),
				JavaCore.newContainerEntry(org.eclipse.core.runtime.Path.fromPortableString("org.eclipse.jdt.launching.JRE_CONTAINER")),
				JavaCore.newContainerEntry(org.eclipse.core.runtime.Path.fromPortableString("org.eclipse.pde.core.requiredPlugins")) },
				project.getFullPath().append("bin"), null);
		var javaOptions= java.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, javaOptions);
		java.setOptions(javaOptions);
		assertNotNull(java.findType("org.junit.Rule"));
		assertNull(java.findType(MigrationSupportProvider.FACTORY));
		checkFailedWrites(java, manifest);
		Path config= temporary.resolve("cleanup.properties");
		Files.writeString(config, "cleanup.junitcleanup=true\ncleanup.junitcleanup_4_ruleexternalresource=true\ncleanup.junitcleanup_4_test=true\n", StandardCharsets.UTF_8);
		byte[] fixtureBytes= Files.readAllBytes(path(fixture));
		byte[] original= Files.readAllBytes(path(manifest));
		Path report= temporary.resolve("report.json");
		var sources= List.of(fixture, instance, shared);
		var originalSources= new java.util.LinkedHashMap<IFile, byte[]>();
		for (IFile file : sources) {
			originalSources.put(file, Files.readAllBytes(path(file)));
			String simple= file.getName().replace(".java", "");
			byte[] inert= ("package mixed; public class " + simple + " {} // ClassRule documentation\n").getBytes(StandardCharsets.UTF_8);
			file.setContents(new ByteArrayInputStream(inert), true, false, null);
		}
		assertEquals(0, invoke("apply", report, config), () -> report(report));
		assertArrayEquals(original, Files.readAllBytes(path(manifest)));
		assertInvisible(java);
		for (var entry : originalSources.entrySet()) {
			entry.getKey().setContents(new ByteArrayInputStream(entry.getValue()), true, false, null);
		}
		assertEquals(2, invoke("check", report, config), () -> report(report));
		assertArrayEquals(original, Files.readAllBytes(path(manifest)));
		assertEquals(instanceSource, Files.readString(path(instance), StandardCharsets.UTF_8));
		assertInvisible(java);
		// A custom apply leaves no proven owner and must restore the staged binding before returning fatal.
		String custom= new String(fixtureBytes, StandardCharsets.UTF_8).replace("@Override public void before()", """
				@Override public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runner.Description description) { return base; }
				@Override public void before()""");
		fixture.setContents(new ByteArrayInputStream(custom.getBytes(StandardCharsets.UTF_8)), true, false, null);
		assertEquals(1, invoke("apply", report, config), () -> report(report));
		assertTrue(report(report).contains("MIXED_RULE_LIFECYCLE"), () -> report(report));
		assertArrayEquals(original, Files.readAllBytes(path(manifest)));
		assertInvisible(java);
		fixture.setContents(new ByteArrayInputStream(fixtureBytes), true, false, null);
		assertEquals(0, invoke("apply", report, config), () -> report(report));
		byte[] committed= Files.readAllBytes(path(manifest));
		assertTrue(new String(committed, StandardCharsets.UTF_8).contains(MigrationSupportProvider.PACKAGE));
		assertTrue(Files.readString(path(instance), StandardCharsets.UTF_8).contains("perTest("));
		Job.getJobManager().join(ClasspathComputer.class, null);
		assertTrue(MigrationSupportProvider.available(java));
		assertEquals(0, invoke("check", report, config), () -> report(report));
		assertArrayEquals(committed, Files.readAllBytes(path(manifest)));
	}

	private enum WriteFailure { BEFORE_WRITE, PARTIAL_WRITE, AFTER_WRITE, EXTERNAL_EDIT, OUT_OF_SYNC }

	private static void checkFailedWrites(IJavaProject java, IFile manifest) throws Exception {
		var sources= ProjectWideCodeCleanupApplication.snapshots(java);
		var options= Map.of("cleanup.junitcleanup", "true", "cleanup.junitcleanup_4_ruleexternalresource", "true");
		byte[] original= Files.readAllBytes(path(manifest));
		byte[] external= (MANIFEST.stripTrailing() + "\nX-Other-Editor: keep\n\n").getBytes(StandardCharsets.UTF_8);
		byte[] externalPrefix= "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8);
		for (boolean onClose : List.of(true, false)) {
			for (WriteFailure when : WriteFailure.values()) {
				CoreException failure= new CoreException(Status.error("Injected manifest failure " + when));
				// Use the real workspace file for every operation except the one failing Eclipse write.
				int[] writes= { 0 };
				IFile wrapped= (IFile) Proxy.newProxyInstance(IFile.class.getClassLoader(), new Class<?>[] { IFile.class },
						(proxy, method, args) -> {
							if ("setContents".equals(method.getName()) && ++writes[0] == (onClose ? 2 : 1)) {
								var stream= (InputStream) args[0];
								byte[] output= stream.readAllBytes();
								switch (when) {
									case BEFORE_WRITE -> { }
									case PARTIAL_WRITE -> Files.write(path(manifest), Arrays.copyOf(output, output.length / 2));
									case AFTER_WRITE -> Files.write(path(manifest), output);
									case EXTERNAL_EDIT -> Files.write(path(manifest), external);
									case OUT_OF_SYNC -> {
										Files.write(path(manifest), externalPrefix);
										Files.setLastModifiedTime(path(manifest), FileTime.fromMillis(manifest.getLocalTimeStamp() + 2000));
										manifest.setContents(new ByteArrayInputStream(output), IResource.NONE, null);
										fail("Eclipse accepted an out-of-sync write");
									}
								}
								throw failure;
							}
							try { return method.invoke(manifest, args); }
							catch (InvocationTargetException e) { throw e.getCause(); }
						});
				var descriptor= new ResourceSnapshot(wrapped, path(manifest), "META-INF/MANIFEST.MF", original);
				var thrown= assertThrows(CoreException.class, () -> {
					try (var staged= MixedSupportImport.stage(java, List.of(descriptor), sources, options, null)) {
						assertNotNull(staged);
					}
				});
				if (when == WriteFailure.OUT_OF_SYNC) assertEquals(IResourceStatus.OUT_OF_SYNC_LOCAL, thrown.getStatus().getCode());
				else assertSame(failure, thrown);
				byte[] expected= switch (when) {
					case EXTERNAL_EDIT -> external;
					case OUT_OF_SYNC -> externalPrefix;
					default -> original;
				};
				assertArrayEquals(expected, Files.readAllBytes(path(manifest)), (onClose ? "restoring " : "staging ") + when);
				if (when == WriteFailure.EXTERNAL_EDIT || when == WriteFailure.OUT_OF_SYNC) {
					manifest.setContents(new ByteArrayInputStream(original), true, false, null);
				}
				assertInvisible(java);
			}
		}
	}

	private static void assertInvisible(IJavaProject project) throws Exception {
		Job.getJobManager().join(ClasspathComputer.class, null);
		assertNull(project.findType(MigrationSupportProvider.FACTORY), Arrays.toString(project.getResolvedClasspath(true)));
	}

	private Object invoke(String mode, Path report, Path config) {
		String[] args= { "--mode", mode, "--report", report.toString(), "--config", config.toString(), "--project", project.getName() };
		var context= (IApplicationContext) Proxy.newProxyInstance(IApplicationContext.class.getClassLoader(),
				new Class<?>[] { IApplicationContext.class }, (proxy, method, values) ->
						"getArguments".equals(method.getName()) ? Map.of(IApplicationContext.APPLICATION_ARGS, args) : null);
		return new ProjectWideCodeCleanupApplication().start(context);
	}

	private static String report(Path path) {
		try { return Files.readString(path, StandardCharsets.UTF_8); }
		catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
	}

	private IFile file(String relativePath, String source) throws Exception {
		var file= project.getFile(relativePath);
		file.create(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), false, null);
		return file;
	}

	private static Path path(IFile file) { return file.getLocation().toFile().toPath(); }
}
