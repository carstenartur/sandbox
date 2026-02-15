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
package org.sandbox.jdt.internal.ui.views.mining;

import org.eclipse.jface.viewers.IStructuredContentProvider;

/**
 * Content provider for the commit table. Input must be an array of
 * {@link CommitTableEntry}.
 *
 * @since 1.2.6
 */
public class CommitTableContentProvider implements IStructuredContentProvider {

	@Override
	public Object[] getElements(Object inputElement) {
		if (inputElement instanceof CommitTableEntry[] entries) {
			return entries;
		}
		return new Object[0];
	}
}
