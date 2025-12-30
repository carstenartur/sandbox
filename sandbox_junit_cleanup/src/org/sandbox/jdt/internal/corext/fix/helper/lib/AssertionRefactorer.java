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

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.TypeCheckingUtils;

/**
 * Helper class for refactoring JUnit assertions from JUnit 4 to JUnit 5 format.
 * Handles parameter reordering (moving message parameter from first to last position).
 */
public final class AssertionRefactorer {

	// Private constructor to prevent instantiation
	private AssertionRefactorer() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Reorders parameters in a method invocation to match JUnit 5 assertion parameter order.
	 * JUnit 5 places the message parameter last, whereas JUnit 4 placed it first.
	 * 
	 * @param node the method invocation to reorder
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 * @param oneparam assertion methods with one value parameter
	 * @param twoparam assertion methods with two value parameters
	 */
	public static void reorderParameters(MethodInvocation node, ASTRewrite rewriter, TextEditGroup group,
			Set<String> oneparam, Set<String> twoparam) {
		String methodName = node.getName().getIdentifier();
		List<Expression> arguments = node.arguments();
		switch (arguments.size()) {
		case 2:
			if (oneparam.contains(methodName)) {
				reorderParameters(rewriter, node, group, 1, 0);
			}
			break;
		case 3:
			if (twoparam.contains(methodName)) {
				reorderParameters(rewriter, node, group, 1, 2, 0); // expected, actual, message
			}
			break;
		case 4:
			reorderParameters(rewriter, node, group, 1, 2, 3, 0); // expected, actual, delta, message
			break;
		default:
			break;
		}
	}

	/**
	 * Reorders method invocation parameters according to the specified order.
	 * Used internally to reorder JUnit assertion parameters.
	 * 
	 * @param rewriter the AST rewriter
	 * @param node the method invocation to reorder
	 * @param group the text edit group
	 * @param order array specifying the new order (indices into current arguments)
	 */
	private static void reorderParameters(ASTRewrite rewriter, MethodInvocation node, TextEditGroup group, int... order) {
		ListRewrite listRewrite = rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
		List<Expression> arguments = node.arguments();
		Expression[] newArguments = new Expression[arguments.size()];
		for (int i = 0; i < order.length; i++) {
			newArguments[i] = (Expression) ASTNode.copySubtree(node.getAST(), arguments.get(order[i]));
		}
		if (!TypeCheckingUtils.isStringType(arguments.get(0), String.class)) {
			return;
		}
		for (int i = 0; i < arguments.size(); i++) {
			listRewrite.replace(arguments.get(i), newArguments[i], group);
		}
	}
}
