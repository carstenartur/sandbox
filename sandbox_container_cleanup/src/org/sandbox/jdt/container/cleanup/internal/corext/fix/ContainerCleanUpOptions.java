/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.corext.fix;

/** Option keys for semantic container-contract cleanups. */
public final class ContainerCleanUpOptions {

	public static final String CLEANUP= "cleanup.container_contracts"; //$NON-NLS-1$
	public static final String APPEND_ARRAY_TO_LIST=
			ContainerCleanUpRule.APPEND_ARRAY_TO_LIST.optionId();
	public static final String UNIQUE_SEQUENCE_TO_SET=
			ContainerCleanUpRule.UNIQUE_SEQUENCE_TO_ORDERED_SET.optionId();
	public static final String PARALLEL_ARRAYS_TO_RECORDS= ContainerCleanUpRule.PARALLEL_ARRAYS_TO_RECORDS.optionId();
	public static final String RECORD_KEY_MAP= ContainerCleanUpRule.RECORD_KEY_MAP.optionId();
	public static final String RETAIN_PROVEN_PARAMETER_BRIDGE= "cleanup.container_contracts.retain_proven_parameter_bridge"; //$NON-NLS-1$
	public static final String CLOSED_SOURCE_PARAMETER_MIGRATION=
			ContainerCleanUpRule.CLOSED_SOURCE_PARAMETER_MIGRATION.optionId();

	private ContainerCleanUpOptions() {
	}
}
