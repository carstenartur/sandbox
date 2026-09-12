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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

/**
 * Eclipse adapter for the common scenario lifecycle. It inventories every Java
 * file in the disposable test project, not only the files named by the scenario.
 * This driver intentionally neither provisions nor claims an upstream workspace.
 */
final class CleanupWorkbenchDriver {

	private CleanupWorkbenchDriver() {
	}

	static void run(CleanupScreenshotScenario scenario, PreviewContract expectedContract,
			CleanupScenarioRunner.ScenarioAction action) throws Exception {
		IProject project= ResourcesPlugin.getWorkspace().getRoot().getProject(scenario.projectName());
		IUndoManager undoManager= RefactoringCore.getUndoManager();
		CleanupScenarioRunner.run(scenario, expectedContract, new CleanupScenarioRunner.Workspace() {
			@Override
			public CleanupSourceSnapshot snapshot() throws Exception {
				if (!project.isAccessible()) {
					throw new AssertionError(scenario.id() + ": project is not open: " + scenario.projectName()); //$NON-NLS-1$
				}
				project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
				Map<String, byte[]> files= new TreeMap<>();
				project.accept(resource -> {
					if (resource instanceof IFile file && "java".equals(file.getFileExtension())) { //$NON-NLS-1$
						try (InputStream input= file.getContents()) {
							files.put(file.getProjectRelativePath().toPortableString(), input.readAllBytes());
						} catch (IOException exception) {
							throw new UncheckedIOException("Cannot snapshot " + file.getFullPath(), exception); //$NON-NLS-1$
						}
					}
					return true;
				});
				return new CleanupSourceSnapshot(files);
			}

			@Override
			public void clearUndoHistory() {
				undoManager.flush();
			}

			@Override
			public boolean canUndo() {
				return undoManager.anythingToUndo();
			}

			@Override
			public void undo() throws Exception {
				undoManager.performUndo(null, new NullProgressMonitor());
			}
		}, action);
	}
}
