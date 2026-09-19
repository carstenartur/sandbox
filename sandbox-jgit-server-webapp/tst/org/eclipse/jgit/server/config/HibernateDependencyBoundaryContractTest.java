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
package org.eclipse.jgit.server.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.w3c.dom.Document;

/** Guards the supported Hibernate ORM/Search version boundaries. */
public class HibernateDependencyBoundaryContractTest {

	private static final Map<String, String> SEARCH_TO_ORM_COMPATIBILITY= Map.of(
			"7.2", "6.6", //$NON-NLS-1$ //$NON-NLS-2$
			"8.4", "7.4"); //$NON-NLS-1$ //$NON-NLS-2$

	@Test
	public void rootManagedHibernateDefaultsStayOnASupportedMapperStack()
			throws Exception {
		Document root= pom("pom.xml"); //$NON-NLS-1$
		String ormVersion= value(root, "/project/properties/hibernate.version"); //$NON-NLS-1$
		String searchVersion= value(root,
				"/project/properties/hibernate-search.version"); //$NON-NLS-1$

		assertCompatiblePair("Root dependencyManagement defaults", ormVersion, //$NON-NLS-1$
				searchVersion);
		assertEquals("${hibernate.version}", //$NON-NLS-1$
				managedVersion(root, "org.hibernate.orm", "hibernate-core")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${hibernate.version}", //$NON-NLS-1$
				managedVersion(root, "org.hibernate.orm", "hibernate-hikaricp")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${hibernate.version}", //$NON-NLS-1$
				managedVersion(root, "org.hibernate.orm", "hibernate-jcache")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${hibernate-search.version}", //$NON-NLS-1$
				managedVersion(root, "org.hibernate.search", "hibernate-search-mapper-orm")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${hibernate-search.version}", //$NON-NLS-1$
				managedVersion(root, "org.hibernate.search", "hibernate-search-backend-lucene")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void storageModulePinsItsIndependentHibernateBoundary() throws Exception {
		Document module= pom("sandbox-jgit-storage-hibernate/pom.xml"); //$NON-NLS-1$
		String ormVersion= value(module,
				"/project/properties/jgit.hibernate-orm.version"); //$NON-NLS-1$
		String searchVersion= value(module,
				"/project/properties/jgit.hibernate-search.version"); //$NON-NLS-1$

		assertCompatiblePair("Storage module boundary", ormVersion, searchVersion); //$NON-NLS-1$
		assertEquals("${jgit.hibernate-orm.version}", //$NON-NLS-1$
				dependencyVersion(module, "org.hibernate.orm", "hibernate-core")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${jgit.hibernate-orm.version}", //$NON-NLS-1$
				dependencyVersion(module, "org.hibernate.orm", "hibernate-hikaricp")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${jgit.hibernate-orm.version}", //$NON-NLS-1$
				dependencyVersion(module, "org.hibernate.orm", "hibernate-jcache")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${jgit.hibernate-search.version}", //$NON-NLS-1$
				dependencyVersion(module, "org.hibernate.search", "hibernate-search-mapper-orm")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${jgit.hibernate-search.version}", //$NON-NLS-1$
				dependencyVersion(module, "org.hibernate.search", "hibernate-search-backend-lucene")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void serverModuleOverridesAllManagedHibernateArtifactsLocally()
			throws Exception {
		Document storage= pom("sandbox-jgit-storage-hibernate/pom.xml"); //$NON-NLS-1$
		Document server= pom("sandbox-jgit-server-webapp/pom.xml"); //$NON-NLS-1$
		String storageOrmVersion= value(storage,
				"/project/properties/jgit.hibernate-orm.version"); //$NON-NLS-1$
		String storageSearchVersion= value(storage,
				"/project/properties/jgit.hibernate-search.version"); //$NON-NLS-1$
		String serverOrmVersion= value(server,
				"/project/properties/jgit.hibernate-orm.version"); //$NON-NLS-1$
		String serverSearchVersion= value(server,
				"/project/properties/jgit.hibernate-search.version"); //$NON-NLS-1$

		assertCompatiblePair("Executable server boundary", serverOrmVersion, //$NON-NLS-1$
				serverSearchVersion);
		assertEquals(
				"The server must stay aligned with the storage module's isolated ORM boundary", //$NON-NLS-1$
				storageOrmVersion, serverOrmVersion);
		assertEquals(
				"The server must stay aligned with the storage module's isolated Search boundary", //$NON-NLS-1$
				storageSearchVersion, serverSearchVersion);
		assertEquals("${jgit.hibernate-orm.version}", //$NON-NLS-1$
				managedVersion(server, "org.hibernate.orm", "hibernate-core")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${jgit.hibernate-orm.version}", //$NON-NLS-1$
				managedVersion(server, "org.hibernate.orm", "hibernate-hikaricp")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${jgit.hibernate-orm.version}", //$NON-NLS-1$
				managedVersion(server, "org.hibernate.orm", "hibernate-jcache")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${jgit.hibernate-search.version}", //$NON-NLS-1$
				managedVersion(server, "org.hibernate.search", "hibernate-search-mapper-orm")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("${jgit.hibernate-search.version}", //$NON-NLS-1$
				managedVersion(server, "org.hibernate.search", "hibernate-search-backend-lucene")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void assertCompatiblePair(String label, String ormVersion,
			String searchVersion) {
		assertFalse(label + " must declare an ORM version", ormVersion.isBlank()); //$NON-NLS-1$
		assertFalse(label + " must declare a Search version", searchVersion.isBlank()); //$NON-NLS-1$
		String compatibleOrmSeries= SEARCH_TO_ORM_COMPATIBILITY
				.get(series(searchVersion));
		if (compatibleOrmSeries == null) {
			fail(label + " uses unsupported Hibernate Search series " //$NON-NLS-1$
					+ searchVersion + "; update this contract with the official compatibility evidence"); //$NON-NLS-1$
		}
		assertEquals(label + " must keep the officially supported ORM/Search pairing", //$NON-NLS-1$
				compatibleOrmSeries, series(ormVersion));
	}

	private static String dependencyVersion(Document document, String groupId,
			String artifactId) throws Exception {
		String dependency= "/project/dependencies/dependency[groupId='" + groupId //$NON-NLS-1$
				+ "' and artifactId='" + artifactId + "']"; //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("1", value(document, "count(" + dependency + ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return value(document, dependency + "/version"); //$NON-NLS-1$
	}

	private static String managedVersion(Document document, String groupId,
			String artifactId) throws Exception {
		String dependency= "/project/dependencyManagement/dependencies/dependency[groupId='" //$NON-NLS-1$
				+ groupId + "' and artifactId='" + artifactId + "']"; //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("1", value(document, "count(" + dependency + ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return value(document, dependency + "/version"); //$NON-NLS-1$
	}

	private static Document pom(String relativePath) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		return factory.newDocumentBuilder().parse(repositoryRoot().resolve(relativePath).toFile());
	}

	private static Path repositoryRoot() throws Exception {
		Path baseDirectory= Path.of(System.getProperty("basedir", ".")) //$NON-NLS-1$ //$NON-NLS-2$
				.toAbsolutePath().normalize();
		Path marker= baseDirectory.resolve("pom.xml"); //$NON-NLS-1$
		if (Files.isRegularFile(marker)) {
			String pomContents= Files.readString(marker, StandardCharsets.UTF_8);
			if (pomContents.contains("<artifactId>sandbox-jgit-server-webapp</artifactId>")) { //$NON-NLS-1$
				return baseDirectory.getParent();
			}
		}
		for (Path candidate= baseDirectory; candidate != null; candidate= candidate.getParent()) {
			if (Files.isRegularFile(candidate.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isDirectory(candidate.resolve("sandbox-jgit-server-webapp"))) { //$NON-NLS-1$
				return candidate;
			}
		}
		throw new IllegalStateException("Could not locate the Sandbox repository root"); //$NON-NLS-1$
	}

	private static String series(String version) {
		String[] components= version.split("\\."); //$NON-NLS-1$
		if (components.length < 2) {
			throw new IllegalArgumentException("Unexpected version: " + version); //$NON-NLS-1$
		}
		return components[0] + "." + components[1]; //$NON-NLS-1$
	}

	private static String value(Document document, String expression)
			throws Exception {
		return XPathFactory.newInstance().newXPath().evaluate(expression, document)
				.strip();
	}
}
