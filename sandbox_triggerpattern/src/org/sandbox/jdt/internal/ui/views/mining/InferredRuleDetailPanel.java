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
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;

/**
 * Detail panel showing inferred rules for a selected commit.
 *
 * <p>Displays each rule with its source/replacement pattern, confidence
 * percentage, and a checkbox for selection. Action buttons allow adopting
 * rules into the HintFileRegistry or exporting as {@code .sandbox-hint}.</p>
 *
 * @since 1.2.6
 */
public class InferredRuleDetailPanel extends Composite {

	private final ScrolledComposite scrolled;
	private final Composite content;
	private final Label headerLabel;
	private final List<Button> ruleCheckboxes = new ArrayList<>();
	private CommitTableEntry currentEntry;

	/**
	 * Creates the detail panel.
	 *
	 * @param parent the parent composite
	 */
	public InferredRuleDetailPanel(Composite parent) {
		super(parent, SWT.NONE);
		setLayout(new GridLayout(1, false));

		headerLabel = new Label(this, SWT.NONE);
		headerLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		headerLabel.setText("Select a commit with inferred rules (\u2705) to see details."); //$NON-NLS-1$

		scrolled = new ScrolledComposite(this, SWT.V_SCROLL | SWT.H_SCROLL);
		scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		content = new Composite(scrolled, SWT.NONE);
		content.setLayout(new GridLayout(1, false));
		scrolled.setContent(content);
	}

	/**
	 * Shows the inferred rules for the given commit table entry.
	 *
	 * @param entry the selected commit entry (may be {@code null} to clear)
	 */
	public void showRules(CommitTableEntry entry) {
		this.currentEntry = entry;
		ruleCheckboxes.clear();

		// Dispose old content children
		for (var child : content.getChildren()) {
			child.dispose();
		}

		if (entry == null || !entry.hasRules()) {
			headerLabel.setText("No rules to display."); //$NON-NLS-1$
			content.layout();
			scrolled.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
			return;
		}

		headerLabel.setText("Commit " + entry.getCommitInfo().shortId() + ": \"" //$NON-NLS-1$ //$NON-NLS-2$
				+ firstLine(entry.getCommitInfo().message()) + "\" — " //$NON-NLS-1$
				+ entry.getRuleCount() + " rule(s) inferred"); //$NON-NLS-1$

		List<InferredRule> rules = entry.getInferredRules();
		for (int i = 0; i < rules.size(); i++) {
			InferredRule rule = rules.get(i);
			createRuleWidget(content, rule, i + 1);
		}

		content.layout();
		scrolled.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	/**
	 * Returns the currently selected (checked) rules.
	 *
	 * @return list of selected inferred rules
	 */
	public List<InferredRule> getSelectedRules() {
		List<InferredRule> selected = new ArrayList<>();
		if (currentEntry == null || !currentEntry.hasRules()) {
			return selected;
		}
		List<InferredRule> rules = currentEntry.getInferredRules();
		for (int i = 0; i < ruleCheckboxes.size() && i < rules.size(); i++) {
			if (ruleCheckboxes.get(i).getSelection()) {
				selected.add(rules.get(i));
			}
		}
		return selected;
	}

	private void createRuleWidget(Composite parent, InferredRule rule, int index) {
		Composite ruleComposite = new Composite(parent, SWT.BORDER);
		ruleComposite.setLayout(new GridLayout(2, false));
		ruleComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		// Checkbox + rule header
		Button checkbox = new Button(ruleComposite, SWT.CHECK);
		checkbox.setSelection(true);
		int confidencePercent = (int) (rule.confidence() * 100);
		checkbox.setText("Rule " + index + ":  Confidence: " + confidencePercent + "%"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		checkbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		ruleCheckboxes.add(checkbox);

		// Pattern display
		StyledText patternText = new StyledText(ruleComposite, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP);
		patternText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		StringBuilder sb = new StringBuilder();
		sb.append(rule.sourcePattern()).append('\n');
		sb.append("=> ").append(rule.replacementPattern()); //$NON-NLS-1$
		if (rule.importChanges() != null && !rule.importChanges().isEmpty()) {
			for (String imp : rule.importChanges().getAddImports()) {
				sb.append("\n   addImport ").append(imp); //$NON-NLS-1$
			}
			for (String imp : rule.importChanges().getRemoveImports()) {
				sb.append("\n   removeImport ").append(imp); //$NON-NLS-1$
			}
		}
		patternText.setText(sb.toString());
		patternText.setEditable(false);
	}

	private static String firstLine(String message) {
		if (message == null) {
			return ""; //$NON-NLS-1$
		}
		int nl = message.indexOf('\n');
		return nl > 0 ? message.substring(0, nl) : message;
	}
}
