/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import java.util.Set;

import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Detects concurrent collection types that require special handling during
 * loop-to-stream conversions.
 * 
 * <p>Concurrent collections like {@link java.util.concurrent.CopyOnWriteArrayList}
 * and {@link java.util.concurrent.ConcurrentHashMap} have different iteration
 * semantics than standard collections:</p>
 * 
 * <ul>
 * <li><b>CopyOnWrite collections:</b> Iterators provide a snapshot of the collection
 * at the time the iterator was created. Modifications during iteration do not throw
 * {@code ConcurrentModificationException} but are not visible to the iterator.</li>
 * 
 * <li><b>Concurrent collections:</b> Weakly consistent iterators that may reflect
 * modifications made after iterator creation, but never throw {@code ConcurrentModificationException}.</li>
 * 
 * <li><b>iterator.remove() not supported:</b> Many concurrent collections do not support
 * {@code iterator.remove()}, and attempting to call it will throw
 * {@code UnsupportedOperationException}.</li>
 * </ul>
 * 
 * <p><b>Safety Rules for Concurrent Collections:</b></p>
 * <ul>
 * <li>Never generate {@code iterator.remove()} for concurrent collections</li>
 * <li>Be aware that modifications may not be visible during iteration</li>
 * <li>Consider the threading context (field vs local variable)</li>
 * </ul>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670 - Point 2.4</a>
 * @since 1.0.0
 */
public final class ConcurrentCollectionDetector {

	/**
	 * Fully qualified names of concurrent collection types.
	 * These collections have special iteration semantics and do not support iterator.remove().
	 */
	private static final Set<String> CONCURRENT_COLLECTION_TYPES = Set.of(
			"java.util.concurrent.CopyOnWriteArrayList", //$NON-NLS-1$
			"java.util.concurrent.CopyOnWriteArraySet", //$NON-NLS-1$
			"java.util.concurrent.ConcurrentHashMap", //$NON-NLS-1$
			"java.util.concurrent.ConcurrentSkipListMap", //$NON-NLS-1$
			"java.util.concurrent.ConcurrentSkipListSet", //$NON-NLS-1$
			"java.util.concurrent.ConcurrentLinkedQueue", //$NON-NLS-1$
			"java.util.concurrent.ConcurrentLinkedDeque", //$NON-NLS-1$
			"java.util.concurrent.LinkedBlockingQueue", //$NON-NLS-1$
			"java.util.concurrent.LinkedBlockingDeque", //$NON-NLS-1$
			"java.util.concurrent.ArrayBlockingQueue", //$NON-NLS-1$
			"java.util.concurrent.PriorityBlockingQueue", //$NON-NLS-1$
			"java.util.concurrent.DelayQueue", //$NON-NLS-1$
			"java.util.concurrent.SynchronousQueue" //$NON-NLS-1$
	);

	private ConcurrentCollectionDetector() {
		// utility class
	}

	/**
	 * Checks if the given type is a concurrent collection.
	 * 
	 * @param typeBinding the type to check (may be null)
	 * @return {@code true} if the type is a concurrent collection
	 */
	public static boolean isConcurrentCollection(ITypeBinding typeBinding) {
		if (typeBinding == null) {
			return false;
		}

		// Check the erasure (raw type) to handle generics
		String qualifiedName = typeBinding.getErasure().getQualifiedName();
		return CONCURRENT_COLLECTION_TYPES.contains(qualifiedName);
	}

	/**
	 * Checks if the given qualified type name is a concurrent collection.
	 * 
	 * @param qualifiedTypeName the fully qualified type name (may be null)
	 * @return {@code true} if the type name matches a concurrent collection
	 */
	public static boolean isConcurrentCollection(String qualifiedTypeName) {
		if (qualifiedTypeName == null) {
			return false;
		}
		return CONCURRENT_COLLECTION_TYPES.contains(qualifiedTypeName);
	}
}
