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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.STRING_SIMPLIFICATION_CLEANUP;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.internal.corext.fix.StringSimplificationFixCore;

/**
 * CleanUp for string simplification using TriggerPattern hints.
 * 
 * <p>This cleanup applies string simplification patterns such as:</p>
 * <ul>
 * <li>{@code "" + x} → {@code String.valueOf(x)}</li>
 * <li>{@code x + ""} → {@code String.valueOf(x)}</li>
 * </ul>
 * 
 * @since 1.2.2
 */
public class StringSimplificationCleanUpCore extends AbstractCleanUp {
	
	public StringSimplificationCleanUpCore(final Map<String, String> options) {
		super(options);
	}
	
	public StringSimplificationCleanUpCore() {
	}
	
	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}
	
	public boolean requireAST() {
		return isEnabled(STRING_SIMPLIFICATION_CLEANUP);
	}
	
	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		CompilationUnit compilationUnit = context.getAST();
		if (compilationUnit == null) {
			return null;
		}
		
		if (!isEnabled(STRING_SIMPLIFICATION_CLEANUP)) {
			return null;
		}
		
		Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
		StringSimplificationFixCore.findOperations(compilationUnit, operations);
		
		if (operations.isEmpty()) {
			return null;
		}
		
		CompilationUnitRewriteOperation[] array = operations.toArray(
				new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[0]);
		return new CompilationUnitRewriteOperationsFixCore(
				"Simplify string concatenation", //$NON-NLS-1$
				compilationUnit, 
				array);
	}
	
	@Override
	public String[] getStepDescriptions() {
		List<String> result = new ArrayList<>();
		if (isEnabled(STRING_SIMPLIFICATION_CLEANUP)) {
			result.add("Simplifies string concatenation with empty strings to String.valueOf()"); //$NON-NLS-1$
		}
		return result.toArray(new String[0]);
	}
	
	@Override
	public String getPreview() {
		if (isEnabled(STRING_SIMPLIFICATION_CLEANUP)) {
			return """
				String result = String.valueOf(value);
				String message = String.valueOf(count);
				"""; //$NON-NLS-1$
		}
		return """
			String result = "" + value;
			String message = count + "";
			"""; //$NON-NLS-1$
	}
}
