/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

/** Shared background request and lifecycle handling for server-backed views. */
abstract class GitResultsView extends ViewPart {

	protected Combo repository;
	protected TableViewer table;
	private Label status;
	private Job request;
	private long generation;

	@Override public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(2, false));
		new Label(parent, SWT.NONE).setText("Repository:"); //$NON-NLS-1$
		repository= new Combo(parent, SWT.DROP_DOWN);
		repository.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		createQuery(parent);
		table= new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		table.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		table.getTable().setHeaderVisible(true);
		table.getTable().setLinesVisible(true);
		table.setContentProvider(ArrayContentProvider.getInstance());
		createColumns();
		Button button= new Button(parent, SWT.PUSH);
		button.setText(actionLabel());
		button.addListener(SWT.Selection, event -> runQuery());
		status= new Label(parent, SWT.WRAP);
		status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		status.setText("Enter a server repository name."); //$NON-NLS-1$
		repository.addListener(SWT.DefaultSelection, event -> runQuery());
		parent.addDisposeListener(event -> { generation++; if (request != null) request.cancel(); });
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, "sandbox_extra_search.git_search"); //$NON-NLS-1$
	}

	protected abstract void createQuery(Composite parent);
	protected abstract void createColumns();
	protected abstract Supplier<? extends List<?>> query(String repo, SemanticSearchClient client);
	protected abstract String actionLabel();

	protected Text input(Composite parent, String label, String hint) {
		new Label(parent, SWT.NONE).setText(label);
		Text text= new Text(parent, SWT.BORDER | SWT.SEARCH);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		text.setMessage(hint);
		text.addListener(SWT.DefaultSelection, event -> runQuery());
		return text;
	}

	protected void column(String label, int width, Function<Object, String> value) {
		var column= new TableViewerColumn(table, SWT.NONE);
		column.getColumn().setText(label);
		column.getColumn().setWidth(width);
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override public String getText(Object element) { return java.util.Objects.toString(value.apply(element), ""); } //$NON-NLS-1$
		});
	}

	String repositoryName() { return repository.getText().strip(); }

	private void runQuery() {
		long current= ++generation;
		if (request != null) request.cancel();
		table.setInput(List.of());
		try {
			String repo= repositoryName();
			SemanticSearchClient.requireRepository(repo);
			// Snapshot every widget value on the UI thread before scheduling.
			var fetch= query(repo, EmbeddedSearchService.getInstance().getSearchClient());
			status.setText("Loading…"); //$NON-NLS-1$
			var display= status.getDisplay();
			request= Job.create(actionLabel(), monitor -> {
				List<?> rows= List.of();
				String message;
				try {
					if (monitor.isCanceled()) throw new OperationCanceledException();
					rows= fetch.get();
					message= monitor.isCanceled() ? "Cancelled." : rows.size() + " result(s)."; //$NON-NLS-1$ //$NON-NLS-2$
					if (monitor.isCanceled()) rows= List.of();
				} catch (OperationCanceledException cancelled) {
					message= "Cancelled."; //$NON-NLS-1$
				} catch (RuntimeException failure) {
					message= "Error: " + failure.getMessage(); //$NON-NLS-1$
				}
				var result= rows;
				var outcome= message;
				if (!display.isDisposed()) display.asyncExec(() -> {
					if (status.isDisposed() || current != generation) return;
					table.setInput(result);
					status.setText(outcome);
					status.getParent().layout();
				});
			});
			request.setUser(true);
			request.schedule();
		} catch (IllegalArgumentException invalid) {
			status.setText(invalid.getMessage());
			status.getParent().layout();
		}
	}

	@Override public void setFocus() { repository.setFocus(); }
}
