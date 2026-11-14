/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix.rules;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;

/** Compares the JDT warning and error multiset for each complete source unit. */
final class CompilerDiagnosticAssertions {
	private CompilerDiagnosticAssertions() {
	}

	record CompilerProblemKey(boolean error, int id, String arguments) {
		static CompilerProblemKey of(IProblem problem) {
			return new CompilerProblemKey(problem.isError(), problem.getID(),
					Arrays.toString(problem.getArguments()));
		}

		String severity() {
			return error ? "ERROR" : "WARNING"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	static Map<ICompilationUnit, Map<CompilerProblemKey, Long>> captureCompilerDiagnostics(
			ICompilationUnit[] units) {
		Map<ICompilationUnit, Map<CompilerProblemKey, Long>> result= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			result.put(unit, compilerDiagnostics(unit));
		}
		return result;
	}

	private static Map<CompilerProblemKey, Long> compilerDiagnostics(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(unit);
		var options= unit.getJavaProject().getOptions(true);
		// Test diagnostics include optional compiler warnings even when the cleanup
		// fixture itself uses a lenient workspace profile. Keep project errors and its language level.
		options.replaceAll((key, severity) -> key.startsWith(JavaCore.PLUGIN_ID + ".compiler.problem.") //$NON-NLS-1$
				&& (JavaCore.IGNORE.equals(severity) || JavaCore.INFO.equals(severity)) ? JavaCore.WARNING : severity);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		return Arrays.stream(root.getProblems())
				.filter(problem -> problem.isError() || problem.isWarning())
				.collect(Collectors.groupingBy(CompilerProblemKey::of, LinkedHashMap::new, Collectors.counting()));
	}

	static void assertNoNewCompilerDiagnostics(ICompilationUnit[] units,
			Map<ICompilationUnit, Map<CompilerProblemKey, Long>> before) {
		StringBuilder differences= new StringBuilder();
		for (ICompilationUnit unit : units) {
			Map<CompilerProblemKey, Long> baseline= before.getOrDefault(unit, Map.of());
			for (Map.Entry<CompilerProblemKey, Long> entry : compilerDiagnostics(unit).entrySet()) {
				long previous= baseline.getOrDefault(entry.getKey(), 0L);
				if (entry.getValue() > previous) {
					CompilerProblemKey problem= entry.getKey();
					differences.append(unit.getElementName() + " gained compiler diagnostic: " + problem.severity() //$NON-NLS-1$
							+ " id=" + problem.id() + " arguments=" + problem.arguments() //$NON-NLS-1$ //$NON-NLS-2$
							+ " before=" + previous + " after=" + entry.getValue()).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		}
		if (!differences.isEmpty()) {
			fail(differences.toString());
		}
	}

}
