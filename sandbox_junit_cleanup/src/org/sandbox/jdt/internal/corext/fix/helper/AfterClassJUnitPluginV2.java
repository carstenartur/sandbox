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
 * Migrates JUnit 4 @AfterClass annotations to JUnit 5 @AfterAll.
 * 
 * <p>This is a simplified version using TriggerPattern-based declarative architecture,
 * refactored from the original plugin to reduce boilerplate.</p>
 * 
 * <p><b>Before:</b></p>
 * <pre>
 * import org.junit.AfterClass;
 * 
 * public class MyTest {
 *     {@literal @}AfterClass
 *     public static void tearDownAfterClass() { }
 * }
 * </pre>
 * 
 * <p><b>After:</b></p>
 * <pre>
 * import org.junit.jupiter.api.AfterAll;
 * 
 * public class MyTest {
 *     {@literal @}AfterAll
 *     public static void tearDownAfterClass() { }
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
@CleanupPattern(
    value = "@AfterClass",
    kind = PatternKind.ANNOTATION,
    qualifiedType = ORG_JUNIT_AFTERCLASS,
    cleanupId = "cleanup.junit.afterclass",
    description = "Migrate @AfterClass to @AfterAll",
    displayName = "JUnit 4 @AfterClass → JUnit 5 @AfterAll"
)
@RewriteRule(
    replaceWith = "@AfterAll",
    removeImports = {ORG_JUNIT_AFTERCLASS},
    addImports = {ORG_JUNIT_JUPITER_API_AFTER_ALL}
)
public class AfterClassJUnitPluginV2 extends TriggerPatternCleanupPlugin {

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				@AfterAll
				public static void tearDownAfterClass() {
				}
				"""; //$NON-NLS-1$
		}
		return """
			@AfterClass
			public static void tearDownAfterClass() {
			}
			"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "AfterClass (TriggerPattern)"; //$NON-NLS-1$
	}
}
