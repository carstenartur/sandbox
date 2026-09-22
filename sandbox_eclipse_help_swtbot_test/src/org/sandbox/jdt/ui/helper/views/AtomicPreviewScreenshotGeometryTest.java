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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class AtomicPreviewScreenshotGeometryTest {

    private static final String ATOMIC_SELECTION =
            "Selection is atomic: use the single candidate checkbox in the Changes tree to include or exclude every required file.";

    private record Fixture(Shell shell, Text header, Table table, Text details,
            StyledText leftPane, StyledText rightPane) {
    }

    @Test
    void prepareAcceptsANativeTextHeaderForFourFilesAndATwoFileCandidate() {
        withFixture(4, 2, fixture -> {
            SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry.prepare(
                    fixture.shell(), 4, 2);
            assertEquals(1, SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry
                    .headerCountMatches(fixture.shell(), 4));
            assertTrue(SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry
                    .matchesPreparedGeometry(fixture.shell()));
            assertNotNull(SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry
                    .captureBounds(fixture.shell()));
            assertEquals(2, fixture.table().getItemCount());
            assertTrue(fixture.details().getText().contains("Affected source files: 2"));
            assertEquals(0, fixture.leftPane().getHorizontalPixel());
            assertEquals(0, fixture.leftPane().getTopIndex());
            assertEquals(0, fixture.leftPane().getTopPixel());
            assertEquals(0, fixture.rightPane().getHorizontalPixel());
            assertEquals(0, fixture.rightPane().getTopIndex());
            assertEquals(0, fixture.rightPane().getTopPixel());
        });
    }

    @Test
    void prepareRejectsMismatchedHeaderCountsEvenWhenFourWouldMatchFourteen() {
        withFixture(14, 2, fixture -> {
            AssertionError failure = assertThrows(AssertionError.class,
                    () -> SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry.prepare(
                            fixture.shell(), 4, 2));
            assertEquals(0, SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry
                    .headerCountMatches(fixture.shell(), 4));
            assertTrue(failure.getMessage().contains("headerMatches=0"));
        });
    }

    @Test
    void sourceFitsRejectsScrolledSourcePanes() {
        withFixture(2, 2, fixture -> {
            SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry.prepare(
                    fixture.shell(), 2, 2);
            assertTrue(SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry
                    .sourceFits(fixture.leftPane()));
            fixture.leftPane().setHorizontalPixel(120);
            fixture.leftPane().setTopIndex(1);
            fixture.leftPane().setTopPixel(20);
            assertFalse(SandboxHelpScreenshotsSWTBotTest.AtomicPreviewScreenshotGeometry
                    .sourceFits(fixture.leftPane()));
        });
    }

    private static void withFixture(int headerFileCount, int candidateFileCount,
            Consumer<Fixture> assertion) {
        Display.getDefault().syncExec(() -> {
            Shell shell = new Shell(Display.getDefault());
            try {
                shell.setLayout(new FillLayout());
                shell.setSize(1_280, 900);

                Composite root = new Composite(shell, SWT.NONE);
                root.setLayout(new GridLayout(1, false));

                Text header = new Text(root, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
                header.setEditable(false);
                header.setText("The following changes to " + headerFileCount
                        + " files are necessary to perform the refactoring.");
                header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

                SashForm sash = new SashForm(root, SWT.HORIZONTAL);
                sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

                Composite overview = new Composite(sash, SWT.NONE);
                overview.setLayout(new GridLayout(1, false));

                Table table = new Table(overview, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
                GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, false);
                tableData.heightHint = 120;
                table.setLayoutData(tableData);
                for (int index = 0; index < candidateFileCount; index++) {
                    TableItem item = new TableItem(table, SWT.NONE);
                    item.setText(index == 0 ? "demo/FirstResource.java" : "demo/FirstTest.java");
                }

                Text details = new Text(overview, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL);
                details.setEditable(false);
                details.setText(ATOMIC_SELECTION + System.lineSeparator()
                        + "Affected source files: " + candidateFileCount + System.lineSeparator()
                        + "The selected files form one atomic migration candidate.");
                GridData detailsData = new GridData(SWT.FILL, SWT.FILL, true, true);
                detailsData.heightHint = 180;
                details.setLayoutData(detailsData);

                Composite compare = new Composite(sash, SWT.NONE);
                compare.setLayout(new GridLayout(2, true));

                StyledText leftPane = sourcePane(compare);
                StyledText rightPane = sourcePane(compare);
                sash.setWeights(35, 65);

                shell.open();
                shell.layout(true, true);
                shell.update();
                assertion.accept(new Fixture(shell, header, table, details, leftPane, rightPane));
            } finally {
                shell.dispose();
            }
        });
    }

    private static StyledText sourcePane(Composite parent) {
        StyledText pane = new StyledText(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        pane.setEditable(false);
        pane.setText("""
                package demo.junit.preview;

                import java.util.concurrent.atomic.AtomicInteger;
                import org.junit.jupiter.api.extension.AfterEachCallback;
                import org.junit.jupiter.api.extension.BeforeEachCallback;

                public final class ExtremelyLongFirstResourceName implements BeforeEachCallback, AfterEachCallback {
                    private final AtomicInteger counter = new AtomicInteger();
                }
                """);
        pane.setStyleRange(new StyleRange(0, "package".length(), null, null, SWT.BOLD));
        pane.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        return pane;
    }
}
