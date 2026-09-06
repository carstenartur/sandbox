/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ui.IMarkerResolution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.ui.tests.quickfix.XMLTestUtils;

class PdeXmlCleanupMarkerServiceTest {

	private static final String SOURCE= """
			<?xml version="1.0" encoding="UTF-8"?>
			<schema xmlns="http://www.w3.org/2001/XMLSchema">
			      <annotation>
			            <documentation>Keep text content unchanged.</documentation>
			      </annotation>
			</schema>
			""";

	private IProject project;
	private final NullProgressMonitor monitor= new NullProgressMonitor();

	@BeforeEach
	void createProject() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot()
				.getProject("PdeXmlMarkerTest-" + System.nanoTime()); //$NON-NLS-1$
		project.create(monitor);
		project.open(monitor);
	}

	@AfterEach
	void deleteProject() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, monitor);
		}
	}

	@Test
	void createsProblemResolutionAppliesSharedCleanupAndClearsMarker() throws Exception {
		String compact= SchemaTransformationUtils.transform(SOURCE, StandardCharsets.UTF_8, false);
		String indented= SchemaTransformationUtils.transform(SOURCE, StandardCharsets.UTF_8, true);
		assertNotEquals(compact, indented, "The fixture must distinguish compact and indented modes"); //$NON-NLS-1$
		IFile file= createFile("schema/sample.exsd", SOURCE); //$NON-NLS-1$
		PdeXmlCleanupMarkerService service= new PdeXmlCleanupMarkerService();

		assertEquals(1, service.refresh(file, monitor));
		assertEquals(SOURCE, read(file), "Marker analysis must not apply formatting"); //$NON-NLS-1$
		IMarker[] markers= file.findMarkers(PdeXmlCleanupMarkerService.MARKER_TYPE,
				false, IResource.DEPTH_ZERO);
		assertEquals(1, markers.length);
		assertEquals(PdeXmlCleanupMarkerService.MARKER_MESSAGE,
				markers[0].getAttribute(IMarker.MESSAGE));
		assertEquals(IMarker.SEVERITY_WARNING,
				markers[0].getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO));
		assertTrue(markers[0].getAttribute(IMarker.LINE_NUMBER, -1) > 0);

		ExsdMarkerResolutionGenerator generator= new ExsdMarkerResolutionGenerator();
		assertTrue(generator.hasResolutions(markers[0]));
		IMarkerResolution[] resolutions= generator.getResolutions(markers[0]);
		assertEquals(1, resolutions.length);
		assertEquals("Normalize PDE XML formatting", resolutions[0].getLabel()); //$NON-NLS-1$
		resolutions[0].run(markers[0]);

		String normalized= read(file);
		assertEquals(compact, normalized, "The marker resolution uses the standalone compact mode"); //$NON-NLS-1$
		assertFalse(SOURCE.equals(normalized));
		XMLTestUtils.assertXmlSemanticallyEqual(SOURCE, normalized);
		assertEquals(0, file.findMarkers(PdeXmlCleanupMarkerService.MARKER_TYPE,
				false, IResource.DEPTH_ZERO).length);
		assertEquals(0, service.refresh(file, monitor),
				"The applied quick fix must be idempotent"); //$NON-NLS-1$
	}

	@Test
	void standaloneCompactServicesDoNotShareIndentationState() throws Exception {
		XMLCleanupService configured= XMLCleanupService.compactFormatting();
		configured.setEnableIndent(true);
		XMLCleanupService standalone= XMLCleanupService.compactFormatting();
		IFile indentedFile= createFile("schema/indented.exsd", SOURCE); //$NON-NLS-1$
		IFile compactFile= createFile("schema/compact.exsd", SOURCE); //$NON-NLS-1$

		assertTrue(configured.processFile(indentedFile, monitor));
		assertTrue(standalone.processFile(compactFile, monitor));
		String indented= read(indentedFile);
		String compact= read(compactFile);
		assertEquals(SchemaTransformationUtils.transform(SOURCE, StandardCharsets.UTF_8, true), indented);
		assertEquals(SchemaTransformationUtils.transform(SOURCE, StandardCharsets.UTF_8, false), compact);
		assertNotEquals(indented, compact, "Changing another service must not change the standalone policy"); //$NON-NLS-1$
		XMLTestUtils.assertXmlSemanticallyEqual(SOURCE, compact);
		XMLTestUtils.assertXmlSemanticallyEqual(SOURCE, indented);
		assertFalse(standalone.processFile(compactFile, monitor), "Compact formatting must be idempotent"); //$NON-NLS-1$
	}

	@Test
	void projectAnalysisCoversRootDescriptorsAndSchemaDirectoriesOnly() throws Exception {
		createFile("plugin.xml", SOURCE.replace("schema", "plugin")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		createFile("schema/extension.exsd", SOURCE); //$NON-NLS-1$
		createFile("src/unrelated.xml", SOURCE.replace("schema", "root")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		PdeXmlCleanupMarkerService service= new PdeXmlCleanupMarkerService();
		assertEquals(2, service.refresh(project, monitor));
		assertEquals(2, project.findMarkers(PdeXmlCleanupMarkerService.MARKER_TYPE,
				false, IResource.DEPTH_INFINITE).length);
	}

	private IFile createFile(String path, String content) throws Exception {
		IFile file= project.getFile(path);
		if (file.getParent() instanceof IFolder folder && !folder.exists()) {
			createFolder(folder);
		}
		try (ByteArrayInputStream input=
				new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
			file.create(input, true, monitor);
		}
		return file;
	}

	private void createFolder(IFolder folder) throws Exception {
		if (folder.getParent() instanceof IFolder parent && !parent.exists()) {
			createFolder(parent);
		}
		folder.create(true, true, monitor);
	}

	private static String read(IFile file) throws Exception {
		try (InputStream input= file.getContents()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
