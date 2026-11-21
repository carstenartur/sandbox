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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;

/** Compares the JDT warning and error multiset for each complete source unit. */
public final class CompilerDiagnosticAssertions {
	private CompilerDiagnosticAssertions() {
	}

	record CompilerProblemKey(boolean error, int id, String arguments, String junit4Arguments) {
		static CompilerProblemKey of(IProblem problem, CompilationUnit root) {
			String[] arguments= problem.getArguments();
			arguments= unassignedResourceArguments(problem, root, arguments);
			if ((problem.getID() == IProblem.MethodCanBeStatic || problem.getID() == IProblem.MethodCanBePotentiallyStatic)
					&& arguments.length == 3 && hasUniqueMethodName(root, problem)) {
				// Parameter types are part of the message even though the warning concerns
				// use of the receiver. Preserve the full signature when it distinguishes overloads.
				arguments= Arrays.copyOf(arguments, 2);
			}
			String junit4Arguments= problem.getID() == IProblem.OverridingMethodWithoutSuperInvocation
					? junit4CallbackArguments(declaringMethod(root, problem)) : null;
			return new CompilerProblemKey(problem.isError(), problem.getID(), Arrays.toString(arguments), junit4Arguments);
		}

		String severity() {
			return error ? "ERROR" : "WARNING"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static String[] unassignedResourceArguments(IProblem problem, CompilationUnit root, String[] arguments) {
		if (problem.getID() != IProblem.PotentiallyUnclosedCloseable || arguments.length != 1
				|| !("<unassigned Closeable value>".equals(arguments[0]) //$NON-NLS-1$
						|| arguments[0].matches("<unassigned Closeable value from line [1-9][0-9]*>"))) { //$NON-NLS-1$
			return arguments;
		}
		int start= problem.getSourceStart();
		int length= problem.getSourceEnd() - start + 1;
		if (start < 0 || length <= 0) {
			return arguments;
		}
		ASTNode node= NodeFinder.perform(root, start, length);
		if (!(node instanceof Expression expression) || node.getStartPosition() != start || node.getLength() != length) {
			return arguments;
		}
		var type= expression.resolveTypeBinding();
		if (type == null || type.isRecovered() || Bindings.findTypeInHierarchy(type, "java.lang.AutoCloseable") == null) { //$NON-NLS-1$
			return arguments;
		}
		ResourceLocation location= resourceLocation(expression);
		if (location == null) {
			return arguments;
		}
		IMethodBinding method= location.method().resolveBinding();
		if (method == null || method.isRecovered()
				|| method.getDeclaringClass() == null || method.getDeclaringClass().isRecovered()
				|| method.getDeclaringClass().isLocal() || method.getDeclaringClass().isAnonymous()) {
			return arguments;
		}
		String key= method.getMethodDeclaration().getKey();
		if (key == null || key.isBlank()) {
			return arguments;
		}
		String source= expression.toString();
		List<Expression> occurrences= new ArrayList<>();
		location.method().accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode candidate) {
				if (candidate.getNodeType() == expression.getNodeType() && candidate instanceof Expression other
						&& source.equals(other.toString()) && location.equals(resourceLocation(other))) {
					occurrences.add(other);
				}
				return true;
			}
		});
		// An unrelated statement can be inserted before this expression. Preserve its
		// structural role and ordinal among identical occurrences instead of that index.
		// The total count also prevents deleted occurrences from crediting a new one.
		return new String[] { "<unassigned Closeable value>", key, location.path(), source, //$NON-NLS-1$
				occurrences.indexOf(expression) + "/" + occurrences.size() }; //$NON-NLS-1$
	}

	private record ResourceLocation(MethodDeclaration method, String path) {
	}

	private static ResourceLocation resourceLocation(ASTNode node) {
		StringBuilder path= new StringBuilder();
		while (!(node instanceof MethodDeclaration)) {
			ASTNode parent= node.getParent();
			var property= node.getLocationInParent();
			if (parent == null || property == null || (node.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
				return null;
			}
			path.append('/').append(property.getId());
			Object child= parent.getStructuralProperty(property);
			if (property.isChildListProperty()) {
				if (!(child instanceof List<?> children) || children.indexOf(node) < 0) {
					return null;
				}
				if (property != Block.STATEMENTS_PROPERTY) {
					path.append('[').append(children.indexOf(node)).append(']');
				}
			} else if (child != node) {
				return null;
			}
			node= parent;
		}
		if ((node.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
			return null;
		}
		return new ResourceLocation((MethodDeclaration) node, path.toString());
	}

	private static IMethodBinding declaringMethod(CompilationUnit root, IProblem problem) {
		int start= problem.getSourceStart();
		if (start < 0) {
			return null;
		}
		ASTNode node= NodeFinder.perform(root, start, problem.getSourceEnd() - start + 1);
		while (node != null) {
			if (node instanceof MethodDeclaration method) {
				return method.resolveBinding();
			}
			node= node.getParent();
		}
		return null;
	}

	private static boolean hasUniqueMethodName(CompilationUnit root, IProblem problem) {
		IMethodBinding binding= declaringMethod(root, problem);
		if (binding == null || binding.getDeclaringClass() == null) {
			return false;
		}
		return Arrays.stream(binding.getDeclaringClass().getDeclaredMethods())
				.filter(candidate -> candidate.getName().equals(binding.getName())).count() == 1;
	}

	private static String junit4CallbackArguments(IMethodBinding method) {
		if (method == null || method.isRecovered() || method.getDeclaringClass() == null) {
			return null;
		}
		var owner= method.getDeclaringClass();
		var parameters= method.getParameterTypes();
		String name= method.getName();
		if (parameters.length == 0 && ("before".equals(name) || "after".equals(name)) //$NON-NLS-1$ //$NON-NLS-2$
				&& Bindings.findTypeInHierarchy(owner, "org.junit.rules.ExternalResource") != null) { //$NON-NLS-1$
			return Arrays.toString(new String[] { owner.getQualifiedName() + "." + name + "()" }); //$NON-NLS-1$ //$NON-NLS-2$
		}
		String extensionPackage= "org.junit.jupiter.api.extension."; //$NON-NLS-1$
		if (parameters.length != 1 || !(extensionPackage + "ExtensionContext").equals(parameters[0].getQualifiedName())) { //$NON-NLS-1$
			return null;
		}
		String phase= switch (name) {
			case "beforeEach", "beforeAll" -> "before"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			case "afterEach", "afterAll" -> "after"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			default -> null;
		};
		if (phase == null || Bindings.findTypeInHierarchy(owner,
				extensionPackage + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Callback") == null) { //$NON-NLS-1$
			return null;
		}
		return Arrays.toString(new String[] { owner.getQualifiedName() + "." + phase + "()" }); //$NON-NLS-1$ //$NON-NLS-2$
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
			Map<CompilerProblemKey, Long> after= compilerDiagnostics(unit);
			for (Map.Entry<CompilerProblemKey, Long> entry : after.entrySet()) {
				CompilerProblemKey key= entry.getKey();
				long previous= baseline.getOrDefault(key, 0L);
				if (previous == 0 && key.junit4Arguments() != null && !key.arguments().equals(key.junit4Arguments())) {
					// Credit a pre-existing missing super call only across one unambiguous JUnit migration.
					CompilerProblemKey legacy= new CompilerProblemKey(key.error(), key.id(), key.junit4Arguments(), key.junit4Arguments());
					if (baseline.containsKey(legacy) && after.keySet().stream()
							.filter(candidate -> key.junit4Arguments().equals(candidate.junit4Arguments())).count() == 1) {
						previous= baseline.get(legacy);
					}
				}
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
