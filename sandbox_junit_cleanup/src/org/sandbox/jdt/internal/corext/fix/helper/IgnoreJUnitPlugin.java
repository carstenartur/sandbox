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

import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;

/**
 * Migrates JUnit 4 @Ignore annotations to JUnit 5 @Disabled.
 * 
 * <p>
 * This plugin handles both marker and single-member annotations, preserving the
 * ignore reason when present.
 * </p>
 * 
 * <p>
 * Uses the TriggerPattern-based declarative architecture with @RewriteRule
 * annotation. Handles three different annotation types (marker, single-member,
 * and normal) through multiple patterns defined in {@code getPatterns()}, with
 * the framework automatically handling NormalAnnotation value extraction.
 * </p>
 * 
 * <p>
 * <b>Before:</b>
 * </p>
 * 
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
 * <p>
 * <b>After:</b>
 * </p>
 * 
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
@CleanupPattern(value = "@Ignore", kind = PatternKind.ANNOTATION, qualifiedType = ORG_JUNIT_IGNORE, cleanupId = "cleanup.junit.ignore", description = "Migrate @Ignore to @Disabled", displayName = "JUnit 4 @Ignore → JUnit 5 @Disabled")
@RewriteRule(replaceWith = "@Disabled($value)", targetQualifiedType = ORG_JUNIT_JUPITER_DISABLED)
public class IgnoreJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected List<Pattern> getPatterns() {
		// Need to explicitly match all three annotation types
		return List.of(
				// Match @Ignore (MarkerAnnotation)
				new Pattern("@Ignore", PatternKind.ANNOTATION, null, null, ORG_JUNIT_IGNORE, null, null),
				// Match @Ignore("reason") (SingleMemberAnnotation)
				new Pattern("@Ignore($value)", PatternKind.ANNOTATION, null, null, ORG_JUNIT_IGNORE, null, null),
				// Match @Ignore(value="reason") (NormalAnnotation)
				new Pattern("@Ignore(value=$value)", PatternKind.ANNOTATION, null, null, ORG_JUNIT_IGNORE, null, null));
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
		return "Ignore"; //$NON-NLS-1$
	}
}
