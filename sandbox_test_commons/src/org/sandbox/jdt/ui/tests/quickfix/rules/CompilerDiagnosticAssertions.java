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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ltk.core.refactoring.Change;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;

/** Compares the JDT warning and error multiset for each complete source unit. */
public final class CompilerDiagnosticAssertions {
	private CompilerDiagnosticAssertions() {
	}

	record CompilerProblemKey(boolean error, int id, String arguments) {
		static CompilerProblemKey of(IProblem problem, CompilationUnit root) {
			String[] arguments= problem.getArguments();
			if ((problem.getID() == IProblem.MethodCanBeStatic || problem.getID() == IProblem.MethodCanBePotentiallyStatic)
					&& arguments.length == 3 && hasUniqueMethodName(root, problem)) {
				// Parameter types are part of the message even though the warning concerns
				// use of the receiver. Preserve the full signature when it distinguishes overloads.
				arguments= Arrays.copyOf(arguments, 2);
			}
			return new CompilerProblemKey(problem.isError(), problem.getID(),
					Arrays.toString(arguments));
		}

		String severity() {
			return error ? "ERROR" : "WARNING"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static boolean hasUniqueMethodName(CompilationUnit root, IProblem problem) {
		int start= problem.getSourceStart();
		if (start < 0) {
			return false;
		}
		ASTNode node= NodeFinder.perform(root, start, problem.getSourceEnd() - start + 1);
		while (node != null && !(node instanceof MethodDeclaration)) {
			node= node.getParent();
		}
		if (node == null) {
			return false;
		}
		MethodDeclaration method= (MethodDeclaration) node;
		IMethodBinding binding= method.resolveBinding();
		if (binding == null || binding.getDeclaringClass() == null) {
			return false;
		}
		return Arrays.stream(binding.getDeclaringClass().getDeclaredMethods())
				.filter(candidate -> candidate.getName().equals(binding.getName())).count() == 1;
	}

	/** Records diagnostics before a direct change or a workbench interaction. */
	public static Snapshot capture(ICompilationUnit... units) {
		return new Snapshot(units);
	}

	/** Original per-file diagnostics, including warnings which an edit may remove. */
	public static final class Snapshot {
		private final ICompilationUnit[] units;
		private final Map<ICompilationUnit, Map<CompilerProblemKey, Long>> before;

		private Snapshot(ICompilationUnit[] units) {
			if (units.length == 0) {
				throw new IllegalArgumentException("The compiler check needs at least one compilation unit"); //$NON-NLS-1$
			}
			this.units= units.clone();
			before= captureCompilerDiagnostics(this.units);
		}

		public void assertNoNewDiagnostics() {
			assertNoNewCompilerDiagnostics(units, before);
		}

		public void assertRestored() {
			assertEquals(before, captureCompilerDiagnostics(units), "Undo must restore the original Java warning and error set"); //$NON-NLS-1$
		}
	}

	/** Performs a direct LTK change and checks every supplied unit against its own baseline. */
	public static Change performAndCheck(Change change, IProgressMonitor monitor, ICompilationUnit... units) throws CoreException {
		Snapshot before= capture(units);
		Change undo= change.perform(monitor);
		before.assertNoNewDiagnostics();
		return undo;
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
				.collect(Collectors.groupingBy(problem -> CompilerProblemKey.of(problem, root), LinkedHashMap::new, Collectors.counting()));
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
