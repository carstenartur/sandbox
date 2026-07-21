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

import java.util.List;
import java.util.Map;

/**
 * Immutable identity and expected reference counts for one package-scoped
 * integer state domain.
 *
 * @param ownerCompilationUnitHandle compilation unit declaring constants and method
 * @param ownerTypeBindingKey declaring type binding key
 * @param ownerTypeQualifiedName qualified type name used for caller replacements
 * @param methodBindingKey migrated method declaration binding key
 * @param parameterIndex index of the state parameter
 * @param prefix common source constant prefix
 * @param enumTypeName generated nested enum name
 * @param constants constants in declaration order
 * @param expectedReferenceCountsByUnit expected replaceable constant-reference counts per unit and binding key
 * @param expectedCallCountsByUnit expected invocations of the migrated method per unit
 */
public record IntEnumCandidate(String ownerCompilationUnitHandle, String ownerTypeBindingKey,
		String ownerTypeQualifiedName, String methodBindingKey, int parameterIndex, String prefix,
		String enumTypeName, List<IntEnumConstant> constants,
		Map<String, Map<String, Integer>> expectedReferenceCountsByUnit,
		Map<String, Integer> expectedCallCountsByUnit) {

	/** Defensively copies nested collections. */
	public IntEnumCandidate {
		constants= List.copyOf(constants);
		expectedReferenceCountsByUnit= expectedReferenceCountsByUnit.entrySet().stream()
				.collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
						entry -> Map.copyOf(entry.getValue())));
		expectedCallCountsByUnit= Map.copyOf(expectedCallCountsByUnit);
	}

	/** Finds a constant by binding key. */
	public IntEnumConstant constant(String bindingKey) {
		return constants.stream().filter(constant -> bindingKey.equals(constant.bindingKey())).findFirst().orElse(null);
	}
}
