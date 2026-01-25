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
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
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
 * Migrates JUnit 4 @Ignore annotations to JUnit 5 @Disabled.
 * 
 * <p>This plugin handles both marker and single-member annotations, preserving
 * the ignore reason when present.</p>
 * 
 * <p>This refactored version replaces the earlier IgnoreJUnitPlugin implementation and
 * uses the TriggerPattern-based declarative architecture to reduce boilerplate.</p>
 * 
 * <p><b>Before:</b></p>
 * <pre>
 * import org.junit.Ignore;
 * 
 * public class MyTest {
 *     {@literal @}Ignore
 *     {@literal @}Test
 *     public void testNotImplemented() { }
 *     
 *     {@literal @}Ignore("not ready yet")
 *     {@literal @}Test
 *     public void testNotReady() { }
 * }
 * </pre>
 * 
 * <p><b>After:</b></p>
 * <pre>
 * import org.junit.jupiter.api.Disabled;
 * 
 * public class MyTest {
 *     {@literal @}Disabled
 *     {@literal @}Test
 *     public void testNotImplemented() { }
 *     
 *     {@literal @}Disabled("not ready yet")
 *     {@literal @}Test
 *     public void testNotReady() { }
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
@CleanupPattern(
    value = "@Ignore",
    kind = PatternKind.ANNOTATION,
    qualifiedType = ORG_JUNIT_IGNORE,
    cleanupId = "cleanup.junit.ignore",
    description = "Migrate @Ignore to @Disabled",
    displayName = "JUnit 4 @Ignore → JUnit 5 @Disabled"
)
public class IgnoreJUnitPluginV2 extends TriggerPatternCleanupPlugin {

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation annotation = junitHolder.getAnnotation();
		Annotation newAnnotation;
		
		// Preserve ignore reason if present
		if (annotation instanceof SingleMemberAnnotation) {
			SingleMemberAnnotation oldAnnotation = (SingleMemberAnnotation) annotation;
			SingleMemberAnnotation newSingleMemberAnnotation = ast.newSingleMemberAnnotation();
			newSingleMemberAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_DISABLED));
			// Move the value (reason) from old to new annotation
			newSingleMemberAnnotation.setValue(ASTNodes.createMoveTarget(rewriter, oldAnnotation.getValue()));
			newAnnotation = newSingleMemberAnnotation;
		} else {
			// MarkerAnnotation - no reason to preserve
			MarkerAnnotation newMarkerAnnotation = ast.newMarkerAnnotation();
			newMarkerAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_DISABLED));
			newAnnotation = newMarkerAnnotation;
		}
		
		ASTNodes.replaceButKeepComment(rewriter, annotation, newAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_IGNORE);
		importRewriter.addImport(ORG_JUNIT_JUPITER_DISABLED);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				@Disabled("not implemented")
				@Test
				public void test() {
					fail("Not yet implemented");
				}
				"""; //$NON-NLS-1$
		}
		return """
			@Ignore("not implemented")
			@Test
			public void test() {
				fail("Not yet implemented");
			}
			"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Ignore (TriggerPattern)"; //$NON-NLS-1$
	}
}
