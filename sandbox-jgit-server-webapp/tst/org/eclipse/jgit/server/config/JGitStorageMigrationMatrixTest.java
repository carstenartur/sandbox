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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Keeps the machine-readable Core migration ledger aligned with build reality. */
public class JGitStorageMigrationMatrixTest {

	private static final Pattern CORE_VERSION= Pattern.compile(
			"<jgit-storage-hibernate\\.version>([^<]+)</jgit-storage-hibernate\\.version>"); //$NON-NLS-1$
	private static final Pattern COMMIT_SHA= Pattern.compile("[0-9a-f]{40}"); //$NON-NLS-1$

	@Test
	public void selectedCoreVersionMatchesTheActiveContract() throws IOException {
		JsonObject matrix= matrix();
		String selectedVersion= matrix.getAsJsonObject("releasePolicy") //$NON-NLS-1$
				.get("selectedVersion").getAsString(); //$NON-NLS-1$
		Matcher pomVersion= CORE_VERSION.matcher(read("pom.xml")); //$NON-NLS-1$
		String candidateVersion= System.getenv(
				"JGIT_STORAGE_HIBERNATE_CANDIDATE_VERSION"); //$NON-NLS-1$

		assertTrue("The module POM must declare jgit-storage-hibernate.version", //$NON-NLS-1$
				pomVersion.find());
		assertFalse(selectedVersion.endsWith("-SNAPSHOT")); //$NON-NLS-1$
		if (candidateVersion == null || candidateVersion.isBlank()) {
			assertEquals("The released migration ledger must match the selected module POM", //$NON-NLS-1$
					selectedVersion, pomVersion.group(1));
		} else {
			assertEquals("The substituted module POM must select the exact candidate", //$NON-NLS-1$
					candidateVersion, pomVersion.group(1));
		}
	}

	@Test
	public void verifiedCapabilitiesReferenceConcreteEvidence() throws IOException {
		JsonObject matrix= matrix();
		assertEquals(2, matrix.get("schemaVersion").getAsInt()); //$NON-NLS-1$
		assertTrue(COMMIT_SHA.matcher(matrix.get("lastVerifiedCommit") //$NON-NLS-1$
				.getAsString()).matches());

		Set<String> ids= new HashSet<>();
		for (JsonElement element : matrix.getAsJsonArray("capabilities")) { //$NON-NLS-1$
			JsonObject capability= element.getAsJsonObject();
			String id= capability.get("id").getAsString(); //$NON-NLS-1$
			assertTrue("Duplicate capability id: " + id, ids.add(id)); //$NON-NLS-1$
			if (capability.get("migrationStatus").getAsString() //$NON-NLS-1$
					.contains("verified")) { //$NON-NLS-1$
				JsonArray evidence= capability.getAsJsonArray("verifiedEvidence"); //$NON-NLS-1$
				assertNotNull("Verified capability lacks evidence: " + id, evidence); //$NON-NLS-1$
				assertFalse("Verified capability has empty evidence: " + id, //$NON-NLS-1$
						evidence.isEmpty());
			}
		}
	}

	@Test
	public void repositoryDeletionPolicyIsVerifiedButNotPubliclyExposed() throws IOException {
		JsonObject deletion= capability(matrix(), "repository-delete"); //$NON-NLS-1$
		Set<String> entrypoints= strings(deletion.getAsJsonArray("currentEntrypoints")); //$NON-NLS-1$
		Set<String> requiredEvidence= strings(deletion.getAsJsonArray("requiredEvidence")); //$NON-NLS-1$
		Set<String> verifiedEvidence= strings(deletion.getAsJsonArray("verifiedEvidence")); //$NON-NLS-1$

		assertEquals(2, entrypoints.size());
		assertTrue(entrypoints.contains(
				"org.eclipse.jgit.server.repository.SandboxRepositoryService.delete")); //$NON-NLS-1$
		assertTrue(entrypoints.contains(
				"org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService.delete")); //$NON-NLS-1$
		assertEquals("core-adapter-delete-policy-verified-runtime-cutover-pending", //$NON-NLS-1$
				deletion.get("migrationStatus").getAsString()); //$NON-NLS-1$
		assertTrue(deletion.get("currentBackend").getAsString() //$NON-NLS-1$
				.contains("REST deletion remains disabled")); //$NON-NLS-1$
		assertTrue(requiredEvidence.contains(
				"Search deletion participant removes projections")); //$NON-NLS-1$
		assertTrue(verifiedEvidence.contains(
				"ExternalHibernateRepositoryDeletionTest.rejectsDeletionWhileTheAdapterOwnsAnOpenHandle")); //$NON-NLS-1$
	}

	private static Set<String> strings(JsonArray array) {
		Set<String> values= new HashSet<>();
		for (JsonElement element : array) {
			values.add(element.getAsString());
		}
		return values;
	}

	private static JsonObject capability(JsonObject matrix, String id) {
		for (JsonElement element : matrix.getAsJsonArray("capabilities")) { //$NON-NLS-1$
			JsonObject capability= element.getAsJsonObject();
			if (id.equals(capability.get("id").getAsString())) { //$NON-NLS-1$
				return capability;
			}
		}
		throw new AssertionError("Missing capability: " + id); //$NON-NLS-1$
	}

	private static JsonObject matrix() throws IOException {
		return JsonParser.parseString(read("docs/jgit-storage-migration-matrix.json")) //$NON-NLS-1$
				.getAsJsonObject();
	}

	private static String read(String relativePath) throws IOException {
		Path baseDirectory= Path.of(System.getProperty("basedir", ".")); //$NON-NLS-1$ //$NON-NLS-2$
		return Files.readString(baseDirectory.resolve(relativePath),
				StandardCharsets.UTF_8);
	}
}
