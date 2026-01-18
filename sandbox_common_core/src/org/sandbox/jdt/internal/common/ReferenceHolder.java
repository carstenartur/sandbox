/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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

import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe reference holder for storing data during AST traversal.
 * This is a simplified version without HelperVisitorProvider coupling.
 * 
 * <p>This class is used by analysis code to track and store references to AST nodes
 * during traversal without requiring OSGi infrastructure.</p>
 * 
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class ReferenceHolder<K, V> extends ConcurrentHashMap<K, V> {
	private static final long serialVersionUID = 1L;
}
