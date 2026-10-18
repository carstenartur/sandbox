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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;

/** Verifies that export follows the visible rule selection for the selected commit. */
public class InferredRuleDetailPanelTest {

	private Display display;
	private Shell shell;
	private InferredRuleDetailPanel panel;

	@BeforeEach
	public void setUp() {
		display = Display.getDefault();
		runOnDisplay(() -> {
			shell = new Shell(display);
			panel = new InferredRuleDetailPanel(shell);
		});
	}

	@AfterEach
	public void tearDown() {
		runOnDisplay(() -> {
			if (shell != null && !shell.isDisposed()) {
				shell.dispose();
			}
		});
	}


	private void runOnDisplay(Runnable test) {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		display.syncExec(() -> {
			try {
				test.run();
			} catch (Throwable exception) {
				failure.set(exception);
			}
		});
		if (failure.get() != null) {
			fail(failure.get());
		}
	}

	@Test
	public void selectedDslRulesFollowVisibleCheckboxSelection() {
		String firstRule = "$x + 0\n=> $x\n;;"; //$NON-NLS-1$
		String secondRule = "$x * 1\n=> $x\n;;"; //$NON-NLS-1$
		CommitTableEntry entry = entry("one", firstRule, secondRule); //$NON-NLS-1$
		AtomicReference<List<String>> selected = new AtomicReference<>();

		runOnDisplay(() -> {
			panel.showRules(entry);
			List<Button> checkboxes = checkboxes(panel);
			assertEquals(2, checkboxes.size());
			assertFalse(checkboxes.get(0).getSelection());
			assertFalse(checkboxes.get(1).getSelection());
			checkboxes.get(0).setSelection(true);
			selected.set(panel.getSelectedDslRules());
		});

		assertEquals(List.of(firstRule), selected.get());
	}

	@Test
	public void changingCommitReplacesTheExportSelectionScope() {
		CommitTableEntry first = entry("one", "$x + 0\n=> $x\n;;"); //$NON-NLS-1$ //$NON-NLS-2$
		CommitTableEntry second = entry("two", "$x * 1\n=> $x\n;;"); //$NON-NLS-1$ //$NON-NLS-2$
		AtomicReference<List<String>> selected = new AtomicReference<>();

		runOnDisplay(() -> {
			panel.showRules(first);
			checkboxes(panel).getFirst().setSelection(true);
			panel.showRules(second);
			selected.set(panel.getSelectedDslRules());
		});

		assertEquals(List.of(), selected.get());
	}

	@Test
	public void invalidDslCannotBeExportedEvenWhenEvaluationClaimsValid() {
		runOnDisplay(() -> {
			panel.showRules(entry("invalid", "not a terminated DSL rule")); //$NON-NLS-1$ //$NON-NLS-2$
			Button checkbox = checkboxes(panel).getFirst();
			assertFalse(checkbox.getSelection());
			assertFalse(checkbox.getEnabled());
			checkbox.setSelection(true);
			assertTrue(panel.getSelectedDslRules().isEmpty());
			assertTrue(labels(panel).stream().anyMatch(text -> text.contains("Language check failed"))); //$NON-NLS-1$
		});
	}

	@Test
	public void unknownSemanticActionCannotBeExportedAsALanguageCheckedRule() {
		runOnDisplay(() -> {
			panel.showRules(entry("invalid-action", "$x + 0\n=>! unknownAction()\n;;")); //$NON-NLS-1$ //$NON-NLS-2$
			Button checkbox = checkboxes(panel).getFirst();
			assertFalse(checkbox.getEnabled());
			checkbox.setSelection(true);
			assertTrue(panel.getSelectedDslRules().isEmpty());
			assertTrue(labels(panel).stream().anyMatch(text -> text.contains("Language check failed"))); //$NON-NLS-1$
		});
	}

	@Test
	public void languageCheckNeverClaimsExamplesOrProjectHaveBeenChecked() {
		runOnDisplay(() -> {
			panel.showRules(entry("draft", "$x + 0\n=> $x\n;;")); //$NON-NLS-1$ //$NON-NLS-2$
			List<String> labels = labels(panel);
			assertTrue(labels.stream().anyMatch(text -> text.contains("Draft — language checked"))); //$NON-NLS-1$
			assertTrue(labels.stream().anyMatch(text -> text.contains("Examples: not checked; project: not checked"))); //$NON-NLS-1$
		});
	}

	@Test
	public void unparseableSourcePatternIsNotAnExportableDraft() {
		runOnDisplay(() -> {
			panel.showRules(entry("invalid-source", "???\n=> $x\n;;")); //$NON-NLS-1$ //$NON-NLS-2$
			assertFalse(checkboxes(panel).getFirst().getEnabled());
			checkboxes(panel).getFirst().setSelection(true);
			assertTrue(panel.getSelectedDslRules().isEmpty());
		});
	}

	@Test
	public void metadataWithoutRulesIsNotAnExportableDraft() {
		runOnDisplay(() -> {
			panel.showRules(entry("metadata", "<!id: empty-document>")); //$NON-NLS-1$ //$NON-NLS-2$
			assertFalse(checkboxes(panel).getFirst().getEnabled());
			checkboxes(panel).getFirst().setSelection(true);
			assertTrue(panel.getSelectedDslRules().isEmpty());
		});
	}

	private static List<String> labels(Composite root) {
		List<String> result = new ArrayList<>();
		for (Control child : root.getChildren()) {
			if (child instanceof Label label) {
				result.add(label.getText());
			}
			if (child instanceof Composite composite) {
				result.addAll(labels(composite));
			}
		}
		return result;
	}

	private static CommitTableEntry entry(String id, String... rules) {
		CommitTableEntry entry = new CommitTableEntry(new CommitInfo(
				id,
				id,
				"Commit " + id, //$NON-NLS-1$
				"Sandbox", //$NON-NLS-1$
				LocalDateTime.of(2026, 1, 1, 0, 0),
				1));
		entry.setEvaluations(java.util.Arrays.stream(rules)
				.map(rule -> evaluation(id, rule))
				.toList());
		entry.setStatus(AnalysisStatus.DONE);
		return entry;
	}

	private static CommitEvaluation evaluation(String id, String rule) {
		return new CommitEvaluation(
				id,
				"Commit " + id, //$NON-NLS-1$
				"file:///repository", //$NON-NLS-1$
				Instant.EPOCH,
				null,
				true,
				null,
				false,
				null,
				0,
				0,
				0,
				TrafficLight.GREEN,
				"Test", //$NON-NLS-1$
				false,
				null,
				true,
				rule,
				null,
				null,
				null,
				"Synthetic evaluation", //$NON-NLS-1$
				"VALID", //$NON-NLS-1$
				null,
				null,
				null);
	}

	private static List<Button> checkboxes(Composite root) {
		List<Button> result = new ArrayList<>();
		for (Control child : root.getChildren()) {
			if (child instanceof Button button) {
				result.add(button);
			}
			if (child instanceof Composite composite) {
				result.addAll(checkboxes(composite));
			}
		}
		return result;
	}
}
