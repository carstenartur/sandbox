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

import java.util.Set;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * Shared utility for detecting structural modifications on a collection.
 * 
 * <p>Used by both {@link PreconditionsChecker} and
 * {@link JdtLoopExtractor.LoopBodyAnalyzer} to ensure consistent detection
 * of collection modifications that block loop-to-stream conversions.</p>
 * 
 * <p><b>Limitation:</b> Only detects modifications on {@link SimpleName} receivers
 * (e.g., {@code list.remove(x)}). Does not detect modifications via field access
 * ({@code this.list.remove(x)}), method return values ({@code getList().remove(x)}),
 * or array access ({@code arrays[0].clear()}). This is an intentional conservative
 * limitation — the detector focuses on the most common loop patterns where the
 * iterated collection is referenced by a local variable name.</p>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 * @since 1.0.0
 */
public final class CollectionModificationDetector {

	/**
	 * Method names that represent structural modifications on a collection.
	 * Calling any of these on the iterated collection causes
	 * ConcurrentModificationException with fail-fast iterators.
	 */
	private static final Set<String> MODIFYING_METHODS = Set.of(
			"remove", "add", "put", "clear", "set", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"addAll", "removeAll", "retainAll"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private CollectionModificationDetector() {
		// utility class
	}

	/**
	 * Checks if a method invocation is a structural modification on a named collection.
	 * 
	 * <p>Detects calls to structural modification methods ({@code remove}, {@code add},
	 * {@code put}, {@code clear}, {@code set}, {@code addAll}, {@code removeAll},
	 * {@code retainAll}) on the given collection variable.</p>
	 * 
	 * @param methodInv the method invocation to check
	 * @param collectionName the name of the iterated collection variable
	 * @return {@code true} if this is a structural modification on the named collection
	 */
	public static boolean isModification(MethodInvocation methodInv, String collectionName) {
		Expression receiver = methodInv.getExpression();
		if (!(receiver instanceof SimpleName receiverName)) {
			return false;
		}

		if (!collectionName.equals(receiverName.getIdentifier())) {
			return false;
		}

		String methodName = methodInv.getName().getIdentifier();
		return MODIFYING_METHODS.contains(methodName);
	}
}
