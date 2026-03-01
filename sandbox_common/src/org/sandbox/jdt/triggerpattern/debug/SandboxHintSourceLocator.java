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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.debug;

import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.IStackFrame;

/**
 * Source locator for {@code .sandbox-hint} debugging.
 *
 * <p>Uses {@link org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult#toHintLine(int)}
 * to map stack frames in generated synthetic classes back to
 * {@code .sandbox-hint} file lines.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintSourceLocator implements ISourceLocator {

	@Override
	public Object getSourceElement(IStackFrame stackFrame) {
		// Future: Map synthetic class stack frames to .sandbox-hint files
		// using CompilationResult.toHintLine()
		return null;
	}
}
