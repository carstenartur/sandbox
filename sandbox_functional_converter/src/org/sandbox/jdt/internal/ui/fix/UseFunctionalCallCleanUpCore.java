/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.LOOP_CONVERSION_ENABLED;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_FOR;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_WHILE;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.FunctionalCallCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.FunctionalCallCleanUp_description;
import static org.sandbox.jdt.internal.ui.preferences.cleanup.CleanUpMessages.LoopConversion_Description;

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

public class UseFunctionalCallCleanUpCore extends AbstractCleanUp {
	
	public UseFunctionalCallCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public UseFunctionalCallCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2) || isEnabled(LOOP_CONVERSION_ENABLED);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<UseFunctionalCallFixCore> computeFixSet = computeFixSet();
		if ((!isEnabled(USEFUNCTIONALLOOP_CLEANUP) && !isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2) && !isEnabled(LOOP_CONVERSION_ENABLED)) || computeFixSet.isEmpty()) {
			return null;
		}
		
		// Check target format preference (STREAM, FOR_LOOP, WHILE_LOOP)
		// Note: Currently only STREAM is fully implemented. FOR_LOOP and WHILE_LOOP
		// support will be added in future phases.
		// For backward compatibility, proceed with STREAM format unless FOR or WHILE is explicitly enabled
		if (isEnabled(USEFUNCTIONALLOOP_FORMAT_FOR) || isEnabled(USEFUNCTIONALLOOP_FORMAT_WHILE)) {
			// Not yet implemented - return null to skip transformation
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
		if (isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2)) {
			result.add(Messages.format(FunctionalCallCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(UseFunctionalCallFixCore::toString).collect(Collectors.toList())) }));
		}
		if (isEnabled(LOOP_CONVERSION_ENABLED)) {
			result.add(LoopConversion_Description);
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

		// Functional loop cleanup (handles both V1 and V2 constants for backward compatibility)
		if (isEnabled(USEFUNCTIONALLOOP_CLEANUP) || isEnabled(USEFUNCTIONALLOOP_CLEANUP_V2)) {
			// LOOP now uses the unified V2 implementation (ULR + Refactorer fallback)
			fixSet.add(UseFunctionalCallFixCore.LOOP);
			fixSet.add(UseFunctionalCallFixCore.ITERATOR_LOOP);
		}
		
		// Bidirectional Loop Conversion (Phase 9) - Currently disabled
		// The LOOP_CONVERSION_* options are defined but the infrastructure to properly
		// read the target format from CleanUpOptions is not yet working correctly.
		// For now, these bidirectional transformations remain disabled.
		// TODO: Implement proper CleanUpOptions value reading for non-boolean options
		// See: https://github.com/carstenartur/sandbox/issues/XXX
		
		return fixSet;
	}
}
