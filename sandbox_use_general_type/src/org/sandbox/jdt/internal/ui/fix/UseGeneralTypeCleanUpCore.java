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
import org.sandbox.jdt.internal.corext.fix.UseGeneralTypeFixCore;

public class UseGeneralTypeCleanUpCore extends AbstractCleanUp {
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
		EnumSet<UseGeneralTypeFixCore> computeFixSet= computeFixSet();
		if (!isEnabled(USE_GENERAL_TYPE_CLEANUP) || computeFixSet.isEmpty()) {
			return null;
		}
		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed= new HashSet<>();
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesprocessed, true));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(UseGeneralTypeCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(USE_GENERAL_TYPE_CLEANUP)) {
			result.add(Messages.format(UseGeneralTypeCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(UseGeneralTypeFixCore::toString)
					.collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		EnumSet<UseGeneralTypeFixCore> computeFixSet= computeFixSet();
		EnumSet.allOf(UseGeneralTypeFixCore.class)
		.forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<UseGeneralTypeFixCore> computeFixSet() {
		EnumSet<UseGeneralTypeFixCore> fixSet= EnumSet.noneOf(UseGeneralTypeFixCore.class);

		if (isEnabled(USE_GENERAL_TYPE_CLEANUP)) {
			fixSet= EnumSet.allOf(UseGeneralTypeFixCore.class);
		}
		return fixSet;
	}
}
