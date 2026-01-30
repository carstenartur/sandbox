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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.JUNIT3_CLEANUP;
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
		return isEnabled(JUNIT_CLEANUP)||isEnabled(JUNIT3_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<JUnitCleanUpFixCore> computeFixSet= computeFixSet();
		if (!(isEnabled(JUNIT_CLEANUP)||isEnabled(JUNIT3_CLEANUP)) || computeFixSet.isEmpty()
//				|| !JavaModelUtil.is1d8OrHigher(compilationUnit.getJavaElement().getJavaProject())
				) {
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
		if ((isEnabled(JUNIT_CLEANUP)||isEnabled(JUNIT3_CLEANUP))) {
			result.add(Messages.format(JUnitCleanUp_description, new Object[] { String.join(",", //$NON-NLS-1$
					computeFixSet().stream().map(JUnitCleanUpFixCore::toString).collect(Collectors.toList())) }));
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		EnumSet<JUnitCleanUpFixCore> computeFixSet= computeFixSet();
		allOfJunit4().forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<JUnitCleanUpFixCore> computeFixSet() {
		EnumSet<JUnitCleanUpFixCore> fixSetJunit4 = isEnabled(JUNIT_CLEANUP)
				? allOfJunit4()
						: EnumSet.noneOf(JUnitCleanUpFixCore.class);
		EnumSet<JUnitCleanUpFixCore> fixSetJunit3 = isEnabled(JUNIT3_CLEANUP)
				? allOfJunit3()
						: EnumSet.noneOf(JUnitCleanUpFixCore.class);
		Map<String, JUnitCleanUpFixCore> cleanupMappings = Map.ofEntries(
				   Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, JUnitCleanUpFixCore.ASSERT),
				   Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION, JUnitCleanUpFixCore.ASSERT_OPTIMIZATION),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, JUnitCleanUpFixCore.ASSUME),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION, JUnitCleanUpFixCore.ASSUME_OPTIMIZATION),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, JUnitCleanUpFixCore.AFTER),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, JUnitCleanUpFixCore.BEFORE),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, JUnitCleanUpFixCore.AFTERCLASS),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, JUnitCleanUpFixCore.BEFORECLASS),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, JUnitCleanUpFixCore.TEST),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT, JUnitCleanUpFixCore.TEST_TIMEOUT),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED, JUnitCleanUpFixCore.TEST_EXPECTED),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST, JUnitCleanUpFixCore.TEST3),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE, JUnitCleanUpFixCore.IGNORE),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY, JUnitCleanUpFixCore.CATEGORY),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER, JUnitCleanUpFixCore.FIX_METHOD_ORDER),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, JUnitCleanUpFixCore.RUNWITH),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, JUnitCleanUpFixCore.EXTERNALRESOURCE),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER, JUnitCleanUpFixCore.RULETEMPORARYFOLDER),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME, JUnitCleanUpFixCore.RULETESTNAME),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, JUnitCleanUpFixCore.RULEEXTERNALRESOURCE),
			       Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS, JUnitCleanUpFixCore.LOSTTESTS)
				);
		EnumSet<JUnitCleanUpFixCore> fixSetcombined=EnumSet.copyOf(fixSetJunit4);
		fixSetcombined.addAll(fixSetJunit3);

		cleanupMappings.forEach((config, fix) -> {
			if (!isEnabled(config)) {
				fixSetcombined.remove(fix);
			}
		});
		return fixSetcombined;
	}

	private EnumSet<JUnitCleanUpFixCore> allOfJunit4() {
		EnumSet<JUnitCleanUpFixCore> allOf= EnumSet.allOf(JUnitCleanUpFixCore.class);
		allOf.remove(JUnitCleanUpFixCore.TEST3);
		return allOf;
	}
	
	private EnumSet<JUnitCleanUpFixCore> allOfJunit3() {
		EnumSet<JUnitCleanUpFixCore> allOf= EnumSet.noneOf(JUnitCleanUpFixCore.class);
		allOf.add(JUnitCleanUpFixCore.TEST3);
		return allOf;
	}
}
