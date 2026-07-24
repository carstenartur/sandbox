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
package org.sandbox.functional.core.terminal;

/** Represents a collect terminal operation. */
public record CollectTerminal(
		CollectorType collectorType,
		String targetVariable,
		String collectionFactory) implements TerminalOperation {

	/** Compatibility constructor for interface-based collectors. */
	public CollectTerminal(CollectorType collectorType, String targetVariable) {
		this(collectorType, targetVariable, null);
	}

	/**
	 * Returns whether this terminal must preserve a concrete collection
	 * implementation with {@code Collectors.toCollection(...)}.
	 */
	public boolean hasCollectionFactory() {
		return collectionFactory != null && !collectionFactory.isBlank();
	}

	/** Types of collectors. */
	public enum CollectorType {
		/** Collectors.toList() */
		TO_LIST,
		/** Collectors.toSet() */
		TO_SET,
		/** Collectors.toMap() */
		TO_MAP,
		/** Collectors.joining() */
		JOINING,
		/** Collectors.groupingBy() */
		GROUPING_BY,
		/** Custom collector */
		CUSTOM
	}

	@Override
	public String operationType() {
		return "collect"; //$NON-NLS-1$
	}
}
