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

import java.util.List;

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
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Migrates JUnit 4 @Ignore annotations to JUnit 5 @Disabled.
 * 
 * <p>This plugin handles both marker and single-member annotations, preserving
 * the ignore reason when present.</p>
 * 
 * <p>This refactored version uses the TriggerPattern-based declarative architecture.
 * Note: @RewriteRule cannot be used here because we need to handle three different
 * annotation patterns (marker, single-member, and normal), so we keep the custom
 * process2Rewrite() implementation.</p>
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
	protected List<Pattern> getPatterns() {
		// Need to explicitly match all three annotation types
		return List.of(
			// Match @Ignore (MarkerAnnotation)
			new Pattern("@Ignore", PatternKind.ANNOTATION, ORG_JUNIT_IGNORE),
			// Match @Ignore("reason") (SingleMemberAnnotation) 
			new Pattern("@Ignore($value)", PatternKind.ANNOTATION, ORG_JUNIT_IGNORE),
			// Match @Ignore(value="reason") (NormalAnnotation)
			new Pattern("@Ignore(value=$value)", PatternKind.ANNOTATION, ORG_JUNIT_IGNORE)
		);
	}

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
		} else if (annotation instanceof org.eclipse.jdt.core.dom.NormalAnnotation) {
			// Handle NormalAnnotation (e.g., @Ignore(value="reason"))
			// Convert to SingleMemberAnnotation for @Disabled
			org.eclipse.jdt.core.dom.NormalAnnotation oldAnnotation = (org.eclipse.jdt.core.dom.NormalAnnotation) annotation;
			@SuppressWarnings("unchecked")
			java.util.List<org.eclipse.jdt.core.dom.MemberValuePair> values = oldAnnotation.values();
			
			// Find the "value" member specifically
			org.eclipse.jdt.core.dom.MemberValuePair valuePair = null;
			for (org.eclipse.jdt.core.dom.MemberValuePair pair : values) {
				if ("value".equals(pair.getName().getIdentifier())) {
					valuePair = pair;
					break;
				}
			}
			
			if (valuePair != null) {
				// If there's a value attribute, extract it and create SingleMemberAnnotation
				SingleMemberAnnotation newSingleMemberAnnotation = ast.newSingleMemberAnnotation();
				newSingleMemberAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_DISABLED));
				newSingleMemberAnnotation.setValue(ASTNodes.createMoveTarget(rewriter, valuePair.getValue()));
				newAnnotation = newSingleMemberAnnotation;
			} else {
				// No value, treat as marker
				MarkerAnnotation newMarkerAnnotation = ast.newMarkerAnnotation();
				newMarkerAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_DISABLED));
				newAnnotation = newMarkerAnnotation;
			}
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
