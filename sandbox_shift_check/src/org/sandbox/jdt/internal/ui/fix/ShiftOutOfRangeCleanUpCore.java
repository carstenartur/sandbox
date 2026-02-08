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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.SHIFT_OUT_OF_RANGE_CLEANUP;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.ShiftOutOfRangeCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.ShiftOutOfRangeCleanUp_description;

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
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.ShiftOutOfRangeFixCore;

/**
 * Core cleanup implementation that detects shift operations with out-of-range
 * shift amounts and replaces them with the effective masked value.
 */
public class ShiftOutOfRangeCleanUpCore extends AbstractCleanUp {
	public ShiftOutOfRangeCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public ShiftOutOfRangeCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(SHIFT_OUT_OF_RANGE_CLEANUP) && !computeFixSet().isEmpty();
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}

		EnumSet<ShiftOutOfRangeFixCore> computeFixSet = computeFixSet();
		if (!isEnabled(SHIFT_OUT_OF_RANGE_CLEANUP) || computeFixSet.isEmpty()) {
			return null;
		}

		Set<CompilationUnitRewriteOperationWithSourceRange> operations = new LinkedHashSet<>();
		Set<ASTNode> nodesProcessed = new HashSet<>();
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesProcessed));

		if (operations.isEmpty()) {
			return null;
		}

		CompilationUnitRewriteOperationWithSourceRange[] array = operations.toArray(
				new CompilationUnitRewriteOperationWithSourceRange[0]);
		return new CompilationUnitRewriteOperationsFixCore(ShiftOutOfRangeCleanUpFix_refactor, compilationUnit, array);
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(SHIFT_OUT_OF_RANGE_CLEANUP)) {
			result.add(Messages.format(ShiftOutOfRangeCleanUp_description,
					new Object[] { String.join(",", computeFixSet().stream()
							.map(ShiftOutOfRangeFixCore::toString)
							.collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb = new StringBuilder();
		EnumSet<ShiftOutOfRangeFixCore> computeFixSet = computeFixSet();
		EnumSet.allOf(ShiftOutOfRangeFixCore.class).forEach(e ->
			sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<ShiftOutOfRangeFixCore> computeFixSet() {
		EnumSet<ShiftOutOfRangeFixCore> fixSet = EnumSet.noneOf(ShiftOutOfRangeFixCore.class);
		if (isEnabled(SHIFT_OUT_OF_RANGE_CLEANUP)) {
			fixSet = EnumSet.allOf(ShiftOutOfRangeFixCore.class);
		}
		return fixSet;
	}
}
