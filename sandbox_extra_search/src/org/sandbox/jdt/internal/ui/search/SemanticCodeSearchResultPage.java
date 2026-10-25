/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search;

import java.util.List;
import java.util.function.Function;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.ISearchResultListener;
import org.eclipse.search.ui.ISearchResultPage;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.Page;
import org.sandbox.jdt.internal.ui.search.gitindex.SearchHit;

/** Displays semantic, hybrid and similar-code results in Eclipse's Search view. */
public class SemanticCodeSearchResultPage extends Page implements ISearchResultPage {
	private TableViewer table;
	private SemanticCodeSearchResult result;
	private ISearchResultViewPart view;
	private String id;
	private final ISearchResultListener listener= event -> refresh();

	@Override public void createControl(Composite parent) {
		table= new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		table.getTable().setHeaderVisible(true);
		table.getTable().setLinesVisible(true);
		table.setContentProvider(ArrayContentProvider.getInstance());
		column("Commit", 180, SearchHit::getCommitHash); //$NON-NLS-1$
		column("Path", 300, SearchHit::getPath); //$NON-NLS-1$
		column("Types", 180, SearchHit::getClassName); //$NON-NLS-1$
		column("Methods", 240, SearchHit::getMethodName); //$NON-NLS-1$
		column("Package", 180, SearchHit::getContent); //$NON-NLS-1$
		PlatformUI.getWorkbench().getHelpSystem().setHelp(table.getControl(), "sandbox_extra_search.git_search"); //$NON-NLS-1$
		refresh();
	}

	private void column(String label, int width, Function<SearchHit, String> value) {
		var column= new TableViewerColumn(table, SWT.NONE);
		column.getColumn().setText(label);
		column.getColumn().setWidth(width);
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override public String getText(Object element) { return java.util.Objects.toString(value.apply((SearchHit) element), ""); } //$NON-NLS-1$
		});
	}

	private void refresh() {
		if (table == null || table.getControl().isDisposed()) return;
		var display= table.getControl().getDisplay();
		if (!display.isDisposed()) display.asyncExec(() -> {
			if (table.getControl().isDisposed()) return;
			table.setInput(result == null ? List.of() : result.getMatches());
			if (view != null) view.updateLabel();
		});
	}

	@Override public void setInput(ISearchResult input, Object state) {
		if (result != null) result.removeListener(listener);
		result= input instanceof SemanticCodeSearchResult semantic ? semantic : null;
		if (result != null) result.addListener(listener);
		refresh();
	}
	@Override public Control getControl() { return table == null ? null : table.getControl(); }
	@Override public void setFocus() { if (table != null) table.getControl().setFocus(); }
	@Override public String getLabel() { return result == null ? "Semantic Search" : result.getLabel(); } //$NON-NLS-1$
	@Override public void setViewPart(ISearchResultViewPart part) { view= part; }
	@Override public void setID(String value) { id= value; }
	@Override public String getID() { return id; }
	@Override public Object getUIState() { return null; }
	@Override public void restoreState(IMemento memento) { }
	@Override public void saveState(IMemento memento) { }
	@Override public void dispose() {
		if (result != null) result.removeListener(listener);
		if (table != null && !table.getControl().isDisposed()) table.getControl().dispose();
		super.dispose();
	}
}
