/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

/**
 * Immutable identity of one JUnit 4 rule field and its named ExternalResource
 * declaration in another compilation unit.
 *
 * @param ruleCompilationUnitHandle handle of the test compilation unit
 * @param fieldBindingKey binding key of the rule field fragment
 * @param resourceCompilationUnitHandle handle of the ExternalResource compilation unit
 * @param resourceTypeBindingKey binding key of the named ExternalResource type
 * @param classRule whether the field is a JUnit 4 {@code @ClassRule}
 */
public record ExternalResourceRuleMigration(String ruleCompilationUnitHandle, String fieldBindingKey,
		String resourceCompilationUnitHandle, String resourceTypeBindingKey, boolean classRule) {
}
