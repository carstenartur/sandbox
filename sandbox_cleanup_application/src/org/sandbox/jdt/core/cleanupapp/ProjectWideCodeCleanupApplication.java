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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
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
import java.util.Set;
import java.util.jar.Manifest;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
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
import org.sandbox.jdt.cleanup.multifile.api.LineDelimiterPreserver;
import org.sandbox.jdt.triggerpattern.git.UnifiedDiffFormatter;

import org.sandbox.jdt.internal.common.PdeManifestImportUpdater;

/**
 * Executes one cleanup refactoring over every source compilation unit of one
 * already imported Eclipse Java project.
 *
 * <p>This application exists for semantic migration QA and documentation. In
 * contrast to the legacy file-oriented application it preserves the JDT cleanup
 * lifecycle: one cleanup instance sees the complete project scope in
 * {@code checkPreConditions}, one {@link Change} is previewed/applied, check
 * runs restore every tracked file byte-for-byte, and incomplete apply runs roll
 * back the source and project-resource snapshots.</p>
 */
public final class ProjectWideCodeCleanupApplication implements IApplication {

	private static final int EXIT_OK= 0;
	private static final int EXIT_ERROR= 1;
	private static final int EXIT_CHANGES= 2;

	private static final String MAVEN_POM= "pom.xml"; //$NON-NLS-1$
	private static final String PDE_MANIFEST= "META-INF/MANIFEST.MF"; //$NON-NLS-1$
	private static final String JUNIT_ISOLATED_TYPE=
			"org.junit.jupiter.api.parallel.Isolated"; //$NON-NLS-1$
	private static final String JUNIT_PARALLEL_PACKAGE=
			"org.junit.jupiter.api.parallel"; //$NON-NLS-1$

	private enum Mode {
		CHECK,
		APPLY
	}

	private record Arguments(String projectName, Path projectLocation, Path configuration,
			Path report, Path patch, Mode mode, List<String> pdeDeployments, int batchMaxFiles, String batch, String receipt) {
	}

	record SourceSnapshot(ICompilationUnit unit, IFile file, Path path, String relativePath,
			byte[] before) {
	}

	record ResourceSnapshot(IFile file, Path path, String relativePath, byte[] before) {
	}

	record MetadataPlan(Map<ResourceSnapshot, byte[]> changes, boolean retiresVintage) {
	}

	record ChangedFile(String relativePath, byte[] before, byte[] after) {
	}

	@Override
	public Object start(IApplicationContext context) {
		Instant started= Instant.now();
		List<String> errors= new ArrayList<>();
		List<String> statusMessages= new ArrayList<>();
		List<String> planningDiagnostics= new ArrayList<>();
		List<ChangedFile> changed= new ArrayList<>();
		List<MigrationBatches.Batch> batches= List.of();
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
		List<ResourceSnapshot> resources= List.of();
		List<ResourceSnapshot> writtenResources= new ArrayList<>();
		Map<String, String> options= Map.of();
		try {
			options= readOptions(arguments.configuration());
			IProject project= requireProject(arguments.projectName(), arguments.projectLocation());
			IJavaProject javaProject= JavaCore.create(project);
			if (!javaProject.exists()) {
				throw new IllegalArgumentException("Workspace project is not a Java project: " //$NON-NLS-1$
						+ arguments.projectName());
			}
			sources= snapshots(javaProject);
			resources= resourceSnapshots(project, arguments.pdeDeployments());
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

			var observer= new MigrationBatchDependencies.CandidateObserver(cleanUps);
			if (arguments.batchMaxFiles() > 0) {
				refactoring.addCleanUp(observer);
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
			if (arguments.batchMaxFiles() > 0 && change != null) {
				var preview= MigrationBatches.plan(javaProject, change, sources, resources, observer.groups(),
						arguments.batchMaxFiles(), options, monitor);
				batches= preview.batches();
				changed= preview.select(arguments.batch(), arguments.receipt(), arguments.mode() == Mode.APPLY, monitor);
			} else if (change != null) {
				boolean restoreAfterExecution= arguments.mode() == Mode.CHECK;
				boolean executionCompleted= false;
				try {
					change.perform(monitor);
					preserveOriginalLineDelimiters(sources);
					updateProjectMetadata(sources, resources, writtenResources, javaProject.getOptions(true));
					refresh(sources, resources, monitor);
					changed= changedFiles(sources, resources);
					executionCompleted= true;
				} finally {
					if (restoreAfterExecution || !executionCompleted) {
						restore(sources, writtenResources, monitor, errors);
					}
				}
			}
		} catch (CoreException | IOException | RuntimeException e) {
			errors.add(describeException(e));
			e.printStackTrace(System.err);
		}

		if (arguments.patch() != null) {
			try {
				writePatch(arguments.patch(), changed);
			} catch (IOException e) {
				errors.add("Cannot write cleanup patch: " + e.getMessage()); //$NON-NLS-1$
			}
		}
		Instant ended= Instant.now();
		try {
			writeReport(arguments.report(), arguments, started, ended, options, sources, changed,
					statusMessages, planningDiagnostics, batches, errors);
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
		Path projectLocation= null;
		Path configuration= null;
		Path report= null;
		Path patch= null;
		Mode mode= Mode.CHECK;
		int maxFiles= 0;
		String batch= null;
		String receipt= null;
		List<String> pdeDeployments= new ArrayList<>();
		for (int index= 0; index < values.length; index++) {
			String value= values[index];
			switch (value) {
			case "--help", "-help" -> { //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
			case "--project" -> project= requiredValue(values, ++index, value); //$NON-NLS-1$
			case "--project-location" -> projectLocation= Path.of(requiredValue(values, ++index, value)); //$NON-NLS-1$
			case "--config", "-config" -> configuration= Path.of(requiredValue(values, ++index, value)); //$NON-NLS-1$ //$NON-NLS-2$
			case "--pde-deployment" -> pdeDeployments.add(requiredValue(values, ++index, value)); //$NON-NLS-1$
			case "--report" -> report= Path.of(requiredValue(values, ++index, value)); //$NON-NLS-1$
			case "--batch-max-files" -> {
				maxFiles= Integer.parseInt(requiredValue(values, ++index, value));
				if (maxFiles < 1) {
					throw new IllegalArgumentException("--batch-max-files must be positive"); //$NON-NLS-1$
				}
			}
			case "--batch" -> batch= requiredValue(values, ++index, value); //$NON-NLS-1$
			case "--batch-receipt" -> receipt= requiredValue(values, ++index, value); //$NON-NLS-1$
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
		if ((batch != null || receipt != null) && maxFiles == 0 || receipt != null && batch == null) {
			throw new IllegalArgumentException("Batch selection requires --batch-max-files and --batch"); //$NON-NLS-1$
		}
		if (maxFiles > 0 && mode == Mode.APPLY && (batch == null || receipt == null)) {
			throw new IllegalArgumentException("Preview the batches, then supply --batch and --batch-receipt for apply"); //$NON-NLS-1$
		}
		return new Arguments(project,
				projectLocation == null ? null : projectLocation.toAbsolutePath().normalize(),
				configuration.toAbsolutePath().normalize(), report.toAbsolutePath().normalize(),
				patch == null ? null : patch.toAbsolutePath().normalize(), mode, List.copyOf(pdeDeployments), maxFiles, batch, receipt);
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

	private static IProject requireProject(String name, Path requestedLocation) throws CoreException, IOException {
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		IProject project= root.getProject(name);
		IProgressMonitor monitor= new NullProgressMonitor();
		if (!project.exists()) {
			if (requestedLocation == null) {
				throw new IllegalArgumentException("Workspace project does not exist and --project-location was not supplied: " //$NON-NLS-1$
						+ name);
			}
			Path canonicalLocation= requestedLocation.toRealPath();
			Path descriptionFile= canonicalLocation.resolve(".project"); //$NON-NLS-1$
			if (!Files.isRegularFile(descriptionFile)) {
				throw new IOException("Imported project location has no .project file: " + canonicalLocation); //$NON-NLS-1$
			}
			IProjectDescription description= ResourcesPlugin.getWorkspace().loadProjectDescription(
					org.eclipse.core.runtime.Path.fromOSString(descriptionFile.toString()));
			if (!name.equals(description.getName())) {
				throw new IOException("Imported .project declares " + description.getName() //$NON-NLS-1$
						+ " but --project requested " + name); //$NON-NLS-1$
			}
			org.eclipse.core.runtime.IPath location=
					org.eclipse.core.runtime.Path.fromOSString(canonicalLocation.toString());
			org.eclipse.core.runtime.IPath defaultLocation=
					root.getLocation() == null ? null : root.getLocation().append(name);
			description.setLocation(location.equals(defaultLocation) ? null : location);
			project.create(description, monitor);
		} else if (requestedLocation != null) {
			Path canonicalLocation= requestedLocation.toRealPath();
			org.eclipse.core.runtime.IPath actualLocation= project.getLocation();
			if (actualLocation == null || !canonicalLocation.equals(actualLocation.toFile().toPath().toRealPath())) {
				throw new IOException("Workspace project exists at another location: " + name); //$NON-NLS-1$
			}
		}
		if (!project.isOpen()) {
			project.open(monitor);
		}
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		return project;
	}

	static List<SourceSnapshot> snapshots(IJavaProject project) throws CoreException, IOException {
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


	static List<ResourceSnapshot> resourceSnapshots(IProject project, List<String> selected) throws IOException {
		List<IFile> files= new ArrayList<>();
		IFile descriptor= project.getFile(PDE_MANIFEST);
		if (descriptor.exists()) {
			files.add(descriptor);
			IFile build= project.getFile("build.properties"); //$NON-NLS-1$
			if (build.exists()) {
				files.add(build);
			}
			IWorkspaceRoot root= project.getWorkspace().getRoot();
			for (String path : selected) {
				if (!path.startsWith("/") || !(root.findMember(path) instanceof IFile file) //$NON-NLS-1$
						|| !("feature.xml".equals(file.getName()) || "target".equals(file.getFileExtension()))) { //$NON-NLS-1$ //$NON-NLS-2$
					throw new IOException("Expected a workspace feature.xml or .target file: " + path); //$NON-NLS-1$
				}
				if (!files.contains(file)) {
					files.add(file);
				}
			}
		} else {
			if (!selected.isEmpty()) {
				throw new IOException("PDE deployment descriptors require a bundle manifest"); //$NON-NLS-1$
			}
			descriptor= project.getFile(MAVEN_POM);
			if (descriptor.exists()) {
				files.add(descriptor);
			}
		}
		List<ResourceSnapshot> snapshots= new ArrayList<>();
		for (IFile file : files) {
			if (file.isLinked(IResource.CHECK_ANCESTORS) || file.getLocation() == null) {
				throw new IOException("Cannot snapshot a linked descriptor: " + file.getFullPath()); //$NON-NLS-1$
			}
			Path path= file.getLocation().toFile().toPath().toAbsolutePath().normalize();
			if (!path.toRealPath().equals(path)) {
				throw new IOException("Cannot snapshot a symbolic descriptor path: " + path); //$NON-NLS-1$
			}
			if (!project.equals(file.getProject()) && !project.getLocation().removeLastSegments(1)
					.equals(file.getProject().getLocation().removeLastSegments(1))) {
				throw new IOException("External deployment project must be a physical sibling: " + path); //$NON-NLS-1$
			}
			snapshots.add(new ResourceSnapshot(file, path, file.getProjectRelativePath().toPortableString(),
					Files.readAllBytes(path)));
		}
		return List.copyOf(snapshots);
	}

	private static void updateProjectMetadata(List<SourceSnapshot> sources, List<ResourceSnapshot> resources,
			List<ResourceSnapshot> written, Map<String, String> options) throws IOException, CoreException {
		if (resources.isEmpty()) {
			return;
		}
		Map<SourceSnapshot, byte[]> replacements= new LinkedHashMap<>();
		for (SourceSnapshot source : sources) {
			byte[] after= Files.readAllBytes(source.path());
			if (!Arrays.equals(source.before(), after)) {
				replacements.put(source, after);
			}
		}
		var proposed= planProjectMetadata(sources, resources, replacements, options);
		for (var entry : proposed.changes().entrySet()) {
			ResourceSnapshot resource= entry.getKey();
			if (!Arrays.equals(resource.before(), Files.readAllBytes(resource.path()))) {
				throw new IOException("Project descriptor changed during cleanup: " + resource.file().getFullPath()); //$NON-NLS-1$
			}
			if (!Arrays.equals(entry.getValue(), resource.before())) {
				written.add(resource);
				Files.write(resource.path(), entry.getValue());
			}
		}
	}

	static MetadataPlan planProjectMetadata(List<SourceSnapshot> sources,
			List<ResourceSnapshot> resources, Map<SourceSnapshot, byte[]> replacements, Map<String, String> options)
			throws IOException, CoreException {
		if (resources.isEmpty()) {
			return new MetadataPlan(Map.of(), false);
		}
		ResourceSnapshot descriptor= resources.getFirst();
		boolean pde= PDE_MANIFEST.equals(descriptor.relativePath());
		boolean isolatedIntroduced= false;
		boolean suiteIntroduced= false;
		for (var entry : replacements.entrySet()) {
			SourceSnapshot source= entry.getKey();
			byte[] after= entry.getValue();
			isolatedIntroduced |= pde && !contains(source.before(), JUNIT_ISOLATED_TYPE) && contains(after, JUNIT_ISOLATED_TYPE);
			Charset charset= Charset.forName(source.file().getCharset());
			String updated= new String(after, charset);
			if (PlatformSuiteDependencies.usesSuite(updated, options)
					&& !PlatformSuiteDependencies.usesSuite(new String(source.before(), charset), options)) {
				if (!pde && !source.relativePath().startsWith("src/test/java/")) { //$NON-NLS-1$
					throw new IOException("Cannot add a test-scoped Suite dependency for " + source.relativePath()); //$NON-NLS-1$
				}
				suiteIntroduced= true;
			}
		}
		if (!isolatedIntroduced && !suiteIntroduced) {
			return new MetadataPlan(Map.of(), false);
		}
		if (descriptor.file().isLinked(IResource.CHECK_ANCESTORS) || Files.isSymbolicLink(descriptor.path())) {
			throw new IOException("Cannot update a linked project descriptor: " + descriptor.relativePath()); //$NON-NLS-1$
		}
		boolean retiresVintage= false;
		byte[] updated= descriptor.before();
		if (isolatedIntroduced) {
			updated= PdeManifestImportUpdater.addImport(updated, JUNIT_PARALLEL_PACKAGE);
		}
		if (suiteIntroduced) {
			if (pde) {
				updated= PdeManifestImportUpdater.addImport(updated, PlatformSuiteDependencies.API_PACKAGE);
			} else {
				String pom= StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(updated)).toString();
				String withSuite= PlatformSuiteDependencies.addToPom(pom);
				Map<Path, VintageDeploymentPlan.SourceTransition> participants= new LinkedHashMap<>();
				for (SourceSnapshot source : sources) {
					Charset encoding= Charset.forName(source.file().getCharset());
					participants.put(source.path(), new VintageDeploymentPlan.SourceTransition(
							new String(source.before(), encoding), new String(replacements.getOrDefault(source, source.before()), encoding)));
				}
				String deployment= VintageDeploymentPlan.afterSuiteMigration(withSuite, descriptor.file(), participants);
				retiresVintage= !deployment.equals(withSuite);
				updated= deployment.getBytes(StandardCharsets.UTF_8);
			}
		}
		Map<ResourceSnapshot, byte[]> proposed= new LinkedHashMap<>();
		proposed.put(descriptor, updated);
		if (pde && suiteIntroduced && resources.size() > 1) {
			String bundle= new Manifest(new ByteArrayInputStream(descriptor.before()))
					.getMainAttributes().getValue("Bundle-SymbolicName"); //$NON-NLS-1$
			for (ResourceSnapshot resource : resources.subList(1, resources.size())) {
				boolean build= "build.properties".equals(resource.relativePath()); //$NON-NLS-1$
				Charset charset= build ? StandardCharsets.ISO_8859_1 : Charset.forName(resource.file().getCharset());
				String source= charset.newDecoder().decode(java.nio.ByteBuffer.wrap(resource.before())).toString();
				String result;
				if (build) {
					result= PdeSuiteDeployment.buildProperties(source);
				} else {
					if (bundle == null) {
						throw new IOException("Deployment planning requires Bundle-SymbolicName"); //$NON-NLS-1$
					}
					result= PdeSuiteDeployment.xml(source, "feature.xml".equals(resource.file().getName()), //$NON-NLS-1$
							bundle.split(";", 2)[0].strip()); //$NON-NLS-1$
				}
				if (!source.equals(result)) {
					proposed.put(resource, result.getBytes(charset));
				}
			}
		}
		return new MetadataPlan(proposed, retiresVintage);
	}

	private static boolean contains(byte[] content, String token) {
		return new String(content, StandardCharsets.UTF_8).contains(token);
	}

	private static void refresh(List<SourceSnapshot> sources, List<ResourceSnapshot> resources,
			IProgressMonitor monitor) throws CoreException {
		for (SourceSnapshot source : sources) {
			source.file().refreshLocal(IResource.DEPTH_ZERO, monitor);
		}
		for (ResourceSnapshot resource : resources) {
			resource.file().refreshLocal(IResource.DEPTH_ZERO, monitor);
		}
	}

	private static List<ChangedFile> changedFiles(List<SourceSnapshot> sources,
			List<ResourceSnapshot> resources) throws IOException {
		List<ChangedFile> result= new ArrayList<>();
		IProject project= sources.getFirst().file().getProject();
		boolean mixed= resources.stream().anyMatch(r -> !project.equals(r.file().getProject()));
		Path parent= project.getLocation().toFile().toPath().getParent();
		for (SourceSnapshot source : sources) {
			byte[] after= Files.readAllBytes(source.path());
			if (!Arrays.equals(source.before(), after)) {
				result.add(new ChangedFile(mixed ? patchPath(parent, source.path()) : source.relativePath(), source.before(), after));
			}
		}
		for (ResourceSnapshot resource : resources) {
			byte[] after= Files.readAllBytes(resource.path());
			if (!Arrays.equals(resource.before(), after)) {
				result.add(new ChangedFile(mixed ? patchPath(parent, resource.path()) : resource.relativePath(), resource.before(), after));
			}
		}
		result.sort(Comparator.comparing(ChangedFile::relativePath));
		return List.copyOf(result);
	}

	static String patchPath(Path root, Path file) {
		return root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
	}

	private static void restore(List<SourceSnapshot> sources, List<ResourceSnapshot> resources,
			IProgressMonitor monitor, List<String> errors) {
		restore(sources, monitor, errors);
		for (ResourceSnapshot resource : resources) {
			restore(resource.file(), resource.path(), resource.relativePath(), resource.before(), monitor, errors);
		}
	}

	private static void restore(List<SourceSnapshot> sources, IProgressMonitor monitor,
			List<String> errors) {
		for (SourceSnapshot source : sources) {
			restore(source.file(), source.path(), source.relativePath(), source.before(), monitor, errors);
		}
	}

	private static void restore(IFile file, Path path, String relativePath, byte[] before,
			IProgressMonitor monitor, List<String> errors) {
		try {
			Files.write(path, before);
			file.refreshLocal(IResource.DEPTH_ZERO, monitor);
		} catch (IOException | CoreException e) {
			errors.add("Cannot restore " + relativePath + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static void writePatch(Path path, List<ChangedFile> changed) throws IOException {
		createParent(path);
		ByteArrayOutputStream patch= new ByteArrayOutputStream();
		for (ChangedFile source : changed) {
			patch.writeBytes(UnifiedDiffFormatter.format(source.relativePath(), source.before(), source.after()));
		}
		Files.write(path, patch.toByteArray());
	}

	private static void writeReport(Path path, Arguments arguments, Instant started, Instant ended,
			Map<String, String> options, List<SourceSnapshot> sources, List<ChangedFile> changed,
			List<String> statusMessages, List<String> planningDiagnostics, List<MigrationBatches.Batch> batches, List<String> errors) throws IOException {
		createParent(path);
		StringBuilder json= new StringBuilder(2048);
		json.append("{\n"); //$NON-NLS-1$
		property(json, "schemaVersion", "1", 1).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
		property(json, "tool", "sandbox-project-cleanup", 1).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
		property(json, "mode", arguments.mode().name().toLowerCase(java.util.Locale.ROOT), 1).append(",\n"); //$NON-NLS-1$
		property(json, "project", arguments.projectName(), 1).append(",\n"); //$NON-NLS-1$
		property(json, "projectLocation", arguments.projectLocation() == null ? "" //$NON-NLS-1$ //$NON-NLS-2$
				: arguments.projectLocation().toString(), 1).append(",\n"); //$NON-NLS-1$
		property(json, "startTime", started.toString(), 1).append(",\n"); //$NON-NLS-1$
		property(json, "endTime", ended.toString(), 1).append(",\n"); //$NON-NLS-1$
		number(json, "durationMs", ended.toEpochMilli() - started.toEpochMilli(), 1).append(",\n"); //$NON-NLS-1$
		number(json, "filesProcessed", sources.size(), 1).append(",\n"); //$NON-NLS-1$
		number(json, "filesChanged", changed.size(), 1).append(",\n"); //$NON-NLS-1$
		array(json, "changedFiles", changed.stream().map(ChangedFile::relativePath).toList(), 1) //$NON-NLS-1$
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
		if (arguments.batchMaxFiles() > 0) {
			number(json, "batchMaxFiles", arguments.batchMaxFiles(), 1).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
			property(json, "selectedBatch", arguments.batch() == null ? "" : arguments.batch(), 1).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			json.append("  \"batches\": ["); //$NON-NLS-1$
			for (int index= 0; index < batches.size(); index++) {
				var batch= batches.get(index);
				json.append(index == 0 ? "\n    {\n" : ",\n    {\n"); //$NON-NLS-1$ //$NON-NLS-2$
				property(json, "id", batch.id(), 3).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
				property(json, "receipt", batch.receipt(), 3).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
				array(json, "files", batch.files(), 3).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
				array(json, "prerequisites", batch.prerequisites(), 3).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
				array(json, "dependencies", batch.dependencies(), 3).append(",\n"); //$NON-NLS-1$ //$NON-NLS-2$
				json.append("      \"oversized\": ").append(batch.files().size() > arguments.batchMaxFiles()).append("\n    }"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			json.append("\n  ],\n"); //$NON-NLS-1$
		}
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

	private static String describeException(Throwable throwable) {
		StringBuilder description= new StringBuilder();
		Set<Throwable> visited= java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		Throwable current= throwable;
		while (current != null && visited.add(current)) {
			if (!description.isEmpty()) {
				description.append(" <- caused by: "); //$NON-NLS-1$
			}
			description.append(current.getClass().getName());
			String message= current.getMessage();
			if (message != null && !message.isBlank()) {
				description.append(": ").append(message); //$NON-NLS-1$
			}
			current= current.getCause();
		}
		return description.toString();
	}


	private static void preserveOriginalLineDelimiters(List<SourceSnapshot> sources)
			throws IOException, CoreException {
		for (SourceSnapshot source : sources) {
			byte[] current= Files.readAllBytes(source.path());
			byte[] normalized= LineDelimiterPreserver.preserve(
					source.before(), current, source.file().getCharset());
			if (!Arrays.equals(current, normalized)) {
				Files.write(source.path(), normalized);
			}
		}
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
				  -application org.sandbox.jdt.core.ProjectWideJavaCleanup
				  --project <workspace-project>
				  [--project-location <external-project-directory>]
				  --config <cleanup.properties>
				  --report <result.json>
				  [--patch <changes.patch>]
				  [--pde-deployment </workspace-project/feature.xml or .target>] (repeatable)
				  [--mode check|apply]
				  [--batch-max-files <positive number> [--batch <id> --batch-receipt <receipt>]]
				"""); //$NON-NLS-1$
	}

	@Override
	public void stop() {
		// Nothing to stop.
	}
}
