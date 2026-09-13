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
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.text.edits.ReplaceEdit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the source guard with real compilation units and disposable LTK changes. */
public class IntToEnumSourceSnapshotGuardTest {

	private static final String ORIGINAL= "class State { int value = 0; }\n"; //$NON-NLS-1$
	private static final String CURRENT= "class State { int value = 1; }\n"; //$NON-NLS-1$
	private IProject project;
	private boolean projectCreated;
	private ICompilationUnit unit;

	@BeforeEach
	void setUp() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot().getProject("IntToEnumSourceGuard"); //$NON-NLS-1$
		assertFalse(project.exists());
		project.create(null);
		projectCreated= true;
		project.open(null);
		var description= project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);
		var javaProject= JavaCore.create(project);
		javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(project.getFullPath()) }, null);
		unit= javaProject.getPackageFragmentRoot(project).getPackageFragment("") //$NON-NLS-1$
				.createCompilationUnit("State.java", ORIGINAL, false, null); //$NON-NLS-1$
	}

	@AfterEach
	void tearDown() throws Exception {
		if (projectCreated) {
			project.delete(true, true, null);
		}
	}

	@Test
	void mutationDuringGenerationRejectsAndDisposesTheGeneratedChange() throws Exception {
		TrackingChange change= new TrackingChange(unit);
		int[] generated= { 0 };
		ICleanUpFix delegate= monitor -> {
			generated[0]++;
			change.setEdit(new ReplaceEdit(0, ORIGINAL.length(), "class Rewritten {}\n")); //$NON-NLS-1$
			unit.getBuffer().setContents(CURRENT);
			return change;
		};
		ICleanUpFix guarded= IntToEnumCleanUpCore.withSourceSnapshot(unit, ORIGINAL, delegate);
		CoreException failure= assertThrows(CoreException.class, () -> guarded.createChange(new NullProgressMonitor()));
		assertEquals(MultiFixMessages.IntToEnumCleanUp_source_changed, failure.getStatus().getMessage());
		assertEquals(1, generated[0], "The delegate must run before the post-generation rejection"); //$NON-NLS-1$
		assertEquals(1, change.disposed);
		assertEquals(0, change.performed);
		assertEquals(CURRENT, unit.getSource());
		assertEquals(ORIGINAL, Files.readString(unit.getResource().getLocation().toFile().toPath(), StandardCharsets.UTF_8));
	}

	@Test
	void unchangedSourceReturnsTheOriginalChangeWithoutDisposingIt() throws Exception {
		TrackingChange change= new TrackingChange(unit);
		ICleanUpFix guarded= IntToEnumCleanUpCore.withSourceSnapshot(unit, ORIGINAL, monitor -> change);
		try {
			assertSame(change, guarded.createChange(new NullProgressMonitor()));
			assertEquals(0, change.disposed);
			assertEquals(0, change.performed);
			assertEquals(ORIGINAL, unit.getSource());
		} finally {
			change.dispose();
		}
	}

	private static final class TrackingChange extends CompilationUnitChange {
		private int disposed;
		private int performed;

		TrackingChange(ICompilationUnit unit) {
			super("Source guard regression", unit); //$NON-NLS-1$
		}

		@Override
		public void dispose() {
			disposed++;
			super.dispose();
		}

		@Override
		public Change perform(IProgressMonitor monitor) throws CoreException {
			performed++;
			return super.perform(monitor);
		}
	}
}
