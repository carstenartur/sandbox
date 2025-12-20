/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

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
		return isEnabled(METHOD_REUSE_CLEANUP);
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		if (!isEnabled(METHOD_REUSE_CLEANUP)) {
			return null;
		}
		
		// TODO: Implement method similarity detection
		// This is a placeholder implementation
		// Real implementation would:
		// 1. Find all methods in the compilation unit
		// 2. For each method, search for similar methods in the project
		// 3. Create markers/warnings for detected duplicates
		// 4. Optionally suggest refactoring
		
		return null;
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			result.add("Find reusable method patterns");
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
		return "";
	}
}
