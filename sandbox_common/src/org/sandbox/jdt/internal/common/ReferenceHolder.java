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
