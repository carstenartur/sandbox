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

import java.util.Arrays;
import java.util.List;

/** Central registry for user-visible semantic container migrations. */
public enum ContainerCleanUpRule {

	APPEND_ARRAY_TO_LIST(
			"cleanup.container_contracts.append_array_to_list", //$NON-NLS-1$
			"sandbox_container_cleanup.append_array_to_list", //$NON-NLS-1$
			ExecutionMode.LOCAL_SAFE,
			true),

	UNIQUE_SEQUENCE_TO_ORDERED_SET(
			"cleanup.container_contracts.unique_sequence_to_set", //$NON-NLS-1$
			"sandbox_container_cleanup.unique_sequence_to_set", //$NON-NLS-1$
			ExecutionMode.LOCAL_SAFE,
			true),

	CLOSED_SOURCE_PARAMETER_MIGRATION(
			"cleanup.container_contracts.closed_source_parameter_migration", //$NON-NLS-1$
			"sandbox_container_cleanup.closed_source_parameter_migration", //$NON-NLS-1$
			ExecutionMode.PROJECT_CLOSED,
			false);

	private final String optionId;
	private final String helpContextId;
	private final ExecutionMode executionMode;
	private final boolean localCleanUp;

	ContainerCleanUpRule(
			String optionId,
			String helpContextId,
			ExecutionMode executionMode,
			boolean localCleanUp) {
		this.optionId= optionId;
		this.helpContextId= helpContextId;
		this.executionMode= executionMode;
		this.localCleanUp= localCleanUp;
	}

	public String optionId() {
		return optionId;
	}

	public String helpContextId() {
		return helpContextId;
	}

	public ExecutionMode executionMode() {
		return executionMode;
	}

	public boolean isLocalCleanUp() {
		return localCleanUp;
	}

	public static List<ContainerCleanUpRule> localCleanUps() {
		return Arrays.stream(values())
				.filter(ContainerCleanUpRule::isLocalCleanUp)
				.toList();
	}

	/** Execution surface approved for a rule. */
	public enum ExecutionMode {
		LOCAL_SAFE,
		PROJECT_CLOSED,
		COMPATIBILITY_MANAGED,
		BREAKING_MIGRATION,
		REPORT_ONLY,
		UNSUPPORTED
	}
}
