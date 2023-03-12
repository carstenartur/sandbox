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

import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.viewers.TableViewer;

public class QualifiednameColumn extends AbstractColumn {

	private static final int bounds= 100;
	private static final String title= "Type"; //$NON-NLS-1$

	public QualifiednameColumn() {
	}

	@Override
	public void createColumn(TableViewer viewer, int pos) {
		// Second column is for the type
		createTableViewerColumn(viewer, title, bounds, pos).setLabelProvider(new AlternatingColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				IVariableBinding p= (IVariableBinding) element;
				return p.getType().getQualifiedName();
			}
		});
	}

	@Override
	protected int compare(IVariableBinding p1, IVariableBinding p2) {
		String qname1= p1.getType().getQualifiedName();
		String qname2= p2.getType().getQualifiedName();
		if (qname1 != null && qname2 != null) {
			return qname1.compareTo(qname2);
		}
		return 0;
	}
}
