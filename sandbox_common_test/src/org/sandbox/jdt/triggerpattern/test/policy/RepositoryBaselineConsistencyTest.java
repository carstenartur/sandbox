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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

	private static final Set<String> BOUNCY_CASTLE_IDS = Set.of("bcutil", "bcprov", "bcpkix", "bcpg"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private static final Map<String, String> BOUNCY_CASTLE_VERSIONS = Map.of(
			"bcutil", "1.85.0", "bcprov", "1.85.2", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"bcpkix", "1.85.0", "bcpg", "1.85.0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private static final Pattern TYCHO_PROPERTY =
			Pattern.compile("<tycho-version>([^<]+)</tycho-version>"); //$NON-NLS-1$
	private static final Pattern ECLIPSE_RELEASE_REPOSITORY = Pattern.compile(
			"https://download\\.eclipse\\.org/releases/([^/]+)/"); //$NON-NLS-1$
	private static final Pattern BOUNCY_CASTLE_UNIT = Pattern.compile(
			"<unit id=\"(bcutil|bcprov|bcpkix|bcpg)\" version=\"([^\"]+)\"/>"); //$NON-NLS-1$
	private static final Pattern OOMPH_RELEASE_VARIABLE = Pattern.compile(
			"(?s)<setupTask\\b(?=[^>]*name=\"eclipse\\.target\\.version\")[^>]*>"); //$NON-NLS-1$

	@Test
	public void machineReadableBaselineIsConsistent() throws Exception {
		Path root = repositoryRoot();
		String pom = read(root, "pom.xml"); //$NON-NLS-1$
		String target = read(root, "sandbox_target/eclipse.target"); //$NON-NLS-1$
		String tychoVersion = firstGroup(TYCHO_PROPERTY, pom, "root Tycho property"); //$NON-NLS-1$

		JsonObject repository = JsonParser.parseString(read(root, "docs/capabilities.json")) //$NON-NLS-1$
				.getAsJsonObject().getAsJsonObject("repository"); //$NON-NLS-1$
		String inventoryTychoVersion = repository.get("tychoVersion").getAsString(); //$NON-NLS-1$
		String eclipseRelease = repository.get("eclipseRelease").getAsString(); //$NON-NLS-1$
		assertEquals(tychoVersion, inventoryTychoVersion,
				"The capability inventory must use the root Tycho version"); //$NON-NLS-1$
		assertTrue(pom.contains("This project uses Tycho ${tycho-version}, which"), //$NON-NLS-1$
				"The Java enforcer diagnostic must interpolate the Tycho property"); //$NON-NLS-1$

		assertEquals(Set.of(eclipseRelease), releaseRepositories(pom),
				"The root POM must resolve only the declared Eclipse release"); //$NON-NLS-1$
		assertEquals(Set.of(eclipseRelease), releaseRepositories(target),
				"The PDE target must resolve only the declared Eclipse release"); //$NON-NLS-1$
		assertEquals(Set.of(eclipseRelease),
				releaseRepositories(read(root, "sandbox_oomph/sandboxproject.setup")), //$NON-NLS-1$
				"The official Oomph project must install tools from the declared Eclipse release"); //$NON-NLS-1$
		assertEquals(Set.of(eclipseRelease),
				releaseRepositories(read(root, "sandbox_product/sandbox.product")), //$NON-NLS-1$
				"The product must provision only the declared Eclipse release"); //$NON-NLS-1$
		assertEquals(Set.of(eclipseRelease),
				releaseRepositories(read(root, "sandbox_product/category.xml")), //$NON-NLS-1$
				"The published p2 category must refer clients to the declared Eclipse release"); //$NON-NLS-1$
		bouncyCastleVersions(target);

		String oomph = read(root, "sandbox_oomph/sandbox.setup"); //$NON-NLS-1$
		String variableTag = firstMatch(OOMPH_RELEASE_VARIABLE, oomph,
				"Oomph eclipse.target.version variable"); //$NON-NLS-1$
		assertTrue(variableTag.contains("value=\"" + eclipseRelease + "\""), //$NON-NLS-1$ //$NON-NLS-2$
				"The Oomph release variable value must match the capability inventory"); //$NON-NLS-1$
		assertTrue(variableTag.contains("defaultValue=\"" + eclipseRelease + "\""), //$NON-NLS-1$ //$NON-NLS-2$
				"The Oomph release variable default must match the capability inventory"); //$NON-NLS-1$

		String compatibilityScript = read(root,
				".github/scripts/compare_patched_jdt_ui_with_target.sh"); //$NON-NLS-1$
		assertTrue(compatibilityScript.contains(
				"\"$ROOT_DIR/sandbox_target/eclipse.target\""), //$NON-NLS-1$
				"The patched JDT UI check must read the executable target definition"); //$NON-NLS-1$
		assertTrue(compatibilityScript.contains(
				"target_release = eclipse_release(target_path)"), //$NON-NLS-1$
				"The patched JDT UI report must derive its release from the target"); //$NON-NLS-1$
		assertTrue(compatibilityScript.contains("Eclipse {target_release}"), //$NON-NLS-1$
				"The patched JDT UI report must render the derived target release"); //$NON-NLS-1$
	}

	@Test
	public void activeDocumentationNamesTheExecutableBaseline() throws Exception {
		Path root = repositoryRoot();
		String pom = read(root, "pom.xml"); //$NON-NLS-1$
		String target = read(root, "sandbox_target/eclipse.target"); //$NON-NLS-1$
		String tychoVersion = firstGroup(TYCHO_PROPERTY, pom, "root Tycho property"); //$NON-NLS-1$
		JsonObject repository = JsonParser.parseString(read(root, "docs/capabilities.json")) //$NON-NLS-1$
				.getAsJsonObject().getAsJsonObject("repository"); //$NON-NLS-1$
		String eclipseRelease = repository.get("eclipseRelease").getAsString(); //$NON-NLS-1$
		String bouncyCastleVersion = displayBouncyCastleVersions(bouncyCastleVersions(target));

		Map<String, List<String>> expectedClaims = Map.ofEntries(
				Map.entry("README.md", List.of("Maven/Tycho " + tychoVersion, //$NON-NLS-1$ //$NON-NLS-2$
						"`main` (" + eclipseRelease + ")")), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("CONTRIBUTING.md", List.of("Tycho " + tychoVersion, //$NON-NLS-1$ //$NON-NLS-2$
						"Eclipse " + eclipseRelease)), //$NON-NLS-1$
				Map.entry(".github/copilot-instructions.md", List.of( //$NON-NLS-1$
						"Tycho " + tychoVersion, "Eclipse " + eclipseRelease)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry(".github/copilot-ref-build.md", List.of( //$NON-NLS-1$
						"| Tycho | " + tychoVersion + " |", //$NON-NLS-1$ //$NON-NLS-2$
						"| Eclipse target | Eclipse " + eclipseRelease + " / Platform ")), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("docs/distribution-compatibility.md", List.of( //$NON-NLS-1$
						"Tycho " + tychoVersion, "Eclipse " + eclipseRelease)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("sandbox_oomph/README.md", List.of( //$NON-NLS-1$
						"Eclipse " + eclipseRelease, "Tycho " + tychoVersion)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("sandbox_oomph/ARCHITECTURE.md", List.of( //$NON-NLS-1$
						"Eclipse " + eclipseRelease, "Tycho " + tychoVersion)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("sandbox_oomph/TODO.md", List.of( //$NON-NLS-1$
						"Eclipse " + eclipseRelease, "Tycho " + tychoVersion)), //$NON-NLS-1$ //$NON-NLS-2$
				Map.entry("sandbox_target/README.md", List.of( //$NON-NLS-1$
						"Eclipse " + eclipseRelease, //$NON-NLS-1$
						"| Tycho | " + tychoVersion, //$NON-NLS-1$
						"| Bouncy Castle | " + bouncyCastleVersion)), //$NON-NLS-1$
				Map.entry("sandbox_target/ARCHITECTURE.md", List.of( //$NON-NLS-1$
						"Eclipse " + eclipseRelease, //$NON-NLS-1$
						"Tycho " + tychoVersion, //$NON-NLS-1$
						"Bouncy Castle " + bouncyCastleVersion)), //$NON-NLS-1$
				Map.entry("sandbox_target/TODO.md", List.of( //$NON-NLS-1$
						"Eclipse " + eclipseRelease, //$NON-NLS-1$
						"| Tycho | " + tychoVersion, //$NON-NLS-1$
						"| Bouncy Castle | " + bouncyCastleVersion))); //$NON-NLS-1$

		for (Map.Entry<String, List<String>> entry : expectedClaims.entrySet()) {
			String content = read(root, entry.getKey());
			for (String expectedClaim : entry.getValue()) {
				assertTrue(content.contains(expectedClaim), () -> entry.getKey()
						+ " must name the executable baseline: " + expectedClaim); //$NON-NLS-1$
			}
		}
	}

	@Test
	public void bouncyCastleVersionDisplayMakesIntentionalProviderPatchVisible() {
		Map<String, String> versions = Map.of(
				"bcutil", "1.85.0", //$NON-NLS-1$ //$NON-NLS-2$
				"bcprov", "1.85.2", //$NON-NLS-1$ //$NON-NLS-2$
				"bcpkix", "1.85.0", //$NON-NLS-1$ //$NON-NLS-2$
				"bcpg", "1.85.0"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("1.85 family (bcprov 1.85.2)", displayBouncyCastleVersions(versions)); //$NON-NLS-1$
	}

	@Test
	public void bouncyCastleTargetRejectsInvertedOrUniformProviderVersions() {
		assertEquals(BOUNCY_CASTLE_VERSIONS, bouncyCastleVersions(bouncyCastleUnits(BOUNCY_CASTLE_VERSIONS)));
		for (Map<String, String> invalid : List.of(
				Map.of("bcutil", "1.85.2", "bcprov", "1.85.0", "bcpkix", "1.85.2", "bcpg", "1.85.2"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
				Map.of("bcutil", "1.85.0", "bcprov", "1.85.0", "bcpkix", "1.85.0", "bcpg", "1.85.0"))) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
			assertThrows(AssertionError.class, () -> bouncyCastleVersions(bouncyCastleUnits(invalid)));
		}
	}

	private static String bouncyCastleUnits(Map<String, String> versions) {
		return versions.entrySet().stream()
				.map(entry -> "<unit id=\"" + entry.getKey() + "\" version=\"" + entry.getValue() + "\"/>") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				.collect(Collectors.joining());
	}

	@Test
	public void activePatchedHostDocumentationMatchesThePins() throws Exception {
		Path root = repositoryRoot();
		Properties pins = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve(".github/patched-jdt-ui.env"), StandardCharsets.UTF_8)) { //$NON-NLS-1$
			pins.load(reader);
		}
		String eclipseRelease = JsonParser.parseString(read(root, "docs/capabilities.json")).getAsJsonObject() //$NON-NLS-1$
				.getAsJsonObject("repository").get("eclipseRelease").getAsString(); //$NON-NLS-1$ //$NON-NLS-2$
		for (String path : List.of("docs/patched-jdt-ui-delivery.md", "docs/multi-file-cleanups.md", //$NON-NLS-1$ //$NON-NLS-2$
				"sandbox_int_to_enum/README.md")) { //$NON-NLS-1$
			String document = read(root, path);
			assertTrue(document.contains("Eclipse " + eclipseRelease), path); //$NON-NLS-1$
			if (path.startsWith("docs/")) { //$NON-NLS-1$
				for (String key : List.of("PATCHED_JDT_UI_COMMIT", "PATCHED_JDT_UI_EXPECTED_PARENT", //$NON-NLS-1$ //$NON-NLS-2$
						"PATCHED_JDT_UI_EXPECTED_BASE_VERSION")) { //$NON-NLS-1$
					String expected = pins.getProperty(key);
					assertTrue(expected != null && !expected.isBlank(), key);
					assertTrue(document.contains(expected), path + " must document " + key); //$NON-NLS-1$
				}
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

	private static Map<String, String> bouncyCastleVersions(String target) {
		Map<String, String> versions = new LinkedHashMap<>();
		Matcher matcher = BOUNCY_CASTLE_UNIT.matcher(target);
		while (matcher.find()) {
			versions.put(matcher.group(1), matcher.group(2));
		}
		assertEquals(BOUNCY_CASTLE_IDS, versions.keySet(),
				"The target must declare the complete four-bundle Bouncy Castle set"); //$NON-NLS-1$
		assertEquals(BOUNCY_CASTLE_VERSIONS, versions,
				"The target must retain the release-specific provider/base version mapping"); //$NON-NLS-1$
		return Map.copyOf(versions);
	}

	private static String displayBouncyCastleVersions(Map<String, String> versions) {
		String base = displayVersion(versions.get("bcutil")); //$NON-NLS-1$
		String provider = displayVersion(versions.get("bcprov")); //$NON-NLS-1$
		if (versions.values().stream().distinct().count() == 1) {
			return base;
		}
		assertEquals(Set.of(versions.get("bcutil"), versions.get("bcprov")), //$NON-NLS-1$ //$NON-NLS-2$
				Set.copyOf(versions.values()),
				"Only the explicitly documented bcprov patch-level divergence is supported"); //$NON-NLS-1$
		assertEquals(versions.get("bcutil"), versions.get("bcpkix")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(versions.get("bcutil"), versions.get("bcpg")); //$NON-NLS-1$ //$NON-NLS-2$
		return base + " family (bcprov " + provider + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String displayVersion(String version) {
		return version.endsWith(".0") //$NON-NLS-1$
				? version.substring(0, version.length() - 2)
				: version;
	}

	private static Set<String> releaseRepositories(String content) {
		Matcher matcher = ECLIPSE_RELEASE_REPOSITORY.matcher(content);
		return matcher.results().map(result -> result.group(1)).collect(Collectors.toUnmodifiableSet());
	}
}
