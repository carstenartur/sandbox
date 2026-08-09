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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.part.ViewPart;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.git.CommandLineGitProvider;
import org.sandbox.jdt.triggerpattern.mining.git.GitHistoryProvider;
import org.sandbox.jdt.triggerpattern.mining.git.JGitHistoryProvider;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/**
 * Eclipse view that displays Git commit history and allows asynchronous
 * AI-powered analysis of commits to infer transformation rules.
 *
 * <p>The selected repository and aggregate queue state are kept visible in the
 * view description so a potentially quota-consuming analysis never looks like
 * an unexplained background operation.</p>
 *
 * @since 1.2.6
 */
public class RefactoringMiningView extends ViewPart {

	public static final String VIEW_ID = "org.sandbox.jdt.views.refactoringMining"; //$NON-NLS-1$

	private static final int DEFAULT_MAX_COMMITS = 50;

	private TableViewer commitTable;
	private InferredRuleDetailPanel detailPanel;
	private CommitAnalysisScheduler scheduler;

	private GitHistoryProvider gitProvider;
	private int maxCommits = DEFAULT_MAX_COMMITS;
	private String activeRepositoryName = ""; //$NON-NLS-1$

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));
		gitProvider = createGitProvider();

		SashForm sash = new SashForm(parent, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		createCommitTable(sash);
		detailPanel = new InferredRuleDetailPanel(sash);
		sash.setWeights(60, 40);
		createToolbar();
	}

	@Override
	public void setFocus() {
		if (commitTable != null && !commitTable.getTable().isDisposed()) {
			commitTable.getTable().setFocus();
		}
	}

	@Override
	public void dispose() {
		if (scheduler != null) {
			scheduler.cancelAnalysis();
		}
		super.dispose();
	}

	private void createCommitTable(Composite parent) {
		commitTable = new TableViewer(parent, SWT.FULL_SELECTION | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		Table table = commitTable.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		createColumn("Commit", 80); //$NON-NLS-1$
		createColumn("Message", 300); //$NON-NLS-1$
		createColumn("Files", 50); //$NON-NLS-1$
		createColumn("AI Status", 80); //$NON-NLS-1$

		commitTable.setContentProvider(ArrayContentProvider.getInstance());
		commitTable.setLabelProvider(new CommitTableLabelProvider());
		commitTable.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection) event.getSelection();
				if (sel.isEmpty()) {
					detailPanel.showRules(null);
				} else if (sel.getFirstElement() instanceof CommitTableEntry entry) {
					detailPanel.showRules(entry);
				}
			}
		});
	}

	private void createColumn(String title, int width) {
		TableViewerColumn col = new TableViewerColumn(commitTable, SWT.NONE);
		col.getColumn().setText(title);
		col.getColumn().setWidth(width);
		col.getColumn().setResizable(true);
	}

	private void createToolbar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();

		Action analyzeAction = new Action("Analyze Project...") { //$NON-NLS-1$
			@Override
			public void run() {
				chooseAndAnalyzeProject();
			}
		};
		analyzeAction.setToolTipText("Analyze Project..."); //$NON-NLS-1$
		mgr.add(analyzeAction);

		Action stopAction = new Action("Stop Analysis") { //$NON-NLS-1$
			@Override
			public void run() {
				if (scheduler != null) {
					scheduler.cancelAnalysis();
				}
			}
		};
		stopAction.setToolTipText("Stop Analysis"); //$NON-NLS-1$
		mgr.add(stopAction);

		mgr.add(new Separator());

		Action exportAction = new Action("Export selected as .sandbox-hint") { //$NON-NLS-1$
			@Override
			public void run() {
				exportAsHintFile();
			}
		};
		exportAction.setToolTipText("Export selected as .sandbox-hint"); //$NON-NLS-1$
		mgr.add(exportAction);
		mgr.update(true);
	}

	private void chooseAndAnalyzeProject() {
		List<IProject> projects = new ArrayList<>();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project.isOpen() && project.getLocation() != null) {
				projects.add(project);
			}
		}
		if (projects.isEmpty()) {
			setContentDescription("No open workspace project is available for analysis"); //$NON-NLS-1$
			return;
		}

		ElementListSelectionDialog dialog = new ElementListSelectionDialog(getSite().getShell(), new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element instanceof IProject project ? project.getName() : super.getText(element);
			}
		});
		dialog.setTitle("Select project for Refactoring Mining"); //$NON-NLS-1$
		dialog.setMessage("Choose the Git-backed workspace project to analyze:"); //$NON-NLS-1$
		dialog.setMultipleSelection(false);
		dialog.setElements(projects.toArray());
		if (dialog.open() != Window.OK || !(dialog.getFirstResult() instanceof IProject project)) {
			return;
		}

		activeRepositoryName = project.getName();
		setContentDescription("Repository: " + activeRepositoryName); //$NON-NLS-1$
		analyzeRepository(project.getLocation().toFile().toPath());
	}

	void analyzeRepository(Path repositoryPath) {
		if (scheduler != null) {
			scheduler.cancelAnalysis();
		}
		if (activeRepositoryName.isBlank()) {
			Path fileName = repositoryPath.getFileName();
			activeRepositoryName = fileName != null ? fileName.toString() : repositoryPath.toString();
		}

		try {
			List<CommitInfo> commits = gitProvider.getHistory(repositoryPath, maxCommits);
			List<CommitTableEntry> entries = new ArrayList<>();
			for (CommitInfo commit : commits) {
				entries.add(new CommitTableEntry(commit));
			}

			commitTable.setInput(entries.toArray(CommitTableEntry[]::new));
			detailPanel.showRules(null);
			scheduler = new CommitAnalysisScheduler(gitProvider, repositoryPath, commitTable,
					this::updateProgressDescription);
			scheduler.startAnalysis(entries);
		} catch (Exception e) {
			setContentDescription("Repository: " + activeRepositoryName + " — error: " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void updateProgressDescription(CommitAnalysisScheduler.Progress progress) {
		String prefix = "Repository: " + activeRepositoryName + " — "; //$NON-NLS-1$ //$NON-NLS-2$
		if (progress.cancelled() && !progress.running()) {
			setContentDescription(prefix + "cancelled after " + progress.completed() + "/" + progress.total()); //$NON-NLS-1$ //$NON-NLS-2$
		} else if (progress.running()) {
			setContentDescription(prefix + "analyzed " + progress.completed() + "/" + progress.total() //$NON-NLS-1$ //$NON-NLS-2$
					+ " — active " + progress.active() + " — queued " + progress.queued()); //$NON-NLS-1$ //$NON-NLS-2$
		} else if (progress.total() == 0) {
			setContentDescription(prefix + "no commits to analyze"); //$NON-NLS-1$
		} else {
			setContentDescription(prefix + "analyzed " + progress.completed() + "/" + progress.total() //$NON-NLS-1$ //$NON-NLS-2$
					+ " — finished"); //$NON-NLS-1$
		}
	}

	private void exportAsHintFile() {
		List<String> selectedDslRules = detailPanel.getSelectedDslRules();
		if (selectedDslRules.isEmpty()) {
			setContentDescription("Repository: " + activeRepositoryName //$NON-NLS-1$
					+ " — select a commit and check the rules to export"); //$NON-NLS-1$
			return;
		}

		FileDialog dialog = new FileDialog(getSite().getShell(), SWT.SAVE);
		dialog.setFilterExtensions(new String[] { "*.sandbox-hint" }); //$NON-NLS-1$
		dialog.setFilterNames(new String[] { "Sandbox Hint Files (*.sandbox-hint)" }); //$NON-NLS-1$
		dialog.setFileName("inferred.sandbox-hint"); //$NON-NLS-1$

		String path = dialog.open();
		if (path != null) {
			String content = buildHintFileContent(selectedDslRules);
			try {
				java.nio.file.Files.writeString(java.nio.file.Path.of(path), content);
			} catch (java.io.IOException e) {
				setContentDescription("Repository: " + activeRepositoryName + " — export failed: " //$NON-NLS-1$ //$NON-NLS-2$
						+ e.getMessage());
			}
		}
	}

	private static String buildHintFileContent(List<String> dslRules) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!id: ai-inferred-rules>\n"); //$NON-NLS-1$
		sb.append("<!description: Rules inferred by AI from code changes>\n"); //$NON-NLS-1$
		sb.append("<!severity: info>\n"); //$NON-NLS-1$
		sb.append("<!tags: ai-inferred, mining>\n\n"); //$NON-NLS-1$
		for (String rule : dslRules) {
			sb.append(rule).append("\n;;\n\n"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	private static GitHistoryProvider createGitProvider() {
		try {
			return new JGitHistoryProvider();
		} catch (Exception e) {
			return new CommandLineGitProvider();
		}
	}
}