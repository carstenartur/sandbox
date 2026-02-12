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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.examples;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.api.BodyConstraint;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

/**
 * Example hint provider demonstrating missing super call detection.
 * 
 * <p>This class shows how to use METHOD_DECLARATION patterns with override
 * and body constraints to detect missing super calls in overridden methods.</p>
 * 
 * <p><b>Note:</b> This is a demonstration of the API. The actual implementation
 * of override detection and body constraint checking is not yet fully implemented
 * in the TriggerPattern engine.</p>
 * 
 * @since 1.2.6
 */
public class MissingSuperCallHintProvider {
	
	/**
	 * Detects missing super.dispose() call in dispose() method overrides.
	 * 
	 * <p>This example demonstrates the proposed API for detecting missing super calls.
	 * When fully implemented, this would match any dispose() method that overrides
	 * a dispose() method but doesn't call super.dispose().</p>
	 * 
	 * <p>Example code that would trigger this hint:</p>
	 * <pre>
	 * class MyWidget extends Widget {
	 *     {@literal @}Override
	 *     public void dispose() {
	 *         // Missing super.dispose() call
	 *         System.out.println("disposing");
	 *     }
	 * }
	 * </pre>
	 */
	@TriggerPattern(
		value = "void dispose()",
		kind = PatternKind.METHOD_DECLARATION,
		overrides = "org.eclipse.swt.widgets.Widget"
	)
	@BodyConstraint(mustContain = "super.dispose()", negate = true)
	@Hint(
		displayName = "Missing super.dispose() call",
		description = "Methods overriding dispose() should call super.dispose()"
	)
	public static IJavaCompletionProposal detectMissingDisposeCall(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();
		
		if (!(matchedNode instanceof MethodDeclaration)) {
			return null;
		}
		
		MethodDeclaration method = (MethodDeclaration) matchedNode;
		Block body = method.getBody();
		
		// Cannot add super call if there's no body (e.g., abstract/interface method)
		if (body == null) {
			return null;
		}
		
		// For now, we manually check if super.dispose() is called
		// In the future, this would be handled by the BodyConstraint annotation
		if (containsSuperDisposeCall(body)) {
			return null; // Super call already present
		}
		
		// Create a fix that adds super.dispose() at the end of the method
		AST ast = ctx.getASTRewrite().getAST();
		
		SuperMethodInvocation superCall = ast.newSuperMethodInvocation();
		superCall.setName(ast.newSimpleName("dispose")); //$NON-NLS-1$
		
		ExpressionStatement superCallStmt = ast.newExpressionStatement(superCall);
		
		// Add the super call as the last statement in the method
		ctx.getASTRewrite().getListRewrite(body, Block.STATEMENTS_PROPERTY)
			.insertLast(superCallStmt, null);
		
		String label = "Add missing super.dispose() call"; //$NON-NLS-1$
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(
			label,
			ctx.getICompilationUnit(),
			ctx.getASTRewrite(),
			10, // relevance
			(Image) null
		);
		
		return proposal;
	}
	
	/**
	 * Helper method to check if a method body contains a super.dispose() call.
	 * 
	 * @param body the method body to check
	 * @return true if super.dispose() is called, false otherwise
	 */
	private static boolean containsSuperDisposeCall(Block body) {
		if (body == null) {
			return false;
		}
		
		// Simple check - in production code, this would need to handle nested blocks
		for (Object stmt : body.statements()) {
			if (stmt instanceof ExpressionStatement) {
				ExpressionStatement exprStmt = (ExpressionStatement) stmt;
				if (exprStmt.getExpression() instanceof SuperMethodInvocation) {
					SuperMethodInvocation superCall = (SuperMethodInvocation) exprStmt.getExpression();
					if ("dispose".equals(superCall.getName().getIdentifier())) { //$NON-NLS-1$
						return true;
					}
				}
			}
		}
		
		return false;
	}
}
