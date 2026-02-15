package org.sandbox.jdt.internal.common;

/*-
 * #%L
 * Sandbox common
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


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * A thread-safe reference holder that extends ConcurrentHashMap for storing AST node references.
 * This class is used by cleanup visitors to track and store references to AST nodes during traversal.
 * 
 * <p>This class does not allow null to be used as a key or value because it is derived from ConcurrentHashMap.</p>
 * 
 * <p><strong>Thread Safety:</strong> This class is thread-safe due to its ConcurrentHashMap base.</p>
 * 
 * @param <V> the type of keys maintained by this map
 * @param <T> the type of mapped values
 * 
 * @author chammer
 * @since 1.15
 */
public class ReferenceHolder<V,T> extends ConcurrentHashMap<V,T> implements HelperVisitorProvider<V,T,ReferenceHolder<V,T>> {

	/**
	 * Serial version UID for serialization compatibility.
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * The associated helper visitor for processing AST nodes.
	 */
	private transient HelperVisitor<ReferenceHolder<V,T>,V,T> hv;
	
	/**
	 * Creates a new empty ReferenceHolder.
	 * This is a convenience factory method to reduce verbosity in plugin code.
	 * 
	 * @param <V> the type of keys
	 * @param <T> the type of values
	 * @return a new ReferenceHolder instance
	 * @since 1.16
	 */
	public static <V,T> ReferenceHolder<V,T> create() {
		return new ReferenceHolder<>();
	}
	
	/**
	 * Creates a new ReferenceHolder for storing AST nodes as keys with Object values.
	 * This is the most common usage pattern across plugins.
	 * 
	 * @return a new ReferenceHolder&lt;ASTNode, Object&gt; instance
	 * @since 1.16
	 */
	public static ReferenceHolder<ASTNode, Object> createForNodes() {
		return new ReferenceHolder<>();
	}
	
	/**
	 * Creates a new ReferenceHolder with Integer keys and typed values.
	 * This pattern is commonly used for collecting results by index.
	 * 
	 * @param <T> the type of values to store
	 * @return a new ReferenceHolder&lt;Integer, T&gt; instance
	 * @since 1.16
	 */
	public static <T> ReferenceHolder<Integer, T> createIndexed() {
		return new ReferenceHolder<>();
	}
	
	/**
	 * Type-safe getter that casts the value to the specified type.
	 * 
	 * @param <R> the expected return type
	 * @param key the key whose associated value is to be returned
	 * @param clazz the class of the expected return type
	 * @return the value cast to type R, or null if not present or wrong type
	 * @since 1.16
	 */
	@SuppressWarnings("unchecked")
	public <R> R getAs(V key, Class<R> clazz) {
		T value = get(key);
		if (value != null && clazz.isInstance(value)) {
			return (R) value;
		}
		return null;
	}
	
	/**
	 * Type-safe put that enforces the value type at compile time.
	 * This is just an alias for put() but with clearer intent.
	 * 
	 * @param key the key with which the value is to be associated
	 * @param value the value to be associated with the key
	 * @return the previous value associated with key, or null
	 * @since 1.16
	 */
	public T putTyped(V key, T value) {
		return put(key, value);
	}

	/**
	 * Gets the helper visitor associated with this reference holder.
	 * 
	 * @return the helper visitor, or null if not set
	 */
	@Override
	public HelperVisitor<ReferenceHolder<V,T>,V,T> getHelperVisitor() {
		return hv;
	}

	/**
	 * Sets the helper visitor for this reference holder.
	 * The helper visitor is used to process AST nodes during traversal.
	 * 
	 * @param hv the helper visitor to associate with this holder
	 */
	@Override
	public void setHelperVisitor(HelperVisitor<ReferenceHolder<V,T>,V,T> hv) {
		this.hv=hv;
	}

	/**
	 * Custom serialization method for writing this object to an ObjectOutputStream.
	 * Uses default serialization behavior.
	 * 
	 * @param stream the object output stream
	 * @throws IOException if an I/O error occurs
	 */
	@SuppressWarnings("static-method")
	private void writeObject(ObjectOutputStream stream)
			throws IOException {
		stream.defaultWriteObject();
	}

	/**
	 * Custom deserialization method for reading this object from an ObjectInputStream.
	 * Uses default deserialization behavior.
	 * 
	 * @param stream the object input stream
	 * @throws IOException if an I/O error occurs
	 * @throws ClassNotFoundException if the class of a serialized object cannot be found
	 */
	@SuppressWarnings("static-method")
	private void readObject(ObjectInputStream stream)
			throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
	}

	/**
	 * Returns the hash code value for this reference holder.
	 * Delegates to the superclass implementation.
	 * 
	 * @return the hash code value
	 */
	@Override
	public int hashCode() {
		return super.hashCode();
	}

	/**
	 * Compares the specified object with this reference holder for equality.
	 * Delegates to the superclass implementation.
	 * 
	 * @param o the object to be compared for equality
	 * @return true if the specified object is equal to this reference holder
	 */
	@Override
	public boolean equals(Object o) {
		return super.equals(o);
	}
}
