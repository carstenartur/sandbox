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

/** Detects concurrent collection iteration semantics relevant to loop conversion. */
public final class ConcurrentCollectionDetector {

	private static final Set<String> SNAPSHOT_COLLECTION_TYPES= Set.of(
			"java.util.concurrent.CopyOnWriteArrayList", //$NON-NLS-1$
			"java.util.concurrent.CopyOnWriteArraySet"); //$NON-NLS-1$

	private static final Set<String> CONCURRENT_COLLECTION_TYPES= Set.of(
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
			"java.util.concurrent.SynchronousQueue"); //$NON-NLS-1$

	private ConcurrentCollectionDetector() {
		// utility class
	}

	public static boolean isConcurrentCollection(ITypeBinding typeBinding) {
		return typeBinding != null && isConcurrentCollection(typeBinding.getErasure().getQualifiedName());
	}

	public static boolean isConcurrentCollection(String qualifiedTypeName) {
		return qualifiedTypeName != null && CONCURRENT_COLLECTION_TYPES.contains(qualifiedTypeName);
	}

	/**
	 * Copy-on-write iterators and spliterators traverse a stable array snapshot.
	 * Read-only sequential enhanced-for conversion can therefore retain their
	 * iteration semantics. Other concurrent collections remain conservatively
	 * blocked because their iterators are weakly consistent.
	 */
	public static boolean hasSnapshotIteration(ITypeBinding typeBinding) {
		return typeBinding != null && hasSnapshotIteration(typeBinding.getErasure().getQualifiedName());
	}

	public static boolean hasSnapshotIteration(String qualifiedTypeName) {
		return qualifiedTypeName != null && SNAPSHOT_COLLECTION_TYPES.contains(qualifiedTypeName);
	}
}
