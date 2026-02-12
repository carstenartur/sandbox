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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
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
 * TriggerPattern-based helper for detecting missing super.dispose() calls in SWT Widget subclasses.
 * 
 * <p>This plugin demonstrates the TriggerPattern framework for detecting missing super calls
 * in overridden methods. It specifically targets the common pattern in SWT/JFace where
 * Widget.dispose() must call super.dispose().</p>
 * 
 * <p><b>Pattern Detected:</b> Methods that override {@code org.eclipse.swt.widgets.Widget.dispose()}
 * but don't call {@code super.dispose()}.</p>
 * 
 * <p><b>Example:</b></p>
 * <pre>
 * class MyWidget extends Widget {
 *     {@literal @}Override
 *     public void dispose() {
 *         // Missing super.dispose() call
 *         cleanup();
 *     }
 * }
 * </pre>
 * 
 * <p><b>Note:</b> This is a demonstration of the TriggerPattern API. The actual implementation
 * of override detection and body constraint checking is not yet fully implemented
 * in the TriggerPattern engine and will be completed in future updates.</p>
 * 
 * @see org.eclipse.swt.widgets.Widget#dispose()
 * @since 1.2.6
 */
public class MissingSuperDisposePlugin {
	
	/**
	 * Detects missing super.dispose() call in dispose() method overrides.
	 * 
	 * <p>This method demonstrates the proposed API for detecting missing super calls.
	 * When the TriggerPattern engine is fully implemented, this will automatically match
	 * any dispose() method that overrides Widget.dispose() but doesn't call super.dispose().</p>
	 * 
	 * <p>The {@code @TriggerPattern} annotation specifies:</p>
	 * <ul>
	 * <li>Pattern: {@code void dispose()} - matches methods with this signature</li>
	 * <li>Kind: METHOD_DECLARATION - matches method declarations (not invocations)</li>
	 * <li>Overrides: org.eclipse.swt.widgets.Widget - only matches if overriding Widget.dispose()</li>
	 * </ul>
	 * 
	 * <p>The {@code @BodyConstraint} annotation specifies:</p>
	 * <ul>
	 * <li>mustContain: {@code super.dispose()} - the pattern to look for in method body</li>
	 * <li>negate: true - triggers when the pattern is NOT found (i.e., missing super call)</li>
	 * </ul>
	 * 
	 * @param ctx the hint context containing the matched node and rewrite utilities
	 * @return a completion proposal to add the missing super.dispose() call, or null if not applicable
	 */
	@TriggerPattern(
		value = "void dispose()",
		kind = PatternKind.METHOD_DECLARATION,
		overrides = "org.eclipse.swt.widgets.Widget"
	)
	@BodyConstraint(mustContain = "super.dispose()", negate = true)
	@Hint(
		displayName = "Missing super.dispose() call",
		description = "Methods overriding Widget.dispose() should call super.dispose()"
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
	 * <p>This is a simple implementation that only checks top-level statements.
	 * A production implementation would need to handle nested blocks, conditional
	 * statements, and other control flow structures.</p>
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
