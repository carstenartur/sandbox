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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/** Finds the exact source chain needed to classify a selected JDT Core harness family. */
public final class JdtCoreHarnessScopeDetector {

	private JdtCoreHarnessScopeDetector() {
	}

	public static JUnitScopeCandidateDetector.SearchSeeds findSearchSeeds(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()) {
			return empty();
		}
		checkCanceled(monitor);
		Set<ICompilationUnit> units= new LinkedHashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				units.add(unit.getPrimary());
			}
		}
		if (units.isEmpty()) {
			return empty();
		}

		boolean[] candidateFound= { false };
		boolean[] complete= { true };
		Set<IJavaElement> referenceTargets= new LinkedHashSet<>();
		Set<ICompilationUnit> directUnits= new LinkedHashSet<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration node) {
						ITypeBinding binding= node.resolveBinding();
						if (!inheritsJdtCoreHarness(binding)) {
							return true;
						}
						candidateFound[0]= true;
						IJavaElement selectedElement= binding == null ? null : binding.getErasure().getJavaElement();
						if (selectedElement instanceof IType selectedType && selectedType.getCompilationUnit() != null) {
							referenceTargets.add(selectedType);
							directUnits.add(selectedType.getCompilationUnit().getPrimary());
						} else {
							complete[0]= false;
						}
						complete[0]&= addSourceSupertypeChain(binding, directUnits);
						return false;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return new JUnitScopeCandidateDetector.SearchSeeds(candidateFound[0], complete[0],
				new ArrayList<>(referenceTargets), new ArrayList<>(directUnits));
	}

	static boolean inheritsJdtCoreHarness(ITypeBinding binding) {
		ITypeBinding current= binding;
		while (current != null) {
			if (JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(current.getErasure().getQualifiedName())) {
				return true;
			}
			current= current.getSuperclass();
		}
		return false;
	}

	private static boolean addSourceSupertypeChain(ITypeBinding binding, Set<ICompilationUnit> units) {
		boolean complete= true;
		ITypeBinding current= binding;
		boolean harnessReached= false;
		while (current != null) {
			IJavaElement element= current.getErasure().getJavaElement();
			if (element instanceof IType type) {
				ICompilationUnit unit= type.getCompilationUnit();
				if (unit != null && unit.exists()) {
					units.add(unit.getPrimary());
				} else if (JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(
						current.getErasure().getQualifiedName())) {
					complete= false;
				}
			} else if (JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(
					current.getErasure().getQualifiedName())) {
				complete= false;
			}
			if (JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(current.getErasure().getQualifiedName())) {
				harnessReached= true;
				break;
			}
			current= current.getSuperclass();
		}
		return complete && harnessReached;
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
