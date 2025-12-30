/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Helper class for adapting JUnit lifecycle methods from JUnit 4 to JUnit 5.
 * Handles method renaming, parameter additions, visibility changes, and exception cleanup.
 */
public final class LifecycleMethodAdapter {

	// Private constructor to prevent instantiation
	private LifecycleMethodAdapter() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Updates lifecycle methods in a class: renames before() -> beforeEach(), after() -> afterEach().
	 * Also handles parameter updates and visibility changes as needed.
	 * 
	 * @param node the type declaration containing lifecycle methods
	 * @param globalRewrite the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewrite the import rewriter
	 * @param methodbefore the old "before" method name
	 * @param methodafter the old "after" method name
	 * @param methodbeforeeach the new "beforeEach" method name
	 * @param methodaftereach the new "afterEach" method name
	 */
	public static void updateLifecycleMethodsInClass(TypeDeclaration node, ASTRewrite globalRewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite, String methodbefore, String methodafter,
			String methodbeforeeach, String methodaftereach) {

		for (MethodDeclaration method : node.getMethods()) {
			if (isLifecycleMethod(method, methodbefore)) {
				processLifecycleMethod(node, method, globalRewrite, ast, group, importRewrite, methodbefore, methodbeforeeach);
			} else if (isLifecycleMethod(method, methodafter)) {
				processLifecycleMethod(node, method, globalRewrite, ast, group, importRewrite, methodafter, methodaftereach);
			}
		}
	}

	/**
	 * Processes a lifecycle method by setting up the appropriate rewriters and applying changes.
	 */
	private static void processLifecycleMethod(TypeDeclaration node, MethodDeclaration method, ASTRewrite globalRewrite,
			AST ast, TextEditGroup group, ImportRewrite importRewrite, String oldMethodName, String newMethodName) {
		ASTRewrite rewriteToUse = getASTRewrite(node, ast, globalRewrite);
		ImportRewrite importRewriteToUse = getImportRewrite(node, ast, importRewrite);

		processMethod(method, rewriteToUse, ast, group, importRewriteToUse, oldMethodName, newMethodName);

		if (rewriteToUse != globalRewrite) {
			DocumentHelper.createChangeForRewrite(
					org.sandbox.jdt.internal.corext.util.ASTNavigationUtils.findCompilationUnit(node), rewriteToUse);
		}
	}

	/**
	 * Processes a single lifecycle method: renames it, adapts super calls, removes throws, ensures parameter.
	 */
	private static void processMethod(MethodDeclaration method, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter, String methodname, String methodnamejunit5) {
		setPublicVisibilityIfProtected(method, rewriter, ast, group);
		adaptSuperBeforeCalls(methodname, methodnamejunit5, method, rewriter, ast, group);
		removeThrowsThrowable(method, rewriter, group);
		rewriter.replace(method.getName(), ast.newSimpleName(methodnamejunit5), group);
		ensureExtensionContextParameter(method, rewriter, ast, group, importRewriter);
	}

	/**
	 * Renames super.before() and super.after() calls to match JUnit 5 lifecycle method names.
	 * Also ensures that the ExtensionContext parameter is passed to super calls.
	 * 
	 * @param oldMethodName the old method name (e.g., "before")
	 * @param newMethodName the new method name (e.g., "beforeEach")
	 * @param method the method containing super calls to update
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 */
	public static void adaptSuperBeforeCalls(String oldMethodName, String newMethodName, MethodDeclaration method,
			ASTRewrite rewriter, AST ast, TextEditGroup group) {
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation node) {
				if (oldMethodName.equals(node.getName().getIdentifier())) {
					rewriter.replace(node.getName(), ast.newSimpleName(newMethodName), group);
					addContextArgumentIfMissing(node, rewriter, ast, group);
				}
				return super.visit(node);
			}
		});
	}

	/**
	 * Ensures that a method declaration has an ExtensionContext parameter.
	 * Adds the parameter if missing. Used when converting ExternalResource lifecycle
	 * methods to JUnit 5 callback interface methods.
	 * 
	 * @param method the method declaration to check and update
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewrite the import rewriter
	 */
	public static void ensureExtensionContextParameter(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group, ImportRewrite importRewrite) {

		// Check if ExtensionContext parameter already exists (in AST or pending rewrites)
		boolean hasExtensionContext = method.parameters().stream()
				.anyMatch(param -> param instanceof SingleVariableDeclaration && isExtensionContext(
						(SingleVariableDeclaration) param, ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT))
				|| rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY).getRewrittenList().stream()
						.anyMatch(param -> param instanceof SingleVariableDeclaration
								&& EXTENSION_CONTEXT.equals(((SingleVariableDeclaration) param).getType().toString()));

		if (!hasExtensionContext) {
			// Add ExtensionContext parameter
			SingleVariableDeclaration newParam = ast.newSingleVariableDeclaration();
			newParam.setType(ast.newSimpleType(ast.newName(EXTENSION_CONTEXT)));
			newParam.setName(ast.newSimpleName(VARIABLE_NAME_CONTEXT));
			ListRewrite listRewrite = rewrite.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY);
			listRewrite.insertLast(newParam, group);

			// Add import for ExtensionContext
			importRewrite.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
		}
	}

	/**
	 * Removes "throws Throwable" from a method declaration.
	 * JUnit 5 lifecycle methods don't need to declare Throwable.
	 * 
	 * @param method the method declaration to modify
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 */
	public static void removeThrowsThrowable(MethodDeclaration method, ASTRewrite rewriter, TextEditGroup group) {
		List<?> thrownExceptionTypes = method.thrownExceptionTypes();
		for (Object exceptionType : thrownExceptionTypes) {
			if (exceptionType instanceof SimpleType) {
				SimpleType exception = (SimpleType) exceptionType;
				if ("Throwable".equals(exception.getName().getFullyQualifiedName())) {
					ListRewrite listRewrite = rewriter.getListRewrite(method,
							MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
					listRewrite.remove(exception, group);
					break; // Only one Throwable should be present
				}
			}
		}
	}

	/**
	 * Changes a method's visibility from protected to public if needed.
	 * JUnit 5 callback methods must be public.
	 * 
	 * @param method the method declaration to modify
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 */
	public static void setPublicVisibilityIfProtected(MethodDeclaration method, ASTRewrite rewrite, AST ast,
			TextEditGroup group) {
		// Iterate through modifiers and search for a protected modifier
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Modifier) {
				Modifier mod = (Modifier) modifier;
				if (mod.isProtected()) {
					ListRewrite modifierRewrite = rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
					Modifier publicModifier = ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD);
					modifierRewrite.replace(mod, publicModifier, group);
					break; // Stop the loop as soon as the modifier is replaced
				}
			}
		}
	}

	/**
	 * Creates a lifecycle callback method for JUnit 5 extension interfaces.
	 * Used when converting ExternalResource before()/after() methods to callback methods.
	 * 
	 * @param ast the AST instance
	 * @param methodName the callback method name (e.g., "beforeEach", "afterEach")
	 * @param paramType the parameter type name (e.g., "ExtensionContext")
	 * @param oldBody the body from the original lifecycle method (will be copied and cleaned)
	 * @param group the text edit group
	 * @return the new method declaration
	 */
	public static MethodDeclaration createLifecycleCallbackMethod(AST ast, String methodName, String paramType,
			Block oldBody, TextEditGroup group) {

		MethodDeclaration method = ast.newMethodDeclaration();
		method.setName(ast.newSimpleName(methodName));
		method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		method.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));

		// Add the ExtensionContext (or similar) parameter
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setType(ast.newSimpleType(ast.newName(paramType)));
		param.setName(ast.newSimpleName(VARIABLE_NAME_CONTEXT));
		method.parameters().add(param);

		// Copy the body from the old method and remove super calls to lifecycle methods
		if (oldBody != null) {
			Block newBody = (Block) ASTNode.copySubtree(ast, oldBody);
			removeSuperLifecycleCalls(newBody);
			method.setBody(newBody);
		}

		return method;
	}

	/**
	 * Removes super calls to lifecycle methods (before/after) from a method body.
	 * When converting ExternalResource to callback interfaces, super calls to lifecycle
	 * methods should be removed as the callback interfaces don't have such super methods.
	 * 
	 * @param body the method body to clean
	 */
	private static void removeSuperLifecycleCalls(Block body) {
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(SuperMethodInvocation node) {
				String methodName = node.getName().getIdentifier();
				// Remove super calls to lifecycle methods
				if (METHOD_BEFORE.equals(methodName) || METHOD_AFTER.equals(methodName) ||
					METHOD_BEFORE_EACH.equals(methodName) || METHOD_AFTER_EACH.equals(methodName) ||
					METHOD_BEFORE_ALL.equals(methodName) || METHOD_AFTER_ALL.equals(methodName)) {
					// Replace the super call with an empty statement by removing it from parent
					ASTNode parent = node.getParent();
					if (parent != null) {
						parent.delete();
					}
				}
				return super.visit(node);
			}
		});
	}

	/**
	 * Checks if a method is a lifecycle method with the given name.
	 * 
	 * @param method the method declaration to check
	 * @param methodName the expected method name
	 * @return true if the method name matches
	 */
	private static boolean isLifecycleMethod(MethodDeclaration method, String methodName) {
		return methodName.equals(method.getName().getIdentifier());
	}

	/**
	 * Adds the ExtensionContext parameter to method invocations if not already present.
	 * Used when migrating JUnit 4 lifecycle methods to JUnit 5 callback interfaces.
	 * 
	 * @param node the method invocation node (MethodInvocation or SuperMethodInvocation)
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 */
	private static void addContextArgumentIfMissing(ASTNode node, ASTRewrite rewriter, AST ast, TextEditGroup group) {
		ListRewrite argsRewrite;
		if (node instanceof MethodInvocation) {
			argsRewrite = rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
		} else if (node instanceof SuperMethodInvocation) {
			argsRewrite = rewriter.getListRewrite(node, SuperMethodInvocation.ARGUMENTS_PROPERTY);
		} else {
			return; // Only supports MethodInvocation and SuperMethodInvocation
		}

		// Check if context argument is already present
		boolean hasContextArgument = argsRewrite.getRewrittenList().stream().anyMatch(
				arg -> arg instanceof SimpleName && VARIABLE_NAME_CONTEXT.equals(((SimpleName) arg).getIdentifier()));

		if (!hasContextArgument) {
			argsRewrite.insertFirst(ast.newSimpleName(VARIABLE_NAME_CONTEXT), group);
		}
	}

	private static boolean isExtensionContext(SingleVariableDeclaration param, String className) {
		ITypeBinding binding = param.getType().resolveBinding();
		return binding != null && className.equals(binding.getQualifiedName());
	}

	private static ASTRewrite getASTRewrite(ASTNode node, AST globalAST, ASTRewrite globalRewrite) {
		return (node.getAST() == globalAST) ? globalRewrite : ASTRewrite.create(node.getAST());
	}

	private static ImportRewrite getImportRewrite(ASTNode node, AST globalAST, ImportRewrite globalImportRewrite) {
		org.eclipse.jdt.core.dom.CompilationUnit compilationUnit = org.sandbox.jdt.internal.corext.util.ASTNavigationUtils
				.findCompilationUnit(node);
		return (node.getAST() == globalAST) ? globalImportRewrite : ImportRewrite.create(compilationUnit, true);
	}
}
