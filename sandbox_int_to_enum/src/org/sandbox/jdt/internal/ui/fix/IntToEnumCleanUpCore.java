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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.INT_TO_ENUM_CLEANUP;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.IntToEnumCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.IntToEnumCleanUp_description;

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
import org.sandbox.jdt.internal.corext.fix.IntToEnumFixCore;

/**
 * Core cleanup implementation that converts integer constants to enums.
 */
public class IntToEnumCleanUpCore extends AbstractCleanUp {
	public IntToEnumCleanUpCore(final Map<String, String> options) {
		super(options);
	}
	
	public IntToEnumCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(INT_TO_ENUM_CLEANUP) && !computeFixSet().isEmpty();
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		
		EnumSet<IntToEnumFixCore> computeFixSet = computeFixSet();
		if (!isEnabled(INT_TO_ENUM_CLEANUP) || computeFixSet.isEmpty()) {
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
		return new CompilationUnitRewriteOperationsFixCore(IntToEnumCleanUpFix_refactor, compilationUnit, array);
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(INT_TO_ENUM_CLEANUP)) {
			result.add(Messages.format(IntToEnumCleanUp_description,
					new Object[] { String.join(",", computeFixSet().stream()
							.map(IntToEnumFixCore::toString)
							.collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb = new StringBuilder();
		EnumSet<IntToEnumFixCore> computeFixSet = computeFixSet();
		EnumSet.allOf(IntToEnumFixCore.class).forEach(e -> 
			sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<IntToEnumFixCore> computeFixSet() {
		EnumSet<IntToEnumFixCore> fixSet = EnumSet.noneOf(IntToEnumFixCore.class);
		if (isEnabled(INT_TO_ENUM_CLEANUP)) {
			fixSet = EnumSet.allOf(IntToEnumFixCore.class);
		}
		return fixSet;
	}
}
