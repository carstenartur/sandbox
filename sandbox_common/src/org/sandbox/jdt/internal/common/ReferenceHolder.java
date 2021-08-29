/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.common;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class does not allow null to be used as a key or value because it is derived from ConcurrentHashMap.
 *
 * @author chammer
 *
 * @param <V>
 * @param <T>
 * @param <E>
 * @since 1.15
 */
public class ReferenceHolder<V,T> extends ConcurrentHashMap<V,T> implements HelperVisitorProvider<V,T,ReferenceHolder<V,T>> {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	HelperVisitor<ReferenceHolder<V,T>,V,T> hv;

	/**
	 *
	 */
	public ReferenceHolder() {
	}

	@Override
	public HelperVisitor<ReferenceHolder<V,T>,V,T> getHelperVisitor() {
		return hv;
	}

	@Override
	public void setHelperVisitor(HelperVisitor<ReferenceHolder<V,T>,V,T> hv) {
		this.hv=hv;
	}

	@SuppressWarnings("static-method")
	private void writeObject(ObjectOutputStream stream)
	        throws IOException {
	    stream.defaultWriteObject();
	}

	@SuppressWarnings("static-method")
	private void readObject(ObjectInputStream stream)
	        throws IOException, ClassNotFoundException {
	    stream.defaultReadObject();
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return super.equals(o);
	}
}