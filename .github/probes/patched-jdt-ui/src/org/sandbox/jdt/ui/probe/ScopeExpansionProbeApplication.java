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
package org.sandbox.jdt.ui.probe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;

import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

/** Executes the installed patched JDT UI scope-expansion path in a real product. */
public final class ScopeExpansionProbeApplication implements IApplication {

	private static final String PROJECT_NAME= "PatchedJdtUiScopeProbe"; //$NON-NLS-1$
	private static final String PACKAGE_NAME= "probe"; //$NON-NLS-1$
	private static final String MARKER= "// patched scope probe\n"; //$NON-NLS-1$
	private static final String FIRST_SOURCE= "package probe;\npublic class First {}\n"; //$NON-NLS-1$
	private static final String SECOND_SOURCE= "package probe;\npublic class Second {}\n"; //$NON-NLS-1$
	private static final String PASS_MARKER= "PATCHED_JDT_UI_SCOPE_PROBE_PASS"; //$NON-NLS-1$
	private static final String REPORT_ENV= "SANDBOX_PATCHED_JDT_UI_PROBE_REPORT"; //$NON-NLS-1$

	private record ProbeResult(int targetCount, int plannedCount, int expansionInvocations,
			int previewCount, int appliedCount, int restoredCount) {
	}

	@Override
	public Object start(IApplicationContext context) throws Exception {
		IProgressMonitor monitor= new NullProgressMonitor();
		IProject project= ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
		ProbeResult result= null;
		Throwable failure= null;
		try {
			deleteIfPresent(project, monitor);
			IJavaProject javaProject= createJavaProject(project, monitor);
			IPackageFragment pack= javaProject.getPackageFragmentRoot(project.getFolder("src")) //$NON-NLS-1$
					.createPackageFragment(PACKAGE_NAME, true, monitor);
			ICompilationUnit first= pack.createCompilationUnit("First.java", FIRST_SOURCE, false, monitor); //$NON-NLS-1$
			ICompilationUnit second= pack.createCompilationUnit("Second.java", SECOND_SOURCE, false, monitor); //$NON-NLS-1$

			ScopeExpandingCleanUp cleanUp= new ScopeExpandingCleanUp(second);
			cleanUp.setOptions(new CleanUpOptions());
			CleanUpRefactoring refactoring= new CleanUpRefactoring("Patched JDT UI runtime scope probe"); //$NON-NLS-1$
			refactoring.addCompilationUnit(first);
			refactoring.addCleanUp(cleanUp);

			RefactoringStatus conditions= refactoring.checkAllConditions(monitor);
			require(!conditions.hasError(), "Cleanup conditions failed: " + conditions); //$NON-NLS-1$
			int targetCount= refactoring.getCleanUpTargetsSize();
			require(targetCount == 2, "Expected two cleanup targets after expansion, found " + targetCount); //$NON-NLS-1$

			Set<String> plannedHandles= handles(cleanUp.plannedUnits());
			Set<String> expectedHandles= handles(List.of(first, second));
			require(plannedHandles.equals(expectedHandles),
					"Preconditions did not receive the complete expanded scope: " + plannedHandles); //$NON-NLS-1$
			require(cleanUp.expansionInvocations() >= 2,
					"Scope expansion did not run to a fixed point: " + cleanUp.expansionInvocations()); //$NON-NLS-1$

			Change change= refactoring.createChange(monitor);
			require(change instanceof CompositeChange, "Expected a composite cleanup change"); //$NON-NLS-1$
			CompositeChange composite= (CompositeChange) change;
			Change[] children= composite.getChildren();
			require(children.length == 2, "Expected two preview changes, found " + children.length); //$NON-NLS-1$
			Set<String> previews= new LinkedHashSet<>();
			for (Change child : children) {
				require(child instanceof TextEditBasedChange,
						"Expected a text-edit cleanup child, found " + child.getClass().getName()); //$NON-NLS-1$
				previews.add(((TextEditBasedChange) child).getPreviewContent(monitor));
			}
			Set<String> expectedPreviews= Set.of(MARKER + FIRST_SOURCE, MARKER + SECOND_SOURCE);
			require(previews.equals(expectedPreviews), "Preview did not cover both source files: " + previews); //$NON-NLS-1$

			validateChange(change, monitor, "apply"); //$NON-NLS-1$
			Change undo= change.perform(monitor);
			require(undo != null, "Cleanup did not produce an undo change"); //$NON-NLS-1$
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			int appliedCount= changedCount(first, second);
			require(appliedCount == 2, "Cleanup did not modify both files: " + appliedCount); //$NON-NLS-1$

			validateChange(undo, monitor, "undo"); //$NON-NLS-1$
			Change redo= undo.perform(monitor);
			require(redo != null, "Undo did not produce a redo change"); //$NON-NLS-1$
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			int restoredCount= restoredCount(first, second);
			require(restoredCount == 2, "Undo did not restore both original files: " + restoredCount); //$NON-NLS-1$

			result= new ProbeResult(targetCount, plannedHandles.size(), cleanUp.expansionInvocations(),
					previews.size(), appliedCount, restoredCount);
			writeReport("PASS", result, ""); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println(PASS_MARKER);
			return IApplication.EXIT_OK;
		} catch (Throwable throwable) {
			failure= throwable;
			try {
				writeReport("FAIL", result, failureMessage(throwable)); //$NON-NLS-1$
			} catch (Throwable reportingFailure) {
				throwable.addSuppressed(reportingFailure);
			}
			if (throwable instanceof Exception exception) {
				throw exception;
			}
			if (throwable instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(throwable);
		} finally {
			try {
				deleteIfPresent(project, monitor);
			} catch (CoreException cleanupFailure) {
				if (failure != null) {
					failure.addSuppressed(cleanupFailure);
				} else {
					throw cleanupFailure;
				}
			}
		}
	}

	@Override
	public void stop() {
		// The probe is synchronous and owns no background work.
	}

	private static IJavaProject createJavaProject(IProject project, IProgressMonitor monitor) throws CoreException {
		project.create(monitor);
		project.open(monitor);
		IProjectDescription description= project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, monitor);

		IFolder source= project.getFolder("src"); //$NON-NLS-1$
		IFolder output= project.getFolder("bin"); //$NON-NLS-1$
		source.create(true, true, monitor);
		output.create(true, true, monitor);
		IJavaProject javaProject= JavaCore.create(project);
		IClasspathEntry sourceEntry= JavaCore.newSourceEntry(source.getFullPath());
		javaProject.setRawClasspath(new IClasspathEntry[] { sourceEntry }, output.getFullPath(), monitor);
		return javaProject;
	}

	private static void validateChange(Change change, IProgressMonitor monitor, String phase) throws CoreException {
		change.initializeValidationData(monitor);
		RefactoringStatus validity= change.isValid(monitor);
		require(!validity.hasError(), "Change is invalid before " + phase + ": " + validity); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static int changedCount(ICompilationUnit first, ICompilationUnit second) throws IOException {
		int count= 0;
		if ((MARKER + FIRST_SOURCE).equals(read(first))) {
			count++;
		}
		if ((MARKER + SECOND_SOURCE).equals(read(second))) {
			count++;
		}
		return count;
	}

	private static int restoredCount(ICompilationUnit first, ICompilationUnit second) throws IOException {
		int count= 0;
		if (FIRST_SOURCE.equals(read(first))) {
			count++;
		}
		if (SECOND_SOURCE.equals(read(second))) {
			count++;
		}
		return count;
	}

	private static String read(ICompilationUnit unit) throws IOException {
		IResource resource= unit.getResource();
		require(resource instanceof IFile, "Compilation unit has no file resource: " + unit.getElementName()); //$NON-NLS-1$
		IFile file= (IFile) resource;
		require(file.getLocation() != null, "Compilation-unit file has no local location: " + unit.getElementName()); //$NON-NLS-1$
		return Files.readString(file.getLocation().toFile().toPath(), StandardCharsets.UTF_8);
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		Set<String> result= new LinkedHashSet<>();
		for (ICompilationUnit unit : units) {
			result.add(unit.getPrimary().getHandleIdentifier());
		}
		return result;
	}

	private static void deleteIfPresent(IProject project, IProgressMonitor monitor) throws CoreException {
		if (project.exists()) {
			project.delete(true, true, monitor);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private static String failureMessage(Throwable failure) {
		String message= failure.getMessage();
		return failure.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void writeReport(String outcome, ProbeResult result, String message) throws IOException {
		String reportPath= System.getenv(REPORT_ENV);
		if (reportPath == null || reportPath.isBlank()) {
			return;
		}
		Path path= Path.of(reportPath).toAbsolutePath().normalize();
		Path parent= path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		int targetCount= result == null ? -1 : result.targetCount();
		int plannedCount= result == null ? -1 : result.plannedCount();
		int expansionInvocations= result == null ? -1 : result.expansionInvocations();
		int previewCount= result == null ? -1 : result.previewCount();
		int appliedCount= result == null ? -1 : result.appliedCount();
		int restoredCount= result == null ? -1 : result.restoredCount();
		String json= "{\n" //$NON-NLS-1$
				+ "  \"schemaVersion\": 1,\n" //$NON-NLS-1$
				+ "  \"result\": \"" + jsonEscape(outcome) + "\",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"targetCount\": " + targetCount + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"plannedCount\": " + plannedCount + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"expansionInvocations\": " + expansionInvocations + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"previewCount\": " + previewCount + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"appliedCount\": " + appliedCount + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"restoredCount\": " + restoredCount + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"message\": \"" + jsonEscape(message) + "\"\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "}\n"; //$NON-NLS-1$
		Files.writeString(path, json, StandardCharsets.UTF_8);
	}

	private static String jsonEscape(String value) {
		return value.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\"", "\\\"") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\n", "\\n") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\r", "\\r") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\t", "\\t"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Cleanup whose public optional method is discovered by the patched JDT UI. */
	public static final class ScopeExpandingCleanUp implements ICleanUp {
		private final ICompilationUnit relatedUnit;
		private List<ICompilationUnit> plannedUnits= List.of();
		private int expansionInvocations;

		ScopeExpandingCleanUp(ICompilationUnit relatedUnit) {
			this.relatedUnit= relatedUnit;
		}

		@Override
		public void setOptions(CleanUpOptions options) {
			// This deterministic probe has no configurable options.
		}

		@Override
		public String[] getStepDescriptions() {
			return new String[] { "Insert patched JDT UI runtime probe marker" }; //$NON-NLS-1$
		}

		@Override
		public CleanUpRequirements getRequirements() {
			return new CleanUpRequirements(false, false, false, null);
		}

		@Override
		public RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] compilationUnits,
				IProgressMonitor monitor) {
			plannedUnits= List.of(compilationUnits.clone());
			return new RefactoringStatus();
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) {
			return progressMonitor -> {
				CompilationUnitChange change= new CompilationUnitChange("Patched JDT UI runtime probe", //$NON-NLS-1$
						context.getCompilationUnit());
				MultiTextEdit edit= new MultiTextEdit();
				edit.addChild(new InsertEdit(0, MARKER));
				change.setEdit(edit);
				return change;
			};
		}

		@Override
		public RefactoringStatus checkPostConditions(IProgressMonitor monitor) {
			return new RefactoringStatus();
		}

		/** Optional method intentionally discovered reflectively by the patched host. */
		public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
				Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
			expansionInvocations++;
			return List.of(relatedUnit);
		}

		List<ICompilationUnit> plannedUnits() {
			return plannedUnits;
		}

		int expansionInvocations() {
			return expansionInvocations;
		}
	}
}
