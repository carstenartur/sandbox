/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;

/**
 * Shared utility for detecting chained intermediate stream operations.
 * 
 * <p>Used by {@link StreamToEnhancedFor} and {@link StreamToIteratorWhile}
 * to ensure reverse conversions only apply to simple forEach patterns
 * (collection.forEach or collection.stream().forEach) and not to
 * pipelines with intermediate operations that would be lost.</p>
 * 
 * @since 1.0.0
 */
public final class StreamOperationDetector {

	private StreamOperationDetector() {
		// utility class
	}

	/**
	 * Checks if a forEach call has chained intermediate stream operations.
	 * 
	 * <p>Patterns like {@code list.stream().filter(...).forEach(...)} or
	 * {@code list.stream().map(...).forEach(...)} cannot be safely converted
	 * to a simple loop because the intermediate operations (filter, map,
	 * flatMap, sorted, distinct, limit, skip, peek) would be lost.</p>
	 * 
	 * <p>Only {@code collection.forEach(...)} and
	 * {@code collection.stream().forEach(...)} (with no intermediate
	 * operations) are convertible.</p>
	 * 
	 * @param forEach the forEach method invocation to check
	 * @return {@code true} if there are chained stream operations that block conversion
	 */
	public static boolean hasChainedStreamOperations(MethodInvocation forEach) {
		Expression receiver = forEach.getExpression();
		if (!(receiver instanceof MethodInvocation)) {
			return false; // collection.forEach() — no chain
		}
		MethodInvocation chainedCall = (MethodInvocation) receiver;
		String methodName = chainedCall.getName().getIdentifier();

		// collection.stream().forEach() is OK — no intermediate ops
		if ("stream".equals(methodName) || "parallelStream".equals(methodName)) { //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}

		// Any other chained method (filter, map, flatMap, sorted, distinct,
		// limit, skip, peek, etc.) means there are intermediate operations
		return true;
	}
}
