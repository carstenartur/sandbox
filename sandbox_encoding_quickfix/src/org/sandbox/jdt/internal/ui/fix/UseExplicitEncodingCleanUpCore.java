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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.EXPLICITENCODING_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.ExplicitEncodingCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.ExplicitEncodingCleanUp_description;

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
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFix;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractExplicitEncoding.ChangeBehavior;

public class UseExplicitEncodingCleanUpCore extends AbstractCleanUp {
	public UseExplicitEncodingCleanUpCore(final Map<String, String> options) {
		super(options);
	}
	public UseExplicitEncodingCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(EXPLICITENCODING_CLEANUP)&& !computeFixSet().isEmpty();
	}
	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		EnumSet<UseExplicitEncodingFixCore> computeFixSet= computeFixSet();
		if(!isEnabled(EXPLICITENCODING_CLEANUP) || computeFixSet.isEmpty()) {
			return null;
		}

		ChangeBehavior cb= computeRefactorDeepth();
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed= new HashSet<>();
		computeFixSet.forEach(i->i.findOperations(compilationUnit,operations,nodesprocessed,cb));
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFix(ExplicitEncodingCleanUpFix_refactor,
				compilationUnit, operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]));
	}

	private ChangeBehavior computeRefactorDeepth() {
		ChangeBehavior cb=ChangeBehavior.KEEP;
		if(isEnabled(EXPLICITENCODING_KEEP_BEHAVIOR)) {
			cb=ChangeBehavior.KEEP;
		}
		if(isEnabled(EXPLICITENCODING_INSERT_UTF8)) {
			cb=ChangeBehavior.USE_UTF8;
		}
		if(isEnabled(EXPLICITENCODING_AGGREGATE_TO_UTF8)) {
			cb=ChangeBehavior.USE_UTF8_AGGREGATE;
		}
		return cb;
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(EXPLICITENCODING_CLEANUP)) {
			String with=computeRefactorDeepth().toString();
			result.add(Messages.format(ExplicitEncodingCleanUp_description,new Object[] {String.join(",", computeFixSet().stream().map(UseExplicitEncodingFixCore::toString).collect(Collectors.toList())),with})); //$NON-NLS-1$
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb=new StringBuilder();
		EnumSet<UseExplicitEncodingFixCore> computeFixSet= computeFixSet();
		ChangeBehavior cb= computeRefactorDeepth();
		EnumSet.allOf(UseExplicitEncodingFixCore.class).forEach(e->sb.append(e.getPreview(computeFixSet.contains(e),cb)));
		return sb.toString();
	}

	private EnumSet<UseExplicitEncodingFixCore> computeFixSet() {
		EnumSet<UseExplicitEncodingFixCore> fixSet= EnumSet.noneOf(UseExplicitEncodingFixCore.class);

		if(isEnabled(EXPLICITENCODING_CLEANUP)) {
			fixSet= EnumSet.allOf(UseExplicitEncodingFixCore.class);
		}
		return fixSet;
	}
}