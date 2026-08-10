/*******************************************************************************
 * Copyright (c) 2025, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Adapts JUnit 4 {@code ExternalResource} lifecycle methods to Jupiter callback
 * methods without weakening their checked-exception or inheritance semantics.
 */
public final class LifecycleMethodAdapter {

	private LifecycleMethodAdapter() {
		throw new UnsupportedOperationException("Utility class"); //$NON-NLS-1$
	}

	/**
	 * Renames the selected lifecycle methods and adapts their callback contract.
	 */
	public static void updateLifecycleMethodsInClass(TypeDeclaration node, ASTRewrite globalRewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite, String methodbefore, String methodafter,
			String methodbeforeeach, String methodaftereach) {
		for (MethodDeclaration method : node.getMethods()) {
			if (isLifecycleMethod(method, methodbefore)) {
				processLifecycleMethod(node, method, globalRewrite, ast, group, importRewrite, methodbefore,
						methodbeforeeach);
			} else if (isLifecycleMethod(method, methodafter)) {
				processLifecycleMethod(node, method, globalRewrite, ast, group, importRewrite, methodafter,
						methodaftereach);
			}
		}
	}

	private static void processLifecycleMethod(TypeDeclaration node, MethodDeclaration method,
			ASTRewrite globalRewrite, AST ast, TextEditGroup group, ImportRewrite importRewrite,
			String oldMethodName, String newMethodName) {
		ASTRewrite rewriteToUse= getASTRewrite(node, ast, globalRewrite);
		ImportRewrite importRewriteToUse= getImportRewrite(node, ast, importRewrite);

		setPublicVisibilityIfProtected(method, rewriteToUse, ast, group);
		adaptSuperLifecycleCalls(oldMethodName, newMethodName, method, rewriteToUse, ast, group,
				directlyExtendsExternalResource(node));
		replaceThrowsThrowableWithException(method, rewriteToUse, ast, group);
		rewriteToUse.replace(method.getName(), ast.newSimpleName(newMethodName), group);
		ensureExtensionContextParameter(method, rewriteToUse, ast, group, importRewriteToUse);

		if (rewriteToUse != globalRewrite) {
			DocumentHelper.createChangeForRewrite(
					org.sandbox.jdt.internal.corext.util.ASTNavigationUtils.findCompilationUnit(node), rewriteToUse);
		}
	}

	/**
	 * Compatibility entry point used by existing callers. Source hierarchy calls
	 * are renamed and receive the callback context.
	 */
	public static void adaptSuperBeforeCalls(String oldMethodName, String newMethodName,
			MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group) {
		adaptSuperLifecycleCalls(oldMethodName, newMethodName, method, rewriter, ast, group, false);
	}

	private static void adaptSuperLifecycleCalls(String oldMethodName, String newMethodName,
			MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group,
			boolean directExternalResourceSubclass) {
		ReferenceHolder<String, Object> holder= ReferenceHolder.create();
		AstProcessorBuilder.with(holder)
			.onSuperMethodInvocation((node, ignored) -> {
				if (!oldMethodName.equals(node.getName().getIdentifier())) {
					return true;
				}
				if (directExternalResourceSubclass && node.getParent() instanceof ExpressionStatement statement) {
					// ExternalResource itself disappears from the hierarchy. Its empty
					// before()/after() implementation therefore has no Jupiter equivalent.
					rewriter.remove(statement, group);
				} else {
					// A source superclass is migrated as part of the same closed plan, so
					// preserve the explicit lifecycle chaining.
					rewriter.replace(node.getName(), ast.newSimpleName(newMethodName), group);
					addContextArgumentIfMissing(node, rewriter, ast, group);
				}
				return true;
			})
			.build(method);
	}

	/** Adds an {@code ExtensionContext} parameter when it is not already present. */
	public static void ensureExtensionContextParameter(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite) {
		boolean hasExtensionContext= method.parameters().stream()
				.anyMatch(param -> param instanceof SingleVariableDeclaration declaration
						&& isExtensionContext(declaration, ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT))
				|| rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY).getRewrittenList().stream()
						.anyMatch(param -> param instanceof SingleVariableDeclaration declaration
								&& EXTENSION_CONTEXT.equals(declaration.getType().toString()));
		if (hasExtensionContext) {
			return;
		}

		SingleVariableDeclaration parameter= ast.newSingleVariableDeclaration();
		parameter.setType(ast.newSimpleType(ast.newName(EXTENSION_CONTEXT)));
		parameter.setName(ast.newSimpleName(VARIABLE_NAME_CONTEXT));
		rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY).insertLast(parameter, group);
		importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
	}

	/**
	 * Historical compatibility method. Jupiter callbacks may throw
	 * {@link Exception}, but not arbitrary {@link Throwable}; replacing the type is
	 * therefore required to keep checked calls in real Eclipse fixtures compilable.
	 */
	public static void removeThrowsThrowable(MethodDeclaration method, ASTRewrite rewriter, TextEditGroup group) {
		replaceThrowsThrowableWithException(method, rewriter, method.getAST(), group);
	}

	private static void replaceThrowsThrowableWithException(MethodDeclaration method, ASTRewrite rewriter,
			AST ast, TextEditGroup group) {
		boolean alreadyThrowsException= method.thrownExceptionTypes().stream()
				.filter(Type.class::isInstance)
				.map(Type.class::cast)
				.anyMatch(type -> isType(type, "java.lang.Exception", "Exception")); //$NON-NLS-1$ //$NON-NLS-2$
		ListRewrite exceptions= rewriter.getListRewrite(method,
				MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
		for (Object exceptionObject : method.thrownExceptionTypes()) {
			if (!(exceptionObject instanceof Type exceptionType)
					|| !isType(exceptionType, "java.lang.Throwable", "Throwable")) { //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}
			if (alreadyThrowsException) {
				exceptions.remove(exceptionType, group);
			} else {
				exceptions.replace(exceptionType,
						ast.newSimpleType(ast.newSimpleName("Exception")), group); //$NON-NLS-1$
			}
		}
	}

	private static boolean isType(Type type, String qualifiedName, String sourceName) {
		ITypeBinding binding= type.resolveBinding();
		return binding != null ? qualifiedName.equals(binding.getErasure().getQualifiedName())
				: sourceName.equals(type.toString()) || qualifiedName.equals(type.toString());
	}

	/** Changes protected callback implementations to the required public visibility. */
	public static void setPublicVisibilityIfProtected(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group) {
		for (Object modifierObject : method.modifiers()) {
			if (modifierObject instanceof Modifier modifier && modifier.isProtected()) {
				rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY).replace(modifier,
						ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD), group);
				return;
			}
		}
	}

	/** Creates a callback method while removing obsolete direct super lifecycle calls. */
	public static MethodDeclaration createLifecycleCallbackMethod(AST ast, String methodName, String paramType,
			Block oldBody, TextEditGroup group) {
		MethodDeclaration method= ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(methodName));
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		method.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));

		SingleVariableDeclaration parameter= ast.newSingleVariableDeclaration();
		parameter.setType(ast.newSimpleType(ast.newName(paramType)));
		parameter.setName(ast.newSimpleName(VARIABLE_NAME_CONTEXT));
		method.parameters().add(parameter);

		if (oldBody != null) {
			Block newBody= (Block) ASTNode.copySubtree(ast, oldBody);
			removeSuperLifecycleCalls(newBody);
			method.setBody(newBody);
		}
		return method;
	}

	private static void removeSuperLifecycleCalls(Block body) {
		ReferenceHolder<String, Object> holder= ReferenceHolder.create();
		AstProcessorBuilder.with(holder)
			.onSuperMethodInvocation((node, ignored) -> {
				String methodName= node.getName().getIdentifier();
				if (METHOD_BEFORE.equals(methodName) || METHOD_AFTER.equals(methodName)
						|| METHOD_BEFORE_EACH.equals(methodName) || METHOD_AFTER_EACH.equals(methodName)
						|| METHOD_BEFORE_ALL.equals(methodName) || METHOD_AFTER_ALL.equals(methodName)) {
					ASTNode parent= node.getParent();
					if (parent instanceof ExpressionStatement) {
						parent.delete();
					}
				}
				return true;
			})
			.build(body);
	}

	private static boolean directlyExtendsExternalResource(TypeDeclaration type) {
		ITypeBinding binding= type.resolveBinding();
		ITypeBinding superclass= binding == null ? null : binding.getSuperclass();
		return superclass != null && ORG_JUNIT_RULES_EXTERNAL_RESOURCE
				.equals(superclass.getErasure().getQualifiedName());
	}

	private static boolean isLifecycleMethod(MethodDeclaration method, String methodName) {
		return methodName.equals(method.getName().getIdentifier());
	}

	private static void addContextArgumentIfMissing(ASTNode node, ASTRewrite rewriter, AST ast,
			TextEditGroup group) {
		ListRewrite arguments;
		if (node instanceof MethodInvocation) {
			arguments= rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
		} else if (node instanceof SuperMethodInvocation) {
			arguments= rewriter.getListRewrite(node, SuperMethodInvocation.ARGUMENTS_PROPERTY);
		} else {
			return;
		}
		boolean hasContextArgument= arguments.getRewrittenList().stream()
				.anyMatch(argument -> argument instanceof SimpleName name
						&& VARIABLE_NAME_CONTEXT.equals(name.getIdentifier()));
		if (!hasContextArgument) {
			arguments.insertFirst(ast.newSimpleName(VARIABLE_NAME_CONTEXT), group);
		}
	}

	private static boolean isExtensionContext(SingleVariableDeclaration parameter, String className) {
		ITypeBinding binding= parameter.getType().resolveBinding();
		return binding != null && className.equals(binding.getQualifiedName());
	}

	private static ASTRewrite getASTRewrite(ASTNode node, AST globalAST, ASTRewrite globalRewrite) {
		return node.getAST() == globalAST ? globalRewrite : ASTRewrite.create(node.getAST());
	}

	private static ImportRewrite getImportRewrite(ASTNode node, AST globalAST,
			ImportRewrite globalImportRewrite) {
		org.eclipse.jdt.core.dom.CompilationUnit compilationUnit=
				org.sandbox.jdt.internal.corext.util.ASTNavigationUtils.findCompilationUnit(node);
		return node.getAST() == globalAST ? globalImportRewrite : ImportRewrite.create(compilationUnit, true);
	}
}
