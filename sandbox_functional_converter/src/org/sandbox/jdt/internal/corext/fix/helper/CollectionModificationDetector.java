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
 * <li>Method invocation (getter pattern): {@code getList().remove(x)}</li>
 * </ul>
 * 
 * <p><b>Method Invocation Heuristic:</b> For method invocations, matches getter
 * method names against collection names. For example, {@code getList().add(x)}
 * is detected when iterating over {@code list}. Supports common getter patterns:
 * {@code getXxx()}, {@code fetchXxx()}, {@code retrieveXxx()}, etc.</p>
 * 
 * <p><b>Limitation:</b> Does not detect modifications via array access
 * ({@code arrays[0].clear()}). This is an intentional conservative limitation.</p>
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
	 * Supports simple names ({@code list.remove(x)}), field access
	 * ({@code this.list.remove(x)}), and method invocation receivers
	 * ({@code getList().remove(x)}).</p>
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
		
		// Check for method invocation receiver: getList().remove(x)
		if (receiver instanceof MethodInvocation getterInvocation) {
			if (matchesGetterPattern(getterInvocation, collectionName)) {
				String methodName = methodInv.getName().getIdentifier();
				return MODIFYING_METHODS.contains(methodName);
			}
		}
		
		return false;
	}
	
	/**
	 * Common getter method prefixes used in heuristic matching.
	 */
	private static final String[] GETTER_PREFIXES = { 
			"get", "fetch", "retrieve", "obtain" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	};
	
	/**
	 * Checks if a method invocation matches a getter pattern for the given collection name.
	 * 
	 * <p>Matches common getter patterns like:</p>
	 * <ul>
	 * <li>{@code getList()} → {@code list}</li>
	 * <li>{@code fetchItems()} → {@code items}</li>
	 * <li>{@code retrieveMap()} → {@code map}</li>
	 * </ul>
	 * 
	 * @param methodInv the method invocation to check
	 * @param collectionName the expected collection name
	 * @return {@code true} if the method name matches a getter pattern for the collection
	 */
	private static boolean matchesGetterPattern(MethodInvocation methodInv, String collectionName) {
		// Only consider no-arg methods (simple getters)
		if (!methodInv.arguments().isEmpty()) {
			return false;
		}
		
		String methodName = methodInv.getName().getIdentifier();
		
		for (String prefix : GETTER_PREFIXES) {
			if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
				// Extract the property name after the prefix (e.g., "List" from "getList")
				String propertyName = methodName.substring(prefix.length());
				
				// Convert first char to lowercase to get the expected variable name
				String expectedName = Character.toLowerCase(propertyName.charAt(0)) + 
						propertyName.substring(1);
				
				if (collectionName.equals(expectedName)) {
					return true;
				}
			}
		}
		
		return false;
	}
}
