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

import javax.lang.model.SourceVersion;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.sandbox.jdt.triggerpattern.api.RewriteActionValue;
import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;

/** Typed declaration-signature actions used by coordinated migrations. */
final class SignatureStructuredRewriteActions {

	private SignatureStructuredRewriteActions() {
	}

	static void registerAll(StructuredRewriteActionRegistry registry) {
		registry.register("renameDeclaration", SignatureStructuredRewriteActions::renameDeclaration); //$NON-NLS-1$
		registry.register("replaceFieldType", SignatureStructuredRewriteActions::replaceFieldType); //$NON-NLS-1$
		registry.register("addParameter", SignatureStructuredRewriteActions::addParameter); //$NON-NLS-1$
		registry.register("removeParameter", SignatureStructuredRewriteActions::removeParameter); //$NON-NLS-1$
		registry.register("replaceParameterType", SignatureStructuredRewriteActions::replaceParameterType); //$NON-NLS-1$
	}

	private static void renameDeclaration(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		ASTNode target= context.resolveAuthorizedNode(action.requiredArgument("target")); //$NON-NLS-1$
		String replacement= validIdentifier(
				context.resolveString(action.requiredArgument("name")), context); //$NON-NLS-1$
		SimpleName name= declarationName(target, context);
		if (name.getIdentifier().equals(replacement)) {
			throw context.failure("renameDeclaration target already has name " + replacement); //$NON-NLS-1$
		}
		context.cuRewrite().getASTRewrite().replace(name,
				context.ast().newSimpleName(replacement), context.editGroup());
	}

	private static void replaceFieldType(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		FieldDeclaration field= fieldTarget(context, action.requiredArgument("target")); //$NON-NLS-1$
		if (field.fragments().size() != 1) {
			throw context.failure("replaceFieldType requires a single-fragment field declaration"); //$NON-NLS-1$
		}
		Type replacement= context.createType(
				context.resolveString(action.requiredArgument("type"))); //$NON-NLS-1$
		replaceType(field.getType(), replacement, context);
	}

	private static void addParameter(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		MethodDeclaration method= methodTarget(context, action.requiredArgument("target")); //$NON-NLS-1$
		String name= validIdentifier(context.resolveString(action.requiredArgument("name")), context); //$NON-NLS-1$
		if (findParameter(method, name) != null) {
			throw context.failure("addParameter would duplicate parameter " + name); //$NON-NLS-1$
		}
		int index= action.arguments().containsKey("index") //$NON-NLS-1$
				? context.resolveInteger(action.arguments().get("index")) : method.parameters().size(); //$NON-NLS-1$
		if (index < 0 || index > method.parameters().size()) {
			throw context.failure("addParameter index " + index + " is outside 0.." //$NON-NLS-1$ //$NON-NLS-2$
					+ method.parameters().size());
		}
		SingleVariableDeclaration parameter= context.ast().newSingleVariableDeclaration();
		parameter.setName(context.ast().newSimpleName(name));
		parameter.setType(context.createType(
				context.resolveString(action.requiredArgument("type")))); //$NON-NLS-1$
		parameters(context, method).insertAt(parameter, index, context.editGroup());
	}

	private static void removeParameter(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		MethodDeclaration method= methodTarget(context, action.requiredArgument("target")); //$NON-NLS-1$
		SingleVariableDeclaration parameter= selectedParameter(action, method, context);
		parameters(context, method).remove(parameter, context.editGroup());
		context.cuRewrite().getImportRemover().registerRemovedNode(parameter);
		context.cuRewrite().getImportRemover().applyRemoves(context.cuRewrite().getImportRewrite());
	}

	private static void replaceParameterType(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		MethodDeclaration method= methodTarget(context, action.requiredArgument("target")); //$NON-NLS-1$
		SingleVariableDeclaration parameter= selectedParameter(action, method, context);
		Type replacement= context.createType(
				context.resolveString(action.requiredArgument("type"))); //$NON-NLS-1$
		replaceType(parameter.getType(), replacement, context);
	}

	private static SingleVariableDeclaration selectedParameter(StructuredRewriteAction action,
			MethodDeclaration method, StructuredRewriteActionContext context) throws CoreException {
		boolean hasName= action.arguments().containsKey("name"); //$NON-NLS-1$
		boolean hasIndex= action.arguments().containsKey("index"); //$NON-NLS-1$
		if (hasName == hasIndex) {
			throw context.failure(action.name()
					+ " requires exactly one parameter selector: name or index"); //$NON-NLS-1$
		}
		if (hasName) {
			String name= context.resolveString(action.arguments().get("name")); //$NON-NLS-1$
			SingleVariableDeclaration parameter= findParameter(method, name);
			if (parameter == null) {
				throw context.failure(action.name() + " cannot find parameter named " + name); //$NON-NLS-1$
			}
			return parameter;
		}
		int index= context.resolveInteger(action.arguments().get("index")); //$NON-NLS-1$
		if (index < 0 || index >= method.parameters().size()) {
			throw context.failure(action.name() + " index " + index + " is outside 0.." //$NON-NLS-1$ //$NON-NLS-2$
					+ Math.max(-1, method.parameters().size() - 1));
		}
		return (SingleVariableDeclaration) method.parameters().get(index);
	}

	private static SingleVariableDeclaration findParameter(MethodDeclaration method, String name) {
		for (Object parameterObject : method.parameters()) {
			SingleVariableDeclaration parameter= (SingleVariableDeclaration) parameterObject;
			if (name.equals(parameter.getName().getIdentifier())) {
				return parameter;
			}
		}
		return null;
	}

	private static SimpleName declarationName(ASTNode target,
			StructuredRewriteActionContext context) throws CoreException {
		if (target instanceof MethodDeclaration method) {
			if (method.isConstructor()) {
				throw context.failure("renameDeclaration does not rename constructors or their declaring types"); //$NON-NLS-1$
			}
			return method.getName();
		}
		if (target instanceof FieldDeclaration field && field.fragments().size() == 1) {
			return ((VariableDeclarationFragment) field.fragments().get(0)).getName();
		}
		if (target instanceof VariableDeclarationFragment fragment
				&& fragment.getParent() instanceof FieldDeclaration) {
			return fragment.getName();
		}
		if (target instanceof SimpleName simple && simple.getParent() instanceof MethodDeclaration method
				&& method.getName() == simple && !method.isConstructor()) {
			return simple;
		}
		if (target instanceof SimpleName simple
				&& simple.getParent() instanceof VariableDeclarationFragment fragment
				&& fragment.getName() == simple && fragment.getParent() instanceof FieldDeclaration) {
			return simple;
		}
		throw context.failure("renameDeclaration requires an exact planned method or single field declaration"); //$NON-NLS-1$
	}

	private static MethodDeclaration methodTarget(StructuredRewriteActionContext context,
			RewriteActionValue value) throws CoreException {
		ASTNode target= context.resolveAuthorizedNode(value);
		if (target instanceof MethodDeclaration method) {
			return method;
		}
		if (target instanceof SimpleName name && name.getParent() instanceof MethodDeclaration method
				&& method.getName() == name) {
			return method;
		}
		throw context.failure("Structured signature action target is not an exact method declaration"); //$NON-NLS-1$
	}

	private static FieldDeclaration fieldTarget(StructuredRewriteActionContext context,
			RewriteActionValue value) throws CoreException {
		ASTNode target= context.resolveAuthorizedNode(value);
		if (target instanceof FieldDeclaration field) {
			return field;
		}
		if (target instanceof VariableDeclarationFragment fragment
				&& fragment.getParent() instanceof FieldDeclaration field) {
			return field;
		}
		if (target instanceof SimpleName name && name.getParent() instanceof VariableDeclarationFragment fragment
				&& fragment.getName() == name && fragment.getParent() instanceof FieldDeclaration field) {
			return field;
		}
		throw context.failure("Structured field action target is not an exact field declaration"); //$NON-NLS-1$
	}

	private static ListRewrite parameters(StructuredRewriteActionContext context,
			MethodDeclaration method) {
		return context.cuRewrite().getASTRewrite()
				.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY);
	}

	private static void replaceType(Type existing, Type replacement,
			StructuredRewriteActionContext context) {
		ASTRewrite rewrite= context.cuRewrite().getASTRewrite();
		rewrite.replace(existing, replacement, context.editGroup());
		context.cuRewrite().getImportRemover().registerRemovedNode(existing);
		context.cuRewrite().getImportRemover().applyRemoves(context.cuRewrite().getImportRewrite());
	}

	private static String validIdentifier(String candidate,
			StructuredRewriteActionContext context) throws CoreException {
		if (!SourceVersion.isIdentifier(candidate) || SourceVersion.isKeyword(candidate)) {
			throw context.failure("Structured action name is not a Java identifier: " + candidate); //$NON-NLS-1$
		}
		return candidate;
	}
}
