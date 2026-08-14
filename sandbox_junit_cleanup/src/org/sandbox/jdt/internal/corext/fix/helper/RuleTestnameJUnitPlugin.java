/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_TEST_NAME;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TestNameRefactorer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/** Migrates a binding-proven local JUnit 4 TestName rule to TestInfo. */
@CleanupPattern(value = "@Rule public TestName $name", kind = PatternKind.FIELD,
		qualifiedType = ORG_JUNIT_RULES_TEST_NAME, cleanupId = "cleanup.junit.ruletestname",
		description = "Migrate @Rule TestName to exact TestInfo method-name semantics",
		displayName = "JUnit 4 @Rule TestName → JUnit 5 TestInfo")
public class RuleTestnameJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		FieldDeclaration field= (FieldDeclaration) match.getMatchedNode();
		if (field.fragments().size() != 1
				|| !(field.fragments().get(0) instanceof VariableDeclarationFragment fragment)
				|| fragment.resolveBinding() == null) {
			return null;
		}
		ITypeBinding binding= fragment.resolveBinding().getType();
		if (binding == null || !ORG_JUNIT_RULES_TEST_NAME.equals(binding.getQualifiedName())
				|| !TestNameRefactorer.assess(field).eligible()) {
			return null;
		}
		JunitHolder holder= new JunitHolder();
		holder.setMinv(field);
		return holder;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		refactorTestnameInClassAndSubclasses(group, rewriter, ast, importRewriter,
				junitHolder.getFieldDeclaration());
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					public String tn;

					@BeforeEach
					void initializeTnFromTestInfo(TestInfo testInfo) {
						this.tn = testInfo.getTestMethod().orElseThrow().getName();
					}

					@Test
					public void testExample() {
						System.out.println("Test method: " + tn);
					}
					"""; //$NON-NLS-1$
		}
		return """
					@Rule
					public TestName tn = new TestName();

					@Test
					public void testExample() {
						System.out.println("Test method: " + tn.getMethodName());
					}
					"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleTestName"; //$NON-NLS-1$
	}
}
