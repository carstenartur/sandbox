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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.model.Build;
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
		var input = MavenCleanupProject.prepare(project, checkout, "test", run);
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
		assertEquals(List.of(selected), MavenCleanupProject.prepare(project, selected, "main", run).sources());
		Path unknown = Files.createDirectory(temporary.resolve("other"));
		assertThrows(IOException.class, () -> MavenCleanupProject.prepare(project, unknown, "both", run));
		assertThrows(IOException.class, () -> MavenCleanupProject.prepare(project, temporary.resolve("missing"), "both", run));
	}

	@Test
	void preservesDifferentMainAndTestLanguageLevelsOnScopedRuns() throws Exception {
		var project = new MavenProject();
		initialize(project, temporary);
		project.addCompileSourceRoot(Files.createDirectories(temporary.resolve("src/main/java")).toString());
		project.addTestCompileSourceRoot(Files.createDirectories(temporary.resolve("src/test/java")).toString());
		project.getProperties().setProperty("maven.compiler.release", "8");
		project.getProperties().setProperty("maven.compiler.testRelease", "17");
		assertThrows(IOException.class, () -> MavenCleanupProject.prepare(project, temporary, "both",
				Files.createDirectory(temporary.resolve("both-run"))));
		var test = MavenCleanupProject.prepare(project, temporary, "test", Files.createDirectory(temporary.resolve("test-run")));
		var main = MavenCleanupProject.prepare(project, temporary, "main", Files.createDirectory(temporary.resolve("main-run")));
		assertEquals("17", preferences(test).getProperty("org.eclipse.jdt.core.compiler.source"));
		assertEquals("1.8", preferences(main).getProperty("org.eclipse.jdt.core.compiler.source"));
	}

	@Test
	void respectsNativeProjectMetadataAndCallerWorkspaceSettings() throws Exception {
		var project = new MavenProject();
		initialize(project, temporary);
		Files.writeString(temporary.resolve(".project"), "existing metadata");
		Path run = Files.createDirectory(temporary.resolve("run"));
		var input = MavenCleanupProject.prepare(project, temporary, "test", run);
		assertEquals(temporary, input.directory());
		assertEquals(List.of(temporary), input.sources());
		assertEquals("test", input.cliScope());
		assertEquals("existing metadata", Files.readString(temporary.resolve(".project")));
		assertTrue(AbstractSandboxMojo.hasCallerWorkspace(Map.of("SANDBOX_WORKSPACE", "/configured")));
		assertTrue(AbstractSandboxMojo.hasCallerWorkspace(Map.of("SANDBOX_CLEANUP_WORKSPACE", "/legacy")));
		assertFalse(AbstractSandboxMojo.hasCallerWorkspace(Map.of("SANDBOX_WORKSPACE", "")));
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
