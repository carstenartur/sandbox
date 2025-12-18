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
	HelperVisitor<ReferenceHolder<V,T>,V,T> hv;

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
