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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Verifies that project-local Hint DSL files remain live workspace resources,
 * rather than a one-time snapshot of the first registry scan.
 */
public class WorkspaceHintReloadTest {

	private static final String PROJECT_NAME= "SandboxHintReloadTest"; //$NON-NLS-1$
	private static final String FILE_NAME= "rules.sandbox-hint"; //$NON-NLS-1$
	private static final String REGISTRY_ID= "project:" + PROJECT_NAME + ":" + FILE_NAME; //$NON-NLS-1$ //$NON-NLS-2$

	private final HintFileRegistry registry= HintFileRegistry.getInstance();
	private IProject project;

	@BeforeEach
	public void setUp() throws Exception {
		registry.clear();
		project= ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
		NullProgressMonitor monitor= new NullProgressMonitor();
		if (project.exists()) {
			project.delete(true, true, monitor);
		}
		project.create(monitor);
		project.open(monitor);
	}

	@AfterEach
	public void tearDown() throws Exception {
		registry.clear();
		if (project != null && project.exists()) {
			project.delete(true, true, new NullProgressMonitor());
		}
	}

	@Test
	public void createEditAndDeleteAreVisibleWithoutWorkbenchRestart() throws Exception {
		IFile hintFile= project.getFile(FILE_NAME);
		hintFile.create(contents(rule("first-rule", "$x + 0", "$x")), true, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		List<String> initialLoad= registry.loadProjectHintFiles(project);
		assertEquals(List.of(REGISTRY_ID), initialLoad);
		assertNotNull(registry.getHintFile(REGISTRY_ID));
		assertEquals("first-rule", registry.getHintFile(REGISTRY_ID).getId()); //$NON-NLS-1$

		hintFile.setContents(contents(rule("edited-rule", "$x * 1", "$x")), true, false, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		List<String> reloaded= registry.loadProjectHintFiles(project);
		assertTrue(reloaded.contains(REGISTRY_ID),
				"Editing a Hint DSL file must invalidate the one-shot project scan"); //$NON-NLS-1$
		assertEquals("edited-rule", registry.getHintFile(REGISTRY_ID).getId()); //$NON-NLS-1$

		hintFile.delete(true, null);

		assertNull(registry.getHintFile(REGISTRY_ID),
				"Deleting a Hint DSL file must remove the stale project rule immediately"); //$NON-NLS-1$
		assertTrue(registry.loadProjectHintFiles(project).isEmpty());
	}

	private static ByteArrayInputStream contents(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	private static String rule(String id, String source, String replacement) {
		return "<!id: " + id + ">\n\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ source + "\n=> " + replacement + "\n;;\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
