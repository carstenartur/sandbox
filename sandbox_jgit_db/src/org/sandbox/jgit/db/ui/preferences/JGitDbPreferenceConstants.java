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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jgit.db.ui.preferences;

/**
 * Constants for the Git Database Index preference page.
 */
public final class JGitDbPreferenceConstants {

	/** Whether to automatically index on commit/pull operations */
	public static final String AUTO_INDEX_ON_COMMIT = "autoIndexOnCommit"; //$NON-NLS-1$

	/** Whether to index Java blob metadata */
	public static final String INDEX_JAVA_METADATA = "indexJavaMetadata"; //$NON-NLS-1$

	/** Whether to index non-Java files */
	public static final String INDEX_NON_JAVA_FILES = "indexNonJavaFiles"; //$NON-NLS-1$

	/** Maximum blob size in KB to index */
	public static final String MAX_BLOB_SIZE_KB = "maxBlobSizeKb"; //$NON-NLS-1$

	private JGitDbPreferenceConstants() {
		// no instantiation
	}
}
