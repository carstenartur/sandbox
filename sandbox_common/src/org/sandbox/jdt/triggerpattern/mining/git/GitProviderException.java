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
package org.sandbox.jdt.triggerpattern.mining.git;

/**
 * Exception thrown when a {@link GitHistoryProvider} operation fails.
 *
 * @since 1.2.6
 */
public class GitProviderException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new exception with the given message.
	 *
	 * @param message the detail message
	 */
	public GitProviderException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception with the given message and cause.
	 *
	 * @param message the detail message
	 * @param cause   the cause
	 */
	public GitProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}
