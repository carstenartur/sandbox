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

/** User-visible execution surfaces with different preview and safety guarantees. */
public enum CleanUpExecutionSurface {
	/** Automatic per-file cleanup performed while saving an editor. */
	SAVE_ACTION,
	/** Interactive cleanup/refactoring with preview and explicit apply. */
	INTERACTIVE_PREVIEW,
	/** Headless project run with explicit report/output policy. */
	HEADLESS_TRANSACTION
}
