/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.sandbox.jdt.container.analysis.LosslessArrayLengthBridge;
import org.sandbox.jdt.container.api.LosslessParameterBridgePlan;

final class LosslessParameterBridgeFix {
	private LosslessParameterBridgeFix() {
	}

	static CompilationUnitRewriteOperationWithSourceRange operation(ICompilationUnit unit, CompilationUnit root,
			LosslessParameterBridgePlan plan) throws CoreException {
		var fresh= LosslessArrayLengthBridge.prove(root, unit.getSource(), plan.migration());
		if (fresh.filter(plan::equals).isEmpty() || !(root.findDeclaringNode(plan.methodKey()) instanceof MethodDeclaration method)) {
			throw new CoreException(Status.error("The original array bridge declaration changed.")); //$NON-NLS-1$
		}
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(CompilationUnitRewrite cu, LinkedProposalModelCore linked) {
				AST ast= method.getAST();
				var group= createTextEditGroup("Retain a deprecated array overload backed by the original array", cu); //$NON-NLS-1$
				var overload= (MethodDeclaration) ASTNode.copySubtree(ast, method);
				overload.setJavadoc(null);
				ImportRewrite imports= cu.getImportRewrite();
				var context= new ContextSensitiveImportRewriteContext(method, imports);
				var deprecated= ast.newMarkerAnnotation();
				deprecated.setTypeName(ast.newName(imports.addImport("java.lang.Deprecated", context))); //$NON-NLS-1$
				overload.modifiers().add(0, deprecated);
				var original= (SingleVariableDeclaration) method.parameters().getFirst();
				String arg= original.getName().getIdentifier();
				var asList= ast.newMethodInvocation();
				asList.setExpression(ast.newName(imports.addImport("java.util.Arrays", context))); //$NON-NLS-1$
				asList.setName(ast.newSimpleName("asList")); //$NON-NLS-1$
				asList.arguments().add(ast.newSimpleName(arg));
				var isNull= ast.newInfixExpression();
				isNull.setOperator(InfixExpression.Operator.EQUALS);
				isNull.setLeftOperand(ast.newSimpleName(arg));
				isNull.setRightOperand(ast.newNullLiteral());
				var nullable= ast.newConditionalExpression();
				nullable.setExpression(isNull);
				nullable.setThenExpression(ast.newNullLiteral());
				nullable.setElseExpression(asList);
				var delegate= ast.newMethodInvocation();
				delegate.setName(ast.newSimpleName(method.getName().getIdentifier()));
				delegate.arguments().add(nullable);
				var ret= ast.newReturnStatement();
				ret.setExpression(delegate);
				var body= ast.newBlock();
				body.statements().add(ret);
				overload.setBody(body);
				cu.getASTRewrite().getListRewrite(method.getParent(), TypeDeclaration.BODY_DECLARATIONS_PROPERTY)
						.insertAfter(overload, method, group);
			}

			@Override
			public String getAdditionalInfo() {
				return "The retained overload passes null through and uses the array-backed list view for length reads."; //$NON-NLS-1$
			}
		};
	}
}
