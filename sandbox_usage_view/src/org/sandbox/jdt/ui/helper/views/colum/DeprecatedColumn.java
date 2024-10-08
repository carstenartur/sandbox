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

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.viewers.TableViewer;

public class DeprecatedColumn extends AbstractColumn {

	private static final int bounds= 100;
	private static final String title= "Deprecated"; //$NON-NLS-1$

	@Override
	public void createColumn(TableViewer viewer, int pos) {
		// now the deprecated or not checkbox
		createTableViewerColumn(viewer, title, bounds, pos).setLabelProvider(new AlternatingColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IVariableBinding p= (IVariableBinding) element;
				return String.valueOf(p.isDeprecated());
			}
		});
	}

	@Override
	protected int compare(IVariableBinding p1, IVariableBinding p2) {
		ITypeBinding declaringClass1= p1.getDeclaringClass();
		ITypeBinding declaringClass2= p2.getDeclaringClass();
		if (declaringClass1 != null && declaringClass2 != null) {
			return Boolean.compare(declaringClass1.isDeprecated(), declaringClass2.isDeprecated());
		}
		return 0;
	}
}
