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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
    private static final String VIEW_ID = "org.eclipse.jdt.ui.helper.views.JavaHelperView"; //$NON-NLS-1$

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
            SWTBotView view = bot.viewById(VIEW_ID);
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
        // Startup may already have opened the view. Exercise a real reopening.
        try {
            bot.viewById(VIEW_ID).close();
        } catch (WidgetNotFoundException e) {
            // Not already open.
        }
        openViewDialog();
        
        // Verify view is open
        SWTBotView view = bot.viewById(VIEW_ID);
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
        
        SWTBotView view = bot.viewById(VIEW_ID);
        view.show();
        view.setFocus();
        
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
        
        SWTBotView view = bot.viewById(VIEW_ID);
        view.show();
        view.setFocus();
        
        // Find the Link with Selection toggle button in toolbar
        try {
            var linkButton = view.toolbarToggleButton("Link with Selection - when enabled, the view automatically updates based on the current selection"); //$NON-NLS-1$
            assertNotNull(linkButton, "Link with Selection button should exist"); //$NON-NLS-1$
            
            boolean original = linkButton.isChecked();
            linkButton.click();
            assertEquals(!original, linkButton.isChecked());
            linkButton.click();
            assertEquals(original, linkButton.isChecked());
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
        
        SWTBotView view = bot.viewById(VIEW_ID);
        view.show();
        view.setFocus();
        
        // Find the Filter Naming Conflicts toggle button in toolbar
        try {
            var filterButton = view.toolbarToggleButton("Filter Naming Conflicts - when enabled, only shows variables with the same name but different types"); //$NON-NLS-1$
            assertNotNull(filterButton, "Filter Naming Conflicts button should exist"); //$NON-NLS-1$
            boolean original = filterButton.isChecked();
            filterButton.click();
            assertEquals(!original, filterButton.isChecked());
            filterButton.click();
            assertEquals(original, filterButton.isChecked());
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
        
        SWTBotView view = bot.viewById(VIEW_ID);
        view.show();
        view.setFocus();
        
        // JFace decorates command tooltips with the active key binding (for
        // example "Refresh (F5)"). Find the real visible action by command ID,
        // not by localized text, the current keymap, or an arbitrary first item.
        List<SWTBotToolbarButton> buttons = view.getToolbarButtons();
        List<SWTBotToolbarButton> refreshButtons = new ArrayList<>();
        Display.getDefault().syncExec(() -> {
            for (SWTBotToolbarButton button : buttons) {
                if (button.widget.getData() instanceof ActionContributionItem item
                        && "org.eclipse.ui.file.refresh".equals(item.getAction().getActionDefinitionId())) { //$NON-NLS-1$
                    refreshButtons.add(button);
                }
            }
        });
        assertEquals(1, refreshButtons.size(), "The view must expose exactly one Refresh toolbar action"); //$NON-NLS-1$
        SWTBotToolbarButton refreshButton = refreshButtons.getFirst();
        assertTrue(refreshButton.isEnabled(), "Refresh should be enabled"); //$NON-NLS-1$
        refreshButton.click();
    }

    @BeforeEach
    public void activateWorkbench() {
        // A native runner can have a visible workbench without an active shell.
        // SWTBot's global menu lookup otherwise constructs SWTBotShell(null).
        Shell[] shell = new Shell[1];
        Display.getDefault().syncExec(() -> {
            var windows = PlatformUI.getWorkbench().getWorkbenchWindows();
            if (windows.length > 0) {
                shell[0] = windows[0].getShell();
            }
        });
        assertNotNull(shell[0], "The native workbench must have a shell"); //$NON-NLS-1$
        new SWTBotShell(shell[0]).activate();
    }

    private void openViewDialog() {
        activateWorkbench();
        bot.menu("Window").menu("Show View").menu("Other...").click(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        SWTBotShell dialog = bot.shell("Show View"); //$NON-NLS-1$
        dialog.activate();
        dialog.bot().tree().expandNode("Java").select(VIEW_TITLE); //$NON-NLS-1$
        dialog.bot().button("Open").click(); //$NON-NLS-1$
        bot.viewById(VIEW_ID).setFocus();
    }

    /**
     * Helper method to open the view if not already open.
     */
    private void openViewIfNeeded() {
        try {
            bot.viewById(VIEW_ID);
        } catch (WidgetNotFoundException e) {
            // View not open, open it
            openViewDialog();
        }
    }
}
