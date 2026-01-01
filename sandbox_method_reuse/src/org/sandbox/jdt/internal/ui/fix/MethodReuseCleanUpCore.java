/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.METHOD_REUSE_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.METHOD_REUSE_INLINE_SEQUENCES;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore;

/**
 * Method Reuse Cleanup Core - Core cleanup logic
 * 
 * This cleanup analyzes methods to find potential code reuse opportunities
 * by detecting similar or duplicate code patterns.
 */
public class MethodReuseCleanUpCore extends AbstractCleanUp {
	
	public MethodReuseCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public MethodReuseCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(METHOD_REUSE_CLEANUP) || isEnabled(METHOD_REUSE_INLINE_SEQUENCES);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		
		EnumSet<MethodReuseCleanUpFixCore> fixSet = computeFixSet();
		if (fixSet.isEmpty()) {
			return null;
		}
		
		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed = new HashSet<>();
		fixSet.forEach(fix -> fix.findOperations(compilationUnit, operations, nodesprocessed));
		
		if (operations.isEmpty()) {
			return null;
		}
		
		return new CompilationUnitRewriteOperationsFixCore("Method Reuse Cleanup", compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperation[0]));
	}
	
	private EnumSet<MethodReuseCleanUpFixCore> computeFixSet() {
		EnumSet<MethodReuseCleanUpFixCore> fixSet = EnumSet.noneOf(MethodReuseCleanUpFixCore.class);
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			fixSet.add(MethodReuseCleanUpFixCore.METHOD_REUSE);
		}
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			fixSet.add(MethodReuseCleanUpFixCore.INLINE_SEQUENCES);
		}
		return fixSet;
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			result.add("Find reusable method patterns");
		}
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			result.add("Find inline code sequences that can be replaced with method calls");
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb = new StringBuilder();
		EnumSet<MethodReuseCleanUpFixCore> computeFixSet = computeFixSet();
		EnumSet.allOf(MethodReuseCleanUpFixCore.class).forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}
}
