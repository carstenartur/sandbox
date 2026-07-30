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
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

/** Applies non-textual structural edits authorized and resolved by a semantic planner. */
public final class PlannedStructuralRewriteOperation extends CompilationUnitRewriteOperationWithSourceRange {

	/** One annotation removal from a resolved declaration. */
	public record AnnotationRemoval(BodyDeclaration declaration, String annotationName) {
		public AnnotationRemoval {
			Objects.requireNonNull(declaration);
			Objects.requireNonNull(annotationName);
		}
	}

	/** Adds a single-member annotation whose value is an integer literal. */
	public record IntegerAnnotationAddition(BodyDeclaration declaration, String annotationName, int value) {
		public IntegerAnnotationAddition {
			Objects.requireNonNull(declaration);
			Objects.requireNonNull(annotationName);
		}
	}

	/** Adds a single-member annotation whose value is a class literal. */
	public record TypeLiteralAnnotationAddition(BodyDeclaration declaration, String annotationName,
			String valueTypeName) {
		public TypeLiteralAnnotationAddition {
			Objects.requireNonNull(declaration);
			Objects.requireNonNull(annotationName);
			Objects.requireNonNull(valueTypeName);
		}
	}

	private final TypeDeclaration type;
	private final String supertypeToRemove;
	private final List<AnnotationRemoval> annotationRemovals;
	private final List<IntegerAnnotationAddition> integerAnnotationAdditions;
	private final List<TypeLiteralAnnotationAddition> typeLiteralAnnotationAdditions;
	private final String description;

	public PlannedStructuralRewriteOperation(TypeDeclaration type, String supertypeToRemove,
			List<AnnotationRemoval> annotationRemovals, String description) {
		this(type, supertypeToRemove, annotationRemovals, List.of(), List.of(), description);
	}

	public PlannedStructuralRewriteOperation(TypeDeclaration type, String supertypeToRemove,
			List<AnnotationRemoval> annotationRemovals,
			List<IntegerAnnotationAddition> integerAnnotationAdditions,
			List<TypeLiteralAnnotationAddition> typeLiteralAnnotationAdditions, String description) {
		this.type= type;
		this.supertypeToRemove= supertypeToRemove;
		this.annotationRemovals= List.copyOf(annotationRemovals == null ? List.of() : annotationRemovals);
		this.integerAnnotationAdditions= List.copyOf(
				integerAnnotationAdditions == null ? List.of() : integerAnnotationAdditions);
		this.typeLiteralAnnotationAdditions= List.copyOf(
				typeLiteralAnnotationAdditions == null ? List.of() : typeLiteralAnnotationAdditions);
		this.description= description == null ? "Apply planned structural rewrite" : description; //$NON-NLS-1$
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		TextEditGroup group= createTextEditGroup(description, cuRewrite);
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		if (supertypeToRemove != null) {
			removeSupertype(rewrite, cuRewrite, group);
		}
		for (AnnotationRemoval removal : annotationRemovals) {
			removeAnnotation(removal, rewrite, group);
		}
		for (IntegerAnnotationAddition addition : integerAnnotationAdditions) {
			addIntegerAnnotation(addition, rewrite, cuRewrite, group);
		}
		for (TypeLiteralAnnotationAddition addition : typeLiteralAnnotationAdditions) {
			addTypeLiteralAnnotation(addition, rewrite, cuRewrite, group);
		}
	}

	private void removeSupertype(ASTRewrite rewrite, CompilationUnitRewrite cuRewrite, TextEditGroup group)
			throws CoreException {
		if (type == null || type.getSuperclassType() == null) {
			throw failure("The planned supertype removal target is missing"); //$NON-NLS-1$
		}
		Type superclass= type.getSuperclassType();
		ITypeBinding binding= superclass.resolveBinding();
		String actual= binding == null ? null : binding.getErasure().getQualifiedName();
		if (!supertypeToRemove.equals(actual)) {
			throw failure("Expected supertype " + supertypeToRemove + " but resolved " + actual); //$NON-NLS-1$ //$NON-NLS-2$
		}
		rewrite.remove(superclass, group);
		cuRewrite.getImportRemover().registerRemovedNode(superclass);
		cuRewrite.getImportRemover().applyRemoves(cuRewrite.getImportRewrite());
	}

	private static void removeAnnotation(AnnotationRemoval removal, ASTRewrite rewrite, TextEditGroup group)
			throws CoreException {
		String expected= removal.annotationName();
		String expectedSimple= simpleName(expected);
		for (Object modifier : removal.declaration().modifiers()) {
			if (modifier instanceof Annotation annotation) {
				String actual= annotationName(annotation);
				if (expected.equals(actual) || expectedSimple.equals(actual)) {
					rewrite.remove(annotation, group);
					return;
				}
			}
		}
		throw failure("The planned annotation removal target " + expected + " is missing"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void addIntegerAnnotation(IntegerAnnotationAddition addition, ASTRewrite rewrite,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		ensureAnnotationMissing(addition.declaration(), addition.annotationName());
		AST ast= cuRewrite.getRoot().getAST();
		SingleMemberAnnotation annotation= ast.newSingleMemberAnnotation();
		annotation.setTypeName(ast.newName(cuRewrite.getImportRewrite().addImport(addition.annotationName())));
		annotation.setValue(ast.newNumberLiteral(Integer.toString(addition.value())));
		modifiers(rewrite, addition.declaration()).insertFirst(annotation, group);
	}

	private static void addTypeLiteralAnnotation(TypeLiteralAnnotationAddition addition, ASTRewrite rewrite,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		ensureAnnotationMissing(addition.declaration(), addition.annotationName());
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite imports= cuRewrite.getImportRewrite();
		SingleMemberAnnotation annotation= ast.newSingleMemberAnnotation();
		annotation.setTypeName(ast.newName(imports.addImport(addition.annotationName())));
		TypeLiteral literal= ast.newTypeLiteral();
		literal.setType(ast.newSimpleType(ast.newName(imports.addImport(addition.valueTypeName()))));
		annotation.setValue(literal);
		modifiers(rewrite, addition.declaration()).insertFirst(annotation, group);
	}

	private static ListRewrite modifiers(ASTRewrite rewrite, BodyDeclaration declaration) throws CoreException {
		if (declaration instanceof TypeDeclaration typeDeclaration) {
			return rewrite.getListRewrite(typeDeclaration, TypeDeclaration.MODIFIERS2_PROPERTY);
		}
		if (declaration instanceof MethodDeclaration methodDeclaration) {
			return rewrite.getListRewrite(methodDeclaration, MethodDeclaration.MODIFIERS2_PROPERTY);
		}
		throw failure("Unsupported planned annotation target " + declaration.getClass().getName()); //$NON-NLS-1$
	}

	private static void ensureAnnotationMissing(BodyDeclaration declaration, String expected) throws CoreException {
		String expectedSimple= simpleName(expected);
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				String actual= annotationName(annotation);
				if (expected.equals(actual) || expectedSimple.equals(actual)) {
					throw failure("The planned annotation addition target already contains " + expected); //$NON-NLS-1$
				}
			}
		}
	}

	private static String annotationName(Annotation annotation) {
		ITypeBinding binding= annotation.resolveTypeBinding();
		return binding == null ? annotation.getTypeName().getFullyQualifiedName() : binding.getQualifiedName();
	}

	private static String simpleName(String name) {
		return name.substring(name.lastIndexOf('.') + 1);
	}

	private static CoreException failure(String message) {
		return new CoreException(new Status(IStatus.ERROR, "sandbox_common", message)); //$NON-NLS-1$
	}
}
