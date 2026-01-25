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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Migrates JUnit 4 @Before annotations to JUnit 5 @BeforeEach.
 * 
 * <p>This is a simplified version using TriggerPattern-based declarative architecture.
 * Compare with the original {@link BeforeJUnitPlugin} to see the reduction in boilerplate.</p>
 * 
 * <p><b>Before:</b></p>
 * <pre>
 * import org.junit.Before;
 * 
 * public class MyTest {
 *     {@literal @}Before
 *     public void setUp() { }
 * }
 * </pre>
 * 
 * <p><b>After:</b></p>
 * <pre>
 * import org.junit.jupiter.api.BeforeEach;
 * 
 * public class MyTest {
 *     {@literal @}BeforeEach
 *     public void setUp() { }
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
@CleanupPattern(
    value = "@Before",
    kind = PatternKind.ANNOTATION,
    qualifiedType = ORG_JUNIT_BEFORE,
    cleanupId = "cleanup.junit.before",
    description = "Migrate @Before to @BeforeEach",
    displayName = "JUnit 4 @Before → JUnit 5 @BeforeEach"
)
public class BeforeJUnitPluginV2 extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		// Call parent implementation to get holder with bindings set
		JunitHolder holder = super.createHolder(match);
		// No additional customization needed for @Before (no placeholders)
		return holder;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation annotation = junitHolder.getAnnotation();
		MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
		newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_BEFORE_EACH));
		ASTNodes.replaceButKeepComment(rewriter, annotation, newAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_BEFORE);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_BEFORE_EACH);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				@BeforeEach
				public void setUp() {
				}
				"""; //$NON-NLS-1$
		}
		return """
			@Before
			public void setUp() {
			}
			"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Before (TriggerPattern)"; //$NON-NLS-1$
	}
}
