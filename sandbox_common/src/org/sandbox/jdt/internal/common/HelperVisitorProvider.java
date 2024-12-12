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


/**
 *
 * @author chammer
 * @param <V>
 * @param <T>
 * @param <E>
 * @since 1.15
 *
 */
public interface HelperVisitorProvider<V,T,E extends HelperVisitorProvider<V, T, E>> {
	/**
	 * @return HelperVisitor
	 */
	HelperVisitor<E,V,T> getHelperVisitor();
	/**
	 * @param hv
	 */
	void setHelperVisitor(HelperVisitor<E,V,T> hv);
}
