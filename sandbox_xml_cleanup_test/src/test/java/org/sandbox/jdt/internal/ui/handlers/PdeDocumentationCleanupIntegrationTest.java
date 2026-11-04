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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.DocumentationPolicy;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.PdeSchemaTarget;
import org.sandbox.jdt.internal.corext.fix.helper.XMLCleanupService;

/** Exercises actual workspace writes through the standalone command services. */
class PdeDocumentationCleanupIntegrationTest {

	private static final String SOURCE= "<schema><annotation><appInfo><meta.schema plugin=\"example\" id=\"items\" name=\"Items\"/></appInfo><documentation>   </documentation></annotation><element name=\"item\"><annotation><documentation>  Raw lazy description  </documentation></annotation></element><annotation><appInfo><meta.section type=\"apiinfo\"/></appInfo><documentation>  Plain reference text  </documentation></annotation></schema>"; //$NON-NLS-1$
	private final NullProgressMonitor monitor= new NullProgressMonitor();
	private IProject project;

	@BeforeEach
	void createProject() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot().getProject("DocumentationCommandTest-" + System.nanoTime()); //$NON-NLS-1$
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
	void interleavedCommandsKeepTheirExplicitOrDefaultPolicy(boolean explicitFirst) throws Exception {
		IFile explicit= createFile("explicit.exsd"); //$NON-NLS-1$
		IFile normal= createFile("normal.exsd"); //$NON-NLS-1$
		var explicitService= new PdeDocumentationBoundaryHandler().createXMLCleanupService();
		var normalService= new XMLCleanupHandler().createXMLCleanupService();
		if (explicitFirst) {
			assertTrue(explicitService.processFile(explicit, monitor));
			assertTrue(normalService.processFile(normal, monitor));
		} else {
			assertTrue(normalService.processFile(normal, monitor));
			assertTrue(explicitService.processFile(explicit, monitor));
		}
		assertTrue(read(explicit).contains("<documentation>Plain reference text</documentation>")); //$NON-NLS-1$
		assertTrue(read(normal).contains("<documentation>  Plain reference text  </documentation>")); //$NON-NLS-1$
		assertTrue(read(explicit).contains("<documentation>  Raw lazy description  </documentation>")); //$NON-NLS-1$
		assertTrue(read(explicit).contains("<documentation>   </documentation>")); //$NON-NLS-1$
		assertFalse(new PdeDocumentationBoundaryHandler().createXMLCleanupService().processFile(explicit, monitor));
		assertFalse(new XMLCleanupHandler().createXMLCleanupService().processFile(normal, monitor));
	}

	@Test
	void interleavedServicesKeepBothPoliciesAndIndentationIndependent() throws Exception {
		String source= """
				<schema>
				  <annotation><appInfo>
				    <meta.schema plugin="example" id="items" name="Items"> \t </meta.schema>
				  </appInfo><documentation>  Overview  stays&#9;raw  </documentation></annotation>
				  <annotation><appInfo>
				    <meta.section type="apiinfo"> \t </meta.section>
				  </appInfo><documentation> &#9;Alpha  beta&#10;  Gamma&#9;delta &#13; </documentation></annotation>
				</schema>
				"""; //$NON-NLS-1$
		List<CleanupCase> cases= new ArrayList<>();
		for (boolean modernize : new boolean[] { false, true }) {
			for (boolean trim : new boolean[] { false, true }) {
				for (boolean indent : new boolean[] { false, true }) {
					PdeSchemaTarget target= modernize ? PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER : PdeSchemaTarget.PRESERVE;
					DocumentationPolicy policy= trim ? DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES : DocumentationPolicy.PRESERVE;
					XMLCleanupService service= new XMLCleanupService(target, policy);
					service.setEnableIndent(indent);
					IFile file= createFile("combined-" + cases.size() + ".exsd", source); //$NON-NLS-1$ //$NON-NLS-2$
					cases.add(new CleanupCase(file, service, modernize, trim, indent));
				}
			}
		}
		byte[][] firstResults= new byte[cases.size()][];
		for (int index : new int[] { 7, 0, 5, 2, 3, 6, 1, 4 }) {
			CleanupCase current= cases.get(index);
			assertTrue(current.service().processFile(current.file(), monitor));
			firstResults[index]= readBytes(current.file());
			String result= new String(firstResults[index], StandardCharsets.UTF_8);
			assertTrue(result.contains(current.modernize() ? "<appinfo>" : "<appInfo>"), result); //$NON-NLS-1$ //$NON-NLS-2$
			assertFalse(result.contains(current.modernize() ? "<appInfo>" : "<appinfo>"), result); //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(result.contains("<meta.section type=\"apiinfo\"/>"), result); //$NON-NLS-1$
			assertTrue(result.contains("<documentation>  Overview  stays\traw  </documentation>"), result); //$NON-NLS-1$
			assertTrue(result.contains(current.trim()
					? "<documentation>Alpha  beta\n  Gamma\tdelta</documentation>" //$NON-NLS-1$
					: "<documentation> \tAlpha  beta\n  Gamma\tdelta &#13; </documentation>"), result); //$NON-NLS-1$
			assertTrue(result.contains(current.indent() ? "\n\t<annotation>" : "<schema><annotation>"), result); //$NON-NLS-1$ //$NON-NLS-2$
		}
		for (int index : new int[] { 1, 6, 4, 3, 0, 7, 2, 5 }) {
			CleanupCase current= cases.get(index);
			assertFalse(current.service().processFile(current.file(), monitor));
			assertArrayEquals(firstResults[index], readBytes(current.file()));
		}
	}

	private record CleanupCase(IFile file, XMLCleanupService service, boolean modernize, boolean trim, boolean indent) {
	}

	@Test
	void concurrentCommandInvocationsUseIndependentImmutablePolicies() throws Exception {
		IFile explicit= createFile("concurrent-explicit.exsd"); //$NON-NLS-1$
		IFile normal= createFile("concurrent-normal.exsd"); //$NON-NLS-1$
		var explicitService= new PdeDocumentationBoundaryHandler().createXMLCleanupService();
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
		assertTrue(read(explicit).contains("<documentation>Plain reference text</documentation>")); //$NON-NLS-1$
		assertTrue(read(normal).contains("<documentation>  Plain reference text  </documentation>")); //$NON-NLS-1$
	}

	private IFile createFile(String name) throws Exception {
		return createFile(name, SOURCE);
	}

	private IFile createFile(String name, String source) throws Exception {
		IFile file= project.getFile(name);
		file.create(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), true, monitor);
		file.setCharset(StandardCharsets.UTF_8.name(), monitor);
		return file;
	}

	private static String read(IFile file) throws Exception {
		return new String(readBytes(file), StandardCharsets.UTF_8);
	}

	private static byte[] readBytes(IFile file) throws Exception {
		try (var content= file.getContents()) {
			return content.readAllBytes();
		}
	}
}
