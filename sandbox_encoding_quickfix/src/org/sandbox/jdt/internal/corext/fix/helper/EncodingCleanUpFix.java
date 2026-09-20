/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix;

/** Encoding entry point for the shared source-preserving fix. */
public final class EncodingCleanUpFix extends NlsAwareCleanUpFix {
	public EncodingCleanUpFix(String name, CompilationUnit root, CompilationUnitRewriteOperation[] operations) {
		super(name, root, operations);
	}
}
