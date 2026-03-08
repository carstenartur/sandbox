/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Plugin to migrate JUnit 4 TestName rule to JUnit 5 TestInfo parameter.
 *
 * @since 1.3.0
 */
@CleanupPattern(value = "@Rule public TestName $name", kind = PatternKind.FIELD, qualifiedType = ORG_JUNIT_RULES_TEST_NAME, cleanupId = "cleanup.junit.ruletestname", description = "Migrate @Rule TestName to TestInfo parameter", displayName = "JUnit 4 @Rule TestName \u2192 JUnit 5 TestInfo")
public class RuleTestnameJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		FieldDeclaration fieldDecl = (FieldDeclaration) match.getMatchedNode();
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) fieldDecl.fragments().get(0);
		if (fragment.resolveBinding() == null) {
			return null;
		}
		ITypeBinding binding = fragment.resolveBinding().getType();
		if (binding == null || !ORG_JUNIT_RULES_TEST_NAME.equals(binding.getQualifiedName())) {
			return null;
		}
		JunitHolder holder = new JunitHolder();
		holder.setMinv(fieldDecl);
		return holder;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		FieldDeclaration node = junitHolder.getFieldDeclaration();
		refactorTestnameInClassAndSubclasses(group, rewriter, ast, importRewriter, node);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
						private String testName;

						@BeforeEach
						void init(TestInfo testInfo) {
							this.testName = testInfo.getDisplayName();
						}
						@Test
						public void test(){
							System.out.println("Test name: " + testName);
						}
					"""; //$NON-NLS-1$
		}
		return """
					@Rule
					public TestName tn = new TestName();

					@Test
					public void test(){
						System.out.println("Test name: " + tn.getMethodName());
					}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleTestname"; //$NON-NLS-1$
	}
}
