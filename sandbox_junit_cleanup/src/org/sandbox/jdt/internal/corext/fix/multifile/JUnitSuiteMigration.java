/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.List;
import java.util.Objects;

/** Immutable relationship between one JUnit 4 suite type and its selected source members. */
public record JUnitSuiteMigration(String suiteCompilationUnitHandle, String suiteTypeBindingKey,
		List<String> referencedTypeBindingKeys, List<String> referencedCompilationUnitHandles) {

	/** Defensively copies the deterministic target sets. */
	public JUnitSuiteMigration {
		Objects.requireNonNull(suiteCompilationUnitHandle);
		Objects.requireNonNull(suiteTypeBindingKey);
		referencedTypeBindingKeys= List.copyOf(referencedTypeBindingKeys);
		referencedCompilationUnitHandles= List.copyOf(referencedCompilationUnitHandles);
		if (referencedTypeBindingKeys.isEmpty()
				|| referencedTypeBindingKeys.size() != referencedCompilationUnitHandles.size()) {
			throw new IllegalArgumentException("A suite migration requires matching non-empty target identities."); //$NON-NLS-1$
		}
	}
}
