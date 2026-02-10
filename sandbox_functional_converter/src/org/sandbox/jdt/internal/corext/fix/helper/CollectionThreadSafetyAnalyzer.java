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

import org.eclipse.jdt.core.dom.*;

/**
 * Analyzes collection thread-safety for loop conversion decisions.
 * 
 * <p>When converting index-based for-loops that access collections via get(i),
 * the conversion to forEach/stream changes the iteration semantics.
 * This analyzer determines if the conversion is safe based on:</p>
 * <ol>
 *   <li>Collection type (concurrent-safe types are always safe)</li>
 *   <li>Collection origin (locally-created collections are safe)</li>
 *   <li>Immutability (unmodifiable/List.of() collections are safe)</li>
 * </ol>
 */
public class CollectionThreadSafetyAnalyzer {

	/**
	 * Safety level for collection thread-safety analysis.
	 */
	public enum SafetyLevel {
		/** Collection created locally in same method, never escapes */
		LOCAL_ONLY,
		/** Concurrent-safe type (CopyOnWriteArrayList, ConcurrentLinkedQueue, etc.) */
		CONCURRENT_SAFE,
		/** Immutable (List.of(), Collections.unmodifiableList(), etc.) */
		IMMUTABLE,
		/** Wrapped in synchronized wrapper */
		SYNCHRONIZED_WRAPPER,
		/** Unknown origin, potentially shared between threads */
		POTENTIALLY_SHARED
	}

	private static final Set<String> CONCURRENT_SAFE_TYPES = Set.of(
		"CopyOnWriteArrayList", //$NON-NLS-1$
		"CopyOnWriteArraySet", //$NON-NLS-1$
		"ConcurrentLinkedQueue", //$NON-NLS-1$
		"ConcurrentLinkedDeque", //$NON-NLS-1$
		"ConcurrentSkipListSet" //$NON-NLS-1$
	);

	private static final Set<String> IMMUTABLE_FACTORY_METHODS = Set.of(
		"of", //$NON-NLS-1$
		"copyOf" //$NON-NLS-1$
	);

	private static final Set<String> SYNCHRONIZED_WRAPPER_METHODS = Set.of(
		"synchronizedList", //$NON-NLS-1$
		"synchronizedSet", //$NON-NLS-1$
		"synchronizedCollection" //$NON-NLS-1$
	);

	private static final Set<String> UNMODIFIABLE_WRAPPER_METHODS = Set.of(
		"unmodifiableList", //$NON-NLS-1$
		"unmodifiableSet", //$NON-NLS-1$
		"unmodifiableCollection" //$NON-NLS-1$
	);

	/**
	 * Analyzes the thread-safety of a collection expression.
	 *
	 * @param collectionExpr the collection expression to analyze
	 * @param scope the enclosing method or block for scope analysis
	 * @return the safety level of the collection
	 */
	public SafetyLevel analyze(Expression collectionExpr, ASTNode scope) {
		if (collectionExpr == null) {
			return SafetyLevel.POTENTIALLY_SHARED;
		}

		// Check if it's a simple name (variable reference)
		if (collectionExpr instanceof SimpleName simpleName) {
			return analyzeVariable(simpleName, scope);
		}

		// Check if it's a method invocation (e.g., getList(), List.of(...))
		if (collectionExpr instanceof MethodInvocation methodInvocation) {
			return analyzeMethodInvocation(methodInvocation);
		}

		// Check if it's a class instance creation (new ArrayList<>())
		if (collectionExpr instanceof ClassInstanceCreation) {
			return SafetyLevel.LOCAL_ONLY;
		}

		return SafetyLevel.POTENTIALLY_SHARED;
	}

	/**
	 * Checks if the safety level allows index elimination conversion.
	 *
	 * @param level the safety level to check
	 * @return true if the collection is safe for forEach conversion
	 */
	public boolean isSafeForConversion(SafetyLevel level) {
		return level != SafetyLevel.POTENTIALLY_SHARED;
	}

	private SafetyLevel analyzeVariable(SimpleName name, ASTNode scope) {
		IBinding binding = name.resolveBinding();
		if (!(binding instanceof IVariableBinding varBinding)) {
			return SafetyLevel.POTENTIALLY_SHARED;
		}

		// Check if it's a local variable
		if (isLocallyCreated(varBinding, scope)) {
			return SafetyLevel.LOCAL_ONLY;
		}

		// Check the type for concurrent-safe types
		ITypeBinding typeBinding = varBinding.getType();
		if (typeBinding != null && isConcurrentSafeType(typeBinding.getName())) {
			return SafetyLevel.CONCURRENT_SAFE;
		}

		// Fields and parameters are potentially shared
		return SafetyLevel.POTENTIALLY_SHARED;
	}

	private SafetyLevel analyzeMethodInvocation(MethodInvocation methodInvocation) {
		String methodName = methodInvocation.getName().getIdentifier();
		Expression expr = methodInvocation.getExpression();

		// Check for Collections.unmodifiableList(), etc.
		if (expr instanceof SimpleName simpleName) {
			String receiverName = simpleName.getIdentifier();
			if ("Collections".equals(receiverName)) { //$NON-NLS-1$
				if (UNMODIFIABLE_WRAPPER_METHODS.contains(methodName)) {
					return SafetyLevel.IMMUTABLE;
				}
				if (SYNCHRONIZED_WRAPPER_METHODS.contains(methodName)) {
					return SafetyLevel.SYNCHRONIZED_WRAPPER;
				}
			}
			// Check for List.of(), Set.of(), etc.
			if (("List".equals(receiverName) || "Set".equals(receiverName) || "Map".equals(receiverName)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					&& IMMUTABLE_FACTORY_METHODS.contains(methodName)) {
				return SafetyLevel.IMMUTABLE;
			}
		}

		return SafetyLevel.POTENTIALLY_SHARED;
	}

	private boolean isConcurrentSafeType(String typeName) {
		return CONCURRENT_SAFE_TYPES.contains(typeName);
	}

	private boolean isLocallyCreated(IVariableBinding varBinding, ASTNode scope) {
		// Local variables declared in the same method are safe
		return varBinding.isParameter() || (!varBinding.isField() && !varBinding.isEnumConstant());
	}
}
