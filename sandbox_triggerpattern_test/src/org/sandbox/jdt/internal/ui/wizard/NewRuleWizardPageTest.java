/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NewRuleWizardPageTest {

	private Display display;
	private Shell shell;

	@BeforeEach
	void setUp() {
		display = Display.getDefault();
		display.syncExec(() -> shell = new Shell(display));
	}

	@AfterEach
	void tearDown() {
		display.syncExec(() -> shell.dispose());
	}

	@Test
	void selectionAndReplacementKeepAllLinesInTheEditableFieldsAndDraft() {
		display.syncExec(() -> {
			var page = new NewRuleWizardPage();
			String before = "first();\nsecond();";
			String after = "modernFirst();\nmodernSecond();";
			page.setInitialChange(before, after);
			page.createControl(shell);
			List<Text> fields = descendants(shell, Text.class);
			assertEquals(before, fields.get(0).getText());
			assertEquals(after, fields.get(2).getText());
			assertTrue((fields.get(0).getStyle() & SWT.MULTI) != 0);
			assertTrue((fields.get(2).getStyle() & SWT.MULTI) != 0);
			assertTrue(page.getFullDslDocument().contains("second();"));
			assertTrue(page.getFullDslDocument().contains("modernSecond();"));
			assertTrue(page.isPageComplete());
		});
	}

	@Test
	void completeDslSurvivesImportAndIsTheDocumentSentToProjectPreview() {
		String dsl = """
				<!id: team.encoding>
				<!binding-policy: required>
				<!tags: encoding, team>
				@id: utf8
				$x.getBytes("UTF-8") :: sourceVersionGE(7)
				=> $x.getBytes(java.nio.charset.StandardCharsets.UTF_8) :: sourceVersionGE(11)
				=> $x.getBytes(java.nio.charset.Charset.forName("UTF-8")) :: otherwise
				;;
				""";
		AtomicReference<String> actualPreview = new AtomicReference<>();
		display.syncExec(() -> {
			var page = new NewRuleWizardPage();
			page.setInitialDsl(dsl);
			page.setProjectPreviewAction(actualPreview::set);
			page.createControl(shell);
			assertEquals(dsl, page.getFullDslDocument());
			assertTrue(page.hasCustomContent());
			assertTrue(page.isPageComplete());
			Button preview = descendants(shell, Button.class).stream()
					.filter(button -> button.getText().equals("Try on my code...")).findFirst().orElseThrow();
			assertTrue(preview.isEnabled());
			preview.notifyListeners(SWT.Selection, new Event());
		});
		assertEquals(dsl, actualPreview.get());
	}

	@Test
	void malformedImportedDslCannotBeFinishedOrPreviewed() {
		display.syncExec(() -> {
			var page = new NewRuleWizardPage();
			page.setInitialDsl("$x.old()\n=> $x.modern()\n");
			page.setProjectPreviewAction(ignored -> {});
			page.createControl(shell);
			assertFalse(page.isPageComplete());
			Button preview = descendants(shell, Button.class).stream()
					.filter(button -> button.getText().equals("Try on my code...")).findFirst().orElseThrow();
			assertFalse(preview.isEnabled());
		});
	}

	@Test
	void editingTheFullDocumentWinsOverPreviouslyAppliedMetadataFields() {
		display.syncExec(() -> {
			var metadata = new HashMap<String, String>();
			var page = new NewRuleWizardPage();
			page.setMetadataSupplier(() -> metadata);
			page.setInitialDsl("<!id: original>\n$x.old()\n=> $x.modern()\n;;\n");
			page.createControl(shell);
			metadata.put("id", "from-form");
			page.applyPendingMetadata();
			assertTrue(page.getFullDslDocument().contains("<!id: from-form>"));
			StyledText fullDocument = descendants(shell, StyledText.class).getFirst();
			fullDocument.setText(page.getFullDslDocument().replace("from-form", "from-editor"));
			page.applyPendingMetadata();
			assertTrue(page.getFullDslDocument().contains("<!id: from-editor>"));
			assertFalse(page.getFullDslDocument().contains("from-form"));
		});
	}

	@Test
	void importedTextBlockIsPreservedButCannotBePreviewedOrSaved() {
		String dsl = "return \"\"\"\n  keep exact indentation\n    keep more indentation\n  \"\"\";\n=> return modern();\n;;\n";
		display.syncExec(() -> {
			var page = new NewRuleWizardPage();
			page.setInitialDsl(dsl);
			page.setProjectPreviewAction(ignored -> {});
			page.createControl(shell);
			assertEquals(dsl, page.getFullDslDocument());
			assertFalse(page.isPageComplete());
			assertTrue(page.getErrorMessage().contains("text blocks"));
		});
	}

	private static <T extends Control> List<T> descendants(Composite parent, Class<T> type) {
		List<T> result = new ArrayList<>();
		for (Control control : parent.getChildren()) {
			if (type.isInstance(control)) {
				result.add(type.cast(control));
			}
			if (control instanceof Composite composite) {
				result.addAll(descendants(composite, type));
			}
		}
		return result;
	}
}
