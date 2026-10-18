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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
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
	void selectionAndReplacementKeepAllLinesInTheEditableFieldsAndDraft() {
		runOnDisplay(() -> {
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
		runOnDisplay(() -> {
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
		runOnDisplay(() -> {
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
	void clearingTheAuthoritativeDocumentDoesNotRestoreTheSelectedTemplate() {
		runOnDisplay(() -> {
			var wizard = new NewSandboxHintFileWizard();
			try {
				wizard.addPages();
				for (var page : wizard.getPages()) {
					page.createControl(shell);
				}
				var page = (NewRuleWizardPage) wizard.getPages()[1];
				page.setVisible(true);
				assertFalse(page.hasCustomContent());
				assertFalse(wizard.getFullDslDocument().isBlank());
				StyledText fullDocument = descendants((Composite) page.getControl(), StyledText.class).getFirst();
				fullDocument.setText("");
				assertEquals("", page.getFullDslDocument());
				assertEquals("", wizard.getFullDslDocument());
				assertTrue(page.hasCustomContent());
				page.setVisible(false);
				page.setVisible(true);
				assertEquals("", wizard.getFullDslDocument());
			} finally {
				wizard.dispose();
			}
		});
	}

	@Test
	void unparseableSourcePatternCannotBeFinishedOrPreviewed() {
		runOnDisplay(() -> {
			var page = new NewRuleWizardPage();
			page.setInitialDsl("???\n=> $x\n;;\n");
			page.setProjectPreviewAction(ignored -> {});
			page.createControl(shell);
			assertFalse(page.isPageComplete());
			Button preview = descendants(shell, Button.class).stream()
					.filter(button -> button.getText().equals("Try on my code...")).findFirst().orElseThrow();
			assertFalse(preview.isEnabled());
		});
	}

	@Test
	void bundledTemplatesPassTheSourcePatternLanguageCheck() {
		runOnDisplay(() -> {
			for (var template : SandboxHintTemplates.values()) {
				var page = new NewRuleWizardPage();
				page.setInitialDsl(template.getRuleContent());
				page.createControl(shell);
				assertTrue(page.isPageComplete(), () -> template + ": " + page.getErrorMessage());
			}
		});
	}

	@Test
	void editingTheFullDocumentWinsOverPreviouslyAppliedMetadataFields() {
		runOnDisplay(() -> {
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
		runOnDisplay(() -> {
			var page = new NewRuleWizardPage();
			page.setInitialDsl(dsl);
			page.setProjectPreviewAction(ignored -> {});
			page.createControl(shell);
			assertEquals(dsl, page.getFullDslDocument());
			assertFalse(page.isPageComplete());
			assertTrue(page.getErrorMessage().contains("text blocks"));
		});
	}

	@Test
	void previewAvailabilityTracksTheCurrentContainerWhenTheInitialSelectionIsEmpty() throws Exception {
		IProject project = ResourcesPlugin.getWorkspace().getRoot()
				.getProject("triggerpattern-preview-" + Long.toUnsignedString(System.nanoTime())); //$NON-NLS-1$
		AtomicReference<NewSandboxHintFileWizard> currentWizard = new AtomicReference<>();
		try {
			project.create(null);
			project.open(null);
			var description = project.getDescription();
			description.setNatureIds(new String[] { JavaCore.NATURE_ID });
			project.setDescription(description, null);
			var folder = project.getFolder("rules"); //$NON-NLS-1$
			folder.create(false, true, null);
			assertTrue(JavaCore.create(project).exists());

			runOnDisplay(() -> {
				var wizard = new NewSandboxHintFileWizard();
				currentWizard.set(wizard);
				wizard.init(PlatformUI.getWorkbench(), StructuredSelection.EMPTY);
				wizard.addPages();
				for (var page : wizard.getPages()) {
					page.createControl(shell);
				}
				var filePage = (NewSandboxHintFileWizardPage) wizard.getPages()[0];
				var rulePage = (NewRuleWizardPage) wizard.getPages()[1];
				rulePage.setVisible(true);
				Button preview = descendants((Composite) rulePage.getControl(), Button.class).stream()
						.filter(button -> button.getText().equals("Try on my code...")).findFirst().orElseThrow(); //$NON-NLS-1$
				assertFalse(preview.isEnabled());
				Text container = descendants((Composite) filePage.getControl(), Text.class).getFirst();
				container.setText(folder.getFullPath().toString());
				assertTrue(filePage.isPageComplete());
				assertTrue(preview.isEnabled(), "Choosing the current Java container must enable project preview"); //$NON-NLS-1$
				assertNull(preview.getToolTipText());
				container.setText(""); //$NON-NLS-1$
				assertFalse(preview.isEnabled(), "Clearing the destination must remove its preview project"); //$NON-NLS-1$
				container.setText("/missing-preview-project"); //$NON-NLS-1$
				assertFalse(preview.isEnabled());
				container.setText(folder.getFullPath().toString());
				assertTrue(preview.isEnabled());
				rulePage.setVisible(false);
			});

			project.close(null);
			runOnDisplay(() -> {
				var wizard = currentWizard.get();
				var filePage = (NewSandboxHintFileWizardPage) wizard.getPages()[0];
				var rulePage = (NewRuleWizardPage) wizard.getPages()[1];
				assertEquals(rulePage, wizard.getNextPage(filePage));
				rulePage.setVisible(true);
				Button preview = descendants((Composite) rulePage.getControl(), Button.class).stream()
						.filter(button -> button.getText().equals("Try on my code...")).findFirst().orElseThrow(); //$NON-NLS-1$
				assertFalse(preview.isEnabled(), "Navigation must refresh a project that became unavailable"); //$NON-NLS-1$
			});
		} finally {
			runOnDisplay(() -> {
				if (currentWizard.get() != null) {
					currentWizard.get().dispose();
				}
			});
			if (project.exists()) {
				project.delete(true, true, null);
			}
		}
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
