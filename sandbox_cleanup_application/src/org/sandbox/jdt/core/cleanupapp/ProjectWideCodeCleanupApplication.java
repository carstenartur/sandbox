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
package org.sandbox.jdt.core.cleanupapp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpDiagnosticsProvider;

/**
 * Executes one cleanup refactoring over every source compilation unit of one
 * already imported Eclipse Java project.
 *
 * <p>This application exists for semantic migration QA and documentation. In
 * contrast to the legacy file-oriented application it preserves the JDT cleanup
 * lifecycle: one cleanup instance sees the complete project scope in
 * {@code checkPreConditions}, one {@link Change} is previewed/applied, and a
 * check run restores every source file byte-for-byte.</p>
 */
public final class ProjectWideCodeCleanupApplication implements IApplication {

	private static final int EXIT_OK= 0;
	private static final int EXIT_ERROR= 1;
	private static final int EXIT_CHANGES= 2;

	private enum Mode {
		CHECK,
		APPLY
	}

	private record Arguments(String projectName, Path configuration, Path report, Path patch, Mode mode) {
	}

	private record SourceSnapshot(ICompilationUnit unit, IFile file, Path path, String relativePath,
			byte[] before) {
	}

	private record ChangedSource(SourceSnapshot source, byte[] after) {
	}

	@Override
	public Object start(IApplicationContext context) {
		Instant started= Instant.now();
		List<String> errors= new ArrayList<>();
		List<String> statusMessages= new ArrayList<>();
		List<String> planningDiagnostics= new ArrayList<>();
		List<ChangedSource> changed= new ArrayList<>();
		Arguments arguments;
		try {
			arguments= parseArguments((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
		} catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			printUsage();
			return Integer.valueOf(EXIT_ERROR);
		}
		if (arguments == null) {
			printUsage();
			return Integer.valueOf(EXIT_OK);
		}

		List<SourceSnapshot> sources= List.of();
		Map<String, String> options= Map.of();
		try {
			options= readOptions(arguments.configuration());
			IProject project= requireProject(arguments.projectName());
			IJavaProject javaProject= JavaCore.create(project);
			if (!javaProject.exists()) {
				throw new IllegalArgumentException("Workspace project is not a Java project: " //$NON-NLS-1$
						+ arguments.projectName());
			}
			sources= snapshots(javaProject);
			if (sources.isEmpty()) {
				throw new IllegalArgumentException("Java project contains no source compilation units: " //$NON-NLS-1$
						+ arguments.projectName());
			}

			IProgressMonitor monitor= new NullProgressMonitor();
			CleanUpRefactoring refactoring= new CleanUpRefactoring();
			for (SourceSnapshot source : sources) {
				refactoring.addCompilationUnit(source.unit());
			}

			ICleanUp[] cleanUps= JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps();
			CleanUpOptions cleanUpOptions= new CleanUpOptions();
			options.forEach(cleanUpOptions::setOption);
			for (ICleanUp cleanUp : cleanUps) {
				cleanUp.setOptions(cleanUpOptions);
				refactoring.addCleanUp(cleanUp);
			}

			RefactoringStatus status= refactoring.checkAllConditions(monitor);
			for (RefactoringStatusEntry entry : status.getEntries()) {
				statusMessages.add(entry.getMessage());
			}
			for (ICleanUp cleanUp : cleanUps) {
				if (cleanUp instanceof IMultiFileCleanUpDiagnosticsProvider provider) {
					String json= provider.getLastPlanningDiagnosticsJson(javaProject);
					if (json != null && !json.isBlank()) {
						planningDiagnostics.add(json);
					}
				}
			}
			if (status.hasFatalError()) {
				throw new IllegalStateException("Cleanup preconditions failed: " //$NON-NLS-1$
						+ status.getMessageMatchingSeverity(RefactoringStatus.FATAL));
			}

			Change change= refactoring.createChange(monitor);
			if (change != null) {
				if (arguments.mode() == Mode.CHECK) {
					try {
						change.perform(monitor);
						refresh(sources, monitor);
						changed= changedSources(sources);
					} finally {
						restore(sources, monitor, errors);
					}
				} else {
					change.perform(monitor);
					refresh(sources, monitor);
					changed= changedSources(sources);
				}
			}
		} catch (CoreException | IOException | RuntimeException e) {
			String message= e.getMessage();
			errors.add(message == null || message.isBlank() ? e.getClass().getName() : message);
			System.err.println(errors.get(errors.size() - 1));
		}

		if (arguments.patch() != null && !changed.isEmpty()) {
			try {
				writePatch(arguments.patch(), changed);
			} catch (IOException e) {
				errors.add("Cannot write cleanup patch: " + e.getMessage()); //$NON-NLS-1$
			}
		}
		Instant ended= Instant.now();
		try {
			writeReport(arguments.report(), arguments, started, ended, options, sources, changed,
					statusMessages, planningDiagnostics, errors);
		} catch (IOException e) {
			errors.add("Cannot write cleanup report: " + e.getMessage()); //$NON-NLS-1$
		}

		if (!errors.isEmpty()) {
			return Integer.valueOf(EXIT_ERROR);
		}
		if (arguments.mode() == Mode.CHECK && !changed.isEmpty()) {
			return Integer.valueOf(EXIT_CHANGES);
		}
		return Integer.valueOf(EXIT_OK);
	}

	private static Arguments parseArguments(String[] values) {
		if (values == null) {
			throw new IllegalArgumentException("Application arguments are unavailable."); //$NON-NLS-1$
		}
		String project= null;
		Path configuration= null;
		Path report= null;
		Path patch= null;
		Mode mode= Mode.CHECK;
		for (int index= 0; index < values.length; index++) {
			String value= values[index];
			switch (value) {
			case "--help", "-help" -> { //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
			case "--project" -> project= requiredValue(values, ++index, value); //$NON-NLS-1$
			case "--config", "-config" -> configuration= Path.of(requiredValue(values, ++index, value)); //$NON-NLS-1$ //$NON-NLS-2$
			case "--report" -> report= Path.of(requiredValue(values, ++index, value)); //$NON-NLS-1$
			case "--patch" -> patch= Path.of(requiredValue(values, ++index, value)); //$NON-NLS-1$
			case "--mode" -> { //$NON-NLS-1$
				String requested= requiredValue(values, ++index, value);
				try {
					mode= Mode.valueOf(requested.toUpperCase(java.util.Locale.ROOT));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("Unsupported project cleanup mode: " + requested, e); //$NON-NLS-1$
				}
			}
			default -> {
				if (!value.isBlank() && !"-pdelaunch".equals(value)) { //$NON-NLS-1$
					throw new IllegalArgumentException("Unknown option: " + value); //$NON-NLS-1$
				}
			}
			}
		}
		if (project == null || project.isBlank()) {
			throw new IllegalArgumentException("--project is required."); //$NON-NLS-1$
		}
		if (configuration == null) {
			throw new IllegalArgumentException("--config is required."); //$NON-NLS-1$
		}
		if (report == null) {
			throw new IllegalArgumentException("--report is required."); //$NON-NLS-1$
		}
		return new Arguments(project, configuration.toAbsolutePath().normalize(),
				report.toAbsolutePath().normalize(), patch == null ? null : patch.toAbsolutePath().normalize(), mode);
	}

	private static String requiredValue(String[] values, int index, String option) {
		if (index >= values.length || values[index].isBlank()) {
			throw new IllegalArgumentException(option + " requires a value."); //$NON-NLS-1$
		}
		return values[index];
	}

	private static Map<String, String> readOptions(Path path) throws IOException {
		if (!Files.isRegularFile(path)) {
			throw new IOException("Cleanup configuration does not exist: " + path); //$NON-NLS-1$
		}
		Properties properties= new Properties();
		try (var reader= Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		Map<String, String> result= new LinkedHashMap<>();
		properties.stringPropertyNames().stream().sorted()
				.forEach(name -> result.put(name, properties.getProperty(name)));
		return result;
	}

	private static IProject requireProject(String name) throws CoreException {
		IProject project= ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (!project.exists()) {
			throw new IllegalArgumentException("Workspace project does not exist: " + name); //$NON-NLS-1$
		}
		if (!project.isOpen()) {
			project.open(new NullProgressMonitor());
		}
		project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		return project;
	}

	private static List<SourceSnapshot> snapshots(IJavaProject project) throws CoreException, IOException {
		List<ICompilationUnit> units= new ArrayList<>();
		for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
			if (root.getKind() != IPackageFragmentRoot.K_SOURCE) {
				continue;
			}
			for (IJavaElement child : root.getChildren()) {
				if (child instanceof IPackageFragment fragment) {
					units.addAll(Arrays.asList(fragment.getCompilationUnits()));
				}
			}
		}
		units.sort(Comparator.comparing(unit -> unit.getPath().toPortableString()));
		List<SourceSnapshot> result= new ArrayList<>();
		for (ICompilationUnit unit : units) {
			if (!(unit.getResource() instanceof IFile file) || file.getLocation() == null) {
				continue;
			}
			Path path= file.getLocation().toFile().toPath().toAbsolutePath().normalize();
			result.add(new SourceSnapshot(unit, file, path,
					file.getProjectRelativePath().toPortableString(), Files.readAllBytes(path)));
		}
		return List.copyOf(result);
	}

	private static void refresh(List<SourceSnapshot> sources, IProgressMonitor monitor) throws CoreException {
		for (SourceSnapshot source : sources) {
			source.file().refreshLocal(IResource.DEPTH_ZERO, monitor);
		}
	}

	private static List<ChangedSource> changedSources(List<SourceSnapshot> sources) throws IOException {
		List<ChangedSource> result= new ArrayList<>();
		for (SourceSnapshot source : sources) {
			byte[] after= Files.readAllBytes(source.path());
			if (!Arrays.equals(source.before(), after)) {
				result.add(new ChangedSource(source, after));
			}
		}
		return List.copyOf(result);
	}

	private static void restore(List<SourceSnapshot> sources, IProgressMonitor monitor, List<String> errors) {
		for (SourceSnapshot source : sources) {
			try {
				Files.write(source.path(), source.before());
				source.file().refreshLocal(IResource.DEPTH_ZERO, monitor);
			} catch (IOException | CoreException e) {
				errors.add("Cannot restore " + source.relativePath() + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	private static void writePatch(Path path, List<ChangedSource> changed) throws IOException {
		createParent(path);
		StringBuilder patch= new StringBuilder();
		for (ChangedSource source : changed) {
			String relative= source.source().relativePath();
			String before= new String(source.source().before(), StandardCharsets.UTF_8);
			String after= new String(source.after(), StandardCharsets.UTF_8);
			patch.append("--- a/").append(relative).append('\n'); //$NON-NLS-1$
			patch.append("+++ b/").append(relative).append('\n'); //$NON-NLS-1$
			String[] beforeLines= before.split("\\R", -1); //$NON-NLS-1$
			String[] afterLines= after.split("\\R", -1); //$NON-NLS-1$
			patch.append("@@ -1,").append(beforeLines.length).append(" +1,") //$NON-NLS-1$ //$NON-NLS-2$
					.append(afterLines.length).append(" @@\n"); //$NON-NLS-1$
			for (String line : beforeLines) {
				patch.append('-').append(line).append('\n');
			}
			for (String line : afterLines) {
				patch.append('+').append(line).append('\n');
			}
		}
		Files.writeString(path, patch.toString(), StandardCharsets.UTF_8);
	}

	private static void writeReport(Path path, Arguments arguments, Instant started, Instant ended,
			Map<String, String> options, List<SourceSnapshot> sources, List<ChangedSource> changed,
			List<String> statusMessages, List<String> planningDiagnostics, List<String> errors) throws IOException {
		createParent(path);
		StringBuilder json= new StringBuilder(2048);
		json.append("{\n"); //$NON-NLS-1$
		property(json, "schemaVersion", "1", 1).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
		property(json, "tool", "sandbox-project-cleanup", 1).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
		property(json, "mode", arguments.mode().name().toLowerCase(java.util.Locale.ROOT), 1).append(",\n"); //$NON-NLS-1$
		property(json, "project", arguments.projectName(), 1).append(",\n"); //$NON-NLS-1$
		property(json, "startTime", started.toString(), 1).append(",\n"); //$NON-NLS-1$
		property(json, "endTime", ended.toString(), 1).append(",\n"); //$NON-NLS-1$
		number(json, "durationMs", ended.toEpochMilli() - started.toEpochMilli(), 1).append(",\n"); //$NON-NLS-1$
		number(json, "filesProcessed", sources.size(), 1).append(",\n"); //$NON-NLS-1$
		number(json, "filesChanged", changed.size(), 1).append(",\n"); //$NON-NLS-1$
		array(json, "changedFiles", changed.stream().map(item -> item.source().relativePath()).toList(), 1) //$NON-NLS-1$
				.append(",\n");
		json.append("  \"cleanupOptions\": {"); //$NON-NLS-1$
		int optionIndex= 0;
		for (Map.Entry<String, String> entry : options.entrySet()) {
			if (optionIndex++ > 0) {
				json.append(',');
			}
			json.append("\n    \"").append(escape(entry.getKey())).append("\": \"") //$NON-NLS-1$ //$NON-NLS-2$
					.append(escape(entry.getValue())).append('"');
		}
		if (!options.isEmpty()) {
			json.append('\n');
		}
		json.append("  },\n"); //$NON-NLS-1$
		array(json, "refactoringStatus", statusMessages, 1).append(",\n"); //$NON-NLS-1$
		json.append("  \"planningDiagnostics\": ["); //$NON-NLS-1$
		for (int index= 0; index < planningDiagnostics.size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append('\n').append("    ").append(planningDiagnostics.get(index)); //$NON-NLS-1$
		}
		if (!planningDiagnostics.isEmpty()) {
			json.append('\n').append("  "); //$NON-NLS-1$
		}
		json.append("],\n"); //$NON-NLS-1$
		number(json, "errorCount", errors.size(), 1).append(",\n"); //$NON-NLS-1$
		array(json, "errors", errors, 1).append('\n'); //$NON-NLS-1$
		json.append("}\n"); //$NON-NLS-1$
		Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
	}

	private static StringBuilder property(StringBuilder json, String name, String value, int indent) {
		return json.append("  ".repeat(indent)).append('"').append(escape(name)).append("\": \"") //$NON-NLS-1$ //$NON-NLS-2$
				.append(escape(value)).append('"');
	}

	private static StringBuilder number(StringBuilder json, String name, long value, int indent) {
		return json.append("  ".repeat(indent)).append('"').append(escape(name)).append("\": ").append(value); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static StringBuilder array(StringBuilder json, String name, List<String> values, int indent) {
		json.append("  ".repeat(indent)).append('"').append(escape(name)).append("\": ["); //$NON-NLS-1$ //$NON-NLS-2$
		for (int index= 0; index < values.size(); index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append("\n").append("  ".repeat(indent + 1)).append('"') //$NON-NLS-1$
					.append(escape(values.get(index))).append('"');
		}
		if (!values.isEmpty()) {
			json.append('\n').append("  ".repeat(indent)); //$NON-NLS-1$
		}
		return json.append(']');
	}

	private static String escape(String value) {
		StringBuilder escaped= new StringBuilder(value.length() + 16);
		for (int index= 0; index < value.length(); index++) {
			char character= value.charAt(index);
			switch (character) {
			case '"' -> escaped.append("\\\""); //$NON-NLS-1$
			case '\\' -> escaped.append("\\\\"); //$NON-NLS-1$
			case '\b' -> escaped.append("\\b"); //$NON-NLS-1$
			case '\f' -> escaped.append("\\f"); //$NON-NLS-1$
			case '\n' -> escaped.append("\\n"); //$NON-NLS-1$
			case '\r' -> escaped.append("\\r"); //$NON-NLS-1$
			case '\t' -> escaped.append("\\t"); //$NON-NLS-1$
			default -> {
				if (character < 0x20) {
					escaped.append(String.format("\\u%04x", Integer.valueOf(character))); //$NON-NLS-1$
				} else {
					escaped.append(character);
				}
			}
			}
		}
		return escaped.toString();
	}

	private static void createParent(Path path) throws IOException {
		Path parent= path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	private static void printUsage() {
		System.out.println("""
				Usage: eclipse -nosplash -data <workspace>
				  -application sandbox_cleanup_application.org.sandbox.jdt.core.ProjectWideJavaCleanup
				  --project <workspace-project>
				  --config <cleanup.properties>
				  --report <result.json>
				  [--patch <changes.patch>]
				  [--mode check|apply]
				"""); //$NON-NLS-1$
	}

	@Override
	public void stop() {
		// Nothing to stop.
	}
}
