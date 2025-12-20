/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * Method Call Replacer - Generates replacement code for inline sequences
 * 
 * This class creates a method invocation to replace an inline code sequence,
 * mapping the inline variables to method parameters based on the variable mapping.
 */
public class MethodCallReplacer {
	
	/**
	 * Create a method invocation that replaces an inline code sequence
	 * 
	 * @param ast The AST to use for creating new nodes
	 * @param targetMethod The method to call
	 * @param variableMapping The mapping from method parameters to inline expressions
	 * @return A method invocation node
	 */
	@SuppressWarnings("unchecked")
	public static MethodInvocation createMethodCall(AST ast, MethodDeclaration targetMethod, VariableMapping variableMapping) {
		if (ast == null || targetMethod == null || variableMapping == null) {
			return null;
		}
		
		MethodInvocation methodCall = ast.newMethodInvocation();
		
		// Set method name
		SimpleName methodName = ast.newSimpleName(targetMethod.getName().getIdentifier());
		methodCall.setName(methodName);
		
		// Create arguments based on parameter mapping
		List<Expression> arguments = createArguments(ast, targetMethod, variableMapping);
		methodCall.arguments().addAll(arguments);
		
		return methodCall;
	}
	
	/**
	 * Create the argument list for the method call
	 */
	@SuppressWarnings("unchecked")
	private static List<Expression> createArguments(AST ast, MethodDeclaration targetMethod, VariableMapping variableMapping) {
		List<Expression> arguments = new ArrayList<>();
		Map<String, String> mappings = variableMapping.getMappings();
		
		// For each parameter in the target method, find the corresponding inline expression
		List<SingleVariableDeclaration> parameters = targetMethod.parameters();
		for (SingleVariableDeclaration param : parameters) {
			String paramName = param.getName().getIdentifier();
			String inlineName = mappings.get(paramName);
			
			if (inlineName != null) {
				// Create a simple name reference for the inline variable
				SimpleName arg = ast.newSimpleName(inlineName);
				arguments.add(arg);
			} else {
				// This shouldn't happen if matching worked correctly
				// but we handle it gracefully
				SimpleName arg = ast.newSimpleName("/* mapping error */");
				arguments.add(arg);
			}
		}
		
		return arguments;
	}
	
	/**
	 * Replace a sequence of statements with a method call using AST rewrite
	 * 
	 * @param rewrite The AST rewrite to use
	 * @param methodCall The method invocation to insert
	 * @param statementsToReplace The statements to replace
	 * @return true if replacement was successful
	 */
	public static boolean replaceWithMethodCall(ASTRewrite rewrite, MethodInvocation methodCall, List<org.eclipse.jdt.core.dom.Statement> statementsToReplace) {
		if (rewrite == null || methodCall == null || statementsToReplace == null || statementsToReplace.isEmpty()) {
			return false;
		}
		
		// Get the parent block and create a list rewrite for it
		org.eclipse.jdt.core.dom.Statement firstStatement = statementsToReplace.get(0);
		org.eclipse.jdt.core.dom.ASTNode parent = firstStatement.getParent();
		
		if (!(parent instanceof org.eclipse.jdt.core.dom.Block)) {
			return false;
		}
		
		ListRewrite listRewrite = rewrite.getListRewrite(parent, org.eclipse.jdt.core.dom.Block.STATEMENTS_PROPERTY);
		
		// Create an expression statement wrapping the method call
		AST ast = rewrite.getAST();
		org.eclipse.jdt.core.dom.ExpressionStatement expressionStatement = ast.newExpressionStatement(methodCall);
		
		// Replace first statement with the method call
		listRewrite.replace(firstStatement, expressionStatement, null);
		
		// Remove remaining statements
		for (int i = 1; i < statementsToReplace.size(); i++) {
			listRewrite.remove(statementsToReplace.get(i), null);
		}
		
		return true;
	}
	
	/**
	 * Check if a method call can be safely created for the given mapping
	 * 
	 * @param targetMethod The target method
	 * @param variableMapping The variable mapping
	 * @return true if all parameters can be mapped
	 */
	@SuppressWarnings("unchecked")
	public static boolean canCreateMethodCall(MethodDeclaration targetMethod, VariableMapping variableMapping) {
		if (targetMethod == null || variableMapping == null) {
			return false;
		}
		
		Map<String, String> mappings = variableMapping.getMappings();
		List<SingleVariableDeclaration> parameters = targetMethod.parameters();
		
		// Check that all parameters have a mapping
		for (SingleVariableDeclaration param : parameters) {
			String paramName = param.getName().getIdentifier();
			if (!mappings.containsKey(paramName)) {
				return false;
			}
		}
		
		return true;
	}
}
