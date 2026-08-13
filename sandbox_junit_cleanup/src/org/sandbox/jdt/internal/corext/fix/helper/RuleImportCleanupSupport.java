/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_TEMPORARY_FOLDER;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_TEST_NAME;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TestNameRefactorer;

/** Coordinates removal of the shared {@code org.junit.Rule} import. */
public final class RuleImportCleanupSupport {

	private RuleImportCleanupSupport() {
		throw new UnsupportedOperationException("Utility class"); //$NON-NLS-1$
	}

	/**
	 * Adds one final import edit when every {@code @Rule} field in this compilation
	 * unit is a selected and eligibility-proven TemporaryFolder or TestName rewrite.
	 * Other rule kinds remain fail-closed and retain their import.
	 */
	public static void addIfSafe(CompilationUnit root, EnumSet<JUnitCleanUpFixCore> fixes,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations) {
		if (root == null || fixes == null || operations == null || operations.isEmpty()) {
			return;
		}
		boolean[] sawRule= { false };
		boolean[] allSelectedAndEligible= { true };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(FieldDeclaration field) {
				if (!hasRuleAnnotation(field)) {
					return true;
				}
				sawRule[0]= true;
				allSelectedAndEligible[0]&= isSelectedAndEligible(field, fixes);
				return allSelectedAndEligible[0];
			}
		});
		if (sawRule[0] && allSelectedAndEligible[0]) {
			operations.add(new RemoveRuleImportOperation());
		}
	}

	private static boolean isSelectedAndEligible(FieldDeclaration field,
			EnumSet<JUnitCleanUpFixCore> fixes) {
		ITypeBinding binding= field.getType().resolveBinding();
		String qualifiedType= binding == null ? null : binding.getErasure().getQualifiedName();
		if (ORG_JUNIT_RULES_TEMPORARY_FOLDER.equals(qualifiedType)) {
			return fixes.contains(JUnitCleanUpFixCore.RULETEMPORARYFOLDER)
					&& RuleTemporayFolderJUnitPlugin.assess(field).eligible();
		}
		if (ORG_JUNIT_RULES_TEST_NAME.equals(qualifiedType)) {
			return fixes.contains(JUnitCleanUpFixCore.RULETESTNAME)
					&& TestNameRefactorer.assess(field).eligible();
		}
		return false;
	}

	private static boolean hasRuleAnnotation(FieldDeclaration field) {
		for (Object modifier : field.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && ORG_JUNIT_RULE.equals(binding.getQualifiedName())) {
					return true;
				}
			}
		}
		return false;
	}

	private static final class RemoveRuleImportOperation
			extends CompilationUnitRewriteOperationWithSourceRange {

		@Override
		public void rewriteASTInternal(CompilationUnitRewrite cuRewrite,
				LinkedProposalModelCore linkedModel) {
			ImportRewrite imports= cuRewrite.getImportRewrite();
			imports.removeImport(ORG_JUNIT_RULE);
		}
	}
}
