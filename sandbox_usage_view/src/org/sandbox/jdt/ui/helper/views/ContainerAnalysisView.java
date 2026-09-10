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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
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
	private final AtomicLong analysisGeneration= new AtomicLong();
	private TableViewer viewer;
	private Text details;
	private Display display;
	private List<ICompilationUnit> currentUnits= List.of();
	private ISelectionListener workbenchSelectionListener;
	private IPartListener2 editorPartListener;
	private volatile Job analysisJob;

	@Override
	public void createPartControl(Composite parent) {
		display= parent.getDisplay();
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
		installEditorPartListener();
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

	private void installEditorPartListener() {
		editorPartListener= new IPartListener2() {
			@Override
			public void partActivated(IWorkbenchPartReference partRef) {
				updateFromEditorPart(partRef);
			}

			@Override
			public void partBroughtToTop(IWorkbenchPartReference partRef) {
				updateFromEditorPart(partRef);
			}

			@Override
			public void partOpened(IWorkbenchPartReference partRef) {
				updateFromEditorPart(partRef);
			}

			@Override
			public void partInputChanged(IWorkbenchPartReference partRef) {
				updateFromEditorPart(partRef);
			}

			@Override
			public void partClosed(IWorkbenchPartReference partRef) {
				// Nothing to do; another activated editor will provide the next input.
			}

			@Override
			public void partDeactivated(IWorkbenchPartReference partRef) {
				// Activation of the next editor is the useful lifecycle event.
			}

			@Override
			public void partHidden(IWorkbenchPartReference partRef) {
				// View input remains useful while an editor is hidden.
			}

			@Override
			public void partVisible(IWorkbenchPartReference partRef) {
				// Visibility alone does not change the active source input.
			}
		};
		getSite().getPage().addPartListener(editorPartListener);
	}

	private void updateFromEditorPart(IWorkbenchPartReference partRef) {
		if (partRef.getPart(false) instanceof IEditorPart) {
			updateFromActiveEditor();
		}
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
		List<ICompilationUnit> normalized= unique.values().stream()
				.sorted(Comparator.comparing(ICompilationUnit::getHandleIdentifier))
				.toList();
		if (sameInput(currentUnits, normalized)) {
			return;
		}
		currentUnits= normalized;
		refresh();
	}

	private static boolean sameInput(List<ICompilationUnit> left, List<ICompilationUnit> right) {
		if (left.size() != right.size()) {
			return false;
		}
		for (int index= 0; index < left.size(); index++) {
			if (!left.get(index).getHandleIdentifier().equals(right.get(index).getHandleIdentifier())) {
				return false;
			}
		}
		return true;
	}

	private void refresh() {
		if (viewer == null || viewer.getControl().isDisposed()) {
			return;
		}
		long generation= analysisGeneration.incrementAndGet();
		Job previous= analysisJob;
		if (previous != null) {
			previous.cancel();
		}
		List<ICompilationUnit> units= currentUnits;
		if (units.isEmpty()) {
			analysisJob= null;
			applyAnalysisResult(generation, List.of(), null);
			return;
		}

		details.setText("Analyzing container usage..."); //$NON-NLS-1$
		Job job= new Job("Analyze semantic container contracts") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				List<ContainerAnalysisRow> rows= new ArrayList<>();
				RuntimeException failure= null;
				try {
					for (ICompilationUnit unit : units) {
						if (monitor.isCanceled() || generation != analysisGeneration.get()) {
							return Status.CANCEL_STATUS;
						}
						rows.addAll(analysisService.analyze(unit));
					}
				} catch (RuntimeException e) {
					failure= e;
					logAnalysisFailure(e);
				}
				if (monitor.isCanceled() || generation != analysisGeneration.get()) {
					return Status.CANCEL_STATUS;
				}
				List<ContainerAnalysisRow> result= List.copyOf(rows);
				RuntimeException resultFailure= failure;
				Display uiDisplay= display;
				if (uiDisplay != null && !uiDisplay.isDisposed()) {
					uiDisplay.asyncExec(() -> applyAnalysisResult(generation, result, resultFailure));
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		analysisJob= job;
		job.schedule();
	}

	private void applyAnalysisResult(long generation, List<ContainerAnalysisRow> rows,
			RuntimeException failure) {
		if (generation != analysisGeneration.get()
				|| viewer == null || viewer.getControl().isDisposed()
				|| details == null || details.isDisposed()) {
			return;
		}
		if (failure != null) {
			viewer.setInput(List.of());
			details.setText("Container analysis failed: " + failureSummary(failure)); //$NON-NLS-1$
			return;
		}
		viewer.setInput(rows);
		if (rows.isEmpty()) {
			details.setText(currentUnits.isEmpty()
					? "Select a Java source element to analyze container usage." //$NON-NLS-1$
					: "No supported or rejected semantic container candidates were found in the selected source."); //$NON-NLS-1$
		} else {
			viewer.setSelection(new org.eclipse.jface.viewers.StructuredSelection(rows.get(0)), true);
			updateDetails();
		}
	}

	private static String failureSummary(RuntimeException failure) {
		String message= failure.getMessage();
		String type= failure.getClass().getSimpleName();
		return message == null || message.isBlank() ? type : type + ": " + message; //$NON-NLS-1$
	}

	private static void logAnalysisFailure(RuntimeException failure) {
		UsageViewPlugin plugin= UsageViewPlugin.getDefault();
		if (plugin != null) {
			plugin.getLog().log(new Status(IStatus.ERROR,
					UsageViewPlugin.PLUGIN_ID, "Container analysis failed", failure)); //$NON-NLS-1$
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
			UsageViewPlugin plugin= UsageViewPlugin.getDefault();
			if (plugin != null) {
				plugin.getLog().log(new Status(IStatus.ERROR,
						UsageViewPlugin.PLUGIN_ID, "Could not navigate to container evidence", e)); //$NON-NLS-1$
			}
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
		if (viewer != null && !viewer.getControl().isDisposed()) {
			viewer.getControl().setFocus();
		}
	}

	@Override
	public void dispose() {
		analysisGeneration.incrementAndGet();
		Job job= analysisJob;
		if (job != null) {
			job.cancel();
		}
		if (workbenchSelectionListener != null && getSite() != null) {
			getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(workbenchSelectionListener);
		}
		if (editorPartListener != null && getSite() != null) {
			getSite().getPage().removePartListener(editorPartListener);
		}
		display= null;
		super.dispose();
	}
}
