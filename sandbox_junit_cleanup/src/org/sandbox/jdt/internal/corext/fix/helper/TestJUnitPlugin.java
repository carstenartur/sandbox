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
 * Migrates JUnit 4 @Test annotations to JUnit 5 @Test.
 * 
 * <p>
 * This plugin only handles marker annotations (without expected/timeout
 * attributes).
 * 
 * @Test annotations with expected or timeout are handled by separate plugins.
 *       </p>
 * 
 *       <p>
 *       Uses TriggerPattern-based declarative architecture to reduce
 *       boilerplate.
 *       </p>
 * 
 *       <p>
 *       <b>Before:</b>
 *       </p>
 * 
 *       <pre>
 * import org.junit.Test;
 * 
 * public class MyTest {
 *     {@literal @}Test
 *     public void testMethod() { }
 * }
 * </pre>
 * 
 *       <p>
 *       <b>After:</b>
 *       </p>
 * 
 *       <pre>
 * import org.junit.jupiter.api.Test;
 * 
 * public class MyTest {
 *     {@literal @}Test
 *     public void testMethod() { }
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
@CleanupPattern(value = "@Test", kind = PatternKind.ANNOTATION, qualifiedType = ORG_JUNIT_TEST, cleanupId = "cleanup.junit.test", description = "Migrate @Test to JUnit 5", displayName = "JUnit 4 @Test → JUnit 5 @Test")
@RewriteRule(replaceWith = "@Test", addImports = { ORG_JUNIT_JUPITER_TEST })
public class TestJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import org.junit.jupiter.api.Test;
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.Test;
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Test"; //$NON-NLS-1$
	}
}
