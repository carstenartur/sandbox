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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport.JUNIT3_TEST_CASE;

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
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/** Finds the source closure needed before any ordinary JUnit 3 hierarchy rewrite. */
public final class JUnit3HierarchyScopeDetector {

	private JUnit3HierarchyScopeDetector() {
	}

	public static JUnitScopeCandidateDetector.SearchSeeds findSearchSeeds(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()) {
			return new JUnitScopeCandidateDetector.SearchSeeds(false, true, List.of(), List.of());
		}
		checkCanceled(monitor);
		Set<ICompilationUnit> units= new LinkedHashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				units.add(unit.getPrimary());
			}
		}
		if (units.isEmpty()) {
			return new JUnitScopeCandidateDetector.SearchSeeds(false, true, List.of(), List.of());
		}

		boolean[] candidateFound= { false };
		boolean[] complete= { true };
		Set<IJavaElement> elements= new LinkedHashSet<>();
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
						if (JdtCoreHarnessScopeDetector.inheritsJdtCoreHarness(binding)) {
							return true;
						}
						if (!isJUnit3Type(binding)) {
							return true;
						}
						IType root= findSourceRoot(binding);
						if (root == null) {
							candidateFound[0]= true;
							complete[0]= false;
							return false;
						}
						try {
							ITypeHierarchy hierarchy= root.newTypeHierarchy(null);
							List<IType> sourceSubtypes= new ArrayList<>();
							boolean binarySubtypeFound= false;
							for (IType subtype : hierarchy.getAllSubtypes(root)) {
								ICompilationUnit subtypeUnit= subtype.getCompilationUnit();
								if (subtypeUnit == null || !subtypeUnit.exists()) {
									binarySubtypeFound= true;
								} else {
									sourceSubtypes.add(subtype);
								}
							}
							if (sourceSubtypes.isEmpty()) {
								return false;
							}
							candidateFound[0]= true;
							complete[0]&= addType(root, elements, directUnits);
							for (IType subtype : sourceSubtypes) {
								complete[0]&= addType(subtype, elements, directUnits);
							}
							complete[0]&= !binarySubtypeFound;
						} catch (JavaModelException e) {
							candidateFound[0]= true;
							complete[0]= false;
						}
						return false;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return new JUnitScopeCandidateDetector.SearchSeeds(candidateFound[0], complete[0],
				new ArrayList<>(elements), new ArrayList<>(directUnits));
	}

	private static boolean isJUnit3Type(ITypeBinding binding) {
		ITypeBinding current= binding;
		while (current != null) {
			if (JUNIT3_TEST_CASE.equals(current.getErasure().getQualifiedName())) {
				return true;
			}
			current= current.getSuperclass();
		}
		return false;
	}

	private static IType findSourceRoot(ITypeBinding binding) {
		ITypeBinding current= binding;
		while (current != null && current.getSuperclass() != null) {
			ITypeBinding superclass= current.getSuperclass();
			if (JUNIT3_TEST_CASE.equals(superclass.getErasure().getQualifiedName())) {
				IJavaElement element= current.getErasure().getJavaElement();
				return element instanceof IType type && type.getCompilationUnit() != null ? type : null;
			}
			current= superclass;
		}
		return null;
	}

	private static boolean addType(IType type, Set<IJavaElement> elements, Set<ICompilationUnit> units) {
		if (type == null || !type.exists()) {
			return false;
		}
		ICompilationUnit unit= type.getCompilationUnit();
		if (unit == null || !unit.exists()) {
			return false;
		}
		elements.add(type);
		units.add(unit.getPrimary());
		return true;
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
