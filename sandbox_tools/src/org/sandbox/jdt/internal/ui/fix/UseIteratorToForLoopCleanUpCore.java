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

import static org.eclipse.jdt.internal.corext.fix.CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_ONLY_IF_LOOP_VAR_USED;
import static org.eclipse.jdt.internal.corext.fix.CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.ToolsCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.ToolsCleanUp_description;

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
import org.eclipse.jdt.core.manipulation.CleanUpContextCore;
import org.eclipse.jdt.core.manipulation.CleanUpRequirementsCore;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCore;
import org.sandbox.jdt.internal.corext.fix.UseIteratorToForLoopFixCore;

public class UseIteratorToForLoopCleanUpCore extends AbstractCleanUpCore {
	public UseIteratorToForLoopCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public UseIteratorToForLoopCleanUpCore() {
	}

	@Override
	public CleanUpRequirementsCore getRequirementsCore() {
		return new CleanUpRequirementsCore(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
	}

	@Override
	public ICleanUpFixCore createFixCore(final CleanUpContextCore context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<UseIteratorToForLoopFixCore> computeFixSet = computeFixSet();
		if (!isEnabled(CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED) || computeFixSet.isEmpty() || !JavaModelUtil.is1d8OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			return null;
		}
		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed = new HashSet<>();
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesprocessed, isEnabled(CONTROL_STATEMENTS_CONVERT_FOR_LOOP_ONLY_IF_LOOP_VAR_USED)));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(ToolsCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED)) {
			result.add(Messages.format(ToolsCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(UseIteratorToForLoopFixCore::toString).collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb = new StringBuilder();
		EnumSet<UseIteratorToForLoopFixCore> computeFixSet = computeFixSet();
		EnumSet.allOf(UseIteratorToForLoopFixCore.class).forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<UseIteratorToForLoopFixCore> computeFixSet() {
		EnumSet<UseIteratorToForLoopFixCore> fixSet = EnumSet.noneOf(UseIteratorToForLoopFixCore.class);

		if (isEnabled(CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED)) {
			fixSet = EnumSet.allOf(UseIteratorToForLoopFixCore.class);
		}
		return fixSet;
	}
}
