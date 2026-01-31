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
		if (!isEnabled(METHOD_REUSE_CLEANUP) && !isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			return null;
		}
		
		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		Set<ASTNode> nodesprocessed = new HashSet<>();
		
		// For inline sequences detection
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			MethodReuseCleanUpFixCore.INLINE_SEQUENCES.findOperations(compilationUnit, operations, nodesprocessed);
		}
		
		if (operations.isEmpty()) {
			return null;
		}
		
		return new CompilationUnitRewriteOperationsFixCore("Method Reuse Cleanup", compilationUnit,
				operations.toArray(new CompilationUnitRewriteOperation[0]));
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
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			return """
				// Before:
				void method1() {
					int x = 0;
					x++;
					System.out.println(x);
				}
				
				void method2() {
					int y = 0;
					y++;
					System.out.println(y);
				}
				
				// After: Method reuse opportunity detected
				// (warning marker would appear)
				""";
		}
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			return """
				// Before:
				String formatName(String first, String last) {
					return first.trim() + " " + last.trim();
				}
				
				void printUser(String firstName, String lastName) {
					String name = firstName.trim() + " " + lastName.trim();
					System.out.println(name);
				}
				
				// After:
				String formatName(String first, String last) {
					return first.trim() + " " + last.trim();
				}
				
				void printUser(String firstName, String lastName) {
					String name = formatName(firstName, lastName);
					System.out.println(name);
				}
				""";
		}
		return "";
	}
}
