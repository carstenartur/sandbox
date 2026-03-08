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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.UseGeneralTypeCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.UseGeneralTypeCleanUp_description;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.triggerpattern.cleanup.HintFileFixCore;

/**
 * Cleanup that widens variable declarations to more general types.
 *
 * <p>Delegates to the {@code use-general-type.sandbox-hint} DSL rule via
 * {@link HintFileFixCore#findOperationsForBundle}. The DSL rule uses the
 * {@code $widestType($var)} replacement function which analyzes all usages of
 * the variable (method calls, field accesses) and walks the type hierarchy to
 * determine the widest possible type. When no widening is possible the match
 * is filtered out before entering rewrite.</p>
 */
public class UseGeneralTypeCleanUpCore extends AbstractCleanUp {

	private static final String BUNDLE_ID = "use-general-type"; //$NON-NLS-1$

	public UseGeneralTypeCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public UseGeneralTypeCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(USE_GENERAL_TYPE_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		if (!isEnabled(USE_GENERAL_TYPE_CLEANUP)) {
			return null;
		}
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed= new HashSet<>();

		// DSL-first: delegate to the use-general-type.sandbox-hint rule.
		// The $widestType function and isNonWidenableDeclaration filter
		// in HintFileFixCore ensure that rewrite is only entered when a
		// real type change is available (no noop).
		HintFileFixCore.findOperationsForBundle(
				compilationUnit, BUNDLE_ID, operations, nodesprocessed);

		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(UseGeneralTypeCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperation[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(USE_GENERAL_TYPE_CLEANUP)) {
			result.add(UseGeneralTypeCleanUp_description);
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		if (isEnabled(USE_GENERAL_TYPE_CLEANUP)) {
			return "List<String> list = new ArrayList<>();\n"; //$NON-NLS-1$
		}
		return "ArrayList<String> list = new ArrayList<>();\n"; //$NON-NLS-1$
	}
}
