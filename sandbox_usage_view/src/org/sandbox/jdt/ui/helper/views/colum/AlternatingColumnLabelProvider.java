/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
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
package org.sandbox.jdt.ui.helper.views.colum;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

public class AlternatingColumnLabelProvider extends ColumnLabelProvider {
	Color grayColor= Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);

	boolean alternatingcolor= false;

	@Override
	public Color getBackground(Object element) {
		alternatingcolor= !alternatingcolor;
		if (alternatingcolor) {
			return grayColor;
		}
		return null;
	}
}
