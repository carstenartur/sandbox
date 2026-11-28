/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.nio.file.Files;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.ReplaceEdit;

import org.sandbox.jdt.internal.common.PdeManifestImportUpdater;
import org.sandbox.jdt.internal.corext.fix.multifile.PdeMixedSupportWiring;

/** Supplies the PDE import required by an atomic ExternalResource-to-Jupiter migration. */
public final class JUnitManifestChangeAdapter implements IAdapterFactory {
	private static final String PARALLEL_PACKAGE= "org.junit.jupiter.api.parallel"; //$NON-NLS-1$
	private static final String ISOLATED= PARALLEL_PACKAGE + ".Isolated"; //$NON-NLS-1$

	@Override
	public Class<?>[] getAdapterList() {
		return new Class<?>[] { Change.class };
	}

	@Override
	public <T> T getAdapter(Object adaptable, Class<T> adapterType) {
		if (adapterType != Change.class || !(adaptable instanceof Change candidate)) {
			return null;
		}
		try {
			// The optional host exposes a narrow public preview contract without a runtime dependency on a fork.
			List<?> ids= (List<?>) adaptable.getClass().getMethod("getCandidateIds").invoke(adaptable); //$NON-NLS-1$
			if (ids.stream().noneMatch(id -> id instanceof String name && name.startsWith("junit-coordinated-migration:external-resource:"))) { //$NON-NLS-1$
				return null;
			}
			Change[] children= (Change[]) adaptable.getClass().getMethod("getChanges").invoke(adaptable); //$NON-NLS-1$
			var monitor= new NullProgressMonitor();
			for (Change child : children) {
				if (!(child instanceof TextFileChange source)) {
					continue;
				}
				IFile affected= source.getFile();
				if (affected == null) {
					continue;
				}
				IFile manifest= affected.getProject().getFile("META-INF/MANIFEST.MF"); //$NON-NLS-1$
				if (manifest.exists() && source.getPreviewContent(monitor).contains(ISOLATED)
						&& !source.getCurrentContent(monitor).contains(ISOLATED)) {
					return adapterType.cast(requiredImport(manifest, monitor));
				}
			}
			return null;
		} catch (ReflectiveOperationException | CoreException exception) {
			throw new IllegalStateException("Could not plan the PDE dependency for " + candidate.getName(), exception); //$NON-NLS-1$
		}
	}

	private static Change requiredImport(IFile manifest, IProgressMonitor monitor) throws CoreException {
		var location= manifest.getLocation();
		if (manifest.isLinked(IResource.CHECK_ANCESTORS) || location == null || Files.isSymbolicLink(location.toFile().toPath())) {
			throw new CoreException(Status.error("Cannot update linked project descriptor " + manifest.getFullPath())); //$NON-NLS-1$
		}
		var change= new SharedImportChange(manifest);
		String original= change.getCurrentContent(monitor);
		String updated= PdeManifestImportUpdater.addImport(original, PARALLEL_PACKAGE);
		if (updated.equals(original)) {
			return null;
		}
		var project= manifest.getProject();
		if (project.hasNature("org.eclipse.pde.PluginNature") && Platform.getBundle("org.eclipse.pde.core") != null) { //$NON-NLS-1$ //$NON-NLS-2$
			var exporter= PdeMixedSupportWiring.jupiterExporter(JavaCore.create(project), PARALLEL_PACKAGE);
			updated= PdeManifestImportUpdater.addImport(original, PARALLEL_PACKAGE, exporter);
		}
		change.original= original;
		change.updated= updated;
		change.setEdit(new ReplaceEdit(0, original.length(), updated));
		change.setTextType("txt"); //$NON-NLS-1$
		return change;
	}

	/** Multiple selected candidates can require the same import. Only its first writer owns the Undo. */
	private static final class SharedImportChange extends TextFileChange {
		private String original;
		private String updated;

		SharedImportChange(IFile manifest) {
			super(manifest.getProjectRelativePath().toPortableString(), manifest);
		}

		@Override
		public Change perform(IProgressMonitor monitor) throws CoreException {
			String current= getCurrentContent(monitor);
			if (updated.equals(current)) {
				return new NullChange();
			}
			if (!original.equals(current)) {
				throw new CoreException(Status.error("Project descriptor changed after preview: " + getFile().getFullPath())); //$NON-NLS-1$
			}
			return super.perform(monitor);
		}
	}
}
