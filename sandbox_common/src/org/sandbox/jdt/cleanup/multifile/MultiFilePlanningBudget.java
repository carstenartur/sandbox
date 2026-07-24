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
package org.sandbox.jdt.cleanup.multifile;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/** Measures source scope and applies warning/hard limits before AST creation. */
public final class MultiFilePlanningBudget {

	/** Result of measuring one source scope. */
	public record Assessment(MultiFilePlanningMetrics metrics, RefactoringStatus status) {
		public Assessment {
			if (metrics == null || status == null) {
				throw new NullPointerException();
			}
		}

		/** @return whether planning may safely continue */
		public boolean mayProceed() {
			return !status.hasFatalError();
		}
	}

	private MultiFilePlanningBudget() {
	}

	/**
	 * Measures distinct primary compilation units using their current working-copy
	 * source, including unsaved edits. UTF-8 byte length is counted without
	 * allocating a second byte array. Hard thresholds abort measurement as soon as
	 * they are exceeded.
	 *
	 * @param units planned source units
	 * @param limits effective limits
	 * @param monitor progress monitor, may be {@code null}
	 * @return metrics and warning/fatal diagnostics
	 * @throws JavaModelException if current source cannot be read
	 */
	public static Assessment assess(ICompilationUnit[] units, MultiFilePlanningLimits limits,
			IProgressMonitor monitor) throws JavaModelException {
		if (units == null || limits == null) {
			throw new NullPointerException();
		}
		RefactoringStatus status= new RefactoringStatus();
		Set<String> measuredHandles= new HashSet<>();
		int unitCount= 0;
		long sourceBytes= 0;
		for (ICompilationUnit unit : units) {
			checkCanceled(monitor);
			if (unit == null || !unit.exists()) {
				status.addFatalError("The coordinated cleanup scope contains a missing compilation unit."); //$NON-NLS-1$
				break;
			}
			ICompilationUnit primary= unit.getPrimary();
			if (!measuredHandles.add(primary.getHandleIdentifier())) {
				continue;
			}
			unitCount++;
			if (unitCount > limits.hardCompilationUnits()) {
				status.addFatalError("Coordinated cleanup planning aborted: " + unitCount //$NON-NLS-1$
						+ " source units exceed the hard limit of " + limits.hardCompilationUnits() + '.'); //$NON-NLS-1$
				break;
			}
			String source= primary.getSource();
			if (source == null) {
				status.addFatalError("The current source of " + primary.getElementName() //$NON-NLS-1$
						+ " is unavailable for coordinated cleanup planning."); //$NON-NLS-1$
				break;
			}
			sourceBytes= saturatedAdd(sourceBytes, utf8Length(source));
			if (sourceBytes > limits.hardSourceBytes()) {
				status.addFatalError("Coordinated cleanup planning aborted: " + sourceBytes //$NON-NLS-1$
						+ " source bytes exceed the hard limit of " + limits.hardSourceBytes() + '.'); //$NON-NLS-1$
				break;
			}
		}
		MultiFilePlanningMetrics metrics= MultiFilePlanningMetrics.scope(unitCount, sourceBytes);
		if (status.hasFatalError()) {
			return new Assessment(metrics, status);
		}
		if (unitCount > limits.warningCompilationUnits() || sourceBytes > limits.warningSourceBytes()) {
			status.addWarning("Coordinated cleanup planning will analyse " + unitCount + " source units (" //$NON-NLS-1$ //$NON-NLS-2$
					+ sourceBytes + " UTF-8 bytes). Warning limits are " //$NON-NLS-1$
					+ limits.warningCompilationUnits() + " units and " + limits.warningSourceBytes() + " bytes."); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return new Assessment(metrics, status);
	}

	static long utf8Length(CharSequence source) {
		long length= 0;
		for (int index= 0; index < source.length(); index++) {
			char character= source.charAt(index);
			if (character <= 0x7f) {
				length= saturatedAdd(length, 1);
			} else if (character <= 0x7ff) {
				length= saturatedAdd(length, 2);
			} else if (Character.isHighSurrogate(character) && index + 1 < source.length()
					&& Character.isLowSurrogate(source.charAt(index + 1))) {
				length= saturatedAdd(length, 4);
				index++;
			} else if (Character.isSurrogate(character)) {
				length= saturatedAdd(length, 1);
			} else {
				length= saturatedAdd(length, 3);
			}
		}
		return length;
	}

	private static long saturatedAdd(long left, long right) {
		return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
	}

	/** Throws immediately when the user or caller cancels planning. */
	public static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
