/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Keeps the executable build, published repositories, setup model and active
 * documentation on one explicit Eclipse/Tycho baseline.
 *
 * @since 1.3.4
 */
public class RepositoryBaselineConsistencyTest {

	private static final Pattern TYCHO_PROPERTY =
			Pattern.compile("<tycho-version>([^<]+)</tycho-version>"); //$NON-NLS-1$
	private static final Pattern ECLIPSE_RELEASE_REPOSITORY = Pattern.compile(
			"https://download\\.eclipse\\.org/releases/([^/]+)/"); //$NON-NLS-1$
	private static final Pattern OOMPH_RELEASE_VARIABLE = Pattern.compile(
			"(?s)<setupTask\\b(?=[^>]*name=\"eclipse\\.target\\.version\")[^>]*>"); //$NON-NLS-1$

	@Test
	public void machineReadableBaselineIsConsistent() throws Exception {
		Path root = repositoryRoot();
		String pom = read(root, "pom.xml"); //$NON-NLS-1$
		String tychoVersion = firstGroup(TYCHO_PROPERTY, pom, "root Tycho property"); //$NON-NLS-1$

		JsonObject repository = JsonParser.parseString(read(root, "docs/capabilities.json")) //$NON-NLS-1$
				.getAsJsonObject().getAsJsonObject("repository"); //$NON-NLS-1$
		String inventoryTychoVersion = repository.get("tychoVersion").getAsString(); //$NON-NLS-1$
		String eclipseRelease = repository.get("eclipseRelease").getAsString(); //$NON-NLS-1$
		assertEquals(tychoVersion, inventoryTychoVersion,
				"The capability inventory must use the root Tycho version"); //$NON-NLS-1$

		assertEquals(Set.of(eclipseRelease), releaseRepositories(pom),
				"The root POM must resolve only the declared Eclipse release"); //$NON-NLS-1$
		assertEquals(Set.of(eclipseRelease),
				releaseRepositories(read(root, "sandbox_target/eclipse.target")), //$NON-NLS-1$
				"The PDE target must resolve only the declared Eclipse release"); //$NON-NLS-1$
		assertEquals(Set.of(eclipseRelease),
				releaseRepositories(read(root, "sandbox_product/sandbox.product")), //$NON-NLS-1$
				"The product must provision only the declared Eclipse release"); //$NON-NLS-1$
		assertEquals(Set.of(eclipseRelease),
				releaseRepositories(read(root, "sandbox_product/category.xml")), //$NON-NLS-1$
				"The published p2 category must refer clients to the declared Eclipse release"); //$NON-NLS-1$

		String oomph = read(root, "sandbox_oomph/sandbox.setup"); //$NON-NLS-1$
		String variableTag = firstMatch(OOMPH_RELEASE_VARIABLE, oomph,
				"Oomph eclipse.target.version variable"); //$NON-NLS-1$
		assertTrue(variableTag.contains("value=\"" + eclipseRelease + "\""), //$NON-NLS-1$ //$NON-NLS-2$
				"The Oomph release variable value must match the capability inventory"); //$NON-NLS-1$
		assertTrue(variableTag.contains("defaultValue=\"" + eclipseRelease + "\""), //$NON-NLS-1$ //$NON-NLS-2$
				"The Oomph release variable default must match the capability inventory"); //$NON-NLS-1$
	}

	@Test
	public void activeDocumentationNamesTheExecutableBaseline() throws Exception {
		Path root = repositoryRoot();
		String pom = read(root, "pom.xml"); //$NON-NLS-1$
		String tychoVersion = firstGroup(TYCHO_PROPERTY, pom, "root Tycho property"); //$NON-NLS-1$
		JsonObject repository = JsonParser.parseString(read(root, "docs/capabilities.json")) //$NON-NLS-1$
				.getAsJsonObject().getAsJsonObject("repository"); //$NON-NLS-1$
		String eclipseRelease = repository.get("eclipseRelease").getAsString(); //$NON-NLS-1$

		Map<String, List<String>> expectedClaims = Map.ofEntries(
				Map.entry("README.md", List.of("Maven/Tycho " + tychoVersion, //$NON-NLS-1$ //$NON-NLS-2$
						"`main` (" + eclipseRelease + ")")), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("CONTRIBUTING.md", List.of("Tycho " + tychoVersion, //$NON-NLS-1$ //$NON-NLS-2$
						"Eclipse " + eclipseRelease)), //$NON-NLS-1$
				Map.entry(".github/copilot-instructions.md", List.of( //$NON-NLS-1$
						"Tycho " + tychoVersion, "Eclipse " + eclipseRelease)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry(".github/copilot-ref-build.md", List.of( //$NON-NLS-1$
						"Tycho " + tychoVersion, "Eclipse " + eclipseRelease)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("docs/distribution-compatibility.md", List.of( //$NON-NLS-1$
						"Tycho " + tychoVersion, "Eclipse " + eclipseRelease)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("sandbox_target/README.md", List.of("Eclipse " + eclipseRelease)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("sandbox_target/ARCHITECTURE.md", List.of( //$NON-NLS-1$
						"Eclipse " + eclipseRelease)), //$NON-NLS-1$
				Map.entry("sandbox_target/TODO.md", List.of("Eclipse " + eclipseRelease))); //$NON-NLS-1$ //$NON-NLS-2$

		for (Map.Entry<String, List<String>> entry : expectedClaims.entrySet()) {
			String content = read(root, entry.getKey());
			for (String expectedClaim : entry.getValue()) {
				assertTrue(content.contains(expectedClaim), () -> entry.getKey()
						+ " must name the executable baseline: " + expectedClaim); //$NON-NLS-1$
			}
		}
	}

	private static Path repositoryRoot() {
		Path candidate = Path.of("").toAbsolutePath().normalize(); //$NON-NLS-1$
		while (candidate != null) {
			if (Files.isRegularFile(candidate.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isDirectory(candidate.resolve("sandbox_target"))) { //$NON-NLS-1$
				return candidate;
			}
			candidate = candidate.getParent();
		}
		throw new IllegalStateException("Could not locate the Sandbox repository root"); //$NON-NLS-1$
	}

	private static String read(Path root, String relativePath) throws IOException {
		return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
	}

	private static String firstGroup(Pattern pattern, String content, String description) {
		Matcher matcher = pattern.matcher(content);
		assertTrue(matcher.find(), () -> "Missing " + description); //$NON-NLS-1$
		return matcher.group(1);
	}

	private static String firstMatch(Pattern pattern, String content, String description) {
		Matcher matcher = pattern.matcher(content);
		assertTrue(matcher.find(), () -> "Missing " + description); //$NON-NLS-1$
		return matcher.group();
	}

	private static Set<String> releaseRepositories(String content) {
		Matcher matcher = ECLIPSE_RELEASE_REPOSITORY.matcher(content);
		return matcher.results().map(result -> result.group(1)).collect(Collectors.toUnmodifiableSet());
	}
}
