/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.jar.Manifest;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/** Guards the independent JDT Core and ECJ Maven version contracts. */
public class JdtCompilerVersionContractTest {

	private static final String JDT_DEPENDENCY=
			"dependency[groupId='org.eclipse.jdt' and artifactId='%s']"; //$NON-NLS-1$

	@Test
	public void jdtCoreAndCompilerUseIndependentManagedVersions() throws Exception {
		Document root= pom("pom.xml"); //$NON-NLS-1$
		assertFalse(value(root, "/project/properties/eclipse-jdt-core.version").isBlank()); //$NON-NLS-1$
		assertFalse(value(root, "/project/properties/eclipse-ecj.version").isBlank(), //$NON-NLS-1$
				"ECJ must have its own published version, not reuse the JDT Core version"); //$NON-NLS-1$
		assertEquals("${eclipse-jdt-core.version}", managedVersion(root, "org.eclipse.jdt.core")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${eclipse-ecj.version}", managedVersion(root, "ecj")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void jgitCompilerDependencyInheritsTheManagedVersion() throws Exception {
		assertInheritsVersion("sandbox-jgit-storage-hibernate/pom.xml", "ecj"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void standaloneTestsInheritTheRuntimeJdtCoreVersion() throws Exception {
		assertInheritsVersion("sandbox_common_test/pom.xml", "org.eclipse.jdt.core"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void standaloneTestsUseTheActiveJdtUiVersion() throws Exception {
		Properties pins= new Properties();
		try (var reader= Files.newBufferedReader(repositoryRoot().resolve(".github/patched-jdt-ui.env"), //$NON-NLS-1$
				StandardCharsets.UTF_8)) {
			pins.load(reader);
		}
		String expected= pins.getProperty("PATCHED_JDT_UI_EXPECTED_BASE_VERSION", ""); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(expected.isBlank(), "The active JDT UI version must be declared"); //$NON-NLS-1$
		Document module= pom("sandbox_common_test/pom.xml"); //$NON-NLS-1$
		String dependency= "/project/dependencies/" + JDT_DEPENDENCY.formatted("org.eclipse.jdt.ui"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("1", value(module, "count(" + dependency + ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(expected, value(module, dependency + "/version"), //$NON-NLS-1$
				"Standalone cleanup tests must use the active host API, not a historical UI version"); //$NON-NLS-1$
	}

	@Test
	public void sourceSnapshotTestDeclaresItsSourceViewerDependency() throws Exception {
		Path manifest= repositoryRoot().resolve("sandbox_int_to_enum_test/META-INF/MANIFEST.MF"); //$NON-NLS-1$
		try (var input= Files.newInputStream(manifest)) {
			String required= new Manifest(input).getMainAttributes().getValue("Require-Bundle"); //$NON-NLS-1$
			assertTrue(Arrays.stream(required.split(",")) //$NON-NLS-1$
					.map(clause -> clause.split(";", 2)[0].strip()) //$NON-NLS-1$
					.anyMatch("org.eclipse.jface.text"::equals), //$NON-NLS-1$
					"The native snapshot regression must directly depend on the public source-viewer API"); //$NON-NLS-1$
		}
	}

	private static void assertInheritsVersion(String path, String artifactId) throws Exception {
		Document module= pom(path);
		String dependency= "/project/dependencies/" + JDT_DEPENDENCY.formatted(artifactId); //$NON-NLS-1$
		assertEquals("1", value(module, "count(" + dependency + ")"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				path + " must retain the actual dependency"); //$NON-NLS-1$
		assertEquals("0", value(module, "count(" + dependency + "/version)"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				path + " must not override the centrally managed component version"); //$NON-NLS-1$
	}

	private static String managedVersion(Document root, String artifactId) throws Exception {
		String dependency= "/project/dependencyManagement/dependencies/" //$NON-NLS-1$
				+ JDT_DEPENDENCY.formatted(artifactId);
		assertEquals("1", value(root, "count(" + dependency + ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return value(root, dependency + "/version"); //$NON-NLS-1$
	}

	private static String value(Document document, String expression) throws Exception {
		return XPathFactory.newInstance().newXPath().evaluate(expression, document).strip();
	}

	private static Document pom(String relativePath) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(repositoryRoot().resolve(relativePath).toFile());
	}

	private static Path repositoryRoot() {
		for (Path candidate= Path.of("").toAbsolutePath().normalize(); //$NON-NLS-1$
				candidate != null; candidate= candidate.getParent()) {
			if (Files.isRegularFile(candidate.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isDirectory(candidate.resolve("sandbox_target"))) { //$NON-NLS-1$
				return candidate;
			}
		}
		throw new IllegalStateException("Could not locate the Sandbox repository root"); //$NON-NLS-1$
	}
}
