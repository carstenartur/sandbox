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
 * Migrates JUnit 4 @After annotations to JUnit 5 @AfterEach.
 * 
 * <p>Uses TriggerPattern-based declarative architecture
 * with @RewriteRule annotation to eliminate boilerplate code.</p>
 * 
 * <p><b>Before:</b></p>
 * <pre>
 * import org.junit.After;
 * 
 * public class MyTest {
 *     {@literal @}After
 *     public void tearDown() { }
 * }
 * </pre>
 * 
 * <p><b>After:</b></p>
 * <pre>
 * import org.junit.jupiter.api.AfterEach;
 * 
 * public class MyTest {
 *     {@literal @}AfterEach
 *     public void tearDown() { }
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
@CleanupPattern(
    value = "@After",
    kind = PatternKind.ANNOTATION,
    qualifiedType = ORG_JUNIT_AFTER,
    cleanupId = "cleanup.junit.after",
    description = "Migrate @After to @AfterEach",
    displayName = "JUnit 4 @After → JUnit 5 @AfterEach"
)
@RewriteRule(
    replaceWith = "@AfterEach",
    removeImports = {ORG_JUNIT_AFTER},
    addImports = {ORG_JUNIT_JUPITER_API_AFTER_EACH}
)
public class AfterJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				@AfterEach
				public void tearDown() {
				}
				"""; //$NON-NLS-1$
		}
		return """
			@After
			public void tearDown() {
			}
			"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "After"; //$NON-NLS-1$
	}
}
