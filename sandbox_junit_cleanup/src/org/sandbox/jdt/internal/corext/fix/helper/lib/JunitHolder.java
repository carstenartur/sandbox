package org.sandbox.jdt.internal.corext.fix.helper.lib;

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

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Data holder for JUnit migration operations.
 * <p>
 * This class stores information about AST nodes found during the find phase
 * that need to be transformed during the rewrite phase of JUnit migration.
 * Different plugins use this holder to pass different types of AST nodes
 * (annotations, method invocations, field declarations, etc.) to the rewrite operation.
 * </p>
 */
public class JunitHolder {
	/** The main AST node to be transformed (can be Annotation, MethodInvocation, FieldDeclaration, TypeDeclaration, or ImportDeclaration) */
	public ASTNode minv;
	
	/** Name or identifier associated with the node (e.g., annotation name, method name) */
	public String minvname;
	
	/** Set of already processed nodes to avoid duplicate transformations */
	public Set<ASTNode> nodesprocessed;
	
	/** Additional string value for the transformation */
	public String value;
	
	/** Method invocation reference (if applicable) */
	public MethodInvocation method;
	
	/** Counter for tracking multiple transformations */
	public int count;
	
	/** Additional context information for complex transformations */
	public Object additionalInfo;
	
	/** Placeholder bindings from TriggerPattern (e.g., "$x" -> ASTNode or "$args$" -> List<ASTNode>) */
	public Map<String, Object> bindings = new HashMap<>();

	/**
	 * Gets the node as an Annotation.
	 * @return the AST node cast to Annotation
	 */
	public Annotation getAnnotation() {
		return (Annotation) minv;
	}

	/**
	 * Gets the node as a MethodInvocation.
	 * @return the AST node cast to MethodInvocation
	 */
	public MethodInvocation getMethodInvocation() {
		return (MethodInvocation) minv;
	}

	/**
	 * Gets the node as an ImportDeclaration.
	 * @return the AST node cast to ImportDeclaration
	 */
	public ImportDeclaration getImportDeclaration() {
		return (ImportDeclaration) minv;
	}

	/**
	 * Gets the node as a FieldDeclaration.
	 * @return the AST node cast to FieldDeclaration
	 */
	public FieldDeclaration getFieldDeclaration() {
		return (FieldDeclaration) minv;
	}

	/**
	 * Gets the node as a TypeDeclaration.
	 * @return the AST node cast to TypeDeclaration
	 */
	public TypeDeclaration getTypeDeclaration() {
		return (TypeDeclaration) minv;
	}
	
	/**
	 * Gets a placeholder binding as an Expression.
	 * 
	 * @param placeholder the placeholder name (e.g., "$x")
	 * @return the bound expression, or null if not found or if it's a list binding
	 */
	public Expression getBindingAsExpression(String placeholder) {
		Object value = bindings.get(placeholder);
		if (value instanceof Expression) {
			return (Expression) value;
		}
		return null;
	}
	
	/**
	 * Gets a placeholder binding.
	 * 
	 * @param placeholder the placeholder name (e.g., "$x")
	 * @return the bound AST node, or null if not found or if it's a list binding
	 */
	public ASTNode getBinding(String placeholder) {
		Object value = bindings.get(placeholder);
		if (value instanceof ASTNode) {
			return (ASTNode) value;
		}
		return null;
	}
	
	/**
	 * Checks if a placeholder binding exists.
	 * 
	 * @param placeholder the placeholder name
	 * @return true if the binding exists
	 */
	public boolean hasBinding(String placeholder) {
		return bindings.containsKey(placeholder);
	}
}
