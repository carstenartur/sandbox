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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Bundle;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.SWTBotAssert;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

import org.eclipse.ui.PlatformUI;

import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/**
 * Deterministic fixture for the real LTK preview produced by a Cleanup refactoring.
 */
final class CleanupPreviewSWTBotScenario implements AutoCloseable {

	static final String MONITOR_DESCRIPTION= "Replace SubProgressMonitor with SubMonitor"; //$NON-NLS-1$
	static final String VIEWER_DESCRIPTION= "Replace ViewerSorter with ViewerComparator"; //$NON-NLS-1$

	private static final String MASTER_OPTION= "cleanup.jfacecleanup"; //$NON-NLS-1$
	private static final String MONITOR_OPTION= "cleanup.jfacecleanup_monitor"; //$NON-NLS-1$
	private static final String VIEWER_OPTION= "cleanup.jfacecleanup_viewer_sorter"; //$NON-NLS-1$
	private static final String IMAGE_OPTION= "cleanup.jfacecleanup_image_dpi"; //$NON-NLS-1$
	private static final long UI_TIMEOUT= 60_000L;

	private final SWTWorkbenchBot bot;
	private final IJavaProject javaProject;
	private final List<ICompilationUnit> units;
	private final List<String> originals;
	private final AtomicReference<Throwable> wizardFailure;
	private SWTBotShell shell;
	private SWTBotTree tree;

	private CleanupPreviewSWTBotScenario(SWTWorkbenchBot bot, IJavaProject javaProject,
			List<ICompilationUnit> units, List<String> originals, AtomicReference<Throwable> wizardFailure,
			SWTBotShell shell, SWTBotTree tree) {
		this.bot= bot;
		this.javaProject= javaProject;
		this.units= List.copyOf(units);
		this.originals= List.copyOf(originals);
		this.wizardFailure= wizardFailure;
		this.shell= shell;
		this.tree= tree;
	}

	static CleanupPreviewSWTBotScenario openMultipleSteps(SWTWorkbenchBot bot, Path repositoryRoot)
			throws Exception {
		String projectName= "CleanupPreviewSteps"; //$NON-NLS-1$
		List<SourceFile> sources= List.of(new SourceFile("CombinedPreview.java", combinedSource())); //$NON-NLS-1$
		return open(bot, repositoryRoot, projectName, sources);
	}

	static CleanupPreviewSWTBotScenario openMultipleFiles(SWTWorkbenchBot bot, Path repositoryRoot)
			throws Exception {
		String projectName= "CleanupPreviewFiles"; //$NON-NLS-1$
		List<SourceFile> sources= List.of(
				new SourceFile("FirstPreview.java", monitorSource("FirstPreview", "First task")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				new SourceFile("SecondPreview.java", monitorSource("SecondPreview", "Second task"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return open(bot, repositoryRoot, projectName, sources);
	}

	SWTBotShell shell() {
		return shell;
	}

	void prepareMultipleStepView() {
		SWTBotTreeItem file= findItemContaining("CombinedPreview.java"); //$NON-NLS-1$
		file.expand();
		SWTBotTreeItem monitor= findDescendant(file, MONITOR_DESCRIPTION);
		SWTBotTreeItem viewer= findDescendant(file, VIEWER_DESCRIPTION);
		viewer.uncheck();
		monitor.select();
		assertFalse(viewer.isChecked(), "The ViewerSorter step should be independently deselected");
	}

	void prepareMultipleFileView() {
		SWTBotTreeItem first= findItemContaining("FirstPreview.java"); //$NON-NLS-1$
		SWTBotTreeItem second= findItemContaining("SecondPreview.java"); //$NON-NLS-1$
		first.expand();
		second.expand();
		second.uncheck();
		findDescendant(first, MONITOR_DESCRIPTION).select();
		assertFalse(second.isChecked(), "The second independent file should be deselected");
	}

	void finishMultipleStepSelectionAndUndo() throws Exception {
		finish();
		String changed= units.get(0).getSource();
		assertTrue(changed.contains("SubMonitor"), "The selected SubMonitor migration was not applied"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(changed.contains("ViewerSorter"), "The deselected ViewerSorter migration was applied"); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(changed.contains("ViewerComparator"), //$NON-NLS-1$
				"The deselected ViewerSorter migration must remain unchanged"); //$NON-NLS-1$
		undoAndAssertOriginals();
	}

	void finishMultipleFileSelectionAndUndo() throws Exception {
		finish();
		assertTrue(units.get(0).getSource().contains("SubMonitor"), //$NON-NLS-1$
				"The selected first file was not cleaned"); //$NON-NLS-1$
		assertEquals(originals.get(1), units.get(1).getSource(),
				"The deselected second file must remain byte-identical"); //$NON-NLS-1$
		undoAndAssertOriginals();
	}

	private void finish() throws Exception {
		shell.bot().button("Finish").click(); //$NON-NLS-1$
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return !shell.isOpen();
			}

			@Override
			public String getFailureMessage() {
				return "Cleanup preview did not close after Finish"; //$NON-NLS-1$
			}
		}, UI_TIMEOUT);
		assertNoWizardFailure();
		javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
	}

	private void undoAndAssertOriginals() throws Exception {
		IUndoManager undoManager= RefactoringCore.getUndoManager();
		assertTrue(undoManager.anythingToUndo(), "Cleanup execution did not register an undo change"); //$NON-NLS-1$
		undoManager.performUndo(null, new NullProgressMonitor());
		javaProject.getProject().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		for (int index= 0; index < units.size(); index++) {
			assertEquals(originals.get(index), units.get(index).getSource(),
					"Undo did not restore " + units.get(index).getElementName()); //$NON-NLS-1$
		}
	}

	private SWTBotTreeItem findItemContaining(String text) {
		for (SWTBotTreeItem item : tree.getAllItems()) {
			SWTBotTreeItem result= findItemContaining(item, text);
			if (result != null) {
				return result;
			}
		}
		throw new AssertionError("Preview tree has no item containing: " + text); //$NON-NLS-1$
	}

	private static SWTBotTreeItem findItemContaining(SWTBotTreeItem item, String text) {
		if (item.getText().contains(text)) {
			return item;
		}
		item.expand();
		for (SWTBotTreeItem child : item.getItems()) {
			SWTBotTreeItem result= findItemContaining(child, text);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	private static SWTBotTreeItem findDescendant(SWTBotTreeItem item, String exactText) {
		item.expand();
		for (SWTBotTreeItem child : item.getItems()) {
			if (exactText.equals(child.getText())) {
				return child;
			}
			SWTBotTreeItem nested= findDescendantOrNull(child, exactText);
			if (nested != null) {
				return nested;
			}
		}
		throw new AssertionError("Preview file has no step: " + exactText); //$NON-NLS-1$
	}

	private static SWTBotTreeItem findDescendantOrNull(SWTBotTreeItem item, String exactText) {
		item.expand();
		for (SWTBotTreeItem child : item.getItems()) {
			if (exactText.equals(child.getText())) {
				return child;
			}
			SWTBotTreeItem nested= findDescendantOrNull(child, exactText);
			if (nested != null) {
				return nested;
			}
		}
		return null;
	}

	@Override
	public void close() throws Exception {
		if (shell != null && shell.isOpen()) {
			SWTBot cancelBot= shell.bot();
			if (cancelBot.button("Cancel").isEnabled()) { //$NON-NLS-1$
				cancelBot.button("Cancel").click(); //$NON-NLS-1$
			}
		}
		assertNoWizardFailure();
		RefactoringCore.getUndoManager().flush();
		IProject project= javaProject.getProject();
		if (project.exists()) {
			AbstractEclipseJava.delete(project);
		}
	}

	private void assertNoWizardFailure() {
		Throwable failure= wizardFailure.get();
		if (failure != null) {
			throw new AssertionError("Cleanup preview failed", failure); //$NON-NLS-1$
		}
	}

	private static CleanupPreviewSWTBotScenario open(SWTWorkbenchBot bot, Path repositoryRoot,
			String projectName, List<SourceFile> sources) throws Exception {
		IProject staleProject= PlatformUI.getWorkbench().getWorkspace().getRoot().getProject(projectName);
		if (staleProject.exists()) {
			AbstractEclipseJava.delete(staleProject);
		}

		IJavaProject javaProject= createJavaProject(repositoryRoot, projectName);
		IPackageFragmentRoot sourceRoot= AbstractEclipseJava.addSourceContainer(javaProject, "src"); //$NON-NLS-1$
		IPackageFragment packageFragment= sourceRoot.createPackageFragment("preview", true, //$NON-NLS-1$
				new NullProgressMonitor());
		List<ICompilationUnit> units= new ArrayList<>();
		List<String> originals= new ArrayList<>();
		for (SourceFile source : sources) {
			ICompilationUnit unit= packageFragment.createCompilationUnit(source.name(), source.content(), true,
					new NullProgressMonitor());
			units.add(unit);
			originals.add(source.content());
		}
		javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());

		RefactoringCore.getUndoManager().flush();
		CleanUpRefactoring refactoring= createRefactoring(units);
		PreviewOnlyCleanupWizard wizard= new PreviewOnlyCleanupWizard(refactoring);
		AtomicReference<Throwable> wizardFailure= new AtomicReference<>();
		openWizardAsynchronously(wizard, wizardFailure);
		SWTBotShell shell= waitForShell(bot, wizardFailure);
		SWTBotTree tree= waitForTree(shell);
		return new CleanupPreviewSWTBotScenario(bot, javaProject, units, originals, wizardFailure, shell, tree);
	}

	private static IJavaProject createJavaProject(Path repositoryRoot, String projectName) throws Exception {
		IJavaProject project= AbstractEclipseJava.createJavaProject(projectName, "bin"); //$NON-NLS-1$
		Path runtimeStubs= repositoryRoot.resolve("testresources/rtstubs_17.jar"); //$NON-NLS-1$
		assertTrue(Files.isRegularFile(runtimeStubs), () -> "Missing runtime stubs: " + runtimeStubs); //$NON-NLS-1$
		IClasspathEntry runtimeEntry= JavaCore.newLibraryEntry(
				org.eclipse.core.runtime.Path.fromOSString(runtimeStubs.toString()), null, null, true);
		project.setRawClasspath(new IClasspathEntry[] { runtimeEntry }, new NullProgressMonitor());
		addBundleToClasspath(project, "org.eclipse.equinox.common"); //$NON-NLS-1$
		addBundleToClasspath(project, "org.eclipse.jface"); //$NON-NLS-1$
		addBundleToClasspath(project, "org.eclipse.swt"); //$NON-NLS-1$
		Map<String, String> compilerOptions= project.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, compilerOptions);
		project.setOptions(compilerOptions);
		return project;
	}

	private static void addBundleToClasspath(IJavaProject project, String symbolicName) throws CoreException {
		Bundle bundle= Platform.getBundle(symbolicName);
		if (bundle == null) {
			throw new CoreException(Status.error("Bundle not found: " + symbolicName)); //$NON-NLS-1$
		}
		addBundleFile(project, bundle, symbolicName);
		Bundle[] fragments= Platform.getFragments(bundle);
		if (fragments != null) {
			for (Bundle fragment : fragments) {
				addBundleFile(project, fragment, fragment.getSymbolicName());
			}
		}
	}

	private static void addBundleFile(IJavaProject project, Bundle bundle, String symbolicName) throws CoreException {
		try {
			File bundleFile= FileLocator.getBundleFile(bundle);
			File classpathFile= bundleFile.isDirectory() && new File(bundleFile, "bin").isDirectory() //$NON-NLS-1$
					? new File(bundleFile, "bin") //$NON-NLS-1$
					: bundleFile;
			IClasspathEntry entry= JavaCore.newLibraryEntry(
					org.eclipse.core.runtime.Path.fromOSString(classpathFile.getAbsolutePath()), null, null);
			AbstractEclipseJava.addToClasspath(project, entry);
		} catch (IOException e) {
			throw new CoreException(Status.error("Cannot locate bundle file: " + symbolicName, e)); //$NON-NLS-1$
		}
	}

	private static CleanUpRefactoring createRefactoring(List<ICompilationUnit> units) {
		Map<String, String> settings= new HashMap<>();
		JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS)
				.getKeys().forEach(key -> settings.put(key, CleanUpOptions.FALSE));
		settings.put(MASTER_OPTION, CleanUpOptions.TRUE);
		settings.put(MONITOR_OPTION, CleanUpOptions.TRUE);
		settings.put(VIEWER_OPTION, CleanUpOptions.TRUE);
		settings.put(IMAGE_OPTION, CleanUpOptions.FALSE);
		MapCleanUpOptions options= new MapCleanUpOptions(settings);

		CleanUpRefactoring refactoring= new CleanUpRefactoring("Clean Up"); //$NON-NLS-1$
		refactoring.setUseOptionsFromProfile(false);
		for (ICompilationUnit unit : units) {
			refactoring.addCompilationUnit(unit);
		}
		for (ICleanUp cleanUp : JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps()) {
			cleanUp.setOptions(options);
			refactoring.addCleanUp(cleanUp);
		}
		return refactoring;
	}

	private static void openWizardAsynchronously(PreviewOnlyCleanupWizard wizard,
			AtomicReference<Throwable> failure) {
		Display display= PlatformUI.getWorkbench().getDisplay();
		Shell parent= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		display.asyncExec(() -> {
			try {
				new RefactoringWizardOpenOperation(wizard).run(parent, "Clean Up"); //$NON-NLS-1$
			} catch (Throwable throwable) {
				failure.set(throwable);
			}
		});
	}

	private static SWTBotShell waitForShell(SWTWorkbenchBot bot, AtomicReference<Throwable> failure) {
		AtomicReference<SWTBotShell> result= new AtomicReference<>();
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				if (failure.get() != null) {
					return true;
				}
				for (SWTBotShell candidate : bot.shells()) {
					if ("Clean Up".equals(candidate.getText())) { //$NON-NLS-1$
						result.set(candidate);
						return true;
					}
				}
				return false;
			}

			@Override
			public String getFailureMessage() {
				return "Cleanup preview shell did not open"; //$NON-NLS-1$
			}
		}, UI_TIMEOUT);
		if (failure.get() != null) {
			throw new AssertionError("Cleanup preview failed before opening", failure.get()); //$NON-NLS-1$
		}
		return result.get().activate();
	}

	private static SWTBotTree waitForTree(SWTBotShell shell) {
		AtomicReference<SWTBotTree> result= new AtomicReference<>();
		shell.bot().waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				SWTBotTree[] trees= shell.bot().trees();
				if (trees.length == 0 || trees[0].getAllItems().length == 0) {
					return false;
				}
				result.set(trees[0]);
				return true;
			}

			@Override
			public String getFailureMessage() {
				return "Cleanup preview tree did not become available"; //$NON-NLS-1$
			}
		}, UI_TIMEOUT);
		SWTBotAssert.assertVisible(result.get());
		return result.get();
	}

	private record SourceFile(String name, String content) {
	}

	private static final class PreviewOnlyCleanupWizard extends RefactoringWizard {
		PreviewOnlyCleanupWizard(CleanUpRefactoring refactoring) {
			super(refactoring, WIZARD_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
			setWindowTitle("Clean Up"); //$NON-NLS-1$
			setDefaultPageTitle("Clean Up"); //$NON-NLS-1$
			setForcePreviewReview(true);
		}

		@Override
		protected void addUserInputPages() {
			// The fixture supplies a deterministic in-memory profile and opens the real LTK preview directly.
		}
	}

	private static String combinedSource() {
		return """
				package preview;

				import org.eclipse.core.runtime.IProgressMonitor;
				import org.eclipse.core.runtime.SubProgressMonitor;
				import org.eclipse.jface.viewers.ViewerSorter;

				public class CombinedPreview extends ViewerSorter {
					public void work(IProgressMonitor monitor) {
						monitor.beginTask("Combined task", 100);
						IProgressMonitor child = new SubProgressMonitor(monitor, 50);
						child.worked(1);
					}
				}
				""";
	}

	private static String monitorSource(String className, String taskName) {
		return """
				package preview;

				import org.eclipse.core.runtime.IProgressMonitor;
				import org.eclipse.core.runtime.SubProgressMonitor;

				public class %s {
					public void work(IProgressMonitor monitor) {
						monitor.beginTask("%s", 100);
						IProgressMonitor child = new SubProgressMonitor(monitor, 50);
						child.worked(1);
					}
				}
				""".formatted(className, taskName);
	}
}
