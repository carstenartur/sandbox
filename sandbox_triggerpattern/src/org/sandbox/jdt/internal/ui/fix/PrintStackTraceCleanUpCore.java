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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.PRINT_STACKTRACE_CLEANUP;

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
import org.sandbox.jdt.internal.corext.fix.PrintStackTraceFixCore;

/**
 * CleanUp for printStackTrace() detection using TriggerPattern hints.
 *
 * <p>This cleanup detects calls to printStackTrace() and suggests
 * replacing them with proper logging.</p>
 *
 * @since 1.3.9
 */
public class PrintStackTraceCleanUpCore extends AbstractCleanUp {

	public PrintStackTraceCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public PrintStackTraceCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(PRINT_STACKTRACE_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}

		if (!isEnabled(PRINT_STACKTRACE_CLEANUP)) {
			return null;
		}

		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		PrintStackTraceFixCore.findOperations(compilationUnit, operations);

		if (operations.isEmpty()) {
			return null;
		}

		CompilationUnitRewriteOperation[] array = operations.toArray(
				new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]);
		return new CompilationUnitRewriteOperationsFixCore(
				MultiFixMessages.PrintStackTraceCleanUpFix_refactor,
				compilationUnit,
				array);
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(PRINT_STACKTRACE_CLEANUP)) {
			result.add(MultiFixMessages.PrintStackTraceCleanUp_description);
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		if (isEnabled(PRINT_STACKTRACE_CLEANUP)) {
			return "logger.log(Level.SEVERE, \"Error\", ex);\n"; //$NON-NLS-1$
		}
		return "ex.printStackTrace();\n"; //$NON-NLS-1$
	}
}
