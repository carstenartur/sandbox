/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies shipped profiles against the actual installed cleanup contributions. */
class ShippedCleanupProfilesTest {

	@ParameterizedTest
	@ValueSource(strings = { "minimal", "standard", "aggressive" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void everyOptionIsRecognizedByTheInstalledCleanupRegistry(String name) throws Exception {
		var registered= JavaPlugin.getDefault().getCleanUpRegistry()
				.getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS);
		Set<String> unknown= new HashSet<>(profile(name).keySet());
		unknown.removeAll(registered.getKeys());
		assertTrue(unknown.isEmpty(), () -> name + " contains silently ignored cleanup options: " + unknown); //$NON-NLS-1$
	}

	@ParameterizedTest
	@CsvSource({ "minimal,false,false", "standard,true,false", "aggressive,true,true" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void documentedTransformationsAreEnabledAtTheSelectedIntensity(String name,
			boolean standard, boolean aggressive) throws Exception {
		var values= profile(name);
		for (String id : Set.of("org.eclipse.jdt.ui.cleanup.unnecessary_array_creation", //$NON-NLS-1$
				"org.eclipse.jdt.ui.cleanup.stringbuffertostringbuilder", //$NON-NLS-1$
				"org.eclipse.jdt.ui.cleanup.simplify_boolean_if_else")) { //$NON-NLS-1$
			assertActive(id, values, standard);
		}
		for (String id : Set.of("org.eclipse.jdt.ui.cleanup.explicitencoding", //$NON-NLS-1$
				"org.eclipse.jdt.ui.cleanup.functionalcleanup", //$NON-NLS-1$
				"org.eclipse.jdt.ui.cleanup.simplifystatus", //$NON-NLS-1$
				"org.eclipse.jdt.ui.cleanup.junitcleanup")) { //$NON-NLS-1$
			assertActive(id, values, aggressive);
		}
	}

	@Test
	void aggressiveSelectsCompatibleStrategiesAndExecutableOperations() throws Exception {
		var options= options(profile("aggressive")); //$NON-NLS-1$
		assertTrue(options.isEnabled("cleanup.explicit_encoding_keep_behavior")); //$NON-NLS-1$
		assertFalse(options.isEnabled("cleanup.explicit_encoding_insert_utf8")); //$NON-NLS-1$
		assertFalse(options.isEnabled("cleanup.explicit_encoding_aggregate_to_utf8")); //$NON-NLS-1$
		assertEquals("stream", options.getValue("cleanup.loop_conversion.target_format")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(options.isEnabled("cleanup.loop_conversion.from.enhanced_for")); //$NON-NLS-1$
		assertTrue(options.isEnabled("cleanup.loop_conversion.from.iterator_while")); //$NON-NLS-1$
		assertFalse(options.isEnabled("cleanup.loop_conversion.from.stream")); //$NON-NLS-1$
		assertFalse(options.isEnabled("cleanup.loop_conversion.from.classic_for")); //$NON-NLS-1$
		assertFalse(options.isEnabled("cleanup.junitcleanup_best_effort")); //$NON-NLS-1$
		assertFalse(options.isEnabled("cleanup.junitcleanup_6_compatibility")); //$NON-NLS-1$
		assertTrue(options.isEnabled("cleanup.junitcleanup_3_test")); //$NON-NLS-1$
		assertTrue(options.isEnabled("cleanup.junitcleanup_4_test")); //$NON-NLS-1$
		assertTrue(options.isEnabled("cleanup.junitcleanup_4_assert")); //$NON-NLS-1$
		assertTrue(options.isEnabled("cleanup.qualify_static_member_accesses_with_declaring_class")); //$NON-NLS-1$
		assertTrue(options.isEnabled("cleanup.remove_trailing_whitespaces")); //$NON-NLS-1$
		assertTrue(options.isEnabled("cleanup.remove_trailing_whitespaces_all")); //$NON-NLS-1$
	}

	private static void assertActive(String id, Map<String, String> values, boolean expected) throws Exception {
		for (var element : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps")) { //$NON-NLS-1$
			if ("cleanUp".equals(element.getName()) && id.equals(element.getAttribute("id"))) { //$NON-NLS-1$ //$NON-NLS-2$
				var cleanup= (ICleanUp) element.createExecutableExtension("class"); //$NON-NLS-1$
				cleanup.setOptions(options(values));
				assertEquals(expected, cleanup.getRequirements().requiresAST(), id + " AST requirement"); //$NON-NLS-1$
				assertEquals(expected, cleanup.getStepDescriptions().length > 0, id + " active operations"); //$NON-NLS-1$
				return;
			}
		}
		fail("Cleanup contribution is missing from the runtime: " + id); //$NON-NLS-1$
	}

	private static CleanUpOptions options(Map<String, String> values) {
		var options= new CleanUpOptions();
		values.forEach(options::setOption);
		return options;
	}

	private static Map<String, String> profile(String name) throws IOException {
		for (Path directory= Path.of(System.getProperty("user.dir")).toAbsolutePath(); //$NON-NLS-1$
				directory != null; directory= directory.getParent()) {
			Path file= directory.resolve(".github/cleanup-profiles/" + name + ".properties"); //$NON-NLS-1$ //$NON-NLS-2$
			if (Files.isRegularFile(file)) {
				var properties= new Properties();
				try (var reader= Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					properties.load(reader);
				}
				var result= new HashMap<String, String>();
				properties.forEach((key, value) -> result.put((String) key, (String) value));
				return result;
			}
		}
		throw new IOException("Cannot find shipped cleanup profile: " + name); //$NON-NLS-1$
	}
}
