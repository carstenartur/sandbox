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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.FunctionalCallCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.FunctionalCallCleanUp_description;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * V2 cleanup for functional loop conversions using Unified Loop Representation (ULR).
 * 
 * <p>This cleanup uses the V2 implementation ({@link org.sandbox.jdt.internal.corext.fix.helper.LoopToFunctionalV2})
 * which is based on the Unified Loop Representation model.</p>
 * 
 * <p><b>Current Status:</b> Phase 1 - Feature Parity with V1</p>
 * <p>In Phase 1, V2 delegates to V1 to ensure identical behavior. This allows
 * thorough testing of the infrastructure before implementing the ULR-based logic.</p>
 * 
 * @see UseFunctionalCallCleanUpCore
 * @see <a href="https://github.com/carstenartur/sandbox/issues/450">Issue #450</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 * @since 1.0.0
 */
public class UseFunctionalCallCleanUpCoreV2 extends AbstractCleanUp {
	
	public UseFunctionalCallCleanUpCoreV2(final Map<String, String> options) {
		super(options);
	}

	public UseFunctionalCallCleanUpCoreV2() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<UseFunctionalCallFixCore> computeFixSet = computeFixSet();
		if (!isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2) || computeFixSet.isEmpty()) {
			return null;
		}
		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed = new HashSet<>();
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesprocessed));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(FunctionalCallCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2)) {
			result.add(Messages.format(FunctionalCallCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(UseFunctionalCallFixCore::toString).collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb = new StringBuilder();
		EnumSet<UseFunctionalCallFixCore> computeFixSet = computeFixSet();
		EnumSet.allOf(UseFunctionalCallFixCore.class).forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<UseFunctionalCallFixCore> computeFixSet() {
		EnumSet<UseFunctionalCallFixCore> fixSet = EnumSet.noneOf(UseFunctionalCallFixCore.class);

		if (isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2)) {
			// Only include LOOP_V2 in the fix set
			fixSet.add(UseFunctionalCallFixCore.LOOP_V2);
		}
		return fixSet;
	}
}
