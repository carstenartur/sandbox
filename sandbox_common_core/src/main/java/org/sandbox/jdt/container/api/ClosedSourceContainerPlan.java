/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.api;

import java.util.List;

/** Source-closed signature change submitted to the coordinated Cleanup lifecycle. */
public sealed interface ClosedSourceContainerPlan permits ClosedSourceParameterMigrationPlan, ClosedSourceReturnMigrationPlan, LosslessParameterBridgePlan {
	List<String> affectedCompilationUnitHandles();
}
