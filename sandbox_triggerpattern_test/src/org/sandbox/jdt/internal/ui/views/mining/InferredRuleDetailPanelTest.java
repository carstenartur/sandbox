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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
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
		display.syncExec(() -> {
			shell = new Shell(display);
			panel = new InferredRuleDetailPanel(shell);
		});
	}

	@AfterEach
	public void tearDown() {
		display.syncExec(() -> {
			if (shell != null && !shell.isDisposed()) {
				shell.dispose();
			}
		});
	}

	@Test
	public void selectedDslRulesFollowVisibleCheckboxSelection() {
		CommitTableEntry entry = entry("one", "rule-one", "rule-two"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		AtomicReference<List<String>> selected = new AtomicReference<>();

		display.syncExec(() -> {
			panel.showRules(entry);
			List<Button> checkboxes = checkboxes(panel);
			assertEquals(2, checkboxes.size());
			checkboxes.get(1).setSelection(false);
			selected.set(panel.getSelectedDslRules());
		});

		assertEquals(List.of("rule-one"), selected.get()); //$NON-NLS-1$
	}

	@Test
	public void changingCommitReplacesTheExportSelectionScope() {
		CommitTableEntry first = entry("one", "rule-one"); //$NON-NLS-1$ //$NON-NLS-2$
		CommitTableEntry second = entry("two", "rule-two"); //$NON-NLS-1$ //$NON-NLS-2$
		AtomicReference<List<String>> selected = new AtomicReference<>();

		display.syncExec(() -> {
			panel.showRules(first);
			panel.showRules(second);
			selected.set(panel.getSelectedDslRules());
		});

		assertEquals(List.of("rule-two"), selected.get()); //$NON-NLS-1$
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
