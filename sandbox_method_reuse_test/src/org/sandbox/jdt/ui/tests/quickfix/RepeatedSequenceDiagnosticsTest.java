/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.helper.RepeatedCodeSequenceExtractor;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** Compilation contracts reduced from complete pinned SWT and JDT source units. */
@SuppressWarnings("nls")
class RepeatedSequenceDiagnosticsTest {

	private IProject resource;
	private IJavaProject project;
	private boolean autoBuilding;
	private IVMInstall originalVm;
	private IVMInstall createdVm;

	@BeforeEach
	void createProject() throws Exception {
		JavaPlugin.getDefault().getPreferenceStore();
		var workspace= ResourcesPlugin.getWorkspace();
		var workspaceDescription= workspace.getDescription();
		autoBuilding= workspaceDescription.isAutoBuilding();
		workspaceDescription.setAutoBuilding(false);
		workspace.setDescription(workspaceDescription);
		originalVm= JavaRuntime.getDefaultVMInstall();
		if (originalVm == null) {
			var type= JavaRuntime.getVMInstallTypes()[0];
			createdVm= type.createVMInstall("repeated-sequence-diagnostics");
			createdVm.setName("Repeated sequence diagnostics");
			createdVm.setInstallLocation(Path.of(System.getProperty("java.home")).toFile());
			JavaRuntime.setDefaultVMInstall(createdVm, null);
		}
		resource= workspace.getRoot().getProject("RepeatedSequenceDiagnostics");
		resource.create(null);
		resource.open(null);
		var description= resource.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		resource.setDescription(description, null);
		resource.getFolder("src").create(true, true, null);
		project= JavaCore.create(resource);
		project.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
				JavaCore.newSourceEntry(resource.getFullPath().append("src")),
				JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath()) },
				resource.getFullPath().append("bin"), null);
		var options= project.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		options.put(JavaCore.COMPILER_RELEASE, JavaCore.ENABLED);
		project.setOptions(options);
	}

	@AfterEach
	void deleteProject() throws Exception {
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

	static Stream<Arguments> sources() {
		return Stream.of(
				Arguments.of("yield-with-two-live-outputs", """
						package test1;
						public class Sample {
							public static float run(int choice, float a, float b, float c) {
								float x, y;
								float z = switch (choice) {
									case 0 -> { x = a; y = b; yield c; }
									case 1 -> { x = b; y = c; yield a; }
									default -> { x = c; y = a; yield b; }
								};
								return x + y + z;
							}
						}
						""", false),
				Arguments.of("new-assignment-to-parameter", """
						package test1;
						public class Sample {
							public static int first(String[] values) {
								int total = 0;
								for (int i = 0; i < values.length; i++) {
									String text = values[i].trim();
									System.out.println(text);
									total += text.length();
								}
								return total;
							}
							public static int second(String[] values) {
								int total = 1;
								for (int i = 0; i < values.length; i++) {
									String text = values[i].trim();
									System.out.println(text);
									total += text.length();
								}
								return total * 2;
							}
						}
						""", false),
				Arguments.of("constructor-parameters-become-shadowing-method-parameters", """
						package test1;
						public class Sample {
							private String name;
							private int height;
							private int style;
							public Sample(String name, int height, int style) {
								setName(name);
								setHeight(height);
								setStyle(style);
							}
							public Sample(String name, int height, int style, boolean trace) {
								setName(name);
								setHeight(height);
								setStyle(style);
								if (trace) System.out.println(this.name);
							}
							private void setName(String value) { this.name = value; }
							private void setHeight(int value) { this.height = value; }
							private void setStyle(int value) { this.style = value; }
							public int size() { return this.height + this.style; }
						}
						""", false),
				Arguments.of("safe-sequence-still-extracted", """
						package test1;
						public class Sample {
							public void first(String value) {
								String text = value.trim();
								text = text.toLowerCase();
								System.out.println(text);
							}
							public void second(String input) {
								String text = input.trim();
								text = text.toLowerCase();
								System.out.println(text);
							}
						}
						""", true));
	}

	@ParameterizedTest(name= "{0}")
	@MethodSource("sources")
	void preservesCompleteUnitDiagnostics(String name, String source, boolean mustExtract) throws Exception {
		Map<String, String> preferences= Map.copyOf(project.getOptions(false));
		var unit= project.getPackageFragmentRoot(resource.getFolder("src")).createPackageFragment("test1", false, null)
				.createCompilationUnit("Sample.java", source, false, null);
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		var root= (CompilationUnit) parser.createAST(null);
		assertFalse(Arrays.stream(root.getProblems()).anyMatch(problem -> problem.isError()),
				Arrays.toString(root.getProblems()));
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		var fix= RepeatedCodeSequenceExtractor.createFix(unit, root, 3);
		if (mustExtract) {
			assertNotNull(fix, "Safe repeated sequences must remain supported");
		}
		if (fix != null) {
			var change= fix.createChange(null);
			var undo= change.perform(null);
			try {
				diagnostics.assertNoNewDiagnostics();
				assertTrue(unit.getSource().contains("extractedSequence"));
			} finally {
				assertNotNull(undo);
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
				change.dispose();
			}
		}
		assertEquals(source, unit.getSource());
		assertEquals(preferences, project.getOptions(false));
		diagnostics.assertRestored();
	}

	@Test
	void previewsTheCurrentPrivateWorkingCopyWithoutReadingTheOlderFile() throws Exception {
		String saved= "package test1; public class Sample {}\n";
		String source= """
				package test1;
				public class Sample {
					public void first(String value) {
						String text = value.trim();
						text = text.toLowerCase();
						System.out.println(text);
					}
					public void second(String input) {
						String text = input.trim();
						text = text.toLowerCase();
						System.out.println(text);
					}
				}
				""";
		var primary= project.getPackageFragmentRoot(resource.getFolder("src"))
				.createPackageFragment("test1", false, null).createCompilationUnit("Sample.java", saved, false, null);
		var unit= primary.getWorkingCopy(new WorkingCopyOwner() { }, null);
		try {
			unit.getBuffer().setContents(source);
			var parser= ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(unit);
			parser.setResolveBindings(true);
			var root= (CompilationUnit) parser.createAST(null);
			var diagnostics= CompilerDiagnosticAssertions.capture(unit);
			var fix= RepeatedCodeSequenceExtractor.createFix(unit, root, 3);
			assertNotNull(fix, "The valid extraction uses the private working-copy source");
			var change= fix.createChange(null);
			try {
				Document preview= new Document(source);
				change.getEdit().copy().apply(preview);
				assertTrue(preview.get().contains("extractedSequence"));
				assertEquals(source, unit.getSource(), "Creating a preview must not mutate the working copy");
				unit.getBuffer().setContents(preview.get());
				diagnostics.assertNoNewDiagnostics();
				unit.getBuffer().setContents(source);
				diagnostics.assertRestored();
				assertEquals(saved, primary.getSource(), "The underlying file is outside this preview");
			} finally {
				change.dispose();
			}
		} finally {
			unit.discardWorkingCopy();
		}
	}
}
