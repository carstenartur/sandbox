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
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

/** Preserves JUnit 4 lifecycle dispatch through unannotated source overrides. */
public final class InheritedLifecycleMethodRefactorer {

	private static final String DESCRIPTION= "Preserve inherited JUnit lifecycle semantics"; //$NON-NLS-1$

	private InheritedLifecycleMethodRefactorer() {
		throw new UnsupportedOperationException("Utility class"); //$NON-NLS-1$
	}

	/**
	 * Adds a lifecycle annotation to methods that override an inherited lifecycle
	 * method without declaring a lifecycle annotation themselves.
	 *
	 * <p>JUnit 4 invokes the reflected superclass method virtually, so an
	 * unannotated override is executed. Jupiter suppresses an overridden lifecycle
	 * method unless the override is itself annotated. This method is used both to
	 * propagate Jupiter annotations when migrating a class and to add JUnit 4
	 * compatibility annotations when a strict-blocked class must remain on JUnit 4
	 * while its superclass has been migrated to Jupiter. Binding-proven propagation
	 * is therefore required to retain the original dispatch semantics.</p>
	 */
	public static void addInheritedLifecycleOverrides(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations,
			Set<ASTNode> nodesProcessed, String sourceAnnotation, String targetAnnotation) {
		addInheritedLifecycleOverrides(compilationUnit, operations, nodesProcessed,
				Set.of(sourceAnnotation), targetAnnotation);
	}

	/**
	 * Adds {@code targetAnnotation} when an override inherits any accepted source
	 * lifecycle annotation. Accepting both the JUnit 4 and Jupiter forms keeps
	 * the operation correct while a coordinated rewrite still exposes the old
	 * bindings and when a later cleanup starts from already migrated sources.
	 */
	public static void addInheritedLifecycleOverrides(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations,
			Set<ASTNode> nodesProcessed, Set<String> sourceAnnotations, String targetAnnotation) {
		Set<String> acceptedSourceAnnotations= Set.copyOf(sourceAnnotations);
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.isConstructor() || nodesProcessed.contains(node)
						|| hasAnyAnnotation(node, acceptedSourceAnnotations)
						|| hasAnnotation(node, targetAnnotation)) {
					return true;
				}
				IMethodBinding binding= node.resolveBinding();
				if (binding == null || !overridesAnnotatedLifecycle(binding, acceptedSourceAnnotations)) {
					return true;
				}
				nodesProcessed.add(node);
				operations.add(addAnnotation(node, targetAnnotation));
				return true;
			}
		});
	}

	private static boolean overridesAnnotatedLifecycle(IMethodBinding binding,
			Set<String> sourceAnnotations) {
		IMethodBinding declaration= binding.getMethodDeclaration();
		ITypeBinding declaringType= declaration.getDeclaringClass();
		for (ITypeBinding superType= declaringType == null ? null : declaringType.getSuperclass();
				superType != null; superType= superType.getSuperclass()) {
			for (IMethodBinding candidate : superType.getDeclaredMethods()) {
				IMethodBinding candidateDeclaration= candidate.getMethodDeclaration();
				if (declaration.overrides(candidateDeclaration)
						&& hasAnyAnnotation(candidateDeclaration, sourceAnnotations)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasAnyAnnotation(MethodDeclaration method, Set<String> qualifiedNames) {
		for (String qualifiedName : qualifiedNames) {
			if (hasAnnotation(method, qualifiedName)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasAnyAnnotation(IMethodBinding method, Set<String> qualifiedNames) {
		for (String qualifiedName : qualifiedNames) {
			if (hasAnnotation(method, qualifiedName)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasAnnotation(MethodDeclaration method, String qualifiedName) {
		if (hasAnnotation(method.resolveBinding(), qualifiedName)) {
			return true;
		}
		String simpleName= qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
		for (Object modifier : method.modifiers()) {
			if (!(modifier instanceof Annotation annotation)) {
				continue;
			}
			ITypeBinding annotationType= annotation.resolveTypeBinding();
			if (annotationType != null && qualifiedName.equals(annotationType.getQualifiedName())) {
				return true;
			}
			String sourceName= annotation.getTypeName().getFullyQualifiedName();
			if (qualifiedName.equals(sourceName) || simpleName.equals(sourceName)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasAnnotation(IMethodBinding method, String qualifiedName) {
		if (method == null) {
			return false;
		}
		for (IAnnotationBinding annotation : method.getAnnotations()) {
			ITypeBinding annotationType= annotation.getAnnotationType();
			if (annotationType != null && qualifiedName.equals(annotationType.getQualifiedName())) {
				return true;
			}
		}
		return false;
	}

	private static CompilationUnitRewriteOperationWithSourceRange addAnnotation(
			MethodDeclaration method, String targetAnnotation) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(CompilationUnitRewrite cuRewrite,
					LinkedProposalModelCore linkedModel) {
				TextEditGroup group= createTextEditGroup(DESCRIPTION, cuRewrite);
				ASTRewrite rewrite= cuRewrite.getASTRewrite();
				AST ast= cuRewrite.getRoot().getAST();
				ImportRewrite imports= cuRewrite.getImportRewrite();
				MarkerAnnotation annotation= ast.newMarkerAnnotation();
				annotation.setTypeName(ast.newName(imports.addImport(targetAnnotation)));

				Annotation lastAnnotation= null;
				for (Object modifier : method.modifiers()) {
					if (modifier instanceof Annotation existing) {
						lastAnnotation= existing;
					}
				}
				ListRewrite modifiers= rewrite.getListRewrite(method,
						MethodDeclaration.MODIFIERS2_PROPERTY);
				if (lastAnnotation == null) {
					modifiers.insertFirst(annotation, group);
				} else {
					modifiers.insertAfter(annotation, lastAnnotation, group);
				}
			}
		};
	}
}
