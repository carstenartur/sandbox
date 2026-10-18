/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0, https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.HintBindingPolicy;
import org.sandbox.jdt.triggerpattern.api.RuleMatchDiagnostics;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlanContext;
import org.sandbox.jdt.triggerpattern.cleanup.HintFileFixCore;
import org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/** Prepares an unsaved rule with the ordinary cleanup backend and project context. */
final class ProjectRuleRefactoring extends Refactoring {

	private static final String NAME= "Try TriggerPattern on my project"; //$NON-NLS-1$
	private static final int MAX_DIAGNOSTICS= 200;
	private final IJavaProject project;
	private final String dsl;
	private final Map<ICompilationUnit, String> snapshots= new LinkedHashMap<>();
	private final Set<ICompilationUnit> compilableUnits= new LinkedHashSet<>();
	private final StringBuilder explanation= new StringBuilder();
	private final List<CompilationUnitChange> preparedChanges= new ArrayList<>();
	private RefactoringStatus preparationStatus;
	private Map<String, String> optionsSnapshot;
	private List<IClasspathEntry> classpathSnapshot;
	private Map<String, String> contextSnapshot;
	private boolean prepared;

	ProjectRuleRefactoring(IJavaProject project, String dsl) {
		this.project= project;
		this.dsl= dsl;
	}

	@Override
	public String getName() {
		return NAME;
	}

	String getExplanation() {
		return explanation.toString();
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor monitor) throws CoreException {
		if (prepared) {
			return preparationStatus;
		}
		try {
			RefactoringStatus status= prepare(monitor);
			prepared= true;
			return status;
		} catch (CoreException | RuntimeException exception) {
			preparedChanges.forEach(Change::dispose);
			preparedChanges.clear();
			snapshots.clear();
			compilableUnits.clear();
			explanation.setLength(0);
			preparationStatus= null;
			optionsSnapshot= null;
			classpathSnapshot= null;
			contextSnapshot= null;
			throw exception;
		}
	}

	private RefactoringStatus prepare(IProgressMonitor monitor) throws CoreException {
		SubMonitor progress= SubMonitor.convert(monitor, NAME, 100);
		progress.checkCanceled();
		preparationStatus= new RefactoringStatus();
		if (project == null || !project.exists() || !project.getProject().isOpen()) {
			preparationStatus.addFatalError("Select an open Java project."); //$NON-NLS-1$
			return preparationStatus;
		}
		HintFile hint;
		try {
			hint= new HintFileParser().parse(dsl);
		} catch (HintParseException exception) {
			preparationStatus.addFatalError(exception.getMessage());
			return preparationStatus;
		}
		// A trial can apply changes, so compatibility fallbacks cannot authorize it.
		hint.setBindingPolicy(HintBindingPolicy.REQUIRED);
		if (hint.getRules().isEmpty() || !hint.getIncludes().isEmpty()
				|| !hint.getEmbeddedJavaBlocks().isEmpty()
				|| hint.getRules().stream().flatMap(rule -> rule.alternatives().stream())
						.anyMatch(alternative -> !alternative.structuredActions().isEmpty()
								|| alternative.hasTextReplacement()
										&& alternative.replacementPattern().contains("<?"))) { //$NON-NLS-1$
			preparationStatus.addFatalError("This trial supports self-contained text rules. " //$NON-NLS-1$
					+ "Resolve includes and use the coordinated cleanup for semantic plans or embedded Java."); //$NON-NLS-1$
			return preparationStatus;
		}
		GuardRegistry.getInstance();
		List<ICompilationUnit> units= new ArrayList<>();
		for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
			progress.checkCanceled();
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE && root.getJavaProject().equals(project)) {
				for (var child : root.getChildren()) {
					if (child instanceof IPackageFragment fragment) {
						units.addAll(List.of(fragment.getCompilationUnits()));
					}
				}
			}
		}
		Map<String, String> options= project.getOptions(true);
		optionsSnapshot= Map.copyOf(options);
		classpathSnapshot= List.of(project.getResolvedClasspath(true));
		contextSnapshot= contextFingerprint(progress);
		int diagnosticCount= 0;
		Map<ICompilationUnit, String> proposedSources= new LinkedHashMap<>();
		SubMonitor files= progress.split(95).setWorkRemaining(units.size());
		try (var scope= SemanticRewritePlanContext.install(SemanticRewritePlan.empty(), options, files::checkCanceled)) {
			for (ICompilationUnit unit : units) {
				files.checkCanceled();
				files.subTask(unit.getElementName());
				String before= unit.getSource();
				snapshots.put(unit, before);
				CompilationUnit ast= parse(unit, null, files.split(1));
				if (hasErrors(ast)) {
					preparationStatus.addWarning("Skipped file with compiler errors: " + unit.getPath()); //$NON-NLS-1$
					continue;
				}
				compilableUnits.add(unit);
				if (diagnosticCount < MAX_DIAGNOSTICS) {
					var candidates= new RuleMatchDiagnostics(hint).explain(ast, before, -1, 0,
							Math.min(30, MAX_DIAGNOSTICS - diagnosticCount), options, SemanticRewritePlan.empty());
					for (var candidate : candidates) {
						explanation.append(unit.getPath()).append('\n').append(candidate.describe()).append("\n\n"); //$NON-NLS-1$
					}
					diagnosticCount+= candidates.size();
				}
				List<TransformationResult> results= new BatchTransformationProcessor(hint)
						.process(ast, options).stream().filter(TransformationResult::hasReplacement).toList();
				if (overlap(results)) {
					preparationStatus.addFatalError("Overlapping rule changes in " + unit.getPath() //$NON-NLS-1$
							+ ". Narrow the rules and try again."); //$NON-NLS-1$
					return preparationStatus;
				}
				Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();
				HintFileFixCore.findOperationsFromHint(ast, hint, operations);
				if (operations.isEmpty()) {
					if (!results.isEmpty()) {
						explanation.append(unit.getPath()).append(": backend withheld unsupported text or comments.\n"); //$NON-NLS-1$
					}
					continue;
				}
				CompilationUnitChange generated= new NlsAwareCleanUpFix(NAME, ast,
						operations.toArray(CompilationUnitRewriteOperation[]::new)).createOptionalChange(files);
				if (generated == null) {
					explanation.append(unit.getPath()).append(": the rule is already applied or the backend withheld this change.\n"); //$NON-NLS-1$
					continue;
				}
				String after= generated.getPreviewContent(files);
				if (before.equals(after)) {
					explanation.append(unit.getPath()).append(": backend withheld the replacement; " //$NON-NLS-1$
							+ "unsupported syntax or comments were preserved without modification.\n"); //$NON-NLS-1$
					generated.dispose();
					continue;
				}
				// Type-check after all proposed sources are installed in the private overlay:
				// a related rule may change the API that this replacement references.
				// One selection per file keeps imports and exception follow-up edits together.
				CompilationUnitChange coherent= new CompilationUnitChange(unit.getElementName(), unit);
				coherent.setEdit(generated.getEdit().copy());
				coherent.addTextEditGroup(new TextEditGroup("Complete rule changes in this file", coherent.getEdit())); //$NON-NLS-1$
				preparedChanges.add(coherent);
				proposedSources.put(unit, after);
				generated.dispose();
			}
		}
		if (!proposedSources.isEmpty()) {
			preparationStatus.merge(validateProposedProject(proposedSources, progress.split(5)));
		}
		explanation.insert(0, "Language checked; project bindings and replacement compiler diagnostics checked. " //$NON-NLS-1$
				+ "Behavioral equivalence and examples still require review.\n" //$NON-NLS-1$
				+ "Each file is selected as a complete change, including imports and follow-up edits.\n\n"); //$NON-NLS-1$
		if (diagnosticCount >= MAX_DIAGNOSTICS) {
			explanation.append("Further diagnostics omitted; the diff includes all prepared changes.\n"); //$NON-NLS-1$
		}
		if (preparedChanges.isEmpty()) {
			preparationStatus.addWarning("No applicable changes. Inspect the candidate explanations."); //$NON-NLS-1$
		}
		return preparationStatus;
	}

	/** Resolves unchanged callers against all proposed files, using private unsaved copies. */
	private RefactoringStatus validateProposedProject(Map<ICompilationUnit, String> proposedSources,
			IProgressMonitor monitor) throws CoreException {
		RefactoringStatus status= new RefactoringStatus();
		WorkingCopyOwner owner= new WorkingCopyOwner() { };
		Map<ICompilationUnit, ICompilationUnit> copies= new LinkedHashMap<>();
		SubMonitor progress= SubMonitor.convert(monitor, snapshots.size() * 2);
		try {
			for (var entry : snapshots.entrySet()) {
				progress.checkCanceled();
				ICompilationUnit copy= entry.getKey().getWorkingCopy(owner, progress.split(1));
				copies.put(entry.getKey(), copy);
				copy.getBuffer().setContents(proposedSources.getOrDefault(entry.getKey(), entry.getValue()));
			}
			for (var entry : copies.entrySet()) {
				progress.checkCanceled();
				if (compilableUnits.contains(entry.getKey())
						&& hasErrors(parse(entry.getValue(), null, progress.split(1), owner))) {
					status.addFatalError("The selected changes break compilation in " + entry.getKey().getPath() //$NON-NLS-1$
							+ ". Keep dependent changes together."); //$NON-NLS-1$
					return status;
				}
			}
		} finally {
			CoreException cleanupFailure= null;
			for (ICompilationUnit copy : copies.values()) {
				try {
					copy.discardWorkingCopy();
				} catch (CoreException exception) {
					if (cleanupFailure == null) {
						cleanupFailure= exception;
					} else {
						cleanupFailure.addSuppressed(exception);
					}
				}
			}
			if (cleanupFailure != null) {
				throw cleanupFailure;
			}
		}
		return status;
	}

	/** Revalidates the actual LTK selection, including disabled complete-file edit groups. */
	private RefactoringStatus validateSelection(IProgressMonitor monitor) throws CoreException {
		Map<ICompilationUnit, String> selected= new LinkedHashMap<>();
		for (CompilationUnitChange change : preparedChanges) {
			if (change.isEnabled() && List.of(change.getTextEditChangeGroups()).stream().anyMatch(group -> group.isEnabled())) {
				selected.put(change.getCompilationUnit(), change.getPreviewContent(monitor));
			}
		}
		return selected.isEmpty() ? new RefactoringStatus() : validateProposedProject(selected, monitor);
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor monitor) throws CoreException {
		RefactoringStatus status= checkInitialConditions(monitor);
		if (optionsSnapshot != null && (!optionsSnapshot.equals(project.getOptions(true))
				|| !classpathSnapshot.equals(List.of(project.getResolvedClasspath(true)))
				|| !contextSnapshot.equals(contextFingerprint(monitor)))) {
			status.addFatalError("The project's compiler options or classpath changed. Run the trial again."); //$NON-NLS-1$
		}
		for (var entry : snapshots.entrySet()) {
			if (!entry.getKey().exists() || !entry.getValue().equals(entry.getKey().getSource())) {
				status.addFatalError("Source changed after preview: " + entry.getKey().getPath() //$NON-NLS-1$
						+ ". Run the project trial again."); //$NON-NLS-1$
			}
		}
		return status;
	}

	/** Includes new/deleted dependency sources and replacement jars at the same path. */
	private Map<String, String> contextFingerprint(IProgressMonitor monitor) throws CoreException {
		SubMonitor progress= SubMonitor.convert(monitor);
		Map<String, String> state= new LinkedHashMap<>();
		for (IPackageFragmentRoot root : project.getAllPackageFragmentRoots()) {
			progress.checkCanceled();
			IResource resource= root.getResource();
			if (resource != null && resource.exists()) {
				resource.accept(child -> {
					progress.checkCanceled();
					state.put(child.getFullPath().toString(), Long.toString(child.getModificationStamp()));
					return true;
				});
			} else if (root.isExternal()) {
				Path path= root.getPath().toFile().toPath();
				try {
					if (Files.isRegularFile(path)) {
						state.put(path.toString(), Files.getLastModifiedTime(path) + ":" + Files.size(path)); //$NON-NLS-1$
					} else if (Files.isDirectory(path)) {
						try (var files= Files.walk(path)) {
						var iterator= files.filter(Files::isRegularFile).iterator();
						while (iterator.hasNext()) {
								progress.checkCanceled();
							Path entry= iterator.next();
								state.put(entry.toString(), Files.getLastModifiedTime(entry) + ":" + Files.size(entry)); //$NON-NLS-1$
							}
						}
					}
				} catch (IOException exception) {
					throw new CoreException(Status.error("Cannot snapshot dependency " + path, exception)); //$NON-NLS-1$
				}
			}
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				for (var child : root.getChildren()) {
					if (child instanceof IPackageFragment fragment) {
						for (ICompilationUnit unit : fragment.getCompilationUnits()) {
							progress.checkCanceled();
							state.put("source:" + unit.getHandleIdentifier(), sourceFingerprint(unit.getSource())); //$NON-NLS-1$
						}
					}
				}
			}
		}
		return Map.copyOf(state);
	}

	private static String sourceFingerprint(String source) {
		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256") //$NON-NLS-1$
					.digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@Override
	public Change createChange(IProgressMonitor monitor) throws CoreException {
		if (checkFinalConditions(monitor).hasFatalError()) {
			throw new CoreException(Status.error("The rule trial is invalid. Rebuild the preview.")); //$NON-NLS-1$
		}
		return new CompositeChange(NAME, preparedChanges.toArray(Change[]::new)) {
			@Override
			public RefactoringStatus isValid(IProgressMonitor progress) throws CoreException {
				RefactoringStatus status= super.isValid(progress);
				status.merge(checkFinalConditions(progress));
				if (!status.hasFatalError()) {
					status.merge(validateSelection(progress));
				}
				return status;
			}

			@Override
			public Change perform(IProgressMonitor progress) throws CoreException {
				if (checkFinalConditions(progress).hasFatalError()) {
					throw new CoreException(Status.error("Source changed after the project trial.")); //$NON-NLS-1$
				}
				RefactoringStatus selection= validateSelection(progress);
				if (selection.hasFatalError()) {
					throw new CoreException(Status.error(selection.getMessageMatchingSeverity(RefactoringStatus.FATAL)));
				}
				return super.perform(progress);
			}
		};
	}

	private CompilationUnit parse(ICompilationUnit unit, String changedSource, IProgressMonitor monitor) {
		return parse(unit, changedSource, monitor, null);
	}

	private CompilationUnit parse(ICompilationUnit unit, String changedSource, IProgressMonitor monitor, WorkingCopyOwner owner) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		if (changedSource == null) {
			parser.setSource(unit);
		} else {
			parser.setSource(changedSource.toCharArray());
			parser.setProject(project);
			parser.setUnitName(unit.getPath().toString());
		}
		parser.setCompilerOptions(project.getOptions(true));
		if (owner != null) {
			parser.setWorkingCopyOwner(owner);
		}
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(false);
		return (CompilationUnit) parser.createAST(monitor);
	}

	private static boolean hasErrors(CompilationUnit root) {
		for (IProblem problem : root.getProblems()) {
			if (problem.isError()) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlap(List<TransformationResult> results) {
		var ordered= results.stream().sorted(Comparator.comparingInt(result -> result.match().getOffset())).toList();
		int end= -1;
		for (TransformationResult result : ordered) {
			if (result.match().getOffset() < end) {
				return true;
			}
			end= result.match().getOffset() + result.match().getLength();
		}
		return false;
	}
}
