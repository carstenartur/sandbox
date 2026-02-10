/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.TRIGGERPATTERN_THREADING_CLEANUP;

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
import org.sandbox.jdt.internal.corext.fix.ThreadingFixCore;

/**
 * CleanUp for threading anti-patterns using TriggerPattern hints.
 *
 * <p>This cleanup detects and fixes threading anti-patterns such as:</p>
 * <ul>
 * <li>{@code thread.run()} → {@code thread.start()} (direct run() call)</li>
 * </ul>
 *
 * <p>Inspired by NetBeans' Tiny.java threading hints.</p>
 *
 * @since 1.2.5
 * @see <a href="https://github.com/apache/netbeans/blob/master/java/java.hints/src/org/netbeans/modules/java/hints/threading/Tiny.java">NetBeans Tiny.java</a>
 */
public class ThreadingCleanUpCore extends AbstractCleanUp {

	public ThreadingCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public ThreadingCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(TRIGGERPATTERN_THREADING_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}

		if (!isEnabled(TRIGGERPATTERN_THREADING_CLEANUP)) {
			return null;
		}

		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		ThreadingFixCore.findOperations(compilationUnit, operations);

		if (operations.isEmpty()) {
			return null;
		}

		CompilationUnitRewriteOperation[] array = operations.toArray(
				new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]);
		return new CompilationUnitRewriteOperationsFixCore(
				MultiFixMessages.ThreadingCleanUpFix_refactor,
				compilationUnit,
				array);
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(TRIGGERPATTERN_THREADING_CLEANUP)) {
			result.add(MultiFixMessages.ThreadingCleanUp_description);
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		if (isEnabled(TRIGGERPATTERN_THREADING_CLEANUP)) {
			return """
				Thread thread = new Thread(runnable);
				thread.start();
				"""; //$NON-NLS-1$
		}
		return """
			Thread thread = new Thread(runnable);
			thread.run();
			"""; //$NON-NLS-1$
	}
}
