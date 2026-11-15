/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.sandbox.jdt.triggerpattern.api.HintPlanRequirement;
import org.sandbox.jdt.triggerpattern.eclipse.ProjectPlanInspector;
import org.sandbox.jdt.triggerpattern.eclipse.ProjectPlanInspector.Report;

/** Browses the source planner's evidence; only the coordinated cleanup may execute it. */
final class PlanInspectionDialog extends Dialog {
	private final String contract;
	private final Report report;

	PlanInspectionDialog(Shell shell, String contract, Report report) {
		super(shell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		this.contract= contract;
		this.report= report;
	}

	static void open(Shell shell, String dsl, IJavaProject project) {
		String title= "Browse semantic plan"; //$NON-NLS-1$
		Optional<String> requirement;
		try {
			requirement= HintPlanRequirement.fromContent(dsl);
		} catch (IllegalArgumentException e) {
			MessageDialog.openError(shell, title, e.getMessage());
			return;
		}
		if (requirement.isEmpty() || project == null) {
			MessageDialog.openInformation(shell, title,
					"Select a destination Java project and declare <!requires-plan: contract-id> in the rule."); //$NON-NLS-1$
			return;
		}
		String contract= requirement.get();
		var result= new AtomicReference<Report>();
		try {
			new ProgressMonitorDialog(shell).run(true, true, monitor -> {
				try {
					result.set(ProjectPlanInspector.inspect(contract, project, monitor));
				} catch (OperationCanceledException e) {
					throw new InterruptedException();
				} catch (CoreException e) {
					result.set(new Report(List.of(), List.of(e.getStatus().getMessage())));
				}
			});
			new PlanInspectionDialog(shell, contract, result.get()).open();
		} catch (InterruptedException e) {
			// Cancellation does not open an incomplete plan.
		} catch (InvocationTargetException e) {
			MessageDialog.openError(shell, title, String.valueOf(e.getCause()));
		}
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Semantic plan — " + contract); //$NON-NLS-1$
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area= (Composite) super.createDialogArea(parent);
		Tree tree= new Tree(area, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData size= new GridData(SWT.FILL, SWT.FILL, true, true);
		size.widthHint= 760;
		size.heightHint= 380;
		tree.setLayoutData(size);
		for (var component : report.components()) {
			var root= new TreeItem(tree, SWT.NONE);
			root.setText(component.label());
			var plan= component.plan();
			var keys= new LinkedHashSet<>(plan.rolesByNode().keySet());
			keys.addAll(plan.valuesByNode().keySet());
			for (var key : keys.stream().sorted(Comparator.comparing(Object::toString)).toList()) {
				var node= item(root, key.toString());
				for (String role : new TreeSet<>(plan.rolesByNode().getOrDefault(key, Set.of()))) {
					item(node, role);
				}
				new TreeMap<>(plan.valuesByNode().getOrDefault(key, Map.of()))
						.forEach((name, value) -> item(node, name + " = " + value)); //$NON-NLS-1$
			}
			for (var relation : plan.relations()) {
				var row= item(root, relation.kind() + "  " + relation.source() + " → " + relation.target()); //$NON-NLS-1$ //$NON-NLS-2$
				new TreeMap<>(relation.attributes()).forEach((name, value) -> item(row, name + " = " + value)); //$NON-NLS-1$
			}
			root.setExpanded(true);
		}
		for (String diagnostic : report.diagnostics()) {
			new TreeItem(tree, SWT.NONE).setText(diagnostic);
		}
		if (tree.getItemCount() == 0) {
			new TreeItem(tree, SWT.NONE).setText("No eligible component in the selected project."); //$NON-NLS-1$
		}
		return area;
	}

	private static TreeItem item(TreeItem parent, String text) {
		TreeItem child= new TreeItem(parent, SWT.NONE);
		child.setText(text);
		return child;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.CLIENT_ID, "Copy planner scaffold", false); //$NON-NLS-1$
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.CLOSE_LABEL, true);
	}

	@Override
	protected void buttonPressed(int id) {
		if (id == IDialogConstants.CLIENT_ID) {
			Clipboard clipboard= new Clipboard(getShell().getDisplay());
			try {
				clipboard.setContents(new Object[] { scaffold(contract) }, new Transfer[] { TextTransfer.getInstance() });
			} finally {
				clipboard.dispose();
			}
			return;
		}
		super.buttonPressed(id);
	}

	static String scaffold(String contract) {
		var literal= AST.newAST(AST.getJLSLatest(), false).newStringLiteral();
		literal.setLiteralValue(contract);
		return """
				import java.util.List;
				import org.eclipse.core.runtime.CoreException;
				import org.eclipse.core.runtime.IProgressMonitor;
				import org.eclipse.jdt.core.IJavaProject;
				import org.sandbox.jdt.triggerpattern.eclipse.ProjectPlanInspector;

				public final class ProjectInspector implements ProjectPlanInspector {
				    public static final String CONTRACT = CONTRACT_LITERAL;

				    @Override
				    public Report discover(String contract, IJavaProject project, IProgressMonitor monitor) throws CoreException {
				        // Discover closed source components, then return their immutable semantic plans.
				        // Register this class at sandbox_common.planInspectors with this exact contract.
				        return new Report(List.of(), List.of("PLAN_NOT_IMPLEMENTED: Complete discovery for " + CONTRACT));
				    }
				}
				""".replace("CONTRACT_LITERAL", literal.getEscapedValue()); //$NON-NLS-1$
	}
}
