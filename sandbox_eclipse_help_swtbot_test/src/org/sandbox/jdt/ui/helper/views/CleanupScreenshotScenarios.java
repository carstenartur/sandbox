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
package org.sandbox.jdt.ui.helper.views;

import java.util.Set;

/** Descriptors for already implemented deterministic Workbench scenarios. */
final class CleanupScreenshotScenarios {

	private static final String PROJECT= "SandboxCleanupPreviewProject"; //$NON-NLS-1$

	static final CleanupScreenshotScenario JFACE= new CleanupScreenshotScenario(
			"jface-file-selection", PreviewContract.FILE_COMBINED_DIFF, PROJECT, //$NON-NLS-1$
			Set.of("src/demo/single/SingleFileCleanup.java", //$NON-NLS-1$
					"src/demo/multi/MonitorOnly.java", "src/demo/multi/SorterOnly.java"), //$NON-NLS-1$ //$NON-NLS-2$
			Set.of("src/demo/single/SingleFileCleanup.java")); //$NON-NLS-1$

	static final CleanupScreenshotScenario INT_TO_ENUM= new CleanupScreenshotScenario(
			"int-to-enum-atomic", PreviewContract.ATOMIC_CANDIDATE, PROJECT, //$NON-NLS-1$
			Set.of("src/demo/coordinated/StateOwner.java", "src/demo/coordinated/StateCaller.java"), Set.of()); //$NON-NLS-1$ //$NON-NLS-2$

	static final CleanupScreenshotScenario METHOD_REUSE= new CleanupScreenshotScenario(
			"method-reuse-file-diff", PreviewContract.FILE_COMBINED_DIFF, PROJECT, //$NON-NLS-1$
			Set.of("src/demo/methodreuse/RepeatedSequence.java"), Set.of()); //$NON-NLS-1$

	private CleanupScreenshotScenarios() {
	}
}
