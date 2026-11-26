/*
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

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
import org.eclipse.osgi.launch.Equinox;
import org.eclipse.pde.core.target.ITargetDefinition;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.pde.core.target.ITargetLocation;
import org.eclipse.pde.core.target.ITargetPlatformService;
import org.eclipse.pde.core.target.LoadTargetDefinitionJob;
import org.eclipse.pde.internal.core.ClasspathComputer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.FrameworkWiring;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.ResourceSnapshot;
import org.sandbox.jdt.internal.common.MigrationSupportProvider;

import com.google.gson.JsonParser;

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
			Import-Package: org.junit.jupiter.api;version="[5,6)",
			 org.junit.jupiter.api.extension;version="[5,6)",
			 org.junit.platform.engine;version="[1.14,2)",
			 org.junit.platform.launcher;version="[1.14,2)"

			""";

	@AfterEach void cleanupProject() throws Exception {
		if (project != null && project.exists()) project.delete(true, true, null);
	}

	@Test void checksAppliesAndRestoresOnlyIntroducedSupport() throws Exception {
		withTarget(Fixture.STANDARD);
	}

	@Test void rejectsARealFactoryWhoseCallbackImportsRequireJupiterSix() throws Exception {
		withTarget(Fixture.CALLBACK_SIX);
	}

	@Test void rejectsContradictoryTransitivePlatformUses() throws Exception { withTarget(Fixture.DESCRIPTOR_SIX); }
	@Test void keepsSourcesWhenTheOlderRequiredFactoryStillShadowsTheImport() throws Exception { withTarget(Fixture.OLD_FACTORY); }
	@Test void commitsTheImportForAnAlreadyMentionedFactory() throws Exception { withTarget(Fixture.PREVIOUS_USE); }
	@Test void keepsTheOriginalPatchExporterFromRequireBundle() throws Exception { withTarget(Fixture.OLDER_API); }

	@Test void preservesTheApiExporterForAnIndependentTempDir() throws Exception { withTarget(Fixture.TEMP_DIR); }
	@Test void extendsAPreviouslyAdaptedRuleFamily() throws Exception { withTarget(Fixture.PRIOR_RULE); }

	private enum Fixture { STANDARD, CALLBACK_SIX, DESCRIPTOR_SIX, OLD_FACTORY, PREVIOUS_USE, OLDER_API, TEMP_DIR, PRIOR_RULE }

	private void withTarget(Fixture shape) throws Exception {
		// Tycho references jars outside eclipse_home/plugins. Build the consumer target from
		// the same installed artifacts that an Eclipse product offers in its plugins directory.
		Path directory= Files.createDirectory(temporary.resolve("target"));
		var context= FrameworkUtil.getBundle(ITargetPlatformService.class).getBundleContext();
		var names= List.of("org.eclipse.osgi", "org.junit", "org.hamcrest", "org.apiguardian.api", "org.opentest4j",
				"junit-jupiter-api", "junit-platform-commons", "junit-platform-engine", "junit-platform-launcher",
				MigrationSupportProvider.BUNDLE);
		for (var bundle : context.getBundles()) {
			String name= bundle.getSymbolicName();
			if (names.contains(name)) {
				Path jar= FileLocator.getBundleFileLocation(bundle).orElseThrow().toPath();
				Path copy= directory.resolve(name + "_" + bundle.getVersion() + ".jar");
				if ((shape == Fixture.CALLBACK_SIX || shape == Fixture.DESCRIPTOR_SIX) && MigrationSupportProvider.BUNDLE.equals(name)) {
					try (var input= new JarInputStream(Files.newInputStream(jar))) {
						var manifest= input.getManifest();
						var attrs= manifest.getMainAttributes();
						String imports= attrs.getValue(Constants.IMPORT_PACKAGE);
						String required= shape == Fixture.CALLBACK_SIX ? imports.replace("[5.14,7)", "[6,7)")
								: imports.replace("[5.14,7)", "[5.14,6)").replace("[1.14,7)", "[1.14,2)")
										.replace("engine.support.descriptor;version=\"[1.14,2)\"", "engine.support.descriptor;version=\"[6,7)\"");
						assertNotEquals(imports, required);
						attrs.putValue(Constants.IMPORT_PACKAGE, required);
						try (var output= new JarOutputStream(Files.newOutputStream(copy), manifest)) {
							for (var entry= input.getNextJarEntry(); entry != null; entry= input.getNextJarEntry()) {
								output.putNextEntry(new java.util.jar.JarEntry(entry.getName()));
								input.transferTo(output);
								output.closeEntry();
							}
						}
					}
					assertTrue(MigrationSupportProvider.compatible(copy));
				} else Files.copy(jar, copy);
			}
		}
		if (shape == Fixture.OLDER_API) {
			Path earlierApi= Path.of(System.getProperty("sandbox.test.earlierJupiterApi"));
			Files.copy(earlierApi, directory.resolve("junit-jupiter-api_5.14.1.jar"));
		}
		if (shape == Fixture.OLD_FACTORY) addOldFactory(directory);
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
				checkMetadataTransaction(shape);
			} finally {
				try { load(previousTarget); }
				finally { service.deleteTarget(fixtureTarget.getHandle()); }
			}
		} finally {
			context.ungetService(reference);
		}
	}

	private void addOldFactory(Path directory) throws Exception {
		Path source= Files.writeString(temporary.resolve("ExternalResourceExtension.java"), """
				package org.sandbox.junit.jupiter;
				public class ExternalResourceExtension {
				   public static org.junit.jupiter.api.extension.Extension perTest(
				       org.junit.jupiter.api.function.Executable before, Runnable after) { return null; }
				}
				""", StandardCharsets.UTF_8);
		try (var paths= Files.list(directory)) {
			String cp= paths.map(Path::toString).collect(Collectors.joining(java.io.File.pathSeparator));
			assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
					"--release", "21", "-cp", cp, "-d", temporary.toString(), source.toString()));
		}
		var headers= new java.util.jar.Manifest(new ByteArrayInputStream("""
				Manifest-Version: 1.0
				Bundle-ManifestVersion: 2
				Bundle-SymbolicName: org.sandbox.junit.migration.support
				Bundle-Version: 1.0.0
				Export-Package: org.sandbox.junit.jupiter;version="1.0"
				Import-Package: org.junit.jupiter.api.extension;version="[5,6)",org.junit.jupiter.api.function;version="[5,6)"

				""".getBytes(StandardCharsets.UTF_8)));
		try (var jar= new JarOutputStream(Files.newOutputStream(directory.resolve("old-support.jar")), headers)) {
			String entry= "org/sandbox/junit/jupiter/ExternalResourceExtension.class";
			jar.putNextEntry(new java.util.jar.JarEntry(entry));
			Files.copy(temporary.resolve(entry), jar);
			jar.closeEntry();
		}
	}

	private static void load(ITargetDefinition target) throws Exception {
		var load= new LoadTargetDefinitionJob(target);
		load.schedule();
		load.join();
		assertTrue(load.getResult().isOK(), () -> load.getResult().toString());
		Job.getJobManager().join(ClasspathComputer.class, new NullProgressMonitor());
	}

	private void checkMetadataTransaction(Fixture shape) throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot().getProject("MixedSupportImport" + System.nanoTime());
		project.create(null);
		project.open(null);
		project.setDefaultCharset(StandardCharsets.UTF_8.name(), null);
		for (String folder : List.of("META-INF", "src", "src/mixed")) project.getFolder(folder).create(true, true, null);
		String descriptor= switch (shape) {
			case PRIOR_RULE -> MANIFEST.replace("Require-Bundle: org.junit", "Require-Bundle: org.junit,\n junit-jupiter-api;bundle-version=\"[5,6)\",\n org.sandbox.junit.migration.support")
					.replace("org.junit.jupiter.api;version=\"[5,6)\",\n org.junit.jupiter.api.extension;version=\"[5,6)\",\n ", "");
			case PREVIOUS_USE -> MANIFEST.replace("Require-Bundle: org.junit", "Require-Bundle: org.junit,\n org.sandbox.junit.migration.support");
			case OLD_FACTORY -> MANIFEST.replace("Require-Bundle: org.junit", "Require-Bundle: org.junit,\n org.sandbox.junit.migration.support;bundle-version=\"[1,1]\"");
			case OLDER_API -> MANIFEST.replace("Require-Bundle: org.junit\nImport-Package: org.junit.jupiter.api;version=\"[5,6)\",\n org.junit.jupiter.api.extension;version=\"[5,6)\",\n ", "Require-Bundle: org.junit,\n junit-jupiter-api;bundle-version=\"[5.14.1,5.14.1]\"\nImport-Package: ");
			default -> MANIFEST;
		};
		var manifest= file("META-INF/MANIFEST.MF", descriptor);
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
		if (shape == Fixture.OLD_FACTORY || shape == Fixture.PREVIOUS_USE) instanceSource= instanceSource.replace(" public void works() {}", """
				 public void works() {}
				 org.junit.jupiter.api.extension.Extension oldFactory() {
				     return org.sandbox.junit.jupiter.ExternalResourceExtension.perTest(() -> {}, () -> {});
				 }
				""");
		var instance= file("src/mixed/InstanceTest.java", instanceSource);
		var shared= file("src/mixed/ClassTest.java", shape == Fixture.PRIOR_RULE ? """
				package mixed;
				@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD)
				@org.junit.jupiter.api.parallel.Isolated
				public class ClassTest {
				    public static Resource value = new Resource();
				    @org.junit.jupiter.api.extension.RegisterExtension
				    static final org.junit.jupiter.api.extension.Extension registered =
				        org.sandbox.junit.jupiter.ExternalResourceExtension.perClass(() -> value, r -> r.before(), r -> r.after());
				    @org.junit.jupiter.api.Test public void works() {}
				}
				""" : instanceSource.replace("InstanceTest", "ClassTest")
						.replace("@org.junit.Rule public", "@org.junit.ClassRule public static"));
		if (shape == Fixture.TEMP_DIR) file("src/mixed/FolderTest.java", """
				package mixed;
				import java.io.File;
				import java.io.IOException;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;
				public class FolderTest {
				    @Rule public TemporaryFolder folder = new TemporaryFolder();
				    @Test public void works() throws IOException { File file = folder.newFile("test"); }
				}
				""");
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
		// Start PDE's lazy container update before waiting for the job it schedules.
		JavaCore.getClasspathContainer(
				org.eclipse.core.runtime.Path.fromPortableString("org.eclipse.pde.core.requiredPlugins"), java);
		Job.getJobManager().join(ClasspathComputer.class, new NullProgressMonitor());
		assertNotNull(java.findType("org.junit.Rule"), () -> project.getName() + " lacks its required JUnit 4 bundle");
		if (shape == Fixture.OLD_FACTORY || shape == Fixture.PREVIOUS_USE || shape == Fixture.PRIOR_RULE) assertNotNull(java.findType(MigrationSupportProvider.FACTORY));
		else assertNull(java.findType(MigrationSupportProvider.FACTORY));
		Path config= temporary.resolve("cleanup.properties");
		Files.writeString(config, "cleanup.junitcleanup=true\ncleanup.junitcleanup_4_ruleexternalresource=true\ncleanup.junitcleanup_4_test=true\n"+ (shape == Fixture.TEMP_DIR ? "cleanup.junitcleanup_4_ruletemporaryfolder=true\n" : ""), StandardCharsets.UTF_8);
		byte[] fixtureBytes= Files.readAllBytes(path(fixture));
		byte[] original= Files.readAllBytes(path(manifest));
		Path report= temporary.resolve("report.json");
		var sources= List.of(fixture, instance, shared);
		if (shape == Fixture.CALLBACK_SIX || shape == Fixture.DESCRIPTOR_SIX || shape == Fixture.OLD_FACTORY) {
			byte[] sharedBefore= Files.readAllBytes(path(shared));
			assertEquals(1, invoke("apply", report, config), () -> report(report));
			assertTrue(report(report).contains("MIXED_RULE_LIFECYCLE"), () -> report(report));
			assertArrayEquals(original, Files.readAllBytes(path(manifest)));
			assertArrayEquals(fixtureBytes, Files.readAllBytes(path(fixture)));
			assertEquals(instanceSource, Files.readString(path(instance), StandardCharsets.UTF_8));
			assertArrayEquals(sharedBefore, Files.readAllBytes(path(shared)));
			if (shape == Fixture.OLD_FACTORY) assertFalse(MigrationSupportProvider.available(java));
			else assertInvisible(java);
			return;
		}
		if (shape == Fixture.TEMP_DIR || shape == Fixture.PRIOR_RULE) {
			var api= MigrationSupportProvider.binaryLocation(java, "org.junit.jupiter.api.Test");
			if (shape == Fixture.TEMP_DIR) {
				var folder= project.getFile("src/mixed/FolderTest.java");
				byte[] source= Files.readAllBytes(path(folder));
				byte[] conflict= (descriptor.stripTrailing() + ",\n org.junit.jupiter.api.io;version=\"[6,7)\"\n\n").getBytes(StandardCharsets.UTF_8);
				manifest.setContents(new ByteArrayInputStream(conflict), true, false, null);
				Job.getJobManager().join(ClasspathComputer.class, null);
				assertEquals(1, invoke("apply", report, config), () -> report(report));
				assertArrayEquals(conflict, Files.readAllBytes(path(manifest)));
				assertArrayEquals(source, Files.readAllBytes(path(folder)));
				assertEquals(instanceSource, Files.readString(path(instance), StandardCharsets.UTF_8));
				manifest.setContents(new ByteArrayInputStream(original), true, false, null);
				Job.getJobManager().join(ClasspathComputer.class, null);
			}
			assertEquals(2, invoke("check", report, config), () -> report(report));
			assertArrayEquals(original, Files.readAllBytes(path(manifest)));
			assertEquals(0, invoke("apply", report, config), () -> report(report));
			Job.getJobManager().join(ClasspathComputer.class, null);
			assertTrue(Files.readString(path(instance), StandardCharsets.UTF_8).contains("@org.junit.jupiter.api.extension.RegisterExtension"));
			if (shape == Fixture.TEMP_DIR) {
				String folder= Files.readString(path(project.getFile("src/mixed/FolderTest.java")), StandardCharsets.UTF_8);
				assertTrue(folder.contains("org.junit.jupiter.api.io.TempDir"), folder);
			}
			String additional= shape == Fixture.TEMP_DIR ? "org.junit.jupiter.api.io" : "org.junit.jupiter.api.function";
			var model= org.eclipse.pde.core.plugin.PluginRegistry.findModel(project).getBundleDescription();
			var wire= Arrays.stream(model.getResolvedImports()).filter(dep -> additional.equals(dep.getName())).findFirst()
					.orElseThrow(() -> new AssertionError(report(path(manifest))));
			assertEquals(5, wire.getExporter().getVersion().getMajor(), additional + " must use the original Jupiter exporter");
			assertEquals(api, MigrationSupportProvider.binaryLocation(java, additional + (shape == Fixture.TEMP_DIR ? ".TempDir" : ".ThrowingConsumer")));
			assertRuntimeIdentity();
			assertEquals(0, invoke("check", report, config), () -> report(report));
			return;
		}
		if (shape == Fixture.OLDER_API || shape == Fixture.PREVIOUS_USE) {
			var apiBefore= MigrationSupportProvider.binaryLocation(java, "org.junit.jupiter.api.Test");
			if (shape == Fixture.OLDER_API) assertTrue(apiBefore.toString().contains("5.14.1"), apiBefore.toString());
			else assertTrue(MigrationSupportProvider.available(java));
			var snapshot= new ResourceSnapshot(manifest, path(manifest), "META-INF/MANIFEST.MF", original);
			byte[] staged;
			try (var candidate= MixedSupportImport.stage(java, List.of(snapshot), ProjectWideCodeCleanupApplication.snapshots(java),
					Map.of("cleanup.junitcleanup", "true", "cleanup.junitcleanup_4_ruleexternalresource", "true"), null)) {
				assertNotNull(candidate);
				staged= Files.readAllBytes(path(manifest));
			}
			byte[] sharedBefore= Files.readAllBytes(path(shared));
			String[] batchArgs= shape == Fixture.PREVIOUS_USE ? new String[] { "--batch-max-files", "3" } : new String[0];
			assertEquals(2, invoke("check", report, config, batchArgs), () -> report(report));
			assertArrayEquals(original, Files.readAllBytes(path(manifest)));
			if (shape == Fixture.PREVIOUS_USE) {
				var batches= JsonParser.parseString(report(report)).getAsJsonObject().getAsJsonArray("batches");
				assertEquals(1, batches.size(), batches.toString());
				var batch= batches.get(0).getAsJsonObject();
				batchArgs= new String[] { "--batch-max-files", "3", "--batch", batch.get("id").getAsString(),
						"--batch-receipt", batch.get("receipt").getAsString() };
			}
			assertEquals(0, invoke("apply", report, config, batchArgs), () -> report(report));
			assertArrayEquals(staged, Files.readAllBytes(path(manifest)));
			Job.getJobManager().join(ClasspathComputer.class, null);
			assertTrue(MigrationSupportProvider.available(java), "The committed factory must supply the generated overload");
			var imports= new java.util.jar.Manifest(new ByteArrayInputStream(Files.readAllBytes(path(manifest)))).getMainAttributes()
					.getValue(Constants.IMPORT_PACKAGE);
			assertTrue(imports.contains(MigrationSupportProvider.PACKAGE), imports);
			assertTrue(Files.readString(path(instance), StandardCharsets.UTF_8).contains("@org.junit.jupiter.api.extension.RegisterExtension"));
			assertEquals(apiBefore, MigrationSupportProvider.binaryLocation(java, "org.junit.jupiter.api.parallel.Isolated"));
			assertEquals(apiBefore, MigrationSupportProvider.binaryLocation(java, "org.junit.jupiter.api.function.ThrowingConsumer"));
			assertEquals(0, invoke("check", report, config), () -> report(report));
			assertRuntimeIdentity();
			if (shape == Fixture.PREVIOUS_USE) {
				assertEquals("Apply reviewed migration batch", RefactoringCore.getUndoManager().peekUndoName());
				RefactoringCore.getUndoManager().performUndo(null, new NullProgressMonitor());
				assertArrayEquals(original, Files.readAllBytes(path(manifest)));
				assertEquals(instanceSource, Files.readString(path(instance), StandardCharsets.UTF_8));
				assertArrayEquals(sharedBefore, Files.readAllBytes(path(shared)));
				assertArrayEquals(fixtureBytes, Files.readAllBytes(path(fixture)));
			}
			return;
		}
		checkFailedWrites(java, manifest);
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
		// A previously provisioned factory is visible even when its callback types belong to different API bundles.
		byte[] incompatible= (MANIFEST.stripTrailing() + ",\n org.sandbox.junit.jupiter,\n"
				+ " org.junit.jupiter.api.parallel;version=\"[5,6)\",\n org.junit.jupiter.api.function;version=\"[6,7)\"\n\n")
				.getBytes(StandardCharsets.UTF_8);
		manifest.setContents(new ByteArrayInputStream(incompatible), true, false, null);
		Job.getJobManager().join(ClasspathComputer.class, null);
		assertTrue(MigrationSupportProvider.available(java));
		assertEquals(1, invoke("apply", report, config), () -> report(report));
		assertTrue(report(report).contains("MIXED_RULE_LIFECYCLE"), () -> report(report));
		assertArrayEquals(incompatible, Files.readAllBytes(path(manifest)));
		for (var entry : originalSources.entrySet()) assertArrayEquals(entry.getValue(), Files.readAllBytes(path(entry.getKey())));
		manifest.setContents(new ByteArrayInputStream(original), true, false, null);
		assertInvisible(java);
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
		Job.getJobManager().join(ClasspathComputer.class, null);
		var consumer= org.eclipse.pde.core.plugin.PluginRegistry.findModel(project).getBundleDescription();
		var imports= Arrays.stream(consumer.getResolvedImports()).collect(Collectors.toMap(
				dep -> dep.getName(), dep -> dep.getExporter()));
		var api= imports.get("org.junit.jupiter.api");
		assertEquals(5, api.getVersion().getMajor());
		for (String pkg : List.of("org.junit.jupiter.api.extension", "org.junit.jupiter.api.parallel", "org.junit.jupiter.api.function")) {
			assertSame(api, imports.get(pkg), pkg + " must use the consumer's original Jupiter API");
		}
		assertRuntimeIdentity();
		assertTrue(Files.readString(path(instance), StandardCharsets.UTF_8).contains("perTest("));
		Job.getJobManager().join(ClasspathComputer.class, null);
		assertTrue(MigrationSupportProvider.available(java));
		assertEquals(0, invoke("check", report, config), () -> report(report));
		assertArrayEquals(committed, Files.readAllBytes(path(manifest)));
	}

	private void assertRuntimeIdentity() throws Exception {
		var framework= new Equinox(Map.of(Constants.FRAMEWORK_STORAGE, temporary.resolve("framework").toString()));
		try {
			framework.init();
			var context= framework.getBundleContext();
			try (var paths= Files.list(temporary.resolve("target"))) {
				for (var path : paths.toList()) {
					if (!path.toFile().getName().startsWith("org.eclipse.osgi_")) {
						context.installBundle("reference:" + path.toUri());
					}
				}
			}
			var consumer= context.installBundle("reference:" + project.getLocation().toFile().toURI());
			assertTrue(framework.adapt(FrameworkWiring.class).resolveBundles(List.of(consumer)));
			Bundle support= FrameworkUtil.getBundle(consumer.loadClass(MigrationSupportProvider.FACTORY));
			assertEquals(MigrationSupportProvider.BUNDLE, support.getSymbolicName());
			for (String type : List.of("org.junit.jupiter.api.extension.Extension", "org.junit.jupiter.api.function.ThrowingConsumer",
					"org.junit.platform.engine.TestDescriptor", "org.junit.platform.launcher.Launcher")) {
				assertSame(consumer.loadClass(type), support.loadClass(type), type);
			}
			var jdtApi= MigrationSupportProvider.binaryLocation(JavaCore.create(project), "org.junit.jupiter.api.extension.Extension");
			try (var archive= new java.util.jar.JarFile(jdtApi.toFile())) {
				var runtimeApi= FrameworkUtil.getBundle(consumer.loadClass("org.junit.jupiter.api.extension.Extension"));
				assertEquals(archive.getManifest().getMainAttributes().getValue(Constants.BUNDLE_VERSION), runtimeApi.getVersion().toString());
				if (project.getFile("src/mixed/FolderTest.java").exists()) {
					assertSame(runtimeApi, FrameworkUtil.getBundle(consumer.loadClass("org.junit.jupiter.api.io.TempDir")));
				}
			}
			var factory= consumer.loadClass(MigrationSupportProvider.FACTORY);
			var callback= consumer.loadClass("org.junit.jupiter.api.function.ThrowingConsumer");
			assertSame(consumer.loadClass("org.junit.jupiter.api.extension.Extension"),
					factory.getMethod("perTest", Supplier.class, callback, Consumer.class).getReturnType());
		} finally {
			framework.stop();
			framework.waitForStop(10000);
		}
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

	private Object invoke(String mode, Path report, Path config, String... batchArgs) {
		var arguments= new ArrayList<>(List.of("--mode", mode, "--report", report.toString(), "--config", config.toString(), "--project", project.getName()));
		arguments.addAll(List.of(batchArgs));
		String[] args= arguments.toArray(String[]::new);
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
