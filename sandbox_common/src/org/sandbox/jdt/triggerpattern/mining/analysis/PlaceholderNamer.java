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
package org.sandbox.jdt.triggerpattern.mining.analysis;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * Generates meaningful placeholder names from AST context.
 *
 * <p>Naming strategy:</p>
 * <ul>
 *   <li>{@link SimpleName} → {@code $<identifier>} (e.g. {@code $bytes})</li>
 *   <li>{@link MethodInvocation} → {@code $expr<n>}</li>
 *   <li>Fallback → {@code $expr<n>}</li>
 * </ul>
 *
 * @since 1.2.6
 */
public class PlaceholderNamer {

	private int counter = 1;

	/**
	 * Generates a placeholder name for the given AST node.
	 *
	 * @param node the AST node to name
	 * @return a placeholder name starting with {@code $}
	 */
	public String nameFor(ASTNode node) {
		if (node instanceof SimpleName simpleName) {
			String id = simpleName.getIdentifier();
			if (!id.startsWith("$")) { //$NON-NLS-1$
				return "$" + id; //$NON-NLS-1$
			}
			return id;
		}
		return "$expr" + counter++; //$NON-NLS-1$
	}

	/**
	 * Resets the internal counter.
	 */
	public void reset() {
		counter = 1;
	}
}
