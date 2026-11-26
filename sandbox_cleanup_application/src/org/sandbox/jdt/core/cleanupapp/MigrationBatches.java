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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;

import org.sandbox.jdt.cleanup.multifile.api.LineDelimiterPreserver;
import org.sandbox.jdt.core.cleanupapp.MigrationBatchDependencies.Group;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.ChangedFile;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.MetadataPlan;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.ResourceSnapshot;
import org.sandbox.jdt.core.cleanupapp.ProjectWideCodeCleanupApplication.SourceSnapshot;

/** A single invocation's native refactoring, review packages and expected workspace contents. */
final class MigrationBatches {
	record SourceEdit(SourceSnapshot source, String beforeText, String afterText, byte[] nativeBytes, byte[] after,
			TextEditBasedChange change) {
		String path() { return source.file().getFullPath().toPortableString(); }
	}
	record Batch(String id, String receipt, List<String> files, List<String> prerequisites, List<String> dependencies) {}
	private record Package(Batch batch, List<SourceEdit> edits, Map<ResourceSnapshot, byte[]> metadata) {}

	private record UnitSettings(Map<String, String> options, String charset) {}

	private record Context(IJavaProject project, List<IClasspathEntry> raw, List<IClasspathEntry> resolved,
			IPath output, Map<String, String> options, Map<ICompilationUnit, UnitSettings> unitOptions,
			Map<IFile, String> resourceCharsets) {
		static Context capture(IJavaProject project, List<SourceSnapshot> sources, List<ResourceSnapshot> resources) throws CoreException {
			var unitOptions= new HashMap<ICompilationUnit, UnitSettings>();
			for (var source : sources) {
				unitOptions.put(source.unit(), new UnitSettings(Map.copyOf(source.unit().getCustomOptions()), source.file().getCharset()));
			}
			var resourceCharsets= new HashMap<IFile, String>();
			for (var resource : resources) resourceCharsets.put(resource.file(), resource.file().getCharset());
			return new Context(project, List.of(project.getRawClasspath()), List.of(project.getResolvedClasspath(true)),
					project.getOutputLocation(), Map.copyOf(project.getOptions(true)), unitOptions, resourceCharsets);
		}
	}

	private final Context context;
	private final Change nativeChange;
	private final List<SourceSnapshot> sources;
	private final List<ResourceSnapshot> resources;
	private final List<SourceEdit> edits;
	private final List<Package> packages;
	private final MetadataPlan fullMetadata;
	private final MixedSupportImport.Bindings mixedBindings;

	private MigrationBatches(Context context, Change nativeChange, List<SourceSnapshot> sources, List<ResourceSnapshot> resources,
			List<SourceEdit> edits, List<Package> packages, MetadataPlan fullMetadata, MixedSupportImport.Bindings mixedBindings) {
		this.context= context;
		this.nativeChange= nativeChange;
		this.sources= sources;
		this.resources= resources;
		this.edits= edits;
		this.packages= packages;
		this.fullMetadata= fullMetadata;
		this.mixedBindings= mixedBindings;
	}

	static MigrationBatches plan(IJavaProject project, Change change, List<SourceSnapshot> sources,
			List<ResourceSnapshot> resources, List<Group> candidates, int limit, Map<String, String> cleanupOptions, MixedSupportImport.Bindings mixedBindings, IProgressMonitor monitor)
			throws CoreException, IOException {
		var context= Context.capture(project, sources, resources);
		Map<IFile, SourceSnapshot> inputs= new HashMap<>();
		Map<String, Integer> windows= new HashMap<>();
		for (int index= 0; index < sources.size(); index++) {
			var input= sources.get(index);
			inputs.put(input.file(), input);
			windows.put(input.file().getFullPath().toPortableString(), index / limit);
		}
		var byPath= new TreeMap<String, SourceEdit>();
		collect(change, inputs, byPath, monitor);
		var edits= List.copyOf(byPath.values());
		var explicitGroups= Set.copyOf(candidates);
		var groups= new ArrayList<>(explicitGroups);
		groups.addAll(MigrationBatchDependencies.discover(project, edits, sources, monitor));
		Map<String, String> parents= new HashMap<>();
		byPath.keySet().forEach(path -> parents.put(path, path));
		// Explicit planner candidates may overlap at an unchanged owner. Ordinary
		// usage of an unchanged source helper is never itself an atomic candidate.
		for (Group candidate : candidates) {
			for (String path : candidate.paths()) parents.putIfAbsent(path, path);
			connect(parents, candidate.paths());
		}
		for (Group group : groups) {
			connect(parents, group.paths().stream().filter(byPath::containsKey).toList());
		}
		Map<String, Set<String>> reasonsByComponent= new HashMap<>();
		for (Group group : groups) {
			var participants= group.paths().stream().filter(byPath::containsKey).distinct().toList();
			if (!participants.isEmpty() && (participants.size() > 1 || explicitGroups.contains(group))) {
				reasonsByComponent.computeIfAbsent(root(parents, participants.getFirst()), key -> new TreeSet<>()).add(group.reason());
			}
		}
		Map<String, List<SourceEdit>> components= new LinkedHashMap<>();
		for (var edit : edits) components.computeIfAbsent(root(parents, edit.path()), key -> new ArrayList<>()).add(edit);
		Map<String, List<SourceEdit>> packages= new LinkedHashMap<>();
		for (var component : components.values()) {
			// A component crossing a stable filing window gets a package of its own.
			// Removing another package can therefore never repack the remaining components.
			int window= windows.get(component.getFirst().path());
			boolean crossesWindow= component.stream().anyMatch(edit -> windows.get(edit.path()) != window);
			String bucket= crossesWindow ? component.getFirst().path() : Integer.toString(window);
			packages.computeIfAbsent(bucket, key -> new ArrayList<>()).addAll(component);
		}
		List<Package> plans= new ArrayList<>();
		var options= context.options();
		var complete= metadata(sources, resources, edits, options, mixedBindings);
		var reviewContext= contextDigest(context, sources, resources, cleanupOptions, limit);
		boolean retirementCaptured= false;
		for (var pack : packages.values()) {
			pack.sort(java.util.Comparator.comparing(SourceEdit::path));
			var reasons= new TreeSet<String>();
			for (var edit : pack) reasons.addAll(reasonsByComponent.getOrDefault(root(parents, edit.path()), Set.of()));
			var meta= packages.size() == 1 ? complete : metadata(sources, resources, pack, options, mixedBindings);
			retirementCaptured |= meta.retiresVintage();
			plans.add(review(project, pack, reasons, meta, reviewContext));
		}
		if (complete.retiresVintage() && !retirementCaptured) {
			// The existing standalone closure proof compares the original suite and
			// every selected legacy member with the proposed deployment in one pass.
			var reasons= new TreeSet<String>();
			for (var plan : plans) reasons.addAll(plan.batch().dependencies());
			reasons.add("Vintage dependency retirement requires the complete original-to-migrated deployment"); //$NON-NLS-1$
			plans= List.of(review(project, edits, reasons, complete, reviewContext));
		}
		return new MigrationBatches(context, change, sources, resources, edits, List.copyOf(plans), complete, mixedBindings);
	}

	private static Package review(IJavaProject project, List<SourceEdit> pack, Set<String> reasons, MetadataPlan meta, String reviewContext) {
		List<String> files= pack.stream().map(e -> e.source().relativePath()).toList();
		String id= digest(files.stream().map(x -> x.getBytes(StandardCharsets.UTF_8)).toList());
		var evidence= new ArrayList<byte[]>();
		evidence.add(reviewContext.getBytes(StandardCharsets.UTF_8));
		for (var edit : pack) {
			evidence.add(edit.source().relativePath().getBytes(StandardCharsets.UTF_8));
			evidence.add(edit.source().before());
			evidence.add(edit.after());
		}
		var prerequisites= new ArrayList<String>();
		for (var entry : meta.changes().entrySet()) {
			var resource= entry.getKey();
			prerequisites.add(reportPath(resource.file(), !project.getProject().equals(resource.file().getProject())));
			evidence.add(resource.file().getFullPath().toPortableString().getBytes(StandardCharsets.UTF_8));
			evidence.add(resource.before());
			evidence.add(entry.getValue());
		}
		var batch= new Batch(id, digest(evidence), files, List.copyOf(prerequisites), List.copyOf(reasons));
		return new Package(batch, List.copyOf(pack), meta.changes());
	}

	/** Inputs shared by every package. Hash their bytes once per plan. */
	private static String contextDigest(Context context, List<SourceSnapshot> sources, List<ResourceSnapshot> resources,
			Map<String, String> cleanupOptions, int limit) throws CoreException {
		var fields= new ArrayList<byte[]>();
		fields.add(context.project().getPath().toPortableString().getBytes(StandardCharsets.UTF_8));
		fields.add(context.output().toPortableString().getBytes(StandardCharsets.UTF_8));
		fields.add(Integer.toString(limit).getBytes(StandardCharsets.UTF_8));
		options(fields, cleanupOptions);
		options(fields, context.options());
		for (var entries : List.of(context.raw(), context.resolved())) {
			fields.add(Integer.toString(entries.size()).getBytes(StandardCharsets.UTF_8));
			for (var entry : entries) fields.add(context.project().encodeClasspathEntry(entry).getBytes(StandardCharsets.UTF_8));
		}
		for (var source : sources) {
			var settings= context.unitOptions().get(source.unit());
			fields.add(source.relativePath().getBytes(StandardCharsets.UTF_8));
			fields.add(settings.charset().getBytes(StandardCharsets.UTF_8));
			options(fields, settings.options());
			fields.add(source.before());
		}
		for (var resource : resources) {
			fields.add(resource.file().getFullPath().toPortableString().getBytes(StandardCharsets.UTF_8));
			fields.add(context.resourceCharsets().get(resource.file()).getBytes(StandardCharsets.UTF_8));
			fields.add(resource.before());
		}
		return digest(fields);
	}

	private static void options(List<byte[]> fields, Map<String, String> values) {
		fields.add(Integer.toString(values.size()).getBytes(StandardCharsets.UTF_8));
		new TreeMap<>(values).forEach((key, value) -> {
			fields.add(key.getBytes(StandardCharsets.UTF_8));
			fields.add(value.getBytes(StandardCharsets.UTF_8));
		});
	}

	private static void connect(Map<String, String> parents, List<String> paths) {
		if (paths.isEmpty()) return;
		String first= root(parents, paths.getFirst());
		for (int index= 1; index < paths.size(); index++) parents.put(root(parents, paths.get(index)), first);
	}

	private static String root(Map<String, String> parents, String path) {
		while (!path.equals(parents.get(path))) {
			String parent= parents.get(path);
			parents.put(path, parents.get(parent));
			path= parent;
		}
		return path;
	}

	private static void collect(Change change, Map<IFile, SourceSnapshot> sources, Map<String, SourceEdit> results,
			IProgressMonitor monitor) throws CoreException, IOException {
		if (change instanceof NullChange || !change.isEnabled()) return;
		if (change instanceof CompositeChange composite) {
			for (Change child : composite.getChildren()) collect(child, sources, results, monitor);
			return;
		}
		if (!(change instanceof TextEditBasedChange text)) throw new IOException("Cannot partition native change " + change.getName()); //$NON-NLS-1$
		Object element= text.getModifiedElement();
		if (element instanceof ICompilationUnit unit) element= unit.getResource();
		var input= element instanceof IFile file ? sources.get(file) : null;
		if (input == null) throw new IOException("Unclassified native project prerequisite: " + change.getName()); //$NON-NLS-1$
		String before= text.getCurrentContent(monitor);
		String after= text.getPreviewContent(monitor);
		String charset= input.file().getCharset();
		var nativeBytes= LineDelimiterPreserver.encodePreview(input.before(), Charset.forName(charset), before, after);
		var reviewed= LineDelimiterPreserver.preserve(input.before(), nativeBytes, charset);
		if (Arrays.equals(reviewed, input.before())) {
			change.setEnabled(false);
			return;
		}
		var edit= new SourceEdit(input, before, after, nativeBytes, reviewed, text);
		if (results.putIfAbsent(edit.path(), edit) != null) throw new IOException("Multiple independent native edits for " + edit.path()); //$NON-NLS-1$
	}

	private static MetadataPlan metadata(List<SourceSnapshot> sources, List<ResourceSnapshot> resources,
			List<SourceEdit> selected, Map<String, String> options, MixedSupportImport.Bindings mixedBindings) throws CoreException, IOException {
		Map<SourceSnapshot, byte[]> replacements= new LinkedHashMap<>();
		selected.forEach(edit -> replacements.put(edit.source(), edit.after()));
		var proposed= ProjectWideCodeCleanupApplication.planProjectMetadata(sources, resources, replacements, options, mixedBindings);
		Map<ResourceSnapshot, byte[]> changes= new LinkedHashMap<>();
		proposed.changes().forEach((resource, bytes) -> {
			if (!Arrays.equals(resource.before(), bytes)) changes.put(resource, bytes);
		});
		return new MetadataPlan(changes, proposed.retiresVintage());
	}

	List<Batch> batches() { return packages.stream().map(Package::batch).toList(); }

	List<ChangedFile> select(String id, String receipt, boolean apply, IProgressMonitor monitor)
			throws IOException, CoreException {
		try {
			List<SourceEdit> selected;
			Map<ResourceSnapshot, byte[]> metadata;
			if (id == null) {
				selected= edits;
				metadata= fullMetadata.changes();
			} else {
				var plan= packages.stream().filter(p -> p.batch().id().equals(id)).findFirst()
						.orElseThrow(() -> new IOException("Batch no longer exists. Review the current batches: " + id)); //$NON-NLS-1$
				if (receipt != null && !plan.batch().receipt().equals(receipt)) throw new IOException("Batch review receipt is stale: " + id); //$NON-NLS-1$
				selected= plan.edits();
				metadata= plan.metadata();
			}
			if (apply) perform(selected, metadata, monitor);
			List<ChangedFile> changes= new ArrayList<>();
			boolean mixed= resources.stream().anyMatch(r -> !sources.getFirst().file().getProject().equals(r.file().getProject()));
			for (var e : selected) changes.add(new ChangedFile(reportPath(e.source().file(), mixed), e.source().before(), e.after()));
			for (var e : metadata.entrySet()) changes.add(new ChangedFile(reportPath(e.getKey().file(), mixed), e.getKey().before(), e.getValue()));
			return List.copyOf(changes);
		} finally {
			nativeChange.dispose();
		}
	}

	private static String reportPath(IFile file, boolean mixed) {
		if (!mixed) return file.getProjectRelativePath().toPortableString();
		var parent= file.getProject().getLocation().toFile().toPath().getParent();
		if (parent == null) throw new IllegalArgumentException("Physical project has no parent for a mixed-project patch"); //$NON-NLS-1$
		return ProjectWideCodeCleanupApplication.patchPath(parent, file.getLocation().toFile().toPath());
	}

	private void perform(List<SourceEdit> selected, Map<ResourceSnapshot, byte[]> metadata, IProgressMonitor monitor)
			throws CoreException {
		Set<SourceEdit> chosen= new HashSet<>(selected);
		for (var edit : edits) edit.change().setEnabled(chosen.contains(edit));
		var transaction= new CompositeChange("Apply reviewed migration batch") { //$NON-NLS-1$
			@Override public Change perform(IProgressMonitor pm) throws CoreException {
				Change undo= super.perform(pm);
				try {
					for (var edit : selected) verify(edit.source().file(), edit.after());
					for (var entry : metadata.entrySet()) verify(entry.getKey().file(), entry.getValue());
				} catch (CoreException failure) {
					if (undo != null) {
						try { undo.perform(new org.eclipse.core.runtime.NullProgressMonitor()); }
						catch (CoreException | RuntimeException rollback) { failure.addSuppressed(rollback); }
						finally { undo.dispose(); }
					}
					throw failure;
				}
				return undo;
			}
		};
		transaction.add(nativeChange);
		for (var edit : selected) {
			if (!Arrays.equals(edit.nativeBytes(), edit.after())) {
				transaction.add(new BatchResourceChange(edit.source().file(), edit.nativeBytes(), edit.after(), edit.source().before()));
			}
		}
		for (var entry : metadata.entrySet()) {
			var resource= entry.getKey();
			transaction.add(new BatchResourceChange(resource.file(), resource.before(), entry.getValue(), null));
		}
		var workspace= ResourcesPlugin.getWorkspace();
		workspace.run(pm -> {
			try {
				validateContext(selected, metadata);
				transaction.initializeValidationData(pm);
				var operation= new PerformChangeOperation(transaction);
				operation.setUndoManager(RefactoringCore.getUndoManager(), transaction.getName());
				operation.run(pm);
				if (!operation.changeExecuted() || operation.getUndoChange() == null)
					throw new IOException("Batch could not execute with Undo: " + operation.getValidationStatus()); //$NON-NLS-1$
			} catch (IOException e) {
				throw new CoreException(Status.error(e.getMessage(), e));
			} catch (CoreException | RuntimeException failure) {
				var partial= transaction.getUndoUntilException();
				if (partial != null) {
					try { partial.perform(new org.eclipse.core.runtime.NullProgressMonitor()); }
					catch (CoreException | RuntimeException rollback) { failure.addSuppressed(rollback); }
					finally { partial.dispose(); }
				}
				throw failure;
			}
		}, workspace.getRoot(), 0, monitor);
	}

	private void validateContext(List<SourceEdit> selected, Map<ResourceSnapshot, byte[]> reviewedMetadata) throws IOException, CoreException {
		var current= ProjectWideCodeCleanupApplication.snapshots(context.project());
		if (!context.equals(Context.capture(context.project(), current, resources))) {
			throw new IOException("Project scope, encoding, compiler options or classpath changed after batch planning"); //$NON-NLS-1$
		}
		var currentSources= new HashMap<IFile, SourceSnapshot>();
		for (var snapshot : current) currentSources.put(snapshot.file(), snapshot);
		for (var source : sources) {
			var now= currentSources.get(source.file());
			if (!source.path().equals(now.path()) || !Arrays.equals(source.before(), now.before())
					|| source.unit().hasUnsavedChanges() || dirty(source.file())) {
				throw new IOException("Source changed after batch planning: " + source.relativePath()); //$NON-NLS-1$
			}
		}
		var deployments= resources.stream().map(ResourceSnapshot::file)
				.filter(f -> "feature.xml".equals(f.getName()) || "target".equals(f.getFileExtension())) //$NON-NLS-1$ //$NON-NLS-2$
				.map(f -> f.getFullPath().toPortableString()).toList();
		var currentResources= ProjectWideCodeCleanupApplication.resourceSnapshots(context.project().getProject(), deployments);
		if (currentResources.size() != resources.size()) throw new IOException("Project descriptor scope changed after batch planning"); //$NON-NLS-1$
		for (int index= 0; index < resources.size(); index++) {
			var resource= resources.get(index);
			var now= currentResources.get(index);
			if (!resource.file().equals(now.file()) || !resource.path().equals(now.path()) || !Arrays.equals(resource.before(), now.before())
					|| dirty(resource.file())) throw new IOException("Project prerequisite changed after batch planning: " + resource.file().getFullPath()); //$NON-NLS-1$
		}
		// Re-run the existing deployment proof inside the workspace scheduling
		// rule. Generated files and project references also participate in it.
		var currentMeta= metadata(sources, resources, selected, context.options(), mixedBindings).changes();
		if (!currentMeta.keySet().equals(reviewedMetadata.keySet()) || currentMeta.entrySet().stream()
				.anyMatch(e -> !Arrays.equals(e.getValue(), reviewedMetadata.get(e.getKey())))) {
			throw new IOException("Deployment prerequisites changed after batch planning"); //$NON-NLS-1$
		}
	}

	private static boolean dirty(IFile file) {
		var buffer= FileBuffers.getTextFileBufferManager().getTextFileBuffer(file.getFullPath(), LocationKind.IFILE);
		return buffer != null && buffer.isDirty();
	}

	private static void verify(IFile file, byte[] expected) throws CoreException {
		try (var input= file.getContents()) {
			if (!Arrays.equals(expected, input.readAllBytes()))
				throw new IOException("Workspace contents differ from the reviewed batch: " + file.getFullPath()); //$NON-NLS-1$
		} catch (IOException e) { throw new CoreException(Status.error(e.getMessage(), e)); }
	}

	private static String digest(List<byte[]> fields) {
		try {
			var hash= MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
			for (byte[] field : fields) {
				hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(field.length).array());
				hash.update(field);
			}
			return HexFormat.of().formatHex(hash.digest());
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

	/** Exact workspace bytes for descriptors and any native line-delimiter correction. */
	private static final class BatchResourceChange extends Change {
		private final IFile file;
		private final byte[] from;
		private final byte[] to;
		private final byte[] beforeNativeChange;

		BatchResourceChange(IFile file, byte[] from, byte[] to, byte[] beforeNativeChange) {
			this.file= file;
			this.from= from;
			this.beforeNativeChange= beforeNativeChange;
			this.to= to;
		}
		@Override public String getName() { return file.getFullPath().toPortableString(); }
		@Override public Object getModifiedElement() { return file; }
		@Override public void initializeValidationData(IProgressMonitor pm) {}
		@Override public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
			try (var input= file.getContents()) {
				byte[] current= input.readAllBytes();
				if (Arrays.equals(current, from) || Arrays.equals(current, beforeNativeChange)) return new RefactoringStatus();
				return RefactoringStatus.createFatalErrorStatus("Resource changed: " + getName()); //$NON-NLS-1$
			} catch (IOException e) { throw new CoreException(Status.error(e.getMessage(), e)); }
		}
		@Override public Change perform(IProgressMonitor pm) throws CoreException {
			verify(file, from);
			file.setContents(new java.io.ByteArrayInputStream(to), false, true, pm);
			return new BatchResourceChange(file, to, from, null);
		}
	}
}
