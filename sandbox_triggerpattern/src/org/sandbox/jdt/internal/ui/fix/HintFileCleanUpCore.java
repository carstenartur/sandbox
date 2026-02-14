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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.HINTFILE_CLEANUP;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.HintFileFixCore;

/**
 * CleanUp that applies transformation rules from {@code .sandbox-hint} files.
 *
 * <p>This is the bridge between the {@code .sandbox-hint} DSL file format and the
 * Eclipse CleanUp framework. It reads all registered hint files from
 * {@link org.sandbox.jdt.triggerpattern.internal.HintFileRegistry HintFileRegistry}
 * and applies their transformation rules as cleanup operations.</p>
 *
 * <p>This enables users to define cleanup rules declaratively in
 * {@code .sandbox-hint} files without writing Java code.</p>
 *
 * @since 1.3.5
 */
public class HintFileCleanUpCore extends AbstractCleanUp {

	public HintFileCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public HintFileCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(HINTFILE_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}

		if (!isEnabled(HINTFILE_CLEANUP)) {
			return null;
		}

		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		HintFileFixCore.findOperations(compilationUnit, operations);

		if (operations.isEmpty()) {
			return null;
		}

		CompilationUnitRewriteOperation[] array = operations.toArray(
				new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]);
		return new CompilationUnitRewriteOperationsFixCore(
				MultiFixMessages.HintFileCleanUpFix_refactor,
				compilationUnit,
				array);
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(HINTFILE_CLEANUP)) {
			result.add(MultiFixMessages.HintFileCleanUp_description);
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		if (isEnabled(HINTFILE_CLEANUP)) {
			return """
				// After applying .sandbox-hint rules:
				String s = String.valueOf(value);
				boolean empty = list.isEmpty();
				"""; //$NON-NLS-1$
		}
		return """
			// Before applying .sandbox-hint rules:
			String s = "" + value;
			boolean empty = list.size() == 0;
			"""; //$NON-NLS-1$
	}
}
