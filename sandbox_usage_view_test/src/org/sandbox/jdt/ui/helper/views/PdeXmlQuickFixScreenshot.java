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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

/** Real Problems-view and marker-resolution scenario for PDE XML cleanup. */
final class PdeXmlQuickFixScreenshot {

	private static final String CORPUS_FILE_PROPERTY= "sandbox.help.xml.corpus.file"; //$NON-NLS-1$
	private static final String CORPUS_REPOSITORY_PROPERTY= "sandbox.help.xml.corpus.repository"; //$NON-NLS-1$
	private static final String CORPUS_REF_PROPERTY= "sandbox.help.xml.corpus.ref"; //$NON-NLS-1$
	private static final String CORPUS_COMMIT_PROPERTY= "sandbox.help.xml.corpus.commit"; //$NON-NLS-1$
	private static final String CORPUS_PATH_PROPERTY= "sandbox.help.xml.corpus.path"; //$NON-NLS-1$
	private static final String MARKER_TYPE= "sandbox_xml_cleanup.pdeXmlCleanupProblem"; //$NON-NLS-1$
	private static final String MARKER_MESSAGE= "PDE XML formatting can be normalized"; //$NON-NLS-1$
	private static final String ANALYZE_LABEL= "Find PDE XML Cleanup Problems"; //$NON-NLS-1$
	private static final String QUICK_FIX_LABEL= "Normalize PDE XML formatting"; //$NON-NLS-1$
	private static final String PROJECT_NAME= "SandboxPdeXmlQuickFix"; //$NON-NLS-1$
	private static final String SCREENSHOT= "xml-cleanup-marker-quick-fix.png"; //$NON-NLS-1$
	private static final String PROVENANCE= "xml-cleanup-marker-quick-fix.provenance.json"; //$NON-NLS-1$
	private static final String PROBLEMS_VIEW= "org.eclipse.ui.views.ProblemView"; //$NON-NLS-1$
	private static final String PROJECT_EXPLORER_VIEW= "org.eclipse.ui.navigator.ProjectExplorer"; //$NON-NLS-1$

	private PdeXmlQuickFixScreenshot() {
	}

	static void capture() throws Exception {
		Path source= Path.of(System.getProperty(CORPUS_FILE_PROPERTY, "")).toAbsolutePath().normalize(); //$NON-NLS-1$
		assertTrue(Files.isRegularFile(source),
				() -> "Pinned PDE XML screenshot corpus is missing: " + source); //$NON-NLS-1$
		byte[] sourceBytes= Files.readAllBytes(source);
		assertTrue(sourceBytes.length > 2_000,
				"The marker screenshot must use a substantial real PDE schema"); //$NON-NLS-1$

		NullProgressMonitor monitor= new NullProgressMonitor();
		IProject project= ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
		if (project.exists()) {
			project.delete(true, true, monitor);
		}
		project.create(monitor);
		project.open(monitor);
		try {
			IFolder schema= project.getFolder("schema"); //$NON-NLS-1$
			schema.create(true, true, monitor);
			IFile file= schema.getFile("cleanUps.exsd"); //$NON-NLS-1$
			try (ByteArrayInputStream input= new ByteArrayInputStream(sourceBytes)) {
				file.create(input, true, monitor);
			}
			file.setCharset(StandardCharsets.UTF_8.name(), monitor);
			String before= read(file);

			SWTWorkbenchBot bot= new SWTWorkbenchBot();
			SWTBotView explorer= bot.viewById(PROJECT_EXPLORER_VIEW);
			explorer.show();
			SWTBotTree explorerTree= explorer.bot().tree();
			SWTBotTreeItem fileItem= explorerTree.getTreeItem(PROJECT_NAME).expand()
					.getNode("schema").expand().getNode("cleanUps.exsd"); //$NON-NLS-1$ //$NON-NLS-2$
			fileItem.select();
			fileItem.contextMenu(ANALYZE_LABEL).click();

			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() throws Exception {
					return markers(file).length == 1;
				}

				@Override
				public String getFailureMessage() {
					return "The PDE XML analysis command created no Problems-view marker"; //$NON-NLS-1$
				}
			});
			IMarker marker= markers(file)[0];
			assertEquals(MARKER_MESSAGE, marker.getAttribute(IMarker.MESSAGE));

			SWTBotView problems= bot.viewById(PROBLEMS_VIEW);
			problems.show();
			SWTBotTree problemTree= problems.bot().tree();
			SWTBotTreeItem problem= waitForTreeItem(bot, problemTree, MARKER_MESSAGE);
			problem.select();
			problem.contextMenu("Quick Fix").click(); //$NON-NLS-1$

			SWTBotShell quickFix= bot.shell("Quick Fix").activate(); //$NON-NLS-1$
			SWTBotTable table= quickFix.bot().table();
			table.getTableItem(QUICK_FIX_LABEL).select();
			captureWorkbench(SandboxCheckout.locate("sandbox.help.screenshot.output") //$NON-NLS-1$
					.resolve("sandbox_xml_cleanup_help/images").resolve(SCREENSHOT)); //$NON-NLS-1$
			writeProvenance(SandboxCheckout.locate("sandbox.help.screenshot.output") //$NON-NLS-1$
					.resolve("sandbox_xml_cleanup_help/images").resolve(PROVENANCE)); //$NON-NLS-1$
			clickButton(quickFix, "Finish", "OK"); //$NON-NLS-1$ //$NON-NLS-2$
			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() throws Exception {
					return quickFix.widget.isDisposed() && markers(file).length == 0;
				}

				@Override
				public String getFailureMessage() {
					return "The PDE XML quick fix did not finish or clear its marker"; //$NON-NLS-1$
				}
			});
			String after= read(file);
			assertFalse(before.equals(after), "The real PDE schema must be changed by the quick fix"); //$NON-NLS-1$
			assertTrue(after.contains("<schema"), "The quick fix must preserve the PDE schema root"); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			project.delete(true, true, monitor);
		}
	}

	private static IMarker[] markers(IFile file) throws Exception {
		return file.findMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
	}

	private static SWTBotTreeItem waitForTreeItem(SWTWorkbenchBot bot, SWTBotTree tree, String text) {
		SWTBotTreeItem[] result= new SWTBotTreeItem[1];
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				result[0]= find(tree.getAllItems(), text);
				return result[0] != null;
			}

			@Override
			public String getFailureMessage() {
				return "Problems view does not contain marker: " + text; //$NON-NLS-1$
			}
		});
		return result[0];
	}

	private static SWTBotTreeItem find(SWTBotTreeItem[] items, String text) {
		for (SWTBotTreeItem item : items) {
			if (item.getText().contains(text)) {
				return item;
			}
			SWTBotTreeItem child= find(item.getItems(), text);
			if (child != null) {
				return child;
			}
		}
		return null;
	}

	private static void clickButton(SWTBotShell shell, String... labels) {
		for (String label : labels) {
			try {
				shell.bot().button(label).click();
				return;
			} catch (org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException exception) {
				// Try the next platform label.
			}
		}
		throw new IllegalStateException("Quick Fix dialog has none of the expected buttons: " //$NON-NLS-1$
				+ List.of(labels));
	}

	private static void captureWorkbench(Path image) throws Exception {
		Files.createDirectories(image.getParent());
		Rectangle displayBounds= UIThreadRunnable.syncExec(Display.getDefault(), new Result<Rectangle>() {
			@Override
			public Rectangle run() {
				return Display.getDefault().getBounds();
			}
		});
		assertTrue(SWTUtils.captureScreenshot(image.toString(), displayBounds),
				() -> "Could not capture PDE XML marker quick-fix screenshot: " + image); //$NON-NLS-1$
		assertTrue(Files.size(image) > 0, () -> "Empty PDE XML screenshot: " + image); //$NON-NLS-1$
	}

	private static void writeProvenance(Path target) throws Exception {
		String json= """
				{
				  "schemaVersion": 1,
				  "repository": "%s",
				  "ref": "%s",
				  "commit": "%s",
				  "sourcePath": "%s",
				  "scenario": "Problems view marker and Quick Fix"
				}
				""".formatted(
					json(System.getProperty(CORPUS_REPOSITORY_PROPERTY, "")), //$NON-NLS-1$
					json(System.getProperty(CORPUS_REF_PROPERTY, "")), //$NON-NLS-1$
					json(System.getProperty(CORPUS_COMMIT_PROPERTY, "")), //$NON-NLS-1$
					json(System.getProperty(CORPUS_PATH_PROPERTY, ""))); //$NON-NLS-1$
		Files.writeString(target, json, StandardCharsets.UTF_8);
	}

	private static String json(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private static String read(IFile file) throws Exception {
		try (InputStream input= file.getContents()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
