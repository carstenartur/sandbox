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
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/** Finds the complete source hierarchy coupled by JUnit lifecycle dispatch. */
public final class JUnitLifecycleScopeDetector {

	private JUnitLifecycleScopeDetector() {
	}

	/**
	 * Finds the highest lifecycle-declaring source type visible from the selection
	 * and every source subtype. Both JUnit 4 and Jupiter annotation names may be
	 * supplied, so an earlier migration cannot erase the edge used by a later pass.
	 */
	public static JUnitScopeCandidateDetector.SearchSeeds findSearchSeeds(IJavaProject project,
			Collection<ICompilationUnit> currentScope, Set<String> lifecycleAnnotations,
			IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()
				|| lifecycleAnnotations == null || lifecycleAnnotations.isEmpty()) {
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

		Set<String> acceptedAnnotations= Set.copyOf(lifecycleAnnotations);
		boolean[] candidateFound= { false };
		boolean[] complete= { true };
		Set<ICompilationUnit> directUnits= new LinkedHashSet<>();
		Set<String> processedRoots= new LinkedHashSet<>();
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
						ITypeBinding rootBinding= lifecycleRoot(binding, acceptedAnnotations);
						if (rootBinding == null) {
							if (binding == null && hasSyntacticLifecycle(node, acceptedAnnotations)) {
								candidateFound[0]= true;
								complete[0]= false;
							}
							return true;
						}
						candidateFound[0]= true;
						var rootElement= rootBinding.getErasure().getJavaElement();
						if (!(rootElement instanceof IType rootType) || rootType.getCompilationUnit() == null) {
							complete[0]= false;
							return false;
						}
						if (!processedRoots.add(rootType.getHandleIdentifier())) {
							return false;
						}
						try {
							ITypeHierarchy hierarchy= newTypeHierarchy(rootType, monitor);
							complete[0]&= addType(rootType, directUnits);
							for (IType subtype : hierarchy.getAllSubtypes(rootType)) {
								ICompilationUnit subtypeUnit= subtype.getCompilationUnit();
								if (subtypeUnit == null || !subtypeUnit.exists()) {
									complete[0]= false;
								} else {
									complete[0]&= addType(subtype, directUnits);
								}
							}
						} catch (JavaModelException e) {
							complete[0]= false;
						}
						return false;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return new JUnitScopeCandidateDetector.SearchSeeds(candidateFound[0], complete[0],
				List.of(), new ArrayList<>(directUnits));
	}

	static ITypeHierarchy newTypeHierarchy(IType rootType, IProgressMonitor monitor)
			throws JavaModelException {
		return rootType.newTypeHierarchy(monitor);
	}

	private static ITypeBinding lifecycleRoot(ITypeBinding binding, Set<String> annotations) {
		ITypeBinding result= null;
		for (ITypeBinding current= binding; current != null; current= current.getSuperclass()) {
			if (declaresLifecycle(current, annotations)) {
				result= current;
			}
		}
		return result;
	}

	private static boolean declaresLifecycle(ITypeBinding type, Set<String> annotations) {
		for (IMethodBinding method : type.getDeclaredMethods()) {
			for (IAnnotationBinding annotation : method.getAnnotations()) {
				ITypeBinding annotationType= annotation.getAnnotationType();
				if (annotationType != null && annotations.contains(annotationType.getQualifiedName())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasSyntacticLifecycle(TypeDeclaration type, Set<String> annotations) {
		for (MethodDeclaration method : type.getMethods()) {
			for (Object modifier : method.modifiers()) {
				if (!(modifier instanceof Annotation annotation)) {
					continue;
				}
				String sourceName= annotation.getTypeName().getFullyQualifiedName();
				for (String qualifiedName : annotations) {
					String simpleName= qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
					if (qualifiedName.equals(sourceName) || simpleName.equals(sourceName)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean addType(IType type, Set<ICompilationUnit> units) {
		if (type == null || !type.exists()) {
			return false;
		}
		ICompilationUnit unit= type.getCompilationUnit();
		if (unit == null || !unit.exists()) {
			return false;
		}
		units.add(unit.getPrimary());
		return true;
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
