/*******************************************************************************
 * Copyright (c) 2025, 2026 Carsten Hammer.
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
import static org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpOptions.MINIMUM_STATEMENTS;

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
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.RepeatedCodeSequenceExtractor;

/** Cleanup for extracting repeated sequences or reusing an existing method. */
public class MethodReuseCleanUpCore extends AbstractCleanUp {

	private Map<String, String> optionsMap;

	public MethodReuseCleanUpCore(final Map<String, String> options) {
		super(options);
		optionsMap= options;
	}

	public MethodReuseCleanUpCore() {
	}

	@Override
	public void setOptions(CleanUpOptions options) {
		super.setOptions(options);
		if (options instanceof MapCleanUpOptions mapOptions) {
			optionsMap= mapOptions.getMap();
		}
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
		if (compilationUnit == null || context.getCompilationUnit() == null || !requireAST()) {
			return null;
		}

		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			ICleanUpFix extraction= RepeatedCodeSequenceExtractor.createFix(
					context.getCompilationUnit(), compilationUnit, getMinimumStatements());
			if (extraction != null) {
				return extraction;
			}
		}

		if (!isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			return null;
		}
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesProcessed= new HashSet<>();
		MethodReuseCleanUpFixCore.INLINE_SEQUENCES.findOperations(
				compilationUnit, operations, nodesProcessed);
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore("Method Reuse Cleanup", //$NON-NLS-1$
				compilationUnit, operations.toArray(new CompilationUnitRewriteOperation[0]));
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			result.add("Extract repeated sequences of at least " + getMinimumStatements() //$NON-NLS-1$
					+ " statements and replace every JDT-validated duplicate with a call"); //$NON-NLS-1$
		}
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			result.add("Replace inline code sequences with calls to an existing method"); //$NON-NLS-1$
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder preview= new StringBuilder();
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			preview.append("""
				void first(String value) {
				    extractedSequence(value);
				}
				private void extractedSequence(String value) {
				    String text = value.trim();
				    text = text.toLowerCase();
				    System.out.println(text);
				}
				void second(String input) {
				    extractedSequence(input);
				}
				"""); //$NON-NLS-1$
		} else {
			preview.append("""
				void first(String value) {
				    String text = value.trim();
				    text = text.toLowerCase();
				    System.out.println(text);
				}
				void second(String input) {
				    String text = input.trim();
				    text = text.toLowerCase();
				    System.out.println(text);
				}
				"""); //$NON-NLS-1$
		}
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			preview.append("""
				void printUser(String first, String last) {
				    String name = formatName(first, last);
				    System.out.println(name);
				}
				"""); //$NON-NLS-1$
		} else {
			preview.append("""
				void printUser(String first, String last) {
				    String name = first.trim() + " " + last.trim();
				    System.out.println(name);
				}
				"""); //$NON-NLS-1$
		}
		return preview.toString();
	}

	private int getMinimumStatements() {
		String configured= optionsMap == null ? null : optionsMap.get(MINIMUM_STATEMENTS);
		if (configured != null) {
			try {
				return RepeatedCodeSequenceExtractor.normalizeMinimum(Integer.parseInt(configured));
			} catch (NumberFormatException exception) {
				// Fall through to the documented default.
			}
		}
		return RepeatedCodeSequenceExtractor.DEFAULT_MINIMUM_STATEMENTS;
	}
}
