/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A reporting limit must neither invent nor hide a cleanup diagnostic regression. */
@SuppressWarnings("nls")
class CompilerDiagnosticLimitTest {

	private IProject resource;
	private IJavaProject project;
	private boolean autoBuilding;
	private IVMInstall originalVm;
	private IVMInstall createdVm;

	@BeforeEach
	void createProject() throws Exception {
		var workspace= ResourcesPlugin.getWorkspace();
		var description= workspace.getDescription();
		autoBuilding= description.isAutoBuilding();
		description.setAutoBuilding(false);
		workspace.setDescription(description);
		originalVm= JavaRuntime.getDefaultVMInstall();
		if (originalVm == null) {
			createdVm= JavaRuntime.getVMInstallTypes()[0].createVMInstall("diagnostic-limit-regression");
			createdVm.setName("Diagnostic limit regression");
			createdVm.setInstallLocation(Path.of(System.getProperty("java.home")).toFile());
			JavaRuntime.setDefaultVMInstall(createdVm, null);
		}
		resource= workspace.getRoot().getProject("CompilerDiagnosticLimit");
		resource.create(null);
		resource.open(null);
		var projectDescription= resource.getDescription();
		projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID });
		resource.setDescription(projectDescription, null);
		resource.getFolder("src").create(true, true, null);
		project= JavaCore.create(resource);
		project.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
				JavaCore.newSourceEntry(resource.getFullPath().append("src")),
				JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath()) },
				resource.getFullPath().append("bin"), null);
		var options= project.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "1");
		project.setOptions(options);
	}

	@AfterEach
	void restoreWorkspace() throws Exception {
		try {
			if (resource != null && resource.exists()) resource.delete(true, true, null);
		} finally {
			if (createdVm != null) {
				JavaRuntime.setDefaultVMInstall(originalVm, null);
				createdVm.getVMInstallType().disposeVMInstall(createdVm.getId());
			}
			var workspace= ResourcesPlugin.getWorkspace();
			var description= workspace.getDescription();
			description.setAutoBuilding(autoBuilding);
			workspace.setDescription(description);
		}
	}

	@Test
	void removingAnEarlyWarningDoesNotExposeAPreviouslyTruncatedWarning() throws Exception {
		String original= source("int first= 1; int second= 2;");
		ICompilationUnit unit= unit(original);
		var baseline= CompilerDiagnosticAssertions.capture(unit);
		unit.getBuffer().setContents(source("int second= 2;"));
		assertDoesNotThrow(() -> {
			baseline.assertNoNewDiagnostics();
		});
		unit.getBuffer().setContents(original);
		baseline.assertRestored();
		assertEquals("1", project.getOption(JavaCore.COMPILER_PB_MAX_PER_UNIT, true));
	}

	@Test
	void aNewWarningBeyondTheReportingLimitIsStillRejected() throws Exception {
		ICompilationUnit unit= unit(source("int first= 1; int second= 2;"));
		var baseline= CompilerDiagnosticAssertions.capture(unit);
		unit.getBuffer().setContents(source("int first= 1; int second= 2; int third= 3;"));
		assertThrows(AssertionError.class, baseline::assertNoNewDiagnostics);
		assertEquals("1", project.getOption(JavaCore.COMPILER_PB_MAX_PER_UNIT, true));
	}

	@Test
	void anAdditionalErrorBeyondTheReportingLimitIsStillRejected() throws Exception {
		ICompilationUnit unit= unit(source("missing(); missing();"));
		var baseline= CompilerDiagnosticAssertions.capture(unit);
		unit.getBuffer().setContents(source("missing(); missing(); missing();"));
		assertThrows(AssertionError.class, baseline::assertNoNewDiagnostics);
		assertEquals("1", project.getOption(JavaCore.COMPILER_PB_MAX_PER_UNIT, true));
	}

	private ICompilationUnit unit(String source) throws Exception {
		return project.getPackageFragmentRoot(resource.getFolder("src"))
				.createPackageFragment("test", false, null).createCompilationUnit("Sample.java", source, false, null);
	}

	private static String source(String statements) {
		return "package test;\nclass Sample {\nstatic void run() {\n"
				+ statements.replace("; ", ";\n") + "\n}\n}\n";
	}
}
