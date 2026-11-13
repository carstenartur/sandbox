/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.UNTOUCH_COMMENT_PROPERTY;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.cleanup.EncodingSourceRewrite;
import org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix;

/** Converts one-code-unit {@link String#lastIndexOf(String)} arguments in the Eclipse cleanup profile. */
public final class StringLastIndexOfCleanUp extends AbstractCleanUp {

	@Override
	public CleanUpRequirements getRequirements() {
		boolean enabled= isEnabled(TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		// String simplification can rewrite an enclosing expression and change the line's tags.
		return new CleanUpRequirements(enabled, enabled, false, null);
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		CompilationUnit root= context.getAST();
		if (!isEnabled(TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP) || root == null
				|| !(root.getJavaElement() instanceof ICompilationUnit unit)) {
			return null;
		}
		List<StringLiteral> candidates= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				if ("lastIndexOf".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
						&& invocation.arguments().size() == 1
						&& invocation.arguments().getFirst() instanceof StringLiteral literal
						&& ((invocation.getFlags() | literal.getFlags()) & (ASTNode.MALFORMED | ASTNode.RECOVERED)) == 0
						&& isStringLastIndexOf(invocation.resolveMethodBinding())
						&& literal.resolveConstantExpressionValue() instanceof String value && value.length() == 1) {
					candidates.add(literal);
				}
				return true;
			}
		});
		if (candidates.isEmpty()) {
			return null;
		}
		List<StringLiteral> literals= EncodingSourceRewrite.literalsWithUnambiguousTags(root, unit, candidates);
		if (literals.isEmpty()) {
			return null;
		}
		CompilationUnitRewriteOperation[] operations= literals.stream().map(ReplaceLiteral::new)
				.toArray(CompilationUnitRewriteOperation[]::new);
		return new NlsAwareCleanUpFix(MultiFixMessages.StringLastIndexOfCleanUp_description, root, operations);
	}

	@Override
	public String[] getStepDescriptions() {
		return isEnabled(TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP)
				? new String[] { MultiFixMessages.StringLastIndexOfCleanUp_description } : new String[0];
	}

	private static boolean isStringLastIndexOf(IMethodBinding binding) {
		if (binding == null || binding.isRecovered() || !isString(binding.getDeclaringClass())) {
			return false;
		}
		ITypeBinding[] parameters= binding.getParameterTypes();
		return parameters.length == 1 && isString(parameters[0]);
	}

	private static boolean isString(ITypeBinding type) {
		return type != null && !type.isRecovered() && String.class.getName().equals(type.getQualifiedName());
	}

	private static final class ReplaceLiteral extends CompilationUnitRewriteOperationWithSourceRange {
		private final StringLiteral literal;

		ReplaceLiteral(StringLiteral literal) {
			this.literal= literal;
		}

		@Override
		public void rewriteASTInternal(CompilationUnitRewrite rewrite, LinkedProposalModelCore linkedModel) {
			CharacterLiteral replacement= rewrite.getAST().newCharacterLiteral();
			char value= ((String) literal.resolveConstantExpressionValue()).charAt(0);
			if (Character.isSurrogate(value) || (value > 127 && literal.getEscapedValue().contains("\\u"))) { //$NON-NLS-1$
				// Keep escaped characters representable in the original source encoding.
				replacement.setEscapedValue("'\\u" + HexFormat.of().toHexDigits(value) + "'"); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				replacement.setCharValue(value);
			}
			TextEditGroup group= createTextEditGroup(MultiFixMessages.StringLastIndexOfCleanUp_description, rewrite);
			literal.setProperty(UNTOUCH_COMMENT_PROPERTY, Boolean.TRUE);
			rewrite.getASTRewrite().replace(literal, replacement, group);
			EncodingSourceRewrite.record(rewrite, literal);
		}
	}
}
