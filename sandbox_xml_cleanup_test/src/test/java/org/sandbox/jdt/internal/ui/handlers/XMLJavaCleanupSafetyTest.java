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
package org.sandbox.jdt.internal.ui.handlers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.XML_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.XML_CLEANUP_INDENT;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.framework.FrameworkUtil;
import org.sandbox.jdt.internal.corext.fix.helper.XMLCleanupService;
import org.sandbox.jdt.internal.ui.fix.XMLCleanUp;
import org.sandbox.jdt.internal.ui.fix.XMLCleanUpCore;

/** Ensures Java cleanup calculation cannot apply project XML resource edits. */
class XMLJavaCleanupSafetyTest {

	private static final String PLUGIN_ID= "sandbox_xml_cleanup"; //$NON-NLS-1$
	private static final String COMMAND_ID= "org.sandbox.jdt.xml.cleanup.command"; //$NON-NLS-1$
	private static final String JAVA_SOURCE= "package fixture; class Example {}"; //$NON-NLS-1$
	private static final String XML_SOURCE= """
			<?xml version="1.0" encoding="UTF-8"?>
			<plugin>
			    <extension point="example.point">
			        <example id="value"/>
			    </extension>
			</plugin>
			""";
	private static final String COMPACT_BODY=
			"<plugin><extension point=\"example.point\"><example id=\"value\"/></extension></plugin>"; //$NON-NLS-1$

	private final NullProgressMonitor monitor= new NullProgressMonitor();
	private IProject project;
	private ICompilationUnit unit;
	private IFile xmlFile;
	private CleanUpContext context;

	@BeforeEach
	void createJavaProjectWithXml() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot()
				.getProject("XmlJavaCleanupSafety-" + System.nanoTime()); //$NON-NLS-1$
		project.create(monitor);
		project.open(monitor);
		IProjectDescription description= project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, monitor);
		IFolder sourceFolder= project.getFolder("src"); //$NON-NLS-1$
		sourceFolder.create(true, true, monitor);
		IFolder outputFolder= project.getFolder("bin"); //$NON-NLS-1$
		outputFolder.create(true, true, monitor);
		IJavaProject javaProject= JavaCore.create(project);
		javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(sourceFolder.getFullPath()) },
				outputFolder.getFullPath(), monitor);
		unit= javaProject.getPackageFragmentRoot(sourceFolder)
				.createPackageFragment("fixture", true, monitor) //$NON-NLS-1$
				.createCompilationUnit("Example.java", JAVA_SOURCE, true, monitor); //$NON-NLS-1$
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		CompilationUnit ast= (CompilationUnit) parser.createAST(monitor);
		assertEquals(unit, ast.getJavaElement());
		context= new CleanUpContext(unit, ast);
		xmlFile= project.getFile("plugin.xml"); //$NON-NLS-1$
		xmlFile.create(new ByteArrayInputStream(XML_SOURCE.getBytes(StandardCharsets.UTF_8)), true, monitor);
		xmlFile.setCharset(StandardCharsets.UTF_8.name(), monitor);
	}

	@AfterEach
	void deleteProject() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, monitor);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void legacyCoreIsInertEvenWhenStoredOptionsAreEnabled(boolean indent) throws Exception {
		XMLCleanUpCore cleanup= new XMLCleanUpCore(enabledOptions(indent));
		byte[] originalBytes= readBytes();
		long originalStamp= xmlFile.getModificationStamp();

		assertAll(
				() -> assertNull(cleanup.createFix(context)),
				() -> assertFalse(cleanup.getRequirements().requiresAST()),
				() -> assertArrayEquals(originalBytes, readBytes()),
				() -> assertEquals(originalStamp, xmlFile.getModificationStamp()),
				() -> assertEquals(JAVA_SOURCE, unit.getSource()));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void javaCleanupCalculationDoesNotWriteXmlOrCreateASyntheticJavaEdit(boolean indent) throws Exception {
		ICleanUp cleanup= new XMLCleanUp(enabledOptions(indent));
		byte[] originalBytes= readBytes();
		long originalStamp= xmlFile.getModificationStamp();

		// This is the same calculation used by refactoring preview and Java save actions.
		// Keep the byte/stamp assertions after calculation even if it throws, so the
		// baseline failure also exposes writes that occurred before the null-change error.
		assertAll(
				() -> assertNull(assertDoesNotThrow(() -> CleanUpRefactoring.calculateChange(context,
						new ICleanUp[] { cleanup }, new ArrayList<>(), null))),
				() -> assertArrayEquals(originalBytes, readBytes()),
				() -> assertEquals(originalStamp, xmlFile.getModificationStamp()),
				() -> assertEquals(JAVA_SOURCE, unit.getSource()));
	}

	@Test
	void registryOffersTheStandaloneCommandWithoutJavaCleanupOrSaveActionContributions() {
		assertNotNull(Platform.getBundle(PLUGIN_ID));
		assertEquals(PLUGIN_ID, FrameworkUtil.getBundle(XMLCleanupHandler.class).getSymbolicName(),
				"Exercise the installed product bundle, not an unregistered test copy"); //$NON-NLS-1$
		var registry= Platform.getExtensionRegistry();
		assertTrue(Arrays.stream(registry.getConfigurationElementsFor("org.eclipse.ui.commands")) //$NON-NLS-1$
				.anyMatch(element -> isOwnContribution(element) && "command".equals(element.getName()) //$NON-NLS-1$
						&& COMMAND_ID.equals(element.getAttribute("id")))); //$NON-NLS-1$
		assertTrue(Arrays.stream(registry.getConfigurationElementsFor("org.eclipse.ui.handlers")) //$NON-NLS-1$
				.anyMatch(element -> isOwnContribution(element)
						&& COMMAND_ID.equals(element.getAttribute("commandId")))); //$NON-NLS-1$
		assertTrue(Arrays.stream(registry.getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps")) //$NON-NLS-1$
				.noneMatch(XMLJavaCleanupSafetyTest::isOwnContribution),
				"Do not expose XML resource edits through Java cleanup or save-action profiles"); //$NON-NLS-1$
	}

	@Test
	void standaloneCommandServiceStillFormatsTheSelectedXmlResource() throws Exception {
		var service= XMLCleanupService.compactFormatting();

		assertTrue(service.processFile(xmlFile, monitor));
		assertTrue(new String(readBytes(), StandardCharsets.UTF_8).endsWith(COMPACT_BODY));
		assertEquals(JAVA_SOURCE, unit.getSource());
		assertFalse(service.processFile(xmlFile, monitor), "A repeated explicit command is a no-op"); //$NON-NLS-1$
	}

	private static Map<String, String> enabledOptions(boolean indent) {
		return Map.of(XML_CLEANUP, CleanUpOptions.TRUE, XML_CLEANUP_INDENT, Boolean.toString(indent));
	}

	private static boolean isOwnContribution(IConfigurationElement element) {
		return PLUGIN_ID.equals(element.getContributor().getName());
	}

	private byte[] readBytes() throws Exception {
		try (var contents= xmlFile.getContents()) {
			return contents.readAllBytes();
		}
	}
}
