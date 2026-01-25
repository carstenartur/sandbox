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
 * Migrates JUnit 4 @BeforeClass annotations to JUnit 5 @BeforeAll.
 * 
 * <p>This is a simplified version using TriggerPattern-based declarative architecture.
 * Compare with the original {@link BeforeClassJUnitPlugin} to see the reduction in boilerplate.</p>
 * 
 * <p><b>Before:</b></p>
 * <pre>
 * import org.junit.BeforeClass;
 * 
 * public class MyTest {
 *     {@literal @}BeforeClass
 *     public static void setUpBeforeClass() { }
 * }
 * </pre>
 * 
 * <p><b>After:</b></p>
 * <pre>
 * import org.junit.jupiter.api.BeforeAll;
 * 
 * public class MyTest {
 *     {@literal @}BeforeAll
 *     public static void setUpBeforeClass() { }
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
@CleanupPattern(
    value = "@BeforeClass",
    kind = PatternKind.ANNOTATION,
    qualifiedType = ORG_JUNIT_BEFORECLASS,
    cleanupId = "cleanup.junit.beforeclass",
    description = "Migrate @BeforeClass to @BeforeAll",
    displayName = "JUnit 4 @BeforeClass → JUnit 5 @BeforeAll"
)
public class BeforeClassJUnitPluginV2 extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		// Call parent implementation to get holder with bindings set
		JunitHolder holder = super.createHolder(match);
		// No additional customization needed for @BeforeClass (no placeholders)
		return holder;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation annotation = junitHolder.getAnnotation();
		MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
		newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_BEFORE_ALL));
		ASTNodes.replaceButKeepComment(rewriter, annotation, newAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_BEFORECLASS);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_BEFORE_ALL);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				@BeforeAll
				public static void setUpBeforeClass() {
				}
				"""; //$NON-NLS-1$
		}
		return """
			@BeforeClass
			public static void setUpBeforeClass() {
			}
			"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "BeforeClass (TriggerPattern)"; //$NON-NLS-1$
	}
}
