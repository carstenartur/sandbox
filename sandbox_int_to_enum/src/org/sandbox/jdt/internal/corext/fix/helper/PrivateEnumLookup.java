/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.text.edits.TextEditGroup;

/** Confines ordinal conversion to a read of a fresh, non-escaping lookup array. */
final class PrivateEnumLookup {
	private PrivateEnumLookup() { }

	static boolean isLocalIndex(SimpleName state, MethodDeclaration method, Map<String, Boolean> localTables) {
		Expression index= withParentheses(state);
		if (!(index.getParent() instanceof ArrayAccess access) || access.getIndex() != index || !readOnly(access)) {
			return false;
		}
		Expression table= access.getArray();
		if (table instanceof ArrayCreation creation) {
			return creation.getType().getDimensions() == 1;
		}
		if (!(table instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding binding)
				|| binding.isRecovered() || binding.getKey() == null || binding.getType().getDimensions() != 1) {
			return false;
		}
		return localTables.computeIfAbsent(binding.getKey(), key -> confinedToReads(binding, method));
	}

	private static boolean confinedToReads(IVariableBinding binding, MethodDeclaration method) {
		CompilationUnit root= (CompilationUnit) method.getRoot();
		if (!(root.findDeclaringNode(binding) instanceof VariableDeclarationFragment declaration)
				|| !(declaration.getParent() instanceof VariableDeclarationStatement)
				|| !(declaration.getInitializer() instanceof ArrayInitializer
						|| declaration.getInitializer() instanceof ArrayCreation)) {
			return false;
		}
		ASTNode enclosing= declaration;
		while (enclosing != null && !(enclosing instanceof MethodDeclaration)) {
			enclosing= enclosing.getParent();
		}
		if (enclosing != method) {
			return false;
		}
		boolean[] confined= { true };
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (name != declaration.getName() && binding.getName().equals(name.getIdentifier())) {
					IBinding use= name.resolveBinding();
					if (use == null || use.isRecovered()) {
						confined[0]= false;
					} else if (binding.isEqualTo(use)) {
						Expression expression= withParentheses(name);
						if (!(expression.getParent() instanceof ArrayAccess access)
								|| access.getArray() != expression || !readOnly(access)) {
							confined[0]= false;
						}
					}
				}
				return false;
			}
		});
		return confined[0];
	}

	private static Expression withParentheses(Expression expression) {
		while (expression.getParent() instanceof ParenthesizedExpression parent) {
			expression= parent;
		}
		return expression;
	}

	private static boolean readOnly(ArrayAccess access) {
		Expression expression= withParentheses(access);
		ASTNode parent= expression.getParent();
		return !(parent instanceof Assignment assignment && assignment.getLeftHandSide() == expression
				|| parent instanceof PostfixExpression || parent instanceof PrefixExpression prefix
						&& (prefix.getOperator() == PrefixExpression.Operator.INCREMENT
								|| prefix.getOperator() == PrefixExpression.Operator.DECREMENT));
	}

	static void rewriteIndexes(List<SimpleName> indexes, ASTRewrite rewrite, TextEditGroup group) {
		for (SimpleName index : indexes) {
			var ast= index.getAST();
			var ordinal= ast.newMethodInvocation();
			ordinal.setExpression(ast.newSimpleName(index.getIdentifier()));
			ordinal.setName(ast.newSimpleName("ordinal")); //$NON-NLS-1$
			rewrite.replace(index, ordinal, group);
		}
	}
}
