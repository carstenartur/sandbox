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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.wizard;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.sandbox.jdt.triggerpattern.internal.DslValidator;
import org.sandbox.jdt.triggerpattern.internal.DslValidator.ValidationResult;

/**
 * Second wizard page &ndash; rule editor with live validation and preview.
 *
 * <p>The user enters a source pattern, an optional guard, and a replacement pattern.
 * The page validates the resulting DSL in real time and shows a serialized preview.</p>
 *
 * @since 1.5.0
 */
public class NewRuleWizardPage extends WizardPage {

	private Text sourcePatternText;
	private Text guardText;
	private Text replacementText;
	private StyledText previewText;
	private Label validationLabel;

	private final DslValidator validator = new DslValidator();
	private boolean customContentEntered;

	protected NewRuleWizardPage() {
		super("newRuleWizardPage"); //$NON-NLS-1$
		setTitle("New Sandbox Hint File \u2014 Rule"); //$NON-NLS-1$
		setDescription("Define a transformation rule with source pattern, guard, and replacement"); //$NON-NLS-1$
	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(2, false));

		ModifyListener modifyListener = e -> {
			customContentEntered = true;
			updatePreview();
		};

		// --- Source pattern ---
		new Label(container, SWT.NONE).setText("Source &Pattern:"); //$NON-NLS-1$
		sourcePatternText = new Text(container, SWT.BORDER | SWT.SINGLE);
		sourcePatternText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		sourcePatternText.setMessage("$x.getBytes(\"UTF-8\")"); //$NON-NLS-1$
		sourcePatternText.addModifyListener(modifyListener);

		// --- Guard ---
		new Label(container, SWT.NONE).setText("&Guard (optional):"); //$NON-NLS-1$
		guardText = new Text(container, SWT.BORDER | SWT.SINGLE);
		guardText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		guardText.setMessage("sourceVersionGE(11)"); //$NON-NLS-1$
		guardText.addModifyListener(modifyListener);

		// --- Replacement ---
		new Label(container, SWT.NONE).setText("&Replacement:"); //$NON-NLS-1$
		replacementText = new Text(container, SWT.BORDER | SWT.SINGLE);
		replacementText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		replacementText.setMessage("$x.getBytes(java.nio.charset.StandardCharsets.UTF_8)"); //$NON-NLS-1$
		replacementText.addModifyListener(modifyListener);

		// --- Preview ---
		Group previewGroup = new Group(container, SWT.NONE);
		previewGroup.setText("Preview"); //$NON-NLS-1$
		previewGroup.setLayout(new GridLayout(1, false));
		GridData previewGroupData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		previewGroup.setLayoutData(previewGroupData);

		previewText = new StyledText(previewGroup, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL);
		GridData previewData = new GridData(SWT.FILL, SWT.FILL, true, true);
		previewData.heightHint = 120;
		previewText.setLayoutData(previewData);

		// --- Validation label ---
		validationLabel = new Label(container, SWT.NONE);
		GridData valData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		validationLabel.setLayoutData(valData);

		setControl(container);
		setPageComplete(true);
	}

	/**
	 * Rebuilds the preview and validates the DSL snippet.
	 */
	private void updatePreview() {
		String source = sourcePatternText.getText().trim();
		if (source.isEmpty()) {
			previewText.setText(""); //$NON-NLS-1$
			validationLabel.setText(""); //$NON-NLS-1$
			return;
		}

		String preview = buildRuleBlock(source, guardText.getText().trim(),
				replacementText.getText().trim());
		previewText.setText(preview);

		// Wrap the rule in minimal metadata so the parser is happy
		String fullDsl = buildFullDslForValidation(preview);
		ValidationResult result = validator.validate(fullDsl);
		if (result.valid()) {
			validationLabel.setText("\u2705 Valid DSL rule"); //$NON-NLS-1$
		} else {
			validationLabel.setText("\u26A0 " + result.message()); //$NON-NLS-1$
		}
	}

	/**
	 * Builds the DSL text for a single rule.
	 */
	private static String buildRuleBlock(String source, String guard, String replacement) {
		StringBuilder sb = new StringBuilder();
		sb.append(source);
		if (!guard.isEmpty()) {
			sb.append(" :: ").append(guard); //$NON-NLS-1$
		}
		sb.append('\n');
		if (!replacement.isEmpty()) {
			sb.append("=> ").append(replacement).append('\n'); //$NON-NLS-1$
		}
		sb.append(";;"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Wraps a rule block with minimal metadata so the parser can validate it.
	 */
	private static String buildFullDslForValidation(String ruleBlock) {
		return "<!id: _wizard_preview>\n" + ruleBlock + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	// --- Public API for the wizard ---

	/**
	 * Returns {@code true} when the user entered any content on this page.
	 */
	public boolean hasCustomContent() {
		return customContentEntered && !sourcePatternText.getText().trim().isEmpty();
	}

	/**
	 * Returns the complete rule block including source, guard, and replacement.
	 */
	public String getFullRuleBlock() {
		return "\n" + buildRuleBlock( //$NON-NLS-1$
				sourcePatternText.getText().trim(),
				guardText.getText().trim(),
				replacementText.getText().trim()) + "\n"; //$NON-NLS-1$
	}
}
