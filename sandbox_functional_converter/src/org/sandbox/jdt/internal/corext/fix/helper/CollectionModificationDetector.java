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
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;

/**
 * Shared utility for detecting structural modifications on a collection.
 * 
 * <p>Used by both {@link PreconditionsChecker} and
 * {@link JdtLoopExtractor.LoopBodyAnalyzer} to ensure consistent detection
 * of collection modifications that block loop-to-stream conversions.</p>
 * 
 * <p><b>Supported Receivers:</b></p>
 * <ul>
 * <li>Simple names: {@code list.remove(x)}</li>
 * <li>Field access: {@code this.list.remove(x)}</li>
 * </ul>
 * 
 * <p><b>Limitation:</b> Does not detect modifications via method return values
 * ({@code getList().remove(x)}) or array access ({@code arrays[0].clear()}).
 * This is an intentional conservative limitation.</p>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 * @since 1.0.0
 */
public final class CollectionModificationDetector {

	/**
	 * Method names that represent structural modifications on a collection.
	 * Calling any of these on the iterated collection causes
	 * ConcurrentModificationException with fail-fast iterators.
	 * 
	 * <p>Includes:</p>
	 * <ul>
	 * <li>Collection methods: add, remove, clear, addAll, removeAll, retainAll, removeIf, replaceAll, sort</li>
	 * <li>List methods: set</li>
	 * <li>Map methods: put, putAll, putIfAbsent, compute, computeIfAbsent, computeIfPresent, merge, replace, replaceAll</li>
	 * </ul>
	 */
	private static final Set<String> MODIFYING_METHODS = Set.of(
			// Collection/List methods
			"remove", "add", "clear", "set", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"addAll", "removeAll", "retainAll", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"removeIf", "replaceAll", "sort", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			// Map methods
			"put", "putAll", "putIfAbsent", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"compute", "computeIfAbsent", "computeIfPresent", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"merge", "replace"); //$NON-NLS-1$ //$NON-NLS-2$

	private CollectionModificationDetector() {
		// utility class
	}

	/**
	 * Checks if a method invocation is a structural modification on a named collection.
	 * 
	 * <p>Detects calls to structural modification methods on the given collection variable.
	 * Supports both simple names ({@code list.remove(x)}) and field access
	 * ({@code this.list.remove(x)}).</p>
	 * 
	 * @param methodInv the method invocation to check
	 * @param collectionName the name of the iterated collection variable
	 * @return {@code true} if this is a structural modification on the named collection
	 */
	public static boolean isModification(MethodInvocation methodInv, String collectionName) {
		Expression receiver = methodInv.getExpression();
		
		// Check for simple name receiver: list.remove(x)
		if (receiver instanceof SimpleName receiverName) {
			if (collectionName.equals(receiverName.getIdentifier())) {
				String methodName = methodInv.getName().getIdentifier();
				return MODIFYING_METHODS.contains(methodName);
			}
		}
		
		// Check for field access receiver: this.list.remove(x)
		if (receiver instanceof FieldAccess fieldAccess) {
			Expression fieldExpression = fieldAccess.getExpression();
			// Check if it's "this.fieldName"
			if (fieldExpression instanceof ThisExpression) {
				SimpleName fieldName = fieldAccess.getName();
				if (collectionName.equals(fieldName.getIdentifier())) {
					String methodName = methodInv.getName().getIdentifier();
					return MODIFYING_METHODS.contains(methodName);
				}
			}
		}
		
		return false;
	}
}
