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

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
