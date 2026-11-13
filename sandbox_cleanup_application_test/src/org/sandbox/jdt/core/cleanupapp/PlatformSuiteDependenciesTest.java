/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.Test;

class PlatformSuiteDependenciesTest {
	static final String MODEL = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>org.example</groupId><artifactId>tests</artifactId><version>1</version>
			  <dependencyManagement><dependencies><dependency>
			    <groupId>org.junit</groupId><artifactId>junit-bom</artifactId><version>5.14.4</version>
			    <type>pom</type><scope>import</scope>
			  </dependency></dependencies></dependencyManagement>
			  <dependencies>
			    <!-- Retain the Vintage engine for other consumers. -->
			    <dependency><groupId>org.junit.vintage</groupId><artifactId>junit-vintage-engine</artifactId></dependency>
			  </dependencies>
			</project>
			""";

	@Test
	void addsBomManagedTestLauncherAndPreservesOtherDeclarationsAndLineEndings() throws Exception {
		String source = MODEL.replace("\n", "\r\n");
		String result = PlatformSuiteDependencies.addToPom(source);
		String addition = """
				    <dependency>
				      <groupId>org.junit.platform</groupId>
				      <artifactId>junit-platform-suite</artifactId>
				      <scope>test</scope>
				    </dependency>
				""".replace("\n", "\r\n");
		assertEquals(source.replace("  </dependencies>", addition + "  </dependencies>"), result);
		assertEquals(result, PlatformSuiteDependencies.addToPom(result));
	}

	@Test
	void respectsExistingSuiteDependencyAndDoesNotInventAnUnmanagedVersion() throws Exception {
		String unmanaged = MODEL.replace("<artifactId>junit-bom</artifactId>", "<artifactId>other</artifactId>");
		assertThrows(IOException.class, () -> PlatformSuiteDependencies.addToPom(unmanaged));
		String existing = unmanaged.replace("org.junit.vintage", "org.junit.platform")
				.replace("junit-vintage-engine", "junit-platform-suite");
		assertEquals(existing, PlatformSuiteDependencies.addToPom(existing));
	}

	@Test
	void refusesAmbiguousMavenModelsBeforeChangingTheirBytes() {
		for (String unsupported : new String[] { MODEL.replace("<dependencies>\n", "<dependencies combine.self=\"override\">\n"),
				MODEL.replace("</project>", "<profiles><profile><id>ci</id></profile></profiles></project>"),
				MODEL.replace("<modelVersion>", "<parent><artifactId>parent</artifactId></parent><modelVersion>"),
				MODEL.replace("<version>5.14.4</version>", "<version>5.7.2</version>"),
				"<!DOCTYPE project []>" + MODEL, MODEL.replace("</project>", "</malformed>") }) {
			assertThrows(IOException.class, () -> PlatformSuiteDependencies.addToPom(unsupported));
		}
	}

	@Test
	void detectsNewSuiteDeclarationsAndIgnoresCommentsStringsAndUnusedImports() {
		String declaration = "import org.junit.platform.suite.api.Suite; @Suite class Tests {}";
		String fq = "@org.junit.platform.suite.api.Suite class Tests {}";
		assertTrue(PlatformSuiteDependencies.usesSuite(declaration, JavaCore.getOptions()));
		assertTrue(PlatformSuiteDependencies.usesSuite(fq, JavaCore.getOptions()));
		for (String irrelevant : new String[] { "/* " + declaration + " */ class Tests {}",
				"class Tests { String s = \"@org.junit.platform.suite.api.Suite\"; }",
				"import org.junit.platform.suite.api.Suite; class Tests {}",
				"import other.Suite; @Suite class Tests {}" }) {
			assertFalse(PlatformSuiteDependencies.usesSuite(irrelevant, JavaCore.getOptions()));
		}
	}
}
