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
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.OBSOLETE_COLLECTION_CLEANUP;

import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.fix.ObsoleteCollectionFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;

/**
 * CleanUp for obsolete collection detection.
 *
 * <p>This is a hint-only cleanup that detects usage of obsolete collection
 * types like {@code Vector}, {@code Hashtable}, and {@code Stack} and reports
 * them as problem markers.</p>
 */
public class ObsoleteCollectionCleanUpCore extends AbstractSandboxCleanUpCore {

	public ObsoleteCollectionCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public ObsoleteCollectionCleanUpCore() {
	}

	@Override
	protected String getCleanUpKey() {
		return OBSOLETE_COLLECTION_CLEANUP;
	}

	@Override
	protected String getFixLabel() {
		return null; // hint-only: no operations
	}

	@Override
	protected String getDescription() {
		return MultiFixMessages.ObsoleteCollectionCleanUp_description;
	}

	@Override
	protected void detect(CompilationUnit cu, CleanUpResult result) {
		ObsoleteCollectionFixCore.findFindings(cu, result.getFindings());
	}

	@Override
	public String getPreview() {
		if (isEnabled(OBSOLETE_COLLECTION_CLEANUP)) {
			return """
				List<String> names = new ArrayList<>();
				Map<String, Integer> scores = new HashMap<>();
				Deque<Task> tasks = new ArrayDeque<>();
				"""; //$NON-NLS-1$
		}
		return """
			List<String> names = new Vector<>();
			Map<String, Integer> scores = new Hashtable<>();
			Deque<Task> tasks = new Stack<>();
			"""; //$NON-NLS-1$
	}
}
