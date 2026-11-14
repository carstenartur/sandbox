/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.cleanup.multifile.ContainerFlowScopeSearch;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.SourceRootPolicy;
import org.sandbox.jdt.container.analysis.AppendOnlyArraySeedDetector;
import org.sandbox.jdt.container.analysis.ContainerFlowContinuationDetector;
import org.sandbox.jdt.container.analysis.ContainerFlowSearchSeedExtractor;
import org.sandbox.jdt.container.analysis.LocalArrayUsageAnalyzer;
import org.sandbox.jdt.container.analysis.LocalContainerFlowGraphBuilder;
import org.sandbox.jdt.container.api.ContainerFlowGraph;
import org.sandbox.jdt.container.api.ContainerFlowGraph.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.ui.helper.views.ContainerAnalysisService.ContainerAnalysisRow;

/** Read-only source evidence for a binding-connected array, including incomplete and external boundaries. */
final class ContainerSourceFlowReport {
	private record Location(ICompilationUnit unit, ContainerUsageProfile profile) { }
	private record RootKey(String unitHandle, String bindingKey) { }

	private final LocalContainerFlowGraphBuilder graphs= new LocalContainerFlowGraphBuilder();
	private final ContainerFlowSearchSeedExtractor searchSeeds= new ContainerFlowSearchSeedExtractor();
	private final ContainerFlowContinuationDetector continuations= new ContainerFlowContinuationDetector();
	private final LocalArrayUsageAnalyzer usage= new LocalArrayUsageAnalyzer();

	List<ContainerAnalysisRow> analyze(ICompilationUnit selected, IProgressMonitor monitor) throws CoreException {
		Map<ICompilationUnit, CompilationUnit> roots= new HashMap<>();
		CompilationUnit source= root(selected, roots, monitor);
		List<ICompilationUnit> allowed= null;
		Set<ContainerAnalysisRow> rows= new LinkedHashSet<>();
		for (var seed : new AppendOnlyArraySeedDetector().findSeeds(source)) {
			if (!seed.identity().hasResolvedBinding() || (seed.elementDomain() != ElementDomain.REFERENCE && seed.elementDomain() != ElementDomain.ENUM)) {
				continue;
			}
			var graph= graphs.build(source, seed);
			if (searchSeeds.extract(graph).isEmpty()) {
				continue;
			}
			if (allowed == null) {
				allowed= JavaProjectCompilationUnits.collect(selected.getJavaProject(), List.of(selected), SourceRootPolicy.PRODUCTION_WITH_DEPENDENT_TESTS);
			}
			trace(new Location(selected, seed), graph, roots, allowed, rows, monitor);
		}
		return rows.stream().sorted(Comparator.comparing(ContainerAnalysisRow::compilationUnitHandle)
				.thenComparingInt(ContainerAnalysisRow::sourceStart).thenComparing(ContainerAnalysisRow::evidenceKind)
				.thenComparing(ContainerAnalysisRow::candidate)).toList();
	}

	private void trace(Location start, ContainerFlowGraph startingGraph, Map<ICompilationUnit, CompilationUnit> roots, List<ICompilationUnit> allowed,
			Set<ContainerAnalysisRow> rows, IProgressMonitor monitor) throws CoreException {
		var pending= new ArrayDeque<Location>();
		Set<RootKey> visited= new HashSet<>();
		Set<String> searched= new HashSet<>();
		String candidate= start.profile().identity().displayName();
		pending.add(start);
		while (!pending.isEmpty()) {
			Location location= pending.removeFirst();
			String handle= location.unit().getPrimary().getHandleIdentifier();
			var identity= location.profile().identity();
			if (!visited.add(new RootKey(handle, identity.bindingKey()))) {
				continue;
			}
			CompilationUnit source= root(location.unit(), roots, monitor);
			var graph= location == start ? startingGraph : graphs.build(source, location.profile());
			for (var edge : graph.edges()) {
				rows.add(row(candidate, handle, edge.kind().name(), edge.sourceStart(), edge.sourceLength(),
						"Binding-resolved array transfer from %s to %s".formatted(graph.node(edge.sourceNodeId()).orElseThrow().kind(), //$NON-NLS-1$
								graph.node(edge.targetNodeId()).orElseThrow().kind())));
			}
			for (var diagnostic : graph.diagnostics()) {
				if (diagnostic.kind() != DiagnosticKind.SCOPE_EXPANSION_REQUIRED) {
					rows.add(row(candidate, handle, diagnostic.kind().name(), diagnostic.sourceStart(), diagnostic.sourceLength(), diagnostic.message()));
				}
			}
			for (var item : usage.analyze(source, location.profile()).evidence()) {
				if (item.kind() == Kind.ARRAY_LENGTH_READ || item.kind() == Kind.INDEXED_READ || item.kind() == Kind.INDEXED_WRITE
						|| item.kind() == Kind.ENCOUNTER_ITERATION || item.kind() == Kind.ARRAY_IDENTITY) {
					rows.add(row(candidate, handle, item.kind().name(), item.sourceStart(), item.sourceLength(), item.summary()));
				}
			}
			var requests= searchSeeds.extract(graph).seeds().stream().filter(seed -> searched.add(seed.stableKey())).toList();
			if (requests.isEmpty()) {
				continue;
			}
			var result= ContainerFlowScopeSearch.findRelatedUnits(location.unit().getJavaProject(), new ContainerFlowSearchPlan(requests),
					List.of(location.unit()), allowed, monitor);
			for (String reason : result.rejectionReasons()) {
				rows.add(row(candidate, handle, "SCOPE_BOUNDARY", identity.sourceStart(), identity.sourceLength(), reason)); //$NON-NLS-1$
			}
			for (var unit : result.compilationUnits()) {
				String targetHandle= unit.getPrimary().getHandleIdentifier();
				var plan= continuations.detect(root(unit, roots, monitor), targetHandle, result.resolvedPlan());
				for (var next : plan.roots()) {
					var evidence= next.profile().evidence().getFirst();
					rows.add(row(candidate, targetHandle, next.kind().name(), evidence.sourceStart(), evidence.sourceLength(), evidence.summary()));
					pending.addLast(new Location(unit, next.profile()));
				}
				for (var boundary : plan.diagnostics()) {
					rows.add(row(candidate, targetHandle, boundary.kind().name(), boundary.sourceStart(), boundary.sourceLength(), boundary.message()));
				}
			}
		}
	}

	private static CompilationUnit root(ICompilationUnit unit, Map<ICompilationUnit, CompilationUnit> roots, IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		return roots.computeIfAbsent(unit, key -> ContainerAnalysisService.parse(key, monitor));
	}

	private static ContainerAnalysisRow row(String candidate, String handle, String kind, int start, int length, String evidence) {
		return new ContainerAnalysisRow(handle, candidate, "ARRAY", "Source flow", "N/A", "REPORT_ONLY", kind, evidence, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"This observation identifies a related array use. Any representation change must preserve every consumer and boundary.", start, length); //$NON-NLS-1$
	}
}
