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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Tests for workspace-level (project) hint file loading and management.
 *
 * <p>Since these are pure JUnit 5 tests without an Eclipse workspace,
 * they test the registry-level logic for project hint files via
 * {@code loadFromString} with project-scoped IDs.</p>
 *
 * <p>The actual workspace discovery via {@link HintFileRegistry#loadProjectHintFiles}
 * requires a PDE test environment with a real {@code IProject}.</p>
 *
 * @since 1.3.6
 */
public class WorkspaceHintFileTest {

	private final HintFileRegistry registry = HintFileRegistry.getInstance();

	@BeforeEach
	public void setUp() {
		registry.clear();
	}

	@AfterEach
	public void tearDown() {
		registry.clear();
	}

	@Test
	public void testProjectScopedIdConvention() throws HintParseException {
		// Simulate what loadProjectHintFiles does: project-scoped IDs
		String content = """
			<!id: my-project-rules>
			<!description: Custom rules for MyProject>

			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$

		String projectId = "project:MyProject:custom-rules.sandbox-hint"; //$NON-NLS-1$
		registry.loadFromString(projectId, content);

		HintFile hintFile = registry.getHintFile(projectId);
		assertNotNull(hintFile, "Project hint file should be registered"); //$NON-NLS-1$
		assertEquals("my-project-rules", hintFile.getId()); //$NON-NLS-1$
		assertEquals(1, hintFile.getRules().size());
	}

	@Test
	public void testProjectHintFilesCoexistWithBundled() throws HintParseException {
		// Load bundled libraries first
		List<String> bundled = registry.loadBundledLibraries(getClass().getClassLoader());
		int bundledCount = bundled.size();
		assertTrue(bundledCount > 0, "Should have bundled libraries loaded"); //$NON-NLS-1$

		// Add a project hint file
		String content = """
			<!id: project-extra>
			$x * 1
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("project:TestProject:extra.sandbox-hint", content); //$NON-NLS-1$

		// Both should be accessible
		Map<String, HintFile> all = registry.getAllHintFiles();
		assertEquals(bundledCount + 1, all.size(),
				"Should have bundled + project hint files"); //$NON-NLS-1$
	}

	@Test
	public void testProjectHintFileIncludesComposition() throws HintParseException {
		// Load bundled libraries (provides "collections" ID)
		registry.loadBundledLibraries(getClass().getClassLoader());

		// Project hint file includes bundled "collections"
		String content = """
			<!id: project-composite>
			<!include: collections>

			$x - 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("project:TestProject:composite.sandbox-hint", content); //$NON-NLS-1$

		HintFile composite = registry.getHintFile("project:TestProject:composite.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(composite);

		// Resolve includes should pull in collections rules
		var allRules = registry.resolveIncludes(composite);
		assertTrue(allRules.size() > 1,
				"Should include own rules + collections rules"); //$NON-NLS-1$
	}

	@Test
	public void testMultipleProjectHintFiles() throws HintParseException {
		// Simulate two projects each with their own hint files
		String content1 = """
			<!id: project-a-rules>
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		String content2 = """
			<!id: project-b-rules>
			$x * 1
			=> $x
			;;
			"""; //$NON-NLS-1$

		registry.loadFromString("project:ProjectA:rules.sandbox-hint", content1); //$NON-NLS-1$
		registry.loadFromString("project:ProjectB:rules.sandbox-hint", content2); //$NON-NLS-1$

		assertEquals(2, registry.getAllHintFiles().size(),
				"Should have hint files from both projects"); //$NON-NLS-1$
	}

	@Test
	public void testLoadProjectHintFilesWithNullProject() {
		// loadProjectHintFiles should handle null gracefully
		List<String> loaded = registry.loadProjectHintFiles(null);
		assertTrue(loaded.isEmpty(), "Should return empty list for null project"); //$NON-NLS-1$
	}

	@Test
	public void testInvalidateProjectAllowsRescan() throws HintParseException {
		// Load a project hint file
		String content = """
			<!id: test-rules>
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("project:TestProject:test.sandbox-hint", content); //$NON-NLS-1$

		// Invalidate with null should not throw
		registry.invalidateProject(null);

		// Hint file should still be accessible
		assertNotNull(registry.getHintFile("project:TestProject:test.sandbox-hint")); //$NON-NLS-1$
	}

	@Test
	public void testClearRemovesProjectHintFiles() throws HintParseException {
		// Load bundled + project
		registry.loadBundledLibraries(getClass().getClassLoader());
		String content = """
			<!id: project-rules>
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("project:TestProject:rules.sandbox-hint", content); //$NON-NLS-1$

		assertFalse(registry.getAllHintFiles().isEmpty());

		registry.clear();

		assertTrue(registry.getAllHintFiles().isEmpty(),
				"Clear should remove all hint files including project ones"); //$NON-NLS-1$
	}

	@Test
	public void testUnregisterProjectHintFile() throws HintParseException {
		String content = """
			<!id: temp-rules>
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		String id = "project:TestProject:temp.sandbox-hint"; //$NON-NLS-1$
		registry.loadFromString(id, content);

		assertNotNull(registry.getHintFile(id));

		HintFile removed = registry.unregister(id);
		assertNotNull(removed);
		assertEquals("temp-rules", removed.getId()); //$NON-NLS-1$

		// Should no longer be findable
		assertEquals(Collections.emptyMap(), registry.getAllHintFiles());
	}
}
