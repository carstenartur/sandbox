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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.sandbox.jdt.triggerpattern.api.PatternIndex;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.internal.DslValidator.ValidationResult;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/**
 * Detail panel showing inferred rules or AI evaluations for a selected commit.
 *
 * <p>Besides successful candidates, the panel presents pending, running,
 * no-result and failure states so users can diagnose a mining run without
 * consulting the Eclipse error log.</p>
 *
 * @since 1.2.6
 */
public class InferredRuleDetailPanel extends Composite {

	private final ScrolledComposite scrolled;
	private final Composite content;
	private final Label headerLabel;
	private final List<Button> ruleCheckboxes = new ArrayList<>();
	private CommitTableEntry currentEntry;

	public InferredRuleDetailPanel(Composite parent) {
		super(parent, SWT.NONE);
		setLayout(new GridLayout(1, false));

		headerLabel = new Label(this, SWT.WRAP);
		headerLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		headerLabel.setText("Select a commit to inspect its analysis state and inferred rules."); //$NON-NLS-1$

		scrolled = new ScrolledComposite(this, SWT.V_SCROLL | SWT.H_SCROLL);
		scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		content = new Composite(scrolled, SWT.NONE);
		content.setLayout(new GridLayout(1, false));
		scrolled.setContent(content);
	}

	public void showRules(CommitTableEntry entry) {
		this.currentEntry = entry;
		ruleCheckboxes.clear();
		for (var child : content.getChildren()) {
			child.dispose();
		}

		if (entry == null) {
			headerLabel.setText("Select a commit to inspect its analysis state and inferred rules."); //$NON-NLS-1$
			finishLayout();
			return;
		}

		if (!entry.hasRules()) {
			String commit = "Commit " + entry.getCommitInfo().shortId() + ": \"" //$NON-NLS-1$ //$NON-NLS-2$
					+ firstLine(entry.getCommitInfo().message()) + "\" — "; //$NON-NLS-1$
			switch (entry.getStatus()) {
			case PENDING -> headerLabel.setText(commit + "waiting for analysis"); //$NON-NLS-1$
			case ANALYZING -> headerLabel.setText(commit + "analysis in progress"); //$NON-NLS-1$
			case NO_RULES -> headerLabel.setText(commit + "analysis finished; no reusable DSL rule was inferred"); //$NON-NLS-1$
			case FAILED -> headerLabel.setText(commit + "analysis failed: " //$NON-NLS-1$
					+ safeFailureMessage(entry.getFailureMessage()));
			case DONE -> headerLabel.setText(commit + "analysis finished without exportable rules"); //$NON-NLS-1$
			}
			finishLayout();
			return;
		}

		headerLabel.setText("Commit " + entry.getCommitInfo().shortId() + ": \"" //$NON-NLS-1$ //$NON-NLS-2$
				+ firstLine(entry.getCommitInfo().message()) + "\" — " //$NON-NLS-1$
				+ entry.getRuleCount() + " rule(s)"); //$NON-NLS-1$

		List<CommitEvaluation> evals = entry.getEvaluations();
		if (!evals.isEmpty()) {
			int index = 1;
			for (CommitEvaluation eval : evals) {
				if (eval.dslRule() != null && !eval.dslRule().isBlank()) {
					createEvaluationWidget(content, eval, index++);
				}
			}
		} else {
			List<InferredRule> rules = entry.getInferredRules();
			for (int i = 0; i < rules.size(); i++) {
				createRuleWidget(content, rules.get(i), i + 1);
			}
		}
		finishLayout();
	}

	public List<String> getSelectedDslRules() {
		List<String> selected = new ArrayList<>();
		if (currentEntry == null || !currentEntry.hasRules()) {
			return selected;
		}
		List<CommitEvaluation> evals = currentEntry.getEvaluations();
		if (!evals.isEmpty()) {
			int checkIndex = 0;
			for (CommitEvaluation eval : evals) {
				if (eval.dslRule() != null && !eval.dslRule().isBlank()) {
					if (checkIndex < ruleCheckboxes.size()
							&& ruleCheckboxes.get(checkIndex).getSelection()
							&& validateDsl(eval.dslRule()).valid()) {
						selected.add(eval.dslRule());
					}
					checkIndex++;
				}
			}
		}
		return selected;
	}

	public List<InferredRule> getSelectedRules() {
		List<InferredRule> selected = new ArrayList<>();
		if (currentEntry == null || !currentEntry.hasRules()) {
			return selected;
		}
		List<InferredRule> rules = currentEntry.getInferredRules();
		for (int i = 0; i < ruleCheckboxes.size() && i < rules.size(); i++) {
			if (ruleCheckboxes.get(i).getSelection() && validateDsl(asDsl(rules.get(i))).valid()) {
				selected.add(rules.get(i));
			}
		}
		return selected;
	}

	private void createEvaluationWidget(Composite parent, CommitEvaluation eval, int index) {
		Composite ruleComposite = new Composite(parent, SWT.BORDER);
		ruleComposite.setLayout(new GridLayout(2, false));
		ruleComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		Button checkbox = new Button(ruleComposite, SWT.CHECK);
		ValidationResult validation = validateDsl(eval.dslRule());
		checkbox.setSelection(false);
		checkbox.setEnabled(validation.valid());
		String trafficIcon = switch (eval.trafficLight()) {
		case GREEN -> "\uD83D\uDFE2"; //$NON-NLS-1$
		case YELLOW -> "\uD83D\uDFE1"; //$NON-NLS-1$
		case RED -> "\uD83D\uDD34"; //$NON-NLS-1$
		case NOT_APPLICABLE -> "\u2B55"; //$NON-NLS-1$
		};
		checkbox.setText("Rule " + index + "  AI assessment: " + trafficIcon + "  " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ (eval.category() != null ? eval.category() : "")); //$NON-NLS-1$
		checkbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		ruleCheckboxes.add(checkbox);

		if (eval.summary() != null && !eval.summary().isBlank()) {
			Label summaryLabel = new Label(ruleComposite, SWT.WRAP);
			summaryLabel.setText(eval.summary());
			summaryLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		}

		StyledText ruleText = new StyledText(ruleComposite, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.BORDER);
		ruleText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		ruleText.setText(eval.dslRule());
		ruleText.setEditable(false);

		createValidationLabels(ruleComposite, validation);
	}

	private void createRuleWidget(Composite parent, InferredRule rule, int index) {
		Composite ruleComposite = new Composite(parent, SWT.BORDER);
		ruleComposite.setLayout(new GridLayout(2, false));
		ruleComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		Button checkbox = new Button(ruleComposite, SWT.CHECK);
		ValidationResult validation = validateDsl(asDsl(rule));
		checkbox.setSelection(false);
		checkbox.setEnabled(validation.valid());
		int confidencePercent = (int) (rule.confidence() * 100);
		checkbox.setText("Rule " + index + ":  Inference confidence: " + confidencePercent + "%"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		checkbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		ruleCheckboxes.add(checkbox);

		StyledText patternText = new StyledText(ruleComposite, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP);
		patternText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		StringBuilder sb = new StringBuilder();
		sb.append(rule.sourcePattern()).append('\n');
		sb.append("=> ").append(rule.replacementPattern()); //$NON-NLS-1$
		patternText.setText(sb.toString());
		patternText.setEditable(false);
		createValidationLabels(ruleComposite, validation);
	}

	private static String asDsl(InferredRule rule) {
		return rule.sourcePattern() + "\n=> " + rule.replacementPattern() + "\n;;"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Rechecks actual content; an inference provider's VALID flag is not evidence. */
	private static ValidationResult validateDsl(String dsl) {
		try {
			var hint = new HintFileParser().parse(dsl);
			if (hint.getRules().isEmpty()) {
				return new ValidationResult(false, "No rules found"); //$NON-NLS-1$
			}
			if (new PatternIndex(hint.getRules()).size() != hint.getRules().size()) {
				return new ValidationResult(false, "A source pattern cannot be parsed by TriggerPattern"); //$NON-NLS-1$
			}
			return new ValidationResult(true, "Language checked"); //$NON-NLS-1$
		} catch (HintFileParser.HintParseException | RuntimeException e) {
			return new ValidationResult(false, e.getMessage() == null ? "Invalid DSL" : e.getMessage()); //$NON-NLS-1$
		}
	}

	private static void createValidationLabels(Composite parent, ValidationResult validation) {
		Label language = new Label(parent, SWT.WRAP);
		language.setText(validation.valid() ? "Draft — language checked" //$NON-NLS-1$
				: "Draft — Language check failed: " + validation.message()); //$NON-NLS-1$
		language.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		Label scope = new Label(parent, SWT.WRAP);
		scope.setText("Examples: not checked; project: not checked. Language checking does not establish behavioral safety."); //$NON-NLS-1$
		scope.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		Label selection = new Label(parent, SWT.WRAP);
		selection.setText(validation.valid() ? "Select explicitly to export this draft for review." //$NON-NLS-1$
				: "Correct the rule before exporting it as a project rule."); //$NON-NLS-1$
		selection.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
	}

	private void finishLayout() {
		content.layout();
		scrolled.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	private static String safeFailureMessage(String message) {
		return message == null || message.isBlank() ? "unknown analysis error" : message; //$NON-NLS-1$
	}

	private static String firstLine(String message) {
		if (message == null) {
			return ""; //$NON-NLS-1$
		}
		int nl = message.indexOf('\n');
		return nl > 0 ? message.substring(0, nl) : message;
	}
}
