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
import java.security.MessageDigest;
import java.util.HexFormat;
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
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
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
	private static final String CORPUS_FILE_ENV= "SANDBOX_HELP_XML_CORPUS_FILE"; //$NON-NLS-1$
	private static final String CORPUS_REPOSITORY_ENV= "SANDBOX_HELP_XML_CORPUS_REPOSITORY"; //$NON-NLS-1$
	private static final String CORPUS_REF_ENV= "SANDBOX_HELP_XML_CORPUS_REF"; //$NON-NLS-1$
	private static final String CORPUS_COMMIT_ENV= "SANDBOX_HELP_XML_CORPUS_COMMIT"; //$NON-NLS-1$
	private static final String CORPUS_PATH_ENV= "SANDBOX_HELP_XML_CORPUS_PATH"; //$NON-NLS-1$
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
		String repository= configured(CORPUS_REPOSITORY_PROPERTY, CORPUS_REPOSITORY_ENV);
		String ref= configured(CORPUS_REF_PROPERTY, CORPUS_REF_ENV);
		String commit= configured(CORPUS_COMMIT_PROPERTY, CORPUS_COMMIT_ENV);
		String sourcePath= configured(CORPUS_PATH_PROPERTY, CORPUS_PATH_ENV);
		Path source= Path.of(configured(CORPUS_FILE_PROPERTY, CORPUS_FILE_ENV))
				.toAbsolutePath().normalize();
		assertTrue(Files.isRegularFile(source),
				() -> "Pinned PDE XML screenshot corpus is missing: " + source); //$NON-NLS-1$
		byte[] sourceBytes= Files.readAllBytes(source);
		assertTrue(sourceBytes.length > 2_000,
				"The marker screenshot must use a substantial real PDE schema"); //$NON-NLS-1$
		assertTrue(commit.matches("[0-9a-f]{40}"), //$NON-NLS-1$
				() -> "Invalid pinned JDT UI commit: " + commit); //$NON-NLS-1$
		assertTrue(sourcePath.endsWith(".exsd"), //$NON-NLS-1$
				() -> "The screenshot source is not a PDE extension schema: " + sourcePath); //$NON-NLS-1$

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
			clickContextMenu(fileItem, ANALYZE_LABEL);

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
			assertTrue(marker.getAttribute(IMarker.LINE_NUMBER, -1) > 0,
					"The real schema marker must identify a source line"); //$NON-NLS-1$
			assertTrue(marker.isSubtypeOf(IMarker.PROBLEM),
					"The custom PDE XML marker must be a subtype of the Eclipse problem marker"); //$NON-NLS-1$
			assertTrue(file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO).length > 0,
					"The Eclipse problem-marker query must expose the custom PDE XML marker"); //$NON-NLS-1$

			SWTBotView problems= bot.viewById(PROBLEMS_VIEW);
			problems.show();
			SWTBotTree problemTree= problems.bot().tree();
			SWTBotTreeItem problem= waitForTreeItem(bot, problemTree, MARKER_MESSAGE);
			problem.select();
			clickContextMenu(problem, "Quick Fix...", "Quick Fix…", "Quick Fix"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			SWTBotShell quickFix= bot.shell("Quick Fix").activate(); //$NON-NLS-1$
			SWTBotTable table= quickFix.bot().table();
			table.getTableItem(QUICK_FIX_LABEL).select();
			Path output= SandboxCheckout.locate("sandbox.help.screenshot.output") //$NON-NLS-1$
					.resolve("sandbox_xml_cleanup_help/images"); //$NON-NLS-1$
			captureWorkbench(output.resolve(SCREENSHOT));
			writeProvenance(output.resolve(PROVENANCE), repository, ref, commit,
					sourcePath, sourceBytes);
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
			assertTrue(after.contains("Schema file written by PDE"), //$NON-NLS-1$
					"The quick fix must preserve the upstream schema comment"); //$NON-NLS-1$
			assertTrue(after.contains("This extension point allows to add clean ups"), //$NON-NLS-1$
					"The quick fix must preserve the upstream schema documentation"); //$NON-NLS-1$
		} finally {
			project.delete(true, true, monitor);
		}
	}

	private static String configured(String property, String environment) {
		String value= System.getProperty(property);
		if (value == null || value.isBlank()) {
			value= System.getenv(environment);
		}
		assertTrue(value != null && !value.isBlank(),
				() -> "Missing PDE XML screenshot configuration: " + property //$NON-NLS-1$
						+ " or " + environment); //$NON-NLS-1$
		return value.strip();
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
				return "Problems view does not contain marker: " + text //$NON-NLS-1$
						+ "\nVisible Problems tree:\n" + describe(tree); //$NON-NLS-1$
			}
		});
		return result[0];
	}

	private static SWTBotTreeItem find(SWTBotTreeItem[] items, String text) {
		for (SWTBotTreeItem item : items) {
			if (item.row().toString().contains(text)) {
				return item;
			}
			if (item.rowCount() > 0) {
				if (!item.isExpanded()) {
					item.expand();
				}
				SWTBotTreeItem child= find(item.getItems(), text);
				if (child != null) {
					return child;
				}
			}
		}
		return null;
	}

	private static String describe(SWTBotTree tree) {
		try {
			StringBuilder result= new StringBuilder();
			for (SWTBotTreeItem item : tree.getAllItems()) {
				append(result, item, 0);
			}
			return result.isEmpty() ? "<empty>" : result.toString(); //$NON-NLS-1$
		} catch (RuntimeException exception) {
			return "<unavailable: " + exception.getClass().getSimpleName() //$NON-NLS-1$
					+ ": " + exception.getMessage() + ">"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static void append(StringBuilder result, SWTBotTreeItem item, int depth) {
		result.append("  ".repeat(depth)).append(item.row()).append('\n'); //$NON-NLS-1$
		if (item.rowCount() > 0) {
			if (!item.isExpanded()) {
				item.expand();
			}
			for (SWTBotTreeItem child : item.getItems()) {
				append(result, child, depth + 1);
			}
		}
	}

	private static void clickContextMenu(SWTBotTreeItem item, String... labels) {
		for (String label : labels) {
			try {
				item.contextMenu(label).click();
				return;
			} catch (WidgetNotFoundException exception) {
				// Try the next platform spelling.
			}
		}
		throw new IllegalStateException("None of the expected context actions is visible: " //$NON-NLS-1$
				+ List.of(labels));
	}

	private static void clickButton(SWTBotShell shell, String... labels) {
		for (String label : labels) {
			try {
				shell.bot().button(label).click();
				return;
			} catch (WidgetNotFoundException exception) {
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

	private static void writeProvenance(Path target, String repository, String ref,
			String commit, String sourcePath, byte[] sourceBytes) throws Exception {
		String json= """
				{
				  "schemaVersion": 1,
				  "repository": "%s",
				  "ref": "%s",
				  "commit": "%s",
				  "sourcePath": "%s",
				  "sourceSha256": "%s",
				  "sourceBytes": %d,
				  "scenario": "Problems view marker and Quick Fix"
				}
				""".formatted(json(repository), json(ref), json(commit), json(sourcePath),
						HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256") //$NON-NLS-1$
								.digest(sourceBytes)),
						Integer.valueOf(sourceBytes.length));
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
