/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.APPEND_ARRAY_TO_LIST;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.UNIQUE_SEQUENCE_TO_SET;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpRule;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpRule.ExecutionMode;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.ContainerCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ContainerCleanUpTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void appliesBothExecutableContainerRulesInOneCleanupFix() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.ArrayList;
			import java.util.Arrays;
			import java.util.List;
			class Sample {
				void collect(String value) {
					String[] appended = new String[0];
					appended = Arrays.copyOf(appended, appended.length + 1);
					appended[appended.length - 1] = value;
					System.out.println(appended.length);
					List<String> unique = new ArrayList<>();
					if (!unique.contains(value)) {
						unique.add(value);
					}
					for (String current : unique) {
						System.out.println(current);
					}
				}
			}
			""");
		ContainerCleanUpCore cleanup= new ContainerCleanUpCore(Map.of(
				CLEANUP, CleanUpOptions.TRUE,
				APPEND_ARRAY_TO_LIST, CleanUpOptions.TRUE,
				UNIQUE_SEQUENCE_TO_SET, CleanUpOptions.TRUE));

		ICleanUpFix fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix);
		fix.createChange(null).perform(null);

		String transformed= unit.getSource();
		assertTrue(transformed.contains("List<String> appended")); //$NON-NLS-1$
		assertTrue(transformed.contains("appended.add(value)")); //$NON-NLS-1$
		assertTrue(transformed.contains("appended.size()")); //$NON-NLS-1$
		assertFalse(transformed.contains("Arrays.copyOf(appended")); //$NON-NLS-1$
		assertTrue(transformed.contains("Set<String> unique")); //$NON-NLS-1$
		assertTrue(transformed.contains("new LinkedHashSet<>()")); //$NON-NLS-1$
		assertFalse(transformed.contains("unique.contains(value)")); //$NON-NLS-1$
	}

	@Test
	void disabledMasterProducesNoFix() throws CoreException {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
				}
			}
			""");
		ContainerCleanUpCore cleanup= new ContainerCleanUpCore(Map.of(
				CLEANUP, CleanUpOptions.FALSE,
				APPEND_ARRAY_TO_LIST, CleanUpOptions.TRUE));
		assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
	}

	@Test
	void cleanupExtensionIsRegisteredWithoutSaveActionConfiguration() {
		var registry= Platform.getExtensionRegistry();
		assertNotNull(registry);
		var elements= registry.getConfigurationElementsFor(
				"org.eclipse.jdt.ui.cleanUps"); //$NON-NLS-1$
		assertTrue(Arrays.stream(elements).anyMatch(element ->
				"cleanUp".equals(element.getName()) //$NON-NLS-1$
						&& "org.sandbox.jdt.ui.cleanup.container_contracts".equals( //$NON-NLS-1$
								element.getAttribute("id")))); //$NON-NLS-1$
		assertFalse(Arrays.stream(elements).anyMatch(element ->
				"org.sandbox.jdt.container.cleanup.internal.ui.preferences.cleanup.SandboxCodeTabPage".equals( //$NON-NLS-1$
							element.getAttribute("class")) //$NON-NLS-1$
						&& "saveAction".equals(element.getAttribute("cleanUpKind")))); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void projectClosedRuleIsRegisteredButNotExposedAsLocalCleanup() {
		ContainerCleanUpRule projectRule=
				ContainerCleanUpRule.CLOSED_SOURCE_PARAMETER_MIGRATION;
		assertEquals(ExecutionMode.PROJECT_CLOSED, projectRule.executionMode());
		assertFalse(projectRule.isLocalCleanUp());
		assertEquals(
				List.of(
						ContainerCleanUpRule.APPEND_ARRAY_TO_LIST,
						ContainerCleanUpRule.UNIQUE_SEQUENCE_TO_ORDERED_SET),
				ContainerCleanUpRule.localCleanUps());
		assertTrue(Arrays.stream(ContainerCleanUpRule.values())
				.allMatch(rule -> !rule.helpContextId().isBlank()));
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit(
				"Sample.java", source, true, null); //$NON-NLS-1$
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(context.getJavaProject());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
