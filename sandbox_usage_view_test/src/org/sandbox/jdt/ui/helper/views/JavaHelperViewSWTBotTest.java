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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * SWTBot UI tests for the JavaHelper View (Usage View).
 * 
 * These tests verify the UI functionality of the view:
 * - View can be opened
 * - Table is present and has expected columns
 * - Toolbar actions work (Link with Selection, Filter Naming Conflicts)
 * 
 * Run with: mvn verify -Pswtbot -pl sandbox_usage_view_test
 */
@TestMethodOrder(OrderAnnotation.class)
public class JavaHelperViewSWTBotTest {

    private static SWTWorkbenchBot bot;
    private static final String VIEW_TITLE = "JavaHelper View"; //$NON-NLS-1$

    @BeforeAll
    public static void setUp() {
        bot = new SWTWorkbenchBot();
        // Close welcome view if present
        try {
            bot.viewByTitle("Welcome").close(); //$NON-NLS-1$
        } catch (WidgetNotFoundException e) {
            // Welcome view not present, ignore
        }
    }

    @AfterAll
    public static void tearDown() {
        // Close the view if it was opened
        try {
            SWTBotView view = bot.viewByTitle(VIEW_TITLE);
            view.close();
        } catch (WidgetNotFoundException e) {
            // View not open, ignore
        }
    }

    /**
     * Test that the JavaHelper View can be opened via Show View dialog.
     */
    @Test
    @Order(1)
    public void testOpenView() {
        // Open the view via menu
        bot.menu("Window").menu("Show View").menu("Other...").click(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        bot.tree().expandNode("Java").select(VIEW_TITLE); //$NON-NLS-1$
        bot.button("Open").click(); //$NON-NLS-1$
        
        // Verify view is open
        SWTBotView view = bot.viewByTitle(VIEW_TITLE);
        assertNotNull(view, "JavaHelper View should be open"); //$NON-NLS-1$
        assertTrue(view.isActive(), "JavaHelper View should be active"); //$NON-NLS-1$
    }

    /**
     * Test that the view contains a table with expected columns.
     */
    @Test
    @Order(2)
    public void testViewHasTable() {
        openViewIfNeeded();
        
        SWTBotView view = bot.viewByTitle(VIEW_TITLE);
        view.show();
        
        // Get the table from the view
        SWTBotTable table = view.bot().table();
        assertNotNull(table, "View should contain a table"); //$NON-NLS-1$
        
        // Verify table has columns (column count > 0)
        int columnCount = table.columnCount();
        assertTrue(columnCount > 0, "Table should have columns, found: " + columnCount); //$NON-NLS-1$
    }

    /**
     * Test that the Link with Selection toolbar button exists and can be toggled.
     */
    @Test
    @Order(3)
    public void testLinkWithSelectionToggle() {
        openViewIfNeeded();
        
        SWTBotView view = bot.viewByTitle(VIEW_TITLE);
        view.show();
        
        // Find the Link with Selection toggle button in toolbar
        try {
            SWTBotToolbarButton linkButton = view.toolbarToggleButton("Link with Selection"); //$NON-NLS-1$
            assertNotNull(linkButton, "Link with Selection button should exist"); //$NON-NLS-1$
            
            // Toggle the button
            linkButton.click();
            // Toggle back
            linkButton.click();
        } catch (WidgetNotFoundException e) {
            fail("Link with Selection button not found: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    /**
     * Test that the Filter Naming Conflicts toolbar button exists.
     */
    @Test
    @Order(4)
    public void testFilterNamingConflictsButton() {
        openViewIfNeeded();
        
        SWTBotView view = bot.viewByTitle(VIEW_TITLE);
        view.show();
        
        // Find the Filter Naming Conflicts toggle button in toolbar
        try {
            SWTBotToolbarButton filterButton = view.toolbarToggleButton("Filter Naming Conflicts"); //$NON-NLS-1$
            assertNotNull(filterButton, "Filter Naming Conflicts button should exist"); //$NON-NLS-1$
        } catch (WidgetNotFoundException e) {
            fail("Filter Naming Conflicts button not found: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    /**
     * Test that the Refresh toolbar button exists.
     */
    @Test
    @Order(5)
    public void testRefreshButton() {
        openViewIfNeeded();
        
        SWTBotView view = bot.viewByTitle(VIEW_TITLE);
        view.show();
        
        // Find the Refresh button in toolbar
        try {
            SWTBotToolbarButton refreshButton = view.toolbarButton("Refresh"); //$NON-NLS-1$
            assertNotNull(refreshButton, "Refresh button should exist"); //$NON-NLS-1$
            
            // Click refresh
            refreshButton.click();
        } catch (WidgetNotFoundException e) {
            fail("Refresh button not found: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    /**
     * Helper method to open the view if not already open.
     */
    private void openViewIfNeeded() {
        try {
            bot.viewByTitle(VIEW_TITLE);
        } catch (WidgetNotFoundException e) {
            // View not open, open it
            bot.menu("Window").menu("Show View").menu("Other...").click(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            bot.tree().expandNode("Java").select(VIEW_TITLE); //$NON-NLS-1$
            bot.button("Open").click(); //$NON-NLS-1$
        }
    }
}
