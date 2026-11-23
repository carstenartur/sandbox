/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.jar.JarFile;

import org.eclipse.jdt.internal.junit5.runner.JUnit5TestLoader;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.launcher.Launcher;
import org.opentest4j.MultipleFailuresError;

class SupportArtifactIT {
	@Test void consumerLoadsTheCanonicalJava21BundleAndItsServices() throws Exception {
		Path artifact= Path.of(System.getProperty("support.artifact")).toRealPath();
		try (var jar= new JarFile(artifact.toFile())) {
			var classes= jar.stream().filter(e -> e.getName().endsWith(".class")).toList();
			assertFalse(classes.isEmpty());
			for (var entry : classes) {
				String name= entry.getName();
				var type= Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false,
						ExternalResourceExtension.class.getClassLoader());
				assertEquals(artifact, source(type), name);
				try (var packaged= jar.getInputStream(entry); var loaded= type.getResourceAsStream("/" + name)) {
					byte[] bytes= packaged.readAllBytes();
					assertEquals(65, (bytes[6] & 255) << 8 | (bytes[7] & 255), name);
					assertNotNull(loaded, name);
					assertArrayEquals(bytes, loaded.readAllBytes(), name);
				}
			}
			assertEquals(artifact, source(ExternalResourceExtension.class));
			var manifest= jar.getManifest().getMainAttributes();
			assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=21))\"", manifest.getValue("Require-Capability"));
			String imports= "," + manifest.getValue("Import-Package") + ",";
			for (String pkg : List.of("org.junit.platform.engine", "org.junit.platform.engine.support.descriptor", "org.junit.platform.launcher")) {
				assertImport(imports, pkg, "[1.14,7)");
			}
			for (String pkg : List.of("org.junit.jupiter.api.extension", "org.junit.jupiter.api.function")) {
				assertImport(imports, pkg, "[5.14,7)");
			}
			assertImport(imports, "org.opentest4j", "[1.3,2)");
			try (var spi= jar.getInputStream(jar.getJarEntry("META-INF/services/org.junit.platform.launcher.PostDiscoveryFilter"))) {
				assertEquals(InheritedTestsFilter.class.getName(), new String(spi.readAllBytes(), StandardCharsets.UTF_8).strip());
			}
			System.out.printf("Support artifact %s SHA-256=%s classes=%d JVM=%s (%s)%n", artifact,
					HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))),
					classes.size(), Runtime.version(), System.getProperty("java.vendor"));
		}
	}

	@Test void qualifiesActualConsumerDependencies() throws Exception {
		String bom= System.getProperty("support.junit.version");
		String platform= bom.startsWith("5.") ? "1." + bom.substring(2) : bom;
		assertVersion(Test.class, bom);
		var engines= ServiceLoader.load(TestEngine.class).stream().map(ServiceLoader.Provider::get)
				.collect(Collectors.toMap(TestEngine::getId, Function.identity()));
		for (String id : List.of("junit-jupiter", "junit-vintage")) {
			assertNotNull(engines.get(id), id);
			assertVersion(engines.get(id).getClass(), bom);
		}
		assertVersion(Launcher.class, platform);
		assertVersion(TestEngine.class, platform);
		assertVersion(MultipleFailuresError.class, "1.3.0");
		String junit4= junit.runner.Version.id();
		assertEquals("4.13.2", junit4);
		try (var jdt= new JarFile(source(JUnit5TestLoader.class).toFile())) {
			String version= jdt.getManifest().getMainAttributes().getValue("Bundle-Version");
			assertTrue(version.startsWith("1.2.100."), version);
			System.out.printf("JDT JUnit 5 runtime=%s JUnit 4=%s%n", version, junit4);
		}
	}

	private static void assertImport(String imports, String pkg, String range) {
		assertTrue(imports.contains("," + pkg + ";version=\"" + range + "\","), imports);
	}

	private static Path source(Class<?> type) throws Exception {
		return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
	}

	private static void assertVersion(Class<?> type, String expected) {
		String version= type.getPackage().getImplementationVersion();
		assertEquals(expected, version, type::getName);
		System.out.printf("%s=%s%n", type.getName(), version);
	}
}
