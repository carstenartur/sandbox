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

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.viewers.TableViewer;

public class DeclaringMethodColumn extends AbstractColumn {

	private static final int bounds= 100;
	private static final String title= "DeclaringMethod"; //$NON-NLS-1$

	public DeclaringMethodColumn() {
	}

	@Override
	public void createColumn(TableViewer viewer, int pos) {
		// now the DeclaringMethod
		createTableViewerColumn(viewer, title, bounds, pos).setLabelProvider(new AlternatingColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IVariableBinding p= (IVariableBinding) element;
				IMethodBinding declaringMethod= p.getDeclaringMethod();
				if (declaringMethod != null) {
					return declaringMethod.getName();
				}
				return ""; //$NON-NLS-1$
			}
		});
	}

	@Override
	protected int compare(IVariableBinding p1, IVariableBinding p2) {
		IMethodBinding declaringMethod1= p1.getDeclaringMethod();
		IMethodBinding declaringMethod2= p2.getDeclaringMethod();
		if (declaringMethod1 != null && declaringMethod2 != null) {
			return declaringMethod1.getName().compareTo(declaringMethod2.getName());
		}
		return 0;
	}
}
