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

/** Classification used to keep coordinated cleanup scope intentional. */
public enum SourceRootKind {
	/** Ordinary production source root. */
	PRODUCTION,
	/** Ordinary test or test-fixture source root. */
	TEST,
	/** Generated source root, excluded from edits by default. */
	GENERATED,
	/** Workspace-derived source root, excluded from edits by default. */
	DERIVED,
	/** Source root located in a Java output tree. */
	OUTPUT,
	/** Missing, non-source, or otherwise unsupported root. */
	EXCLUDED;

	/**
	 * @return whether a cleanup may edit this root under a normal policy
	 */
	public boolean isEditableByDefault() {
		return this == PRODUCTION || this == TEST;
	}
}
