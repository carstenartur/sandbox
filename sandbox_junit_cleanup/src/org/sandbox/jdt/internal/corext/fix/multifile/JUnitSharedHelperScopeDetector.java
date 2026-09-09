/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_ASSERT;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_ASSUME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/**
 * Discovers narrow source-owned assertion and assumption helper wrappers whose
 * complete caller closure can be delegated to the shared multi-file search.
 *
 * <p>The first supported contract is deliberately conservative: one private
 * helper, or one package-private static helper, with a {@code void} body that
 * consists of exactly one direct JUnit 4 {@code Assert} or {@code Assume}
 * invocation. Public/protected helpers and overridable package-private instance
 * methods remain outside automatic migration. Recursive helper chains and
 * signature changes are later Phase-4 slices.</p>
 */
public final class JUnitSharedHelperScopeDetector {

	private record HelperCandidate(IMethod method, String bindingKey, ICompilationUnit compilationUnit) {
	}

	private JUnitSharedHelperScopeDetector() {
	}

	/**
	 * Finds direct assertion/assumption wrapper methods reachable from the current
	 * scope and returns their declarations as reverse-reference search seeds.
	 */
	public static JUnitScopeCandidateDetector.SearchSeeds findSearchSeeds(IJavaProject project,
			Collection<ICompilationUnit> currentScope, boolean migrateAssertions,
			boolean migrateAssumptions, IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()
				|| !migrateAssertions && !migrateAssumptions) {
			return empty();
		}
		checkCanceled(monitor);
		List<ICompilationUnit> selected= normalize(project, currentScope);
		if (selected.isEmpty()) {
			return empty();
		}

		Map<String, HelperCandidate> possible= new LinkedHashMap<>();
		Map<String, HelperCandidate> verified= new LinkedHashMap<>();
		Map<String, CompilationUnit> selectedRoots= parse(project, selected, monitor);
		for (Map.Entry<String, CompilationUnit> entry : selectedRoots.entrySet()) {
			collect(entry.getValue(), project, migrateAssertions, migrateAssumptions, possible, verified);
		}

		Set<ICompilationUnit> declarationUnits= new LinkedHashSet<>();
		for (HelperCandidate candidate : possible.values()) {
			if (!verified.containsKey(candidate.bindingKey())) {
				declarationUnits.add(candidate.compilationUnit());
			}
		}
		if (!declarationUnits.isEmpty()) {
			Map<String, CompilationUnit> declarationRoots= parse(project, new ArrayList<>(declarationUnits), monitor);
			for (CompilationUnit root : declarationRoots.values()) {
				verifyKnownDeclarations(root, project, migrateAssertions, migrateAssumptions, possible, verified);
			}
		}
		checkCanceled(monitor);
		if (verified.isEmpty()) {
			return empty();
		}

		List<IJavaElement> elements= new ArrayList<>();
		Set<ICompilationUnit> directUnits= new LinkedHashSet<>();
		for (HelperCandidate helper : verified.values()) {
			elements.add(helper.method());
			directUnits.add(helper.compilationUnit());
		}
		return new JUnitScopeCandidateDetector.SearchSeeds(true, true, elements,
				new ArrayList<>(directUnits));
	}

	private static void collect(CompilationUnit root, IJavaProject project, boolean migrateAssertions,
			boolean migrateAssumptions, Map<String, HelperCandidate> possible,
			Map<String, HelperCandidate> verified) {
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding binding= declaration(node.resolveBinding());
				HelperCandidate candidate= candidate(binding, project);
				if (candidate != null && isDirectWrapper(node, migrateAssertions, migrateAssumptions)) {
					verified.putIfAbsent(candidate.bindingKey(), candidate);
				}
				return true;
			}

			@Override
			public boolean visit(MethodInvocation node) {
				HelperCandidate candidate= candidate(declaration(node.resolveMethodBinding()), project);
				if (candidate != null) {
					possible.putIfAbsent(candidate.bindingKey(), candidate);
				}
				return true;
			}
		});
	}

	private static void verifyKnownDeclarations(CompilationUnit root, IJavaProject project,
			boolean migrateAssertions, boolean migrateAssumptions,
			Map<String, HelperCandidate> possible, Map<String, HelperCandidate> verified) {
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding binding= declaration(node.resolveBinding());
				if (binding == null || !possible.containsKey(binding.getKey())) {
					return true;
				}
				HelperCandidate candidate= candidate(binding, project);
				if (candidate != null && isDirectWrapper(node, migrateAssertions, migrateAssumptions)) {
					verified.putIfAbsent(candidate.bindingKey(), candidate);
				}
				return true;
			}
		});
	}

	private static boolean isDirectWrapper(MethodDeclaration method, boolean migrateAssertions,
			boolean migrateAssumptions) {
		IMethodBinding helper= declaration(method.resolveBinding());
		if (helper == null || helper.isConstructor() || method.getBody() == null
				|| !"void".equals(helper.getReturnType().getQualifiedName()) //$NON-NLS-1$
				|| method.getBody().statements().size() != 1
				|| !(method.getBody().statements().get(0) instanceof ExpressionStatement statement)
				|| !(statement.getExpression() instanceof MethodInvocation invocation)) {
			return false;
		}
		IMethodBinding invoked= declaration(invocation.resolveMethodBinding());
		ITypeBinding owner= invoked == null ? null : invoked.getDeclaringClass();
		String ownerName= owner == null ? null : owner.getErasure().getQualifiedName();
		return migrateAssertions && ORG_JUNIT_ASSERT.equals(ownerName)
				|| migrateAssumptions && ORG_JUNIT_ASSUME.equals(ownerName);
	}

	private static HelperCandidate candidate(IMethodBinding binding, IJavaProject project) {
		if (!eligible(binding)) {
			return null;
		}
		IJavaElement javaElement= binding.getJavaElement();
		if (!(javaElement instanceof IMethod method) || !method.exists()
				|| !project.equals(method.getJavaProject())) {
			return null;
		}
		IJavaElement ancestor= method.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (!(ancestor instanceof ICompilationUnit unit) || !unit.exists()) {
			return null;
		}
		return new HelperCandidate(method, binding.getKey(), unit.getPrimary());
	}

	private static boolean eligible(IMethodBinding binding) {
		if (binding == null || binding.isConstructor()) {
			return false;
		}
		int modifiers= binding.getModifiers();
		if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
			return false;
		}
		return Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers);
	}

	private static IMethodBinding declaration(IMethodBinding binding) {
		return binding == null ? null : binding.getMethodDeclaration();
	}

	private static List<ICompilationUnit> normalize(IJavaProject project,
			Collection<ICompilationUnit> units) {
		Map<String, ICompilationUnit> result= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				ICompilationUnit primary= unit.getPrimary();
				result.put(primary.getHandleIdentifier(), primary);
			}
		}
		return new ArrayList<>(result.values());
	}

	private static Map<String, CompilationUnit> parse(IJavaProject project, List<ICompilationUnit> units,
			IProgressMonitor monitor) {
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots.put(source.getPrimary().getHandleIdentifier(), ast);
			}
		}, monitor);
		return roots;
	}

	private static JUnitScopeCandidateDetector.SearchSeeds empty() {
		return new JUnitScopeCandidateDetector.SearchSeeds(false, true, List.of(), List.of());
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
