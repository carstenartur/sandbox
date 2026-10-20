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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
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
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument;

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
	void blankSavedCompilationUnitStillOpensAnEditableRuleDraft() {
		for (String blank : List.of("", " \t")) {
			assertBlankSeedOpensEditableDraft(blank, "class Example { int value = 1; }");
		}
	}

	@Test
	void deletingTheCompleteCompilationUnitStillOpensAnEditableRuleDraft() {
		for (String blank : List.of("", " \t")) {
			assertBlankSeedOpensEditableDraft("class Example { int value = 1; }", blank);
		}
	}

	private void assertBlankSeedOpensEditableDraft(String before, String after) {
		runOnDisplay(() -> {
			var wizard = new NewSandboxHintFileWizard();
			try {
				var change = RuleAuthoringDocument.changedLines(before, after).orElseThrow();
				wizard.setInitialChange(change.before(), change.after());
				wizard.setInitialExample("src/Example.java", before, after);
				wizard.addPages();
				var page = (NewRuleWizardPage) wizard.getPages()[1];
				page.createControl(shell);
				assertNull(page.getExamples());
				List<Text> fields = descendants((Composite) page.getControl(), Text.class);
				assertEquals(change.before(), fields.get(0).getText());
				assertEquals(change.after(), fields.get(2).getText());
				assertTrue(fields.get(0).getEditable());
				assertTrue(fields.get(2).getEditable());
				assertFalse(page.isPageComplete());
				fields.get(0).setText("old()");
				fields.get(2).setText("modern()");
				assertTrue(page.isPageComplete(), page.getErrorMessage());
				assertTrue(page.getFullDslDocument().contains("old()\n=> modern()\n;;"));
			} finally {
				wizard.dispose();
			}
		});
	}

	@Test
	void completeNonblankInitialEvidenceIsRetainedExactly() {
		runOnDisplay(() -> {
			var wizard = new NewSandboxHintFileWizard();
			try {
				String before = "class Example { int value = 1; }\n";
				String after = "class Example { int value = 2; }\n";
				wizard.setInitialExample("src/Example.java", before, after);
				wizard.addPages();
				var page = (NewRuleWizardPage) wizard.getPages()[1];
				assertEquals(List.of(new RuleExampleSuite.Example("Saved edit", "src/Example.java", before, after, true)),
						page.getExamples().examples());
			} finally {
				wizard.dispose();
			}
		});
	}

	@Test
	void acceptedExampleProposalPreservesMetadataGuardsAndCompleteSourceEvidence() {
		runOnDisplay(() -> {
			var page = new NewRuleWizardPage();
			page.setInitialChange("return n + 0;", "return n;");
			page.setMetadataSupplier(() -> java.util.Map.of("id", "team.exact", "tags", "team, tested"));
			var suite = new RuleExampleSuite(RuleExampleSuite.CURRENT_VERSION, List.of(
					new RuleExampleSuite.Example("Saved edit", "src/p/Example.java",
							"package p; class Example { int f(int n) { return n + 0; } }\n",
							"package p; class Example { int f(int n) { return n; } }\n", true)));
			page.setExamples(suite);
			page.createControl(shell);
			descendants(shell, Text.class).get(1).setText("sourceVersionGE(17)");
			page.applyPendingMetadata();
			assertTrue(page.canRefineExamples());
			page.applyExampleProposal(new RuleAuthoringDocument.ChangeExample("return $n + 0;", "return $n;"));
			assertEquals(RuleAuthoringDocument.fromChange("return $n + 0;", "return $n;", "sourceVersionGE(17)")
					.withMetadata(java.util.Map.of("id", "team.exact", "tags", "team, tested")).content(),
					page.getFullDslDocument());
			assertEquals(suite, page.getExamples());
			page.setVisible(false);
			page.setVisible(true);
			assertEquals(suite, page.getExamples());
		});
	}

	@Test
	void importedAndEditedCompleteDocumentsCannotBeRebuiltByExampleProposals() {
		runOnDisplay(() -> {
			var page = new NewRuleWizardPage();
			page.setInitialChange("return n + 0;", "return n;");
			page.createControl(shell);
			assertTrue(page.canRefineExamples());
			String complete = "<!id: imported>\n$x.old()\n=> $x.modern()\n=> $x.fallback() :: otherwise\n;;\n";
			descendants(shell, StyledText.class).getFirst().setText(complete);
			assertFalse(page.canRefineExamples());
			assertThrows(IllegalStateException.class, () -> page.applyExampleProposal(
					new RuleAuthoringDocument.ChangeExample("return $n + 0;", "return $n;")));
			assertEquals(complete, page.getFullDslDocument());
			var imported = new NewRuleWizardPage();
			imported.setInitialDsl(complete);
			imported.createControl(shell);
			assertFalse(imported.canRefineExamples());
			assertEquals(complete, imported.getFullDslDocument());
		});
	}

	@Test
	void examplesRequireAnActualProjectForParsingAndDialogReplay() {
		assertThrows(IllegalArgumentException.class, () -> RuleExamplesDialog.parseSource(null,
				java.util.Map.of(), "Example.java", "class Example {}", new NullProgressMonitor()));
		runOnDisplay(() -> {
			var dialog = new RuleExamplesDialog(shell, "$x.old()\n=> $x.modern()\n;;\n", null, null, null, null);
			try {
				dialog.create();
				for (String label : List.of("Verify", "Refine from examples", "Use proposed rule")) {
					assertFalse(descendants(dialog.getShell(), Button.class).stream()
							.filter(button -> button.getText().equals(label)).findFirst().orElseThrow().isEnabled());
				}
				assertNull(dialog.getAcceptedProposal());
			} finally {
				dialog.close();
			}
		});
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
			String actualBefore = fields.get(0).getText();
			String actualAfter = fields.get(2).getText();
			assertEquals(before, actualBefore.replace("\r\n", "\n").replace('\r', '\n'));
			assertEquals(after, actualAfter.replace("\r\n", "\n").replace('\r', '\n'));
			assertTrue((fields.get(0).getStyle() & SWT.MULTI) != 0);
			assertTrue((fields.get(2).getStyle() & SWT.MULTI) != 0);
			assertEquals("<!binding-policy: required>\n\n" + actualBefore + "\n=> " + actualAfter + "\n;;\n",
					page.getFullDslDocument());
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
