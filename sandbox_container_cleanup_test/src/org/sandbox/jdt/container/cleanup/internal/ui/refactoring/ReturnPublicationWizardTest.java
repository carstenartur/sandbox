/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.container.api.ReturnPublicationPolicy;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ReturnPublicationWizardTest {
	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void wizardRequiresAnExplicitPolicyAndDisablesCopyBelowJava10() throws Throwable {
		var fragment= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		String example= """
				package test;
				import java.util.Arrays;
import java.util.List;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
				final class Example {
				    static String[] rows(String entry) {
				        String[] parts = new String[0];
				        parts = Arrays.copyOf(parts, parts.length + 1);
				        parts[parts.length - 1] = entry;
				        return parts;
				    }
				    int count(String entry) { String[] result=rows(entry); return result.length; }
				}
				""";
		ICompilationUnit producer= fragment.createCompilationUnit("Example.java", example, true, null); //$NON-NLS-1$
		context.getJavaProject().setOption(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
		context.getJavaProject().setOption(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
		context.getJavaProject().setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
		var command= PlatformUI.getWorkbench().getService(org.eclipse.ui.commands.ICommandService.class)
				.getCommand("org.sandbox.jdt.container.selectReturnPublication"); //$NON-NLS-1$
		assertTrue(command.isDefined());
		var failed= new java.util.concurrent.atomic.AtomicReference<Throwable>();
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> {
			try {
				var manager= org.eclipse.core.runtime.jobs.Job.getJobManager();
				var workspace= org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot();
				manager.beginRule(workspace, null);
				var shell= new Shell(PlatformUI.getWorkbench().getDisplay());
				var wizard= ReturnPublicationAction.createWizard(new ReturnPublicationRefactoring(producer));
				var dialog= new org.eclipse.jface.wizard.WizardDialog(shell, wizard);
				try {
					dialog.create();
					IWizardPage page= wizard.getPage("returnPublication"); //$NON-NLS-1$
					assertNotNull(page);
					assertNotNull(page.getControl());
					assertFalse(page.isPageComplete());
					var buttons= Arrays.stream(((Composite) page.getControl()).getChildren()).filter(Button.class::isInstance)
							.map(Button.class::cast).toList();
					assertEquals(2, buttons.size());
					assertFalse(buttons.get(0).getSelection());
					assertFalse(buttons.get(1).isEnabled());
					buttons.get(0).setSelection(true);
					buttons.get(0).notifyListeners(SWT.Selection, new Event());
					assertTrue(page.isPageComplete());
				} finally {
					dialog.close();
					shell.dispose();
					manager.endRule(workspace);
				}
			} catch (Throwable failure) {
				failed.set(failure);
			}
		});
		if (failed.get() != null) {
			throw failed.get();
		}
	}
	@Test
	void explicitPublicationChoicePreviewsAndAppliesEveryFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path output) throws Exception {
		String oldProducer= """
				package test;
				import java.util.Arrays;
				final class Rows {
				    static String[] rows(String value) {
				        String[] rows = new String[0];
				        rows = Arrays.copyOf(rows, rows.length + 1);
				        rows[rows.length - 1] = value;
				        return rows;
				    }
				}
				""";
		String oldConsumer= """
				package test;
				class Display {
				    int count(String x) { String[] items = Rows.rows(x); return items.length; }
				}
				""";
		var rows= createUnit("Rows.java", oldProducer); //$NON-NLS-1$
		var display= createUnit("Display.java", oldConsumer); //$NON-NLS-1$
		var monitor= new NullProgressMonitor();
		for (var policy : ReturnPublicationPolicy.values()) {
			var refactoring= new ReturnPublicationRefactoring(rows);
			assertFalse(refactoring.checkInitialConditions(monitor).hasError());
			assertTrue(refactoring.checkFinalConditions(monitor).hasFatalError());
			refactoring.select(policy);
			assertFalse(refactoring.checkFinalConditions(monitor).hasError());
			var change= (org.eclipse.ltk.core.refactoring.CompositeChange) refactoring.createChange(monitor);
			assertEquals(2, change.getChildren().length);
			change.initializeValidationData(monitor);
			change.getChildren()[0].setEnabled(false);
			assertTrue(change.isValid(monitor).hasFatalError());
			change.getChildren()[0].setEnabled(true);
			var undo= change.perform(monitor);
			assertNotNull(undo);
			assertTrue(display.getSource().contains("items.size()")); //$NON-NLS-1$
			var f1= java.nio.file.Files.writeString(output.resolve("Rows.java"), rows.getSource()); //$NON-NLS-1$
			var f2= java.nio.file.Files.writeString(output.resolve("Display.java"), display.getSource()); //$NON-NLS-1$
			assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", output.toString(), f1.toString(), f2.toString())); //$NON-NLS-1$
			try (var loader= new java.net.URLClassLoader(new java.net.URL[] {output.toUri().toURL()}, null)) {
				var method= loader.loadClass("test.Rows").getDeclaredMethod("rows", String.class); //$NON-NLS-1$ //$NON-NLS-2$
				method.setAccessible(true);
				List<?> published= (List<?>) method.invoke(null, "entry"); //$NON-NLS-1$
				assertEquals(List.of("entry"), published); //$NON-NLS-1$
				assertThrows(UnsupportedOperationException.class, published::clear);
				if (policy == ReturnPublicationPolicy.PRESERVE_NULLS) {
					assertEquals(Arrays.asList((String) null), method.invoke(null, (Object) null));
				} else {
					var failure= assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(null, (Object) null));
					assertEquals(NullPointerException.class, failure.getCause().getClass());
				}
			}
			undo.perform(monitor);
			assertEquals(oldProducer, rows.getSource());
			assertEquals(oldConsumer, display.getSource());
		}
		var stale= new ReturnPublicationRefactoring(rows);
		assertFalse(stale.checkInitialConditions(monitor).hasError());
		stale.select(ReturnPublicationPolicy.REJECT_NULLS);
		var proposed= stale.createChange(monitor);
		createUnit("Another.java", oldConsumer.replace("class Display", "class Another")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertThrows(CoreException.class, () -> proposed.perform(monitor));
		assertEquals(oldProducer, rows.getSource());
		assertEquals(oldConsumer, display.getSource());
	}

	private ICompilationUnit createUnit(String name, String source) throws CoreException {
		var fragment= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit(name, source, true, null);
	}
}
