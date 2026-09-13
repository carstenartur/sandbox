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
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.InstanceScope;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPostSaveListener;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.ISourceViewerExtension2;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.ui.PlatformUI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.sandbox.jdt.internal.corext.fix.IntToEnumCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.IntToEnumCleanUpCore;

/** Deterministic source-revision regressions; no reconciler timing or sleeps. */
@SuppressWarnings("restriction")
public class IntToEnumSourceSnapshotTest {

	enum Shape { IF_ELSE, SWITCH }

	private IProject project;
	private boolean projectCreated;
	private IJavaProject javaProject;
	private IPackageFragment pack;
	private ICompilationUnit unit;
	private IntToEnumCleanUpCore cleanup;

	@BeforeEach
	void setUp() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot().getProject("IntToEnumSourceSnapshot"); //$NON-NLS-1$
		assertFalse(project.exists(), "Never overwrite an existing project"); //$NON-NLS-1$
		project.create(null);
		projectCreated= true;
		project.open(null);
		var description= project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);
		javaProject= JavaCore.create(project);
		var source= project.getFolder("src"); //$NON-NLS-1$
		source.create(true, true, null);
		javaProject.setRawClasspath(new IClasspathEntry[] {
				JavaCore.newSourceEntry(source.getFullPath()),
				JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER")) //$NON-NLS-1$
		}, project.getFullPath().append("bin"), null); //$NON-NLS-1$
		var options= javaProject.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		javaProject.setOptions(options);
		pack= javaProject.getPackageFragmentRoot(source).createPackageFragment("test", false, null); //$NON-NLS-1$
		cleanup= new IntToEnumCleanUpCore(Map.of(MYCleanUpConstants.INT_TO_ENUM_CLEANUP, CleanUpOptions.TRUE,
				IntToEnumCleanUpOptions.PROJECT_WIDE, CleanUpOptions.FALSE));
	}

	@AfterEach
	void tearDown() throws Exception {
		try {
			if (cleanup != null) {
				cleanup.checkPostConditions(new NullProgressMonitor());
			}
		} finally {
			if (projectCreated && project != null && project.exists()) {
				project.delete(true, true, null);
			}
		}
	}

	@ParameterizedTest
	@EnumSource(Shape.class)
	void currentSourceStillMigrates(Shape shape) throws Exception {
		CompilationUnit ast= prepare(shape);
		assertMigration(ast, unit.getSource());
	}

	@ParameterizedTest
	@EnumSource(Shape.class)
	void insertionBeforeMigratedNodesDoesNotReuseOldOffsets(Shape shape) throws Exception {
		CompilationUnit old= prepare(shape);
		String current= unit.getSource().replace("void run()", "// inserted before save\n    void run()"); //$NON-NLS-1$ //$NON-NLS-2$
		unit.getBuffer().setContents(current);
		assertMigration(old, current);
	}

	@ParameterizedTest
	@EnumSource(Shape.class)
	void deletionBeforeMigratedNodesDoesNotReuseOldOffsets(Shape shape) throws Exception {
		CompilationUnit old= prepare(shape);
		String current= unit.getSource().replace("    // existing comment\n", ""); //$NON-NLS-1$ //$NON-NLS-2$
		unit.getBuffer().setContents(current);
		assertMigration(old, current);
	}

	@ParameterizedTest
	@EnumSource(Shape.class)
	void equalLengthEditMustBeReanalysedRatherThanReplacedFromOldBindings(Shape shape) throws Exception {
		CompilationUnit old= prepare(shape);
		String original= unit.getSource();
		String current= changedArgument(original);
		assertEquals(original.length(), current.length());
		unit.getBuffer().setContents(current);
		assertCompiles();
		assertNull(fix(old), "The changed use must reject the candidate, even at unchanged offsets"); //$NON-NLS-1$
		assertEquals(current, unit.getSource());
	}

	@ParameterizedTest
	@EnumSource(Shape.class)
	void changingSourceAfterFixCreationIsRejectedBeforeEditGeneration(Shape shape) throws Exception {
		CompilationUnit ast= prepare(shape);
		ICleanUpFix fix= fix(ast);
		assertNotNull(fix);
		String current= changedArgument(unit.getSource());
		unit.getBuffer().setContents(current);
		assertThrows(CoreException.class, () -> fix.createChange(new NullProgressMonitor()));
		assertEquals(current, unit.getSource(), "Rejection must not alter the newer source"); //$NON-NLS-1$
		assertCompiles();
	}

	@ParameterizedTest
	@EnumSource(Shape.class)
	void saveParticipantReanalysesTheCachedAstWithoutTouchingAnotherDirtyEditor(Shape shape) throws Exception {
		prepare(shape);
		String otherSource= "package test; class Other {}\n"; //$NON-NLS-1$
		ICompilationUnit other= pack.createCompilationUnit("Other.java", otherSource, false, null); //$NON-NLS-1$
		var page= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		var otherEditor= JavaUI.openInEditor(other);
		var editor= JavaUI.openInEditor(unit);
		try {
			// Stop this fixture's reconciler before installing an intentionally stale cache.
			// The ordinary editor-save integration test keeps reconciliation enabled.
			((ISourceViewerExtension2) ((JavaEditor) editor).getViewer()).unconfigure();
			String otherDirty= otherSource + "// unsaved\n"; //$NON-NLS-1$
			other.getBuffer().setContents(otherDirty);
			CompilationUnit old= parse();
			String current= unit.getSource().replace("void run()", "// inserted before save\n    void run()"); //$NON-NLS-1$ //$NON-NLS-2$
			unit.getBuffer().setContents(current);
			var options= new HashMap<>(CleanUpPreferenceUtil.loadSaveParticipantOptions(InstanceScope.INSTANCE));
			options.replaceAll((key, value) -> CleanUpOptions.FALSE);
			options.put(MYCleanUpConstants.INT_TO_ENUM_CLEANUP, CleanUpOptions.TRUE);
			options.put(IntToEnumCleanUpOptions.PROJECT_WIDE, CleanUpOptions.FALSE);
			options.put(CleanUpConstants.CLEANUP_ON_SAVE_ADDITIONAL_OPTIONS, CleanUpOptions.TRUE);
			CleanUpPreferenceUtil.saveSaveParticipantOptions(new ProjectScope(project), options);

			// Reproduce the exact handoff explicitly, independent of background reconciliation timing.
			CoreASTProvider provider= CoreASTProvider.getInstance();
			provider.setActiveJavaElement(unit);
			provider.cache(old, unit);
			assertSame(old, SharedASTProviderCore.getAST(unit, SharedASTProviderCore.WAIT_NO, null));
			new CleanUpPostSaveListener().saved(unit, null, new NullProgressMonitor());
			editor.doSave(new NullProgressMonitor());
			String saved= Files.readString(unit.getResource().getLocation().toFile().toPath(), StandardCharsets.UTF_8);
			assertEquals(saved, unit.getSource());
			assertMigratedSource(saved);
			assertTrue(saved.contains("// inserted before save"), saved); //$NON-NLS-1$
			assertCompiles();
			assertEquals(otherDirty, other.getSource());
			assertTrue(otherEditor.isDirty());
			assertEquals(otherSource, Files.readString(other.getResource().getLocation().toFile().toPath(), StandardCharsets.UTF_8));
		} finally {
			CoreASTProvider.getInstance().disposeAST();
			page.closeEditor(editor, false);
			page.closeEditor(otherEditor, false);
		}
	}

	private CompilationUnit prepare(Shape shape) throws Exception {
		String body= shape == Shape.IF_ELSE ? """
				if (status == STATUS_PENDING) { System.out.println("pending"); }
				else if (status == STATUS_APPROVED) { System.out.println("approved"); }
				""" : """
				switch (status) {
				    case STATUS_PENDING: System.out.println("pending"); break;
				    case STATUS_APPROVED: System.out.println("approved"); break;
				}
				""";
		String source= """
				package test;
				public class LocalState {
				    private static final int STATUS_PENDING = 0;
				    private static final int STATUS_APPROVED = 1;
				    // existing comment
				    void run() { process(STATUS_PENDING); }
				    private void process(int status) {
				        %s
				    }
				}
				""".formatted(body);
		if (shape == Shape.SWITCH) {
			source= source.replace("process(STATUS_PENDING);", "/* no external caller */"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		unit= pack.createCompilationUnit("LocalState.java", source, false, null); //$NON-NLS-1$
		assertCompiles();
		return parse();
	}

	private ICleanUpFix fix(CompilationUnit ast) throws Exception {
		assertFalse(cleanup.checkPreConditions(javaProject, new ICompilationUnit[] { unit }, new NullProgressMonitor()).hasFatalError());
		return cleanup.createFix(new CleanUpContext(unit, ast));
	}

	private void assertMigration(CompilationUnit ast, String current) throws Exception {
		ICleanUpFix fix= fix(ast);
		assertNotNull(fix);
		var change= fix.createChange(new NullProgressMonitor());
		try {
			Document document= new Document(current);
			change.getEdit().copy().apply(document);
			String migrated= document.get();
			unit.getBuffer().setContents(migrated);
			assertCompiles();
			assertMigratedSource(migrated);
			assertEquals(current.contains("// existing comment"), migrated.contains("// existing comment")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(current.contains("// inserted before save"), migrated.contains("// inserted before save")); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			change.dispose();
		}
	}

	private static String changedArgument(String source) {
		return source.contains("switch (status)") //$NON-NLS-1$
				? source.replace("/* no external caller */", "int x = STATUS_PENDING; ") //$NON-NLS-1$ //$NON-NLS-2$
				: source.replace("process(STATUS_PENDING)", "process(0             )"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void assertMigratedSource(String source) {
		assertTrue(source.contains("process(Status status)"), source); //$NON-NLS-1$
		assertTrue(source.contains("switch (status)") //$NON-NLS-1$
				? source.contains("case PENDING:") : source.contains("process(Status.PENDING)"), source); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private CompilationUnit parse() {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private void assertCompiles() {
		assertEquals(List.of(), Arrays.stream(parse().getProblems()).filter(IProblem::isError)
				.map(IProblem::getMessage).toList());
	}
}
