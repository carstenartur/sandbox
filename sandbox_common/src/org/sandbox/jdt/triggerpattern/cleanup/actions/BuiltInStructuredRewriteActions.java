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
package org.sandbox.jdt.triggerpattern.cleanup.actions;

import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.sandbox.jdt.triggerpattern.api.RewriteActionValue;
import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;

/** Canonical built-in runtime handlers for the standard action catalog. */
final class BuiltInStructuredRewriteActions {

	private BuiltInStructuredRewriteActions() {
	}

	static void registerAll(StructuredRewriteActionRegistry registry) {
		registry.register("addAnnotation", BuiltInStructuredRewriteActions::addAnnotation); //$NON-NLS-1$
		registry.register("removeAnnotation", BuiltInStructuredRewriteActions::removeAnnotation); //$NON-NLS-1$
		registry.register("addModifier", BuiltInStructuredRewriteActions::addModifier); //$NON-NLS-1$
		registry.register("removeModifier", BuiltInStructuredRewriteActions::removeModifier); //$NON-NLS-1$
		registry.register("removeSupertype", BuiltInStructuredRewriteActions::removeSupertype); //$NON-NLS-1$
		registry.register("replaceSupertype", BuiltInStructuredRewriteActions::replaceSupertype); //$NON-NLS-1$
		registry.register("removeDeclaration", BuiltInStructuredRewriteActions::removeDeclaration); //$NON-NLS-1$
		registry.register("qualifyInvocation", BuiltInStructuredRewriteActions::qualifyInvocation); //$NON-NLS-1$
	}

	private static void addAnnotation(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		BodyDeclaration declaration= declarationTarget(context,
				action.requiredArgument("target")); //$NON-NLS-1$
		String annotationName= context.resolveString(action.requiredArgument("annotation")); //$NON-NLS-1$
		ensureAnnotationMissing(declaration, annotationName, context);
		AST ast= context.ast();
		String importedName= context.cuRewrite().getImportRewrite().addImport(annotationName);
		Annotation annotation;
		RewriteActionValue value= action.arguments().get("value"); //$NON-NLS-1$
		if (value == null) {
			MarkerAnnotation marker= ast.newMarkerAnnotation();
			marker.setTypeName(ast.newName(importedName));
			annotation= marker;
		} else {
			SingleMemberAnnotation single= ast.newSingleMemberAnnotation();
			single.setTypeName(ast.newName(importedName));
			single.setValue(context.createExpression(value));
			annotation= single;
		}
		modifiers(context.cuRewrite().getASTRewrite(), declaration, context)
				.insertFirst(annotation, context.editGroup());
	}

	private static void removeAnnotation(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		BodyDeclaration declaration= declarationTarget(context,
				action.requiredArgument("target")); //$NON-NLS-1$
		String expected= context.resolveString(action.requiredArgument("annotation")); //$NON-NLS-1$
		String expectedSimple= simpleName(expected);
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				String actual= annotationName(annotation);
				if (expected.equals(actual) || expectedSimple.equals(simpleName(actual))) {
					context.cuRewrite().getASTRewrite().remove(annotation, context.editGroup());
					context.cuRewrite().getImportRemover().registerRemovedNode(annotation);
					context.cuRewrite().getImportRemover()
							.applyRemoves(context.cuRewrite().getImportRewrite());
					return;
				}
			}
		}
		throw context.failure("Structured action cannot find annotation " + expected); //$NON-NLS-1$
	}

	private static void addModifier(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		BodyDeclaration declaration= declarationTarget(context,
				action.requiredArgument("target")); //$NON-NLS-1$
		Modifier.ModifierKeyword keyword= modifierKeyword(
				context.resolveString(action.requiredArgument("modifier")), context); //$NON-NLS-1$
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Modifier existing && existing.getKeyword() == keyword) {
				throw context.failure("Structured action modifier already exists: " + keyword); //$NON-NLS-1$
			}
		}
		modifiers(context.cuRewrite().getASTRewrite(), declaration, context)
				.insertLast(context.ast().newModifier(keyword), context.editGroup());
	}

	private static void removeModifier(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		BodyDeclaration declaration= declarationTarget(context,
				action.requiredArgument("target")); //$NON-NLS-1$
		Modifier.ModifierKeyword keyword= modifierKeyword(
				context.resolveString(action.requiredArgument("modifier")), context); //$NON-NLS-1$
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Modifier existing && existing.getKeyword() == keyword) {
				context.cuRewrite().getASTRewrite().remove(existing, context.editGroup());
				return;
			}
		}
		throw context.failure("Structured action cannot find modifier " + keyword); //$NON-NLS-1$
	}

	private static void removeSupertype(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		ASTNode target= context.resolveAuthorizedNode(action.requiredArgument("target")); //$NON-NLS-1$
		String expected= context.resolveString(action.requiredArgument("type")); //$NON-NLS-1$
		rewriteSupertype(target, expected, null, context);
	}

	private static void replaceSupertype(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		ASTNode target= context.resolveAuthorizedNode(action.requiredArgument("target")); //$NON-NLS-1$
		String expected= context.resolveString(action.requiredArgument("type")); //$NON-NLS-1$
		String replacement= context.resolveString(action.requiredArgument("replacement")); //$NON-NLS-1$
		rewriteSupertype(target, expected, replacement, context);
	}

	private static void removeDeclaration(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		ASTNode target= context.resolveAuthorizedNode(action.requiredArgument("target")); //$NON-NLS-1$
		ASTNode removable= exactDeclarationTarget(target, context);
		ASTRewrite rewrite= context.cuRewrite().getASTRewrite();
		if (removable instanceof VariableDeclarationFragment fragment
				&& fragment.getParent() instanceof FieldDeclaration field
				&& field.fragments().size() > 1) {
			rewrite.getListRewrite(field, FieldDeclaration.FRAGMENTS_PROPERTY)
					.remove(fragment, context.editGroup());
		} else {
			rewrite.remove(removable, context.editGroup());
			context.cuRewrite().getImportRemover().registerRemovedNode(removable);
			context.cuRewrite().getImportRemover()
					.applyRemoves(context.cuRewrite().getImportRewrite());
		}
	}

	private static void qualifyInvocation(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		ASTNode target= context.resolveAuthorizedNode(action.requiredArgument("target")); //$NON-NLS-1$
		MethodInvocation invocation= invocationTarget(target, context);
		IMethodBinding binding= invocation.resolveMethodBinding();
		if (binding == null || !Modifier.isStatic(binding.getModifiers())) {
			throw context.failure("qualifyInvocation requires one resolved static method"); //$NON-NLS-1$
		}
		if (invocation.getExpression() != null) {
			throw context.failure("qualifyInvocation target is already qualified"); //$NON-NLS-1$
		}
		String owner= context.resolveString(action.requiredArgument("owner")); //$NON-NLS-1$
		String imported= context.cuRewrite().getImportRewrite().addImport(owner);
		context.cuRewrite().getASTRewrite().set(invocation, MethodInvocation.EXPRESSION_PROPERTY,
				context.ast().newName(imported), context.editGroup());
	}

	private static BodyDeclaration declarationTarget(StructuredRewriteActionContext context,
			RewriteActionValue value) throws CoreException {
		ASTNode current= context.resolveAuthorizedNode(value);
		while (current != null) {
			if (current instanceof BodyDeclaration declaration) {
				if (declaration instanceof FieldDeclaration field && field.fragments().size() != 1) {
					throw context.failure(
							"Declaration-wide action is ambiguous for a multi-fragment field"); //$NON-NLS-1$
				}
				return declaration;
			}
			current= current.getParent();
		}
		throw context.failure("Structured action target is not a Java declaration"); //$NON-NLS-1$
	}

	private static ASTNode exactDeclarationTarget(ASTNode target,
			StructuredRewriteActionContext context) throws CoreException {
		ASTNode current= target;
		while (current instanceof SimpleName) {
			current= current.getParent();
		}
		if (current instanceof VariableDeclarationFragment || current instanceof BodyDeclaration) {
			return current;
		}
		while (current != null && !(current instanceof BodyDeclaration)) {
			current= current.getParent();
		}
		if (current == null) {
			throw context.failure("removeDeclaration target is not a removable declaration"); //$NON-NLS-1$
		}
		return current;
	}

	private static MethodInvocation invocationTarget(ASTNode target,
			StructuredRewriteActionContext context) throws CoreException {
		if (target instanceof MethodInvocation invocation) {
			return invocation;
		}
		if (target instanceof SimpleName name && name.getParent() instanceof MethodInvocation invocation
				&& invocation.getName() == name) {
			return invocation;
		}
		throw context.failure("Structured action target is not an exact method invocation"); //$NON-NLS-1$
	}

	private static void rewriteSupertype(ASTNode target, String expected, String replacement,
			StructuredRewriteActionContext context) throws CoreException {
		ASTNode current= target;
		while (current != null && !(current instanceof TypeDeclaration)
				&& !(current instanceof EnumDeclaration) && !(current instanceof RecordDeclaration)) {
			current= current.getParent();
		}
		if (current instanceof TypeDeclaration type) {
			Type superclass= type.getSuperclassType();
			if (superclass != null && matchesType(superclass, expected)) {
				rewriteType(superclass, replacement, context);
				return;
			}
			if (rewriteInterface(type.superInterfaceTypes(), expected, replacement, context)) {
				return;
			}
		} else if (current instanceof EnumDeclaration type) {
			if (rewriteInterface(type.superInterfaceTypes(), expected, replacement, context)) {
				return;
			}
		} else if (current instanceof RecordDeclaration type) {
			if (rewriteInterface(type.superInterfaceTypes(), expected, replacement, context)) {
				return;
			}
		}
		throw context.failure("Structured action cannot find supertype " + expected); //$NON-NLS-1$
	}

	private static boolean rewriteInterface(List<?> interfaces, String expected, String replacement,
			StructuredRewriteActionContext context) throws CoreException {
		for (Object candidate : interfaces) {
			if (candidate instanceof Type type && matchesType(type, expected)) {
				rewriteType(type, replacement, context);
				return true;
			}
		}
		return false;
	}

	private static void rewriteType(Type existing, String replacement,
			StructuredRewriteActionContext context) {
		if (replacement == null) {
			context.cuRewrite().getASTRewrite().remove(existing, context.editGroup());
		} else {
			String imported= context.cuRewrite().getImportRewrite().addImport(replacement);
			Type newType= context.ast().newSimpleType(context.ast().newName(imported));
			context.cuRewrite().getASTRewrite().replace(existing, newType, context.editGroup());
		}
		context.cuRewrite().getImportRemover().registerRemovedNode(existing);
		context.cuRewrite().getImportRemover().applyRemoves(context.cuRewrite().getImportRewrite());
	}

	private static boolean matchesType(Type type, String expected) {
		ITypeBinding binding= type.resolveBinding();
		if (binding == null) {
			return false;
		}
		ITypeBinding erasure= binding.getErasure();
		ITypeBinding declaration= (erasure == null ? binding : erasure).getTypeDeclaration();
		return declaration != null && (expected.equals(declaration.getQualifiedName())
				|| expected.equals(declaration.getName()));
	}

	private static ListRewrite modifiers(ASTRewrite rewrite, BodyDeclaration declaration,
			StructuredRewriteActionContext context) throws CoreException {
		ChildListPropertyDescriptor property;
		if (declaration instanceof TypeDeclaration) {
			property= TypeDeclaration.MODIFIERS2_PROPERTY;
		} else if (declaration instanceof EnumDeclaration) {
			property= EnumDeclaration.MODIFIERS2_PROPERTY;
		} else if (declaration instanceof AnnotationTypeDeclaration) {
			property= AnnotationTypeDeclaration.MODIFIERS2_PROPERTY;
		} else if (declaration instanceof RecordDeclaration) {
			property= RecordDeclaration.MODIFIERS2_PROPERTY;
		} else if (declaration instanceof MethodDeclaration) {
			property= MethodDeclaration.MODIFIERS2_PROPERTY;
		} else if (declaration instanceof FieldDeclaration) {
			property= FieldDeclaration.MODIFIERS2_PROPERTY;
		} else {
			throw context.failure("Unsupported declaration modifiers: " + declaration.getClass().getName()); //$NON-NLS-1$
		}
		return rewrite.getListRewrite(declaration, property);
	}

	private static Modifier.ModifierKeyword modifierKeyword(String value,
			StructuredRewriteActionContext context) throws CoreException {
		Modifier.ModifierKeyword keyword= Modifier.ModifierKeyword.toKeyword(value);
		if (keyword == null) {
			throw context.failure("Unknown Java modifier " + value); //$NON-NLS-1$
		}
		return keyword;
	}

	private static void ensureAnnotationMissing(BodyDeclaration declaration, String expected,
			StructuredRewriteActionContext context) throws CoreException {
		String expectedSimple= simpleName(expected);
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				String actual= annotationName(annotation);
				if (expected.equals(actual) || expectedSimple.equals(simpleName(actual))) {
					throw context.failure("Structured action annotation already exists: " + expected); //$NON-NLS-1$
				}
			}
		}
	}

	private static String annotationName(Annotation annotation) {
		ITypeBinding binding= annotation.resolveTypeBinding();
		return binding == null ? annotation.getTypeName().getFullyQualifiedName()
				: binding.getQualifiedName();
	}

	private static String simpleName(String qualifiedName) {
		int separator= qualifiedName.lastIndexOf('.');
		return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
	}
}
