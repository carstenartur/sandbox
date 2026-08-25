package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseBundleClasspath;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
/*
 * Cleanup execution scenarios persist temporary profiles in the shared
 * workbench. Capture all deterministic Help images first and run the
 * profile-mutating verify... scenarios afterwards.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SandboxHelpScreenshotsMergeGateSWTBotTest {

	private static final String CLEANUP_PREVIEW_PROJECT= "SandboxCleanupPreviewProject"; //$NON-NLS-1$
	private static final List<String> SHADOW_PLATFORM_SOURCES= List.of(
			"src/org/eclipse/core/runtime/IProgressMonitor.java", //$NON-NLS-1$
			"src/org/eclipse/core/runtime/SubMonitor.java", //$NON-NLS-1$
			"src/org/eclipse/core/runtime/SubProgressMonitor.java", //$NON-NLS-1$
			"src/org/eclipse/jface/viewers/ViewerComparator.java", //$NON-NLS-1$
			"src/org/eclipse/jface/viewers/ViewerSorter.java"); //$NON-NLS-1$

	private static SandboxHelpScreenshotsSWTBotTest screenshots;

	@BeforeAll
	public static void setUp() throws Exception {
		SandboxHelpScreenshotsSWTBotTest.setUp();
		screenshots= new SandboxHelpScreenshotsSWTBotTest();
	}

	@AfterEach
	public void closeTransientDialogs() {
		screenshots.closeTransientDialogs();
	}

	@AfterAll
	public static void tearDown() throws Exception {
		SandboxHelpScreenshotsSWTBotTest.tearDown();
	}

	@Test
	@Order(5)
	public void captureCleanupConfigurationTabs() throws IOException {
		// Keep the original deterministic TriggerPattern setup, then overwrite the
		// ordinary cleanup tabs with focused states in which their documented
		// options are actually enabled.
		screenshots.captureCleanupConfigurationTabs();
		FocusedCleanupConfigurationScreenshots.capture();
	}

	@Test
	@Order(6)
	public void captureCssCleanupPreferences() throws IOException {
		screenshots.captureCssCleanupPreferences();
	}

	@Test
	@Order(1)
	public void captureRuleInferencePreferences() throws IOException {
		screenshots.captureRuleInferencePreferences();
	}

	@Test
	@Order(2)
	public void captureRefactoringMiningWorkflow() throws Exception {
		screenshots.captureRefactoringMiningWorkflow();
	}

	@Test
	@Order(3)
	public void captureNewHintRuleWizard() throws Exception {
		screenshots.captureNewHintRuleWizard();
	}

	@Test
	@Order(4)
	public void captureRealCleanupPreviewAndVerifyIndependentSelection() throws Exception {
		IUndoManager undoManager= RefactoringCore.getUndoManager();
		undoManager.flush();
		try {
			IProject previewProject= ResourcesPlugin.getWorkspace().getRoot().getProject(CLEANUP_PREVIEW_PROJECT);
			useRealTargetPlatformBindings(previewProject);

			IFile singleFile= previewProject.getFile("src/demo/single/SingleFileCleanup.java"); //$NON-NLS-1$
			assertTrue(singleFile.exists(), "The deterministic single-file preview fixture must exist"); //$NON-NLS-1$
			String before= readFile(singleFile);

			try {
				screenshots.captureRealCleanupPreviewAndVerifyIndependentSelection();
			} catch (AssertionError failure) {
				String previewTree= activePreviewTree();
				System.out.println("[help-screenshots] Real Cleanup preview tree:\n" + previewTree); //$NON-NLS-1$
				throw new AssertionError(failure.getMessage() + "\nReal Cleanup preview tree:\n" + previewTree, //$NON-NLS-1$
						failure);
			}

			assertTrue(undoManager.anythingToUndo(),
					"The single-file Cleanup operation must remain available for aggregate undo verification"); //$NON-NLS-1$
			undoManager.performUndo(null, new NullProgressMonitor());
			assertEquals(before, readFile(singleFile),
					"Undo must restore the single-file preview fixture byte-for-byte"); //$NON-NLS-1$
		} finally {
			undoManager.flush();
		}
	}

	@Test
	@Order(7)
	public void verifyRealMethodReuseCleanupPreviewApplyAndUndo() throws Exception {
		IUndoManager undoManager= RefactoringCore.getUndoManager();
		undoManager.flush();
		try {
			screenshots.verifyRealMethodReuseCleanupPreviewApplyAndUndo();
		} finally {
			undoManager.flush();
		}
	}

	private static String activePreviewTree() {
		try {
			SWTWorkbenchBot workbench= new SWTWorkbenchBot();
			SWTBotTree tree= workbench.activeShell().bot().tree();
			StringBuilder result= new StringBuilder();
			for (SWTBotTreeItem root : tree.getAllItems()) {
				appendTreeItem(result, root, 0);
			}
			return result.isEmpty() ? "<empty tree>" : result.toString(); //$NON-NLS-1$
		} catch (RuntimeException exception) {
			return "<unavailable: " + exception.getClass().getSimpleName() + ": " + exception.getMessage() + ">"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
	}

	private static void appendTreeItem(StringBuilder result, SWTBotTreeItem item, int depth) {
		result.append("  ".repeat(depth)) //$NON-NLS-1$
				.append(item.isChecked() ? "[x] " : "[ ] ") //$NON-NLS-1$ //$NON-NLS-2$
				.append(item.getText())
				.append('\n');
		for (SWTBotTreeItem child : item.getItems()) {
			appendTreeItem(result, child, depth + 1);
		}
	}

	private static void useRealTargetPlatformBindings(IProject project) throws Exception {
		assertTrue(project.exists(), "The deterministic Cleanup preview project must exist"); //$NON-NLS-1$
		NullProgressMonitor monitor= new NullProgressMonitor();
		for (String path : SHADOW_PLATFORM_SOURCES) {
			IFile source= project.getFile(path);
			if (source.exists()) {
				source.delete(true, monitor);
			}
		}

		EclipseBundleClasspath.addBundles(JavaCore.create(project),
				"org.eclipse.equinox.common", //$NON-NLS-1$
				"org.eclipse.jface", //$NON-NLS-1$
				"org.eclipse.swt"); //$NON-NLS-1$
		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
		assertNoJavaErrors(project);
	}

	private static void assertNoJavaErrors(IProject project) throws Exception {
		IMarker[] markers= project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER,
				true, IResource.DEPTH_INFINITE);
		String errors= Stream.of(markers)
			.filter(marker -> marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO)
						== IMarker.SEVERITY_ERROR)
			.map(marker -> marker.getResource().getProjectRelativePath()
						+ ":" + marker.getAttribute(IMarker.LINE_NUMBER, -1) //$NON-NLS-1$
						+ ": " + marker.getAttribute(IMarker.MESSAGE, "Unknown Java problem")) //$NON-NLS-1$ //$NON-NLS-2$
			.collect(Collectors.joining("\n")); //$NON-NLS-1$
		assertTrue(errors.isEmpty(),
				"The real target-platform Cleanup preview fixture must compile before SWTBot QA:\n\t" + errors); //$NON-NLS-1$
	}

	private static String readFile(IFile file) throws Exception {
		try (InputStream input= file.getContents()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
