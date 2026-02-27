/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;
import org.sandbox.jdt.triggerpattern.eclipse.HintMarkerReporter;

/**
 * Template-method base class for sandbox cleanups in {@code sandbox_triggerpattern}.
 *
 * <p>Subclasses only define:</p>
 * <ol>
 *   <li>{@link #getCleanUpKey()} — the {@code MYCleanUpConstants} key</li>
 *   <li>{@link #detect(CompilationUnit, CleanUpResult)} — the detection logic</li>
 *   <li>{@link #getFixLabel()} — the label for rewrite fixes (may return
 *       {@code null} for hint-only cleanups)</li>
 *   <li>{@link #getDescription()} — for {@code getStepDescriptions()}</li>
 *   <li>{@link #getPreview()} — preview text</li>
 * </ol>
 *
 * <p>The base class handles the entire {@code createFix()} pipeline:
 * detect → report markers for findings → build {@link ICleanUpFix} for
 * operations. This supports three modes transparently:</p>
 * <ul>
 *   <li><b>Rewriting</b> — {@code detect()} adds operations →
 *       returns {@code CompilationUnitRewriteOperationsFixCore}</li>
 *   <li><b>Hint-only</b> — {@code detect()} adds findings →
 *       creates markers, returns {@code null}</li>
 *   <li><b>Mixed</b> — {@code detect()} adds both →
 *       creates markers AND returns a fix</li>
 * </ul>
 *
 * <p><b>Note:</b> This base class is exclusively for experimental cleanups in
 * the {@code sandbox_triggerpattern} module. These cleanups are sandbox-specific
 * features (hint-only detection, DSL-driven rules) that do not have Eclipse JDT
 * counterparts and are not intended for upstream contribution. JDT-portable
 * cleanups in other modules (e.g., {@code sandbox_encoding_quickfix},
 * {@code sandbox_junit_cleanup}) must continue to extend {@code AbstractCleanUp}
 * directly to maintain 1:1 porting correspondence.</p>
 *
 * @since 1.3.7
 */
public abstract class AbstractSandboxCleanUpCore extends AbstractCleanUp {

	protected AbstractSandboxCleanUpCore(Map<String, String> options) {
		super(options);
	}

	protected AbstractSandboxCleanUpCore() {
	}

	/**
	 * The {@code MYCleanUpConstants} key that enables/disables this cleanup.
	 *
	 * @return the constant key string
	 */
	protected abstract String getCleanUpKey();

	/**
	 * Runs detection on the compilation unit. Implementations add rewrite
	 * operations and/or hint findings to {@code result}.
	 *
	 * @param cu     the compilation unit to analyze
	 * @param result collects operations and findings
	 */
	protected abstract void detect(CompilationUnit cu, CleanUpResult result);

	/**
	 * Label shown in the refactoring preview for rewrite operations.
	 * May return {@code null} for hint-only cleanups (never used).
	 *
	 * @return the fix label, or {@code null}
	 */
	protected abstract String getFixLabel();

	/**
	 * Description shown in the cleanup step list.
	 *
	 * @return the step description
	 */
	protected abstract String getDescription();

	/**
	 * Returns the preview text shown in the cleanup configuration dialog.
	 * Subclasses must override to provide cleanup-specific before/after examples.
	 *
	 * @return the preview text
	 */
	@Override
	public abstract String getPreview();

	// ── Template method implementations ──

	@Override
	public final CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(isEnabled(getCleanUpKey()), false, false, null);
	}

	@Override
	public final ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit cu = context.getAST();
		if (cu == null || !isEnabled(getCleanUpKey())) {
			return null;
		}

		CleanUpResult result = new CleanUpResult();
		detect(cu, result);

		// Report hint-only findings as problem markers
		if (result.hasFindings() && cu.getJavaElement() != null) {
			IResource resource = cu.getJavaElement().getResource();
			if (resource != null) {
				HintMarkerReporter.clearMarkers(resource);
				HintMarkerReporter.reportFindings(resource, result.getFindings());
			}
		}

		// Build fix for rewrite operations (if any)
		if (!result.hasOperations()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(
				getFixLabel(), cu,
				result.getOperations().toArray(new CompilationUnitRewriteOperation[0]));
	}

	@Override
	public final String[] getStepDescriptions() {
		if (isEnabled(getCleanUpKey())) {
			return new String[] { getDescription() };
		}
		return new String[0];
	}
}
