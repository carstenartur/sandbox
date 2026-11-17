/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.maven.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenCleanupProjectTest {
	@TempDir Path temporary;

	@Test
	void linksRealMavenRootsWithTheirBindingsAndOriginalPatchPaths() throws Exception {
		Path checkout = Files.createDirectory(temporary.resolve("consumer & fixture"));
		Path main = Files.createDirectories(checkout.resolve("src/main/java"));
		Path test = Files.createDirectories(checkout.resolve("custom-specs"));
		Path dependency = Files.createFile(temporary.resolve("dependency.jar"));
		Path testDependency = Files.createFile(temporary.resolve("test dependency.jar"));
		MavenProject project = new MavenProject() {
			@Override public List<String> getCompileClasspathElements() { return List.of(dependency.toString()); }
			@Override public List<String> getTestClasspathElements() { return List.of(dependency.toString(), testDependency.toString()); }
		};
		initialize(project, checkout);
		project.addCompileSourceRoot(main.toString());
		project.addTestCompileSourceRoot(test.toString());
		project.getProperties().setProperty("maven.compiler.release", "17");
		project.getProperties().setProperty("project.build.sourceEncoding", "UTF-8");
		Path run = Files.createDirectory(temporary.resolve("run"));
		var input = prepare(project, checkout, "test", run);
		assertEquals(List.of(test), input.sources());
		assertEquals("both", input.cliScope()); // The custom test root has already been selected by Maven.
		Path description = input.directory().resolve(".project");
		assertEquals(main.toUri().toASCIIString(), value(description, "//link[name='src/main/java']/locationURI"));
		assertEquals(test.toUri().toASCIIString(), value(description, "//link[name='custom-specs']/locationURI"));
		assertTrue(Files.isDirectory(input.directory().resolve("src/main")));
		assertFalse(Files.exists(checkout.resolve(".project")));
		assertFalse(Files.exists(checkout.resolve(".classpath")));
		Path cp = input.directory().resolve(".classpath");
		assertEquals("src", value(cp, "//classpathentry[@path='src/main/java']/@kind"));
		assertEquals("true", value(cp, "//classpathentry[@path='custom-specs']//attribute[@name='test']/@value"));
		assertEquals("lib", value(cp, "//classpathentry[@path='" + dependency + "']/@kind"));
		assertEquals("true", value(cp, "//classpathentry[@path='" + testDependency + "']//attribute[@name='test']/@value"));
		Properties settings = preferences(input);
		assertEquals("17", settings.getProperty("org.eclipse.jdt.core.compiler.source"));
		assertEquals("enabled", settings.getProperty("org.eclipse.jdt.core.compiler.release"));
	}

	@Test
	void selectsNarrowerPathsAndIgnoresOnlyAbsentConventionalRoots() throws Exception {
		var project = new MavenProject();
		initialize(project, temporary);
		Path src = Files.createDirectories(temporary.resolve("src/main/java"));
		Path selected = Files.createDirectory(src.resolve("example"));
		project.addCompileSourceRoot(src.toString());
		project.addTestCompileSourceRoot(temporary.resolve("src/test/java").toString());
		var run = Files.createDirectory(temporary.resolve("run"));
		assertEquals(List.of(selected), prepare(project, selected, "main", run).sources());
		Path unknown = Files.createDirectory(temporary.resolve("other"));
		assertThrows(IOException.class, () -> prepare(project, unknown, "both", run));
		assertThrows(IOException.class, () -> prepare(project, temporary.resolve("missing"), "both", run));
	}

	@Test
	void preservesDifferentMainAndTestLanguageLevelsOnScopedRuns() throws Exception {
		var project = new MavenProject();
		initialize(project, temporary);
		project.addCompileSourceRoot(Files.createDirectories(temporary.resolve("src/main/java")).toString());
		project.addTestCompileSourceRoot(Files.createDirectories(temporary.resolve("src/test/java")).toString());
		project.getProperties().setProperty("maven.compiler.release", "8");
		project.getProperties().setProperty("maven.compiler.testRelease", "17");
		assertThrows(IOException.class, () -> prepare(project, temporary, "both",
				Files.createDirectory(temporary.resolve("both-run"))));
		var test = prepare(project, temporary, "test", Files.createDirectory(temporary.resolve("test-run")));
		var main = prepare(project, temporary, "main", Files.createDirectory(temporary.resolve("main-run")));
		assertEquals("17", preferences(test).getProperty("org.eclipse.jdt.core.compiler.source"));
		assertEquals("1.8", preferences(main).getProperty("org.eclipse.jdt.core.compiler.source"));
	}

	@Test
	void respectsNativeProjectMetadataAndCallerWorkspaceSettings() throws Exception {
		var project = new MavenProject();
		initialize(project, temporary);
		String description = "<projectDescription><name>Original</name><natures><nature>org.eclipse.jdt.core.javanature</nature></natures></projectDescription>";
		Files.writeString(temporary.resolve(".project"), description, StandardCharsets.UTF_8);
		Files.createDirectory(temporary.resolve("src"));
		Files.writeString(temporary.resolve(".classpath"),
				"<classpath><classpathentry kind='src' path='src'/><classpathentry kind='output' path='bin'/></classpath>",
				StandardCharsets.UTF_8);
		Path run = Files.createDirectory(temporary.resolve("run"));
		var input = MavenCleanupProject.prepare(project, temporary, "test", run, false, includeTests -> {
			throw new AssertionError("A native Eclipse project must use its existing classpath");
		});
		assertEquals(temporary, input.directory());
		assertEquals(List.of(temporary), input.sources());
		assertEquals("test", input.cliScope());
		assertEquals(description, Files.readString(temporary.resolve(".project"), StandardCharsets.UTF_8));
		assertTrue(AbstractSandboxMojo.hasCallerWorkspace(Map.of("SANDBOX_WORKSPACE", "/configured")));
		assertTrue(AbstractSandboxMojo.hasCallerWorkspace(Map.of("SANDBOX_CLEANUP_WORKSPACE", "/legacy")));
		assertFalse(AbstractSandboxMojo.hasCallerWorkspace(Map.of("SANDBOX_WORKSPACE", "")));
	}

	@Test
	void mainOnlyResolutionPreservesCompileRootsAndLeavesUnavailableTestArtifactsOutOfCollection() throws Exception {
		var project = new MavenProject();
		initialize(project, temporary);
		for (String scope : List.of("compile", "provided", "system", "runtime", "test")) {
			var dependency = new Dependency();
			dependency.setGroupId("example");
			dependency.setArtifactId(scope);
			dependency.setVersion("1");
			dependency.setScope(scope);
			project.getModel().addDependency(dependency);
		}
		var main = MavenCleanupClasspath.request(project, null, false);
		assertEquals(List.of("compile", "provided", "system"), main.getMavenProject().getDependencies().stream().map(Dependency::getScope).toList());
		var test = MavenCleanupClasspath.request(project, null, true);
		assertEquals(5, test.getMavenProject().getDependencies().size());
		assertEquals(5, project.getDependencies().size());
		assertEquals("0", value(Path.of("target/classes/META-INF/maven/plugin.xml"), "count(//mojo/requiresDependencyResolution)"));
	}

	@Test
	void givesPatchesOneCheckoutRelativePathForModuleAndSiblingRoots() throws Exception {
		Path checkout = Files.createDirectory(temporary.resolve("checkout"));
		Files.createDirectory(checkout.resolve(".git"));
		Path module = Files.createDirectory(checkout.resolve("module"));
		Path src = Files.createDirectories(module.resolve("src/main/java"));
		Path shared = Files.createDirectories(checkout.resolve("shared/java"));
		var project = new MavenProject();
		initialize(project, module);
		project.addCompileSourceRoot(src.toString());
		project.addCompileSourceRoot(shared.toString());
		var input = MavenCleanupProject.prepare(project, module, "main", Files.createDirectory(temporary.resolve("run")),
				true, includeTests -> project);
		Path metadata = input.directory().resolve(".project");
		assertEquals(src.toUri().toASCIIString(), value(metadata, "//link[name='module/src/main/java']/locationURI"));
		assertEquals(shared.toUri().toASCIIString(), value(metadata, "//link[name='shared/java']/locationURI"));
		Path outside = Files.createDirectory(temporary.resolve("external"));
		project.addTestCompileSourceRoot(outside.toString());
		assertThrows(IOException.class, () -> MavenCleanupProject.prepare(project, module, "test",
				Files.createDirectory(temporary.resolve("patch")), true, includeTests -> project));
		assertEquals(List.of(outside), prepare(project, outside, "test", Files.createDirectory(temporary.resolve("check"))).sources());
		var onlyMain = MavenCleanupProject.prepare(project, src, "both", Files.createDirectory(temporary.resolve("main")),
				true, includeTests -> { assertFalse(includeTests); return project; });
		assertEquals(List.of(src), onlyMain.sources());
	}

	private static MavenCleanupProject prepare(MavenProject project, Path selected, String scope, Path run) throws Exception {
		return MavenCleanupProject.prepare(project, selected, scope, run, false, includeTests -> project);
	}

	private static void initialize(MavenProject project, Path checkout) {
		project.setFile(checkout.resolve("pom.xml").toFile());
		project.setBuild(new Build());
		project.getBuild().setOutputDirectory(checkout.resolve("target/classes").toString());
		project.getBuild().setTestOutputDirectory(checkout.resolve("target/test-classes").toString());
	}

	private static Properties preferences(MavenCleanupProject input) throws IOException {
		var properties = new Properties();
		try (var stream = Files.newInputStream(input.directory().resolve(".settings/org.eclipse.jdt.core.prefs"))) {
			properties.load(stream);
		}
		return properties;
	}

	private static String value(Path xml, String xpath) throws Exception {
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
		return XPathFactory.newInstance().newXPath().evaluate(xpath, doc);
	}
}
