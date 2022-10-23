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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.XML_CLEANUP;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.XMLCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.XMLCleanUp_description;

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
import org.sandbox.jdt.internal.corext.fix.XMLCleanUpFixCore;

public class XMLCleanUpCore extends AbstractCleanUpCore {
	public XMLCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public XMLCleanUpCore() {
	}

	@Override
	public CleanUpRequirementsCore getRequirementsCore() {
		return new CleanUpRequirementsCore(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(XML_CLEANUP);
	}

	@Override
	public ICleanUpFixCore createFixCore(final CleanUpContextCore context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<XMLCleanUpFixCore> computeFixSet= computeFixSet();
		if (!isEnabled(XML_CLEANUP) || computeFixSet.isEmpty()
				|| !JavaModelUtil.is1d8OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			return null;
		}
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed= new HashSet<>();
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesprocessed,
				true));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore(XMLCleanUpFix_refactor, compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(XML_CLEANUP)) {
			result.add(Messages.format(XMLCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(XMLCleanUpFixCore::toString)
					.collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		EnumSet<XMLCleanUpFixCore> computeFixSet= computeFixSet();
		EnumSet.allOf(XMLCleanUpFixCore.class)
		.forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return 
				sb.toString();
	}

	private EnumSet<XMLCleanUpFixCore> computeFixSet() {
		EnumSet<XMLCleanUpFixCore> fixSet= EnumSet.noneOf(XMLCleanUpFixCore.class);

		if (isEnabled(XML_CLEANUP)) {
			fixSet= EnumSet.allOf(XMLCleanUpFixCore.class);
		}
		return fixSet;
	}
}
