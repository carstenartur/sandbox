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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

/**
 * Severity levels for hints, matching NetBeans severity model.
 * 
 * @since 1.2.2
 */
public enum Severity {
	/**
	 * Error severity - indicates a serious problem that should be fixed.
	 */
	ERROR,
	
	/**
	 * Warning severity - indicates a potential problem or improvement.
	 */
	WARNING,
	
	/**
	 * Informational severity - provides useful information.
	 */
	INFO,
	
	/**
	 * Hint severity - suggests a code improvement.
	 */
	HINT
}
