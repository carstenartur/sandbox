/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

/** Implemented by cleanups that expose an explicit activation-time impact claim. */
public interface ICleanUpImpactMetadata {

	/**
	 * Returns the impact of the cleanup under its current option set.
	 *
	 * @return stable cleanup id, impact level and compatibility statement
	 */
	CleanUpImpact getCleanUpImpact();
}
