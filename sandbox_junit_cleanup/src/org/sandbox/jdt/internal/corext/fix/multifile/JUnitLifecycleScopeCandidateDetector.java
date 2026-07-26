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

/**
 * Finds source type hierarchies whose JUnit 4 lifecycle annotations must be
 * migrated as one closed cleanup scope.
 */
public final class JUnitLifecycleScopeCandidateDetector {

	private static final Set<String> LIFECYCLE_ANNOTATIONS= Set.of(
			"org.junit.Before", //$NON-NLS-1$
			"org.junit.After", //$NON-NLS-1$
			"org.junit.BeforeClass", //$NON-NLS-1$
			"org.junit.AfterClass"); //$NON-NLS-1$

	private static final Set<String> LIFECYCLE_SIMPLE_NAMES= Set.of(
			"Before", "After", "BeforeClass", "AfterClass"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	private record LifecycleSyntax(boolean candidateFound, boolean complete) {
	}

	/** Types whose declarations and references define the required hierarchy closure. */
	public record SearchSeeds(boolean candidateFound, boolean complete, List<IJavaElement> elements) {
		public SearchSeeds {
			elements= List.copyOf(elements);
		}
	}

	private JUnitLifecycleScopeCandidateDetector() {
	}

	/** Finds selected lifecycle types plus all source supertypes declaring lifecycle methods. */
	public static SearchSeeds findSearchSeeds(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()) {
			return new SearchSeeds(false, true, List.of());
		}
		checkCanceled(monitor);
		Set<ICompilationUnit> units= new LinkedHashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				units.add(unit.getPrimary());
			}
		}
		if (units.isEmpty()) {
			return new SearchSeeds(false, true, List.of());
		}

		boolean[] candidateFound= { false };
		boolean[] complete= { true };
		Set<IJavaElement> elements= new LinkedHashSet<>();
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
						checkCanceled(monitor);
						ITypeBinding binding= node.resolveBinding();
						LifecycleSyntax syntax= inspectLifecycleSyntax(node);
						boolean bindingLifecycle= hierarchyDeclaresLifecycle(binding, new LinkedHashSet<>());
						if (!syntax.candidateFound() && !bindingLifecycle) {
							return true;
						}
						candidateFound[0]= true;
						complete[0]&= syntax.complete();
						if (binding == null) {
							complete[0]= false;
							return true;
						}
						complete[0]&= addHierarchyElements(binding, elements, new LinkedHashSet<>());
						return true;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return new SearchSeeds(candidateFound[0], complete[0], new ArrayList<>(elements));
	}

	private static LifecycleSyntax inspectLifecycleSyntax(TypeDeclaration type) {
		boolean candidate= false;
		boolean complete= true;
		for (MethodDeclaration method : type.getMethods()) {
			for (Object modifier : method.modifiers()) {
				if (!(modifier instanceof Annotation annotation)) {
					continue;
				}
				ITypeBinding annotationBinding= annotation.resolveTypeBinding();
				if (annotationBinding != null && LIFECYCLE_ANNOTATIONS.contains(annotationBinding.getQualifiedName())) {
					candidate= true;
					continue;
				}
				String name= annotation.getTypeName().getFullyQualifiedName();
				if (LIFECYCLE_SIMPLE_NAMES.contains(simpleName(name))) {
					candidate= true;
					complete&= annotationBinding != null;
				}
			}
		}
		return new LifecycleSyntax(candidate, complete);
	}

	private static boolean hierarchyDeclaresLifecycle(ITypeBinding binding, Set<String> visited) {
		if (binding == null) {
			return false;
		}
		ITypeBinding declaration= binding.getErasure().getTypeDeclaration();
		String key= declaration.getKey();
		if (key == null || !visited.add(key)) {
			return false;
		}
		for (IMethodBinding method : declaration.getDeclaredMethods()) {
			for (IAnnotationBinding annotation : method.getAnnotations()) {
				ITypeBinding type= annotation.getAnnotationType();
				if (type != null && LIFECYCLE_ANNOTATIONS.contains(type.getQualifiedName())) {
					return true;
				}
			}
		}
		if (hierarchyDeclaresLifecycle(declaration.getSuperclass(), visited)) {
			return true;
		}
		for (ITypeBinding interfaceBinding : declaration.getInterfaces()) {
			if (hierarchyDeclaresLifecycle(interfaceBinding, visited)) {
				return true;
			}
		}
		return false;
	}

	private static boolean addHierarchyElements(ITypeBinding binding, Set<IJavaElement> elements,
			Set<String> visited) {
		if (binding == null) {
			return true;
		}
		ITypeBinding declaration= binding.getErasure().getTypeDeclaration();
		String key= declaration.getKey();
		if (key == null || !visited.add(key)) {
			return key != null;
		}
		boolean complete= addJavaElement(declaration, elements);
		ITypeBinding superclass= declaration.getSuperclass();
		if (superclass != null && sourceHierarchyType(superclass)) {
			complete&= addHierarchyElements(superclass, elements, visited);
		}
		for (ITypeBinding interfaceBinding : declaration.getInterfaces()) {
			if (sourceHierarchyType(interfaceBinding)) {
				complete&= addHierarchyElements(interfaceBinding, elements, visited);
			}
		}
		return complete;
	}

	private static boolean sourceHierarchyType(ITypeBinding binding) {
		if (binding == null) {
			return false;
		}
		IJavaElement element= binding.getErasure().getJavaElement();
		return element != null && element.exists() && element.getAncestor(IJavaElement.COMPILATION_UNIT) != null;
	}

	private static boolean addJavaElement(ITypeBinding binding, Set<IJavaElement> elements) {
		IJavaElement element= binding == null ? null : binding.getErasure().getJavaElement();
		if (element == null || !element.exists()) {
			return false;
		}
		elements.add(element);
		return true;
	}

	private static String simpleName(String name) {
		int separator= name.lastIndexOf('.');
		return separator < 0 ? name : name.substring(separator + 1);
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
