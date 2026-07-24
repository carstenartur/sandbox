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
package org.sandbox.jdt.core.cleanupapp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/**
 * Resolves canonical input files to primary compilation units and groups them by
 * Java project without modifying workspace resources or source contents.
 */
final class CompilationUnitProjectGrouper {

	private static final String OUTSIDE_WORKSPACE= "OUTSIDE_WORKSPACE"; //$NON-NLS-1$
	private static final String NOT_COMPILATION_UNIT= "NOT_COMPILATION_UNIT"; //$NON-NLS-1$
	private static final String NO_JAVA_PROJECT= "NO_JAVA_PROJECT"; //$NON-NLS-1$
	private static final String RESOLUTION_FAILED= "RESOLUTION_FAILED"; //$NON-NLS-1$

	/** One requested file and its normalized primary compilation unit. */
	record Input(File file, ICompilationUnit compilationUnit) {
		Input {
			Objects.requireNonNull(file, "file"); //$NON-NLS-1$
			Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$
		}
	}

	/** One deterministic per-project input transaction candidate. */
	record ProjectGroup(IJavaProject project, List<Input> inputs) {
		ProjectGroup {
			Objects.requireNonNull(project, "project"); //$NON-NLS-1$
			inputs= List.copyOf(inputs);
		}

		List<ICompilationUnit> compilationUnits() {
			return inputs.stream().map(Input::compilationUnit).toList();
		}
	}

	/** Structured resolution problem for one requested file. */
	record Diagnostic(String input, String reasonCode, String message) {
		Diagnostic {
			input= input == null ? "" : input; //$NON-NLS-1$
			reasonCode= reasonCode == null ? RESOLUTION_FAILED : reasonCode;
			message= message == null ? "" : message; //$NON-NLS-1$
		}
	}

	/** Stable grouping result. */
	record Result(List<ProjectGroup> groups, List<Diagnostic> diagnostics) {
		Result {
			groups= List.copyOf(groups);
			diagnostics= List.copyOf(diagnostics);
		}
	}

	private static final class ResolutionException extends RuntimeException {
		private static final long serialVersionUID= 1L;
		private final String reasonCode;

		ResolutionException(String reasonCode, String message) {
			super(message);
			this.reasonCode= reasonCode;
		}
	}

	private final Function<File, ICompilationUnit> resolver;

	CompilationUnitProjectGrouper() {
		this(CompilationUnitProjectGrouper::resolveWorkspaceCompilationUnit);
	}

	CompilationUnitProjectGrouper(Function<File, ICompilationUnit> resolver) {
		this.resolver= Objects.requireNonNull(resolver, "resolver"); //$NON-NLS-1$
	}

	Result group(Collection<File> files) {
		if (files == null) {
			return new Result(List.of(), List.of(new Diagnostic("", RESOLUTION_FAILED, //$NON-NLS-1$
					"Input file collection is null"))); //$NON-NLS-1$
		}
		List<File> orderedFiles= files.stream().filter(Objects::nonNull)
				.sorted(Comparator.comparing(CompilationUnitProjectGrouper::stablePath)).toList();
		Map<String, ProjectAccumulator> projects= new LinkedHashMap<>();
		List<Diagnostic> diagnostics= new ArrayList<>();
		for (File file : orderedFiles) {
			try {
				ICompilationUnit resolved= resolver.apply(file);
				if (resolved == null || !resolved.exists()) {
					diagnostics.add(new Diagnostic(stablePath(file), NOT_COMPILATION_UNIT,
							"No existing compilation unit was resolved")); //$NON-NLS-1$
					continue;
				}
				ICompilationUnit primary= resolved.getPrimary();
				if (primary == null || !primary.exists()) {
					diagnostics.add(new Diagnostic(stablePath(file), NOT_COMPILATION_UNIT,
							"The resolved compilation unit has no existing primary unit")); //$NON-NLS-1$
					continue;
				}
				IJavaProject project= primary.getJavaProject();
				if (project == null || !project.exists()) {
					diagnostics.add(new Diagnostic(stablePath(file), NO_JAVA_PROJECT,
							"The resolved compilation unit is not in an existing Java project")); //$NON-NLS-1$
					continue;
				}
				String projectKey= stableProjectKey(project);
				projects.computeIfAbsent(projectKey, ignored -> new ProjectAccumulator(project))
						.add(file, primary);
			} catch (ResolutionException exception) {
				diagnostics.add(new Diagnostic(stablePath(file), exception.reasonCode, exception.getMessage()));
			} catch (RuntimeException exception) {
				diagnostics.add(new Diagnostic(stablePath(file), RESOLUTION_FAILED,
						exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
			}
		}

		List<ProjectGroup> groups= projects.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getValue().freeze()).toList();
		diagnostics.sort(Comparator.comparing(Diagnostic::input)
				.thenComparing(Diagnostic::reasonCode).thenComparing(Diagnostic::message));
		return new Result(groups, diagnostics);
	}

	private static final class ProjectAccumulator {
		private final IJavaProject project;
		private final Map<String, Input> inputsByUnitHandle= new LinkedHashMap<>();

		ProjectAccumulator(IJavaProject project) {
			this.project= project;
		}

		void add(File file, ICompilationUnit unit) {
			String handle= unit.getHandleIdentifier();
			if (handle == null || handle.isBlank()) {
				throw new ResolutionException(NOT_COMPILATION_UNIT,
						"The resolved compilation unit has no stable Java-model handle"); //$NON-NLS-1$
			}
			inputsByUnitHandle.putIfAbsent(handle, new Input(file, unit));
		}

		ProjectGroup freeze() {
			List<Input> inputs= inputsByUnitHandle.entrySet().stream().sorted(Map.Entry.comparingByKey())
					.map(Map.Entry::getValue).toList();
			return new ProjectGroup(project, inputs);
		}
	}

	private static ICompilationUnit resolveWorkspaceCompilationUnit(File file) {
		IPath path= Path.fromOSString(file.getAbsolutePath());
		IFile workspaceFile= ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(path);
		if (workspaceFile == null || !workspaceFile.exists()) {
			throw new ResolutionException(OUTSIDE_WORKSPACE,
					"File is outside the current Eclipse workspace"); //$NON-NLS-1$
		}
		ICompilationUnit unit= JavaCore.createCompilationUnitFrom(workspaceFile);
		if (unit == null) {
			throw new ResolutionException(NOT_COMPILATION_UNIT,
					"Workspace file is not a Java compilation unit"); //$NON-NLS-1$
		}
		return unit;
	}

	private static String stableProjectKey(IJavaProject project) {
		String handle= project.getHandleIdentifier();
		return handle == null || handle.isBlank() ? project.getElementName() : handle;
	}

	private static String stablePath(File file) {
		return file.toPath().toAbsolutePath().normalize().toString().replace(File.separatorChar, '/');
	}
}
