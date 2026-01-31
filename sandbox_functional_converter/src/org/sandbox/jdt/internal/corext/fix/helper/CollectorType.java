/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;

/**
 * Types of collector operations supported for COLLECT operations.
 * 
 * <p>Each collector type represents a specific collection pattern and knows how to
 * create the appropriate Collectors method invocation for the stream pipeline.</p>
 * 
 * <ul>
 * <li><b>TO_LIST</b>: Collects elements into a List. 
 *     Pattern: {@code result.add(item)}. 
 *     Maps to: {@code .collect(Collectors.toList())}</li>
 * <li><b>TO_SET</b>: Collects elements into a Set. 
 *     Pattern: {@code set.add(item)}. 
 *     Maps to: {@code .collect(Collectors.toSet())}</li>
 * </ul>
 * 
 * @see ProspectiveOperation
 * @see OperationType#COLLECT
 */
public enum CollectorType {
	
	/**
	 * Collects elements into a List.
	 * Pattern: {@code List<T> result = new ArrayList<>(); for(T item : items) result.add(item);}
	 */
	TO_LIST("toList", StreamConstants.COLLECTORS_CLASS),
	
	/**
	 * Collects elements into a Set.
	 * Pattern: {@code Set<T> result = new HashSet<>(); for(T item : items) result.add(item);}
	 */
	TO_SET("toSet", StreamConstants.COLLECTORS_CLASS);
	
	private final String methodName;
	private final String className;
	
	CollectorType(String methodName, String className) {
		this.methodName = methodName;
		this.className = className;
	}
	
	/**
	 * Returns the collector method name (e.g., "toList", "toSet").
	 * 
	 * @return the method name
	 */
	public String getMethodName() {
		return methodName;
	}
	
	/**
	 * Returns the fully qualified collector class name.
	 * 
	 * @return the class name (e.g., "java.util.stream.Collectors")
	 */
	public String getClassName() {
		return className;
	}
	
	/**
	 * Creates a Collectors method invocation expression.
	 * Example: {@code Collectors.toList()} or {@code Collectors.toSet()}
	 * 
	 * @param ast the AST to create nodes in
	 * @return a MethodInvocation for the Collectors factory method
	 */
	public Expression createCollectorExpression(AST ast) {
		MethodInvocation collectorInvocation = ast.newMethodInvocation();
		collectorInvocation.setExpression(ast.newSimpleName(StreamConstants.COLLECTORS_CLASS_NAME));
		collectorInvocation.setName(ast.newSimpleName(methodName));
		return collectorInvocation;
	}
	
	/**
	 * Determines the appropriate CollectorType based on the collection type binding.
	 * 
	 * @param collectionTypeQualifiedName the fully qualified name of the collection type
	 * @return TO_LIST for List types, TO_SET for Set types, or null if not supported
	 */
	public static CollectorType fromCollectionType(String collectionTypeQualifiedName) {
		if (collectionTypeQualifiedName == null) {
			return null;
		}
		
		// Check for List types
		if (collectionTypeQualifiedName.equals("java.util.List") || 
			collectionTypeQualifiedName.equals("java.util.ArrayList") ||
			collectionTypeQualifiedName.equals("java.util.LinkedList")) {
			return TO_LIST;
		}
		
		// Check for Set types
		if (collectionTypeQualifiedName.equals("java.util.Set") ||
			collectionTypeQualifiedName.equals("java.util.HashSet") ||
			collectionTypeQualifiedName.equals("java.util.TreeSet") ||
			collectionTypeQualifiedName.equals("java.util.LinkedHashSet")) {
			return TO_SET;
		}
		
		return null;
	}
}
