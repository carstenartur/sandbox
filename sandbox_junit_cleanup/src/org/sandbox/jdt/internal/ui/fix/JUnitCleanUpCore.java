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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.JUNIT_CLEANUP;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.JUnitCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.JUnitCleanUp_description;

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
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

public class JUnitCleanUpCore extends AbstractCleanUp {
	public JUnitCleanUpCore(final Map<String, String> options) {
		super(options);
	}
	/**
	 *
	 */
	public JUnitCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(JUNIT_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<JUnitCleanUpFixCore> computeFixSet= computeFixSet();
		if (!isEnabled(JUNIT_CLEANUP) || computeFixSet.isEmpty()
				|| !JavaModelUtil.is1d8OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			return null;
		}
		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed= new HashSet<>();
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesprocessed));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(JUnitCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(JUNIT_CLEANUP)) {
			result.add(Messages.format(JUnitCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(JUnitCleanUpFixCore::toString)
					.collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		EnumSet<JUnitCleanUpFixCore> computeFixSet= computeFixSet();
		EnumSet.allOf(JUnitCleanUpFixCore.class)
		.forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<JUnitCleanUpFixCore> computeFixSet() {
		EnumSet<JUnitCleanUpFixCore> fixSet= EnumSet.noneOf(JUnitCleanUpFixCore.class);
		if (isEnabled(JUNIT_CLEANUP)) {
			fixSet= EnumSet.allOf(JUnitCleanUpFixCore.class);
		}
		return fixSet;
	}
}
