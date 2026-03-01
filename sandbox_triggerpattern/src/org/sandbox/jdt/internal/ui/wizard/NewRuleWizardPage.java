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
package org.sandbox.jdt.internal.ui.wizard;

import java.util.Optional;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.sandbox.jdt.internal.ui.preferences.LlmPreferencePage;
import org.sandbox.jdt.triggerpattern.internal.DslValidator;
import org.sandbox.jdt.triggerpattern.internal.DslValidator.ValidationResult;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/**
 * Second wizard page &ndash; rule editor with live validation, preview, and
 * optional AI-powered rule generation.
 *
 * <p>The user enters a source pattern, an optional guard, and a replacement
 * pattern. The page validates the resulting DSL in real time and shows a
 * serialized preview.</p>
 *
 * <p>When the LLM service is available, a &ldquo;Generate with AI&rdquo; button
 * allows the user to infer a DSL rule from the source pattern using AI.</p>
 *
 * @since 1.5.0
 */
public class NewRuleWizardPage extends WizardPage {

	private Text sourcePatternText;
	private Text guardText;
	private Text replacementText;
	private StyledText previewText;
	private Label validationLabel;
	private Button generateAiButton;

	private final DslValidator validator = new DslValidator();
	private boolean customContentEntered;
	private String initialSourcePattern;
	private boolean autoTriggerAi;

	protected NewRuleWizardPage() {
		super("newRuleWizardPage"); //$NON-NLS-1$
		setTitle("New Sandbox Hint File \u2014 Rule"); //$NON-NLS-1$
		setDescription("Define a transformation rule with source pattern, guard, and replacement"); //$NON-NLS-1$
	}

	/**
	 * Pre-fills the source pattern field when the wizard is opened from a
	 * code selection. Must be called before the page is created.
	 *
	 * @param code the code snippet to use as initial source pattern
	 */
	public void setInitialSourcePattern(String code) {
		this.initialSourcePattern = code;
	}

	/**
	 * Enables automatic AI inference when the page becomes visible.
	 * Must be called before the page is created.
	 *
	 * @param auto {@code true} to auto-trigger AI inference
	 */
	public void setAutoTriggerAi(boolean auto) {
		this.autoTriggerAi = auto;
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

		// --- AI generation button ---
		createAiSection(container);

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

		// Pre-fill from code selection if available
		if (initialSourcePattern != null && !initialSourcePattern.isBlank()) {
			sourcePatternText.setText(initialSourcePattern);
		}

		setControl(container);
		setPageComplete(true);

		// Auto-trigger AI inference after the page is fully rendered
		if (autoTriggerAi && EclipseLlmService.getInstance().isAvailable()
				&& generateAiButton != null) {
			Display.getCurrent().asyncExec(this::runAiInference);
		}
	}

	/**
	 * Creates the AI / manual-assist section. Shows different UI depending on
	 * whether the LLM service is configured.
	 */
	private void createAiSection(Composite parent) {
		// Span 2 columns for the AI/manual section
		Composite aiComposite = new Composite(parent, SWT.NONE);
		aiComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		aiComposite.setLayout(new GridLayout(3, false));

		boolean aiAvailable = EclipseLlmService.getInstance().isAvailable();

		if (aiAvailable) {
			// === AI available: Generate button + provider info ===
			generateAiButton = new Button(aiComposite, SWT.PUSH);
			generateAiButton.setText("\u2728 Generate with AI"); //$NON-NLS-1$
			generateAiButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					runAiInference();
				}
			});

			Label providerInfo = new Label(aiComposite, SWT.NONE);
			providerInfo.setText(getConfiguredProviderLabel());
			providerInfo.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

			Button pasteButton = new Button(aiComposite, SWT.PUSH);
			pasteButton.setText("\u2398 Paste rule from clipboard"); //$NON-NLS-1$
			pasteButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					pasteRuleFromClipboard();
				}
			});
		} else {
			// === AI NOT available: helpful guidance ===
			Label infoIcon = new Label(aiComposite, SWT.NONE);
			infoIcon.setText("\uD83D\uDCA1"); // 💡

			Label infoLabel = new Label(aiComposite, SWT.WRAP);
			infoLabel.setText("AI generation not configured. " //$NON-NLS-1$
					+ "You can create rules manually below, " //$NON-NLS-1$
					+ "use a template from page 1, or paste an existing rule."); //$NON-NLS-1$
			infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Composite buttonBar = new Composite(aiComposite, SWT.NONE);
			buttonBar.setLayout(new RowLayout(SWT.HORIZONTAL));

			Button configureButton = new Button(buttonBar, SWT.PUSH);
			configureButton.setText("\u2699 Configure LLM..."); //$NON-NLS-1$
			configureButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					openLlmPreferences();
				}
			});

			Button pasteButton = new Button(buttonBar, SWT.PUSH);
			pasteButton.setText("\u2398 Paste rule from clipboard"); //$NON-NLS-1$
			pasteButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					pasteRuleFromClipboard();
				}
			});
		}
	}

	/**
	 * Runs AI inference in a background job to avoid blocking the UI thread.
	 */
	private void runAiInference() {
		String code = sourcePatternText.getText().trim();
		if (code.isEmpty()) {
			return;
		}

		generateAiButton.setEnabled(false);
		validationLabel.setText("\u23F3 Generating rule with AI..."); //$NON-NLS-1$

		Job job = new Job("Generating DSL rule with AI") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				AiRuleInferenceEngine engine = EclipseLlmService.getInstance().getEngine();
				String pseudoDiff = buildPseudoDiff(code);
				Optional<CommitEvaluation> result = engine.inferRuleFromDiff(pseudoDiff);

				Display.getDefault().asyncExec(() -> {
					if (generateAiButton.isDisposed()) {
						return;
					}
					generateAiButton.setEnabled(true);
					if (result.isPresent() && result.get().dslRule() != null
							&& !result.get().dslRule().isBlank()) {
						fillFieldsFromInferredRule(result.get().dslRule());
					} else {
						validationLabel.setText("\u26A0 AI could not generate a rule"); //$NON-NLS-1$
					}
				});
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
	}

	/**
	 * Builds an additions-only pseudo-diff from a code snippet for the AI engine.
	 * Aligns with the format used in {@code DslFromSelectionAssistProcessor}.
	 */
	private static String buildPseudoDiff(String code) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- a/snippet.java\n"); //$NON-NLS-1$
		sb.append("+++ b/snippet.java\n"); //$NON-NLS-1$
		String[] lines = code.split("\n", -1); //$NON-NLS-1$
		sb.append("@@ -1,0 +1,").append(lines.length).append(" @@\n"); //$NON-NLS-1$ //$NON-NLS-2$
		for (String line : lines) {
			sb.append('+').append(line).append('\n');
		}
		return sb.toString();
	}

	/**
	 * Parses the AI-generated DSL rule and fills the source/guard/replacement
	 * fields. Falls back to putting the entire rule in the source field.
	 */
	private void fillFieldsFromInferredRule(String dslRule) {
		// Try to extract source, guard, and replacement from the rule text
		String trimmed = dslRule.trim();
		if (trimmed.endsWith(";;")) { //$NON-NLS-1$
			trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
		}
		// Remove metadata directives (lines starting with <!)
		StringBuilder ruleBody = new StringBuilder();
		for (String line : trimmed.split("\n")) { //$NON-NLS-1$
			String stripped = line.trim();
			if (!stripped.startsWith("<!") && !stripped.isEmpty()) { //$NON-NLS-1$
				if (ruleBody.length() > 0) {
					ruleBody.append('\n');
				}
				ruleBody.append(stripped);
			}
		}

		String body = ruleBody.toString();
		String[] parts = body.split("\n"); //$NON-NLS-1$
		if (parts.length >= 1) {
			// First line: source pattern [:: guard]
			String sourceLine = parts[0].trim();
			int guardIdx = sourceLine.indexOf(" :: "); //$NON-NLS-1$
			if (guardIdx >= 0) {
				sourcePatternText.setText(sourceLine.substring(0, guardIdx).trim());
				guardText.setText(sourceLine.substring(guardIdx + 4).trim());
			} else {
				sourcePatternText.setText(sourceLine);
			}
			// Subsequent lines starting with "=>": replacement
			for (int i = 1; i < parts.length; i++) {
				String line = parts[i].trim();
				if (line.startsWith("=> ")) { //$NON-NLS-1$
					String replacement = line.substring(3).trim();
					// Strip guard from replacement line
					int repGuardIdx = replacement.indexOf(" :: "); //$NON-NLS-1$
					if (repGuardIdx >= 0) {
						replacement = replacement.substring(0, repGuardIdx).trim();
					}
					replacementText.setText(replacement);
					break;
				}
			}
		}
		customContentEntered = true;
		updatePreview();
	}

	/**
	 * Rebuilds the preview and validates the DSL snippet.
	 * Updates the page completion state based on validation result.
	 */
	private void updatePreview() {
		String source = sourcePatternText.getText().trim();
		if (source.isEmpty()) {
			previewText.setText(""); //$NON-NLS-1$
			validationLabel.setText(""); //$NON-NLS-1$
			setErrorMessage(null);
			setPageComplete(true);
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
			setErrorMessage(null);
			setPageComplete(true);
		} else {
			validationLabel.setText("\u26A0 " + result.message()); //$NON-NLS-1$
			setErrorMessage(result.message());
			setPageComplete(false);
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

	// --- Helper methods for AI section ---

	/**
	 * Returns a label like "Gemini &ndash; gemini-2.0-flash" from the current preferences.
	 */
	private static String getConfiguredProviderLabel() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		String provider = prefs.get(LlmPreferencePage.PREF_PROVIDER, ""); //$NON-NLS-1$
		String model = prefs.get(LlmPreferencePage.PREF_MODEL_NAME, ""); //$NON-NLS-1$
		if (provider.isBlank()) {
			return ""; //$NON-NLS-1$
		}
		return model.isBlank() ? provider : provider + " \u2013 " + model; //$NON-NLS-1$
	}

	/**
	 * Opens the LLM preference page directly, then re-checks AI availability.
	 */
	private void openLlmPreferences() {
		PreferencesUtil.createPreferenceDialogOn(
				getShell(),
				"org.sandbox.jdt.triggerpattern.preferences.LlmPreferencePage", //$NON-NLS-1$
				null, null).open();
		refreshAiAvailability();
	}

	/**
	 * Re-evaluates AI availability after preferences might have changed.
	 */
	private void refreshAiAvailability() {
		boolean nowAvailable = EclipseLlmService.getInstance().isAvailable();
		if (nowAvailable && generateAiButton == null) {
			validationLabel.setText("\u2705 AI is now configured \u2014 reopen the wizard to use 'Generate with AI'"); //$NON-NLS-1$
		}
	}

	/**
	 * Pastes a DSL rule from the system clipboard and fills the fields.
	 */
	private void pasteRuleFromClipboard() {
		Clipboard clipboard = new Clipboard(Display.getCurrent());
		try {
			String text = (String) clipboard.getContents(TextTransfer.getInstance());
			if (text != null && !text.isBlank()) {
				fillFieldsFromInferredRule(text);
			} else {
				validationLabel.setText("\u26A0 Clipboard is empty or contains no text"); //$NON-NLS-1$
			}
		} finally {
			clipboard.dispose();
		}
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
