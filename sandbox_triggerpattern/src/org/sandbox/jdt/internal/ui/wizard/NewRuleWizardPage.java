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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
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
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.sandbox.jdt.internal.ui.preferences.LlmPreferencePage;
import org.sandbox.jdt.triggerpattern.api.PatternIndex;
import org.sandbox.jdt.triggerpattern.internal.DslValidator.ValidationResult;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintProgramParser;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument;
import org.sandbox.jdt.triggerpattern.internal.ChangeVariableGeneralizer;
import org.sandbox.jdt.triggerpattern.editor.SandboxHintPartitionScanner;
import org.sandbox.jdt.triggerpattern.editor.SandboxHintSourceViewerConfiguration;
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
	private final IDocument ruleDocument = new Document();
	private Label validationLabel;
	private Button generateAiButton;
	private Button projectPreviewButton;
	private Button generalizeVariablesButton;

	private final HintProgramParser validator = new HintProgramParser();
	private boolean customContentEntered;
	private String initialSourcePattern;
	private String initialReplacement = ""; //$NON-NLS-1$
	private String initialDsl;
	private boolean autoTriggerAi;
	private boolean updatingDocument;
	private boolean requiresReplacement;
	private String authoringError;
	private Consumer<String> projectPreviewAction;
	private Supplier<Map<String, String>> metadataSupplier = Map::of;
	private Supplier<String> defaultContentSupplier = () -> ""; //$NON-NLS-1$
	private final Map<String, String> appliedMetadata = new LinkedHashMap<>();

	protected NewRuleWizardPage() {
		super("newRuleWizardPage"); //$NON-NLS-1$
		setTitle("New Sandbox Hint File \u2014 Rule"); //$NON-NLS-1$
		setDescription("Review the Java change, explicitly generalize variables, then try the complete rule on your code"); //$NON-NLS-1$
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

	/** Starts with the actual before and after Java code, retaining every line. */
	public void setInitialChange(String before, String after) {
		initialSourcePattern = before;
		initialReplacement = after;
		requiresReplacement = true;
	}

	/** Imports a complete file, retaining all metadata, rules and alternatives. */
	public void setInitialDsl(String dsl) {
		initialDsl = dsl;
	}

	/** Supplies a real project-run action. A DSL preview alone is not a project check. */
	public void setProjectPreviewAction(Consumer<String> action) {
		boolean availabilityChanged = (projectPreviewAction == null) != (action == null);
		projectPreviewAction = action;
		if (availabilityChanged && projectPreviewButton != null && !projectPreviewButton.isDisposed()) {
			validateDocument();
		}
	}

	/** Shares explicit metadata edits with copy, document preview and file saving. */
	public void setMetadataSupplier(Supplier<Map<String, String>> supplier) {
		metadataSupplier = supplier;
	}

	/** Lets template choices use the same editable document and project preview. */
	public void setDefaultContentSupplier(Supplier<String> supplier) {
		defaultContentSupplier = supplier;
	}

	@Override
	public void setVisible(boolean visible) {
		if (visible) {
			updatingDocument = true;
			try {
				if (!customContentEntered) {
					appliedMetadata.clear();
					ruleDocument.set(defaultContentSupplier.get());
				}
				applyPendingMetadata();
			} finally {
				updatingDocument = false;
			}
		}
		super.setVisible(visible);
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
			updateDocumentFromFields();
		};

		// --- Source pattern ---
		new Label(container, SWT.NONE).setText("&Before / source pattern:"); //$NON-NLS-1$
		sourcePatternText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		GridData sourceData = new GridData(SWT.FILL, SWT.FILL, true, false);
		sourceData.heightHint = 70;
		sourcePatternText.setLayoutData(sourceData);
		sourcePatternText.setMessage("$x.getBytes(\"UTF-8\")"); //$NON-NLS-1$
		sourcePatternText.addModifyListener(modifyListener);

		// --- Guard ---
		new Label(container, SWT.NONE).setText("&Guard (optional):"); //$NON-NLS-1$
		guardText = new Text(container, SWT.BORDER | SWT.SINGLE);
		guardText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		guardText.setMessage("sourceVersionGE(11)"); //$NON-NLS-1$
		guardText.addModifyListener(modifyListener);

		// --- Replacement ---
		new Label(container, SWT.NONE).setText("&After / replacement:"); //$NON-NLS-1$
		replacementText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		GridData replacementData = new GridData(SWT.FILL, SWT.FILL, true, false);
		replacementData.heightHint = 70;
		replacementText.setLayoutData(replacementData);
		replacementText.setMessage("$x.getBytes(java.nio.charset.StandardCharsets.UTF_8)"); //$NON-NLS-1$
		replacementText.addModifyListener(modifyListener);

		generalizeVariablesButton = new Button(container, SWT.PUSH);
		generalizeVariablesButton.setText("Choose variables to generalize..."); //$NON-NLS-1$
		generalizeVariablesButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		generalizeVariablesButton.addListener(SWT.Selection, event -> chooseVariables());

		// --- AI generation button ---
		createAiSection(container);

		Label guidance = new Label(container, SWT.WRAP);
		guidance.setText("Names, method calls and literals stay fixed. To generalize, explicitly replace a variable " //$NON-NLS-1$
				+ "with the same $placeholder in Before and After. Review the resulting rule on your project."); //$NON-NLS-1$
		guidance.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		// --- The complete document is authoritative, including imported alternatives. ---
		Group previewGroup = new Group(container, SWT.NONE);
		previewGroup.setText("Complete rule document (editable)"); //$NON-NLS-1$
		previewGroup.setLayout(new GridLayout(1, false));
		GridData previewGroupData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		previewGroup.setLayoutData(previewGroupData);

		SourceViewer ruleViewer = new SourceViewer(previewGroup, null, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		GridData previewData = new GridData(SWT.FILL, SWT.FILL, true, true);
		previewData.heightHint = 120;
		ruleViewer.getControl().setLayoutData(previewData);
		FastPartitioner partitioner = new FastPartitioner(new SandboxHintPartitionScanner(),
				SandboxHintPartitionScanner.PARTITION_TYPES);
		partitioner.connect(ruleDocument);
		ruleDocument.setDocumentPartitioner(partitioner);
		ruleViewer.configure(new SandboxHintSourceViewerConfiguration());
		ruleViewer.setDocument(ruleDocument);
		ruleViewer.getTextWidget().addVerifyKeyListener(event -> {
			if (event.character == ' ' && (event.stateMask & SWT.MOD1) != 0
					&& ruleViewer.canDoOperation(SourceViewer.CONTENTASSIST_PROPOSALS)) {
				ruleViewer.doOperation(SourceViewer.CONTENTASSIST_PROPOSALS);
				event.doit = false;
			}
		});
		ruleDocument.addDocumentListener(new IDocumentListener() {
			@Override
			public void documentAboutToBeChanged(DocumentEvent event) {
				// The complete document is edited in place.
			}
			@Override
			public void documentChanged(DocumentEvent event) {
				if (!updatingDocument) {
					customContentEntered = true;
					lockHelperFields();
				}
				validateDocument();
			}
		});

		Composite documentActions = new Composite(container, SWT.NONE);
		documentActions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		documentActions.setLayout(new RowLayout(SWT.HORIZONTAL));
		projectPreviewButton = new Button(documentActions, SWT.PUSH);
		projectPreviewButton.setText("Try on my code..."); //$NON-NLS-1$
		projectPreviewButton.addListener(SWT.Selection, event -> {
			if (projectPreviewAction != null && isPageComplete()) {
				applyPendingMetadata();
				projectPreviewAction.accept(getFullDslDocument());
			}
		});
		Button copyButton = new Button(documentActions, SWT.PUSH);
		copyButton.setText("Copy complete rule"); //$NON-NLS-1$
		copyButton.addListener(SWT.Selection, event -> {
			applyPendingMetadata();
			Clipboard clipboard = new Clipboard(Display.getCurrent());
			try {
				clipboard.setContents(new Object[] { getFullDslDocument() }, new org.eclipse.swt.dnd.Transfer[] { TextTransfer.getInstance() });
			} finally {
				clipboard.dispose();
			}
		});

		// --- Validation label ---
		validationLabel = new Label(container, SWT.NONE);
		GridData valData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		validationLabel.setLayoutData(valData);

		// Pre-fill from code selection if available
		if (initialSourcePattern != null) {
			sourcePatternText.setText(initialSourcePattern);
			replacementText.setText(initialReplacement);
		}
		if (initialDsl != null) {
			fillFieldsFromInferredRule(initialDsl);
		}

		setControl(container);
		validateDocument();

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
		String code = sourcePatternText.getText();
		String replacement = replacementText.getText();
		if (code.isEmpty()) {
			return;
		}

		generateAiButton.setEnabled(false);
		validationLabel.setText("\u23F3 Generating rule with AI..."); //$NON-NLS-1$

		Job job = new Job("Generating DSL rule with AI") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				AiRuleInferenceEngine engine = EclipseLlmService.getInstance().getEngine();
				String pseudoDiff = buildPseudoDiff(code, replacement);
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

	/** Builds a real before/after diff when the author supplied a replacement. */
	private static String buildPseudoDiff(String before, String after) {
		StringBuilder diff = new StringBuilder("--- a/snippet.java\n+++ b/snippet.java\n"); //$NON-NLS-1$
		String[] beforeLines = before.split("\\R", -1); //$NON-NLS-1$
		String[] afterLines = after.split("\\R", -1); //$NON-NLS-1$
		diff.append("@@ -1,").append(beforeLines.length).append(" +1,") //$NON-NLS-1$ //$NON-NLS-2$
				.append(afterLines.length).append(" @@\n"); //$NON-NLS-1$
		for (String line : beforeLines) {
			diff.append('-').append(line).append('\n');
		}
		for (String line : afterLines) {
			diff.append('+').append(line).append('\n');
		}
		return diff.toString();
	}

	/** Imports the complete result, with no extraction of its first rule/alternative. */
	private void fillFieldsFromInferredRule(String dslRule) {
		customContentEntered = true;
		authoringError = null;
		// Import is the newest authored document, so earlier form values must not
		// silently overwrite its metadata on the next copy or save.
		appliedMetadata.clear();
		appliedMetadata.putAll(metadataSupplier.get());
		updatingDocument = true;
		try {
			ruleDocument.set(new RuleAuthoringDocument(dslRule).content());
		} finally {
			updatingDocument = false;
		}
		lockHelperFields();
		validateDocument();
	}

	private void lockHelperFields() {
		// Rebuilding from three fields would discard metadata or extra alternatives.
		sourcePatternText.setEnabled(false);
		guardText.setEnabled(false);
		replacementText.setEnabled(false);
		generalizeVariablesButton.setEnabled(false);
		if (generateAiButton != null) {
			generateAiButton.setEnabled(false);
		}
	}

	private void chooseVariables() {
		var example = new RuleAuthoringDocument.ChangeExample(sourcePatternText.getText(), replacementText.getText());
		var candidates = ChangeVariableGeneralizer.candidates(example);
		if (candidates.isEmpty()) {
			MessageDialog.openInformation(getShell(), "Generalize variables", //$NON-NLS-1$
					"No shared variable-shaped names were found. Methods, types, members and literals stay fixed."); //$NON-NLS-1$
			return;
		}
		ListSelectionDialog dialog = new ListSelectionDialog(getShell(), candidates,
				ArrayContentProvider.getInstance(), new LabelProvider(),
				"Choose names to replace by $placeholders on both sides. These are syntactic suggestions; " //$NON-NLS-1$
						+ "their bindings and semantic scope have not been proved. Nothing is selected by default."); //$NON-NLS-1$
		dialog.setTitle("Generalize selected variables"); //$NON-NLS-1$
		dialog.setInitialSelections(new Object[0]);
		if (dialog.open() == Window.OK) {
			java.util.Set<String> selected = java.util.Arrays.stream(dialog.getResult())
					.map(String.class::cast).collect(java.util.stream.Collectors.toSet());
			var generalized = ChangeVariableGeneralizer.generalize(example, selected);
			sourcePatternText.setText(generalized.before());
			replacementText.setText(generalized.after());
		}
	}

	private void updateDocumentFromFields() {
		updatingDocument = true;
		try {
			authoringError = null;
			ruleDocument.set(sourcePatternText.getText().isBlank() ? "" //$NON-NLS-1$
					: RuleAuthoringDocument.fromChange(sourcePatternText.getText(), replacementText.getText(),
							guardText.getText()).withMetadata(appliedMetadata).content());
		} catch (IllegalArgumentException exception) {
			authoringError = exception.getMessage();
			ruleDocument.set(""); //$NON-NLS-1$
		} finally {
			updatingDocument = false;
		}
		validateDocument();
	}

	/** A language check is explicitly not presented as a semantic/project proof. */
	private void validateDocument() {
		if (validationLabel == null || validationLabel.isDisposed()) {
			return;
		}
		if (authoringError != null && sourcePatternText.isEnabled()) {
			validationLabel.setText(authoringError);
			setErrorMessage(authoringError);
			setPageComplete(false);
			projectPreviewButton.setEnabled(false);
			return;
		}
		if (requiresReplacement && sourcePatternText.isEnabled()
				&& (sourcePatternText.getText().isBlank() || replacementText.getText().isBlank())) {
			String message = "A reusable change needs nonempty Before and After code; expand the example to include surrounding statements"; //$NON-NLS-1$
			validationLabel.setText(message);
			setErrorMessage(message);
			setPageComplete(false);
			projectPreviewButton.setEnabled(false);
			return;
		}
		String content = getFullDslDocument();
		ValidationResult result = null;
		if (RuleAuthoringDocument.containsJavaTextBlock(content)) {
			result = new ValidationResult(false, RuleAuthoringDocument.textBlockDiagnostic());
		} else if (!content.isBlank()) {
			try {
				var hint = validator.parseHintFile(content);
				result = new PatternIndex(hint.getRules()).size() == hint.getRules().size()
						? new ValidationResult(true, "Language check passed") //$NON-NLS-1$
						: new ValidationResult(false, "A source pattern cannot be parsed by TriggerPattern"); //$NON-NLS-1$
			} catch (HintParseException exception) {
				result = new ValidationResult(false, "Line " + exception.getLineNumber() + ": " + exception.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		boolean valid = result == null || result.valid();
		validationLabel.setText(result == null ? "Draft — choose a template or enter Java code" //$NON-NLS-1$
				: valid ? "Language check passed — behavior and project use have not been verified" //$NON-NLS-1$
						: "Language check failed: " + result.message()); //$NON-NLS-1$
		setErrorMessage(valid ? null : result.message());
		setPageComplete(valid);
		projectPreviewButton.setEnabled(projectPreviewAction != null && result != null && valid);
		projectPreviewButton.setToolTipText(projectPreviewAction == null
				? "Choose a Java project to try the rule on real code" : null); //$NON-NLS-1$
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
	 * Returns {@code true} once the user edits this page, including clearing the document.
	 */
	public boolean hasCustomContent() {
		return customContentEntered;
	}

	/** Returns the very same complete document used for import, editing and preview. */
	public String getFullDslDocument() {
		return ruleDocument.get();
	}

	/** Applies changed form values once; later edits in the full document win. */
	public void applyPendingMetadata() {
		Map<String, String> pending = new LinkedHashMap<>();
		metadataSupplier.get().forEach((key, value) -> {
			if (!Objects.equals(appliedMetadata.get(key), value)) {
				pending.put(key, value);
			}
		});
		if (pending.isEmpty()) {
			return;
		}
		appliedMetadata.putAll(pending);
		boolean wasUpdating = updatingDocument;
		updatingDocument = true;
		try {
			ruleDocument.set(new RuleAuthoringDocument(ruleDocument.get()).withMetadata(pending).content());
		} finally {
			updatingDocument = wasUpdating;
		}
	}

	/** @deprecated Use {@link #getFullDslDocument()} to preserve file-level directives. */
	@Deprecated
	public String getFullRuleBlock() {
		return getFullDslDocument();
	}
}
