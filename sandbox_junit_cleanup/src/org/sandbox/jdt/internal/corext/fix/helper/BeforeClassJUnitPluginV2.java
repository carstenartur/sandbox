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

import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;

/**
 * Migrates JUnit 4 @BeforeClass annotations to JUnit 5 @BeforeAll.
 * 
 * <p>This is a simplified version using TriggerPattern-based declarative architecture
 * that replaces the original BeforeClassJUnitPlugin implementation and reduces boilerplate.</p>
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
@RewriteRule(
    replaceWith = "@BeforeAll",
    removeImports = {ORG_JUNIT_BEFORECLASS},
    addImports = {ORG_JUNIT_JUPITER_API_BEFORE_ALL}
)
public class BeforeClassJUnitPluginV2 extends TriggerPatternCleanupPlugin {

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
