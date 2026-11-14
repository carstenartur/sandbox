/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

/**
 * Assertions for the complete lifecycle of one coordinated multi-file cleanup.
 *
 * <p>The helper executes one {@link CleanUpRefactoring}, verifies every affected
 * compilation unit and its Java warning and error set after apply, performs the returned undo
 * change, and finally proves that source and semantic state returned to the
 * original per-file baseline.</p>
 */
public final class MultiFileCleanUpLifecycleAssertions {

	private MultiFileCleanUpLifecycleAssertions() {
		// assertions only
	}

	/**
	 * Applies the configured cleanup profile atomically and verifies compilation and
	 * undo for the complete source set.
	 *
	 * @param compilationUnits all units participating in the coordinated cleanup
	 * @param expectedAfterApply expected source contents after the common change, in
	 *            the same order as {@code compilationUnits}
	 * @return the final-condition status of the cleanup refactoring
	 * @throws CoreException if Eclipse cannot create or execute either change
	 */
	public static RefactoringStatus assertApplyCompileAndUndo(ICompilationUnit[] compilationUnits,
			String[] expectedAfterApply) throws CoreException {
		return assertApplyCompileAndUndo(compilationUnits, compilationUnits, expectedAfterApply);
	}

	/**
	 * Applies a cleanup to an initial selection and verifies the complete set of
	 * units expected to be affected after coordinated scope expansion.
	 *
	 * @param selectedUnits compilation units explicitly added to the refactoring
	 * @param verifiedUnits units whose source and Java warnings and errors must be verified
	 * @param expectedAfterApply expected source contents for {@code verifiedUnits}
	 * @return the final-condition status of the cleanup refactoring
	 * @throws CoreException if Eclipse cannot create or execute either change
	 */
	public static RefactoringStatus assertApplyCompileAndUndo(ICompilationUnit[] selectedUnits,
			ICompilationUnit[] verifiedUnits, String[] expectedAfterApply) throws CoreException {
		assertEquals(verifiedUnits.length, expectedAfterApply.length,
				"Each verified compilation unit needs one expected post-apply source"); //$NON-NLS-1$
		Set<String> verifiedHandles= Arrays.stream(verifiedUnits)
				.map(ICompilationUnit::getPrimary)
				.map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());
		assertTrue(Arrays.stream(selectedUnits)
				.map(ICompilationUnit::getPrimary)
				.map(ICompilationUnit::getHandleIdentifier)
				.allMatch(verifiedHandles::contains),
				"Each selected compilation unit must be included in verifiedUnits"); //$NON-NLS-1$
		String[] originals= contents(verifiedUnits);
		var baselineProblems= CompilerDiagnosticAssertions.captureCompilerDiagnostics(verifiedUnits);

		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.setUseOptionsFromProfile(true);
		for (ICompilationUnit unit : selectedUnits) {
			refactoring.addCompilationUnit(unit);
		}
		for (ICleanUp cleanup : JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps()) {
			refactoring.addCleanUp(cleanup);
		}

		IUndoManager undoManager= RefactoringCore.getUndoManager();
		undoManager.flush();
		CreateChangeOperation create= new CreateChangeOperation(
				new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS),
				RefactoringStatus.FATAL);
		PerformChangeOperation apply= new PerformChangeOperation(create);
		apply.setUndoManager(undoManager, refactoring.getName());
		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		workspace.run(apply, new NullProgressMonitor());

		RefactoringStatus status= create.getConditionCheckingStatus();
		assertFalse(status.hasFatalError(), () -> "Cleanup final conditions failed: " + status); //$NON-NLS-1$
		assertTrue(apply.changeExecuted(), "The coordinated cleanup change was not executed"); //$NON-NLS-1$
		Change undo= apply.getUndoChange();
		assertNotNull(undo, "The coordinated cleanup did not create an undo change"); //$NON-NLS-1$

		assertPostApplyContents(verifiedUnits, expectedAfterApply);
		CompilerDiagnosticAssertions.assertNoNewCompilerDiagnostics(verifiedUnits, baselineProblems);

		PerformChangeOperation performUndo= new PerformChangeOperation(undo);
		workspace.run(performUndo, new NullProgressMonitor());
		assertTrue(performUndo.changeExecuted(), "The coordinated cleanup undo change was not executed"); //$NON-NLS-1$
		assertRestoredContents(verifiedUnits, originals);
		assertEquals(baselineProblems, CompilerDiagnosticAssertions.captureCompilerDiagnostics(verifiedUnits),
				"Undo must restore the original Java warning and error set"); //$NON-NLS-1$
		return status;
	}

	private static void assertPostApplyContents(ICompilationUnit[] units, String[] expected) throws CoreException {
		String[] actual= contents(units);
		for (int i= 0; i < units.length; i++) {
			String unitName= units[i].getElementName();
			assertEquals(normalizeWhitespace(expected[i]), normalizeWhitespace(actual[i]),
					() -> unitName + " has unexpected content after the coordinated cleanup"); //$NON-NLS-1$
		}
	}

	private static void assertRestoredContents(ICompilationUnit[] units, String[] originals) throws CoreException {
		String[] actual= contents(units);
		for (int i= 0; i < units.length; i++) {
			String unitName= units[i].getElementName();
			assertEquals(normalizeLineEndings(originals[i]), normalizeLineEndings(actual[i]),
					() -> unitName + " was not restored exactly by undo"); //$NON-NLS-1$
		}
	}

	private static String[] contents(ICompilationUnit[] units) throws CoreException {
		String[] result= new String[units.length];
		for (int i= 0; i < units.length; i++) {
			result[i]= units[i].getBuffer().getContents();
		}
		return result;
	}

	private static String normalizeWhitespace(String source) {
		return Arrays.stream(normalizeLineEndings(source).split("\n", -1)) //$NON-NLS-1$
				.map(MultiFileCleanUpLifecycleAssertions::normalizeIndentation)
				.map(String::stripTrailing)
				.collect(Collectors.joining("\n")) //$NON-NLS-1$
				.replaceAll("\n{3,}", "\n\n") //$NON-NLS-1$ //$NON-NLS-2$
				.strip();
	}

	private static String normalizeIndentation(String line) {
		int endOfIndent= 0;
		while (endOfIndent < line.length()
				&& (line.charAt(endOfIndent) == ' ' || line.charAt(endOfIndent) == '\t')) {
			endOfIndent++;
		}
		if (endOfIndent == 0) {
			return line;
		}
		String leading= line.substring(0, endOfIndent).replace("\t", "    "); //$NON-NLS-1$ //$NON-NLS-2$
		return leading + line.substring(endOfIndent);
	}

	private static String normalizeLineEndings(String source) {
		return source.replace("\r\n", "\n").replace("\r", "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}
}
