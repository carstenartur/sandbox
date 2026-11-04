/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises actual workspace writes through the standalone command services. */
class PdeAppinfoCleanupIntegrationTest {

	private static final String SOURCE= "<schema><annotation><appInfo><meta.schema plugin=\"example\" id=\"items\" name=\"Items\"/></appInfo><documentation>   </documentation></annotation></schema>"; //$NON-NLS-1$
	private final NullProgressMonitor monitor= new NullProgressMonitor();
	private IProject project;

	@BeforeEach
	void createProject() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot().getProject("AppinfoCommandTest-" + System.nanoTime()); //$NON-NLS-1$
		project.create(monitor);
		project.open(monitor);
	}

	@AfterEach
	void deleteProject() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, monitor);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void interleavedCommandsKeepTheirExplicitOrDefaultTarget(boolean explicitFirst) throws Exception {
		IFile explicit= createFile("explicit.exsd"); //$NON-NLS-1$
		IFile normal= createFile("normal.exsd"); //$NON-NLS-1$
		var explicitService= new PdeAppinfoModernizationHandler().createXMLCleanupService();
		var normalService= new XMLCleanupHandler().createXMLCleanupService();
		if (explicitFirst) {
			assertTrue(explicitService.processFile(explicit, monitor));
			assertTrue(normalService.processFile(normal, monitor));
		} else {
			assertTrue(normalService.processFile(normal, monitor));
			assertTrue(explicitService.processFile(explicit, monitor));
		}
		assertTrue(read(explicit).contains("<appinfo>")); //$NON-NLS-1$
		assertTrue(read(normal).contains("<appInfo>")); //$NON-NLS-1$
		assertTrue(read(explicit).contains("<documentation>   </documentation>")); //$NON-NLS-1$
		assertFalse(new PdeAppinfoModernizationHandler().createXMLCleanupService().processFile(explicit, monitor));
		assertFalse(new XMLCleanupHandler().createXMLCleanupService().processFile(normal, monitor));
	}

	@Test
	void concurrentCommandInvocationsUseIndependentImmutableTargets() throws Exception {
		IFile explicit= createFile("concurrent-explicit.exsd"); //$NON-NLS-1$
		IFile normal= createFile("concurrent-normal.exsd"); //$NON-NLS-1$
		var explicitService= new PdeAppinfoModernizationHandler().createXMLCleanupService();
		var normalService= new XMLCleanupHandler().createXMLCleanupService();
		CountDownLatch start= new CountDownLatch(1);
		try (var executor= Executors.newFixedThreadPool(2)) {
			var explicitResult= executor.submit(() -> {
				start.await();
				return explicitService.processFile(explicit, new NullProgressMonitor());
			});
			var normalResult= executor.submit(() -> {
				start.await();
				return normalService.processFile(normal, new NullProgressMonitor());
			});
			start.countDown();
			assertTrue(explicitResult.get(10, TimeUnit.SECONDS));
			assertTrue(normalResult.get(10, TimeUnit.SECONDS));
		}
		assertTrue(read(explicit).contains("<appinfo>")); //$NON-NLS-1$
		assertTrue(read(normal).contains("<appInfo>")); //$NON-NLS-1$
	}

	private IFile createFile(String name) throws Exception {
		IFile file= project.getFile(name);
		file.create(new ByteArrayInputStream(SOURCE.getBytes(StandardCharsets.UTF_8)), true, monitor);
		file.setCharset(StandardCharsets.UTF_8.name(), monitor);
		return file;
	}

	private static String read(IFile file) throws Exception {
		try (var content= file.getContents()) {
			return new String(content.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
