/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.maven.cleanup;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenEclipseMetadataTest {
	@TempDir Path base;
	private static final String JAVA = "<projectDescription><natures><nature>org.eclipse.jdt.core.javanature</nature></natures></projectDescription>";
	private static final String SOURCE = "<classpathentry kind='src' path='src/main/java'/>";
	private static final String M2E = "<classpathentry kind='con' path='org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER'/>";
	private static final String JRE = "org.eclipse.jdt.launching.JRE_CONTAINER";

	@Test
	void requiresAJavaProjectAndUsableClasspathBeforeNativeImport() throws Exception {
		assertFalse(MavenEclipseMetadata.canImportDirectly(base));
		Files.writeString(base.resolve(".project"), "<projectDescription><natures/></projectDescription>",
				StandardCharsets.UTF_8);
		classpath(base, SOURCE);
		assertFalse(MavenEclipseMetadata.canImportDirectly(base));
		Files.writeString(base.resolve(".project"), JAVA, StandardCharsets.UTF_8);
		classpath(base, "");
		assertFalse(MavenEclipseMetadata.canImportDirectly(base));
		classpath(base, SOURCE);
		assertTrue(MavenEclipseMetadata.canImportDirectly(base));
		classpath(base, SOURCE + M2E);
		assertFalse(MavenEclipseMetadata.canImportDirectly(base));
		Files.writeString(base.resolve(".classpath"), "<!DOCTYPE classpath><classpath/>", StandardCharsets.UTF_8);
		assertThrows(IOException.class, () -> MavenEclipseMetadata.canImportDirectly(base));
	}

	@Test
	void preservesM2eFiltersJreLibrariesAndPreferencesInTheTemporaryProject() throws Exception {
		Files.writeString(base.resolve(".project"), JAVA, StandardCharsets.UTF_8);
		Path library = Files.createFile(base.resolve("extra.jar"));
		String classpath = """
				<classpathentry kind="src" path="src/main/java" excluding="ignore/**" output="target/classes">
				  <attributes><attribute name="optional" value="true"/></attributes>
				</classpathentry>
				<classpathentry kind="con" path="%s/JavaSE-17"/>
				<classpathentry kind="lib" exported="true" path="extra.jar"/>
				""".formatted(JRE) + M2E;
		classpath(base, classpath);
		Path originalSettings = Files.createDirectory(base.resolve(".settings"));
		Files.writeString(originalSettings.resolve("org.eclipse.jdt.core.prefs"), """
				org.eclipse.jdt.core.compiler.problem.forbiddenReference=warning
				org.eclipse.jdt.core.compiler.source=11
				""", StandardCharsets.UTF_8);
		Files.writeString(originalSettings.resolve("org.eclipse.core.resources.prefs"),
				"encoding//src/main/java=UTF-8\n", StandardCharsets.UTF_8);

		Path generated = Files.createDirectory(base.resolve("generated"));
		Path settings = Files.createDirectory(generated.resolve(".settings"));
		Files.writeString(settings.resolve("org.eclipse.jdt.core.prefs"), "org.eclipse.jdt.core.compiler.source=17\n",
				StandardCharsets.UTF_8);
		String prefix = base.getFileName() + "/";
		classpath(generated, "<classpathentry kind='src' path='" + prefix + "src/main/java'/>"
				+ "<classpathentry kind='con' path='" + JRE + "'/>"
				+ "<classpathentry kind='lib' path='/resolved.jar'/><classpathentry kind='output' path='.sandbox-classes'/>");
		MavenEclipseMetadata.preserve(base, generated, base.getParent(), true);
		var doc = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(generated.resolve(".classpath").toFile());
		var xpath = XPathFactory.newDefaultInstance().newXPath();
		assertEquals("ignore/**", xpath.evaluate("//classpathentry[@kind='src']/@excluding", doc));
		assertEquals("", xpath.evaluate("//classpathentry[@kind='src']/@output", doc));
		assertEquals("true", xpath.evaluate("//classpathentry[@kind='src']//attribute[@name='optional']/@value", doc));
		assertEquals(JRE + "/JavaSE-17", xpath.evaluate("//classpathentry[@kind='con']/@path", doc));
		assertEquals("1", xpath.evaluate("count(//classpathentry[@kind='con'])", doc));
		assertEquals("true", xpath.evaluate("//classpathentry[@path='" + library + "']/@exported", doc));
		assertEquals("1", xpath.evaluate("count(//classpathentry[@path='/resolved.jar'])", doc));
		assertEquals(".sandbox-classes", xpath.evaluate("//classpathentry[@kind='output']/@path", doc));
		var prefs = new Properties();
		try (var in = Files.newInputStream(settings.resolve("org.eclipse.jdt.core.prefs"))) { prefs.load(in); }
		assertEquals("17", prefs.getProperty("org.eclipse.jdt.core.compiler.source"));
		assertEquals("warning", prefs.getProperty("org.eclipse.jdt.core.compiler.problem.forbiddenReference"));
		try (var in = Files.newInputStream(settings.resolve("org.eclipse.core.resources.prefs"))) { prefs.load(in); }
		assertEquals("UTF-8", prefs.getProperty("encoding//" + prefix + "src/main/java"));
		assertEquals("<classpath>" + classpath + "</classpath>", Files.readString(base.resolve(".classpath"), StandardCharsets.UTF_8));
	}

	@Test
	void keepsEclipseLanguageSettingsWhenMavenSuppliesNoExplicitLanguageLevel() throws Exception {
		Files.writeString(base.resolve(".project"), JAVA, StandardCharsets.UTF_8);
		Path generated = Files.createDirectory(base.resolve("generated"));
		Files.createDirectory(base.resolve(".settings"));
		Files.createDirectory(generated.resolve(".settings"));
		String prefs = ".settings/org.eclipse.jdt.core.prefs";
		Files.writeString(base.resolve(prefs), "org.eclipse.jdt.core.compiler.source=11\n", StandardCharsets.UTF_8);
		Files.writeString(generated.resolve(prefs), "org.eclipse.jdt.core.compiler.source=1.8\n",
				StandardCharsets.UTF_8);
		MavenEclipseMetadata.preserve(base, generated, base, false);
		assertTrue(Files.readString(generated.resolve(prefs), StandardCharsets.UTF_8).contains("org.eclipse.jdt.core.compiler.source=11"));
	}

	@Test
	void reportsUnresolvedIdeClasspathInsteadOfDroppingIt() throws Exception {
		Files.writeString(base.resolve(".project"), JAVA, StandardCharsets.UTF_8);
		classpath(base, SOURCE + M2E + "<classpathentry kind='con' path='custom.ide.container'/>");
		Path generated = Files.createDirectory(base.resolve("generated"));
		classpath(generated, SOURCE);
		var failure = assertThrows(IOException.class, () -> MavenEclipseMetadata.preserve(base, generated, base, false));
		assertTrue(failure.getMessage().contains("custom.ide.container"));
		assertTrue(failure.getMessage().contains("workspace"));
	}

	private static void classpath(Path dir, String entries) throws IOException {
		Files.writeString(dir.resolve(".classpath"), ("<classpath>" + entries + "</classpath>"), StandardCharsets.UTF_8);
	}
}
