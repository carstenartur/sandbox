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
import org.sandbox.jdt.triggerpattern.cleanup.MatchHolder;

/**
 * Data holder for JUnit migration operations.
 * <p>
 * This class stores information about AST nodes found during the find phase
 * that need to be transformed during the rewrite phase of JUnit migration.
 * Different plugins use this holder to pass different types of AST nodes
 * (annotations, method invocations, field declarations, etc.) to the rewrite operation.
 * </p>
 * <p>
 * Refactored to use private fields with fluent setters for better encapsulation.
 * A Builder inner class is provided for convenient construction.
 * </p>
 * <p>
 * Implements {@link MatchHolder} interface for type-safe integration with
 * {@link org.sandbox.jdt.triggerpattern.cleanup.AbstractPatternCleanupPlugin}.
 * </p>
 */
public class JunitHolder implements MatchHolder {
	/** The main AST node to be transformed (can be Annotation, MethodInvocation, FieldDeclaration, TypeDeclaration, or ImportDeclaration) */
	private ASTNode minv;
	
	/** Name or identifier associated with the node (e.g., annotation name, method name) */
	private String minvname;
	
	/** Set of already processed nodes to avoid duplicate transformations */
	private Set<ASTNode> nodesprocessed;
	
	/** Additional string value for the transformation */
	private String value;
	
	/** Method invocation reference (if applicable) */
	private MethodInvocation method;
	
	/** Counter for tracking multiple transformations */
	private int count;
	
	/** Additional context information for complex transformations */
	private Object additionalInfo;
	
	/** Placeholder bindings from TriggerPattern (e.g., "$x" -> ASTNode or "$args$" -> List<ASTNode>) */
	private Map<String, Object> bindings = new HashMap<>();

	// ========== Getters ==========
	
	/**
	 * Gets the main AST node.
	 * @return the AST node
	 */
	@Override
	public ASTNode getMinv() {
		return minv;
	}
	
	/**
	 * Gets the name or identifier associated with the node.
	 * @return the name
	 */
	public String getMinvname() {
		return minvname;
	}
	
	/**
	 * Gets the set of already processed nodes.
	 * @return the set of processed nodes
	 */
	public Set<ASTNode> getNodesprocessed() {
		return nodesprocessed;
	}
	
	/**
	 * Gets the additional string value.
	 * @return the value
	 */
	public String getValue() {
		return value;
	}
	
	/**
	 * Gets the method invocation reference.
	 * @return the method invocation
	 */
	public MethodInvocation getMethod() {
		return method;
	}
	
	/**
	 * Gets the counter.
	 * @return the count
	 */
	public int getCount() {
		return count;
	}
	
	/**
	 * Gets the additional context information.
	 * @return the additional info
	 */
	public Object getAdditionalInfo() {
		return additionalInfo;
	}
	
	/**
	 * Gets the placeholder bindings map.
	 * @return the bindings map
	 */
	@Override
	public Map<String, Object> getBindings() {
		return bindings;
	}

	/**
	 * Gets the node as an Annotation.
	 * @return the AST node cast to Annotation
	 */
	@Override
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
	
	// ========== Fluent Setters for Backward Compatibility ==========
	
	/**
	 * Sets the main AST node.
	 * @param minv the AST node
	 * @return this holder for fluent API
	 */
	public JunitHolder setMinv(ASTNode minv) {
		this.minv = minv;
		return this;
	}
	
	/**
	 * Sets the name or identifier.
	 * @param minvname the name
	 * @return this holder for fluent API
	 */
	public JunitHolder setMinvname(String minvname) {
		this.minvname = minvname;
		return this;
	}
	
	/**
	 * Sets the set of already processed nodes.
	 * @param nodesprocessed the set of processed nodes
	 * @return this holder for fluent API
	 */
	public JunitHolder setNodesprocessed(Set<ASTNode> nodesprocessed) {
		this.nodesprocessed = nodesprocessed;
		return this;
	}
	
	/**
	 * Sets the additional string value.
	 * @param value the value
	 * @return this holder for fluent API
	 */
	public JunitHolder setValue(String value) {
		this.value = value;
		return this;
	}
	
	/**
	 * Sets the method invocation reference.
	 * @param method the method invocation
	 * @return this holder for fluent API
	 */
	public JunitHolder setMethod(MethodInvocation method) {
		this.method = method;
		return this;
	}
	
	/**
	 * Sets the counter.
	 * @param count the count
	 * @return this holder for fluent API
	 */
	public JunitHolder setCount(int count) {
		this.count = count;
		return this;
	}
	
	/**
	 * Sets the additional context information.
	 * @param additionalInfo the additional info
	 * @return this holder for fluent API
	 */
	public JunitHolder setAdditionalInfo(Object additionalInfo) {
		this.additionalInfo = additionalInfo;
		return this;
	}
	
	/**
	 * Sets the placeholder bindings map.
	 * @param bindings the bindings map, may be {@code null} to reset to an empty map
	 * @return this holder for fluent API
	 */
	public JunitHolder setBindings(Map<String, Object> bindings) {
		this.bindings = bindings != null ? bindings : new HashMap<>();
		return this;
	}
	
	/**
	 * Gets a placeholder binding as an Expression.
	 * 
	 * @param placeholder the placeholder name (e.g., "$x")
	 * @return the bound expression, or null if not found or if it's a list binding
	 */
	@Override
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
	
	// ========== Builder ==========
	
	/**
	 * Builder for creating JunitHolder instances with a fluent API.
	 * Provides a type-safe way to construct holders with only the required fields set.
	 */
	public static class Builder {
		private final JunitHolder holder;
		
		/**
		 * Creates a new builder instance.
		 */
		public Builder() {
			this.holder = new JunitHolder();
		}
		
		/**
		 * Sets the main AST node.
		 * @param minv the AST node
		 * @return this builder
		 */
		public Builder minv(ASTNode minv) {
			holder.setMinv(minv);
			return this;
		}
		
		/**
		 * Sets the name or identifier.
		 * @param minvname the name
		 * @return this builder
		 */
		public Builder minvname(String minvname) {
			holder.setMinvname(minvname);
			return this;
		}
		
		/**
		 * Sets the set of already processed nodes.
		 * @param nodesprocessed the set of processed nodes
		 * @return this builder
		 */
		public Builder nodesprocessed(Set<ASTNode> nodesprocessed) {
			holder.setNodesprocessed(nodesprocessed);
			return this;
		}
		
		/**
		 * Sets the additional string value.
		 * @param value the value
		 * @return this builder
		 */
		public Builder value(String value) {
			holder.setValue(value);
			return this;
		}
		
		/**
		 * Sets the method invocation reference.
		 * @param method the method invocation
		 * @return this builder
		 */
		public Builder method(MethodInvocation method) {
			holder.setMethod(method);
			return this;
		}
		
		/**
		 * Sets the counter.
		 * @param count the count
		 * @return this builder
		 */
		public Builder count(int count) {
			holder.setCount(count);
			return this;
		}
		
		/**
		 * Sets the additional context information.
		 * @param additionalInfo the additional info
		 * @return this builder
		 */
		public Builder additionalInfo(Object additionalInfo) {
			holder.setAdditionalInfo(additionalInfo);
			return this;
		}
		
		/**
		 * Sets the placeholder bindings map.
		 * @param bindings the bindings map
		 * @return this builder
		 * @throws IllegalArgumentException if {@code bindings} is {@code null}
		 */
		public Builder bindings(Map<String, Object> bindings) {
			if (bindings == null) {
				throw new IllegalArgumentException("bindings must not be null");
			}
			holder.setBindings(bindings);
			return this;
		}
		
		/**
		 * Builds the JunitHolder instance.
		 * @return the constructed holder
		 */
		public JunitHolder build() {
			return holder;
		}
	}
	
	/**
	 * Creates a new builder instance.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}
}
