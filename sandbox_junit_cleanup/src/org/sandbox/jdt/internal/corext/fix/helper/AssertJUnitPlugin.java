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

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodMigrationPlugin;

/**
 * Migrates JUnit 4 Assert calls to JUnit 5 Assertions.
 * 
 * <p>
 * Special handling:
 * </p>
 * <ul>
 * <li>assertThat → Hamcrest MatcherAssert.assertThat</li>
 * <li>Other assertions → JUnit 5 Assertions with parameter reordering</li>
 * </ul>
 */
public class AssertJUnitPlugin extends AbstractMethodMigrationPlugin {

	@Override
	protected String getSourceClass() {
		return ORG_JUNIT_ASSERT;
	}

	@Override
	protected String getTargetClass() {
		return ORG_JUNIT_JUPITER_API_ASSERTIONS;
	}

	@Override
	protected String getTargetSimpleName() {
		return ASSERTIONS;
	}

	@Override
	protected Set<String> getMethodNames() {
		return ALL_ASSERTION_METHODS;
	}

	@Override
	protected void processMethodInvocation(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, MethodInvocation node) {

		Expression assertexpression = node.getExpression();

		// Special handling for assertThat - delegate to Hamcrest
		if (METHOD_ASSERT_THAT.equals(node.getName().getIdentifier()) && assertexpression instanceof SimpleName
				&& "Assert".equals(((SimpleName) assertexpression).getIdentifier())) {
			rewriter.set(node, MethodInvocation.EXPRESSION_PROPERTY, null, group);
			importRewriter.addStaticImport(ORG_HAMCREST_MATCHER_ASSERT, METHOD_ASSERT_THAT, false);
			importRewriter.removeImport(ORG_JUNIT_ASSERT);
		} else {
			// Standard assertion handling - use base class behavior
			super.processMethodInvocation(group, rewriter, ast, importRewriter, node);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					Assertions.assertNotEquals(5,result, "failuremessage");  // expected = 5, actual = result
					Assertions.assertTrue(false,"failuremessage");
					"""; //$NON-NLS-1$
		}
		return """
				Assert.assertNotEquals("failuremessage",5, result);  // expected = 5, actual = result
				Assert.assertTrue("failuremessage",false);
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Assert"; //$NON-NLS-1$
	}
}
