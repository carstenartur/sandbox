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
import java.util.HashMap;
import java.util.Map;

public class ReferenceHolder<T> extends HashMap<String,T> implements HelperVisitorProvider<T> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	HelperVisitor<ReferenceHolder<T>> hv;
	Map<VisitorEnum,Object> vistor2data=new HashMap<>();
	
	public ReferenceHolder() {
	}

	@Override
	public HelperVisitor<ReferenceHolder<T>> getHelperVisitor() {
		return hv;
	}

	@Override
	public void setHelperVisitor(HelperVisitor<ReferenceHolder<T>> hv) {
		this.hv=hv;
	}
	
	private void writeObject(ObjectOutputStream stream)
	        throws IOException {
	    stream.defaultWriteObject();
	}

	private void readObject(ObjectInputStream stream)
	        throws IOException, ClassNotFoundException {
	    stream.defaultReadObject();
	}

	public Object getNodeData(VisitorEnum node) {
		return vistor2data.get(node);
	}

	@Override
	public void setNodeData(VisitorEnum node, Object data) {
		this.vistor2data.put(node, data);
	}

}