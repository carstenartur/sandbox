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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_AFTER;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_AFTER_ALL;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_AFTER_EACH;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_BEFORE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_BEFORE_ALL;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_BEFORE_EACH;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.text.edits.TextEditGroup;

/**
 * Adds Jupiter callback bridges while retaining the JUnit 4
 * {@code ExternalResource} contract.
 *
 * <p>This staged form is required when a shared fixture has both migrated
 * Jupiter consumers and a strict-mode consumer that must stay on JUnit 4. The
 * callbacks delegate to the original virtual {@code before()}/{@code after()}
 * methods, so source subclasses keep their established override and explicit
 * {@code super} semantics.</p>
 */
public final class ExternalResourceCompatibilityRefactorer {

	private record CallbackConfig(String beforeInterface, String afterInterface,
			String beforeMethod, String afterMethod) {
	}

	private ExternalResourceCompatibilityRefactorer() {
		throw new UnsupportedOperationException("Utility class"); //$NON-NLS-1$
	}

	/** Adds compatibility callbacks to a direct {@code ExternalResource} subclass. */
	public static void addJupiterCallbacks(TypeDeclaration node, boolean classRule,
			ASTRewrite rewrite, AST ast, TextEditGroup group, ImportRewrite imports) {
		if (node == null || !ExternalResourceRefactorer.isDirectlyExtendingExternalResource(node.resolveBinding())) {
			return;
		}
		CallbackConfig config= classRule
				? new CallbackConfig(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK,
						ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK,
						METHOD_BEFORE_ALL, METHOD_AFTER_ALL)
				: new CallbackConfig(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK,
						ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK,
						METHOD_BEFORE_EACH, METHOD_AFTER_EACH);

		addInterfaceIfMissing(node, config.beforeInterface(), rewrite, ast, group, imports);
		addInterfaceIfMissing(node, config.afterInterface(), rewrite, ast, group, imports);

		ListRewrite members= rewrite.getListRewrite(node, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		if (!declaresCallback(node, config.beforeMethod())) {
			members.insertLast(createBeforeBridge(ast, config.beforeMethod(), imports), group);
		}
		if (!declaresCallback(node, config.afterMethod())) {
			members.insertLast(createAfterBridge(ast, config.afterMethod(), imports), group);
		}
	}

	private static void addInterfaceIfMissing(TypeDeclaration node, String qualifiedName,
			ASTRewrite rewrite, AST ast, TextEditGroup group, ImportRewrite imports) {
		ITypeBinding binding= node.resolveBinding();
		if (binding != null) {
			for (ITypeBinding interfaceBinding : binding.getInterfaces()) {
				if (qualifiedName.equals(interfaceBinding.getErasure().getQualifiedName())) {
					return;
				}
			}
		}
		for (Object interfaceObject : node.superInterfaceTypes()) {
			if (interfaceObject instanceof Type interfaceType) {
				ITypeBinding interfaceBinding= interfaceType.resolveBinding();
				if (interfaceBinding != null
						&& qualifiedName.equals(interfaceBinding.getErasure().getQualifiedName())) {
					return;
				}
			}
		}
		String interfaceName= imports.addImport(qualifiedName);
		rewrite.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY)
				.insertLast(ast.newSimpleType(ast.newName(interfaceName)), group);
	}

	private static boolean declaresCallback(TypeDeclaration node, String methodName) {
		for (MethodDeclaration method : node.getMethods()) {
			if (!methodName.equals(method.getName().getIdentifier()) || method.parameters().size() != 1
					|| !(method.parameters().get(0) instanceof SingleVariableDeclaration parameter)) {
				continue;
			}
			ITypeBinding parameterType= parameter.getType().resolveBinding();
			if (parameterType != null && ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT
					.equals(parameterType.getErasure().getQualifiedName())) {
				return true;
			}
			String sourceType= parameter.getType().toString();
			if ("ExtensionContext".equals(sourceType) //$NON-NLS-1$
					|| ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT.equals(sourceType)) {
				return true;
			}
		}
		return false;
	}

	private static MethodDeclaration createBeforeBridge(AST ast, String methodName, ImportRewrite imports) {
		MethodDeclaration method= createCallbackMethod(ast, methodName, imports);
		method.thrownExceptionTypes().add(ast.newSimpleType(ast.newSimpleName("Exception"))); //$NON-NLS-1$

		TryStatement guardedCall= ast.newTryStatement();
		Block body= ast.newBlock();
		body.statements().add(invokeLifecycle(ast, METHOD_BEFORE));
		guardedCall.setBody(body);
		guardedCall.catchClauses().add(rethrow(ast, "Exception", "exception")); //$NON-NLS-1$ //$NON-NLS-2$
		guardedCall.catchClauses().add(rethrow(ast, "Error", "error")); //$NON-NLS-1$ //$NON-NLS-2$
		guardedCall.catchClauses().add(wrapUnexpectedThrowable(ast));

		Block callbackBody= ast.newBlock();
		callbackBody.statements().add(guardedCall);
		method.setBody(callbackBody);
		return method;
	}

	private static MethodDeclaration createAfterBridge(AST ast, String methodName, ImportRewrite imports) {
		MethodDeclaration method= createCallbackMethod(ast, methodName, imports);
		Block body= ast.newBlock();
		body.statements().add(invokeLifecycle(ast, METHOD_AFTER));
		method.setBody(body);
		return method;
	}

	private static MethodDeclaration createCallbackMethod(AST ast, String methodName, ImportRewrite imports) {
		MethodDeclaration method= ast.newMethodDeclaration();
		MarkerAnnotation override= ast.newMarkerAnnotation();
		override.setTypeName(ast.newSimpleName("Override")); //$NON-NLS-1$
		method.modifiers().add(override);
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		method.setName(ast.newSimpleName(methodName));
		method.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));

		SingleVariableDeclaration context= ast.newSingleVariableDeclaration();
		String contextType= imports.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
		context.setType(ast.newSimpleType(ast.newName(contextType)));
		context.setName(ast.newSimpleName("context")); //$NON-NLS-1$
		method.parameters().add(context);
		return method;
	}

	private static ExpressionStatement invokeLifecycle(AST ast, String methodName) {
		MethodInvocation invocation= ast.newMethodInvocation();
		invocation.setName(ast.newSimpleName(methodName));
		return ast.newExpressionStatement(invocation);
	}

	private static CatchClause rethrow(AST ast, String typeName, String variableName) {
		CatchClause clause= ast.newCatchClause();
		SingleVariableDeclaration exception= ast.newSingleVariableDeclaration();
		exception.setType(ast.newSimpleType(ast.newSimpleName(typeName)));
		exception.setName(ast.newSimpleName(variableName));
		clause.setException(exception);
		ThrowStatement rethrow= ast.newThrowStatement();
		rethrow.setExpression(ast.newSimpleName(variableName));
		Block body= ast.newBlock();
		body.statements().add(rethrow);
		clause.setBody(body);
		return clause;
	}

	private static CatchClause wrapUnexpectedThrowable(AST ast) {
		CatchClause clause= ast.newCatchClause();
		SingleVariableDeclaration throwable= ast.newSingleVariableDeclaration();
		throwable.setType(ast.newSimpleType(ast.newSimpleName("Throwable"))); //$NON-NLS-1$
		throwable.setName(ast.newSimpleName("throwable")); //$NON-NLS-1$
		clause.setException(throwable);

		ClassInstanceCreation wrapper= ast.newClassInstanceCreation();
		wrapper.setType(ast.newSimpleType(ast.newSimpleName("RuntimeException"))); //$NON-NLS-1$
		wrapper.arguments().add(ast.newSimpleName("throwable")); //$NON-NLS-1$
		ThrowStatement throwWrapper= ast.newThrowStatement();
		throwWrapper.setExpression(wrapper);
		Block body= ast.newBlock();
		body.statements().add(throwWrapper);
		clause.setBody(body);
		return clause;
	}
}
