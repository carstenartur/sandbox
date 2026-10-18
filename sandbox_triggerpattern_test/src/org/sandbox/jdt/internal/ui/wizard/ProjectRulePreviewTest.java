/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Exercises the installed project-trial entry point through a real modal dialog. */
class ProjectRulePreviewTest {

	@RegisterExtension
	EclipseJava22 context= new EclipseJava22();

	@Test
	void projectTrialOpensTheRefactoringDialogAndCancelPreservesSource() throws Exception {
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Sample.java",
						"package trial; public class Sample { int value() { return 1 + 2; } }", false, null);
		String before= unit.getSource();
		String rule= "<!id: trial>\n<!binding-policy: required>\n1 + 2\n=> 3\n;;\n";
		AtomicReference<String> explanation= new AtomicReference<>();
		AtomicReference<String> errorDialog= new AtomicReference<>();
		Display display= Display.getDefault();
		display.syncExec(() -> {
			Shell parent= new Shell(display);
			Listener capture= event -> {
				if (event.widget instanceof Shell candidate
						&& candidate.getData() instanceof IWizardContainer container
						&& container.getCurrentPage() instanceof UserInputWizardPage page
						&& page.getControl() != null
						&& page.getWizard() instanceof RefactoringWizard wizard
						&& wizard.getRefactoring() instanceof ProjectRuleRefactoring refactoring) {
					display.asyncExec(() -> {
						try {
							explanation.set(refactoring.getExplanation());
						} finally {
							candidate.close();
						}
					});
				} else if (event.widget instanceof Shell candidate && candidate.getText().contains("TriggerPattern")) {
					display.asyncExec(() -> {
						errorDialog.set(candidate.getText());
						candidate.close();
					});
				}
			};
			display.addFilter(SWT.Show, capture);
			try {
				ProjectRulePreview.open(parent, context.getJavaProject(), rule);
			} finally {
				display.removeFilter(SWT.Show, capture);
				parent.dispose();
			}
		});
		assertNotNull(explanation.get(), "Expected an initialized RefactoringWizard; saw dialog: " + errorDialog.get());
		assertTrue(explanation.get().contains(unit.getPath().toString()), explanation.get());
		assertTrue(explanation.get().contains("Source requirement: MATCH"), explanation.get());
		assertEquals(before, unit.getSource());
	}
}
