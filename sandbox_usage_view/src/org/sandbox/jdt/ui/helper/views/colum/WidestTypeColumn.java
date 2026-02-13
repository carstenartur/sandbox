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
package org.sandbox.jdt.ui.helper.views.colum;

import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.sandbox.jdt.internal.corext.util.TypeWideningAnalyzer.TypeWideningResult;
import org.sandbox.jdt.ui.helper.views.TypeWideningCache;

/**
 * Column displaying the widest (most general) type a variable can be downgraded to,
 * based on actual usage analysis via {@link org.sandbox.jdt.internal.corext.util.TypeWideningAnalyzer}.
 *
 * <p>When a variable can be widened, the cell shows the qualified name of the widest type.
 * A tooltip provides additional details about the current type and the suggested widest type.
 * If no widening is possible, the cell is empty.</p>
 */
public class WidestTypeColumn extends AbstractColumn {

	private static final int MINIMUM_WIDTH = 150;
	private static final String TITLE = "Widest Type"; //$NON-NLS-1$
	private static final int COLUMN_WEIGHT = 3;

	private final TypeWideningCache typeWideningCache;

	/**
	 * Creates a new WidestTypeColumn.
	 *
	 * @param typeWideningCache the cache containing pre-computed type widening results
	 */
	public WidestTypeColumn(TypeWideningCache typeWideningCache) {
		this.typeWideningCache = typeWideningCache;
	}

	@Override
	public void createColumn(TableViewer viewer, int pos) {
		createTableViewerColumn(viewer, TITLE, MINIMUM_WIDTH, pos)
				.setLabelProvider(createLabelProvider());
	}

	@Override
	public void createColumn(TableViewer viewer, int pos, TableColumnLayout tableColumnLayout) {
		TableViewerColumn viewerColumn = createTableViewerColumn(viewer, TITLE, MINIMUM_WIDTH, pos);
		viewerColumn.setLabelProvider(createLabelProvider());
		tableColumnLayout.setColumnData(viewerColumn.getColumn(),
				new ColumnWeightData(COLUMN_WEIGHT, MINIMUM_WIDTH, true));
	}

	private ConflictHighlightingLabelProvider createLabelProvider() {
		return new ConflictHighlightingLabelProvider() {
			@Override
			public String getText(Object element) {
				return getWidestTypeText((IVariableBinding) element);
			}

			@Override
			public String getToolTipText(Object element) {
				return getWidestTypeTooltip((IVariableBinding) element);
			}
		};
	}

	private String getWidestTypeText(IVariableBinding binding) {
		if (typeWideningCache == null) {
			return ""; //$NON-NLS-1$
		}
		TypeWideningResult result = typeWideningCache.getResult(binding.getKey());
		if (result != null && result.canWiden()) {
			return result.getWidestType().getQualifiedName();
		}
		return ""; //$NON-NLS-1$
	}

	private String getWidestTypeTooltip(IVariableBinding binding) {
		if (typeWideningCache == null) {
			return null;
		}
		TypeWideningResult result = typeWideningCache.getResult(binding.getKey());
		if (result != null && result.canWiden()) {
			return binding.getName() + ": " //$NON-NLS-1$
					+ result.getCurrentType().getQualifiedName()
					+ " \u2192 " //$NON-NLS-1$
					+ result.getWidestType().getQualifiedName();
		}
		return null;
	}

	@Override
	public int getColumnWeight() {
		return COLUMN_WEIGHT;
	}

	@Override
	public int getMinimumWidth() {
		return MINIMUM_WIDTH;
	}

	@Override
	protected int compare(IVariableBinding p1, IVariableBinding p2) {
		String type1 = getWidestTypeText(p1);
		String type2 = getWidestTypeText(p2);
		return type1.compareTo(type2);
	}
}
