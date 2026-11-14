/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.corext.fix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.cleanup.multifile.ContainerFlowScopeSearch;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.SourceRootPolicy;
import org.sandbox.jdt.container.api.ContainerCompatibilityDecision;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchSeed;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow.Edit;

/** Closes the source references to one producer and to every reader receiving its distinct, ordered values. */
public final class ClosedUniqueSequencePlanner {
	private ClosedUniqueSequencePlanner() {
	}

	/** Stable source identifiers and edit ranges. A plan can be compared to a fresh analysis after preview. */
	public record Plan(String producerHandle, List<String> readerHandles, Map<String, List<Edit>> unitEdits, ContainerCompatibilityDecision compatibility) {
		public Plan {
			readerHandles= List.copyOf(readerHandles);
			var ranges= new LinkedHashMap<String, List<Edit>>();
			unitEdits.forEach((handle, edits) -> ranges.put(handle, List.copyOf(edits)));
			unitEdits= Map.copyOf(ranges);
			java.util.Objects.requireNonNull(compatibility);
		}
	}

	public record Result(Plan plan, List<ICompilationUnit> required, String reason) {
		public Result {
			required= List.copyOf(required);
		}
	}

	public static Map<String, CompilationUnit> parse(IJavaProject project, Collection<ICompilationUnit> units, IProgressMonitor monitor) {
		return ClosedSourceContainerMigrationCoordinator.parse(project, List.copyOf(units), monitor);
	}

	public static Result analyze(IMethod producer, Collection<ICompilationUnit> selected, Map<String, CompilationUnit> knownRoots,
			IProgressMonitor monitor) throws CoreException {
		var project= producer.getJavaProject();
		var allowed= JavaProjectCompilationUnits.collect(List.of(project), selected, SourceRootPolicy.PRODUCTION_WITH_DEPENDENT_TESTS);
		var initial= List.of(producer.getCompilationUnit().getPrimary());
		var found= search(producer, List.of(), initial, allowed, monitor);
		if (!found.complete()) {
			return new Result(null, initial, "The Java search could not establish every source caller of the producer."); //$NON-NLS-1$
		}
		var roots= new LinkedHashMap<>(knownRoots);
		resolveMissing(project, found.compilationUnits(), roots, monitor);
		var readers= new LinkedHashSet<IMethod>();
		for (var unit : found.compilationUnits()) {
			var root= roots.get(unit.getPrimary().getHandleIdentifier());
			if (root == null) {
				return new Result(null, found.compilationUnits(), "A required producer or source caller could not be resolved."); //$NON-NLS-1$
			}
			var discovered= ClosedUniqueSequenceFlow.readers(root, producer);
			if (discovered.isEmpty()) {
				return new Result(null, found.compilationUnits(), "A producer reference has an unsupported receiver, publication or positional use."); //$NON-NLS-1$
			}
			readers.addAll(discovered.get());
		}
		var required= new LinkedHashSet<>(found.compilationUnits());
		for (var method : readers) {
			if (method.getCompilationUnit() == null) {
				return new Result(null, List.copyOf(required), "A reader declaration is outside the editable source closure."); //$NON-NLS-1$
			}
			required.add(method.getCompilationUnit().getPrimary());
		}
		if (!readers.isEmpty()) {
			found= search(producer, readers, required, allowed, monitor);
			if (!found.complete()) {
				return new Result(null, List.copyOf(required), "The Java search could not establish every source caller of a reader."); //$NON-NLS-1$
			}
			required.addAll(found.compilationUnits());
			resolveMissing(project, required, roots, monitor);
		}
		var boundary= ContainerSignatureBoundaries.check(project, found.resolvedPlan(), roots, monitor);
		if (boundary != null) {
			return new Result(null, List.copyOf(required), boundary.explanation);
		}
		Map<String, List<Edit>> edits= new LinkedHashMap<>();
		for (var unit : required) {
			String handle= unit.getPrimary().getHandleIdentifier();
			var root= roots.get(handle);
			if (root == null || java.util.Arrays.stream(root.getProblems()).anyMatch(problem -> problem.isError())) {
				return new Result(null, List.copyOf(required), "A required source unit is missing or has compiler errors."); //$NON-NLS-1$
			}
			var changes= ClosedUniqueSequenceFlow.edits(root, producer, readers);
			if (changes.isEmpty()) {
				return new Result(null, List.copyOf(required), "A declaration or use requires List behavior or accepts unrelated values."); //$NON-NLS-1$
			}
			if (!changes.get().isEmpty()) {
				edits.put(handle, List.copyOf(changes.get()));
			}
		}
		boolean hasCaller= edits.values().stream().flatMap(List::stream).anyMatch(edit -> edit.kind() == ClosedUniqueSequenceFlow.Kind.CALLER_SET_TYPE);
		if (!hasCaller) {
			return new Result(null, List.copyOf(required), "The producer has no proven direct source consumer."); //$NON-NLS-1$
		}
		boolean lockedSnapshot= edits.values().stream().flatMap(List::stream)
				.anyMatch(edit -> edit.kind() == ClosedUniqueSequenceFlow.Kind.SYNCHRONIZED_SET_FACTORY);
		if (lockedSnapshot) {
			String handle= producer.getCompilationUnit().getPrimary().getHandleIdentifier();
			var root= roots.get(handle);
			var factory= edits.get(handle).stream().filter(edit -> edit.kind() == ClosedUniqueSequenceFlow.Kind.SYNCHRONIZED_SET_FACTORY).findFirst().orElseThrow();
			var node= NodeFinder.perform(root, factory.start(), factory.length());
			while (node != null && !(node instanceof VariableDeclarationFragment)) {
				node= node.getParent();
			}
			if (node == null || ContainerSignatureBoundaries.observesRegistryField(project, roots, producer.getDeclaringType().getFullyQualifiedName(),
					((VariableDeclarationFragment) node).getName().getIdentifier(), monitor)) {
				return new Result(null, List.copyOf(required), "Reflection or a method-handle lookup can observe the registry's field type independently of its source callers."); //$NON-NLS-1$
			}
		}
		String proof= lockedSnapshot
				? "The private registry admits only guarded insertions. Registration and snapshot copying keep the same monitor. Each caller receives a separate encounter-ordered set preserving null and first registration; a return-only bridge is impossible." //$NON-NLS-1$
				: "The internal producer returns a fresh ordered set. Every source caller uses the first encounter order, accepts null, and observes only distinct values. A same-name return-only bridge is impossible."; //$NON-NLS-1$
		var plan= new Plan(producer.getHandleIdentifier(), readers.stream().map(IMethod::getHandleIdentifier).sorted().toList(), edits,
				new ContainerCompatibilityDecision(ContainerCompatibilityDecision.Mode.CLOSED_SOURCE_AUTOMATIC,
						ContainerCompatibilityDecision.BridgeFeasibility.IMPOSSIBLE_RETURN_TYPE_ONLY, proof));
		return new Result(plan, List.copyOf(required), ""); //$NON-NLS-1$
	}

	private static ContainerFlowScopeSearch.Result search(IMethod producer, Collection<IMethod> readers, Collection<ICompilationUnit> units,
			Collection<ICompilationUnit> allowed, IProgressMonitor monitor) throws CoreException {
		List<SearchSeed> seeds= new ArrayList<>();
		seeds.add(seed(producer, -1));
		for (var reader : readers.stream().sorted(Comparator.comparing(IMethod::getHandleIdentifier)).toList()) {
			seeds.add(seed(reader, 0));
		}
		return ContainerFlowScopeSearch.findRelatedUnits(producer.getJavaProject(), new ContainerFlowSearchPlan(seeds), units, allowed, monitor);
	}

	private static SearchSeed seed(IMethod method, int parameter) {
		return new SearchSeed(method.getHandleIdentifier(), SearchKind.METHOD_CALLERS, method.getKey(), method.getKey(), method.getHandleIdentifier(),
				parameter, "Find every caller of the proven ordered-set signature"); //$NON-NLS-1$
	}

	private static void resolveMissing(IJavaProject project, Collection<ICompilationUnit> required, Map<String, CompilationUnit> roots,
			IProgressMonitor monitor) {
		var missing= required.stream().filter(unit -> !roots.containsKey(unit.getPrimary().getHandleIdentifier())).toList();
		if (!missing.isEmpty()) {
			roots.putAll(parse(project, missing, monitor));
		}
	}
}
