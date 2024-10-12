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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.JFACE_CLEANUP;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.JFaceCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.JFaceCleanUp_description;

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
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;

public class JFaceCleanUpCore extends AbstractCleanUp {
	public JFaceCleanUpCore(final Map<String, String> options) {
		super(options);
	}
	/**
	 *
	 */
	public JFaceCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(JFACE_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<JfaceCleanUpFixCore> computeFixSet= computeFixSet();
		if (!isEnabled(JFACE_CLEANUP) || computeFixSet.isEmpty()
				|| !JavaModelUtil.is1d8OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			return null;
		}
		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed= new HashSet<>();
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesprocessed,
				true));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(JFaceCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(JFACE_CLEANUP)) {
			result.add(Messages.format(JFaceCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(JfaceCleanUpFixCore::toString)
					.collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		EnumSet<JfaceCleanUpFixCore> computeFixSet= computeFixSet();
		EnumSet.allOf(JfaceCleanUpFixCore.class)
		.forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<JfaceCleanUpFixCore> computeFixSet() {
		EnumSet<JfaceCleanUpFixCore> fixSet= EnumSet.noneOf(JfaceCleanUpFixCore.class);

		if (isEnabled(JFACE_CLEANUP)) {
			fixSet= EnumSet.allOf(JfaceCleanUpFixCore.class);
		}
		return fixSet;
	}
}
