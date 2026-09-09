/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

import org.sandbox.jdt.ui.helper.views.ContainerAnalysisService.ContainerAnalysisRow;

/**
 * Read-only projection of the shared semantic container analysis. Each table row
 * is source-backed evidence, so navigation leads to the observation that supports
 * or blocks the recommendation rather than to a synthetic UI-only diagnostic.
 */
public final class ContainerAnalysisView extends ViewPart implements IShowInTarget {

	public static final String VIEW_ID= "org.sandbox.jdt.ui.helper.views.ContainerAnalysisView"; //$NON-NLS-1$

	private final ContainerAnalysisService analysisService= new ContainerAnalysisService();
	private TableViewer viewer;
	private Text details;
	private List<ICompilationUnit> currentUnits= List.of();
	private ISelectionListener workbenchSelectionListener;

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));
		SashForm sash= new SashForm(parent, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		viewer= new TableViewer(sash, SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setContentProvider(ArrayContentProvider.getInstance());
		Table table= viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		addColumn("Candidate", 130, ContainerAnalysisRow::candidate); //$NON-NLS-1$
		addColumn("Current", 90, ContainerAnalysisRow::currentShape); //$NON-NLS-1$
		addColumn("Target contract", 310, ContainerAnalysisRow::targetContract); //$NON-NLS-1$
		addColumn("Confidence", 90, ContainerAnalysisRow::confidence); //$NON-NLS-1$
		addColumn("Status", 110, ContainerAnalysisRow::status); //$NON-NLS-1$
		addColumn("Evidence", 420, row -> row.evidenceKind() + ": " + row.evidenceSummary()); //$NON-NLS-1$ //$NON-NLS-2$

		details= new Text(sash, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
		details.setText("Select an evidence row to inspect its semantic explanation."); //$NON-NLS-1$
		sash.setWeights(72, 28);

		viewer.addSelectionChangedListener(event -> updateDetails());
		viewer.addDoubleClickListener(this::navigate);
		getSite().setSelectionProvider(viewer);
		contributeActions();
		installSelectionListener();
		updateFromActiveEditor();
	}

	private void addColumn(String title, int width, Function<ContainerAnalysisRow, String> text) {
		TableViewerColumn viewerColumn= new TableViewerColumn(viewer, SWT.NONE);
		TableColumn column= viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(width);
		column.setResizable(true);
		viewerColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return element instanceof ContainerAnalysisRow row ? text.apply(row) : ""; //$NON-NLS-1$
			}
		});
	}

	private void contributeActions() {
		Action refresh= new Action("Refresh container analysis") { //$NON-NLS-1$
			@Override
			public void run() {
				refresh();
			}
		};
		refresh.setToolTipText("Re-run read-only container contract analysis for the current Java selection"); //$NON-NLS-1$
		IToolBarManager toolbar= getViewSite().getActionBars().getToolBarManager();
		toolbar.add(refresh);
	}

	private void installSelectionListener() {
		workbenchSelectionListener= (IWorkbenchPart part, org.eclipse.jface.viewers.ISelection selection) -> {
			if (part == this) {
				return;
			}
			List<ICompilationUnit> units= compilationUnits(selection);
			if (!units.isEmpty()) {
				setInput(units);
			}
		};
		getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(workbenchSelectionListener);
	}

	private void updateFromActiveEditor() {
		IEditorPart editor= getSite().getPage().getActiveEditor();
		if (editor == null) {
			return;
		}
		ICompilationUnit unit= compilationUnit(editor.getEditorInput());
		if (unit != null) {
			setInput(List.of(unit));
		}
	}

	private void setInput(List<ICompilationUnit> units) {
		Map<String, ICompilationUnit> unique= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			if (unit != null && unit.exists()) {
				ICompilationUnit primary= unit.getPrimary();
				unique.put(primary.getHandleIdentifier(), primary);
			}
		}
		currentUnits= List.copyOf(unique.values());
		refresh();
	}

	private void refresh() {
		if (viewer == null || viewer.getControl().isDisposed()) {
			return;
		}
		List<ContainerAnalysisRow> rows= new ArrayList<>();
		try {
			for (ICompilationUnit unit : currentUnits) {
				rows.addAll(analysisService.analyze(unit));
			}
			viewer.setInput(rows);
			if (rows.isEmpty()) {
				details.setText(currentUnits.isEmpty()
						? "Select a Java source element to analyze container usage." //$NON-NLS-1$
						: "No supported or rejected semantic container candidates were found in the selected source."); //$NON-NLS-1$
			} else {
				viewer.getTable().setSelection(0);
				viewer.setSelection(new org.eclipse.jface.viewers.StructuredSelection(rows.get(0)), true);
				updateDetails();
			}
		} catch (RuntimeException e) {
			viewer.setInput(List.of());
			details.setText("Container analysis failed: " + e.getMessage()); //$NON-NLS-1$
			UsageViewPlugin.getDefault().getLog().log(new Status(IStatus.ERROR,
					UsageViewPlugin.PLUGIN_ID, "Container analysis failed", e)); //$NON-NLS-1$
		}
	}

	private void updateDetails() {
		if (details == null || details.isDisposed()) {
			return;
		}
		Object selected= viewer.getStructuredSelection().getFirstElement();
		details.setText(selected instanceof ContainerAnalysisRow row
				? row.details()
				: "Select an evidence row to inspect its semantic explanation."); //$NON-NLS-1$
	}

	private void navigate(DoubleClickEvent event) {
		Object selected= ((IStructuredSelection) event.getSelection()).getFirstElement();
		if (!(selected instanceof ContainerAnalysisRow row)) {
			return;
		}
		IJavaElement element= JavaCore.create(row.compilationUnitHandle());
		if (!(element instanceof ICompilationUnit unit) || !unit.exists()) {
			return;
		}
		try {
			IEditorPart editor= JavaUI.openInEditor(unit, true, true);
			if (editor instanceof ITextEditor textEditor) {
				textEditor.selectAndReveal(row.sourceStart(), row.sourceLength());
			}
		} catch (org.eclipse.ui.PartInitException | org.eclipse.jdt.core.JavaModelException e) {
			UsageViewPlugin.getDefault().getLog().log(new Status(IStatus.ERROR,
					UsageViewPlugin.PLUGIN_ID, "Could not navigate to container evidence", e)); //$NON-NLS-1$
		}
	}

	@Override
	public boolean show(ShowInContext context) {
		List<ICompilationUnit> units= compilationUnits(context == null ? null : context.getSelection());
		if (units.isEmpty()) {
			return false;
		}
		setInput(units);
		return true;
	}

	private static List<ICompilationUnit> compilationUnits(org.eclipse.jface.viewers.ISelection selection) {
		if (!(selection instanceof IStructuredSelection structured)) {
			return List.of();
		}
		List<ICompilationUnit> result= new ArrayList<>();
		for (Object element : structured.toList()) {
			ICompilationUnit unit= compilationUnit(element);
			if (unit != null) {
				result.add(unit);
			}
		}
		return result;
	}

	private static ICompilationUnit compilationUnit(Object element) {
		if (element instanceof ICompilationUnit unit) {
			return unit;
		}
		if (element instanceof IJavaElement javaElement) {
			IJavaElement ancestor= javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
			return ancestor instanceof ICompilationUnit unit ? unit : null;
		}
		if (element instanceof IFile file) {
			IJavaElement javaElement= JavaCore.create(file);
			return javaElement instanceof ICompilationUnit unit ? unit : null;
		}
		return null;
	}

	private static ICompilationUnit compilationUnit(IEditorInput input) {
		if (input instanceof IFileEditorInput fileInput) {
			return compilationUnit(fileInput.getFile());
		}
		IJavaElement javaElement= input == null ? null : input.getAdapter(IJavaElement.class);
		return compilationUnit(javaElement);
	}

	@Override
	public void setFocus() {
		if (viewer != null) {
			viewer.getControl().setFocus();
		}
	}

	@Override
	public void dispose() {
		if (workbenchSelectionListener != null && getSite() != null) {
			getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(workbenchSelectionListener);
		}
		super.dispose();
	}
}
