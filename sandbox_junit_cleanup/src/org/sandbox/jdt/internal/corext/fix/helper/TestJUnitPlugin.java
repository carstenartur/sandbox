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

import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMarkerAnnotationJUnitPlugin;

/**
 * Plugin to migrate JUnit 4 @Test annotations to JUnit 5 @Test.
 */
public class TestJUnitPlugin extends AbstractMarkerAnnotationJUnitPlugin {

	@Override
	protected String getSourceAnnotation() {
		return ORG_JUNIT_TEST;
	}

	@Override
	protected String getTargetAnnotationName() {
		return ANNOTATION_TEST;
	}

	@Override
	protected String getTargetAnnotationImport() {
		return ORG_JUNIT_JUPITER_TEST;
	}
	
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
