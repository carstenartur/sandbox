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

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

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
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

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
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, new HashSet<>()));
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
					computeFixSet().stream().map(JUnitCleanUpFixCore::toString).collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		EnumSet<JUnitCleanUpFixCore> computeFixSet= computeFixSet();
		EnumSet.allOf(JUnitCleanUpFixCore.class).forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	

	private EnumSet<JUnitCleanUpFixCore> computeFixSet() {
		EnumSet<JUnitCleanUpFixCore> fixSet = isEnabled(JUNIT_CLEANUP)
				? EnumSet.allOf(JUnitCleanUpFixCore.class)
						: EnumSet.noneOf(JUnitCleanUpFixCore.class);
		Map<String, JUnitCleanUpFixCore> cleanupMappings = Map.ofEntries(
				   Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, JUnitCleanUpFixCore.ASSERT),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, JUnitCleanUpFixCore.ASSUME),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, JUnitCleanUpFixCore.AFTER),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, JUnitCleanUpFixCore.BEFORE),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, JUnitCleanUpFixCore.AFTERCLASS),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, JUnitCleanUpFixCore.BEFORECLASS),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, JUnitCleanUpFixCore.TEST),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE, JUnitCleanUpFixCore.IGNORE),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, JUnitCleanUpFixCore.RULETEMPORARYFOLDER),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, JUnitCleanUpFixCore.EXTERNALRESOURCE),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER, JUnitCleanUpFixCore.RUNWITH),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME, JUnitCleanUpFixCore.RULETESTNAME),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, JUnitCleanUpFixCore.RULEEXTERNALRESOURCE)
				);
		cleanupMappings.forEach((config, fix) -> {
			if (!isEnabled(config)) {
				fixSet.remove(fix);
			}
		});
		return fixSet;
	}
}
