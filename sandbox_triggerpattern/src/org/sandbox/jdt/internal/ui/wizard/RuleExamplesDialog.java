/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.sandbox.jdt.triggerpattern.api.RuleExampleRefiner;
import org.sandbox.jdt.triggerpattern.api.RuleExampleRefiner.Proposal;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite.Example;
import org.sandbox.jdt.triggerpattern.api.RuleExampleVerifier;
import org.sandbox.jdt.triggerpattern.api.RuleExampleVerifier.Report;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlanContext;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument.ChangeExample;

/** Editable evidence and cancellable replay; no result is persisted as authority. */
final class RuleExamplesDialog extends TitleAreaDialog {

	private static final int USE_PROPOSAL = IDialogConstants.CLIENT_ID + 1;
	private final String dsl;
	private final IJavaProject project;
	private final ChangeExample seed;
	private final Function<ChangeExample, String> documentFactory;
	private RuleExampleSuite suite;
	private Text json;
	private Text output;
	private Proposal proposal;
	private Proposal acceptedProposal;

	RuleExamplesDialog(Shell shell, String dsl, RuleExampleSuite suite, IJavaProject project,
			ChangeExample seed, Function<ChangeExample, String> documentFactory) {
		super(shell);
		this.dsl = dsl;
		this.suite = suite == null ? new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, List.of()) : suite;
		this.project = project;
		this.seed = seed;
		this.documentFactory = documentFactory;
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("TriggerPattern rule examples"); //$NON-NLS-1$
	}

	@Override
	protected Point getInitialSize() {
		return new Point(920, 760);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		setTitle("Examples for the complete rule"); //$NON-NLS-1$
		setMessage("Keep replayable source evidence beside the rule. Passing examples do not prove a rule safe for all code."); //$NON-NLS-1$
		Composite content = new Composite(area, SWT.NONE);
		content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		content.setLayout(new GridLayout());

		Label guidance = new Label(content, SWT.WRAP);
		guidance.setText("Schema 1: each example has a unique name, unitName (a Java source path within the selected project), " //$NON-NLS-1$
				+ "complete before/after compilation units, and expectChange. Use true for a real change; " //$NON-NLS-1$
				+ "use false with identical source for a counterexample. JSON represents source line breaks as \\n.\n" //$NON-NLS-1$
				+ "Replay requires at least one of each, exact expected output, compilation and an unchanged second application. " //$NON-NLS-1$
				+ "Imports and structured actions currently report UNKNOWN; use Try on my code for them.\n" //$NON-NLS-1$
				+ (hasProject() ? "Project: " + project.getElementName() //$NON-NLS-1$
						: "Choose a Java project on the destination page to enable Verify and Refine.")); //$NON-NLS-1$
		guidance.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		json = new Text(content, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		GridData jsonData = new GridData(SWT.FILL, SWT.FILL, true, true);
		jsonData.heightHint = 250;
		json.setLayoutData(jsonData);
		json.setText(suite.toJson());
		json.addModifyListener(event -> invalidateResult());

		Composite actions = new Composite(content, SWT.NONE);
		actions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		actions.setLayout(new RowLayout());
		action(actions, "Add positive", () -> addExample(true)); //$NON-NLS-1$
		action(actions, "Add counterexample", () -> addExample(false)); //$NON-NLS-1$
		Button verify = action(actions, "Verify", () -> runVerification(false)); //$NON-NLS-1$
		verify.setEnabled(hasProject());
		Button refine = action(actions, "Refine from examples", () -> runVerification(true)); //$NON-NLS-1$
		refine.setEnabled(hasProject() && seed != null && documentFactory != null);
		refine.setToolTipText("Available for the Before/After helper fields; imported or edited complete documents stay intact. " //$NON-NLS-1$
				+ "Tries up to 64 choices for at most six shared names, then offers a proposal for review."); //$NON-NLS-1$

		Label helperNotice = new Label(content, SWT.WRAP);
		helperNotice.setText("Add buttons create editable starting points. Replace them with independent examples. " //$NON-NLS-1$
				+ "Keeping examples does not run a cleanup or qualify a rule for later use."); //$NON-NLS-1$
		helperNotice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		output = new Text(content, SWT.READ_ONLY | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		GridData outputData = new GridData(SWT.FILL, SWT.FILL, true, true);
		outputData.heightHint = 180;
		output.setLayoutData(outputData);
		output.setText("No replay result for this dialog. Edit the examples, then choose Verify."); //$NON-NLS-1$
		return area;
	}

	private static Button action(Composite parent, String label, Runnable action) {
		Button button = new Button(parent, SWT.PUSH);
		button.setText(label);
		button.addListener(SWT.Selection, event -> action.run());
		return button;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, USE_PROPOSAL, "Use proposed rule", false).setEnabled(false); //$NON-NLS-1$
		createButton(parent, IDialogConstants.OK_ID, "Keep examples", true); //$NON-NLS-1$
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == USE_PROPOSAL && proposal != null) {
			acceptedProposal = proposal;
			okPressed();
		} else {
			super.buttonPressed(buttonId);
		}
	}

	@Override
	protected void okPressed() {
		RuleExampleSuite parsed = parseExamples();
		if (parsed != null) {
			suite = parsed;
			super.okPressed();
		} else {
			acceptedProposal = null;
		}
	}

	RuleExampleSuite getSuite() {
		return suite;
	}

	Proposal getAcceptedProposal() {
		return acceptedProposal;
	}

	private RuleExampleSuite parseExamples() {
		try {
			RuleExampleSuite parsed = RuleExampleSuite.fromJson(json.getText());
			setErrorMessage(null);
			return parsed;
		} catch (IllegalArgumentException exception) {
			setErrorMessage(exception.getMessage());
			return null;
		}
	}

	private void invalidateResult() {
		proposal = null;
		acceptedProposal = null;
		Button use = getButton(USE_PROPOSAL);
		if (use != null) {
			use.setEnabled(false);
		}
		if (output != null) {
			output.setText("Examples changed. Verify again; no previous result or proposal is retained."); //$NON-NLS-1$
		}
	}

	private void addExample(boolean positive) {
		RuleExampleSuite current = parseExamples();
		if (current == null) {
			return;
		}
		var entries = new ArrayList<>(current.examples());
		String prefix = positive ? "Positive " : "Counterexample "; //$NON-NLS-1$ //$NON-NLS-2$
		int number = 1;
		var names = entries.stream().map(Example::name).toList();
		while (names.contains(prefix + number)) {
			number++;
		}
		Example source = entries.stream().filter(example -> !positive || example.expectChange())
				.findFirst().orElse(null);
		String before = source == null ? "class Example { int value = 0; }\n" : source.before(); //$NON-NLS-1$
		String after = positive ? source == null ? "class Example { int value = 1; }\n" : source.after() : before; //$NON-NLS-1$
		entries.add(new Example(prefix + number, source == null ? "Example.java" : source.unitName(), before, after, positive)); //$NON-NLS-1$
		json.setText(new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, entries).toJson());
	}

	private boolean hasProject() {
		return project != null && project.exists() && project.getProject().isAccessible();
	}

	private void runVerification(boolean refine) {
		RuleExampleSuite current = parseExamples();
		if (current == null) {
			return;
		}
		invalidateResult();
		if (!hasProject()) {
			setErrorMessage("A currently accessible Java project is required. Choose it on the destination page."); //$NON-NLS-1$
			return;
		}
		AtomicReference<Report> report = new AtomicReference<>();
		AtomicReference<Proposal> proposed = new AtomicReference<>();
		try {
			Map<String, String> options = Map.copyOf(project.getOptions(true));
			new ProgressMonitorDialog(getShell()).run(true, true, monitor -> {
				monitor.beginTask(refine ? "Refining from examples (up to 64 candidates)" : "Verifying rule examples", //$NON-NLS-1$ //$NON-NLS-2$
						IProgressMonitor.UNKNOWN);
				try (var scope = SemanticRewritePlanContext.install(SemanticRewritePlan.empty(), options,
						() -> checkCanceled(monitor))) {
					RuleExampleVerifier verifier = new RuleExampleVerifier(options,
							(unitName, source) -> parseSource(project, options, unitName, source, monitor));
					if (refine) {
						RuleExampleRefiner.refine(seed, current, verifier, example -> {
							checkCanceled(monitor);
							return documentFactory.apply(example);
						}).ifPresent(proposed::set);
					} else {
						report.set(verifier.verify(dsl, current));
					}
					checkCanceled(monitor);
				} finally {
					monitor.done();
				}
			});
			if (refine) {
				proposal = proposed.get();
				if (proposal == null) {
					output.setText("No candidate passed all examples within the bounded search. The complete rule is unchanged."); //$NON-NLS-1$
					return;
				}
				output.setText(format(proposal.report()) + "\nProposed complete rule — review before using:\n" //$NON-NLS-1$
						+ documentFactory.apply(proposal.example()));
				getButton(USE_PROPOSAL).setEnabled(true);
			} else {
				output.setText(format(report.get()));
			}
		} catch (InvocationTargetException exception) {
			if (exception.getCause() instanceof OperationCanceledException) {
				output.setText("Canceled. No result or proposal is retained."); //$NON-NLS-1$
			} else {
				setErrorMessage("Examples could not be checked: " + exception.getCause().getMessage()); //$NON-NLS-1$
			}
		} catch (InterruptedException | OperationCanceledException exception) {
			output.setText("Canceled. No result or proposal is retained."); //$NON-NLS-1$
		} catch (RuntimeException exception) {
			setErrorMessage("Examples could not be checked: " + exception.getMessage()); //$NON-NLS-1$
		}
	}

	static CompilationUnit parseSource(IJavaProject project, Map<String, String> options,
			String unitName, String source, IProgressMonitor monitor) {
		checkCanceled(monitor);
		if (project == null || !project.exists() || !project.getProject().isAccessible()) {
			throw new IllegalArgumentException("An accessible Java project is required for example bindings"); //$NON-NLS-1$
		}
		if (unitName == null || !unitName.endsWith(".java") || unitName.indexOf('\\') >= 0 //$NON-NLS-1$
				|| unitName.indexOf(':') >= 0 || Arrays.stream(unitName.split("/", -1)) //$NON-NLS-1$
						.anyMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."))) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new IllegalArgumentException("unitName must be a project-relative Java source path, such as src/p/Example.java"); //$NON-NLS-1$
		}
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(project);
		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		// JDT needs the workspace path when setProject() is used; the sidecar stays
		// project-relative so its evidence can be reopened in a renamed project.
		parser.setUnitName(project.getPath().append(new Path(unitName)).toPortableString());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(false);
		parser.setStatementsRecovery(false);
		CompilationUnit result = (CompilationUnit) parser.createAST(monitor);
		checkCanceled(monitor);
		return result;
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor.isCanceled() || Thread.currentThread().isInterrupted()) {
			throw new OperationCanceledException();
		}
	}

	private static String format(Report report) {
		StringBuilder text = new StringBuilder(report.passed()
				? "PASS for these examples and this rule. This is bounded evidence, not a general proof.\n" //$NON-NLS-1$
				: "The example suite has not passed.\n"); //$NON-NLS-1$
		if (!report.diagnostic().isEmpty()) {
			text.append(report.diagnostic()).append('\n');
		}
		text.append("Rule SHA-256: ").append(report.ruleSha256()).append('\n') //$NON-NLS-1$
				.append("Examples SHA-256: ").append(report.suiteSha256()).append('\n'); //$NON-NLS-1$
		for (var result : report.results()) {
			text.append('\n').append(result.outcome()).append(" — ").append(result.name()) //$NON-NLS-1$
					.append(": ").append(result.message()).append('\n'); //$NON-NLS-1$
			if (result.outcome() != RuleExampleVerifier.Outcome.PASS) {
				text.append(result.actualSource()).append('\n');
			}
		}
		return text.toString();
	}
}
