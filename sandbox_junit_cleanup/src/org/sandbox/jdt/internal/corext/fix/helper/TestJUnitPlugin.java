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

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

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
