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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class NewRuleWizardPageTest {

	@RegisterExtension
	EclipseJava22 context = new EclipseJava22();

	private Display display;
	private Shell shell;

	@BeforeEach
	void setUp() {
		display = Display.getDefault();
		display.syncExec(() -> shell = new Shell(display));
	}

	@AfterEach
	void tearDown() {
		display.syncExec(() -> {
			PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeAllEditors(false);
			shell.dispose();
		});
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
			Button preview = previewButton(page);
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
			Button preview = previewButton(page);
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

	@Test
	void clearingTheCompleteDocumentSavesTheVisibleEmptyFile() {
		runOnDisplay(() -> {
			var wizard = new NewSandboxHintFileWizard();
			wizard.init(PlatformUI.getWorkbench(), new StructuredSelection(context.getJavaProject().getProject()));
			var dialog = new WizardDialog(shell, wizard);
			dialog.create();
			try {
				var filePage = (NewSandboxHintFileWizardPage) wizard.getPages()[0];
				var rulePage = (NewRuleWizardPage) wizard.getPages()[1];
				dialog.showPage(wizard.getNextPage(filePage));
				StyledText document = descendants((Composite) rulePage.getControl(), StyledText.class).getFirst();
				assertFalse(document.getText().isBlank());
				document.setText("");
				dialog.showPage(filePage);
				dialog.showPage(wizard.getNextPage(filePage));
				assertEquals("", document.getText());
				assertTrue(wizard.canFinish());
				assertTrue(wizard.performFinish());
				IFile file = context.getJavaProject().getProject().getFile(filePage.getFileName());
				try (var contents = file.getContents()) {
					assertEquals(0, contents.readAllBytes().length);
				}
				assertEquals("", wizard.getFullDslDocument());
			} finally {
				dialog.close();
			}
		});
	}

	@Test
	void choosingATargetProjectEnablesPreviewWithoutAnInitialSelection() {
		runOnDisplay(() -> {
			var wizard = new NewSandboxHintFileWizard();
			wizard.init(PlatformUI.getWorkbench(), StructuredSelection.EMPTY);
			var dialog = new WizardDialog(shell, wizard);
			dialog.create();
			try {
				var filePage = (NewSandboxHintFileWizardPage) wizard.getPages()[0];
				descendants((Composite) filePage.getControl(), Text.class).getFirst()
						.setText(context.getJavaProject().getPath().toString());
				var rulePage = (NewRuleWizardPage) wizard.getPages()[1];
				dialog.showPage(wizard.getNextPage(filePage));
				assertTrue(previewButton(rulePage).isEnabled());
			} finally {
				dialog.close();
			}
		});
	}

	@Test
	void projectPreviewUsesTheCurrentTargetContainer() throws Exception {
		IJavaProject target = AbstractEclipseJava.createJavaProject("WizardTarget_" + System.nanoTime(), "bin");
		try {
			target.setRawClasspath(Arrays.stream(context.getJavaProject().getRawClasspath())
					.filter(entry -> entry.getEntryKind() != IClasspathEntry.CPE_SOURCE).toArray(IClasspathEntry[]::new), null);
			target.setOptions(context.getJavaProject().getOptions(false));
			var targetSource = AbstractEclipseJava.addSourceContainer(target, "src");
			String source = "package trial; class Sample { int value() { return 1 + 2; } }";
			context.getSourceFolder().createPackageFragment("trial", false, null)
					.createCompilationUnit("Sample.java", source, false, null);
			var targetUnit = targetSource.createPackageFragment("trial", false, null)
					.createCompilationUnit("Sample.java", source, false, null);
			runOnDisplay(() -> {
				var wizard = new NewSandboxHintFileWizard();
				wizard.init(PlatformUI.getWorkbench(), new StructuredSelection(context.getJavaProject().getProject()));
				var dialog = new WizardDialog(shell, wizard);
				dialog.create();
				try {
					var filePage = (NewSandboxHintFileWizardPage) wizard.getPages()[0];
					descendants((Composite) filePage.getControl(), Text.class).getFirst().setText(target.getPath().toString());
					var rulePage = (NewRuleWizardPage) wizard.getPages()[1];
					dialog.showPage(wizard.getNextPage(filePage));
					descendants((Composite) rulePage.getControl(), StyledText.class).getFirst()
							.setText("<!id: target-project>\n<!binding-policy: required>\n1 + 2\n=> 3\n;;\n");
					String preview = openPreviewAndCancel(previewButton(rulePage));
					assertTrue(preview.contains(targetUnit.getPath().toString()), preview);
					assertEquals(source, targetUnit.getSource());
				} finally {
					dialog.close();
				}
			});
		} finally {
			target.getProject().delete(true, true, null);
		}
	}

	private String openPreviewAndCancel(Button button) {
		assertTrue(button.isEnabled());
		AtomicReference<String> result = new AtomicReference<>();
		Listener capture = event -> {
			if (event.widget instanceof Shell candidate && candidate.getData() instanceof IWizardContainer container
					&& container.getCurrentPage() != null
					&& container.getCurrentPage().getWizard() instanceof RefactoringWizard preview
					&& preview.getRefactoring() instanceof ProjectRuleRefactoring refactoring) {
				display.asyncExec(() -> {
					try {
						result.set(refactoring.getExplanation());
					} finally {
						candidate.close();
					}
				});
			} else if (event.widget instanceof Shell candidate && candidate.getText().contains("TriggerPattern")) {
				display.asyncExec(() -> {
					result.set(String.join("\n", descendants(candidate, Label.class).stream().map(Label::getText).toList()));
					candidate.close();
				});
			}
		};
		display.addFilter(SWT.Show, capture);
		try {
			button.notifyListeners(SWT.Selection, new Event());
		} finally {
			display.removeFilter(SWT.Show, capture);
		}
		return result.get();
	}

	private static Button previewButton(NewRuleWizardPage page) {
		List<Button> buttons = descendants((Composite) page.getControl(), Button.class).stream()
				.filter(button -> button.getText().equals("Try on my code...") || button.getText().equals("Try on project...")).toList();
		assertEquals(1, buttons.size());
		return buttons.getFirst();
	}

	private void runOnDisplay(UiOperation operation) {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		display.syncExec(() -> {
			try {
				operation.run();
			} catch (Throwable exception) {
				failure.set(exception);
			}
		});
		if (failure.get() instanceof AssertionError assertion) {
			throw assertion;
		}
		if (failure.get() != null) {
			throw new AssertionError(failure.get());
		}
	}

	@FunctionalInterface
	private interface UiOperation {
		void run() throws Exception;
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
