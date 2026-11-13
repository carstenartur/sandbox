/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.ui.PlatformUI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Native registration/layout smoke test for the read-only Container Analysis view. */
public class ContainerAnalysisViewSWTBotTest {

	private static final String VIEW_TITLE= "Container Analysis"; //$NON-NLS-1$
	private static SWTWorkbenchBot bot;

	@BeforeAll
	public static void setUp() {
		bot= new SWTWorkbenchBot();
		try {
			bot.viewByTitle("Welcome").close(); //$NON-NLS-1$
		} catch (WidgetNotFoundException e) {
			// Welcome is optional in the test workbench.
		}
	}

	@AfterAll
	public static void tearDown() {
		try {
			bot.viewByTitle(VIEW_TITLE).close();
		} catch (WidgetNotFoundException e) {
			// The open-view assertion reports a missing contribution more clearly.
		}
	}

	@Test
	public void registeredViewOpensWithEvidenceTableAndExplanationArea() {
		bot.menu("Window").menu("Show View").menu("Other...").click(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		bot.tree().expandNode("Java").select(VIEW_TITLE); //$NON-NLS-1$
		bot.button("Open").click(); //$NON-NLS-1$

		SWTBotView view= bot.viewByTitle(VIEW_TITLE);
		assertNotNull(view);
		view.show();
		SWTBotTable table= view.bot().table();
		assertNotNull(table);
		assertEquals(6, table.columnCount(),
				"Container evidence table must expose the candidate/contract/status/evidence columns"); //$NON-NLS-1$
		assertNotNull(view.bot().text(),
				"Container Analysis must expose a read-only semantic explanation area"); //$NON-NLS-1$
	}

	@Test
	public void synchronizedListEvidenceNavigatesToTheIterationExpression() throws Exception {
		var project= AbstractEclipseJava.createJavaProject("ContainerMonitorNavigation", "bin"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			AbstractEclipseJava.addToClasspath(project,
					JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER"))); //$NON-NLS-1$
			var sources= AbstractEclipseJava.addSourceContainer(project, "src"); //$NON-NLS-1$
			var sourcePackage= sources.createPackageFragment("demo", false, null); //$NON-NLS-1$
			String source= """
					package demo;
					import java.util.ArrayList;
					import java.util.Collections;
					import java.util.List;
					class MonitorSample {
						private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
						void inspect() {
							synchronized (entries) {
								for (String entry : this.entries) { }
							}
						}
					}
					""";
			ICompilationUnit unit= sourcePackage.createCompilationUnit("MonitorSample.java", source, false, null); //$NON-NLS-1$
			Display.getDefault().syncCall(() -> {
				JavaUI.openInEditor(unit);
				return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
						.showView(ContainerAnalysisView.VIEW_ID);
			});
			SWTBotView view= bot.viewById(ContainerAnalysisView.VIEW_ID);
			view.show();
			SWTBotTable table= view.bot().table();
			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() {
					return table.rowCount() == 1 && table.cell(0, 5).startsWith("LOCKED_ITERATION:"); //$NON-NLS-1$
				}

				@Override
				public String getFailureMessage() {
					return "The Container Analysis view did not display the synchronized list iteration"; //$NON-NLS-1$
				}
			});
			assertEquals("REPORT_ONLY", table.cell(0, 4)); //$NON-NLS-1$
			SWTBotEclipseEditor editor= bot.editorByTitle(unit.getElementName()).toTextEditor();
			table.getTableItem(0).doubleClick();
			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() {
					return "this.entries".equals(editor.getSelection()); //$NON-NLS-1$
				}

				@Override
				public String getFailureMessage() {
					return "Double-clicking the evidence did not reveal the loop's list expression"; //$NON-NLS-1$
				}
			});
			assertEquals(source, unit.getSource());
		} finally {
			project.getProject().delete(true, true, null);
		}
	}
}
